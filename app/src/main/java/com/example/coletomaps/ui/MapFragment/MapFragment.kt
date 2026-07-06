package com.example.coletomaps.ui.MapFragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

data class RutaLocal(
    val nombre: String,
    val colorHex: String,
    val puntos: List<LatLng>,
    val valorDiurno: String,
    val valorTarde: String,
    val valorNoche: String,
    val callesRecorrido: String
)

class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnPolylineClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var mapView: com.google.android.gms.maps.MapView

    // Variable de caché para evitar que el mapa se destruya y recargue al cambiar de ventana
    private var rootViews: View? = null

    private var currentMarker: Marker? = null
    private var camaraInicializada = false

    // Almacena la última posición conocida del GPS para re-centrar al cambiar de ventana
    private var ultimaPosicionGPS: LatLng? = null

    private var rutasVisibles = false
    private val listaPolilineas = mutableListOf<Polyline>()
    private var rutaSeleccionada: Polyline? = null

    private lateinit var searchViewRutas: SearchView
    private lateinit var listViewSugerencias: ListView
    private lateinit var adapterSugerencias: ArrayAdapter<String>
    private var nombresRutas = mutableListOf<String>()

    private lateinit var cardInfoRuta: CardView
    private lateinit var txtNombreRuta: TextView
    private lateinit var txtDiurno: TextView
    private lateinit var txtTarde: TextView
    private lateinit var txtNoche: TextView
    private lateinit var txtRecorrido: TextView
    private val listaMarcadoresReportes = mutableListOf<Marker>()
    private var reportesVisibles = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (rootViews == null) {
            rootViews = inflater.inflate(R.layout.fragment_home, container, false)
        }
        return rootViews
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        listViewSugerencias = view.findViewById(R.id.listViewSugerencias)
        cardInfoRuta = view.findViewById(R.id.cardInfoRuta)
        txtNombreRuta = view.findViewById(R.id.txtNombreRuta)
        txtDiurno = view.findViewById(R.id.txtDiurno)
        txtTarde = view.findViewById(R.id.txtTarde)
        txtNoche = view.findViewById(R.id.txtNoche)
        txtRecorrido = view.findViewById(R.id.txtRecorrido)
        val fabReportes = (activity as? com.example.coletomaps.MainActivity)?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabReportes)

        fabReportes?.setOnClickListener {
            alternarVisibilidadReportes()
        }

        mapView = view.findViewById(R.id.map)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        val viewBuscador = requireActivity().findViewById<SearchView>(R.id.searchViewRutas)
        if (viewBuscador != null && viewBuscador.visibility == View.VISIBLE) {
            searchViewRutas = viewBuscador
            configurarBuscador()
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnPolylineClickListener(this)

        val limitesArica = LatLngBounds(
            LatLng(-18.5360, -70.3550),
            LatLng(-18.4250, -70.2600)
        )
        mMap.setLatLngBoundsForCameraTarget(limitesArica)
        mMap.setMinZoomPreference(12.0f)

        mMap?.setOnMarkerClickListener { marker ->
            val reporteId = marker.tag as? String

            // Si el marcador tiene un ID de reporte asignado, lanzamos la validación
            if (reporteId != null) {
                mostrarDialogoValidacionComunitaria(reporteId, marker.title ?: "Incidente")
                true // Indica que nosotros manejamos el clic
            } else {
                false // Deja que Google Maps maneje marcadores normales de la app
            }
        }

        // SOLUCIÓN 2: Si volvemos de otra pantalla y tenemos la última posición guardada, centramos de inmediato
        if (ultimaPosicionGPS != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ultimaPosicionGPS!!, 15f))
        } else if (!camaraInicializada) {
            val centroArica = LatLng(-18.4783, -70.3126)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centroArica, 14f))
        }

        // SOLUCIÓN 1: Limpieza total al presionar cualquier parte libre del mapa
        mMap.setOnMapClickListener {
            if (rutaSeleccionada != null) {
                restablecerRutas()
            }
            listViewSugerencias.visibility = View.GONE
            if (::searchViewRutas.isInitialized) {
                searchViewRutas.setQuery("", false) // Borra el texto escrito
                searchViewRutas.clearFocus()        // Cierra el teclado
            }
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

                // Guardamos la coordenada en memoria
                ultimaPosicionGPS = currentPosition

                currentMarker?.remove()
                currentMarker = mMap.addMarker(
                    MarkerOptions().position(currentPosition).title("Mi ubicación")
                )

                if (!camaraInicializada) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 15f))
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

    // --- LÓGICA DE TRANSPORTE COLECTIVO ---

    private fun obtenerRutasLocales(): List<RutaLocal> {
        return listOf(
            RutaLocal(
                nombre = "Línea 1", colorHex = "#0000FF",
                puntos = listOf(LatLng(-18.4746, -70.2979), LatLng(-18.4772, -70.2995), LatLng(-18.4815, -70.3012), LatLng(-18.4850, -70.2980)),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1200",
                callesRecorrido = "18 de Septiembre, General Velásquez, UTA Saucache, Centro."
            ),
            RutaLocal(
                nombre = "Línea 2", colorHex = "#FF0000",
                puntos = listOf(LatLng(-18.4746, -70.2979), LatLng(-18.4650, -70.2950), LatLng(-18.4550, -70.2930), LatLng(-18.4480, -70.3010)),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1200",
                callesRecorrido = "Diego Portales, Av. Chile, Playa Chinchorro."
            ),
            RutaLocal(
                nombre = "Línea 3", colorHex = "#00FF00",
                puntos = listOf(
                    LatLng(-18.4567128, -70.2808515), LatLng(-18.4588226, -70.2804647), LatLng(-18.4641428, -70.2814317),
                    LatLng(-18.4700133, -70.282012), LatLng(-18.4768007, -70.2827856), LatLng(-18.4847802, -70.2845263),
                    LatLng(-18.4881647, -70.2853984), LatLng(-18.4890175, -70.2859444), LatLng(-18.4899008, -70.2873897),
                    LatLng(-18.4922206, -70.288571), LatLng(-18.492314, -70.2899499), LatLng(-18.4930613, -70.2930034),
                    LatLng(-18.493435, -70.2955644), LatLng(-18.4905392, -70.3000954), LatLng(-18.4943691, -70.3035428),
                    LatLng(-18.4968652, -70.3049213), LatLng(-18.5003115, -70.308806)
                ),
                valorDiurno = "$900", valorTarde = "$1000", valorNoche = "$1100",
                callesRecorrido = "Saucache, Azolas, Rotonda Manuel Castillo."
            ),
            RutaLocal(
                nombre = "Línea 4", colorHex = "#24A4AB",
                puntos = listOf(LatLng(-18.4567128, -70.2808515), LatLng(-18.4588226, -70.2804647), LatLng(-18.4641428, -70.2814317), LatLng(-18.4700133, -70.282012), LatLng(-18.4768007, -70.2827856), LatLng(-18.4847802, -70.2845263), LatLng(-18.4881647, -70.2853984)),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1300",
                callesRecorrido = "Capitán Ávalos, Valente Rossi, Linderos."
            ),
            RutaLocal(
                nombre = "Línea U", colorHex = "#9224AB",
                puntos = listOf(
                    LatLng(-18.4985603, -70.2860866), LatLng(-18.4932157, -70.2887665), LatLng(-18.492356, -70.2887862),
                    LatLng(-18.4916784, -70.2884114), LatLng(-18.4912112, -70.2895705), LatLng(-18.4920148, -70.2902405),
                    LatLng(-18.4931173, -70.2925263), LatLng(-18.4936593, -70.2924475), LatLng(-18.4950422, -70.2920533),
                    LatLng(-18.4956215, -70.2920928), LatLng(-18.4972099, -70.2930386), LatLng(-18.4986797, -70.2938012),
                    LatLng(-18.4994571, -70.2946209), LatLng(-18.4987352, -70.2952357), LatLng(-18.4976524, -70.2973144),
                    LatLng(-18.4956812, -70.2982219), LatLng(-18.4947094, -70.2983976), LatLng(-18.4929603, -70.2968459),
                    LatLng(-18.4919607, -70.296319), LatLng(-18.4905725, -70.2954407), LatLng(-18.4881847, -70.293889),
                    LatLng(-18.487907, -70.2937427), LatLng(-18.4866277, -70.2977484), LatLng(-18.4851479, -70.302598),
                    LatLng(-18.484148, -70.3069416), LatLng(-18.4836009, -70.3102293), LatLng(-18.4825034, -70.3129089),
                    LatLng(-18.4803152, -70.3157353), LatLng(-18.477744, -70.3181002), LatLng(-18.4768687, -70.3189654),
                    LatLng(-18.4754463, -70.3160813), LatLng(-18.4735315, -70.3137165), LatLng(-18.4710149, -70.3112939),
                    LatLng(-18.4710149, -70.3103133), LatLng(-18.4735315, -70.3069102), LatLng(-18.4775799, -70.3032763),
                    LatLng(-18.4773063, -70.3017766), LatLng(-18.4762669, -70.3009114), LatLng(-18.475501, -70.3006807),
                    LatLng(-18.4693302, -70.2952051), LatLng(-18.469526, -70.2952807)
                ),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1000",
                callesRecorrido = "Edmundo Flores, Tucapel,Centro, 18 de Septiembre."
            )
        )
    }

    fun alternarVisibilidadRutas() {
        if (!::searchViewRutas.isInitialized) return
        rutasVisibles = !rutasVisibles

        if (rutasVisibles) {
            val lineasArica = obtenerRutasLocales()
            for (ruta in lineasArica) {
                val colorParseado = try { Color.parseColor(ruta.colorHex) } catch (e: Exception) { Color.BLUE }
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
        cardInfoRuta.visibility = View.GONE
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

            txtNombreRuta.text = rutaInfo.nombre
            txtDiurno.text = "Diurno: ${rutaInfo.valorDiurno}"
            txtTarde.text = "Tarde: ${rutaInfo.valorTarde}"
            txtNoche.text = "Noche: ${rutaInfo.valorNoche}"
            txtRecorrido.text = rutaInfo.callesRecorrido

            cardInfoRuta.visibility = View.VISIBLE
        }
    }

    private fun restablecerRutas() {
        rutaSeleccionada = null
        cardInfoRuta.visibility = View.GONE

        for (polyline in listaPolilineas) {
            polyline.isVisible = true
            polyline.width = 12f
        }
    }

    private fun configurarBuscador() {
        val rutas = obtenerRutasLocales()
        nombresRutas = rutas.map { "${it.nombre} \n(${it.callesRecorrido})" }.toMutableList()

        adapterSugerencias = ArrayAdapter(
            requireContext(),
            R.layout.item_ruta,
            R.id.textRuta,
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
                    if (rutaSeleccionada != null) {
                        restablecerRutas()
                    }
                }
                return true
            }
        })

        searchViewRutas.setOnCloseListener {
            restablecerRutas()
            listViewSugerencias.visibility = View.GONE
            false
        }

        listViewSugerencias.setOnItemClickListener { parent, _, position, _ ->
            val itemSeleccionado = parent.getItemAtPosition(position) as String
            val nombreLimpio = itemSeleccionado.substringBefore("\n").trim()

            searchViewRutas.setQuery(nombreLimpio, false)
            listViewSugerencias.visibility = View.GONE
            searchViewRutas.clearFocus()

            seleccionarRutaPorNombre(nombreLimpio)
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
    private fun alternarVisibilidadReportes() {
        reportesVisibles = !reportesVisibles
        if (reportesVisibles) {
            escucharReportesEnTiempoReal()
            android.widget.Toast.makeText(requireContext(), "Mostrando reportes comunitarios", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            limpiarMarcadoresReportes()
            android.widget.Toast.makeText(requireContext(), "Reportes ocultados", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun escucharReportesEnTiempoReal() {
        // 1. Calculamos el límite de tiempo: 2 horas atrás desde este instante
        val dosHorasEnSegundos = 2 * 60 * 60
        val tiempoLimiteExpiracion = com.google.firebase.Timestamp(
            com.google.firebase.Timestamp.now().seconds - dosHorasEnSegundos, 0
        )

        // 2. Modificamos la consulta inicial agregando el filtro whereGreaterThan
        com.example.coletomaps.ui.data.FirebaseManager.db.collection("reportes")
            .whereEqualTo("activo", true)
            .whereGreaterThan("fechaCreacion", tiempoLimiteExpiracion) // Solo lo fresco de las últimas 2 horas
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) {
                    android.util.Log.w("ColetoMaps", "Error al escuchar reportes", e)
                    return@addSnapshotListener
                }

                // Cada vez que cambie algo en la base de datos fresca, refrescamos los marcadores
                limpiarMarcadoresReportes()

                for (document in snapshots) {
                    val id = document.getString("id") ?: ""
                    val tipo = document.getString("tipoIncidente") ?: ""
                    val lat = document.getDouble("latitud") ?: 0.0
                    val lng = document.getDouble("longitud") ?: 0.0
                    val hora = document.getString("hora") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val votosPos = document.getLong("votosPositivos")?.toInt() ?: 0
                    val votosNeg = document.getLong("votosNegativos")?.toInt() ?: 0
                    val usuariosVotantes = document.get("usuariosVotantes") as? List<String> ?: emptyList()

                    val posicion = LatLng(lat, lng)

                    // 3. Definir el ícono y su color de fondo correspondiente
                    val (recursoIcono, colorFondo) = when (tipo.lowercase()) {
                        "incendio" -> Pair(R.drawable.ic_incendio, android.graphics.Color.parseColor("#FF6D00")) // Naranja vibrante
                        "congestión" -> Pair(R.drawable.ic_congestion, android.graphics.Color.parseColor("#2962FF")) // Azul rey
                        "accidente vehicular" -> Pair(R.drawable.ic_accidente, android.graphics.Color.parseColor("#D50000")) // Rojo alerta
                        "corte de calle" -> Pair(R.drawable.ic_corte, android.graphics.Color.parseColor("#00C853")) // Verde oscuro / o Gris oscuro "#37474F"
                        else -> Pair(R.drawable.ic_accidente, android.graphics.Color.RED)
                    }

// 4. Transformar mandando el contexto, el ícono y el color elegido
                    val miIconoPersonalizado = bitmapDescriptorFromVector(requireContext(), recursoIcono, colorFondo)

                    val markerOptions = MarkerOptions()
                        .position(posicion)
                        .title(tipo.uppercase())
                        .snippet(if (descripcion.isNotEmpty()) "$descripcion\nHora: $hora" else "Hora: $hora")

                    // 5. Asignar el ícono pro si se renderizó bien, o dejar el pin de siempre si falla
                    if (miIconoPersonalizado != null) {
                        markerOptions.icon(miIconoPersonalizado)
                    } else {
                        markerOptions.icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED))
                    }

                    val marker = mMap?.addMarker(markerOptions)
                    if (marker != null) {
                        marker.tag = id
                        listaMarcadoresReportes.add(marker)
                    }
                }
            }

    }

    private fun limpiarMarcadoresReportes() {
        for (marker in listaMarcadoresReportes) {
            marker.remove()
        }
        listaMarcadoresReportes.clear()
    }
    private fun mostrarDialogoValidacionComunitaria(reporteId: String, tipoIncidente: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"
        val docRef = com.example.coletomaps.ui.data.FirebaseManager.db.collection("reportes").document(reporteId)

        docRef.get().addOnSuccessListener { document ->
            if (!document.exists()) return@addOnSuccessListener

            // 1. Extraer toda la data del reporte
            val latReporte = document.getDouble("latitud") ?: 0.0
            val lngReporte = document.getDouble("longitud") ?: 0.0
            val usuariosVotantes = document.get("usuariosVotantes") as? List<String> ?: emptyList()
            val descripcion = document.getString("descripcion") ?: ""
            val hora = document.getString("hora") ?: ""

            // 2. Inflar la Bottom Sheet elegante
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
            val vistaLayout = layoutInflater.inflate(R.layout.dialog_detalle_reporte, null)
            bottomSheetDialog.setContentView(vistaLayout)

            // Vincular vistas de la tarjeta
            val tvTipo = vistaLayout.findViewById<android.widget.TextView>(R.id.tvTvTipo)
            val tvHora = vistaLayout.findViewById<android.widget.TextView>(R.id.tvTvHora)
            val tvDescripcion = vistaLayout.findViewById<android.widget.TextView>(R.id.tvTvDescripcion)
            val tvAlerta = vistaLayout.findViewById<android.widget.TextView>(R.id.tvAlertaRestriccion)
            val btnSi = vistaLayout.findViewById<android.widget.Button>(R.id.btnVotoSi)
            val btnNo = vistaLayout.findViewById<android.widget.Button>(R.id.btnVotoNo)

            // Setear datos informativos obligatorios (Siempre visibles)
            tvTipo.text = tipoIncidente.uppercase()
            tvHora.text = "Reportado a las $hora"
            if (descripcion.isNotEmpty()) {
                tvDescripcion.text = descripcion
            }

            // 3. Evaluar restricciones lógicas en segundo plano sin romper la UX
            var puedeVotar = true
            var motivoBloqueo = ""

            // Evaluar si ya votó
            if (usuariosVotantes.contains(currentUserId)) {
                puedeVotar = false
                motivoBloqueo = "🔒 Ya emitiste tu voto para este incidente."
            }

            // Evaluar distancia geográfica (200 metros)
            val miUbicacionActual = mMap?.myLocation
            if (miUbicacionActual != null && puedeVotar) {
                val resultadoDistancia = FloatArray(1)
                android.location.Location.distanceBetween(
                    miUbicacionActual.latitude, miUbicacionActual.longitude,
                    latReporte, lngReporte,
                    resultadoDistancia
                )
                if (resultadoDistancia[0] > 200f) {
                    puedeVotar = false
                    motivoBloqueo = "🔒 Estás muy lejos (${resultadoDistancia[0].toInt()}m) para validar este reporte."
                }
            } else if (miUbicacionActual == null && puedeVotar) {
                puedeVotar = false
                motivoBloqueo = "🔒 Esperando señal GPS para habilitar votación."
            }

            // 4. Aplicar los cambios visuales de restricción
            if (!puedeVotar) {
                tvAlerta.text = motivoBloqueo
                tvAlerta.visibility = android.view.View.VISIBLE

                // Apagamos los botones visualmente cambiándolos a gris y quitando clics
                btnSi.isEnabled = false
                btnSi.alpha = 0.5f
                btnNo.isEnabled = false
                btnNo.alpha = 0.5f
            } else {
                // Si todo está ok, asignamos las acciones de transacciones de Firestore normales
                btnSi.setOnClickListener {
                    actualizarVotosReporte(reporteId, currentUserId, esPositivo = true)
                    bottomSheetDialog.dismiss()
                }
                btnNo.setOnClickListener {
                    actualizarVotosReporte(reporteId, currentUserId, esPositivo = false)
                    bottomSheetDialog.dismiss()
                }
            }

            // Mostrar la tarjeta integrada en pantalla
            bottomSheetDialog.show()

        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(requireContext(), "Error al cargar detalle: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarVotosReporte(reporteId: String, userId: String, esPositivo: Boolean) {
        val docRef = com.example.coletomaps.ui.data.FirebaseManager.db.collection("reportes").document(reporteId)

        com.example.coletomaps.ui.data.FirebaseManager.db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val votosPos = snapshot.getLong("votosPositivos") ?: 0
            val votosNeg = snapshot.getLong("votosNegativos") ?: 0
            val votantes = snapshot.get("usuariosVotantes") as? List<String> ?: emptyList()

            // Creamos una nueva lista mutable añadiendo al usuario actual para bloquearlo en el futuro
            val nuevosVotantes = votantes.toMutableList()
            nuevosVotantes.add(userId)

            transaction.update(docRef, "usuariosVotantes", nuevosVotantes)

            if (esPositivo) {
                transaction.update(docRef, "votosPositivos", votosPos + 1)
            } else {
                val nuevosVotantesNeg = votosNeg + 1
                transaction.update(docRef, "votosNegativos", nuevosVotantesNeg)

                // Regla comunitaria: si acumula 3 o más votos negativos, se oculta automáticamente
                if (nuevosVotantesNeg >= 3) {
                    transaction.update(docRef, "activo", false)
                }
            }
        }.addOnSuccessListener {
            android.widget.Toast.makeText(requireContext(), "¡Voto registrado! Gracias por cooperar", android.widget.Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(requireContext(), "Error al procesar el voto: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun bitmapDescriptorFromVector(
        context: android.content.Context,
        vectorResId: Int,
        backgroundColor: Int
    ): com.google.android.gms.maps.model.BitmapDescriptor? {

        // 1. Obtener el ícono vectorial y teñirlo de BLANCO para que contraste con el fondo
        val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId) ?: return null
        val iconoBlanco = vectorDrawable.mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(iconoBlanco, android.graphics.Color.WHITE)

        // 2. Definir el tamaño del marcador (puedes ajustar estos números para agrandar/achicar)
        val size = 90
        val padding = 20 // Espacio entre el borde del círculo y el ícono interno

        // 3. Crear el mapa de bits y el lienzo para dibujar
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 4. Dibujar el círculo de fondo con el color que le mandemos
        val paint = android.graphics.Paint().apply {
            color = backgroundColor
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        // Dibujamos el círculo justo al centro
        val radio = size / 2f
        canvas.drawCircle(radio, radio, radio, paint)

        // 5. Dibujar una pequeña sombra o borde blanco exterior para que resalte más en el mapa
        val strokePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawCircle(radio, radio, radio - 2.5f, strokePaint)

        // 6. Posicionar y dibujar el ícono blanco al centro del círculo
        iconoBlanco.setBounds(padding, padding, size - padding, size - padding)
        iconoBlanco.draw(canvas)

        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}