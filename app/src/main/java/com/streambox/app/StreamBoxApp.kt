package com.streambox.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreamBoxApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Simple initialization - no complex injection here
    }
}
