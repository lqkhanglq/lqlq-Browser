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
import com.lqlq.browser.automation.connector.content.ContentGenerationRequest
import com.lqlq.browser.automation.connector.content.ContentProviderErrorCode
import com.lqlq.browser.automation.connector.content.ContentProviderException
import com.lqlq.browser.automation.connector.content.GeminiContentConnector
import com.lqlq.browser.automation.connector.image.ImageProviderException
import com.lqlq.browser.automation.connector.voice.VoiceProviderException
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.script.ContentDurationPolicy
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
    private val onImportImages: ((String) -> Boolean)? = null,
    private val onReplaceSceneImage: ((jobId: String, sceneId: String) -> Boolean)? = null,
    private val onDownloadImage: ((filePath: String) -> Boolean)? = null,
    private val onImportBackgroundMusic: (() -> Boolean)? = null,
    private val onPreviewVideo: ((filePath: String) -> Boolean)? = null,
    private val onStartYouTubeAuth: (() -> Boolean)? = null,
    private val onFetchGeminiWebContent: ((prompt: String, clientRequestId: String) -> Unit)? = null,
    private val onFetchChatGptWebContent: ((prompt: String, clientRequestId: String) -> Unit)? = null,
    private val onCancelAutomationTask: ((clientRequestId: String) -> Unit)? = null,
    private val onRunGeminiWebPocDebug: ((prompt: String, clientRequestId: String) -> Unit)? = null,
    private val onFetchWebImageUrls: ((jobId: String, prompt: String, clientRequestId: String) -> Unit)? = null,
    private val onScrapePinterestImages: ((jobId: String, groupsJson: String, clientRequestId: String) -> Unit)? = null
) {

    /**
     * Lấy nội dung từ Gemini web (dùng phiên đăng nhập sẵn có, không cần API key).
     * Dùng LẠI đúng prompt/schema JSON của đường API (GeminiContentConnector.buildPrompt)
     * chỉ đổi kênh gửi sang WebView, để định dạng đầu ra khớp hệt, không phải viết
     * lại logic parse. Chạy foreground ẨN (không hiện overlay) — WebView bắt buộc
     * main thread nhưng không cần hiện cho người dùng thấy. Trả về ngay
     * clientRequestId, JS poll qua getAsyncTaskStatus() để lấy field "rawText" khi
     * state = DONE — cùng cơ chế poll đã dùng cho các tác vụ nền khác.
     */
    @JavascriptInterface
    fun fetchGeminiWebContent(requestJson: String): String {
        return respond {
            val prompt = buildWebContentPrompt(parseJsonObject(requestJson), provider = "gemini-web")
            val clientRequestId = "req-gemini-web-${System.currentTimeMillis()}-${(1000..9999).random()}"
            AutomationAsyncTaskStore.markRunning(context, clientRequestId)
            onFetchGeminiWebContent?.invoke(prompt, clientRequestId)
                ?: throw IllegalStateException("Gemini web driver chua san sang.")
            success().put("clientRequestId", clientRequestId)
        }
    }

    @JavascriptInterface
    fun fetchChatGptWebContent(requestJson: String): String {
        return respond {
            val prompt = buildWebContentPrompt(parseJsonObject(requestJson), provider = "chatgpt-web")
            val clientRequestId = "req-chatgpt-web-${System.currentTimeMillis()}-${(1000..9999).random()}"
            AutomationAsyncTaskStore.markRunning(context, clientRequestId)
            onFetchChatGptWebContent?.invoke(prompt, clientRequestId)
                ?: throw IllegalStateException("ChatGPT web driver chua san sang.")
            success().put("clientRequestId", clientRequestId)
        }
    }

    /**
     * Nút debug "🧪 POC web" — chạy ĐÚNG prompt thật (giống hệt fetchGeminiWebContent)
     * nhưng HIỆN overlay để xem trực tiếp Gemini xử lý, dùng để kiểm tra khi nghi ngờ
     * lấy sai nội dung mà không cần bấm "Tạo nội dung" cả pipeline.
     */
    @JavascriptInterface
    fun runGeminiWebPocDebug(requestJson: String): String {
        return respond {
            val prompt = buildWebContentPrompt(parseJsonObject(requestJson), provider = "gemini-web")
            val clientRequestId = "req-gemini-web-debug-${System.currentTimeMillis()}-${(1000..9999).random()}"
            AutomationAsyncTaskStore.markRunning(context, clientRequestId)
            onRunGeminiWebPocDebug?.invoke(prompt, clientRequestId)
                ?: throw IllegalStateException("Gemini web driver chua san sang.")
            success().put("clientRequestId", clientRequestId)
        }
    }

    /**
     * Lay anh cho Google Images/Pinterest bang cach hoi Gemini web (dung LAI dung
     * phien Gemini web da co, khong mo them WebView tim kiem rieng cho tung canh -
     * cach cu mo tuan tu 12 trang tim kiem rat cham va de bi Google chan/captcha).
     * Gemini duoc yeu cau tra ve JSON danh sach URL anh truc tiep theo tung chi so
     * canh; MainActivity tai bytes tung URL va goi importImageArtifactsByIndex() de
     * day buoc anh sang COMPLETED - JS chi can poll getAsyncTaskStatus() nhu cu.
     */
    @JavascriptInterface
    fun scrapeWebImages(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val providerId = payload.optString("providerId").trim()
            require(jobId.isNotBlank()) { "Thieu jobId de cao anh." }
            require(providerId.isNotBlank()) { "Thieu providerId de cao anh." }
            val clientRequestId = "req-scrape-images-${System.currentTimeMillis()}-${(1000..9999).random()}"
            AutomationAsyncTaskStore.markRunning(context, clientRequestId)
            if (providerId == com.lqlq.browser.automation.connector.image.AutomationImageProviders.PINTEREST_IMAGE_SEARCH_WEB) {
                // Pinterest: CHI cao cac canh CON THIEU anh (resume) - gom cac canh
                // lien tiep cung query (cung nhan vat) tim 1 lan, moi nhom mang theo
                // chiSoGoc (startIndex) de gan dung canh.
                val missing = facade.getMissingSceneImageQueries(jobId)
                require(missing.isNotEmpty()) { "Tat ca canh da co anh - khong con canh nao can cao." }
                val groupsJson = buildPinterestGroupsJson(missing)
                onScrapePinterestImages?.invoke(jobId, groupsJson, clientRequestId)
                    ?: throw IllegalStateException("Pinterest image driver chua san sang.")
            } else {
                val queries = facade.getSceneImageSearchQueries(jobId)
                require(queries.isNotEmpty()) { "Job nay chua co scene prompts de cao anh." }
                val prompt = buildWebImageUrlPrompt(queries)
                onFetchWebImageUrls?.invoke(jobId, prompt, clientRequestId)
                    ?: throw IllegalStateException("Web image URL driver chua san sang.")
            }
            success().put("clientRequestId", clientRequestId)
        }
    }

    /**
     * Prompt danh cho ChatGPT web (khong phai Gemini) - ChatGPT co the nhung anh
     * THAT truc tiep vao khung tra loi khi duoc hoi kieu nay (da kiem chung bang
     * tay), khac voi Gemini chi dua link tim kiem dang text khong tai duoc.
     */
    /**
     * Gom cac query LIEN TIEP giong nhau thanh nhom {query, count} - vd 3 canh con
     * cua "Goku" -> 1 nhom {"Goku", 3}. Pinterest tim 1 lan/nhom, lay `count` anh.
     * Tra ve JSON: {"groups":[{"query":"Goku","count":3}, ...]} theo dung thu tu canh.
     */
    private fun buildPinterestGroupsJson(missing: List<Pair<Int, String>>): String {
        val groups = JSONArray()
        var i = 0
        while (i < missing.size) {
            val startIndex = missing[i].first
            val q = missing[i].second.trim().ifBlank { "anime" }
            var count = 1
            // Gom cac canh THIEU lien tiep (chiSoGoc lien tiep) va cung query.
            while (i + count < missing.size &&
                missing[i + count].second.trim() == missing[i].second.trim() &&
                missing[i + count].first == missing[i + count - 1].first + 1
            ) {
                count++
            }
            groups.put(JSONObject().put("query", q).put("count", count).put("startIndex", startIndex))
            i += count
        }
        return JSONObject().put("groups", groups).toString()
    }

    private fun buildWebImageUrlPrompt(queries: List<String>): String {
        val itemsBlock = queries.mapIndexed { index, query -> "${index + 1}. $query" }.joinToString("\n")
        return """
Giúp mình tìm và hiện ảnh thật (không phải tạo ảnh AI) cho ${queries.size} mục sau,
lần lượt theo đúng thứ tự:
$itemsBlock

Hiển thị trực tiếp từng ảnh trong câu trả lời, không cần giải thích dài dòng.
""".trimIndent()
    }

    private fun buildWebContentPrompt(payload: JSONObject, provider: String): String {
        val topic = payload.optString("topic").trim()
        require(topic.isNotBlank()) { "Hay nhap chu de noi dung." }
        val durationPolicy = ContentDurationPolicy.fromTopic(
            topic = topic,
            desiredDurationSeconds = payload.optInt("desiredDurationSeconds", 0).takeIf { it > 0 }
        )
        val basePrompt = GeminiContentConnector().buildPrompt(
            ContentGenerationRequest(
                providerId = AutomationFacade.WEB_CONTENT_PROVIDER_ID,
                model = provider,
                topic = topic,
                language = payload.optString("language").trim().ifBlank { "vi" },
                contentType = payload.optString("contentType").trim().ifBlank { AutomationFacade.DEFAULT_CONTENT_TYPE },
                promptTemplate = payload.optString("promptTemplate"),
                maximumOutputLength = payload.optInt("maximumOutputLength", DEFAULT_MAX_OUTPUT_LENGTH),
                durationPolicy = durationPolicy
            )
        )
        if (provider != "chatgpt-web") return basePrompt
        val accountTier = payload.optString("chatGptAccountTier").trim().ifBlank { "free" }
        val mode = payload.optString("chatGptMode").trim().ifBlank { "write" }
        val reasoning = payload.optString("chatGptReasoning").trim().ifBlank { "standard" }
        return buildChatGptWebContentPrompt(basePrompt, accountTier, mode, reasoning)
    }

    private fun buildChatGptWebContentPrompt(basePrompt: String, accountTier: String, mode: String, reasoning: String): String {
        val accountLabel = when (accountTier.lowercase()) {
            "plus" -> "ChatGPT Plus"
            "pro" -> "ChatGPT Pro"
            else -> "ChatGPT thường"
        }
        val modeLabel = when (mode.lowercase()) {
            "web_search" -> "Tìm kiếm web"
            "deep_research" -> "Nghiên cứu sâu"
            else -> "Viết"
        }
        val modeInstruction = when (mode.lowercase()) {
            "web_search" -> "Nếu thật sự cần dữ kiện theo thời điểm hoặc cần kiểm chứng ngoài ngữ cảnh sẵn có, bạn có thể tự tìm trên web. Dù vậy, chỉ trả về kết quả cuối cùng đúng định dạng yêu cầu."
            "deep_research" -> "Hãy suy nghĩ kỹ hơn, lập luận sâu hơn, và ưu tiên chất lượng kịch bản. Dù vậy, chỉ trả về kết quả cuối cùng đúng định dạng yêu cầu."
            else -> "Tập trung vào việc viết kịch bản rõ ràng, tự nhiên, mạch lạc, dễ đọc bằng giọng AI. Không lan man và không dùng web nếu không thật sự cần."
        }
        val reasoningInstruction = when (reasoning.lowercase()) {
            "max" -> "Ưu tiên mức xử lý rất cao: kiểm tra độ mạch lạc toàn bài, chiều sâu từng mục, nhịp kể, và độ dày nội dung cho video dài."
            "high" -> "Ưu tiên mức xử lý cao: suy nghĩ kỹ trước khi trả lời, đảm bảo kịch bản đầy đủ, logic, và khớp thời lượng."
            else -> "Dùng mức xử lý chuẩn: cân bằng giữa tốc độ, độ rõ ràng, và độ chặt của kịch bản."
        }
        return """
Bạn đang được dùng trong luồng tạo kịch bản video tự động của ứng dụng.
Loại tài khoản người dùng đã chọn: $accountLabel.
Chế độ người dùng đã chọn: $modeLabel.
$reasoningInstruction
$modeInstruction
Nếu đề bài nêu số lượng mục cụ thể như top 10, 5 nhân vật, 3 lý do, 7 địa điểm..., bạn phải giữ đúng chính xác số lượng mục đó; không tự ý tăng thêm hoặc bớt đi. Nếu thời lượng video dài, hãy đào sâu nội dung của từng mục thay vì nới số lượng mục.
Không giải thích thêm về cách bạn suy nghĩ. Chỉ trả về kết quả cuối cùng theo đúng định dạng được yêu cầu bên dưới.

$basePrompt
""".trim()
    }

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
            onCancelAutomationTask?.invoke(normalizedId)
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
                // KHONG tra ve cuc rawText dai qua day (bi cat cut o cau noi voi noi
                // dung 300s -> JSON hong). Chi bao CO nội dung; pipeline doc thang tu
                // store bang chinh clientRequestId nay (xem preFetchedRawTaskId).
                .put("hasRawText", task.has("rawText"))
        }
    }

    @JavascriptInterface
    fun findActiveAutomationTaskByJobId(jobId: String): String {
        return respond {
            val normalizedJobId = jobId.trim()
            require(normalizedJobId.isNotBlank()) { "Thieu jobId de tim task dang chay." }
            val clientRequestId = AutomationAsyncTaskStore.findActiveTaskIdByJobId(context, normalizedJobId)
            success()
                .put("found", !clientRequestId.isNullOrBlank())
                .put("clientRequestId", clientRequestId)
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
    @JvmOverloads
    fun getImageProviderConfigurationStatus(requestJson: String = "{}"): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val providerId = payload.optString("providerId").ifBlank { null }
            // Tu phuc hoi (cau hinh rong cho provider khong can credential) CHI
            // cho dung provider dang duoc hoi/chon o day - khong lam voi toan bo
            // danh sach de tranh nhay lung tung selection.
            val status = facade.getImageProviderConfigurationStatus(providerId)
            val providers = facade.listImageProviders()
            success()
                .put("status", status.toJson())
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
                    videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null }),
                    videoQualityTier = payload.optString("videoQualityTier").ifBlank { null },
                    videoBackgroundMode = payload.optString("videoBackgroundMode").ifBlank { null },
                    videoMotionMode = payload.optString("videoMotionMode").ifBlank { null },
                    backgroundMusicFilePath = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getFilePathIfPresent(context),
                    backgroundMusicLoop = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context).loop,
                    backgroundMusicVolume = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context).volume,
                    videoSubtitleColor = payload.optString("videoSubtitleColor").ifBlank { null }
                )
            }
            success().put("job", snapshot.toJson())
        }
    }

    /** Chon 1 file nhac tu may (giong cach chon media da co) lam nhac nen toan cuc cho video. */
    @JavascriptInterface
    fun startBackgroundMusicImport(): String {
        return respond {
            val started = onImportBackgroundMusic?.invoke() ?: false
            require(started) { "Khong the mo trinh chon nhac nen luc nay." }
            success().put("started", true)
        }
    }

    @JavascriptInterface
    fun getBackgroundMusicSettings(): String {
        return respond {
            success().put("settings", com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context).toJson())
        }
    }

    @JavascriptInterface
    fun setBackgroundMusicOptions(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val settings = com.lqlq.browser.automation.AutomationBackgroundMusicStore.setOptions(
                context,
                loop = payload.optBoolean("loop", true),
                volume = payload.optDouble("volume", com.lqlq.browser.automation.AutomationBackgroundMusicStore.DEFAULT_VOLUME.toDouble()).toFloat()
            )
            success().put("settings", settings.toJson())
        }
    }

    @JavascriptInterface
    fun clearBackgroundMusic(): String {
        return respond {
            val settings = com.lqlq.browser.automation.AutomationBackgroundMusicStore.clear(context)
            success().put("settings", settings.toJson())
        }
    }

    private fun com.lqlq.browser.automation.AutomationBackgroundMusicStore.BackgroundMusicSettings.toJson(): JSONObject {
        return JSONObject()
            .put("hasMusic", hasMusic)
            .put("displayName", displayName)
            .put("mimeType", mimeType)
            .put("loop", loop)
            .put("volume", volume)
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

    /**
     * PASS 1 Timeline Editor: dung TimelineProject (read-only) tu snapshot job hien
     * tai + nhac nen toan cuc, tra ve JSON de JS hien thi/kiem tra. Chua co chinh
     * sua/luu — chi la nen de PASS sau lam khung dung video kieu CapCut.
     */
    @JavascriptInterface
    fun getTimelineProject(jobId: String): String {
        return respond {
            val snapshot = runBlocking { facade.getAutomationJob(jobId.trim()) }
                ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
            val music = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context)
            val project = com.lqlq.browser.automation.video.timeline.TimelineProjectBuilder
                .buildFromSnapshot(snapshot, music)
            success().put("timeline", com.lqlq.browser.automation.video.timeline.TimelineProjectJson.toJson(project))
        }
    }

    /** Timeline preview: giọng đọc tổng dạng data URL để <audio> phát khi tua. */
    @JavascriptInterface
    fun getVoiceDataUrl(jobId: String): String {
        return respond {
            val url = runBlocking { facade.getVoiceDataUrl(jobId.trim()) }
            success().put("dataUrl", url ?: "")
        }
    }

    @JavascriptInterface
    fun deleteAutomationJob(jobId: String): String {
        return respond {
            val normalizedJobId = jobId.trim()
            require(normalizedJobId.isNotBlank()) { "Job ID is required." }
            runBlocking { facade.deleteAutomationJob(normalizedJobId) }
            success()
        }
    }

    /** Xem truoc VIDEO_MP4 ngay trong app (VideoView native), khong can export ra Downloads. */
    @JavascriptInterface
    fun previewVideo(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            require(jobId.isNotBlank()) { "Thieu jobId de xem truoc video." }
            val filePath = facade.resolvePreviewableVideoPath(jobId)
                ?: throw IllegalStateException("Job nay chua co VIDEO_MP4 de xem truoc.")
            val started = onPreviewVideo?.invoke(filePath) ?: false
            require(started) { "Khong the mo trinh xem truoc video luc nay." }
            success().put("started", true)
        }
    }

    @JavascriptInterface
    fun getYouTubePublishStatus(): String {
        return respond {
            val cfg = com.lqlq.browser.automation.publish.YouTubePublishStore.getConfig(context)
            success()
                .put("hasCredentials", cfg.hasCredentials)
                .put("connected", cfg.isConnected)
                .put("privacyStatus", cfg.privacyStatus)
                .put("redirectUri", com.lqlq.browser.automation.publish.YouTubePublishStore.REDIRECT_URI)
        }
    }

    @JavascriptInterface
    fun saveYouTubePublishConfig(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val clientId = payload.optString("clientId").trim()
            val clientSecret = payload.optString("clientSecret").trim()
            require(clientId.isNotBlank() && clientSecret.isNotBlank()) { "Can nhap Client ID va Client Secret." }
            com.lqlq.browser.automation.publish.YouTubePublishStore.saveCredentials(context, clientId, clientSecret)
            payload.optString("privacyStatus").ifBlank { null }?.let {
                com.lqlq.browser.automation.publish.YouTubePublishStore.savePrivacy(context, it)
            }
            val cfg = com.lqlq.browser.automation.publish.YouTubePublishStore.getConfig(context)
            success().put("connected", cfg.isConnected).put("hasCredentials", cfg.hasCredentials)
        }
    }

    @JavascriptInterface
    fun setYouTubePrivacy(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            com.lqlq.browser.automation.publish.YouTubePublishStore.savePrivacy(
                context,
                payload.optString("privacyStatus").ifBlank { "private" }
            )
            success()
        }
    }

    @JavascriptInterface
    fun connectYouTubeAccount(): String {
        return respond {
            val cfg = com.lqlq.browser.automation.publish.YouTubePublishStore.getConfig(context)
            require(cfg.hasCredentials) { "Hay nhap va luu Client ID/Secret truoc khi ket noi." }
            val started = onStartYouTubeAuth?.invoke() ?: false
            require(started) { "Khong the mo man hinh dong y Google luc nay." }
            success().put("started", true)
        }
    }

    @JavascriptInterface
    fun disconnectYouTubeAccount(): String {
        return respond {
            com.lqlq.browser.automation.publish.YouTubePublishStore.disconnect(context)
            success()
        }
    }

    /** Dang video da render len YouTube (chay nen qua worker; JS poll getAsyncTaskStatus). */
    @JavascriptInterface
    fun publishToYouTube(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            require(jobId.isNotBlank()) { "Thieu jobId de dang." }
            val cfg = com.lqlq.browser.automation.publish.YouTubePublishStore.getConfig(context)
            require(cfg.isConnected) { "Chua ket noi YouTube. Hay ket noi tai khoan truoc." }
            val clientRequestId = "req-publishYouTube-${System.currentTimeMillis()}-${(1000..9999).random()}"
            enqueueWorker("publishYouTube", requestJson, clientRequestId)
            success().put("clientRequestId", clientRequestId).put("queued", true)
        }
    }

    /** Them 1 canh moi (nguoi dung tu them). Can nhap it nhat loi doc hoac tu khoa. */
    @JavascriptInterface
    fun addScene(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            require(jobId.isNotBlank()) { "Thieu jobId." }
            val voiceText = payload.optString("voiceText").trim()
            val onScreenText = payload.optString("onScreenText").trim()
            val stockSearchQuery = payload.optString("stockSearchQuery").trim()
            require(voiceText.isNotBlank() || stockSearchQuery.isNotBlank()) {
                "Can nhap it nhat Loi doc hoac Tu khoa tim anh cho canh moi."
            }
            val ok = facade.addScene(jobId, voiceText, onScreenText, stockSearchQuery)
            require(ok) { "Khong tim thay job de them canh." }
            success()
        }
    }

    /** Xoa 1 canh. */
    @JavascriptInterface
    fun deleteScene(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val ok = facade.deleteScene(jobId, sceneId)
            require(ok) { "Khong tim thay canh de xoa." }
            success()
        }
    }

    /** Sua tay loi doc + tieu de cua 1 canh (nguoi dung tu chinh noi dung). */
    @JavascriptInterface
    fun updateSceneText(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val ok = facade.updateSceneText(
                jobId = jobId,
                sceneId = sceneId,
                voiceText = payload.optString("voiceText"),
                onScreenText = payload.optString("onScreenText"),
                stockSearchQuery = payload.optString("stockSearchQuery")
            )
            require(ok) { "Khong tim thay canh de cap nhat." }
            success()
        }
    }

    /** Timeline: tach 1 canh tai offsetMs (vach playhead) thanh 2 canh. */
    @JavascriptInterface
    fun splitScene(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            val offsetMs = payload.optLong("offsetMs", 0L)
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val ok = facade.splitScene(jobId, sceneId, offsetMs)
            require(ok) { "Khong tach duoc (vi tri cat qua gan mep canh)." }
            success()
        }
    }

    /** Timeline: dat thoi luong (ms) cho 1 canh (keo mep clip). */
    @JavascriptInterface
    fun setSceneDuration(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            val durationMs = payload.optLong("durationMs", 0L)
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            require(durationMs > 0L) { "Thoi luong khong hop le." }
            val ok = facade.setSceneDuration(jobId, sceneId, durationMs)
            require(ok) { "Khong tim thay canh de dat thoi luong." }
            success()
        }
    }

    /** Doi thu tu 1 canh len/xuong. direction: "up" hoac "down". */
    @JavascriptInterface
    fun moveScene(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            val direction = payload.optString("direction").trim().lowercase()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val delta = if (direction == "up") -1 else 1
            val ok = facade.moveScene(jobId, sceneId, delta)
            require(ok) { "Khong the doi thu tu canh (dau/cuoi danh sach)." }
            success()
        }
    }

    /** Mo trinh chon anh tu may de THAY anh cho DUNG 1 canh (giu anh cac canh khac). */
    @JavascriptInterface
    fun replaceSceneImage(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val started = onReplaceSceneImage?.invoke(jobId, sceneId) ?: false
            require(started) { "Khong the mo trinh chon anh luc nay." }
            success().put("started", true)
        }
    }

    /** Xoa anh 1 canh (danh dau thieu) de luong cao web lay lai dung canh do. */
    @JavascriptInterface
    fun clearSceneImage(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val ok = facade.clearSceneImage(jobId, sceneId)
            require(ok) { "Khong tim thay canh de xoa anh." }
            success()
        }
    }

    /** Tai anh 1 canh ve may (thu vien anh). */
    @JavascriptInterface
    fun downloadSceneImage(requestJson: String): String {
        return respond {
            val payload = parseJsonObject(requestJson)
            val jobId = payload.optString("jobId").trim()
            val sceneId = payload.optString("sceneId").trim()
            require(jobId.isNotBlank() && sceneId.isNotBlank()) { "Thieu jobId hoac sceneId." }
            val path = facade.resolveSceneImagePath(jobId, sceneId)
                ?: throw IllegalStateException("Canh nay chua co anh de tai.")
            val ok = onDownloadImage?.invoke(path) ?: false
            require(ok) { "Khong the tai anh ve may luc nay." }
            success().put("started", true)
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
            aspectRatio = payload.optString("aspectRatio").trim().ifBlank { "9:16" },
            videoRendererMode = normalizeVideoRendererMode(payload.optString("videoRendererMode")),
            videoWorkerUrl = normalizeVideoWorkerUrl(payload.optString("videoWorkerUrl").ifBlank { null }),
            videoQualityTier = payload.optString("videoQualityTier").ifBlank { "1080p" },
            videoBackgroundMode = payload.optString("videoBackgroundMode").ifBlank { "blurred_fill" },
            videoMotionMode = payload.optString("videoMotionMode").ifBlank { "auto_mix" },
            backgroundMusicFilePath = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getFilePathIfPresent(context),
            backgroundMusicLoop = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context).loop,
            backgroundMusicVolume = com.lqlq.browser.automation.AutomationBackgroundMusicStore.getSettings(context).volume,
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
            .put("voiceStatus", voiceStatus)
            .put("videoStatus", videoStatus)
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
            .put("imageStatus", imageStatus)
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
