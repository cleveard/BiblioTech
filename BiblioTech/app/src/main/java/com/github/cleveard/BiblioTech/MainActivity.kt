package com.github.cleveard.BiblioTech

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import java.io.File
import com.github.cleveard.BiblioTech.db.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"

/**
 * Interface for managing navigation
 */
interface ManageNavigation {
    fun setTitle(title: String?, subtitle: String?)
    fun navigate(action: NavDirections): Boolean
}

/**
 * The main activity for the app
 */
class MainActivity : AppCompatActivity(), ManageNavigation {
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
         * @param classType The java class for the view model to be created.
         */
        fun <T: ViewModel> getViewModel(activity: FragmentActivity?, classType: Class<T>): T {
            return ViewModelProviders.of(activity!!, mViewModelFactory).get(classType)
        }
    }

    /**
     * Navigation action bar configuration
     */
    private lateinit var appBarConfiguration: AppBarConfiguration

    /**
     * Save view name
     */
    private lateinit var viewNames: LiveData<List<String>>

    /**
     * The navigation view
     */
    private lateinit var navView: NavigationView

    /**
     * The navigation controller
     */
    private lateinit var navController: NavController

    /**
     * ID of the last destination we navigated to
     * We use this so suppress navigating to the same destination twice in a row
     */
    private var lastNavId: Int = 0

    /**
     * View name of the last view we navigated to
     * We use this so suppress navigating to the same destination twice in a row
     */
    private var lastNavFilter: String = ""

    /**
     * @inheritDoc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Create the view model factory
        mViewModelFactory = ViewModelProvider.AndroidViewModelFactory(application)

        // Remember the cache directory
        mCache = cacheDir

        // Create the data base
        BookDatabase.initialize(applicationContext)
        BookRepository.initialize(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Setup the action toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup the navigation drawer
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        // Listen to destination changes
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            lastNavId = destination.id
            lastNavFilter = arguments?.getString("filterName")?: ""
        }

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_books, R.id.nav_scan
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        // Handle selection of an item in the navigation drawer
        navView.setNavigationItemSelectedListener {item ->
            val action = when (item.itemId) {
                // This is the id for all saved filters. Setup navigating
                // to the books list with the filter name as argument
                R.id.saved_filters -> MobileNavigationDirections.filterBooks(item.title.toString())
                // This is the id for the book list. Setup navigating
                // to the books list without any argument
                R.id.nav_books -> MobileNavigationDirections.filterBooks()
                // This is the id for the scan destination
                R.id.nav_scan -> MobileNavigationDirections.scanCodes()
                else -> return@setNavigationItemSelectedListener false
            }
            // Try to navigate and close the drawer if we succeed.
            if (navigate(action))
                drawerLayout.closeDrawer(GravityCompat.START)
            false
        }

        // Get the saved filter list and observe changes to it
        MainScope().launch {
            viewNames = BookRepository.repo.getViewNames()
            viewNames.value?.let { updateViews(it) }
            // Update the views in the navigation drawer when it changes
            viewNames.observe(this@MainActivity) {
                updateViews(it)
            }
        }
    }

    /**
     * Update views in the navigation drawer
     * @param viewNames The names of the current saved views
     */
    private fun updateViews(viewNames: List<String>) {
        val menu = navView.menu.findItem(R.id.saved_filters).subMenu
        // Remove the old items
        menu.clear()

        // Add new ones
        for (viewName in viewNames) {
            // Empty view name is used to hold the Books filter
            if (viewName.isNotEmpty()) {
                // Add items not already there
                menu.add(Menu.NONE, R.id.saved_filters, Menu.NONE, viewName).also {
                    // Need isCheckable for the navigation selection listener to work
                    it.isCheckable = true
                    it.isChecked = false
                    it.icon = applicationContext.resources.getDrawable(R.drawable.ic_baseline_filter_alt_24, null)
                }
            }
        }
    }

    /**
     * Navigate to a destination if it is different from the current destination
     * @param action The action and argument for the navigation
     * @return True if the navigation was done
     */
    override fun navigate(action: NavDirections): Boolean {
        val diff = when(action.actionId) {
            // This action is used to navigate to a book list
            // Only navigate if we aren't on a book list, or
            // The list is using a different filter
            R.id.filter_books -> {
                lastNavId != R.id.nav_books ||
                    action.arguments.getString("filterName")?: "" != lastNavFilter
            }
            // This action is used to navigate to the scan destination
            // Only navigate if we aren't on the scan destination
            R.id.scan_codes -> lastNavId != R.id.nav_scan
            else -> true
        }
        // If the current destination is different from the desired, do the navigation
        if (diff)
            navController.navigate(action)
        // Return the result
        return diff
    }

    /**
     * Set the title and subtitle of the current action bar for a book list
     * @param title The title. Default is the title from the strings.xml file
     * @param subtitle The subtitle. Default is ""
     */
    override fun setTitle(title: String?, subtitle: String?) {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = title.let {
            if (it == null || it.isEmpty())
                applicationContext.resources.getString(R.string.menu_books)
            else
                it
        }
        toolbar.subtitle = subtitle?: ""
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
