package com.safekids.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.safekids.R
import com.safekids.data.PreferenceManager

/**
 * SplashActivity - The modern entry point of the app.
 * Provides a sleek intro animation and redirects to the appropriate screen.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        prefManager = PreferenceManager(this)

        // Find the logo card and start a pulse animation
        val logoCard = findViewById<MaterialCardView>(R.id.cvLogo)
        logoCard.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                logoCard.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(1200)
                    .start()
            }
            .start()

        // Redirect after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            proceedToNext()
        }, 2500)
    }

    private fun proceedToNext() {
        if (isFinishing) return

        val targetActivity = if (!prefManager.onboardingDone) {
            OnboardingActivity::class.java
        } else {
            MainActivity::class.java
        }

        startActivity(Intent(this, targetActivity))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
