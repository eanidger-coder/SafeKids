package com.safekids.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safekids.data.dao.BlacklistDao
import com.safekids.data.dao.BlockedEventDao
import com.safekids.data.dao.SessionDao
import com.safekids.data.entities.BlacklistedChannel
import com.safekids.data.entities.BlacklistedKeyword
import com.safekids.data.entities.BlockedEvent
import com.safekids.data.entities.ViewingSession

@Database(
    entities = [
        BlacklistedChannel::class,
        BlacklistedKeyword::class,
        ViewingSession::class,
        BlockedEvent::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SafeKidsDatabase : RoomDatabase() {

    abstract fun blacklistDao(): BlacklistDao
    abstract fun sessionDao(): SessionDao
    abstract fun blockedEventDao(): BlockedEventDao

    companion object {
        @Volatile
        private var INSTANCE: SafeKidsDatabase? = null

        fun getInstance(context: Context): SafeKidsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeKidsDatabase::class.java,
                    "safekids_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
