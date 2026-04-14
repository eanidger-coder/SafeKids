package com.safekids.ui

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val blockedTitle = intent.getStringExtra("blocked_title") ?: ""
        val blockedReason = intent.getStringExtra("blocked_reason") ?: "keyword"

        // Set the appropriate message based on reason
        val messageView = findViewById<TextView>(R.id.tvBlockMessage)
        messageView.text = when (blockedReason) {
            "escalation" -> "שמנו לב שהסרטונים הולכים\nולהיות פחות מתאימים.\nבוא נבחר משהו אחר! \uD83C\uDF08"
            "blacklist" -> "הסרטון הזה נחסם על ידי ההורים.\nבוא נמצא סרטון יותר כיף! \uD83C\uDF88"
            else -> getString(R.string.block_message)
        }

        // Back to safe videos button
        findViewById<Button>(R.id.btnBackToSafe).setOnClickListener {
            // Navigate to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
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
                // Do nothing — child must use the buttons
            }
        })
    }
}
