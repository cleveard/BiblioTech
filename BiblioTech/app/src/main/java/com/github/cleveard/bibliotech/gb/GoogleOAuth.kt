package com.github.cleveard.bibliotech.gb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.github.cleveard.bibliotech.LaunchIntentForResult
import com.github.cleveard.bibliotech.annotations.EnvironmentValues
import com.github.cleveard.bibliotech.utils.OAuth
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.openid.appauth.*
import java.lang.Exception
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
