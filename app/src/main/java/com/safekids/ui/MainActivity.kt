package com.safekids.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.safekids.R
import com.safekids.data.PreferenceManager
import com.google.android.material.card.MaterialCardView

/**
 * MainActivity — Simplified 'One-Button' Control Center.
 * Handles app activation and status monitoring.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager
    private lateinit var tvStatus: TextView
    private lateinit var ivPowerIcon: ImageView
    private lateinit var btnToggle: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefManager = PreferenceManager(this)

        tvStatus = findViewById(R.id.tvProtectionStatus)
        ivPowerIcon = findViewById(R.id.ivPowerIcon)
        btnToggle = findViewById(R.id.btnToggleProtection)

        updateUI()

        btnToggle.setOnClickListener {
            prefManager.protectionEnabled = !prefManager.protectionEnabled
            updateUI()
        }

        findViewById<android.view.View>(R.id.btnOpenDashboard).setOnClickListener {
            startActivity(Intent(this, ParentDashboardActivity::class.java))
        }
    }

    private fun updateUI() {
        val enabled = prefManager.protectionEnabled
        if (enabled) {
            tvStatus.text = getString(R.string.main_protection_active)
            tvStatus.setTextColor(getColor(R.color.sk_neon_cyan))
            ivPowerIcon.setColorFilter(getColor(R.color.sk_neon_cyan))
            btnToggle.strokeColor = getColor(R.color.sk_neon_cyan)
        } else {
            tvStatus.text = getString(R.string.main_protection_inactive)
            tvStatus.setTextColor(getColor(R.color.sk_white))
            ivPowerIcon.setColorFilter(getColor(R.color.sk_white))
            btnToggle.strokeColor = getColor(R.color.sk_glass_outline)
        }
    }
}
