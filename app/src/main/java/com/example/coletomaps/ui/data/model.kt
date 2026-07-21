package com.example.coletomaps.ui.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp

// Modelo para cada línea de colectivo (Ej: Línea 1, Línea 2, etc.)
data class LineaColectivo(
    val id: String,
    val nombre: String,        // Ej: "Línea 1" o "Línea 4"
    val tarifaDiurna: Int,     // Para cumplir más adelante con la HU de Tarifas
    val tarifaNocturna: Int,
    val rutaIda: List<LatLng>, // Lista de coordenadas que forman el camino de ida
    val rutaVuelta: List<LatLng> // Lista de coordenadas de vuelta
)
data class ReporteIncidente(
    val id: String = "",
    val userId: String = "",
    val tipoIncidente: String = "",
    val descripcion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val calle: String = "",            // 👈 NUEVO CAMPO: Ej: "Av. Diego Portales" o "Calle 18 de Septiembre"
    val hora: String = "",
    val fechaCreacion: Timestamp = Timestamp.now(),
    val votosPositivos: Int = 0,
    val votosNegativos: Int = 0,
    val usuariosVotantes: List<String> = emptyList(),
    val activo: Boolean = true
)