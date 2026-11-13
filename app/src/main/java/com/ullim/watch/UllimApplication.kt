package com.ullim.watch

import android.app.Application
import com.ullim.watch.settings.SettingsRuntime

class UllimApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRuntime.get(this)
    }
}
