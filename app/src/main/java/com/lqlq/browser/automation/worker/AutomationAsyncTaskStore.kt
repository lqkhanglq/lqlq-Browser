package com.lqlq.browser.automation.worker

import android.content.Context
import org.json.JSONObject

/**
 * Ghi trạng thái các tác vụ automation chạy nền (WorkManager) để JS poll thay vì
 * đợi bridge trả kết quả đồng bộ (nguyên nhân màn hình bị đứng khi trước).
 * Không chứa credential nên không cần mã hoá như AndroidKeystoreAutomationCredentialStore.
 */
object AutomationAsyncTaskStore {
    private const val PREFS_NAME = "automation_async_tasks"

    const val STATE_QUEUED = "QUEUED"
    const val STATE_RUNNING = "RUNNING"
    const val STATE_DONE = "DONE"
    const val STATE_ERROR = "ERROR"
    const val STATE_CANCELLED = "CANCELLED"

    fun markQueued(context: Context, clientRequestId: String) {
        write(context, clientRequestId, JSONObject().put("state", STATE_QUEUED))
    }

    fun markRunning(context: Context, clientRequestId: String) {
        write(context, clientRequestId, JSONObject().put("state", STATE_RUNNING))
    }

    fun markProgress(
        context: Context,
        clientRequestId: String,
        jobId: String,
        state: String,
        message: String,
        completedSteps: Int,
        totalSteps: Int,
        topic: String
    ) {
        val safeTotal = totalSteps.coerceAtLeast(1)
        val safeCompleted = completedSteps.coerceIn(0, safeTotal)
        val progressPercent = ((safeCompleted * 100.0) / safeTotal).toInt().coerceIn(0, 100)
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", if (state == STATE_ERROR || state == STATE_CANCELLED) state else STATE_RUNNING)
                .put("pipelineState", state)
                .put("jobId", jobId)
                .put("message", message)
                .put("completedSteps", safeCompleted)
                .put("totalSteps", safeTotal)
                .put("progressPercent", progressPercent)
                .put("topic", topic)
        )
    }

    /**
     * Progress cho khau "lay noi dung tu Gemini/ChatGPT web" (truoc pipeline).
     * Chi ghi khi tac vu con RUNNING/QUEUED — khong duoc de progress den muon ghi
     * de len DONE/ERROR/CANCELLED (se khien JS poll lai vo han).
     */
    fun markFetchProgress(context: Context, clientRequestId: String, percent: Int, message: String) {
        val current = get(context, clientRequestId)?.optString("state")
        if (current == STATE_DONE || current == STATE_ERROR || current == STATE_CANCELLED) return
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", STATE_RUNNING)
                .put("progressPercent", percent.coerceIn(0, 100))
                .put("message", message)
        )
    }

    fun markDone(context: Context, clientRequestId: String, jobId: String?, message: String?) {
        if (isCancelled(context, clientRequestId)) return
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", STATE_DONE)
                .put("jobId", jobId)
                .put("message", message)
        )
    }

    /** Dung cho tac vu "lay noi dung tu Gemini web" — ket qua la van ban, chua co jobId. */
    fun markDoneWithRawText(context: Context, clientRequestId: String, rawText: String) {
        if (isCancelled(context, clientRequestId)) return
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", STATE_DONE)
                .put("rawText", rawText)
        )
    }

    fun markCancelled(context: Context, clientRequestId: String, message: String? = null) {
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", STATE_CANCELLED)
                .put("message", message ?: "Da dung tac vu.")
        )
    }

    fun markError(context: Context, clientRequestId: String, message: String?) {
        if (isCancelled(context, clientRequestId)) return
        write(
            context,
            clientRequestId,
            JSONObject()
                .put("state", STATE_ERROR)
                .put("message", message)
        )
    }

    fun get(context: Context, clientRequestId: String): JSONObject? {
        val raw = prefs(context).getString(clientRequestId, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            null
        }
    }

    fun findActiveTaskIdByJobId(context: Context, jobId: String): String? {
        val normalizedJobId = jobId.trim()
        if (normalizedJobId.isEmpty()) return null
        val all = prefs(context).all
        val matches = mutableListOf<Pair<String, JSONObject>>()
        all.forEach { (taskId, rawValue) ->
            val raw = rawValue as? String ?: return@forEach
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val state = json.optString("state").trim().uppercase()
            val taskJobId = json.optString("jobId").trim()
            if (taskJobId != normalizedJobId) return@forEach
            if (state == STATE_QUEUED || state == STATE_RUNNING) {
                matches += taskId to json
            }
        }
        return matches
            .maxByOrNull { (_, json) -> json.optInt("progressPercent", 0) }
            ?.first
    }

    private fun write(context: Context, clientRequestId: String, payload: JSONObject) {
        prefs(context).edit().putString(clientRequestId, payload.toString()).apply()
    }

    private fun isCancelled(context: Context, clientRequestId: String): Boolean {
        return get(context, clientRequestId)?.optString("state") == STATE_CANCELLED
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
