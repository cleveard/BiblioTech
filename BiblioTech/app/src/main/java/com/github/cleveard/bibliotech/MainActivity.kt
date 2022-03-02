package com.github.cleveard.bibliotech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.coroutineScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import java.io.File
import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.gb.GoogleBooksOAuth
import com.github.cleveard.bibliotech.ui.books.BooksFragmentDirections
import com.github.cleveard.bibliotech.ui.scan.ScanFragmentDirections
import com.github.cleveard.bibliotech.utils.OAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"

/** Channel to queue launch requests */
private val requestChannel: Channel<Pair<Intent,(Intent?)-> Unit>> = Channel()
/** Channel used to return result */
private val resultChannel: Channel<Intent?> = Channel()

/**
 * Interface for managing navigation
 */
interface ManageNavigation {
    /**
     * Set the title and subtitle on the navigation bar
     * @param title The title
     * @param subtitle The optional subtitle
     */
    fun setTitle(title: String?, subtitle: String?)

    /**
     * Navigate to a destination
     * @param action The destination to navigate to
     * @return True if the navigation succeeded
     */
    fun navigate(action: NavDirections): Boolean
}

/**
 * Interface for handling credentials
 */
interface BookCredentials {
    /**
     * Authorize the credentials
     * @return True if the credentials are authorized
     */
    suspend fun login(): Boolean

    /**
     * Remove the credential authorization
     * @return True if the credentials are authorized
     */
    suspend fun logout(): Boolean

    /**
     * Run an action with a the access token for the credentials
     * @param action The action to run, whose only argument is the access token
     * @return The return value of the action
     */
    suspend fun <T> execute(action: suspend (token:String?) -> T): T

    /** True if the credentials are authorized */
    val isAuthorized: Boolean
}

private var googleBooksAuth: OAuth? = null

/**
 * The main activity for the app
 */
class MainActivity : AppCompatActivity(), ManageNavigation, BookCredentials {
    companion object {
        /** Url to the  BiblioTech help web page */
        private const val HELP_URL: String = "https://cleveard.github.io/BiblioTech/help/"

        // The File to the app cache directory. Used to save thumbnails
        private var mCache: File? = null

        /**
         * File to the application cache directory.
         * Use to save thumbnails
         */
        val cache: File?
            get() = mCache
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

    /** Launcher to start an activity using an intent and return an intent result */
    private val launcher: ActivityResultLauncher<Intent> =
        registerForActivityResult(object: ActivityResultContract<Intent, Intent?>() {
            override fun createIntent(context: Context, input: Intent): Intent {
                return input
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
                return intent
            }
        }) {
            MainScope().launch {
                resultChannel.send(it)
            }
        }

    /**
     * @inheritDoc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Create the credentials
        googleBooksAuth = googleBooksAuth?: GoogleBooksOAuth(application)

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
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        // Listen to destination changes
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            lastNavId = destination.id
            lastNavFilter = arguments?.getString("filterName")?: ""
            navView.menu.findItem(R.id.action_nav_books_to_exportImportFragment).isEnabled =
                lastNavId == R.id.nav_books
            navView.menu.findItem(R.id.action_to_settingsFragment).isEnabled =
                lastNavId == R.id.nav_books || lastNavId == R.id.nav_scan
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
                R.id.action_nav_books_to_exportImportFragment -> {
                    if (lastNavId != R.id.nav_books)
                        return@setNavigationItemSelectedListener false
                    BooksFragmentDirections.actionNavBooksToExportImportFragment(lastNavFilter)
                }
                R.id.action_to_settingsFragment -> {
                    when (lastNavId) {
                        R.id.nav_books -> BooksFragmentDirections.actionNavBooksToSettingsFragment()
                        R.id.nav_scan -> ScanFragmentDirections.actionNavScanToSettingsFragment()
                        else -> return@setNavigationItemSelectedListener false
                    }
                }
                R.id.action_help -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL)))
                    return@setNavigationItemSelectedListener true
                }
                else -> return@setNavigationItemSelectedListener false
            }
            // Try to navigate and close the drawer if we succeed.
            val navigated = navigate(action)
            if (navigated)
                drawerLayout.closeDrawer(GravityCompat.START)
            navigated
        }

        // Get the saved filter list and observe changes to it
        viewNames = BookRepository.repo.getViewNames()
        viewNames.value?.let { updateViews(it) }
        // Update the views in the navigation drawer when it changes
        viewNames.observe(this@MainActivity) {
            updateViews(it)
        }

        // Create a job tied to the activity lifecycle that
        // processes the intent launch requests
        lifecycle.coroutineScope.launch {
            flow {
                for (r in requestChannel)
                    emit(r)
            }.collect {request ->
                launcher.launch(request.first)
                val result = resultChannel.receive()
                request.second(result)
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
                    it.icon = ResourcesCompat.getDrawable(applicationContext.resources,
                        R.drawable.ic_baseline_filter_alt_24, null)
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

    override suspend fun login(): Boolean {
        return (googleBooksAuth?.login {intent ->
            val returnValue = CompletableDeferred<Intent?>()
            requestChannel.send(Pair(intent) { result ->
                returnValue.completeWith(Result.success(result))
            })
            returnValue.await()
        })?: false
    }

    override suspend fun logout(): Boolean {
        return googleBooksAuth?.logout()?: false
    }

    @Throws(OAuth.AuthException::class)
    override suspend fun <T> execute(action: suspend (token: String?) -> T): T {
        if (!isAuthorized)
            throw OAuth.AuthException("execute: Not authorized", googleBooksAuth?.authorizationException)

        try {
            return googleBooksAuth!!.execute(action)
        } catch (e: OAuth.AuthException) {
            logout()
        }

        if (!login())
            throw OAuth.AuthException("Token refresh failed", googleBooksAuth?.authorizationException)
        return googleBooksAuth!!.execute(action)
    }

    override val isAuthorized: Boolean
        get() = googleBooksAuth?.isAuthorized == true
}
