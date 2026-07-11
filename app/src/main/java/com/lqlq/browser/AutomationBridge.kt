package com.lqlq.browser

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lqlq.browser.automation.AutomationConnectionTestResult
import com.lqlq.browser.automation.AutomationContentRunRequest
import com.lqlq.browser.automation.AutomationFacade
import com.lqlq.browser.automation.AutomationUiArtifactSnapshot
import com.lqlq.browser.automation.AutomationUiAssetPlanSnapshot
import com.lqlq.browser.automation.AutomationUiImageProviderSnapshot
import com.lqlq.browser.automation.AutomationUiJobSnapshot
import com.lqlq.browser.automation.AutomationVideoExportResult
import com.lqlq.browser.automation.AutomationUiVideoRenderPlanSnapshot
import com.lqlq.browser.automation.AutomationUiScenePromptSnapshot
import com.lqlq.browser.automation.AutomationUiVideoRenderSceneSnapshot
import com.lqlq.browser.automation.AutomationUiVoiceDefinitionSnapshot
import com.lqlq.browser.automation.AutomationUiVoiceProviderSnapshot
import com.lqlq.browser.automation.connector.content.ContentProviderErrorCode
import com.lqlq.browser.automation.connector.content.ContentProviderException
import com.lqlq.browser.automation.connector.image.ImageProviderException
import com.lqlq.browser.automation.connector.voice.VoiceProviderException
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.worker.AutomationAsyncTaskStore
import com.lqlq.browser.automation.worker.AutomationPipelineWorker
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class AutomationBridge(
    private val context: Context,
    private val facade: AutomationFacade,
    private val onSharePublish: ((AutomationShareSheetRequest) -> Unit)? = null,
    private val onImportImages: ((String) -> Boolean)? = null
) {

    @JavascriptInterface
    fun runAutomationContentAsync(requestJson: String): String {
        return respond {
            parseJsonObject(requestJson)
            val clientRequestId = "req-content-${System.currentTimeMillis()}-${(1000..9999).random()}"
            enqueueWorker("generateContent", requestJson, clientRequestId)
            success().put("clientRequestId", clientRequestId).put("queued", true)
        }
    }

    @JavascriptInterface
    fun runAutomationStageAsync(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val action = payload.optString("action").trim()
            require(action.isNotBlank()) { "Thieu action de chay nen." }
            require(action in setOf("retryImage", "retryVoice", "retryVideo", "exportVideo")) {
                "Action khong ho tro chay nen: $action"
            }
            val clientRequestId = "req-${action}-${System.currentTimeMillis()}-${(1000..9999).random()}"
            enqueueWorker(action, requestJson, clientRequestId)
            success().put("clientRequestId", clientRequestId).put("queued", true)
        }
    }

    @JavascriptInterface
    fun cancelAutomationTask(clientRequestId: String): String {
        return respond {
            val normalizedId = clientRequestId.trim()
            require(normalizedId.isNotBlank()) { "Thieu ma tac vu can dung." }
            WorkManager.getInstance(context).cancelAllWorkByTag(normalizedId)
            AutomationAsyncTaskStore.markCancelled(context, normalizedId)
            success().put("cancelled", true)
        }
    }

    @JavascriptInterface
    fun getAsyncTaskStatus(clientRequestId: String): String {
        return respond {
            val task = AutomationAsyncTaskStore.get(context, clientRequestId.trim())
                ?: return@respond success().put("state", "UNKNOWN")
            success()
                .put("state", task.optString("state"))
                .put("pipelineState", if (task.has("pipelineState")) task.optString("pipelineState") else null)
                .put("jobId", if (task.has("jobId")) task.optString("jobId") else null)
                .put("message", if (task.has("message")) task.optString("message") else null)
                .put("completedSteps", task.optInt("completedSteps", 0))
                .put("totalSteps", task.optInt("totalSteps", 0))
                .put("progressPercent", task.optInt("progressPercent", 0))
                .put("topic", if (task.has("topic")) task.optString("topic") else null)
        }
    }

    private fun enqueueWorker(action: String, payloadJson: String, clientRequestId: String) {
        AutomationAsyncTaskStore.markQueued(context, clientRequestId)
        val data = Data.Builder()
            .putString(AutomationPipelineWorker.KEY_ACTION, action)
            .putString(AutomationPipelineWorker.KEY_PAYLOAD, payloadJson)
            .putString(AutomationPipelineWorker.KEY_CLIENT_REQUEST_ID, clientRequestId)
            .build()
        val request = OneTimeWorkRequestBuilder<AutomationPipelineWorker>()
            .setInputData(data)
            .addTag(clientRequestId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AutomationPipelineWorker.QUEUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    @JavascriptInterface
    fun getGeminiConfigurationStatus(): String {
        return respond {
            success()
                .put("status", facade.getGeminiConfigurationStatus().toJson())
                .put("maxTopicLength", AutomationFacade.MAX_AUTOMATION_CONTENT_LENGTH)
        }
    }

    @JavascriptInterface
    fun saveGeminiConfiguration(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val status = facade.saveGeminiConfiguration(
                apiKey = payload.optString("apiKey"),
                model = payload.optString("model")
            )
            success().put("status", status.toJson())
        }
    }

    @JavascriptInterface
    fun testGeminiConnection(): String {
        return respond {
            val result = runBlocking { facade.testGeminiConnection() }
            success()
                .put("status", result.toJson())
        }
    }

    @JavascriptInterface
    fun listImageProviders(): String {
        return respond {
            success().put(
                "providers",
                JSONArray().apply {
                    facade.listImageProviders().forEach { provider ->
                        put(provider.toJson())
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun getImageProviderConfigurationStatus(): String {
        return respond {
            val providers = facade.listImageProviders()
            success()
                .put("status", facade.getImageProviderConfigurationStatus().toJson())
                .put(
                    "providers",
                    JSONArray().apply {
                        providers.forEach { provider ->
                            put(provider.toJson())
                        }
                    }
                )
        }
    }

    @JavascriptInterface
    fun saveImageProviderConfiguration(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val status = facade.saveImageProviderConfiguration(
                providerId = payload.optString("providerId"),
                apiKey = payload.optString("apiKey"),
                model = payload.optString("model"),
                accountId = payload.optString("accountId").ifBlank { null }
            )
            success()
                .put("status", status.toJson())
                .put(
                    "providers",
                    JSONArray().apply {
                        facade.listImageProviders().forEach { provider ->
                            put(provider.toJson())
                        }
                    }
                )
        }
    }

    @JavascriptInterface
    fun testImageProviderConnection(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            val result = runBlocking { facade.testImageProviderConnection(providerId) }
            success().put("status", result.toJson())
        }
    }

    @JavascriptInterface
    fun listVoiceProviders(): String {
        return respond {
            success().put(
                "providers",
                JSONArray().apply {
                    facade.listVoiceProviders().forEach { provider ->
                        put(provider.toJson())
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun listVoiceDefinitions(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            success().put(
                "voices",
                JSONArray().apply {
                    facade.listVoiceDefinitions(providerId).forEach { voice ->
                        put(voice.toJson())
                    }
                }
            )
        }
    }

    @JavascriptInterface
    fun getVoiceProviderConfigurationStatus(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            success()
                .put("status", facade.getVoiceProviderConfigurationStatus(providerId).toJson())
                .put(
                    "providers",
                    JSONArray().apply {
                        facade.listVoiceProviders().forEach { provider ->
                            put(provider.toJson())
                        }
                    }
                )
        }
    }

    @JavascriptInterface
    fun saveVoiceProviderConfiguration(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val status = facade.saveVoiceProviderConfiguration(
                providerId = payload.optString("providerId"),
                locale = payload.optString("locale"),
                voiceId = payload.optString("voiceId").ifBlank { null },
                model = payload.optString("model").ifBlank { null },
                apiKey = payload.optString("apiKey").ifBlank { null },
                region = payload.optString("region").ifBlank { null },
                engineName = payload.optString("engineName").ifBlank { null },
                speechRate = payload.optDouble("speechRate", 1.0).toFloat(),
                pitch = payload.optDouble("pitch", 1.0).toFloat(),
                outputFormat = payload.optString("outputFormat").ifBlank { "wav" }
            )
            success()
                .put("status", status.toJson())
                .put(
                    "providers",
                    JSONArray().apply {
                        facade.listVoiceProviders().forEach { provider ->
                            put(provider.toJson())
                        }
                    }
                )
        }
    }

    @JavascriptInterface
    fun testVoiceProviderConnection(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            val result = runBlocking { facade.testVoiceProviderConnection(providerId) }
            success().put("status", result.toJson())
        }
    }

    @JavascriptInterface
    fun synthesizeVoiceSample(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            val artifact = runBlocking { facade.synthesizeVoiceSample(providerId) }
            success().put("artifact", artifact.toJson())
        }
    }

    @JavascriptInterface
    fun openVoiceProviderSettings(requestJson: String = ""): String {
        return respond {
            val providerId = requestJson.trim().takeIf { it.isNotEmpty() }?.let {
                parseJsonObject(it).optString("providerId")
            }
            facade.openVoiceProviderSettings(providerId)
            success().put("opened", true)
        }
    }

    @JavascriptInterface
    fun retryImageStep(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking {
                facade.retryImageStep(
                    jobId = payload.optString("jobId"),
                    providerId = payload.optString("providerId").ifBlank { null }
                )
            }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun retryVoiceStep(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking {
                facade.retryVoiceStep(
                    jobId = payload.optString("jobId"),
                    providerId = payload.optString("providerId").ifBlank { null }
                )
            }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun retryVideoStep(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking {
                facade.retryVideoStep(
                    jobId = payload.optString("jobId"),
                    videoRendererMode = normalizeVideoRendererMode(payload.optString("videoRendererMode").ifBlank { null }),
                    videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null })
                )
            }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun testVideoRenderWorker(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val workerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null })
                ?: throw IllegalArgumentException("Can nhap worker URL hop le.")
            val result = runBlocking { facade.testVideoRenderWorker(workerUrl) }
            success().put("status", result.toJson())
        }
    }

    @JavascriptInterface
    fun generateAutomationContent(requestJson: String): String {
        return respond {
            val request = parseContentRequest(requestJson)
            val snapshot = runBlocking { facade.generateAutomationContent(request) }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun getAutomationJob(jobId: String): String {
        return respond {
            val snapshot = runBlocking { facade.getAutomationJob(jobId.trim()) }
                ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun exportVideoMp4(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val result = runBlocking {
                facade.exportVideoMp4ToDownloads(
                    jobId = payload.optString("jobId").trim()
                )
            }
            success().put("export", result.toJson())
        }
    }

    @JavascriptInterface
    fun startAutomationImageImport(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            require(jobId.isNotBlank()) { "Can chon phien co job hop le de nhap anh." }
            val started = onImportImages?.invoke(jobId) ?: false
            require(started) { "Khong the mo trinh chon anh luc nay." }
            success().put("started", true)
        }
    }

    @JavascriptInterface
    fun retryAutomationMetadata(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking { facade.retryAutomationMetadata(payload.optString("jobId").trim()) }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun approveAutomationReview(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking { facade.approveAutomationReview(payload.optString("jobId").trim()) }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun rejectAutomationReview(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking {
                facade.rejectAutomationReview(
                    jobId = payload.optString("jobId").trim(),
                    reason = payload.optString("reason")
                )
            }
            success().put("job", snapshot.toJson())
        }
    }

    @JavascriptInterface
    fun shareAutomationPublish(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val result = runBlocking { facade.preparePublishShare(payload.optString("jobId").trim()) }
            onSharePublish?.invoke(
                AutomationShareSheetRequest(
                    contentUri = result.export.contentUri,
                    mimeType = result.export.mimeType,
                    text = result.shareText,
                    chooserTitle = result.chooserTitle
                )
            ) ?: throw IllegalStateException("Share sheet callback chua san sang.")
            success()
                .put("job", result.job.toJson())
                .put("export", result.export.toJson())
        }
    }

    @JavascriptInterface
    fun markAutomationPublished(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val snapshot = runBlocking { facade.markAutomationPublished(payload.optString("jobId").trim()) }
            success().put("job", snapshot.toJson())
        }
    }

    private fun parseContentRequest(requestJson: String): AutomationContentRunRequest {
        val payload = parseJsonObject(requestJson)
        val topic = payload.optString("topic").trim()
        require(topic.isNotBlank()) { "Hay nhap chu de noi dung." }
        require(topic.length <= AutomationFacade.MAX_AUTOMATION_CONTENT_LENGTH) {
            "Noi dung qua dai. Gioi han toi da la 50000 ky tu."
        }
        require(!CALLBACK_OR_PATH_PATTERN.matches(topic)) {
            "Noi dung khong duoc la callback URL hoac filesystem path."
        }

        return AutomationContentRunRequest(
            topic = topic,
            language = payload.optString("language").trim().ifBlank { "vi" },
            contentType = payload.optString("contentType").trim().ifBlank { AutomationFacade.DEFAULT_CONTENT_TYPE },
            promptTemplate = payload.optString("promptTemplate"),
            maximumOutputLength = payload.optInt("maximumOutputLength", DEFAULT_MAX_OUTPUT_LENGTH),
            desiredDurationSeconds = payload.optInt("desiredDurationSeconds", 0).takeIf { it > 0 },
            requestedSceneCount = payload.optInt("requestedSceneCount", 0).takeIf { it > 0 },
            videoRendererMode = normalizeVideoRendererMode(payload.optString("videoRendererMode")),
            videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null })
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
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Worker URL khong hop le.")
        require(!uri.host.isNullOrBlank()) { "Worker URL phai co host hop le." }
        return normalized.removeSuffix("/")
    }

    private fun parseJsonObject(requestJson: String): JSONObject {
        val normalizedJson = requestJson.trim()
        require(normalizedJson.isNotBlank()) { "Request payload is required." }
        require(normalizedJson.length <= MAX_REQUEST_JSON_LENGTH) { "Request payload is too large." }
        return try {
            JSONObject(normalizedJson)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Request payload is invalid.")
        }
    }

    private fun respond(block: () -> JSONObject): String {
        return try {
            block().toString()
        } catch (error: IllegalArgumentException) {
            failure("VALIDATION", error.message ?: "Yeu cau khong hop le.").toString()
        } catch (error: IllegalStateException) {
            failure("EXPORT_FAILED", error.message ?: "Khong the export artifact luc nay.").toString()
        } catch (error: ContentProviderException) {
            failure(error.code.name, error.message ?: "Khong the xu ly yeu cau voi provider hien tai.").toString()
        } catch (error: ImageProviderException) {
            failure(error.code.name, error.message ?: "Khong the xu ly image provider luc nay.").toString()
        } catch (error: VoiceProviderException) {
            failure(error.code.name, error.message ?: "Khong the xu ly voice provider luc nay.").toString()
        } catch (_: Throwable) {
            failure(
                ContentProviderErrorCode.PROVIDER_FAILURE.name,
                "Khong the xu ly yeu cau tu dong hoa luc nay. Thu lai sau."
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

    private fun AutomationCredentialStatusSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("state", state)
            .put("providerId", providerId)
            .put("model", model)
            .put("message", message)
            .put("accountId", accountId)
            .put("voiceId", voiceId)
            .put("locale", locale)
            .put("engineName", engineName)
    }

    private fun AutomationConnectionTestResult.toJson(): JSONObject {
        return JSONObject()
            .put("state", state)
            .put("providerId", providerId)
            .put("model", model)
            .put("message", message)
    }

    private fun AutomationVideoExportResult.toJson(): JSONObject {
        return JSONObject()
            .put("displayName", displayName)
            .put("mimeType", mimeType)
            .put("contentUri", contentUri)
            .put("displayPath", displayPath)
            .put("sizeBytes", sizeBytes)
    }

    private fun AutomationUiJobSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("jobId", jobId)
            .put("projectId", projectId)
            .put("workflowId", workflowId)
            .put("workflowVersion", workflowVersion)
            .put("topic", topic)
            .put("status", status)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("publishMode", publishMode)
            .put("generatedText", generatedText)
            .put("providerId", providerId)
            .put("model", model)
            .put("requestId", requestId)
            .put("runtimeMessage", runtimeMessage)
            .put(
                "usageMetadata",
                JSONObject().apply {
                    usageMetadata.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            )
            .put(
                "steps",
                JSONArray().apply {
                    steps.forEach { step ->
                        put(
                            JSONObject()
                                .put("stepId", step.stepId)
                                .put("stepKey", step.stepKey)
                                .put("stepType", step.stepType)
                                .put("status", step.status)
                                .put("connectorBindingId", step.connectorBindingId)
                                .put("waitingReason", step.waitingReason)
                        )
                    }
                }
            )
            .put(
                "dependencies",
                JSONArray().apply {
                    dependencies.forEach { dependency ->
                        put(
                            JSONObject()
                                .put("dependencyId", dependency.dependencyId)
                                .put("fromStepId", dependency.fromStepId)
                                .put("toStepId", dependency.toStepId)
                        )
                    }
                }
            )
            .put(
                "artifacts",
                JSONArray().apply {
                    artifacts.forEach { artifact ->
                        put(artifact.toJson())
                    }
                }
            )
            .put(
                "scenePrompts",
                JSONArray().apply {
                    scenePrompts.forEach { scenePrompt ->
                        put(scenePrompt.toJson())
                    }
                }
            )
            .put(
                "assetPlans",
                JSONArray().apply {
                    assetPlans.forEach { assetPlan ->
                        put(assetPlan.toJson())
                    }
                }
            )
            .put("videoRenderPlan", videoRenderPlan?.toJson())
            .put("metadataPlan", metadataPlan?.toJson())
            .put("reviewState", reviewState?.toJson())
            .put("publishPlan", publishPlan?.toJson())
    }

    private fun AutomationUiArtifactSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("artifactId", artifactId)
            .put("artifactType", artifactType)
            .put("mimeType", mimeType)
            .put("uri", uri)
            .put("sizeBytes", sizeBytes)
            .put("sourceUrl", sourceUrl)
            .put("sceneId", sceneId)
            .put("ordinal", ordinal)
            .put("providerRequestId", providerRequestId)
            .put("previewDataUrl", previewDataUrl)
    }

    private fun AutomationUiScenePromptSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("sceneId", sceneId)
            .put("ordinal", ordinal)
            .put("summary", summary)
            .put("visualPrompt", visualPrompt)
            .put("negativePrompt", negativePrompt)
            .put("aspectRatio", aspectRatio)
            .put("voiceText", voiceText)
            .put("onScreenText", onScreenText)
            .put("plannedDurationMs", plannedDurationMs)
            .put("stockSearchQuery", stockSearchQuery)
            .put("visualDirection", visualDirection)
    }

    private fun AutomationUiAssetPlanSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("sceneId", sceneId)
            .put("ordinal", ordinal)
            .put("strategy", strategy)
            .put("preferredProviderId", preferredProviderId)
            .put("assetQuery", assetQuery)
            .put("templateId", templateId)
            .put("renderMode", renderMode)
            .put("durationMs", durationMs)
            .put("rationale", rationale)
    }

    private fun AutomationUiVideoRenderPlanSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("rendererId", rendererId)
            .put("planVersion", planVersion)
            .put("renderTarget", renderTarget)
            .put("width", width)
            .put("height", height)
            .put("fps", fps)
            .put("voiceArtifactUri", voiceArtifactUri)
            .put("voiceMimeType", voiceMimeType)
            .put("sceneCount", sceneCount)
            .put("totalDurationMs", totalDurationMs)
            .put("handoffHints", JSONArray(handoffHints))
            .put(
                "scenes",
                JSONArray().apply {
                    scenes.forEach { scene ->
                        put(scene.toJson())
                    }
                }
            )
    }

    private fun com.lqlq.browser.automation.metadata.MetadataPlan.toJson(): JSONObject {
        return JSONObject()
            .put("schema", schema)
            .put("title", title)
            .put("shortTitle", shortTitle)
            .put("description", description)
            .put("hashtags", JSONArray(hashtags))
            .put("language", language)
            .put("category", category)
            .put("thumbnailText", thumbnailText)
            .put(
                "platforms",
                JSONObject().apply {
                    platforms.forEach { (key, value) ->
                        put(
                            key,
                            JSONObject()
                                .put("title", value.title)
                                .put("description", value.description)
                                .put("caption", value.caption)
                                .put("hashtags", JSONArray(value.hashtags))
                                .put("visibility", value.visibility)
                        )
                    }
                }
            )
            .put("sourceSceneCount", sourceSceneCount)
            .put("sourceVoiceDurationMs", sourceVoiceDurationMs)
            .put("sourceVideoDurationMs", sourceVideoDurationMs)
            .put("safetyNotes", JSONArray(safetyNotes))
    }

    private fun com.lqlq.browser.automation.review.ReviewState.toJson(): JSONObject {
        return JSONObject()
            .put("schema", schema)
            .put("status", status)
            .put(
                "checks",
                JSONArray(checks.map { check ->
                    JSONObject()
                        .put("key", check.key)
                        .put("label", check.label)
                        .put("passed", check.passed)
                        .put("detail", check.detail)
                })
            )
            .put("warnings", JSONArray(warnings))
            .put("approvedAtEpochMs", approvedAtEpochMs)
            .put("rejectedAtEpochMs", rejectedAtEpochMs)
            .put("rejectedReason", rejectedReason)
    }

    private fun com.lqlq.browser.automation.publish.PublishPlan.toJson(): JSONObject {
        return JSONObject()
            .put("schema", schema)
            .put("status", status)
            .put("targets", JSONArray(targets))
            .put("videoArtifactUri", videoArtifactUri)
            .put("metadataArtifactUri", metadataArtifactUri)
            .put("reviewStatus", reviewStatus)
            .put("publishMode", publishMode)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("publishedAtEpochMs", publishedAtEpochMs)
            .put("notes", JSONArray(notes))
    }

    private fun AutomationUiVideoRenderSceneSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("sceneId", sceneId)
            .put("ordinal", ordinal)
            .put("summary", summary)
            .put("visualPrompt", visualPrompt)
            .put("imageArtifactUri", imageArtifactUri)
            .put("imageArtifactId", imageArtifactId)
            .put("renderMode", renderMode)
            .put("templateId", templateId)
            .put("strategy", strategy)
            .put("durationMs", durationMs)
            .put("subtitleText", subtitleText)
    }

    private fun AutomationUiImageProviderSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("providerId", providerId)
            .put("displayName", displayName)
            .put("authType", authType)
            .put("costType", costType)
            .put("health", health)
            .put("selected", selected)
            .put("supportedModels", JSONArray(supportedModels))
            .put("defaultModel", defaultModel)
            .put("syncOrAsync", syncOrAsync)
            .put("supportsPolling", supportsPolling)
            .put("supportsAspectRatio", supportsAspectRatio)
            .put("supportsNegativePrompt", supportsNegativePrompt)
            .put("supportedOutputFormats", JSONArray(supportedOutputFormats))
            .put("maxImagesPerJob", maxImagesPerJob)
            .put("stabilityLevel", stabilityLevel)
            .put("configurationStatus", configurationStatus.toJson())
    }

    private fun AutomationUiVoiceProviderSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("providerId", providerId)
            .put("displayName", displayName)
            .put("authType", authType)
            .put("costType", costType)
            .put("health", health)
            .put("selected", selected)
            .put("supportedLocales", JSONArray(supportedLocales))
            .put("verifiedLocales", JSONArray(verifiedLocales))
            .put("defaultLocale", defaultLocale)
            .put("supportedOutputFormats", JSONArray(supportedOutputFormats))
            .put("supportsPitch", supportsPitch)
            .put("supportsSpeechRate", supportsSpeechRate)
            .put("supportsSamplePreview", supportsSamplePreview)
            .put("supportsChunking", supportsChunking)
            .put("requiresCredentials", requiresCredentials)
            .put("stabilityLevel", stabilityLevel)
            .put("configurationStatus", configurationStatus.toJson())
    }

    private fun AutomationUiVoiceDefinitionSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("voiceId", voiceId)
            .put("displayName", displayName)
            .put("locale", locale)
            .put("engineName", engineName)
            .put("networkRequired", networkRequired)
            .put("installed", installed)
            .put("isDefault", isDefault)
            .put("genderHint", genderHint)
    }

    companion object {
        private const val MAX_REQUEST_JSON_LENGTH = 262_144
        private const val DEFAULT_MAX_OUTPUT_LENGTH = 12_000
        private val CALLBACK_OR_PATH_PATTERN = Regex(
            pattern = "^\\s*(?:(?:https?|javascript|data|file|content)://\\S+|[A-Za-z]:\\\\.*|/.*)\\s*$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}

data class AutomationShareSheetRequest(
    val contentUri: String,
    val mimeType: String,
    val text: String,
    val chooserTitle: String
)
