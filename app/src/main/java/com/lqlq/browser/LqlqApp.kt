package com.lqlq.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.lqlq.browser.automation.AutomationFacade
import com.lqlq.browser.automation.database.AutomationDatabase

class LqlqApp : Application() {
    lateinit var automationFacade: AutomationFacade
        private set
    val automationDatabase: AutomationDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AutomationDatabase.create(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        automationFacade = AutomationFacade.createDefault()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                PlaybackService.CHANNEL_ID,
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_playback_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
