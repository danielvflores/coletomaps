package com.example.coletomaps.ui.slideshow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.coletomaps.R
import com.example.coletomaps.ui.data.ReporteIncidente
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MisReportesFragment : Fragment() {

    private lateinit var rvMisReportes: RecyclerView
    private lateinit var tvNoMis: TextView
    private lateinit var adapter: ReportesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val listaMisReportes = mutableListOf<ReporteIncidente>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val vista = inflater.inflate(R.layout.fragment_mis_reportes, container, false)

        rvMisReportes = vista.findViewById(R.id.rvMisReportes)
        tvNoMis = vista.findViewById(R.id.tvNoMisReportes)

        rvMisReportes.layoutManager = LinearLayoutManager(requireContext())

        // Inicializamos el adapter con 'listaMisReportes' y el callback 'onLongClick'
        adapter = ReportesAdapter(
            lista = listaMisReportes,
            onLongClick = { reporteSeleccionado ->
                // Diálogo de confirmación para finalizar el reporte
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Finalizar Reporte")
                    .setMessage("¿Deseas marcar este incidente como resuelto? Se eliminará del mapa de todos los conductores.")
                    .setPositiveButton("Sí, resolver") { _, _ ->
                        // Usamos el ID del reporte para desactivarlo en Firestore
                        val reporteId = reporteSeleccionado.id
                        if (reporteId.isNotEmpty()) {
                            db.collection("reportes").document(reporteId)
                                .update("activo", false)
                                .addOnSuccessListener {
                                    // Firebase actualizará la lista en tiempo real por el SnapshotListener
                                }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        rvMisReportes.adapter = adapter

        escucharMisReportes()

        return vista
    }

    private fun escucharMisReportes() {
        val currentUserId = auth.currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            tvNoMis.visibility = View.VISIBLE
            rvMisReportes.visibility = View.GONE
            return
        }

        // 1. Filtramos estrictamente en Firestore que 'activo' sea true
        db.collection("reportes")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("activo", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                listaMisReportes.clear()

                for (doc in snapshots) {
                    val reporteBase = doc.toObject(ReporteIncidente::class.java)

                    // 2. Candado extra en código: Solo procesamos si el documento viene marcado como activo
                    if (reporteBase.activo) {
                        val reporteConId = reporteBase.copy(id = doc.id)
                        listaMisReportes.add(reporteConId)
                    }
                }

                // Ordenamos por los más recientes primero
                listaMisReportes.sortByDescending { it.fechaCreacion }

                if (listaMisReportes.isEmpty()) {
                    tvNoMis.visibility = View.VISIBLE
                    rvMisReportes.visibility = View.GONE
                } else {
                    tvNoMis.visibility = View.GONE
                    rvMisReportes.visibility = View.VISIBLE
                }

                adapter.actualizarLista(listaMisReportes)
            }
    }
}