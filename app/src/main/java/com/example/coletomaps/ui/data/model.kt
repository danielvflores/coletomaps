package com.example.coletomaps.ui.data

import com.google.android.gms.maps.model.LatLng

// Modelo para cada línea de colectivo (Ej: Línea 1, Línea 2, etc.)
data class LineaColectivo(
    val id: String,
    val nombre: String,        // Ej: "Línea 1" o "Línea 4"
    val tarifaDiurna: Int,     // Para cumplir más adelante con la HU de Tarifas
    val tarifaNocturna: Int,
    val rutaIda: List<LatLng>, // Lista de coordenadas que forman el camino de ida
    val rutaVuelta: List<LatLng> // Lista de coordenadas de vuelta
)
