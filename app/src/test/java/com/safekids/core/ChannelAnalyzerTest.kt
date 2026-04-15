package com.safekids.core

import com.safekids.data.dao.BlacklistDao
import com.safekids.data.entities.BlacklistedChannel
import com.safekids.data.entities.BlacklistedKeyword
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChannelAnalyzer — Layer 3 of the filtering engine.
 */
class ChannelAnalyzerTest {

    /** In-memory fake BlacklistDao for unit tests. */
    private class FakeBlacklistDao : BlacklistDao {
        val channels = mutableListOf<BlacklistedChannel>()
        val keywords = mutableListOf<BlacklistedKeyword>()

        override fun getAllChannels(): Flow<List<BlacklistedChannel>> = flowOf(channels.toList())
        override suspend fun getAllChannelNames(): List<String> = channels.map { it.channelName }
        override suspend fun insertChannel(channel: BlacklistedChannel) { channels.add(channel) }
        override suspend fun deleteChannel(channel: BlacklistedChannel) {
            channels.removeAll { it.channelName == channel.channelName }
        }
        override suspend fun deleteChannelById(id: Long) {
            channels.removeAll { it.id == id }
        }

        override fun getAllKeywords(): Flow<List<BlacklistedKeyword>> = flowOf(keywords.toList())
        override suspend fun getAllKeywordStrings(): List<String> = keywords.map { it.keyword }
        override suspend fun insertKeyword(keyword: BlacklistedKeyword) { keywords.add(keyword) }
        override suspend fun deleteKeyword(keyword: BlacklistedKeyword) {
            keywords.removeAll { it.keyword == keyword.keyword }
        }
        override suspend fun deleteKeywordById(id: Long) {
            keywords.removeAll { it.id == id }
        }
    }

    private lateinit var dao: FakeBlacklistDao
    private lateinit var analyzer: ChannelAnalyzer

    @Before
    fun setUp() {
        dao = FakeBlacklistDao()
        analyzer = ChannelAnalyzer(dao)
    }

    @Test
    fun `empty blacklist should not block any channel`() {
        assertFalse(analyzer.isBlacklisted("Any Channel"))
        assertFalse(analyzer.isBlacklisted(""))
    }

    @Test
    fun `blacklisted channel should match exact name`() = runBlocking {
        dao.channels.add(BlacklistedChannel(channelName = "Scary Kids TV"))
        analyzer.refreshBlacklist()
        assertTrue(analyzer.isBlacklisted("Scary Kids TV"))
    }

    @Test
    fun `channel match should be case insensitive`() = runBlocking {
        dao.channels.add(BlacklistedChannel(channelName = "BadChannel"))
        analyzer.refreshBlacklist()
        assertTrue(analyzer.isBlacklisted("badchannel"))
        assertTrue(analyzer.isBlacklisted("BADCHANNEL"))
        assertTrue(analyzer.isBlacklisted("BadChannel"))
    }

    @Test
    fun `channel match should support substring (to catch variants)`() = runBlocking {
        dao.channels.add(BlacklistedChannel(channelName = "huggy"))
        analyzer.refreshBlacklist()
        assertTrue("Partial channel name should match", analyzer.isBlacklisted("Huggy Wuggy Official"))
    }

    @Test
    fun `non blacklisted channel should not match`() = runBlocking {
        dao.channels.add(BlacklistedChannel(channelName = "Scary Kids TV"))
        analyzer.refreshBlacklist()
        assertFalse(analyzer.isBlacklisted("Peppa Pig Official"))
    }

    @Test
    fun `refresh should pick up new entries`() = runBlocking {
        analyzer.refreshBlacklist()
        assertFalse(analyzer.isBlacklisted("NewBad"))

        dao.channels.add(BlacklistedChannel(channelName = "NewBad"))
        analyzer.refreshBlacklist()
        assertTrue(analyzer.isBlacklisted("NewBad"))
    }

    @Test
    fun `refresh should drop removed entries`() = runBlocking {
        dao.channels.add(BlacklistedChannel(channelName = "TempBad"))
        analyzer.refreshBlacklist()
        assertTrue(analyzer.isBlacklisted("TempBad"))

        dao.channels.clear()
        analyzer.refreshBlacklist()
        assertFalse(analyzer.isBlacklisted("TempBad"))
    }

    @Test
    fun `refreshCustomKeywords returns dao entries`() = runBlocking {
        dao.keywords.add(BlacklistedKeyword(keyword = "banned_word"))
        dao.keywords.add(BlacklistedKeyword(keyword = "another_one"))
        val result = analyzer.refreshCustomKeywords()
        assertEquals(2, result.size)
        assertTrue(result.contains("banned_word"))
        assertTrue(result.contains("another_one"))
    }

    @Test
    fun `violation counts should accumulate per channel`() {
        analyzer.recordViolation("ChA")
        analyzer.recordViolation("ChA")
        analyzer.recordViolation("ChB")

        val offenders = analyzer.getRepeatOffenders(minViolations = 2)
        assertTrue("ChA should be a repeat offender", offenders.keys.any { it.contains("cha") })
        assertFalse("ChB should not be a repeat offender yet", offenders.keys.any { it == "chb" && offenders[it]!! >= 2 })
    }

    @Test
    fun `violation counts should be case insensitive`() {
        analyzer.recordViolation("ChannelX")
        analyzer.recordViolation("channelx")
        analyzer.recordViolation("CHANNELX")
        val offenders = analyzer.getRepeatOffenders(minViolations = 3)
        assertEquals(1, offenders.size)
        assertEquals(3, offenders.values.first())
    }

    @Test
    fun `getRepeatOffenders filters by threshold`() {
        repeat(5) { analyzer.recordViolation("BigOffender") }
        analyzer.recordViolation("SmallOffender")

        val strict = analyzer.getRepeatOffenders(minViolations = 5)
        assertEquals(1, strict.size)

        val loose = analyzer.getRepeatOffenders(minViolations = 1)
        assertEquals(2, loose.size)
    }
}
