package com.example.coletomaps.ui.MapFragment
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.coletomaps.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import android.graphics.Color


class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentMarker: Marker? = null
    private var camaraInicializada = false
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
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //val location = LatLng(-18.4746, -70.2979) // Arica, Chile
        //mMap.addMarker(MarkerOptions().position(location).title("Mi ubicación"))
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            mMap.isMyLocationEnabled = true
            startLocationUpdates()
            cargarRutasDePrueba()


        } else {

            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun startLocationUpdates() {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult) {

                val location = locationResult.lastLocation ?: return

                val currentPosition = LatLng(
                    location.latitude,
                    location.longitude
                )

                currentMarker?.remove()

                currentMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(currentPosition)
                        .title("Mi ubicación")
                )

                if(!camaraInicializada){
                    mMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            currentPosition,
                            17f
                        )
                    )
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

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

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

    private fun mostrarRutaEnMapa(coordenadas: List<LatLng>, colorLinea: Int): Polyline {
        val opcionesPolilinea = PolylineOptions()
            .addAll(coordenadas) // Añade todos los puntos de la ruta de una
            .color(colorLinea)   // Asigna el color (ej: azul, rojo)
            .width(12f)          // Grosor de la línea en el mapa
            .geodesic(true)      // Hace que la línea siga la curvatura de la tierra

        return mMap.addPolyline(opcionesPolilinea)
    }
    private fun cargarRutasDePrueba() {
        // Coordenadas simuladas para una línea de colectivo en Arica
        val puntosLinea1 = listOf(
            LatLng(-18.4746, -70.2979), // Centro / Plaza Colón
            LatLng(-18.4772, -70.2995), // Patricio Lynch
            LatLng(-18.4815, -70.3012), // Av. Vicuña Mackenna
            LatLng(-18.4850, -70.2980)  // Cerca del Campus Velásquez / UTA
        )

        // Llamamos a nuestra funciónadora usando color Azul
        mostrarRutaEnMapa(puntosLinea1, Color.BLUE)
    }


}