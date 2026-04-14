package com.safekids.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safekids.R
import com.safekids.data.PreferenceManager

/**
 * PinActivity — PIN entry for parent authentication.
 * Supports two modes: "set" (first-time) and "verify" (unlock).
 */
class PinActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager
    private var mode = "verify" // "set", "confirm", "verify", "unlock"
    private var pendingPin = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvPinDisplay: TextView
    private var currentPin = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        prefManager = PreferenceManager(this)
        mode = intent.getStringExtra("mode") ?: if (prefManager.isPinSet()) "verify" else "set"

        tvTitle = findViewById(R.id.tvPinTitle)
        tvPinDisplay = findViewById(R.id.tvPinDisplay)

        updateTitle()
        setupKeypad()
    }

    private fun updateTitle() {
        tvTitle.text = when (mode) {
            "set" -> getString(R.string.pin_set_title)
            "confirm" -> getString(R.string.pin_confirm_title)
            "verify", "unlock" -> getString(R.string.pin_title)
            else -> getString(R.string.pin_title)
        }
    }

    private fun setupKeypad() {
        // Number buttons 0-9
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        for (i in buttonIds.indices) {
            findViewById<Button>(buttonIds[i]).setOnClickListener {
                if (currentPin.length < 4) {
                    currentPin.append(i)
                    updatePinDisplay()
                    if (currentPin.length == 4) {
                        onPinComplete()
                    }
                }
            }
        }

        // Backspace button
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin.deleteCharAt(currentPin.length - 1)
                updatePinDisplay()
            }
        }
    }

    private fun updatePinDisplay() {
        val dots = "●".repeat(currentPin.length) + "○".repeat(4 - currentPin.length)
        tvPinDisplay.text = dots
    }

    private fun onPinComplete() {
        when (mode) {
            "set" -> {
                pendingPin = currentPin.toString()
                currentPin.clear()
                mode = "confirm"
                updateTitle()
                updatePinDisplay()
            }
            "confirm" -> {
                if (currentPin.toString() == pendingPin) {
                    prefManager.parentPin = pendingPin
                    Toast.makeText(this, "✓ הקוד נשמר בהצלחה", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.pin_error_mismatch), Toast.LENGTH_SHORT).show()
                    currentPin.clear()
                    mode = "set"
                    updateTitle()
                    updatePinDisplay()
                }
            }
            "verify", "unlock" -> {
                if (prefManager.verifyPin(currentPin.toString())) {
                    ParentSessionManager.authenticate()
                    
                    val targetActivity = intent.getStringExtra("target_activity")
                    if (targetActivity != null) {
                        try {
                            // If we weren't started for a result (e.g. from BaseParentActivity),
                            // we need to start the activity ourselves.
                            if (callingActivity == null) {
                                val targetClass = Class.forName(targetActivity)
                                val targetIntent = Intent(this, targetClass)
                                targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(targetIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.pin_error_wrong), Toast.LENGTH_SHORT).show()
                    currentPin.clear()
                    updatePinDisplay()
                }
            }
        }
    }
}
