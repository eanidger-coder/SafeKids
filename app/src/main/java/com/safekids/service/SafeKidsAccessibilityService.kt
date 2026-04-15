package com.safekids.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.safekids.R
import com.safekids.SafeKidsApp
import com.safekids.core.ChannelAnalyzer
import com.safekids.core.ContentClassifier
import com.safekids.core.EscalationTracker
import com.safekids.data.PreferenceManager
import com.safekids.data.entities.BlockedEvent
import com.safekids.ui.BlockedActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * SafeKidsAccessibilityService — Monitors YouTube Kids for safety.
 *
 * IMPORTANT: This is an AccessibilityService, NOT a foreground service.
 * The system manages its lifecycle. We do NOT call startForeground().
 */
class SafeKidsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SafeKids"
        private val MONITORED_PACKAGES = setOf(
            "com.google.android.apps.youtube.kids",
            "com.google.android.youtube"
        )
        private const val DEBOUNCE_MS = 1200L
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "safekids_service_v3"
    }

    private lateinit var classifier: ContentClassifier
    private lateinit var escalationTracker: EscalationTracker
    private lateinit var channelAnalyzer: ChannelAnalyzer
    private lateinit var prefManager: PreferenceManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastScanTime = 0L
    private var lastBlockedTitle = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SafeKids Service Connected")

        try {
            prefManager = PreferenceManager(applicationContext)
            val app = application as SafeKidsApp
            val db = app.database

            classifier = ContentClassifier().apply {
                setSensitivity(prefManager.sensitivityLevel)
            }
            channelAnalyzer = ChannelAnalyzer(db.blacklistDao())
            escalationTracker = EscalationTracker(db.sessionDao())

            // Show a regular notification (NOT startForeground!)
            showServiceNotification()

            // Observe blacklist changes
            serviceScope.launch {
                db.blacklistDao().getAllChannels().collectLatest {
                    channelAnalyzer.refreshBlacklist()
                }
            }
            serviceScope.launch {
                db.blacklistDao().getAllKeywords().collectLatest {
                    val customKeywords = channelAnalyzer.refreshCustomKeywords()
                    classifier.updateCustomBlacklist(customKeywords)
                }
            }

            Log.i(TAG, "SafeKids Service fully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service", e)
        }
    }

    private fun showServiceNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeKids Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                lightColor = Color.CYAN
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = "Notification showing SafeKids protection status"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_security_shield)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        // Use regular notify — AccessibilityService does NOT need startForeground
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check if protection is enabled (with safety for lateinit)
        if (!::prefManager.isInitialized || !prefManager.protectionEnabled) return

        val packageName = event.packageName?.toString() ?: return
        if (!MONITORED_PACKAGES.contains(packageName)) return

        val now = System.currentTimeMillis()
        if (now - lastScanTime < DEBOUNCE_MS) return
        lastScanTime = now

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scanScreen()
        }
    }

    private fun scanScreen() {
        val rootNode = rootInActiveWindow ?: return

        try {
            val allText = StringBuilder()
            val titleCandidates = mutableListOf<String>()
            val channelCandidates = mutableListOf<String>()

            // Single-pass extraction: collect all text AND smart-extract title/channel
            extractAllContent(rootNode, allText, titleCandidates, channelCandidates, depth = 0)

            val screenText = allText.toString()
            if (screenText.isBlank()) return

            // Pick best title/channel candidates
            val videoTitle = pickBestTitle(titleCandidates)
            val channelName = pickBestChannel(channelCandidates)

            val score = classifier.classify(screenText)
            val channelBlocked = channelName.isNotEmpty() && channelAnalyzer.isBlacklisted(channelName)

            serviceScope.launch {
                val escalation = escalationTracker.recordAndAnalyze(videoTitle, channelName, score)
                val shouldBlock = score.isBlocked || channelBlocked || escalation.isEscalating

                if (shouldBlock) {
                    val blockTitle = videoTitle.ifEmpty { screenText.take(50).trim() }
                    if (blockTitle == lastBlockedTitle) return@launch
                    lastBlockedTitle = blockTitle

                    Log.w(TAG, "BLOCKED: '$blockTitle' | score=${score.totalScore} | channel=$channelName | reason=${
                        if (channelBlocked) "blacklist" else if (escalation.isEscalating) "escalation" else "keyword"
                    }")

                    val app = application as SafeKidsApp
                    app.database.blockedEventDao().insert(
                        BlockedEvent(
                            videoTitle = blockTitle,
                            channelName = channelName,
                            reason = if (channelBlocked) "blacklist" else if (escalation.isEscalating) "escalation" else "keyword",
                            matchedTerms = score.categories.flatMap { it.matchedTerms }.joinToString(", "),
                            violenceScore = score.totalScore
                        )
                    )

                    showBlockScreen(blockTitle,
                        if (channelBlocked) "blacklist" else if (escalation.isEscalating) "escalation" else "keyword",
                        packageName
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning screen", e)
        }
    }

    /**
     * Single-pass content extraction — collects all text AND identifies
     * likely video title and channel name based on text properties.
     */
    private fun extractAllContent(
        node: AccessibilityNodeInfo,
        allText: StringBuilder,
        titleCandidates: MutableList<String>,
        channelCandidates: MutableList<String>,
        depth: Int
    ) {
        // Collect text
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""

        if (text.isNotEmpty()) {
            allText.append(text).append(" ")
            categorizeText(text, titleCandidates, channelCandidates)
        }
        if (contentDesc.isNotEmpty() && contentDesc != text) {
            allText.append(contentDesc).append(" ")
            categorizeText(contentDesc, titleCandidates, channelCandidates)
        }

        // Recurse into children (max depth 15 to avoid stack overflow)
        if (depth < 15) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    extractAllContent(child, allText, titleCandidates, channelCandidates, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }
    }

    /**
     * Categorize a text string as a potential video title or channel name
     * based on length, character content, and patterns.
     */
    private fun categorizeText(text: String, titles: MutableList<String>, channels: MutableList<String>) {
        val len = text.length

        // Skip very short or very long text (UI labels, descriptions)
        if (len < 3 || len > 200) return

        // Skip common UI labels
        if (text in setOf("הבא", "חזרה", "חפש", "בית", "ספרייה", "Search", "Home", "Library",
                "More", "Settings", "עוד", "הגדרות", "שתף", "Share")) return

        // Video titles are typically 10-120 chars and contain meaningful content
        if (len in 10..120) {
            titles.add(text)
        }

        // Channel names are typically 3-50 chars and shorter than titles
        if (len in 3..50) {
            channels.add(text)
        }
    }

    private fun pickBestTitle(candidates: List<String>): String {
        // Prefer longer, more descriptive text as the title
        return candidates
            .filter { it.length >= 10 }
            .maxByOrNull { it.length } ?: candidates.firstOrNull() ?: ""
    }

    private fun pickBestChannel(candidates: List<String>): String {
        // Prefer shorter text that looks like a channel name
        return candidates
            .filter { it.length in 3..40 }
            .filter { !it.contains("\n") }
            .minByOrNull { it.length } ?: ""
    }

    private fun showBlockScreen(title: String, reason: String, sourcePackage: String) {
        // Go back one step inside the source app so the offending video is no
        // longer on screen beneath our overlay, then present the block activity.
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (_: Exception) { /* best-effort */ }

        val intent = Intent(applicationContext, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("blocked_title", title)
            putExtra("blocked_reason", reason)
            putExtra("blocked_package", sourcePackage)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "SafeKids Service interrupted")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Cancel the notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
}
