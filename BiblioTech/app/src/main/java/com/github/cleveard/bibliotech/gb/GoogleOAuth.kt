package com.github.cleveard.bibliotech.gb

import android.content.Context
import com.github.cleveard.bibliotech.annotations.EnvironmentValues
import com.github.cleveard.bibliotech.utils.OAuth
import net.openid.appauth.*

private const val oauthKey = "GOOGLE_BOOKS_OAUTH_ID"
private const val oauthScheme = "GOOGLE_BOOKS_REDIRECT_SCHEME"

@EnvironmentValues(oauthKey, oauthScheme)
class GoogleBooksOAuth(context: Context): OAuth(
    context,
    "https://accounts.google.com/o/oauth2/v2/auth",
    "https://oauth2.googleapis.com/token",
    GoogleBooksOAuth_Environment[oauthKey]!!,
    "${GoogleBooksOAuth_Environment[oauthScheme]!!}:/oauth2redirect/",
    "googleBooksAuthState"
) {
    override fun AuthorizationRequest.Builder.setupRequest() {
        setScope("https://www.googleapis.com/auth/books")
    }
}
