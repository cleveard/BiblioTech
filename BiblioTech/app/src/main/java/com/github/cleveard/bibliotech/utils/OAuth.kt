package com.github.cleveard.bibliotech.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import net.openid.appauth.*
import java.io.IOException
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
    private val persist: SharedPreferences = createSecretPreferences(context)
    private val authConfig = AuthorizationServiceConfiguration(
        Uri.parse(authEndpoint),
        Uri.parse(tokenEndpoint))
    private var authService: AuthorizationService = AuthorizationService(context)
    private var authState: AuthState = try {
        AuthState.jsonDeserialize(persist.getString(preferenceKey, "")!!)
    } catch (e: Exception) {
        AuthState(authConfig)
    }
    val authorizationException: AuthorizationException?
        get() = authState.authorizationException
    val isAuthorized: Boolean
        get() = authState.isAuthorized

    class AuthException(msg: String? = null, cause: Throwable? = null): Exception(msg, cause)

    abstract fun AuthorizationRequest.Builder.setupRequest()

    suspend fun login(launcher: suspend (Intent) -> Intent?): Boolean {
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
            val result = launcher(intent)
            if (result != null) {
                val resp = AuthorizationResponse.fromIntent(result)
                val ex = AuthorizationException.fromIntent(result)
                authState.update(resp, ex)
                save()
                if (resp != null) {
                    suspendCoroutine { resume ->
                        authService.performTokenRequest(
                            resp.createTokenExchangeRequest()
                        ) { response, ex ->
                            authState.update(response, ex)
                            save()
                            resume.resume(Unit)
                        }
                    }
                }
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
    suspend fun <T> execute(action: suspend (token:String?) -> T): T {
        val result = suspendCoroutine {
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

    companion object {
        private fun createSecretPreferences(context: Context): SharedPreferences {
            val name = "secret_shared_prefs"
            return try {
                EncryptedSharedPreferences.create(
                    name, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: IOException) {
                context.deleteSharedPreferences(name)
                EncryptedSharedPreferences.create(
                    name, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
    }
}
