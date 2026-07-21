package com.example.coletomaps.ui.slideshow

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ReportesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // Tenemos exactamente 2 pestañas
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TodosLosReportesFragment() // Primera pestaña
            else -> MisReportesFragment()   // Segunda pestaña
        }
    }
}