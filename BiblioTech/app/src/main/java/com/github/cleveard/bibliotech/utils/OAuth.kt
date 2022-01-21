package com.github.cleveard.bibliotech.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.github.cleveard.bibliotech.LaunchIntentForResult
import net.openid.appauth.*
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class OAuth(
    context: Context,
    authEndpoint: String,
    tokenEndpoint: String,
    private val clientId: String,
    private val redirectUri: String,
    private val preferenceKey: String
) {
    private val persist: SharedPreferences = EncryptedSharedPreferences.create(
        "secret_shared_prefs", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val authConfig = AuthorizationServiceConfiguration(
        Uri.parse(authEndpoint),
        Uri.parse(tokenEndpoint))
    private var authService: AuthorizationService = AuthorizationService(context)
    private var authState: AuthState = AuthState(authConfig)
    val authorizationException: AuthorizationException?
        get() = authState.authorizationException
    val isAuthorized: Boolean
        get() = authState.isAuthorized

    class AuthException(msg: String? = null, cause: Throwable? = null): Exception(msg, cause)

    abstract fun AuthorizationRequest.Builder.setupRequest()

    suspend fun login(launcher: LaunchIntentForResult): Boolean {
        if (!authState.isAuthorized) {
            val req = AuthorizationRequest.Builder(
                authConfig,
                clientId,
                ResponseTypeValues.CODE,
                Uri.parse(redirectUri)
            )
                .also { it.setupRequest() }
                .build()

            val intent = authService.getAuthorizationRequestIntent(req)
            val result = launcher.launchForResult(intent)
            if (result != null) {
                val resp = AuthorizationResponse.fromIntent(result)
                val ex = AuthorizationException.fromIntent(result)
                if (resp != null) {
                    suspendCoroutine<Unit> {resume ->
                        authService.performTokenRequest(
                            resp.createTokenExchangeRequest()
                        ) { response, ex ->
                            authState.update(response, ex)
                            save()
                            resume.resume(Unit)
                        }
                    }
                }
                save()
            }
        }

        return authState.isAuthorized
    }

    fun logout(): Boolean {
        if (authState.isAuthorized) {
            authState = AuthState(authConfig)
            save()
        }

        return authState.isAuthorized
    }

    @Throws(AuthException::class)
    suspend fun <T> execute(launcher: LaunchIntentForResult, action: suspend (token:String?) -> T): T {
        if (!isAuthorized)
            throw AuthException("execute: Not authorized", authorizationException)

        try {
            return execute(action)
        } catch (e: AuthException) {
        }

        if (!login(launcher))
            throw AuthException("Token refresh failed", authorizationException)
        return execute(action)
    }

    @Throws(AuthException::class)
    suspend fun <T> execute(action: suspend (token:String?) -> T): T {
        val result = suspendCoroutine<Triple<String?, String?, AuthorizationException?>> {
            authState.performActionWithFreshTokens(authService) {token, id, ex ->
                save()
                it.resume(Triple(token, id, ex))
            }
        }
        if (result.third != null)
            throw AuthException("Not authorized", result.third)
        return action(result.first)
    }

    private fun save(state: AuthState = authState): AuthState {
        persist.edit().let {
            it.putString(preferenceKey, state.jsonSerializeString())
            it.commit()
        }
        return state
    }
}