package com.github.cleveard.bibliotech.ui.widget

import android.app.Activity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Menu provider for logging into and out of Google Books
 * and for revoking access to Google Books
 * @param activity The fragment activity
 * @param scope The view model scope for the fragment
 */
open class LoginMenuProvider(private val activity: Activity, private val scope: CoroutineScope): MenuProvider {
    override fun onPrepareMenu(menu: Menu) {
        // Are we logged in
        val loggedIn = (activity as? BookCredentials)?.isAuthorized == true
        // Set the logout menu item title depending on whether we are logged in or not
        menu.findItem(R.id.action_logout)?.title = if (loggedIn)
            activity.resources.getString(R.string.logout)
        else
            activity.resources.getString(R.string.login)
        // Disable the revoke menu item if we aren't logged in
        menu.findItem(R.id.action_revoke)?.isEnabled = loggedIn
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // Inflate the menu
        menuInflater.inflate(R.menu.login_options, menu)
        if (android.os.Build.VERSION.SDK_INT >= 28)
            menu.setGroupDividerEnabled(true)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_logout,
            R.id.action_revoke -> {
                (activity as? BookCredentials)?.let {credentials ->
                    scope.launch {
                        if (credentials.isAuthorized)
                            credentials.logout(menuItem.itemId == R.id.action_revoke)
                        else
                            credentials.login()
                    }
                }
            }
            else -> return false
        }
        return true
    }
}