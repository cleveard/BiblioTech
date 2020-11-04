package com.example.cleve.bibliotech

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import com.example.cleve.bibliotech.db.*

const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"
class MainActivity : AppCompatActivity() {
    companion object {
        // The File to the app cache directory. Used to save thumbnails
        private lateinit var mCache: File

        /**
         * File to the application cache directory.
         * Use to save thumbnails
         */
        val cache: File
            get() = mCache

        // Factory used to create AndroidViewModel view models
        private lateinit var mViewModelFactory: ViewModelProvider.Factory

        /**
         * Create an AndroidViewModel view model for a fragment
         * @param activity The fragments activity
         * @param classType The java class for the viewmodel to be created.
         */
        fun <T: ViewModel> getViewModel(activity: FragmentActivity?, classType: Class<T>): T {
            return ViewModelProviders.of(activity!!, mViewModelFactory).get(classType)
        }
    }

    private lateinit var appBarConfiguration: AppBarConfiguration

    /**
     * @inheritDoc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Create the view model factory
        mViewModelFactory = ViewModelProvider.AndroidViewModelFactory(application)

        // Create the data base
        BookDatabase.initialize(applicationContext)
        BookRepository.initialize(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup the action toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup the navigation drawer
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_books, R.id.nav_scan
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Remember the cache directory
        mCache = cacheDir
    }

    /**
     * @inheritDoc
     */
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * @inheritDoc
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Handle Volume up or down key and send a local intent.
        // The Scan fragment listens and starts scanning for bar codes
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val intent =
                    Intent(KEY_EVENT_ACTION).apply { putExtra(KEY_EVENT_EXTRA, keyCode) }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }
}
