package com.safekids.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.safekids.R
import com.safekids.data.PreferenceManager

/**
 * OnboardingActivity — guides the parent through initial setup.
 * Steps: Welcome → Set PIN → Accessibility → Overlay → Done
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager
    private var currentStep = 0

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var btnAction: Button
    private lateinit var tvStepIndicator: TextView

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            nextStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefManager = PreferenceManager(this)

        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDescription = findViewById(R.id.tvStepDescription)
        btnAction = findViewById(R.id.btnOnboardingAction)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)

        showStep(0)
    }

    private fun showStep(step: Int) {
        currentStep = step
        tvStepIndicator.text = "${step + 1}/5"

        when (step) {
            0 -> { // Welcome
                tvStepTitle.text = getString(R.string.onboarding_welcome_title)
                tvStepDescription.text = getString(R.string.onboarding_welcome_subtitle)
                btnAction.text = getString(R.string.btn_next)
                btnAction.setOnClickListener { nextStep() }
            }
            1 -> { // Set PIN
                tvStepTitle.text = getString(R.string.onboarding_step_pin)
                tvStepDescription.text = getString(R.string.onboarding_step_pin_subtitle)
                btnAction.text = getString(R.string.btn_next)
                btnAction.setOnClickListener {
                    val intent = Intent(this, PinActivity::class.java).apply {
                        putExtra("mode", "set")
                    }
                    pinLauncher.launch(intent)
                }
            }
            2 -> { // Accessibility Service
                tvStepTitle.text = getString(R.string.onboarding_step_accessibility)
                tvStepDescription.text = getString(R.string.onboarding_step_accessibility_subtitle)
                btnAction.text = getString(R.string.btn_grant_permission)
                btnAction.setOnClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
            3 -> { // Overlay permission
                tvStepTitle.text = getString(R.string.onboarding_step_overlay)
                tvStepDescription.text = getString(R.string.onboarding_step_overlay_subtitle)
                btnAction.text = getString(R.string.btn_grant_permission)
                btnAction.setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            4 -> { // Done
                tvStepTitle.text = getString(R.string.onboarding_step_done)
                tvStepDescription.text = getString(R.string.onboarding_step_done_subtitle)
                btnAction.text = getString(R.string.btn_start)
                btnAction.setOnClickListener {
                    prefManager.onboardingDone = true
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-advance if permission was granted
        if (currentStep == 2 && isAccessibilityServiceEnabled()) {
            nextStep()
        } else if (currentStep == 3 && Settings.canDrawOverlays(this)) {
            nextStep()
        }
    }

    private fun nextStep() {
        if (currentStep < 4) {
            showStep(currentStep + 1)
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
}
