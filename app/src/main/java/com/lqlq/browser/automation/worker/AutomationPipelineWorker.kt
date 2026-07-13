package com.lqlq.browser.automation.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lqlq.browser.LqlqApp
import com.lqlq.browser.automation.AutomationContentRunRequest
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Chạy các bước automation nặng (gọi Gemini/image/voice/video provider) trong nền qua
 * WorkManager, thay vì để JS gọi bridge đồng bộ và làm đứng WebView cho tới khi xong.
 * Enqueue theo unique queue APPEND nên tự nhiên chỉ 1 tác vụ chạy tại 1 thời điểm,
 * các tác vụ enqueue sau sẽ tự xếp hàng chạy nối tiếp.
 */
class AutomationPipelineWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val QUEUE_NAME = "automation-pipeline-queue"
        const val KEY_ACTION = "action"
        const val KEY_PAYLOAD = "payload"
        const val KEY_CLIENT_REQUEST_ID = "clientRequestId"

        private const val DEFAULT_MAX_OUTPUT_LENGTH = 12_000
        private val CALLBACK_OR_PATH_PATTERN = Regex(
            "https?://|file://|content://|[a-zA-Z]:\\\\|/etc/|/proc/|javascript:",
            RegexOption.IGNORE_CASE
        )

        private fun actionLabel(action: String): String = when (action) {
            "generateContent" -> "Đang tạo nội dung từ chủ đề..."
            "retryImage" -> "Đang tạo/lấy lại ảnh..."
            "retryVoice" -> "Đang tạo giọng đọc..."
            "retryVideo" -> "Đang render video..."
            "exportVideo" -> "Đang xuất MP4..."
            "publishYouTube" -> "Đang đăng video lên YouTube..."
            else -> "Đang xử lý phiên tự động..."
        }
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val payloadJson = inputData.getString(KEY_PAYLOAD) ?: "{}"
        val clientRequestId = inputData.getString(KEY_CLIENT_REQUEST_ID)

        val title = "lqlq Automation"
        val runningMessage = actionLabel(action)
        if (clientRequestId != null) {
            AutomationAsyncTaskStore.markRunning(applicationContext, clientRequestId)
        }
        // Chạy như foreground service thật (giống PlaybackService cho nhạc/truyện) để
        // hệ điều hành (kể cả các ROM Android siết pin mạnh) không trì hoãn/giết tác vụ
        // khi app ở nền — đây là nguyên nhân tác vụ trước đó "chạy mãi không lên %".
        val notification = AutomationNotifications.buildRunningNotification(applicationContext, title, runningMessage)
        try {
            setForeground(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(
                        AutomationNotifications.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    ForegroundInfo(AutomationNotifications.NOTIFICATION_ID, notification)
                }
            )
        } catch (_: Throwable) {
            // Máy không cho chạy foreground service (hiếm) — vẫn thử chạy nền thường
            // thay vì chặn hẳn tác vụ.
        }

        var doneMessage: String? = null
        return try {
            val facade = (applicationContext as LqlqApp).automationFacade
            val payload = JSONObject(payloadJson)
            val jobId = when (action) {
                "generateContent" -> {
                    val request = parseContentRequest(payload, clientRequestId)
                    facade.generateAutomationContent(request).jobId
                }
                "retryImage" -> {
                    facade.retryImageStep(
                        jobId = payload.optString("jobId"),
                        providerId = payload.optString("providerId").ifBlank { null }
                    ).jobId
                }
                "retryVoice" -> {
                    facade.retryVoiceStep(
                        jobId = payload.optString("jobId"),
                        providerId = payload.optString("providerId").ifBlank { null }
                    ).jobId
                }
                "retryVideo" -> {
                    facade.retryVideoStep(
                        jobId = payload.optString("jobId"),
                        videoRendererMode = normalizeVideoRendererMode(payload.optString("videoRendererMode")),
                        videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null }),
                        videoQualityTier = payload.optString("videoQualityTier").ifBlank { null },
                        videoBackgroundMode = payload.optString("videoBackgroundMode").ifBlank { null },
                        videoMotionMode = payload.optString("videoMotionMode").ifBlank { null },
                        backgroundMusicFilePath = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getFilePathIfPresent(applicationContext),
                        backgroundMusicLoop = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(applicationContext).loop,
                        backgroundMusicVolume = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(applicationContext).volume,
                        videoSubtitleColor = payload.optString("videoSubtitleColor").ifBlank { null }
                    ).jobId
                }
                "exportVideo" -> {
                    facade.exportVideoMp4ToDownloads(jobId = payload.optString("jobId").trim())
                    payload.optString("jobId").trim()
                }
                "publishYouTube" -> {
                    val targetJobId = payload.optString("jobId").trim()
                    val data = facade.getYouTubePublishData(targetJobId)
                        ?: throw IllegalStateException("Job chua co VIDEO_MP4 de dang len YouTube.")
                    val videoId = com.lqlq.browser.automation.publish.YouTubePublishManager.upload(
                        context = applicationContext,
                        videoFilePath = data.videoFilePath,
                        title = data.title,
                        description = data.description,
                        tags = data.tags
                    )
                    doneMessage = "Đã đăng lên YouTube: https://youtu.be/$videoId"
                    targetJobId
                }
                else -> throw IllegalArgumentException("Action khong ho tro: $action")
            }
            if (clientRequestId != null) {
                AutomationAsyncTaskStore.markDone(applicationContext, clientRequestId, jobId, doneMessage ?: "Đã hoàn tất.")
            }
            AutomationNotifications.showDone(applicationContext, title, doneMessage ?: "Đã hoàn tất: ${actionLabel(action)}")
            Result.success()
        } catch (cancelled: CancellationException) {
            if (clientRequestId != null) {
                AutomationAsyncTaskStore.markCancelled(applicationContext, clientRequestId)
            }
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message ?: "Không thể xử lý phiên tự động lúc này."
            if (clientRequestId != null) {
                AutomationAsyncTaskStore.markError(applicationContext, clientRequestId, message)
            }
            AutomationNotifications.showError(applicationContext, title, "Lỗi: $message")
            Result.failure()
        }
    }

    private fun parseContentRequest(
        payload: JSONObject,
        clientRequestId: String?
    ): AutomationContentRunRequest {
        val topic = payload.optString("topic").trim()
        require(topic.isNotBlank()) { "Hay nhap chu de noi dung." }
        require(topic.length <= com.lqlq.browser.automation.AutomationFacade.MAX_AUTOMATION_CONTENT_LENGTH) {
            "Noi dung qua dai. Gioi han toi da la 50000 ky tu."
        }
        require(!CALLBACK_OR_PATH_PATTERN.containsMatchIn(topic)) {
            "Noi dung khong duoc la callback URL hoac filesystem path."
        }
        return AutomationContentRunRequest(
            topic = topic,
            language = payload.optString("language").trim().ifBlank { "vi" },
            contentType = payload.optString("contentType").trim().ifBlank {
                com.lqlq.browser.automation.AutomationFacade.DEFAULT_CONTENT_TYPE
            },
            promptTemplate = payload.optString("promptTemplate"),
            maximumOutputLength = payload.optInt("maximumOutputLength", DEFAULT_MAX_OUTPUT_LENGTH),
            desiredDurationSeconds = payload.optInt("desiredDurationSeconds", 0).takeIf { it > 0 },
            requestedSceneCount = payload.optInt("requestedSceneCount", 0).takeIf { it > 0 },
            aspectRatio = payload.optString("aspectRatio").trim().ifBlank { "9:16" },
            // Noi dung dai KHONG truyen qua JS nua (getAsyncTaskStatus tra ve cuc chu
            // dai bi cat -> JSON hong -> pipeline rong). JS chi gui MA TAC VU fetch;
            // doc thang rawText da luu san trong store (cung tien trinh).
            preFetchedRawText = payload.optString("preFetchedRawText").trim().ifBlank {
                payload.optString("preFetchedRawTaskId").trim().takeIf { it.isNotEmpty() }?.let { fetchId ->
                    AutomationAsyncTaskStore.get(applicationContext, fetchId)
                        ?.optString("rawText")?.trim()?.takeIf { it.isNotEmpty() }
                }
            },
            clientRequestId = clientRequestId,
            videoRendererMode = normalizeVideoRendererMode(payload.optString("videoRendererMode")),
            videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null }),
            videoQualityTier = payload.optString("videoQualityTier").ifBlank { "1080p" },
            videoBackgroundMode = payload.optString("videoBackgroundMode").ifBlank { "blurred_fill" },
            videoMotionMode = payload.optString("videoMotionMode").ifBlank { "auto_mix" },
            backgroundMusicFilePath = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getFilePathIfPresent(applicationContext),
            backgroundMusicLoop = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(applicationContext).loop,
            backgroundMusicVolume = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(applicationContext).volume,
            videoSubtitleColor = payload.optString("videoSubtitleColor").ifBlank { "#FFFFFF" }
        )
    }

    private fun normalizeVideoRendererMode(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            "android_native_render" -> "android_native_render"
            "local_plan_only" -> "local_plan_only"
            "external_moviepy_worker" -> "external_moviepy_worker"
            else -> "android_native_render"
        }
    }

    private fun normalizeVideoWorkerUrl(value: String?): String? {
        val normalized = value?.trim()?.ifBlank { null } ?: return null
        require(normalized.length <= 512) { "Worker URL qua dai." }
        require(!normalized.startsWith("javascript:", ignoreCase = true)) { "Worker URL khong hop le." }
        require(!normalized.startsWith("data:", ignoreCase = true)) { "Worker URL khong hop le." }
        require(normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
            "Worker URL phai bat dau bang http:// hoac https://."
        }
        return normalized
    }
}
