package com.commuteoptimizer.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.commuteoptimizer.widget.ui.CommuteFragment
import com.commuteoptimizer.widget.ui.LiveTrainsFragment
import com.commuteoptimizer.widget.ui.SearchFragment
import com.commuteoptimizer.widget.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    private lateinit var commuteFragment: CommuteFragment
    private lateinit var liveTrainsFragment: LiveTrainsFragment
    private lateinit var searchFragment: SearchFragment
    private lateinit var settingsFragment: SettingsFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        if (savedInstanceState == null) {
            setupFragments()
            bottomNav.selectedItemId = R.id.nav_commute
        } else {
            restoreFragments()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_commute -> showFragment(commuteFragment)
                R.id.nav_search -> showFragment(searchFragment)
                R.id.nav_live_trains -> showFragment(liveTrainsFragment)
                R.id.nav_settings -> showFragment(settingsFragment)
            }
            true
        }
    }

    private fun setupFragments() {
        commuteFragment = CommuteFragment()
        searchFragment = SearchFragment()
        liveTrainsFragment = LiveTrainsFragment()
        settingsFragment = SettingsFragment()

        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, settingsFragment, TAG_SETTINGS)
            .hide(settingsFragment)
            .add(R.id.fragment_container, liveTrainsFragment, TAG_LIVE_TRAINS)
            .hide(liveTrainsFragment)
            .add(R.id.fragment_container, searchFragment, TAG_SEARCH)
            .hide(searchFragment)
            .add(R.id.fragment_container, commuteFragment, TAG_COMMUTE)
            .commit()
        activeFragment = commuteFragment
    }

    private fun restoreFragments() {
        commuteFragment = supportFragmentManager.findFragmentByTag(TAG_COMMUTE) as? CommuteFragment
            ?: CommuteFragment()
        searchFragment = supportFragmentManager.findFragmentByTag(TAG_SEARCH) as? SearchFragment
            ?: SearchFragment()
        liveTrainsFragment = supportFragmentManager.findFragmentByTag(TAG_LIVE_TRAINS) as? LiveTrainsFragment
            ?: LiveTrainsFragment()
        settingsFragment = supportFragmentManager.findFragmentByTag(TAG_SETTINGS) as? SettingsFragment
            ?: SettingsFragment()
        activeFragment = listOf(commuteFragment, searchFragment, liveTrainsFragment, settingsFragment)
            .firstOrNull { it.isVisible } ?: commuteFragment
    }

    private fun showFragment(fragment: Fragment) {
        if (fragment == activeFragment) return

        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            show(fragment)
            commit()
        }
        activeFragment = fragment
    }

    companion object {
        private const val TAG_COMMUTE = "commute"
        private const val TAG_LIVE_TRAINS = "live_trains"
        private const val TAG_SETTINGS = "settings"
        private const val TAG_SEARCH = "search"
    }
}
