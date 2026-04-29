package com.focusguard.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.focusguard.app.R
import com.focusguard.app.data.Prefs
import com.focusguard.app.databinding.ActivityMainBinding
import com.focusguard.app.service.MonitorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val dashboardFragment = DashboardFragment()
    private val sessionFragment   = SessionFragment()
    private val settingsFragment  = SettingsFragment()

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            dashboardFragment.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!PermissionHelper.hasUsageStats(this) || !PermissionHelper.hasOverlay(this)) {
            startActivity(Intent(this, PermissionSetupActivity::class.java))
        }

        setupBottomNav()
        loadFragment(dashboardFragment)

        if (Prefs.isMonitoringOn(this)) {
            MonitorService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            sessionReceiver,
            IntentFilter(MonitorService.ACTION_SESSION_UPDATE),
            RECEIVER_NOT_EXPORTED
        )
        dashboardFragment.refresh()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(sessionReceiver)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { loadFragment(dashboardFragment); true }
                R.id.nav_session   -> { loadFragment(sessionFragment);   true }
                R.id.nav_settings  -> { loadFragment(settingsFragment);  true }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
