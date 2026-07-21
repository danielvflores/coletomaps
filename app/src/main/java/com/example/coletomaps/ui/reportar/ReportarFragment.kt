package com.example.coletomaps.ui.reportar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.coletomaps.R
import com.example.coletomaps.databinding.FragmentReportarBinding
import com.example.coletomaps.ui.data.FirebaseManager
import com.example.coletomaps.ui.data.ReporteIncidente
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import android.location.Geocoder
import java.io.IOException

class ReportarFragment : Fragment() {

    private var _binding: FragmentReportarBinding? = null
    private val binding get() = _binding!!
    private var latitudCapturada: Double = 0.0
    private var longitudCapturada: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Asignar hora actual de forma automática por comodidad
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.etHora.setText(sdf.format(Calendar.getInstance().time))

        // Obtener ubicación actual al presionar el botón correspondiente
        binding.btnMiUbicacion.setOnClickListener {
            obtenerUbicacionActual()
        }

        binding.btnCancelarReporte.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnComentar.setOnClickListener {
            guardarReporteEnFirestore()
        }
    }

    private fun obtenerUbicacionActual() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitudCapturada = location.latitude
                    longitudCapturada = location.longitude
                    binding.etUbicacion.setText("${String.format("%.4f", latitudCapturada)}, ${String.format("%.4f", longitudCapturada)}")
                } else {
                    Toast.makeText(requireContext(), "No se pudo obtener el GPS. Revisa si está activo.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarReporteEnFirestore() {
        val idSeleccionado = binding.rgTipoIncidente.checkedRadioButtonId
        if (idSeleccionado == -1) {
            Toast.makeText(requireContext(), "Por favor selecciona un tipo de incidente", Toast.LENGTH_SHORT).show()
            return
        }

        val radioButton = binding.root.findViewById<android.widget.RadioButton>(idSeleccionado)
        val tipoIncidente = radioButton.text.toString()
        val hora = binding.etHora.text.toString().trim()
        val descripcion = binding.etDescripcion.text.toString().trim()

        if (latitudCapturada == 0.0 && longitudCapturada == 0.0) {
            Toast.makeText(requireContext(), "Debes presionar 'Mi ubicación' para georreferenciar el reporte", Toast.LENGTH_SHORT).show()
            return
        }

        if (hora.isEmpty()) {
            Toast.makeText(requireContext(), "Por favor ingresa la hora del incidente", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"

        // --- LOGICA DE COOLDOWN (TIEMPO Y DISTANCIA) ---
        // Calculamos el límite de tiempo: 5 minutos atrás desde ahora
        val cincoMinutosEnSegundos = 5 * 60
        val tiempoLimite = Timestamp(Timestamp.now().seconds - cincoMinutosEnSegundos, 0)

        // Consultamos si este usuario ya subió reportes en los últimos 5 minutos
        FirebaseManager.db.collection("reportes")
            .whereEqualTo("userId", currentUserId)
            .whereGreaterThan("fechaCreacion", tiempoLimite)
            .get()
            .addOnSuccessListener { documentos ->
                var antiSpamBloqueado = false

                for (doc in documentos) {
                    val latExistente = doc.getDouble("latitud") ?: 0.0
                    val lngExistente = doc.getDouble("longitud") ?: 0.0

                    // Calcular distancia matemática entre el intento actual y el reporte viejo
                    val resultadoDistancia = FloatArray(1)
                    android.location.Location.distanceBetween(
                        latitudCapturada, longitudCapturada,
                        latExistente, lngExistente,
                        resultadoDistancia
                    )
                    val distanciaMetros = resultadoDistancia[0]

                    // Si está a menos de 100 metros en el mismo rango de tiempo, se activa el Cooldown
                    if (distanciaMetros < 100f) {
                        antiSpamBloqueado = true
                        break
                    }
                }

                if (antiSpamBloqueado) {
                    Toast.makeText(
                        requireContext(),
                        "🚨 Cooldown activo: Ya creaste un reporte muy cerca en los últimos 5 minutos.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Si pasa el filtro de Cooldown, procedemos a guardar el reporte normalmente
                    ejecutarInsercionReporte(currentUserId, tipoIncidente, descripcion, hora)
                }
            }
            .addOnFailureListener { e ->
                // Si falla la consulta de seguridad, por precaución permitimos el flujo pero avisamos
                Log.e("ColetoMaps", "Error validando cooldown, procediendo igual: ${e.message}")
                ejecutarInsercionReporte(currentUserId, tipoIncidente, descripcion, hora)
            }
    }

    private fun ejecutarInsercionReporte(userId: String, tipo: String, desc: String, hora: String) {
        val reporteRef = FirebaseManager.db.collection("reportes").document()

        // 1. Obtenemos el nombre de la calle usando el Geocoder
        val calleDetectada = obtenerNombreCalle(latitudCapturada, longitudCapturada)

        // 2. Creamos el reporte incluyendo la calle detectada
        val nuevoReporte = ReporteIncidente(
            id = reporteRef.id,
            userId = userId,
            tipoIncidente = tipo,
            descripcion = desc,
            latitud = latitudCapturada,
            longitud = longitudCapturada,
            calle = calleDetectada, // 👈 Pasamos el nombre de la calle aquí
            hora = hora,
            fechaCreacion = Timestamp.now(),
            votosPositivos = 1,
            votosNegativos = 0,
            usuariosVotantes = listOf(userId),
            activo = true
        )

        reporteRef.set(nuevoReporte)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "¡Reporte publicado con éxito!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al guardar reporte: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun obtenerNombreCalle(latitud: Double, longitud: Double): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        return try {
            // Le pedimos al Geocoder que busque solo 1 dirección para esas coordenadas
            val direcciones = geocoder.getFromLocation(latitud, longitud, 1)
            if (!direcciones.isNullOrEmpty()) {
                val direccion = direcciones[0]

                // Usamos thoroName (nombre de la calle). Si es nulo, intentamos con la dirección completa simplificada
                val nombreCalle = direccion.thoroughfare ?: direccion.getAddressLine(0)?.split(",")?.get(0)

                nombreCalle ?: "Ubicación desconocida"
            } else {
                "Ubicación desconocida"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            "Ubicación aproximada" // Retorno de respaldo por si el teléfono no tiene internet en ese microsegundo
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}