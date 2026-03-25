package com.safekids.core

import com.safekids.data.dao.SessionDao
import com.safekids.data.entities.ViewingSession
import java.util.UUID

/**
 * EscalationTracker — Layer 2 of SafeKids detection engine.
 *
 * THE KEY FEATURE: Detects gradual content escalation across videos.
 * A child may start with a safe video, but YouTube recommendations
 * gradually lead to violent content. This tracker catches that pattern
 * even when no single video exceeds the block threshold.
 */
class EscalationTracker(private val sessionDao: SessionDao) {

    data class EscalationResult(
        val isEscalating: Boolean,
        val gradient: Float,          // rate of score increase
        val sessionAverage: Float,    // average violence across session
        val videoCount: Int,
        val trend: Trend
    )

    enum class Trend(val labelHe: String) {
        SAFE("בטוח"),
        RISING("עולה"),
        ESCALATING("מסלים - מסוכן"),
        CRITICAL("קריטי - חסימה מיידית")
    }

    private var currentSessionId: String = UUID.randomUUID().toString()
    private val sessionScores = mutableListOf<Float>()

    // Config
    var escalationWindow: Int = 5          // number of videos to analyze
    var gradientThreshold: Float = 0.15f   // minimum rise per video to flag
    var sessionAverageThreshold: Float = 0.4f // average score that triggers alert

    /**
     * Record a video that was just viewed and check for escalation.
     */
    suspend fun recordAndAnalyze(
        videoTitle: String,
        channelName: String,
        score: ContentClassifier.ContentScore
    ): EscalationResult {
        // Persist to database
        sessionDao.insert(
            ViewingSession(
                videoTitle = videoTitle,
                channelName = channelName,
                violenceScore = score.totalScore,
                categories = score.categories.joinToString(",") { it.category.name },
                sessionId = currentSessionId
            )
        )

        // Track in memory for fast analysis
        sessionScores.add(score.totalScore)

        return analyzeEscalation()
    }

    /**
     * Analyze the current session for escalation patterns.
     */
    private fun analyzeEscalation(): EscalationResult {
        if (sessionScores.size < 2) {
            return EscalationResult(
                isEscalating = false,
                gradient = 0f,
                sessionAverage = sessionScores.firstOrNull() ?: 0f,
                videoCount = sessionScores.size,
                trend = Trend.SAFE
            )
        }

        // Take the last N videos
        val window = sessionScores.takeLast(escalationWindow)
        val average = window.average().toFloat()

        // Calculate gradient (linear regression slope)
        val gradient = calculateGradient(window)

        // Determine trend
        val trend = when {
            gradient >= gradientThreshold * 2 && average >= sessionAverageThreshold -> Trend.CRITICAL
            gradient >= gradientThreshold && average >= sessionAverageThreshold * 0.7f -> Trend.ESCALATING
            gradient > 0.05f -> Trend.RISING
            else -> Trend.SAFE
        }

        val isEscalating = trend == Trend.ESCALATING || trend == Trend.CRITICAL

        return EscalationResult(
            isEscalating = isEscalating,
            gradient = gradient,
            sessionAverage = average,
            videoCount = sessionScores.size,
            trend = trend
        )
    }

    /**
     * Calculate the slope of violence scores across videos.
     * Positive slope = content is getting more violent over time.
     */
    private fun calculateGradient(scores: List<Float>): Float {
        if (scores.size < 2) return 0f

        val n = scores.size
        val xMean = (n - 1) / 2f
        val yMean = scores.average().toFloat()

        var numerator = 0f
        var denominator = 0f

        for (i in scores.indices) {
            val xDiff = i - xMean
            val yDiff = scores[i] - yMean
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }

        return if (denominator != 0f) numerator / denominator else 0f
    }

    /**
     * Start a new viewing session (e.g., child opened YouTube Kids fresh).
     */
    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        sessionScores.clear()
    }

    /**
     * Get the current session ID for logging purposes.
     */
    fun getCurrentSessionId(): String = currentSessionId
}
