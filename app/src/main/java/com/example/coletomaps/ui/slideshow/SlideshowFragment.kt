package com.example.coletomaps.ui.slideshow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.coletomaps.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SlideshowFragment : Fragment() {

    // 1. Asegúrate de tener estas dos variables declaradas aquí arriba
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 2. Aquí cambiamos a tu nuevo nombre de archivo XML: fragment_historial_reportes
        val vista = inflater.inflate(R.layout.fragment_historial_reportes, container, false)

        // 3. Al estar declaradas arriba, ya no te darán error aquí
        tabLayout = vista.findViewById(R.id.tabLayoutReportes)
        viewPager = vista.findViewById(R.id.viewPagerReportes)

        // 4. Configuramos el adaptador deslizable
        val adapter = ReportesPagerAdapter(this)
        viewPager.adapter = adapter

        // 5. Vinculamos el TabLayout con el ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Todos los reportes"
                else -> "Mis reportes"
            }
        }.attach()

        return vista
    }
}