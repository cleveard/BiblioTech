package com.example.cleve.bibliotech

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import java.io.File

private lateinit var appBarConfiguration: AppBarConfiguration

class MainActivity : AppCompatActivity(), ShareViewModel {
    companion object Static {
        const val ARG_BOOK_LIST = "bookList"
        const val ARG_POSITION = "position"
        lateinit var cache: File
    }

    private lateinit var mViewModelProvider : ViewModelProvider
    private lateinit var mViewModel: ViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navController = findNavController(R.id.nav_host_fragment)
        val mainView = findViewById<DrawerLayout>(R.id.activity_main)
        appBarConfiguration = AppBarConfiguration(navController.graph, mainView)
        setupActionBarWithNavController(navController, appBarConfiguration)
        findViewById<NavigationView>(R.id.navDrawer)
            .setupWithNavController(navController)

        mViewModelProvider = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))
        mViewModel = mViewModelProvider[ViewModel::class.java]
        mViewModel.initialize(this, savedInstanceState)

        cache = this.cacheDir
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        if (::mViewModelProvider.isInitialized) {
            val data = mViewModelProvider[ViewModel::class.java]
            outState.putString(ARG_BOOK_LIST, data.bookList)
            outState.putInt(ARG_POSITION, data.position)
        }
    }

    override val provider
        get() = mViewModelProvider
}
