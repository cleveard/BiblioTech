package com.github.cleveard.bibliotech

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Make sure this is before calling super.onCreate
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MainActivity::class.java)
        // Start the main activity
        startActivity(intent)
        finish()
    }
}