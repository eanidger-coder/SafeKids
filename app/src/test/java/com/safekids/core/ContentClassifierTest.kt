package com.safekids.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ContentClassifier — the core detection engine.
 */
class ContentClassifierTest {

    private lateinit var classifier: ContentClassifier

    @Before
    fun setup() {
        classifier = ContentClassifier()
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.BALANCED)
    }

    @Test
    fun `safe content should not be blocked`() {
        val score = classifier.classify("פאו פטרול מציל חתלתול קטן")
        assertFalse("Safe content should not be blocked", score.isBlocked)
        assertTrue("Safe content score should be low", score.totalScore <= 0.2f)
    }

    @Test
    fun `peppa pig should not be blocked`() {
        val score = classifier.classify("Peppa Pig goes on holiday")
        assertFalse("Peppa Pig should be safe", score.isBlocked)
    }

    @Test
    fun `physical violence keywords should be detected`() {
        val score = classifier.classify("ספיידרמן נלחם ומכה את הגובלין")
        assertTrue("Violence should be detected", score.isBlocked)
        assertTrue("Score should be high", score.totalScore >= 0.5f)
        assertTrue("Category should be VIOLENCE_PHYSICAL",
            score.categories.any { it.category == ContentClassifier.Category.VIOLENCE_PHYSICAL })
    }

    @Test
    fun `huggy wuggy horror content should be blocked`() {
        val score = classifier.classify("Huggy Wuggy scary jumpscare compilation")
        assertTrue("Horror kids content should be blocked", score.isBlocked)
        assertTrue("Score should be high", score.totalScore >= 0.7f)
    }

    @Test
    fun `hebrew horror keywords should be detected`() {
        val score = classifier.classify("האגי ואגי סקיבידי טוילט מפחיד")
        assertTrue("Hebrew horror should be detected", score.isBlocked)
    }

    @Test
    fun `elsagate content should be blocked with maximum score`() {
        val score = classifier.classify("Elsa injection Spider-Man pregnant bad baby")
        assertTrue("Elsagate should be blocked", score.isBlocked)
        assertTrue("Elsagate score should be maximum", score.totalScore >= 0.9f)
    }

    @Test
    fun `weapons should be detected`() {
        val score = classifier.classify("ילד משחק עם אקדח וחרב")
        assertTrue("Weapons should be detected", score.isBlocked)
    }

    @Test
    fun `verbal violence in Hebrew should be detected`() {
        val score = classifier.classify("הוא טיפש ומטומטם בריון")
        assertTrue("Verbal violence should be detected", score.isBlocked)
    }

    @Test
    fun `ninja turtles fight should be detected`() {
        val score = classifier.classify("צבי הנינג'ה נלחמים קרב גדול")
        assertTrue("Ninja turtles fight should be detected", score.isBlocked)
    }

    @Test
    fun `custom blacklist should work`() {
        classifier.updateCustomBlacklist(listOf("bad_channel_123"))
        val score = classifier.classify("video from bad_channel_123")
        assertTrue("Custom blacklisted content should be blocked", score.isBlocked)
        assertEquals(1.0f, score.totalScore, 0.01f)
    }

    @Test
    fun `strict sensitivity should block more`() {
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.STRICT)
        val score = classifier.classify("באטמן נגד ג'וקר")
        // With strict, even lower scores should block
        assertTrue("Strict should have lower threshold", classifier.blockThreshold <= 0.3f)
    }

    @Test
    fun `relaxed sensitivity should allow more`() {
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.RELAXED)
        assertTrue("Relaxed should have higher threshold", classifier.blockThreshold >= 0.7f)
    }
}
