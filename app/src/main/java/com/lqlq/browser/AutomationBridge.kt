package com.lqlq.browser

import android.webkit.JavascriptInterface
import com.lqlq.browser.automation.AutomationFacade
import com.lqlq.browser.automation.AutomationStartRequest
import com.lqlq.browser.automation.AutomationUiDependencySnapshot
import com.lqlq.browser.automation.AutomationUiJobSnapshot
import com.lqlq.browser.automation.AutomationUiRecentJob
import com.lqlq.browser.automation.AutomationUiStepSnapshot
import com.lqlq.browser.automation.repository.AutomationRepositoryErrorCode
import com.lqlq.browser.automation.repository.AutomationRepositoryException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated shell-only bridge for the Automation Center MVP.
 * It intentionally exposes a small, redacted API and never leaks DAO/Room types.
 */
class AutomationBridge(
    private val automationFacade: AutomationFacade
) {

    @JavascriptInterface
    fun startMockAutomation(requestJson: String): String {
        return respond {
            val request = parseStartRequest(requestJson)
            val snapshot = runBlocking { automationFacade.startMockAutomation(request) }
            success()
                .put("job", snapshot.toJson())
                .put(
                    "message",
                    "Da tao job mock va luu vao co so du lieu cuc bo. Chua goi AI, TTS, video hay dang bai that."
                )
        }
    }

    @JavascriptInterface
    fun getAutomationJob(jobId: String): String {
        return respond {
            val normalizedJobId = jobId.trim()
            require(normalizedJobId.isNotBlank()) { "Job ID is required." }
            val snapshot = runBlocking { automationFacade.getAutomationJob(normalizedJobId) }
                ?: throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.NOT_FOUND,
                    "Automation job was not found."
                )
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun listRecentAutomationJobs(projectId: String): String {
        return respond {
            val normalizedProjectId = projectId.trim().ifBlank { AutomationFacade.MOCK_PROJECT_ID }
            val jobs = runBlocking {
                automationFacade.listRecentAutomationJobs(normalizedProjectId)
            }
            success().put(
                "jobs",
                JSONArray().apply { jobs.forEach { put(it.toJson()) } }
            )
        }
    }

    private fun parseStartRequest(requestJson: String): AutomationStartRequest {
        val normalizedJson = requestJson.trim()
        require(normalizedJson.isNotBlank()) { "Request payload is required." }
        require(normalizedJson.length <= MAX_REQUEST_JSON_LENGTH) { "Request payload is too large." }

        val payload = try {
            JSONObject(normalizedJson)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Request payload is invalid.")
        }

        val topic = payload.optString("topic").trim()
        val contentServiceId = payload.optString("contentServiceId").trim()
        val voiceServiceId = payload.optString("voiceServiceId").trim()
        val videoServiceId = payload.optString("videoServiceId").trim()
        val publishServiceId = payload.optString("publishServiceId").trim()
        val publishMode = payload.optString("publishMode").trim()

        require(topic.isNotBlank()) { "Hay nhap chu de noi dung." }
        require(topic.length <= AutomationFacade.MAX_TOPIC_LENGTH) {
            "Chu de qua dai. Hay rut gon de tiep tuc."
        }
        require(!DISALLOWED_TOPIC_PATTERN.containsMatchIn(topic)) {
            "Chu de chua noi dung khong hop le."
        }
        require(contentServiceId in ALLOWED_CONTENT_SERVICES) { "Dich vu tao noi dung chua duoc ho tro." }
        require(voiceServiceId in ALLOWED_VOICE_SERVICES) { "Dich vu giong doc chua duoc ho tro." }
        require(videoServiceId in ALLOWED_VIDEO_SERVICES) { "Dich vu video chua duoc ho tro." }
        require(publishServiceId in ALLOWED_PUBLISH_SERVICES) { "Dich vu dang ban nhap chua duoc ho tro." }
        require(publishMode == ALLOWED_PUBLISH_MODE) { "Che do dang bai nay chua duoc ho tro." }

        return AutomationStartRequest(
            topic = topic,
            contentServiceId = contentServiceId,
            voiceServiceId = voiceServiceId,
            videoServiceId = videoServiceId,
            publishServiceId = publishServiceId,
            publishMode = publishMode
        )
    }

    private fun respond(
        block: () -> JSONObject
    ): String {
        return try {
            block().toString()
        } catch (error: IllegalArgumentException) {
            failure("VALIDATION", error.message ?: "Yeu cau khong hop le.").toString()
        } catch (error: AutomationRepositoryException) {
            failure(
                error.code.name,
                redactedRepositoryMessage(error.code)
            ).toString()
        } catch (_: Throwable) {
            failure(
                AutomationRepositoryErrorCode.STORAGE.name,
                "Khong the xu ly tu dong hoa luc nay. Thu lai sau."
            ).toString()
        }
    }

    private fun success(): JSONObject = JSONObject().put("ok", true)

    private fun failure(errorCode: String, message: String): JSONObject {
        return JSONObject()
            .put("ok", false)
            .put("errorCode", errorCode)
            .put("message", message)
    }

    private fun redactedRepositoryMessage(
        code: AutomationRepositoryErrorCode
    ): String {
        return when (code) {
            AutomationRepositoryErrorCode.VALIDATION ->
                "Du lieu quy trinh chua hop le. Hay kiem tra lai thong tin."
            AutomationRepositoryErrorCode.NOT_FOUND ->
                "Khong tim thay quy trinh tu dong hoa da yeu cau."
            AutomationRepositoryErrorCode.CONFLICT ->
                "Quy trinh nay dang trung voi du lieu da ton tai. Hay tao lai."
            AutomationRepositoryErrorCode.CONSTRAINT ->
                "He thong chua san sang tao quy trinh nay voi cau hinh hien tai."
            AutomationRepositoryErrorCode.STORAGE ->
                "Khong the luu quy trinh vao co so du lieu cuc bo luc nay."
        }
    }

    private fun AutomationUiJobSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("jobId", jobId)
            .put("projectId", projectId)
            .put("workflowId", workflowId)
            .put("workflowVersion", workflowVersion)
            .put("topic", topic)
            .put("status", status)
            .put("publishMode", publishMode)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put(
                "steps",
                JSONArray().apply { steps.forEach { put(it.toJson()) } }
            )
            .put(
                "dependencies",
                JSONArray().apply { dependencies.forEach { put(it.toJson()) } }
            )
    }

    private fun AutomationUiStepSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("stepId", stepId)
            .put("stepKey", stepKey)
            .put("stepType", stepType)
            .put("status", status)
            .put("connectorBindingId", connectorBindingId)
            .put("waitingReason", waitingReason)
    }

    private fun AutomationUiDependencySnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("dependencyId", dependencyId)
            .put("fromStepId", fromStepId)
            .put("toStepId", toStepId)
    }

    private fun AutomationUiRecentJob.toJson(): JSONObject {
        return JSONObject()
            .put("jobId", jobId)
            .put("topic", topic)
            .put("status", status)
            .put("createdAtEpochMs", createdAtEpochMs)
    }

    companion object {
        private const val MAX_REQUEST_JSON_LENGTH = 8_192
        private const val ALLOWED_PUBLISH_MODE = "review-before-post"

        private val ALLOWED_CONTENT_SERVICES = setOf("mock-content")
        private val ALLOWED_VOICE_SERVICES = setOf("mock-voice")
        private val ALLOWED_VIDEO_SERVICES = setOf("mock-video")
        private val ALLOWED_PUBLISH_SERVICES = setOf("mock-publish-draft")
        private val DISALLOWED_TOPIC_PATTERN =
            Regex("(<|>|javascript:|<script|</script|data:)", RegexOption.IGNORE_CASE)
    }
}
