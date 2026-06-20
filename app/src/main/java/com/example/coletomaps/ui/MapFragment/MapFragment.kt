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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import androidx.annotation.RequiresPermission
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.widget.SearchView

data class RutaLocal(
    val nombre: String,
    val colorHex: String,
    val puntos: List<LatLng>
)

class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnPolylineClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Uso de MapView dinámico para acoplarse al XML
    private lateinit var mapView: com.google.android.gms.maps.MapView

    private var currentMarker: Marker? = null
    private var camaraInicializada = false

    // Control de estado de las rutas fijas
    private var rutasVisibles = false
    private val listaPolilineas = mutableListOf<Polyline>()
    private var rutaSeleccionada: Polyline? = null

    private lateinit var searchViewRutas: SearchView
    private lateinit var listViewSugerencias: ListView
    private lateinit var adapterSugerencias: ArrayAdapter<String>
    private var nombresRutas = mutableListOf<String>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // SOLUCIÓN: Inflamos el layout correcto que contiene el buscador y el mapa
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Enlazar componentes del buscador
        searchViewRutas = view.findViewById(R.id.searchViewRutas)
        listViewSugerencias = view.findViewById(R.id.listViewSugerencias)

        // Inicializar el mapa integrado de forma segura
        mapView = view.findViewById(R.id.map)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        configurarBuscador()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnPolylineClickListener(this)

        mMap.setOnMapClickListener {
            if (rutaSeleccionada != null) {
                restablecerRutas()
            }
            listViewSugerencias.visibility = View.GONE
            searchViewRutas.clearFocus()
        }

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
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
                startLocationUpdates()
            }
        }
    }

    // Métodos obligatorios requeridos por el ciclo de vida del MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    // --- LOGICA LOCAL DE COLECTIVOS ---

    private fun obtenerRutasLocales(): List<RutaLocal> {
        return listOf(
            RutaLocal(
                nombre = "Línea 1 - Centro / UTA",
                colorHex = "#0000FF",
                puntos = listOf(
                    LatLng(-18.4746, -70.2979),
                    LatLng(-18.4772, -70.2995),
                    LatLng(-18.4815, -70.3012),
                    LatLng(-18.4850, -70.2980)
                )
            ),
            RutaLocal(
                nombre = "Línea 2 - Diego Portales / Chinchorro",
                colorHex = "#FF0000",
                puntos = listOf(
                    LatLng(-18.4746, -70.2979),
                    LatLng(-18.4650, -70.2950),
                    LatLng(-18.4550, -70.2930),
                    LatLng(-18.4480, -70.3010)
                )
            ),
            RutaLocal(
                nombre = "Línea 3 - Saucache / Rotonda",
                colorHex = "#00FF00",
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
            val lineasArica = obtenerRutasLocales()
            for (ruta in lineasArica) {
                val colorParseado = try {
                    Color.parseColor(ruta.colorHex)
                } catch (e: Exception) {
                    Color.BLUE
                }
                val polilinea = mostrarRutaEnMapa(ruta, colorParseado)
                listaPolilineas.add(polilinea)
            }
        } else {
            removerRutasDelMapa()
            searchViewRutas.setQuery("", false)
            listViewSugerencias.visibility = View.GONE
        }
    }

    private fun mostrarRutaEnMapa(ruta: RutaLocal, colorLinea: Int): Polyline {
        val opcionesPolilinea = PolylineOptions()
            .addAll(ruta.puntos)
            .color(colorLinea)
            .width(12f)
            .geodesic(true)

        val polilinea = mMap.addPolyline(opcionesPolilinea)
        polilinea.isClickable = true
        polilinea.tag = ruta
        return polilinea
    }

    private fun removerRutasDelMapa() {
        for (polilinea in listaPolilineas) {
            polilinea.remove()
        }
        listaPolilineas.clear()
        rutaSeleccionada = null
    }

    override fun onPolylineClick(polylineClicked: Polyline) {
        val rutaInfo = polylineClicked.tag as? RutaLocal ?: return

        if (rutaSeleccionada == polylineClicked) {
            restablecerRutas()
        } else {
            rutaSeleccionada = polylineClicked

            for (polyline in listaPolilineas) {
                if (polyline == polylineClicked) {
                    polyline.isVisible = true
                    polyline.width = 24f
                } else {
                    polyline.isVisible = false
                }
            }
            mostrarPopUpInformativo(rutaInfo)
        }
    }

    private fun restablecerRutas() {
        rutaSeleccionada = null
        for (polyline in listaPolilineas) {
            polyline.isVisible = true
            polyline.width = 12f
        }
        searchViewRutas.setQuery("", false)
    }

    private fun mostrarPopUpInformativo(ruta: RutaLocal) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(ruta.nombre)
            .setMessage("Has seleccionado la ruta fija de transporte colectivo.\n\nCódigo de color: ${ruta.colorHex}")
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnCancelListener {
                restablecerRutas()
            }
            .show()
    }

    private fun configurarBuscador() {
        val rutas = obtenerRutasLocales()
        nombresRutas = rutas.map { it.nombre }.toMutableList()

        adapterSugerencias = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            nombresRutas
        )
        listViewSugerencias.adapter = adapterSugerencias

        searchViewRutas.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchViewRutas.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    adapterSugerencias.filter.filter(newText)
                    listViewSugerencias.visibility = View.VISIBLE
                } else {
                    listViewSugerencias.visibility = View.GONE
                }
                return true
            }
        })

        listViewSugerencias.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position) as String
            searchViewRutas.setQuery(nombreSeleccionado, false)
            listViewSugerencias.visibility = View.GONE
            searchViewRutas.clearFocus()

            seleccionarRutaPorNombre(nombreSeleccionado)
        }
    }

    private fun seleccionarRutaPorNombre(nombre: String) {
        if (!rutasVisibles) {
            alternarVisibilidadRutas()
        }

        val polilineaEncontrada = listaPolilineas.find {
            val info = it.tag as? RutaLocal
            info?.nombre == nombre
        }

        if (polilineaEncontrada != null) {
            onPolylineClick(polilineaEncontrada)

            val infoRuta = polilineaEncontrada.tag as? RutaLocal
            infoRuta?.puntos?.firstOrNull()?.let { primerPunto ->
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(primerPunto, 15f))
            }
        }
    }
}