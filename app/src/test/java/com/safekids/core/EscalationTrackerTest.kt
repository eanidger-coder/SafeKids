package com.safekids.core

import com.safekids.data.dao.SessionDao
import com.safekids.data.entities.ViewingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EscalationTracker — Layer 2 of the filtering engine.
 *
 * The tracker should flag sessions where content gradually becomes more
 * violent, even when no single video crosses the block threshold.
 */
class EscalationTrackerTest {

    /** Minimal in-memory fake DAO that satisfies the interface for unit tests. */
    private class FakeSessionDao : SessionDao {
        val inserted = mutableListOf<ViewingSession>()
        override suspend fun getSessionVideos(sessionId: String): List<ViewingSession> =
            inserted.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
        override suspend fun getRecentVideos(limit: Int): List<ViewingSession> =
            inserted.sortedByDescending { it.timestamp }.take(limit)
        override fun getAllSessions(): Flow<List<ViewingSession>> = flowOf(inserted.toList())
        override suspend fun insert(session: ViewingSession) { inserted.add(session) }
        override suspend fun deleteOlderThan(before: Long) {
            inserted.removeAll { it.timestamp < before }
        }
    }

    private lateinit var dao: FakeSessionDao
    private lateinit var tracker: EscalationTracker

    @Before
    fun setUp() {
        dao = FakeSessionDao()
        tracker = EscalationTracker(dao)
    }

    private fun contentScore(value: Float): ContentClassifier.ContentScore =
        ContentClassifier.ContentScore(
            totalScore = value,
            categories = emptyList(),
            isBlocked = value >= 0.5f
        )

    @Test
    fun `single video should not trigger escalation`() = runBlocking {
        val result = tracker.recordAndAnalyze("video1", "channel", contentScore(0.3f))
        assertFalse(result.isEscalating)
        assertEquals(EscalationTracker.Trend.SAFE, result.trend)
        assertEquals(1, result.videoCount)
    }

    @Test
    fun `consistently safe content should remain safe`() = runBlocking {
        repeat(5) { tracker.recordAndAnalyze("v$it", "ch", contentScore(0.1f)) }
        val result = tracker.recordAndAnalyze("v5", "ch", contentScore(0.1f))
        assertFalse(result.isEscalating)
        assertEquals(EscalationTracker.Trend.SAFE, result.trend)
    }

    @Test
    fun `rapidly rising scores should be flagged as escalating or critical`() = runBlocking {
        // 0.1 -> 0.3 -> 0.5 -> 0.7 -> 0.9 — strong positive slope and high average
        val series = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        var result: EscalationTracker.EscalationResult? = null
        for ((i, v) in series.withIndex()) {
            result = tracker.recordAndAnalyze("v$i", "ch", contentScore(v))
        }
        assertNotNull(result)
        assertTrue("Gradient should be positive", result!!.gradient > 0f)
        assertTrue(
            "Should be flagged as escalating/critical",
            result.isEscalating ||
                result.trend == EscalationTracker.Trend.ESCALATING ||
                result.trend == EscalationTracker.Trend.CRITICAL
        )
    }

    @Test
    fun `flat mid-score content should not be flagged as escalating`() = runBlocking {
        repeat(5) { tracker.recordAndAnalyze("v$it", "ch", contentScore(0.4f)) }
        val result = tracker.recordAndAnalyze("v5", "ch", contentScore(0.4f))
        // Flat slope should not trigger ESCALATING/CRITICAL
        assertFalse(
            "Flat (non-rising) scores must not be escalating",
            result.trend == EscalationTracker.Trend.ESCALATING ||
                result.trend == EscalationTracker.Trend.CRITICAL
        )
    }

    @Test
    fun `declining scores should not be flagged`() = runBlocking {
        val series = listOf(0.9f, 0.7f, 0.5f, 0.3f, 0.1f)
        var result: EscalationTracker.EscalationResult? = null
        for ((i, v) in series.withIndex()) {
            result = tracker.recordAndAnalyze("v$i", "ch", contentScore(v))
        }
        assertNotNull(result)
        assertFalse("Declining series must not escalate", result!!.isEscalating)
        assertTrue("Gradient should be non-positive", result.gradient <= 0f)
    }

    @Test
    fun `startNewSession should reset analysis window`() = runBlocking {
        // Build up a rising pattern
        listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEachIndexed { i, v ->
            tracker.recordAndAnalyze("v$i", "ch", contentScore(v))
        }
        val previousSession = tracker.getCurrentSessionId()

        tracker.startNewSession()

        val newSession = tracker.getCurrentSessionId()
        assertNotEquals("Session id must change after reset", previousSession, newSession)

        val firstAfterReset = tracker.recordAndAnalyze("new", "ch", contentScore(0.4f))
        assertEquals("Video count should restart at 1", 1, firstAfterReset.videoCount)
        assertEquals("Trend should be SAFE on fresh session", EscalationTracker.Trend.SAFE, firstAfterReset.trend)
    }

    @Test
    fun `viewing sessions should be persisted via dao`() = runBlocking {
        tracker.recordAndAnalyze("title-a", "channel-a", contentScore(0.2f))
        tracker.recordAndAnalyze("title-b", "channel-b", contentScore(0.6f))

        assertEquals(2, dao.inserted.size)
        assertEquals("title-a", dao.inserted[0].videoTitle)
        assertEquals("channel-b", dao.inserted[1].channelName)
        // All videos in the current tracker session share the same session id
        val distinctIds = dao.inserted.map { it.sessionId }.distinct()
        assertEquals(1, distinctIds.size)
    }

    @Test
    fun `escalation window should only consider recent videos`() = runBlocking {
        // Start with many very-high scores; then a long safe tail
        repeat(3) { tracker.recordAndAnalyze("bad$it", "ch", contentScore(0.9f)) }
        repeat(5) { tracker.recordAndAnalyze("good$it", "ch", contentScore(0.05f)) }
        val result = tracker.recordAndAnalyze("good-final", "ch", contentScore(0.05f))

        // Within the recent window the slope is ~0 and the average is very low.
        assertFalse("Escalation must look at recent window, not ancient history", result.isEscalating)
        assertTrue("Recent-window average should be low", result.sessionAverage < 0.2f)
    }
}
