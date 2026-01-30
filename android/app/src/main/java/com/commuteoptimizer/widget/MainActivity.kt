package com.commuteoptimizer.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.commuteoptimizer.widget.ui.CommuteFragment
import com.commuteoptimizer.widget.ui.LiveTrainsFragment
import com.commuteoptimizer.widget.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_commute -> CommuteFragment()
                R.id.nav_live_trains -> LiveTrainsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> CommuteFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_commute
        }
    }
}
