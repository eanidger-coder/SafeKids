package com.safekids.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.safekids.R
import com.safekids.SafeKidsApp
import com.safekids.core.ContentClassifier
import com.safekids.data.PreferenceManager
import com.safekids.data.entities.BlacklistedChannel
import com.safekids.data.entities.BlacklistedKeyword
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ParentDashboardActivity — PIN-protected parent control center.
 * Manage blacklists, view activity logs, adjust sensitivity.
 */
class ParentDashboardActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_dashboard)

        prefManager = PreferenceManager(this)

        setupBlacklistSection()
        setupSensitivitySlider()
        setupActivityLog()
    }

    private fun setupBlacklistSection() {
        val db = SafeKidsApp.instance.database

        // Add channel button
        findViewById<Button>(R.id.btnAddChannel).setOnClickListener {
            showAddDialog("הוסף ערוץ לחסימה", "שם הערוץ") { name ->
                lifecycleScope.launch {
                    db.blacklistDao().insertChannel(
                        BlacklistedChannel(channelName = name)
                    )
                    Toast.makeText(this@ParentDashboardActivity, "✓ הערוץ נוסף", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Add keyword button
        findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            showAddDialog("הוסף מילת מפתח", "מילת מפתח") { keyword ->
                lifecycleScope.launch {
                    db.blacklistDao().insertKeyword(
                        BlacklistedKeyword(keyword = keyword)
                    )
                    Toast.makeText(this@ParentDashboardActivity, "✓ המילה נוספה", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe channels
        val chipGroupChannels = findViewById<ChipGroup>(R.id.chipGroupChannels)
        lifecycleScope.launch {
            db.blacklistDao().getAllChannels().collectLatest { channels ->
                chipGroupChannels.removeAllViews()
                channels.forEach { channel ->
                    val chip = Chip(this@ParentDashboardActivity).apply {
                        text = channel.channelName
                        isCloseIconVisible = true
                        setOnCloseIconClickListener {
                            lifecycleScope.launch {
                                db.blacklistDao().deleteChannel(channel)
                            }
                        }
                    }
                    chipGroupChannels.addView(chip)
                }
            }
        }

        // Observe keywords
        val chipGroupKeywords = findViewById<ChipGroup>(R.id.chipGroupKeywords)
        lifecycleScope.launch {
            db.blacklistDao().getAllKeywords().collectLatest { keywords ->
                chipGroupKeywords.removeAllViews()
                keywords.forEach { keyword ->
                    val chip = Chip(this@ParentDashboardActivity).apply {
                        text = keyword.keyword
                        isCloseIconVisible = true
                        setOnCloseIconClickListener {
                            lifecycleScope.launch {
                                db.blacklistDao().deleteKeyword(keyword)
                            }
                        }
                    }
                    chipGroupKeywords.addView(chip)
                }
            }
        }
    }

    private fun setupSensitivitySlider() {
        val slider = findViewById<Slider>(R.id.sliderSensitivity)
        val tvSensitivityLabel = findViewById<TextView>(R.id.tvSensitivityLabel)

        // Set initial value
        val currentLevel = prefManager.sensitivityLevel
        slider.value = when (currentLevel) {
            ContentClassifier.SensitivityLevel.STRICT -> 0f
            ContentClassifier.SensitivityLevel.BALANCED -> 1f
            ContentClassifier.SensitivityLevel.RELAXED -> 2f
        }
        tvSensitivityLabel.text = currentLevel.labelHe

        slider.addOnChangeListener { _, value, _ ->
            val level = when (value.toInt()) {
                0 -> ContentClassifier.SensitivityLevel.STRICT
                2 -> ContentClassifier.SensitivityLevel.RELAXED
                else -> ContentClassifier.SensitivityLevel.BALANCED
            }
            prefManager.sensitivityLevel = level
            tvSensitivityLabel.text = level.labelHe
        }
    }

    private fun setupActivityLog() {
        val tvLogSummary = findViewById<TextView>(R.id.tvLogSummary)
        val db = SafeKidsApp.instance.database

        lifecycleScope.launch {
            db.blockedEventDao().getRecent(20).collectLatest { events ->
                if (events.isEmpty()) {
                    tvLogSummary.text = "אין אירועים עדיין"
                } else {
                    val logText = events.joinToString("\n\n") { event ->
                        val time = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(event.timestamp))
                        "🛑 $time\n${event.videoTitle}\nסיבה: ${translateReason(event.reason)}\nמילים: ${event.matchedTerms}"
                    }
                    tvLogSummary.text = logText
                }
            }
        }
    }

    private fun translateReason(reason: String): String = when (reason) {
        "keyword" -> "מילת מפתח"
        "escalation" -> "הסלמה הדרגתית"
        "blacklist" -> "רשימה שחורה"
        else -> reason
    }

    private fun showAddDialog(title: String, hint: String, onAdd: (String) -> Unit) {
        val editText = EditText(this).apply {
            this.hint = hint
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("הוסף") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    onAdd(text)
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
