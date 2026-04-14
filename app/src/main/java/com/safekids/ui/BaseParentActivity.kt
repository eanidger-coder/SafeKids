package com.safekids.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * BaseParentActivity — Abstract base class for all parent-controlled screens.
 * Enforces PIN authentication on every enter or resume if the session is expired.
 */
abstract class BaseParentActivity : AppCompatActivity() {

    /** Set to true after security check passes, subclasses should check this */
    protected var isSecurityCleared = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isSecurityCleared = checkSecurity()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            isSecurityCleared = checkSecurity()
        }
    }

    /**
     * Common security gate for parent screens.
     * Returns true if authenticated and safe to proceed.
     */
    private fun checkSecurity(): Boolean {
        if (isFinishing) return false

        return if (!ParentSessionManager.isAuthenticated()) {
            // Unauthenticated: Redirect to PIN
            val intent = Intent(this, PinActivity::class.java).apply {
                // Point back to this activity after success
                putExtra("target_activity", this@BaseParentActivity::class.java.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
            false
        } else {
            // Authenticated: Refresh session timer
            ParentSessionManager.touch()
            true
        }
    }
}
