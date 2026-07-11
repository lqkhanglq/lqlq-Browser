package com.lqlq.browser.automation.worker

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lqlq.browser.R

object AutomationNotifications {
    const val CHANNEL_ID = "automation_pipeline"
    const val NOTIFICATION_ID = 9200

    fun buildRunningNotification(context: Context, title: String, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun showRunning(context: Context, title: String, message: String) {
        notify(context, title, message, ongoing = true)
    }

    fun showDone(context: Context, title: String, message: String) {
        notify(context, title, message, ongoing = false)
    }

    fun showError(context: Context, title: String, message: String) {
        notify(context, title, message, ongoing = false)
    }

    private fun notify(context: Context, title: String, message: String, ongoing: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
