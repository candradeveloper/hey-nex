package com.heynex

import android.app.Application
import android.content.Context

class HeyNexApplication : Application() {

    companion object {
        lateinit var context: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}
