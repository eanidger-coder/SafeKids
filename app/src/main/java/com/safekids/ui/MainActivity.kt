package com.safekids.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.safekids.R
import com.safekids.SafeKidsApp
import com.safekids.data.PreferenceManager
import kotlinx.coroutines.*

/**
 * MainActivity — the home screen showing protection status and quick stats.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startActivity(Intent(this, ParentDashboardActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = PreferenceManager(this)

        // First launch → Onboarding
        if (!prefManager.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        if (prefManager.onboardingDone) {
            updateProtectionStatus()
            updateStats()
        }
    }

    private fun setupUI() {
        // Protection status
        updateProtectionStatus()

        // Dashboard button (PIN protected)
        findViewById<Button>(R.id.btnDashboard).setOnClickListener {
            val intent = Intent(this, PinActivity::class.java).apply {
                putExtra("mode", "verify")
            }
            pinLauncher.launch(intent)
        }

        // Quick toggle
        findViewById<Button>(R.id.btnToggleProtection).setOnClickListener {
            // Require PIN to disable protection
            if (prefManager.protectionEnabled) {
                val intent = Intent(this, PinActivity::class.java).apply {
                    putExtra("mode", "verify")
                }
                pinLauncher.launch(intent)
            }
        }

        // Update stats
        updateStats()
    }

    private fun updateProtectionStatus() {
        val tvStatus = findViewById<TextView>(R.id.tvProtectionStatus)
        val ivStatus = findViewById<ImageView>(R.id.ivProtectionIcon)

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        if (accessibilityEnabled && prefManager.protectionEnabled) {
            tvStatus.text = getString(R.string.main_protection_active)
            tvStatus.setTextColor(getColor(R.color.sk_safe_green))
        } else {
            tvStatus.text = getString(R.string.main_protection_inactive)
            tvStatus.setTextColor(getColor(R.color.sk_error))
        }
    }

    private fun updateStats() {
        scope.launch {
            val db = SafeKidsApp.instance.database
            val todayStart = getTodayStartMillis()

            val blockedToday = withContext(Dispatchers.IO) {
                db.blockedEventDao().countSince(todayStart)
            }
            val escalations = withContext(Dispatchers.IO) {
                db.blockedEventDao().countEscalationsSince(todayStart)
            }

            findViewById<TextView>(R.id.tvBlockedCount).text = blockedToday.toString()
            findViewById<TextView>(R.id.tvEscalationCount).text = escalations.toString()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun getTodayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
