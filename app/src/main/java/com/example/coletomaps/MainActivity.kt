package com.example.coletomaps

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.coletomaps.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_logout) {
                drawerLayout.closeDrawers()

                com.example.coletomaps.ui.data.FirebaseManager.auth.signOut()

                val intent = intent
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                startActivity(intent)
                finish()
                true
            } else {
                val handled = androidx.navigation.ui.NavigationUI
                    .onNavDestinationSelected(menuItem, navController)

                if (handled) drawerLayout.closeDrawers()
                handled
            }
        }

        binding.root.post {
            if (!com.example.coletomaps.ui.data.FirebaseManager.isUserLoggedIn()) {
                Log.d("ColetoMapsFirebase", "No hay usuario activo. Redirigiendo...")
                if (navController.currentDestination?.id != R.id.nav_login) {
                    navController.navigate(R.id.nav_login)
                }
            }
        }

        // Botón del auto: mostrar/ocultar rutas
        binding.appBarMain.fab.setOnClickListener { view ->
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)

            val fragmentoActual =
                navHostFragment?.childFragmentManager?.primaryNavigationFragment

            if (fragmentoActual is com.example.coletomaps.ui.MapFragment.MapFragment) {
                fragmentoActual.alternarVisibilidadRutas()
            } else {
                Snackbar.make(view, "Vuelve a la pantalla home", Snackbar.LENGTH_SHORT)
                    .setAnchorView(R.id.fab)
                    .show()
            }
        }

        // Botón verde claro: reportar accidente
        binding.appBarMain.fabReportar.setOnClickListener {
            if (navController.currentDestination?.id != R.id.nav_reportar) {
                navController.navigate(R.id.nav_reportar)
            }
        }

        // Botón rojo: ver reportes existentes
        binding.appBarMain.fabReportes.setOnClickListener {
            Snackbar.make(
                binding.root,
                "Aquí se mostrarán los reportes en el mapa",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_login) {
                supportActionBar?.hide()
                binding.appBarMain.toolbar.visibility = View.GONE
                binding.appBarMain.fab.visibility = View.GONE
                binding.appBarMain.fabReportar.visibility = View.GONE
                binding.appBarMain.fabReportes.visibility = View.GONE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                supportActionBar?.show()
                binding.appBarMain.toolbar.visibility = View.VISIBLE
                binding.appBarMain.fab.visibility = View.VISIBLE
                binding.appBarMain.fabReportar.visibility = View.VISIBLE
                binding.appBarMain.fabReportes.visibility = View.VISIBLE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }

            val searchView =
                binding.appBarMain.toolbar
                    .findViewById<androidx.appcompat.widget.SearchView>(R.id.searchViewRutas)

            searchView?.visibility =
                if (destination.id == R.id.nav_home) View.VISIBLE else View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id == R.id.nav_login) {
                    finishAffinity()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}