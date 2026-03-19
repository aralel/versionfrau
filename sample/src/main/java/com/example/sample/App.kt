package com.example.sample

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("VersionFrau", "Version: ${BuildConfig.VERSION_NAME}")
        Log.d("VersionFrau", "Code: ${BuildConfig.VERSION_CODE}")
        Log.d("VersionFrau", "Built: ${BuildConfig.BUILD_TIME}")
    }
}
