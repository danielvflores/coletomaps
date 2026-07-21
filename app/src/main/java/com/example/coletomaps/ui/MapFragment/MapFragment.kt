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
    private val listaReportesObjetos = mutableListOf<com.example.coletomaps.ui.data.ReporteIncidente>()

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
                callesRecorrido = "Capitán Ávalos, Renato Rocca, Azolas, Rotonda Manuel Castillo, 18 de Septiembre, Thompson, Prat, San Martín"
            ),
            RutaLocal(
                nombre = "Línea 4", colorHex = "#24A4AB",
                puntos = listOf(LatLng(-18.4567128, -70.2808515), LatLng(-18.4588226, -70.2804647), LatLng(-18.4641428, -70.2814317), LatLng(-18.4700133, -70.282012), LatLng(-18.4768007, -70.2827856), LatLng(-18.4847802, -70.2845263), LatLng(-18.4881647, -70.2853984)),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1300",
                callesRecorrido = "Capitán Ávalos, Valente Rossi, Linderos, Antártica, Diego Portales, Tucapel, Independencia, Maipú"
            ),
            RutaLocal(
                nombre = "Línea 5", colorHex = "#D4AF37", // Color Oro/Mostaza para diferenciarla
                puntos = listOf(
                    LatLng(-18.4793698, -70.3201154), LatLng(-18.4790561, -70.3197431),
                    LatLng(-18.4788342, -70.3194541), LatLng(-18.4786604, -70.319234), LatLng(-18.4784093, -70.3189112),
                    LatLng(-18.4782235, -70.3186687), LatLng(-18.4778439, -70.3181891), LatLng(-18.4773468, -70.3175547),
                    LatLng(-18.4777172, -70.3171277), LatLng(-18.4779007, -70.3169221), LatLng(-18.478206, -70.3165841),
                    LatLng(-18.478809, -70.3159053), LatLng(-18.479592, -70.3167317), LatLng(-18.4807039, -70.3179129),
                    LatLng(-18.4813825, -70.3171871), LatLng(-18.4819874, -70.3165223), LatLng(-18.4826316, -70.3158271),
                    LatLng(-18.4832239, -70.315169), LatLng(-18.4836234, -70.3147385), LatLng(-18.4840988, -70.314673),
                    LatLng(-18.4856175, -70.3144191), LatLng(-18.4860832, -70.3143282), LatLng(-18.4862557, -70.3142902),
                    LatLng(-18.4865366, -70.314143), LatLng(-18.4870369, -70.3138188), LatLng(-18.4879309, -70.3132575),
                    LatLng(-18.4887645, -70.3127435), LatLng(-18.4890016, -70.3125836), LatLng(-18.4893733, -70.3124317),
                    LatLng(-18.4915013, -70.3116538), LatLng(-18.491958, -70.3115639), LatLng(-18.4925476, -70.3117342),
                    LatLng(-18.4933188, -70.3119624), LatLng(-18.4938659, -70.312112), LatLng(-18.4946823, -70.3122483),
                    LatLng(-18.4951558, -70.3123103), LatLng(-18.4957088, -70.3123035), LatLng(-18.4960665, -70.3122836),
                    LatLng(-18.4964052, -70.3122112), LatLng(-18.4967936, -70.3120887), LatLng(-18.4971022, -70.3119264),
                    LatLng(-18.4973821, -70.3117341), LatLng(-18.4975841, -70.311559), LatLng(-18.4978767, -70.3113123),
                    LatLng(-18.4987092, -70.3106226), LatLng(-18.4991513, -70.3102478), LatLng(-18.5003664, -70.3092219),
                    LatLng(-18.5004711, -70.3091365), LatLng(-18.4992316, -70.3075907), LatLng(-18.497551, -70.3055135),
                    LatLng(-18.4972259, -70.3052144), LatLng(-18.4970359, -70.3050619), LatLng(-18.4967157, -70.3048767),
                    LatLng(-18.4957805, -70.304385), LatLng(-18.494893, -70.303928), LatLng(-18.4941847, -70.3035328),
                    LatLng(-18.4937927, -70.3032498), LatLng(-18.4928364, -70.3023771), LatLng(-18.4920367, -70.3016588),
                    LatLng(-18.4921377, -70.3012194), LatLng(-18.4922112, -70.3008856), LatLng(-18.4922883, -70.3005649),
                    LatLng(-18.4934245, -70.3008316), LatLng(-18.4936028, -70.300881), LatLng(-18.4937503, -70.3009061),
                    LatLng(-18.493863, -70.3009026), LatLng(-18.4940885, -70.3017734), LatLng(-18.4943347, -70.3027575),
                    LatLng(-18.4945116, -70.3029476), LatLng(-18.4947445, -70.3030845), LatLng(-18.4952343, -70.3033197),
                    LatLng(-18.4964462, -70.3038337), LatLng(-18.497266, -70.3042424), LatLng(-18.4974733, -70.3043751),
                    LatLng(-18.4978789, -70.3047068), LatLng(-18.4980487, -70.3048775), LatLng(-18.4975188, -70.3053406),
                    LatLng(-18.4974462, -70.30542), LatLng(-18.4973932, -70.3054787), LatLng(-18.4975919, -70.3057065),
                    LatLng(-18.498111, -70.306349), LatLng(-18.500335, -70.3091169), LatLng(-18.4976867, -70.3113529),
                    LatLng(-18.4972484, -70.3117198), LatLng(-18.4969549, -70.3118956), LatLng(-18.4967226, -70.3120059),
                    LatLng(-18.4964018, -70.3121265), LatLng(-18.4961178, -70.3121798), LatLng(-18.4958486, -70.3122075),
                    LatLng(-18.4952368, -70.3122167), LatLng(-18.494964, -70.3122051), LatLng(-18.4947749, -70.3121882),
                    LatLng(-18.4944323, -70.3121205), LatLng(-18.4941142, -70.3120641), LatLng(-18.493622, -70.3119555),
                    LatLng(-18.4930556, -70.3117919), LatLng(-18.4920158, -70.3114901), LatLng(-18.4919562, -70.3114788),
                    LatLng(-18.4914952, -70.3115726), LatLng(-18.4905049, -70.3119304), LatLng(-18.4893402, -70.3123558),
                    LatLng(-18.4890644, -70.3124659), LatLng(-18.4888953, -70.3125627), LatLng(-18.4885292, -70.3127873),
                    LatLng(-18.4881506, -70.313027), LatLng(-18.4873731, -70.3134946), LatLng(-18.4863949, -70.3141176),
                    LatLng(-18.4862999, -70.3141677), LatLng(-18.4862498, -70.3141855), LatLng(-18.486139, -70.3142322),
                    LatLng(-18.4860165, -70.3142555), LatLng(-18.4855825, -70.3143294), LatLng(-18.4841258, -70.3145969),
                    LatLng(-18.4841094, -70.3146481), LatLng(-18.4841312, -70.3146968), LatLng(-18.4844091, -70.3149531),
                    LatLng(-18.4845314, -70.315069), LatLng(-18.4846109, -70.3152005), LatLng(-18.4846197, -70.3152847),
                    LatLng(-18.484582, -70.3154089), LatLng(-18.4844843, -70.3155832), LatLng(-18.4842894, -70.3159063),
                    LatLng(-18.484232, -70.3160092), LatLng(-18.4842158, -70.316125), LatLng(-18.4841668, -70.3166644),
                    LatLng(-18.483757, -70.3168525), LatLng(-18.482981, -70.317423), LatLng(-18.4822261, -70.3179963),
                    LatLng(-18.4813865, -70.3186447), LatLng(-18.4804368, -70.3192986), LatLng(-18.4793749, -70.3201165)
                ),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1200",
                callesRecorrido = "San Ignacio de Loyola, Pablo Picasso, Rafael Sotomayor, Luis Gallardo, Ignacio Vergara, Alfonso Néspolo Arata, San Marcos, Patricio Lynch, Cristóbal Colón"
            ),
            RutaLocal(
                nombre = "Línea 7", colorHex = "#FF8F00", // Nueva línea (Naranja)
                puntos = listOf(
                    LatLng(-18.4491999, -70.2946631), LatLng(-18.4492204, -70.2946089), LatLng(-18.4492883, -70.2945482),
                    LatLng(-18.4496151, -70.2942644), LatLng(-18.4497713, -70.2941387), LatLng(-18.4500447, -70.2938994),
                    LatLng(-18.4501224, -70.2937453), LatLng(-18.4501625, -70.2936307), LatLng(-18.4501754, -70.293538),
                    LatLng(-18.4501845, -70.2932188), LatLng(-18.4501861, -70.2929586), LatLng(-18.4501551, -70.2928018),
                    LatLng(-18.4501163, -70.2927063), LatLng(-18.4500529, -70.2926122), LatLng(-18.4499584, -70.2924963),
                    LatLng(-18.4498368, -70.2923422), LatLng(-18.4490752, -70.2913619), LatLng(-18.4483573, -70.2904426),
                    LatLng(-18.4481051, -70.2901222), LatLng(-18.4475483, -70.2894028), LatLng(-18.4473338, -70.2891628),
                    LatLng(-18.4471152, -70.2889923), LatLng(-18.4469607, -70.2889059), LatLng(-18.44683, -70.28885),
                    LatLng(-18.4467304, -70.2888173), LatLng(-18.4465583, -70.2887846), LatLng(-18.4463591, -70.2887723),
                    LatLng(-18.4462081, -70.2887785), LatLng(-18.4460865, -70.2887907), LatLng(-18.4459209, -70.2888221),
                    LatLng(-18.4455671, -70.288893), LatLng(-18.4449021, -70.289028), LatLng(-18.4439707, -70.2892108),
                    LatLng(-18.4430176, -70.2893895), LatLng(-18.4420786, -70.2895753), LatLng(-18.4411639, -70.289761),
                    LatLng(-18.4406638, -70.2898676), LatLng(-18.4391998, -70.2901735), LatLng(-18.4382712, -70.2903729),
                    LatLng(-18.4380949, -70.2916967), LatLng(-18.4379679, -70.2926336), LatLng(-18.4378659, -70.2933681),
                    LatLng(-18.4376612, -70.2949925), LatLng(-18.4376514, -70.2950454), LatLng(-18.4376393, -70.2951707),
                    LatLng(-18.4384858, -70.2951619), LatLng(-18.44013, -70.2951466), LatLng(-18.4417363, -70.2951526),
                    LatLng(-18.4434229, -70.2951466), LatLng(-18.4462224, -70.2951345), LatLng(-18.44922, -70.2951285),
                    LatLng(-18.4537472, -70.2950864), LatLng(-18.4546363, -70.2950864), LatLng(-18.4548804, -70.295169),
                    LatLng(-18.4550979, -70.2952563), LatLng(-18.4552962, -70.2952746), LatLng(-18.4554554, -70.2952419),
                    LatLng(-18.4557631, -70.295143), LatLng(-18.4559806, -70.2951004), LatLng(-18.4564502, -70.2951024),
                    LatLng(-18.4570643, -70.2951024), LatLng(-18.4578157, -70.2951036), LatLng(-18.4585518, -70.2950955),
                    LatLng(-18.4588325, -70.2951083), LatLng(-18.458972, -70.2951275), LatLng(-18.4593215, -70.2952212),
                    LatLng(-18.4596472, -70.2953834), LatLng(-18.4601278, -70.2957186), LatLng(-18.4605625, -70.2960693),
                    LatLng(-18.4610534, -70.2964597), LatLng(-18.4615659, -70.2969172), LatLng(-18.4617793, -70.2970906),
                    LatLng(-18.4621103, -70.297324), LatLng(-18.4623279, -70.2974728), LatLng(-18.4629593, -70.2980369),
                    LatLng(-18.4636868, -70.2986639), LatLng(-18.4654083, -70.3001473), LatLng(-18.4683166, -70.3026484),
                    LatLng(-18.469628, -70.3038188), LatLng(-18.4709537, -70.304951), LatLng(-18.4715606, -70.305451),
                    LatLng(-18.4716703, -70.3055454), LatLng(-18.4724363, -70.3046896), LatLng(-18.4732202, -70.3038),
                    LatLng(-18.4741656, -70.3027478), LatLng(-18.4752414, -70.301432), LatLng(-18.4759015, -70.3006081),
                    LatLng(-18.4811335, -70.2964688)
                ),
                valorDiurno = "$1000", valorTarde = "$1100", valorNoche = "$1200",
                callesRecorrido = "Avenida Diego Portales, Avenida Santa María, Avenida Cancha Rayada, Doctor Salvador Neghme"
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
                callesRecorrido = "Edmundo Flores, Tucapel, 18 de Septiembre, General Velásquez, Juan Noé, Arturo Prat, San Marcos, Azolas, Saucache"
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

            verificarReportesEnRuta(rutaInfo, listaReportesObjetos)
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
        val dosHorasEnSegundos = 2 * 60 * 60
        val tiempoLimiteExpiracion = com.google.firebase.Timestamp(
            com.google.firebase.Timestamp.now().seconds - dosHorasEnSegundos, 0
        )

        com.example.coletomaps.ui.data.FirebaseManager.db.collection("reportes")
            .whereEqualTo("activo", true)
            .whereGreaterThan("fechaCreacion", tiempoLimiteExpiracion)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) {
                    android.util.Log.w("ColetoMaps", "Error al escuchar reportes", e)
                    return@addSnapshotListener
                }

                limpiarMarcadoresReportes()
                listaReportesObjetos.clear() // NUEVO: Limpiamos la lista global de objetos

                for (document in snapshots) {
                    val id = document.getString("id") ?: document.id
                    val tipo = document.getString("tipoIncidente") ?: ""
                    val lat = document.getDouble("latitud") ?: 0.0
                    val lng = document.getDouble("longitud") ?: 0.0
                    val hora = document.getString("hora") ?: ""
                    val descripcion = document.getString("descripcion") ?: ""
                    val calle = document.getString("calle") ?: ""

                    val reporteObj = com.example.coletomaps.ui.data.ReporteIncidente(
                        id = id,
                        tipoIncidente = tipo, // Aquí mapeamos tu variable 'tipo' al campo 'tipoIncidente'
                        latitud = lat,
                        longitud = lng,
                        hora = hora,
                        descripcion = descripcion,
                        calle = calle
                    )
                    listaReportesObjetos.add(reporteObj)

                    val posicion = LatLng(lat, lng)

                    val (recursoIcono, colorFondo) = when (tipo.lowercase()) {
                        "incendio" -> Pair(R.drawable.ic_incendio, android.graphics.Color.parseColor("#FF6D00"))
                        "congestión" -> Pair(R.drawable.ic_congestion, android.graphics.Color.parseColor("#2962FF"))
                        "accidente vehicular" -> Pair(R.drawable.ic_accidente, android.graphics.Color.parseColor("#D50000"))
                        "corte de calle" -> Pair(R.drawable.ic_corte, android.graphics.Color.parseColor("#00C853"))
                        else -> Pair(R.drawable.ic_accidente, android.graphics.Color.RED)
                    }

                    val miIconoPersonalizado = bitmapDescriptorFromVector(requireContext(), recursoIcono, colorFondo)

                    val markerOptions = MarkerOptions()
                        .position(posicion)
                        .title(tipo.uppercase())
                        .snippet(if (descripcion.isNotEmpty()) "$descripcion\nHora: $hora" else "Hora: $hora")

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

            // NUEVO: Extraemos la calle guardada en Firestore
            val calle = document.getString("calle") ?: ""

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

            // MODIFICADO: Ahora concatenamos el tipo de incidente con la calle si existe
            if (calle.isNotEmpty()) {
                tvTipo.text = "${tipoIncidente.uppercase()} \n(En $calle)"
            } else {
                tvTipo.text = tipoIncidente.uppercase()
            }

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
    private fun verificarReportesEnRuta(ruta: RutaLocal, reportesActivos: List<com.example.coletomaps.ui.data.ReporteIncidente>) {
        val reportesCercanos = mutableListOf<com.example.coletomaps.ui.data.ReporteIncidente>()

        // Pasamos todo el texto de las calles de la ruta a minúsculas para evitar problemas con mayúsculas/minúsculas
        val callesDeLaRuta = ruta.callesRecorrido.lowercase()

        for (reporte in reportesActivos) {
            val calleReporte = reporte.calle.lowercase().trim()

            // Validamos que el reporte tenga una calle asignada y que esa calle esté en el recorrido de la ruta
            if (calleReporte.isNotEmpty() && callesDeLaRuta.contains(calleReporte)) {
                reportesCercanos.add(reporte)
            }
        }

        // Si encontramos incidentes en las calles de la ruta, mostramos la alerta
        if (reportesCercanos.isNotEmpty()) {
            val mensajeBuilder = java.lang.StringBuilder()
            mensajeBuilder.append("⚠️ ¡Atención! Se detectaron incidentes en las calles de esta ruta:\n\n")

            for ((index, rep) in reportesCercanos.withIndex()) {
                mensajeBuilder.append("${index + 1}. [${rep.tipoIncidente.uppercase()}] en calle: ${rep.calle}\n")
            }

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Reportes en tu Recorrido")
                .setMessage(mensajeBuilder.toString())
                .setPositiveButton("Entendido", null)
                .show()
        }
    }
}