package com.example.coletomaps

import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.coletomaps.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout

        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        //pruba de firebase
        val estaConectado = com.example.coletomaps.ui.data.FirebaseManager.isUserLoggedIn()
        if (!estaConectado) {
            // Aquí en el futuro redireccionaremos a la pantalla de Login (HU01)
            android.util.Log.d("ColetoMapsFirebase", "Firebase listo. No hay usuario activo todavía.")
        }
        //login
        if (!com.example.coletomaps.ui.data.FirebaseManager.isUserLoggedIn()) {
            navController.navigate(R.id.nav_login) // Asegúrate de agregar el id "nav_login" en mi_nav_graph.xml
        }
        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_logout) { // ID que puedes asignar en tu archivo de menú lateral
                // 1. Cerramos sesión en Firebase de forma segura
                com.example.coletomaps.ui.data.FirebaseManager.auth.signOut()

                // 2. Redirigimos al Login
                navController.navigate(R.id.nav_login)

                // 3. Cerramos el panel lateral de la interfaz
                drawerLayout.closeDrawers()
                true
            } else {
                // Deja que el componente de navegación maneje los otros clics normales
                val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                if (handled) drawerLayout.closeDrawers()
                handled
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

}