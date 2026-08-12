package com.github.cleveard.bibliotech.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import com.github.cleveard.bibliotech.R
import com.google.api.client.http.HttpResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Scanner
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class OAuth(
    val context: Context,
    authEndpoint: String,
    tokenEndpoint: String,
    private val revokeEndpoint: String?,
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

    /**
     * Logout from Google books
     * @param revoke Set to true to revoke access as well as logout
     * @return Pair of response code and response message for revoking the current access token
     */
    suspend fun logout(revoke: Boolean): Pair<Int, String?> {
        // Return value if already logged out
        var responseCode = Pair<Int, String?>(HttpURLConnection.HTTP_OK, null)
        if (authState.isAuthorized) {
            // Lets revoke the token. If we don't have an endpoint
            // to revoke the token, then don't try to revoke it.
            revokeEndpoint?.let {endPoint ->
                // Switch to an IO thread
                responseCode = if (revoke) {
                    withContext(Dispatchers.IO) {
                        // The connection to revoke the token
                        var connection: HttpURLConnection? = null
                        try {
                            // Build the URI for the revoke request
                            // ${endPoint}?token=${accessToken}
                            // and convert it to a URL
                            val url = URI(
                                endPoint.toUri()
                                    .buildUpon()
                                    .appendQueryParameter("token", authState.accessToken)
                                    .build()
                                    .toString()
                            ).toURL()
                            // Open the connection to the URL
                            connection = url.openConnection() as HttpURLConnection
                            // Set the content type
                            connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                            // Make this a POST request
                            connection.requestMethod = "POST"
                            // Get the response, use errorStream if there was an error
                            val input = if (connection.responseCode >= HttpURLConnection.HTTP_BAD_REQUEST)
                                connection.errorStream
                            else
                                connection.inputStream
                            // Read the response into a single string
                            val response = Scanner(input, StandardCharsets.UTF_8.name())
                                .useDelimiter("\\A").let {
                                    if (it.hasNext())
                                        it.next()
                                    else
                                        ""
                                }
                            // Return the result
                            Pair(connection.responseCode, response)
                        } catch (_: MalformedURLException) {
                            // Return an error
                            Pair(0, null)
                        } catch (_: IOException) {
                            // Return an error
                            Pair(0, null)
                        } finally {
                            // Disconnect the connection
                            connection?.disconnect()
                        }
                    }
                } else
                    Pair(HttpURLConnection.HTTP_OK, null)
            }
            // Save an unauthorised authState into the encrypted store
            authState = AuthState(authConfig)
            save()
        }

        // Return the response
        return responseCode
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
        try {
            return action(result.first)
        } catch (e: HttpResponseException) {
            if (e.statusCode == HttpURLConnection. HTTP_UNAUTHORIZED)
                throw AuthException("Request is unauthorized", e)
            throw e
        }
    }

    private suspend fun save(state: AuthState = authState): AuthState {
        context.persist.edit {
            it.put(tokenKey, state.jsonSerializeString())
        }
        return state
    }

    companion object {
        fun toastAuthError(context: Context) {
            Toast.makeText(context, R.string.auth_error, Toast.LENGTH_LONG)
                .show()
        }
    }
}
