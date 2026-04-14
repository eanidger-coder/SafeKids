package com.safekids.ui

import android.os.SystemClock

/**
 * ParentSessionManager — Core security component for PIN enforcement.
 * Tracks when the parent last authenticated and enforces a timeout.
 */
object ParentSessionManager {
    private var lastAuthenticatedTime: Long = 0
    private const val SESSION_TIMEOUT_MS = 180_000 // 3 minutes of inactivity allowed within parent area

    /**
     * Call this when PIN is successfully verified.
     */
    fun authenticate() {
        lastAuthenticatedTime = SystemClock.elapsedRealtime()
    }

    /**
     * Check if we need to re-authenticate the parent.
     */
    fun isAuthenticated(): Boolean {
        if (lastAuthenticatedTime == 0L) return false
        val now = SystemClock.elapsedRealtime()
        return (now - lastAuthenticatedTime) < SESSION_TIMEOUT_MS
    }

    /**
     * Clear session.
     */
    fun invalidate() {
        lastAuthenticatedTime = 0L
    }

    /**
     * Updates activity time to keep session alive while using parent screens.
     */
    fun touch() {
        if (isAuthenticated()) {
            lastAuthenticatedTime = SystemClock.elapsedRealtime()
        }
    }
}
