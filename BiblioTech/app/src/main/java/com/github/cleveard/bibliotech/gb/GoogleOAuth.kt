package com.github.cleveard.bibliotech.gb

import android.content.Context
import com.github.cleveard.bibliotech.BuildConfig
import com.github.cleveard.bibliotech.utils.OAuth
import net.openid.appauth.*

private const val oauthKey = "com.github.cleveard.bibliotech.GOOGLE_BOOKS_OAUTH_ID"
private const val oauthScheme = "com.github.cleveard.bibliotech.GOOGLE_BOOKS_REDIRECT_SCHEME"

class GoogleBooksOAuth(context: Context): OAuth(
    context,
    "https://accounts.google.com/o/oauth2/v2/auth",
    "https://oauth2.googleapis.com/token",
    BuildConfig.GOOGLE_BOOKS_OAUTH_ID,
    "${BuildConfig.GOOGLE_BOOKS_REDIRECT_SCHEME}:/oauth2redirect/}",
    "googleBooksAuthState"
) {
    override fun AuthorizationRequest.Builder.setupRequest() {
        setScope("https://www.googleapis.com/auth/books")
    }
}
