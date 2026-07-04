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
// Modelo para los reportes comunitarios en el mapa
data class ReporteIncidente(
    val id: String = "",
    val tipoIncidente: String = "",  // "incendio", "congestión", "accidente vehicular", "corte"
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val hora: String = "",           // hh:mm
    val votosPositivos: Int = 0,
    val votosNegativos: Int = 0,
    val activo: Boolean = true
)