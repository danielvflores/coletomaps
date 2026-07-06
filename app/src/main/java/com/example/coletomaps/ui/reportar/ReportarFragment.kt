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

        val radioButton = binding.root.findViewById<RadioButton>(idSeleccionado)
        val tipoIncidente = radioButton.text.toString()
        val hora = binding.etHora.text.toString().trim()

        if (latitudCapturada == 0.0 && longitudCapturada == 0.0) {
            Toast.makeText(requireContext(), "Debes presionar 'Mi ubicación' para georreferenciar el reporte", Toast.LENGTH_SHORT).show()
            return
        }

        // Generamos un ID único en Firestore de forma manual
        val reporteRef = FirebaseManager.db.collection("reportes").document()

        val nuevoReporte = ReporteIncidente(
            id = reporteRef.id,
            tipoIncidente = tipoIncidente,
            latitud = latitudCapturada,
            longitud = longitudCapturada,
            hora = hora,
            votosPositivos = 1, // El creador le da el primer voto implícito
            votosNegativos = 0,
            activo = true
        )

        reporteRef.set(nuevoReporte)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "¡Reporte publicado con éxito!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp() // Volver al mapa automáticamente
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al guardar reporte: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}