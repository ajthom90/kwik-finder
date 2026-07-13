package com.ajthom90.kwikfinder

import android.app.Application
import org.osmdroid.config.Configuration

class KwikFinderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Required by osmdroid before any MapView is created.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE),
        )
        container = AppContainer(this)
    }
}
