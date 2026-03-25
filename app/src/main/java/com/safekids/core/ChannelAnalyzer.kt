package com.safekids.core

import com.safekids.data.dao.BlacklistDao

/**
 * ChannelAnalyzer — Layer 3 of SafeKids detection engine.
 *
 * Checks if a channel is on the parent's blacklist.
 * Tracks channels that repeatedly produce flagged content.
 */
class ChannelAnalyzer(private val blacklistDao: BlacklistDao) {

    private var cachedBlacklist: Set<String> = emptySet()
    private val channelViolationCount = mutableMapOf<String, Int>()

    /**
     * Check if a channel name matches the parent's blacklist.
     */
    fun isBlacklisted(channelName: String): Boolean {
        val normalized = channelName.lowercase().trim()
        return cachedBlacklist.any { blacklisted ->
            normalized.contains(blacklisted.lowercase())
        }
    }

    /**
     * Record a violation for a channel. If it exceeds the threshold,
     * it will be auto-flagged for parent review.
     */
    fun recordViolation(channelName: String) {
        val normalized = channelName.lowercase().trim()
        val count = (channelViolationCount[normalized] ?: 0) + 1
        channelViolationCount[normalized] = count
    }

    /**
     * Get channels with repeated violations for parent review.
     */
    fun getRepeatOffenders(minViolations: Int = 3): Map<String, Int> {
        return channelViolationCount.filter { it.value >= minViolations }
    }

    /**
     * Refresh the cached blacklist from the database.
     */
    suspend fun refreshBlacklist() {
        cachedBlacklist = blacklistDao.getAllChannelNames().map { it.lowercase() }.toSet()
    }

    /**
     * Update the blacklist with parent-managed custom keywords.
     */
    suspend fun refreshCustomKeywords(): List<String> {
        return blacklistDao.getAllKeywordStrings()
    }
}
