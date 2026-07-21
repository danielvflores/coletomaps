package com.example.coletomaps.ui.slideshow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.coletomaps.R
import com.example.coletomaps.ui.data.ReporteIncidente

class ReportesAdapter(
    private var lista: List<ReporteIncidente>,
    private val onLongClick: ((ReporteIncidente) -> Unit)? = null
) : RecyclerView.Adapter<ReportesAdapter.ReporteViewHolder>() {

    class ReporteViewHolder(vista: View) : RecyclerView.ViewHolder(vista) {
        val tvCalle: TextView = vista.findViewById(R.id.tvItemCalle)
        val tvTipo: TextView = vista.findViewById(R.id.tvItemTipo)
        val tvDescripcion: TextView = vista.findViewById(R.id.tvItemDescripcion)
        val tvHora: TextView = vista.findViewById(R.id.tvItemHora)
        val tvVotosPos: TextView = vista.findViewById(R.id.tvVotosPositivos)
        val tvVotosNeg: TextView = vista.findViewById(R.id.tvVotosNegativos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReporteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reporte, parent, false)
        return ReporteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReporteViewHolder, position: Int) {
        val reporte = lista[position]

        // Seteamos la calle o un texto por defecto por si hay reportes antiguos sin este campo
        holder.tvCalle.text = reporte.calle.ifEmpty { "Ubicación en Arica" }
        holder.tvTipo.text = "Alerta: ${reporte.tipoIncidente.replaceFirstChar { it.uppercase() }}"
        holder.tvDescripcion.text = reporte.descripcion.ifEmpty { "Sin detalles adicionales." }
        holder.tvHora.text = "Creado a las ${reporte.hora}"

        holder.tvVotosPos.text = "👍 ${reporte.votosPositivos}"
        holder.tvVotosNeg.text = "👎 ${reporte.votosNegativos}"

        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(reporte)
            true
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<ReporteIncidente>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}