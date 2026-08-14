package com.github.cleveard.bibliotech.ui.widget

import android.app.Activity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.navigation.NavDirections
import com.github.cleveard.bibliotech.ManageNavigation
import com.github.cleveard.bibliotech.R

/**
 * Menu provider for navigation to settings
 * @param activity The fragment activity
 * @param to The NavDirections from the current fragment to the settings fragment
 */
class SettingsMenuProvider(private val activity: Activity, private val to: NavDirections): MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // Inflate the setting menu
        menuInflater.inflate(R.menu.settings_option, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        // Direct action to onActionSelected
        return when(menuItem.itemId) {
            R.id.action_to_settingsFragment -> {
                (activity as? ManageNavigation)?.navigate(to)
                true
            }
            else -> false
        }
    }
}