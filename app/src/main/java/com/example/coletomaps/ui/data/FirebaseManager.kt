package com.example.coletomaps.ui.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {

    // Instancia para controlar el inicio de sesión y registro (HU01)
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    // Instancia para controlar los datos del mapa y reportes en el futuro
    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Función de prueba para verificar si hay un usuario conectado
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}