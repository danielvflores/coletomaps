package com.example.coletomaps.ui.MapFragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.coletomaps.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import androidx.annotation.RequiresPermission

// Estructura simple para modelar tus líneas locales
data class RutaLocal(
    val nombre: String,
    val colorHex: String,
    val puntos: List<LatLng>
)

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentMarker: Marker? = null
    private var camaraInicializada = false

    // Control de estado de las rutas fijas
    private var rutasVisibles = false
    private val listaPolilineas = mutableListOf<Polyline>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.frament_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val currentPosition = LatLng(location.latitude, location.longitude)

                currentMarker?.remove()
                currentMarker = mMap.addMarker(
                    MarkerOptions().position(currentPosition).title("Mi ubicación")
                )

                if (!camaraInicializada) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 17f))
                    camaraInicializada = true
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // --- LOGICA LOCAL DE COLECTIVOS ---

    // Definición de tus 3 arreglos de rutas fijas directamente en el código
    private fun obtenerRutasLocales(): List<RutaLocal> {
        return listOf(
            RutaLocal(
                nombre = "Línea 1 - Centro / UTA",
                colorHex = "#0000FF", // Azul
                puntos = listOf(
                    LatLng(-18.4746, -70.2979),
                    LatLng(-18.4772, -70.2995),
                    LatLng(-18.4815, -70.3012),
                    LatLng(-18.4850, -70.2980)
                )
            ),
            RutaLocal(
                nombre = "Línea 2 - Diego Portales / Chinchorro",
                colorHex = "#FF0000", // Rojo
                puntos = listOf(
                    LatLng(-18.4746, -70.2979),
                    LatLng(-18.4650, -70.2950),
                    LatLng(-18.4550, -70.2930),
                    LatLng(-18.4480, -70.3010)
                )
            ),
            RutaLocal(
                nombre = "Línea 3 - Saucache / Rotonda",
                colorHex = "#00FF00", // Verde
                puntos = listOf(
                    LatLng(-18.4746, -70.2979),
                    LatLng(-18.4820, -70.2890),
                    LatLng(-18.4880, -70.2850),
                    LatLng(-18.4910, -70.2790)
                )
            )
        )
    }

    fun alternarVisibilidadRutas() {
        rutasVisibles = !rutasVisibles

        if (rutasVisibles) {
            // Muestra instantáneamente los datos de los arreglos locales
            val lineasArica = obtenerRutasLocales()
            for (ruta in lineasArica) {
                val colorParseado = try {
                    Color.parseColor(ruta.colorHex)
                } catch (e: Exception) {
                    Color.BLUE
                }

                val polilinea = mostrarRutaEnMapa(ruta.puntos, colorParseado)
                listaPolilineas.add(polilinea)
            }
        } else {
            // Remueve las políneas locales
            removerRutasDelMapa()
        }
    }

    private fun mostrarRutaEnMapa(coordenadas: List<LatLng>, colorLinea: Int): Polyline {
        val opcionesPolilinea = PolylineOptions()
            .addAll(coordenadas)
            .color(colorLinea)
            .width(12f)
            .geodesic(true)

        return mMap.addPolyline(opcionesPolilinea)
    }

    private fun removerRutasDelMapa() {
        for (polilinea in listaPolilineas) {
            polilinea.remove()
        }
        listaPolilineas.clear()
    }
}