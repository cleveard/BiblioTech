package com.github.cleveard.bibliotech.utils

import android.content.Context
import android.content.Intent
import net.openid.appauth.*
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.core.net.toUri
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.runBlocking

abstract class OAuth(
    val context: Context,
    authEndpoint: String,
    tokenEndpoint: String,
    private val clientId: String,
    private val redirectUri: String,
    preferenceKey: String
) {
    private val tokenKey = EncryptedPreferences.stringKey(preferenceKey)
    private val Context.persist: EncryptedPreferences by EncryptedPreferences.create(
    "encrypted_data_preferences",
    "google_books_keyset",
    "android-keystore://google_books_oauth_key",
    ReplaceFileCorruptionHandler { emptyPreferences() })
    private val authConfig = AuthorizationServiceConfiguration(
        authEndpoint.toUri(),
        tokenEndpoint.toUri())
    private var authService: AuthorizationService = AuthorizationService(context)
    private var authState: AuthState = runBlocking {
        try {
            AuthState.jsonDeserialize(context.applicationContext.persist.get(tokenKey)!!)
        } catch (_: Exception) {
            AuthState(authConfig)
        }
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
                redirectUri.toUri()
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
                            resume.resume(Unit)
                        }
                    }
                    save()
                }
            }
        }

        return authState.isAuthorized
    }

    suspend fun logout(): Boolean {
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
                it.resume(Triple(token, id, ex))
            }
        }
        save()
        if (result.third != null)
            throw AuthException("Not authorized", result.third)
        return action(result.first)
    }

    private suspend fun save(state: AuthState = authState): AuthState {
        context.persist.edit {
            it.put(tokenKey, state.jsonSerializeString())
        }
        return state
    }
}
