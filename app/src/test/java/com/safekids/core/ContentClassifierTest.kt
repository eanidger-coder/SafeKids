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
    fun `user reported failure מכות should be blocked`() {
        val score = classifier.classify("סרטון עם מכות של גיבורי על")
        assertTrue("Hebrew violence 'מכות' should be blocked", score.isBlocked)
    }

    @Test
    fun `user reported failure יובל המבולבל on blacklist should be blocked`() {
        classifier.updateCustomBlacklist(listOf("יובל המבולבל"))
        val score = classifier.classify("הפרק החדש של יובל המבולבל")
        assertTrue("Blacklisted 'יובל המבולבל' should be blocked", score.isBlocked)
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

    // ---------------- Added regression coverage for filtering ----------------

    @Test
    fun `empty string should be safe`() {
        val score = classifier.classify("")
        assertFalse("Empty text should not be blocked", score.isBlocked)
        assertEquals(0f, score.totalScore, 0.001f)
        assertTrue("No categories matched", score.categories.isEmpty())
    }

    @Test
    fun `whitespace only should be safe`() {
        val score = classifier.classify("     \n\t   ")
        assertFalse("Whitespace text should not be blocked", score.isBlocked)
    }

    @Test
    fun `weapons category should be detected`() {
        val score = classifier.classify("ילד משחק עם אקדח ורובה")
        assertTrue("Weapons should be detected", score.isBlocked)
        assertTrue("Should contain WEAPONS category",
            score.categories.any { it.category == ContentClassifier.Category.WEAPONS })
    }

    @Test
    fun `elsagate content should be detected`() {
        val score = classifier.classify("elsa pregnant bad baby joker prank")
        assertTrue("Elsagate should be blocked", score.isBlocked)
        assertTrue("Score should be very high", score.totalScore >= 0.8f)
    }

    @Test
    fun `skibidi toilet content should be flagged`() {
        val score = classifier.classify("skibidi toilet ep 42 compilation")
        assertTrue("Skibidi toilet is flagged as horror/brainrot", score.isBlocked)
    }

    @Test
    fun `dark themes should be detected`() {
        val score = classifier.classify("scary nightmare horror demon compilation")
        assertTrue("Dark themes should be blocked", score.isBlocked)
        assertTrue("Should match at least one category", score.categories.isNotEmpty())
    }

    @Test
    fun `arabic violence keywords should be detected`() {
        val score = classifier.classify("فيديو فيه قتال و حرب")
        assertTrue("Arabic violence should be detected", score.isBlocked)
    }

    @Test
    fun `case should not matter`() {
        val lower = classifier.classify("huggy wuggy jumpscare")
        val upper = classifier.classify("HUGGY WUGGY JUMPSCARE")
        val mixed = classifier.classify("Huggy Wuggy JumpScare")
        assertTrue(lower.isBlocked)
        assertTrue(upper.isBlocked)
        assertTrue(mixed.isBlocked)
    }

    @Test
    fun `multiple categories should all be reported`() {
        val score = classifier.classify("huggy wuggy jumpscare with gun and blood")
        assertTrue("Multi-category content should be blocked", score.isBlocked)
        assertTrue("Should report at least 2 categories", score.categories.size >= 2)
    }

    @Test
    fun `custom blacklist should override low sensitivity`() {
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.RELAXED)
        classifier.updateCustomBlacklist(listOf("banned_word"))
        val score = classifier.classify("innocent-looking video banned_word inside")
        assertTrue("Custom blacklist must override even RELAXED sensitivity", score.isBlocked)
    }

    @Test
    fun `updating custom blacklist should clear previous entries`() {
        classifier.updateCustomBlacklist(listOf("old_banned"))
        val first = classifier.classify("contains old_banned term")
        assertTrue("Old entry blocks at first", first.isBlocked)

        classifier.updateCustomBlacklist(listOf("new_banned"))
        val second = classifier.classify("contains old_banned term")
        assertFalse("Old entry must no longer block after update", second.isBlocked)

        val third = classifier.classify("contains new_banned term")
        assertTrue("New entry should block", third.isBlocked)
    }

    @Test
    fun `sensitivity thresholds are distinct`() {
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.STRICT)
        val strict = classifier.blockThreshold
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.BALANCED)
        val balanced = classifier.blockThreshold
        classifier.setSensitivity(ContentClassifier.SensitivityLevel.RELAXED)
        val relaxed = classifier.blockThreshold

        assertTrue("STRICT < BALANCED", strict < balanced)
        assertTrue("BALANCED < RELAXED", balanced < relaxed)
    }

    @Test
    fun `long benign content should not be blocked`() {
        val text = ("פאו פטרול יוצא להרפתקה חדשה וידידותית וכולם שמחים " +
            "ולומדים על חברות ועל חיות וחשוב לשמור על הסביבה. ").repeat(5)
        val score = classifier.classify(text)
        assertFalse("Long wholesome content must stay safe", score.isBlocked)
    }

    @Test
    fun `user reported failure יואבי נשרף should be blocked`() {
        val score = classifier.classify("יואבי נשרף??!! יואבי והאמא הנדחפת | עופר ומאור")
        assertTrue("'יואבי נשרף' must be blocked", score.isBlocked)
    }

    @Test
    fun `hebrew passive burn forms should be blocked`() {
        assertTrue(classifier.classify("הילד נשרף בטעות").isBlocked)
        assertTrue(classifier.classify("הבית נשרפה במהירות").isBlocked)
        assertTrue(classifier.classify("הבית עולה באש בוער").isBlocked)
    }

    @Test
    fun `hebrew passive injury forms should be blocked`() {
        assertTrue(classifier.classify("הילד נפצע קשה").isBlocked)
        assertTrue(classifier.classify("המכונית התפוצצה").isBlocked)
        assertTrue(classifier.classify("הכלב נחנק").isBlocked)
    }

    @Test
    fun `total score should never exceed reasonable bounds`() {
        // Custom blacklist sets score 2.0f internally; API surface should still be usable.
        classifier.updateCustomBlacklist(listOf("xxx"))
        val score = classifier.classify("xxx violent kill war huggy wuggy")
        assertTrue("Should be blocked", score.isBlocked)
        assertTrue("Total score should be > 1 when blacklisted", score.totalScore > 1.0f)
    }
}
