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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodosLosReportesFragment : Fragment() {

    private lateinit var rvTodos: RecyclerView
    private lateinit var tvNoReportes: TextView
    private lateinit var adapter: ReportesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val listaReportes = mutableListOf<ReporteIncidente>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val vista = inflater.inflate(R.layout.fragment_todos_los_reportes, container, false)

        rvTodos = vista.findViewById(R.id.rvTodosLosReportes)
        tvNoReportes = vista.findViewById(R.id.tvNoTodosReportes)

        rvTodos.layoutManager = LinearLayoutManager(requireContext())
        adapter = ReportesAdapter(listaReportes)
        rvTodos.adapter = adapter

        escucharFirestore()

        return vista
    }

    private fun escucharFirestore() {
        val limiteHaceDosHoras = Calendar.getInstance().apply {
            add(Calendar.HOUR, -2)
        }.time

        db.collection("reportes")
            .whereEqualTo("activo", true)
            .whereGreaterThanOrEqualTo("fechaCreacion", com.google.firebase.Timestamp(limiteHaceDosHoras))
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                listaReportes.clear()
                for (doc in snapshots) {
                    val reporte = doc.toObject(ReporteIncidente::class.java)
                    listaReportes.add(reporte)
                }

                // Ordenar por hora más reciente primero
                listaReportes.sortByDescending { it.fechaCreacion }

                actualizarInterfaz()
            }
    }

    private fun actualizarInterfaz() {
        // Notifica los cambios al RecyclerView
        adapter.notifyDataSetChanged()

        // Muestra u oculta el texto informativo según corresponda
        if (listaReportes.isEmpty()) {
            tvNoReportes.visibility = View.VISIBLE
            rvTodos.visibility = View.GONE
        } else {
            tvNoReportes.visibility = View.GONE
            rvTodos.visibility = View.VISIBLE
        }
    }
}