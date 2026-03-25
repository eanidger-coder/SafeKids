package com.safekids.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.safekids.SafeKidsApp
import com.safekids.core.ChannelAnalyzer
import com.safekids.core.ContentClassifier
import com.safekids.core.EscalationTracker
import com.safekids.data.PreferenceManager
import com.safekids.data.entities.BlockedEvent
import com.safekids.ui.BlockedActivity
import kotlinx.coroutines.*

/**
 * SafeKidsAccessibilityService — monitors YouTube Kids in real-time.
 *
 * Walks the accessibility tree to extract video titles and channel names,
 * feeds them through the 3-layer detection engine, and triggers blocking
 * when dangerous content is detected.
 */
class SafeKidsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SafeKids"
        private const val YT_KIDS_PACKAGE = "com.google.android.apps.youtube.kids"
        private const val DEBOUNCE_MS = 1500L // avoid scanning too frequently
    }

    private lateinit var classifier: ContentClassifier
    private lateinit var escalationTracker: EscalationTracker
    private lateinit var channelAnalyzer: ChannelAnalyzer
    private lateinit var prefManager: PreferenceManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastScanTime = 0L
    private var lastBlockedTitle = ""

    override fun onCreate() {
        super.onCreate()
        val app = SafeKidsApp.instance
        val db = app.database

        prefManager = PreferenceManager(this)
        classifier = ContentClassifier().apply {
            setSensitivity(prefManager.sensitivityLevel)
        }
        escalationTracker = EscalationTracker(db.sessionDao())
        channelAnalyzer = ChannelAnalyzer(db.blacklistDao())

        // Load blacklists into memory
        serviceScope.launch {
            channelAnalyzer.refreshBlacklist()
            val customKeywords = channelAnalyzer.refreshCustomKeywords()
            classifier.updateCustomBlacklist(customKeywords)
        }

        Log.i(TAG, "SafeKids accessibility service created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefManager.protectionEnabled) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != YT_KIDS_PACKAGE) return

        // Debounce rapid events
        val now = System.currentTimeMillis()
        if (now - lastScanTime < DEBOUNCE_MS) return
        lastScanTime = now

        // Only process content change and window state events
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                scanScreen()
            }
        }
    }

    /**
     * Walk the accessibility node tree and extract text content.
     */
    private fun scanScreen() {
        val rootNode = rootInActiveWindow ?: return

        val allText = StringBuilder()
        val videoTitle = extractVideoTitle(rootNode)
        val channelName = extractChannelName(rootNode)

        // Collect all visible text from the screen
        collectNodeText(rootNode, allText)
        rootNode.recycle()

        val screenText = allText.toString()
        if (screenText.isBlank()) return

        // Run classification
        val score = classifier.classify(screenText)

        // Check channel blacklist
        val channelBlocked = channelName.isNotEmpty() && channelAnalyzer.isBlacklisted(channelName)

        serviceScope.launch {
            // Track escalation
            val escalation = escalationTracker.recordAndAnalyze(
                videoTitle = videoTitle,
                channelName = channelName,
                score = score
            )

            // Decision: block or allow
            val shouldBlock = score.isBlocked || channelBlocked || escalation.isEscalating

            if (shouldBlock) {
                val blockTitle = videoTitle.ifEmpty { screenText.take(50) }

                // Avoid blocking the same content repeatedly
                if (blockTitle == lastBlockedTitle) return@launch
                lastBlockedTitle = blockTitle

                // Determine reason
                val reason = when {
                    channelBlocked -> "blacklist"
                    escalation.isEscalating -> "escalation"
                    else -> "keyword"
                }

                val matchedTerms = score.categories
                    .flatMap { it.matchedTerms }
                    .joinToString(", ")

                // Log the event
                val app = SafeKidsApp.instance
                app.database.blockedEventDao().insert(
                    BlockedEvent(
                        videoTitle = blockTitle,
                        channelName = channelName,
                        reason = reason,
                        matchedTerms = matchedTerms,
                        violenceScore = score.totalScore
                    )
                )

                // Record channel violation
                if (channelName.isNotEmpty()) {
                    channelAnalyzer.recordViolation(channelName)
                }

                // Show blocking screen
                Log.w(TAG, "BLOCKED: [$reason] '$blockTitle' (score=${score.totalScore})")
                showBlockScreen(blockTitle, reason)
            }
        }
    }

    /**
     * Extract what looks like a video title from the node tree.
     */
    private fun extractVideoTitle(node: AccessibilityNodeInfo): String {
        // YouTube Kids typically shows the title in large text views
        return findNodeByHeuristic(node, maxDepth = 8, isTitle = true)
    }

    /**
     * Extract channel name from the node tree.
     */
    private fun extractChannelName(node: AccessibilityNodeInfo): String {
        return findNodeByHeuristic(node, maxDepth = 8, isTitle = false)
    }

    /**
     * Heuristic: walk tree looking for text nodes that likely represent
     * video titles or channel names based on position and content.
     */
    private fun findNodeByHeuristic(
        node: AccessibilityNodeInfo,
        maxDepth: Int,
        isTitle: Boolean
    ): String {
        if (maxDepth <= 0) return ""

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        // For title: look for longer text that isn't a button label
        if (isTitle && text.length > 10 && node.className?.toString()?.contains("TextView") == true) {
            return text
        }

        // For channel: look for shorter text near the title
        if (!isTitle && text.length in 3..40 && desc.isEmpty()) {
            return text
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByHeuristic(child, maxDepth - 1, isTitle)
            child.recycle()
            if (result.isNotEmpty()) return result
        }

        return ""
    }

    /**
     * Collect all text from the accessibility tree.
     */
    private fun collectNodeText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int = 0) {
        if (depth > 15) return // prevent infinite recursion

        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, sb, depth + 1)
            child.recycle()
        }
    }

    /**
     * Launch the block screen overlay.
     */
    private fun showBlockScreen(title: String, reason: String) {
        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("blocked_title", title)
            putExtra("blocked_reason", reason)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "SafeKids accessibility service interrupted")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
