package com.lqlq.browser.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lqlq.browser.R
import com.lqlq.browser.automation.worker.AutomationAsyncTaskStore
import com.lqlq.browser.automation.worker.AutomationNotifications

class AutomationJobNotificationPublisher(
    context: Context
) : AutomationPipelineProgressListener {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AutomationNotifications.CHANNEL_ID,
                "Tạo video tự động",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tiến độ từng bước của phiên tạo video chạy nền"
                setShowBadge(false)
            }
        )
    }

    override fun onProgress(progress: AutomationPipelineProgress) {
        ensureChannel()
        progress.clientRequestId?.takeIf { it.isNotBlank() }?.let { clientRequestId ->
            AutomationAsyncTaskStore.markProgress(
                context = appContext,
                clientRequestId = clientRequestId,
                jobId = progress.jobId,
                state = progress.state,
                message = progress.message,
                completedSteps = progress.completedSteps,
                totalSteps = progress.totalSteps,
                topic = progress.topic
            )
        }

        val ongoing = progress.state == "RUNNING" || progress.state == "QUEUED"
        val indeterminate = progress.state == "QUEUED"
        val notification = NotificationCompat.Builder(appContext, AutomationNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(buildTitle(progress))
            .setContentText(progress.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progress.message))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setProgress(
                progress.totalSteps.coerceAtLeast(1),
                progress.completedSteps.coerceIn(0, progress.totalSteps.coerceAtLeast(1)),
                indeterminate
            )
            .build()
        runCatching { manager.notify(AutomationNotifications.NOTIFICATION_ID, notification) }
    }

    private fun buildTitle(progress: AutomationPipelineProgress): String {
        val topic = progress.topic.take(34)
        return when (progress.state) {
            "QUEUED" -> "Đang chờ: $topic"
            "RUNNING" -> "Đang tạo video: $topic"
            "COMPLETED" -> "Đã hoàn tất: $topic"
            "FAILED" -> "Tạo video thất bại: $topic"
            "WAITING_USER" -> "Cần kiểm tra: $topic"
            else -> "Phiên video: $topic"
        }
    }
}
