package com.example.classmatetaskshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.classmatetaskshare.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // 1. Mark R.id.nav_gallery (Reminder List) as the top-level destination.
        // This naturally removes the back arrow from that page.
        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_gallery))

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // 2. Add a listener to control exactly when the icon is visible.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_gallery) {
                // Remove both hamburger and arrow on the Reminder List page
                binding.appBarMain.toolbar.navigationIcon = null
            } else {
                // Let the back arrow appear for Profile and Create Task pages
                // This ensures you can still click "Back" to return to the list
            }
        }
    }

    // Keep onCreateOptionsMenu REMOVED to hide triple-dots

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}