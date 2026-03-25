package com.safekids

import android.app.Application
import com.safekids.data.SafeKidsDatabase

class SafeKidsApp : Application() {

    val database: SafeKidsDatabase by lazy {
        SafeKidsDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SafeKidsApp
            private set
    }
}
