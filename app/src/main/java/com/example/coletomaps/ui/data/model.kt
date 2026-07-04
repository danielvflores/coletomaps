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
    val userId: String = "",           // ID del usuario que creó el reporte
    val tipoIncidente: String = "",    // "incendio", "congestión", "accidente vehicular", "corte"
    val descripcion: String = "",      // Breve descripción añadida por el usuario
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val hora: String = "",             // hh:mm para visual rápida
    val fechaCreacion: Timestamp = Timestamp.now(), // Para calcular la expiración exacta
    val votosPositivos: Int = 0,
    val votosNegativos: Int = 0,
    val usuariosVotantes: List<String> = emptyList(), // Lista de IDs de usuarios que ya votaron
    val activo: Boolean = true
)