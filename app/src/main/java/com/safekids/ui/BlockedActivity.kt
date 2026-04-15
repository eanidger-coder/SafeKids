package com.safekids.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.safekids.R

/**
 * BlockedActivity — shown when violent content is detected.
 * Child-friendly, non-scary design with a friendly message.
 */
class BlockedActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SafeKids-Blocked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val blockedTitle = intent.getStringExtra("blocked_title") ?: ""
        val blockedReason = intent.getStringExtra("blocked_reason") ?: "keyword"
        val blockedPackage = intent.getStringExtra("blocked_package") ?: ""

        // Set the appropriate message based on reason
        val messageView = findViewById<TextView>(R.id.tvBlockMessage)
        messageView.text = when (blockedReason) {
            "escalation" -> "שמנו לב שהסרטונים הולכים\nולהיות פחות מתאימים.\nבוא נבחר משהו אחר! \uD83C\uDF08"
            "blacklist" -> "הסרטון הזה נחסם על ידי ההורים.\nבוא נמצא סרטון יותר כיף! \uD83C\uDF88"
            else -> getString(R.string.block_message)
        }

        // Back to safe videos button — relaunch the same app fresh (YouTube Kids
        // / YouTube) so the child lands on a clean home page instead of being
        // kicked out to the phone home screen.
        findViewById<Button>(R.id.btnBackToSafe).setOnClickListener {
            returnToSafeHome(blockedPackage)
            finish()
        }

        // Parent unlock button
        findViewById<Button>(R.id.btnParentUnlock).setOnClickListener {
            val pinIntent = Intent(this, PinActivity::class.java).apply {
                putExtra("mode", "unlock")
            }
            startActivity(pinIntent)
            finish()
        }

        // Prevent back button from bypassing the block (works on Android 13+)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back button behaves like "back to safe" — return to the clean
                // home of the source app rather than exit.
                returnToSafeHome(blockedPackage)
                finish()
            }
        })
    }

    /**
     * Re-open the source app (YouTube Kids / YouTube) on a clean home screen
     * so the child continues inside the app, just not on the blocked video.
     * Falls back to the Android home launcher if the source app can't be
     * re-launched (uninstalled / unknown package).
     */
    private fun returnToSafeHome(sourcePackage: String) {
        val pm = packageManager
        val launchIntent = if (sourcePackage.isNotEmpty()) {
            pm.getLaunchIntentForPackage(sourcePackage)
        } else null

        if (launchIntent != null) {
            launchIntent.apply {
                // Clear the task so the child lands on the app's home screen,
                // not back on the offending video.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                startActivity(launchIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to relaunch $sourcePackage, falling back to home", e)
            }
        }

        // Fallback — Android home launcher
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }
}
