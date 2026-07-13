package com.lqlq.browser.automation

import kotlinx.coroutines.delay
import com.lqlq.browser.BuildConfig
import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.artifact.AutomationExportedArtifact
import com.lqlq.browser.automation.artifact.AutomationSavedArtifact
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.connector.content.ContentGenerationConnector
import com.lqlq.browser.automation.connector.content.ContentGenerationRequest
import com.lqlq.browser.automation.connector.content.ContentGenerationResult
import com.lqlq.browser.automation.connector.content.ContentProviderConfig
import com.lqlq.browser.automation.connector.content.ContentProviderErrorCode
import com.lqlq.browser.automation.connector.content.ContentProviderException
import com.lqlq.browser.automation.connector.content.GeminiContentConnector
import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.connector.image.CloudflareWorkersAiImageConnector
import com.lqlq.browser.automation.connector.image.ImageProviderAuthType
import com.lqlq.browser.automation.connector.image.DefaultImageProviderRegistry
import com.lqlq.browser.automation.connector.image.ImageGenerationConnector
import com.lqlq.browser.automation.connector.image.ImageGenerationRequest
import com.lqlq.browser.automation.connector.image.ImageGenerationResult
import com.lqlq.browser.automation.connector.image.ImageProviderConfig
import com.lqlq.browser.automation.connector.image.ImageProviderDefinition
import com.lqlq.browser.automation.connector.image.ImageProviderErrorCode
import com.lqlq.browser.automation.connector.image.ImageProviderException
import com.lqlq.browser.automation.connector.image.ImageProviderHealth
import com.lqlq.browser.automation.connector.image.ImageProviderRegistry
import com.lqlq.browser.automation.connector.image.OpenAiImageConnector
import com.lqlq.browser.automation.connector.image.PexelsStockImageConnector
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsConnector
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import com.lqlq.browser.automation.connector.voice.FptAiVoiceConnector
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceProviderAuthType
import com.lqlq.browser.automation.connector.voice.VoiceGenerationConnector
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import com.lqlq.browser.automation.connector.voice.VoiceProviderConnectionResult
import com.lqlq.browser.automation.connector.voice.VoiceProviderDefinition
import com.lqlq.browser.automation.connector.voice.VoiceProviderErrorCode
import com.lqlq.browser.automation.connector.voice.VoiceProviderException
import com.lqlq.browser.automation.connector.voice.VoiceProviderHealth
import com.lqlq.browser.automation.connector.voice.VoiceProviderRegistry
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import com.lqlq.browser.automation.engine.AutomationEngine
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.image.ScenePromptGenerationRequest
import com.lqlq.browser.automation.image.ScenePromptGenerator
import com.lqlq.browser.automation.image.ScriptScenePromptGenerator
import com.lqlq.browser.automation.metadata.HeuristicMetadataGenerator
import com.lqlq.browser.automation.metadata.MetadataGenerationRequest
import com.lqlq.browser.automation.metadata.MetadataGenerator
import com.lqlq.browser.automation.metadata.MetadataPlan
import com.lqlq.browser.automation.model.AutomationFoundationStatus
import com.lqlq.browser.automation.model.AutomationJobStatus
import com.lqlq.browser.automation.model.AutomationStepStatus
import com.lqlq.browser.automation.publish.PublishPlan
import com.lqlq.browser.automation.repository.AutomationConnectorBindingRecord
import com.lqlq.browser.automation.repository.AutomationJobGraphSnapshot
import com.lqlq.browser.automation.repository.AutomationJobRecord
import com.lqlq.browser.automation.repository.AutomationOutboxEventRecord
import com.lqlq.browser.automation.repository.AutomationProjectRecord
import com.lqlq.browser.automation.repository.AutomationRepository
import com.lqlq.browser.automation.repository.AutomationStepDependencyRecord
import com.lqlq.browser.automation.repository.AutomationStepRecord
import com.lqlq.browser.automation.repository.AutomationWorkflowDefinitionRecord
import com.lqlq.browser.automation.repository.CreateAutomationJobGraphCommand
import com.lqlq.browser.automation.review.ReviewCheck
import com.lqlq.browser.automation.review.ReviewState
import com.lqlq.browser.automation.script.ContentDurationPolicy
import com.lqlq.browser.automation.script.ScriptSegmentKind
import com.lqlq.browser.automation.script.StructuredScript
import com.lqlq.browser.automation.script.StructuredScriptParser
import com.lqlq.browser.automation.visual.HeuristicVisualAssetPlanner
import com.lqlq.browser.automation.visual.VisualAssetPlan
import com.lqlq.browser.automation.visual.VisualAssetPlanJson
import com.lqlq.browser.automation.visual.VisualAssetPlanRequest
import com.lqlq.browser.automation.visual.VisualAssetPlanner
import com.lqlq.browser.automation.video.LocalPlanVideoRenderer
import com.lqlq.browser.automation.video.NoOpVideoRenderWorkerClient
import com.lqlq.browser.automation.video.NoOpNativeVideoRenderer
import com.lqlq.browser.automation.video.ExternalMoviePyVideoRenderer
import com.lqlq.browser.automation.video.AndroidNativeSlideshowVideoRenderer
import com.lqlq.browser.automation.video.NativeVideoRenderer
import com.lqlq.browser.automation.video.VideoRenderPlan
import com.lqlq.browser.automation.video.VideoRenderPlanJson
import com.lqlq.browser.automation.video.VideoRenderRequest
import com.lqlq.browser.automation.video.VideoRenderer
import com.lqlq.browser.automation.video.VideoRenderWorkerClient
import com.lqlq.browser.automation.voice.WavAudioAssembler
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AutomationFacade private constructor(
    private val engine: AutomationEngine,
    private val repository: AutomationRepository,
    private val artifactStore: AutomationArtifactStore,
    private val credentialStore: AutomationCredentialStore,
    private val contentConnector: ContentGenerationConnector,
    private val scenePromptGenerator: ScenePromptGenerator,
    private val visualAssetPlanner: VisualAssetPlanner,
    private val imageProviderRegistry: ImageProviderRegistry,
    private val imageConnectors: Map<String, ImageGenerationConnector>,
    private val voiceProviderRegistry: VoiceProviderRegistry,
    private val voiceConnectors: Map<String, VoiceGenerationConnector>,
    private val videoRenderer: VideoRenderer,
    private val nativeVideoRenderer: NativeVideoRenderer,
    private val videoRenderWorkerClient: VideoRenderWorkerClient,
    private val metadataGenerator: MetadataGenerator,
    private val progressListener: AutomationPipelineProgressListener,
    private val runtimeJobStore: RuntimeJobStore? = null
) {

    private val runtimeJobs = ConcurrentHashMap<String, RuntimeAutomationJob>()
    private val runtimeProgressClientIds = ConcurrentHashMap<String, String>()
    @Volatile private var restoredFromDisk = false

    /**
     * Ghi runtimeJob vao RAM + LUU xuong dia (JSON) de app kill/mo lai khong mat.
     * Moi cho truoc day lam `runtimeJobs[jobId] = x` deu chuyen sang goi ham nay.
     */
    private fun putRuntimeJob(jobId: String, job: RuntimeAutomationJob) {
        runtimeJobs[jobId] = job
        runCatching { runtimeJobStore?.save(jobId, serializeRuntimeJob(job).toString()) }
    }

    private fun persistRuntimeJob(jobId: String) {
        val job = runtimeJobs[jobId] ?: return
        runCatching { runtimeJobStore?.save(jobId, serializeRuntimeJob(job).toString()) }
    }

    /** Nap lai tat ca runtimeJob da luu (goi 1 lan khi khoi tao app). */
    fun restorePersistedRuntimeJobs() {
        if (restoredFromDisk) return
        restoredFromDisk = true
        val store = runtimeJobStore ?: return
        runCatching {
            store.loadAll().forEach { (jobId, json) ->
                runCatching {
                    deserializeRuntimeJob(JSONObject(json))?.let { runtimeJobs.putIfAbsent(jobId, it) }
                }
            }
        }
    }

    // --- Serialize/deserialize RuntimeAutomationJob (bo qua metadataPlan/reviewState/
    //     publishPlan/videoRenderPlan - se tu sinh lai khi retry; bo previewDataUrl de
    //     file nho, anh van doc tu file that de render). ---

    private fun serializeRuntimeJob(job: RuntimeAutomationJob): JSONObject {
        return JSONObject().apply {
            put("status", job.status)
            put("generatedText", job.generatedText)
            put("contentProviderId", job.contentProviderId)
            put("contentModel", job.contentModel)
            put("requestId", job.requestId)
            put("usageMetadata", JSONObject().apply { job.usageMetadata.forEach { (k, v) -> put(k, v) } })
            put("scenePrompts", JSONArray().apply { job.scenePrompts.forEach { put(scenePromptToJson(it)) } })
            put("assetPlans", JSONArray().apply { job.assetPlans.forEach { put(assetPlanToJson(it)) } })
            put("artifacts", JSONArray().apply { job.artifacts.forEach { put(artifactToJson(it)) } })
            put("runtimeMessage", job.runtimeMessage)
            put("stepOverrides", JSONObject().apply {
                job.stepOverrides.forEach { (k, v) -> put(k, JSONObject().put("status", v.status).put("waitingReason", v.waitingReason)) }
            })
            put("imageProviderId", job.imageProviderId)
            put("imageModel", job.imageModel)
            put("imageAttemptHistory", JSONArray(job.imageAttemptHistory))
            put("voiceProviderId", job.voiceProviderId)
            put("voiceId", job.voiceId)
            put("voiceLocale", job.voiceLocale)
            put("voiceAttemptHistory", JSONArray(job.voiceAttemptHistory))
            put("videoRendererId", job.videoRendererId)
            put("videoAttemptHistory", JSONArray(job.videoAttemptHistory))
            put("videoRendererMode", job.videoRendererMode)
            put("videoWorkerUrl", job.videoWorkerUrl)
            put("videoQualityTier", job.videoQualityTier)
            put("videoBackgroundMode", job.videoBackgroundMode)
            put("videoMotionMode", job.videoMotionMode)
            put("backgroundMusicFilePath", job.backgroundMusicFilePath)
            put("backgroundMusicLoop", job.backgroundMusicLoop)
            put("backgroundMusicVolume", job.backgroundMusicVolume.toDouble())
            put("videoSubtitleColor", job.videoSubtitleColor)
            put("lastVoiceSignature", job.lastVoiceSignature)
            put("lastVideoSignature", job.lastVideoSignature)
        }
    }

    private fun deserializeRuntimeJob(o: JSONObject): RuntimeAutomationJob? {
        return runCatching {
            val usage = linkedMapOf<String, Long>()
            o.optJSONObject("usageMetadata")?.let { u ->
                u.keys().forEach { key -> usage[key] = u.optLong(key) }
            }
            val stepOverrides = linkedMapOf<String, StepOverride>()
            o.optJSONObject("stepOverrides")?.let { s ->
                s.keys().forEach { key ->
                    val so = s.optJSONObject(key) ?: return@forEach
                    stepOverrides[key] = StepOverride(so.optString("status"), so.optString("waitingReason"))
                }
            }
            RuntimeAutomationJob(
                status = o.optString("status"),
                generatedText = o.optString("generatedText"),
                contentProviderId = o.optString("contentProviderId"),
                contentModel = o.optString("contentModel"),
                requestId = o.optStringOrNull("requestId"),
                usageMetadata = usage,
                scenePrompts = o.optJSONArray("scenePrompts")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::scenePromptFromJson) } } ?: emptyList(),
                assetPlans = o.optJSONArray("assetPlans")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::assetPlanFromJson) } } ?: emptyList(),
                videoRenderPlan = null,
                metadataPlan = null,
                reviewState = null,
                publishPlan = null,
                artifacts = o.optJSONArray("artifacts")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::artifactFromJson) } } ?: emptyList(),
                runtimeMessage = o.optString("runtimeMessage"),
                stepOverrides = stepOverrides,
                imageProviderId = o.optStringOrNull("imageProviderId"),
                imageModel = o.optStringOrNull("imageModel"),
                imageAttemptHistory = o.optJSONArray("imageAttemptHistory").toStringList(),
                voiceProviderId = o.optStringOrNull("voiceProviderId"),
                voiceId = o.optStringOrNull("voiceId"),
                voiceLocale = o.optStringOrNull("voiceLocale"),
                voiceAttemptHistory = o.optJSONArray("voiceAttemptHistory").toStringList(),
                videoRendererId = o.optStringOrNull("videoRendererId"),
                videoAttemptHistory = o.optJSONArray("videoAttemptHistory").toStringList(),
                videoRendererMode = o.optString("videoRendererMode").ifBlank { "android_native_render" },
                videoWorkerUrl = o.optStringOrNull("videoWorkerUrl"),
                videoQualityTier = o.optString("videoQualityTier").ifBlank { "1080p" },
                videoBackgroundMode = o.optString("videoBackgroundMode").ifBlank { "blurred_fill" },
                videoMotionMode = o.optString("videoMotionMode").ifBlank { "auto_mix" },
                backgroundMusicFilePath = o.optStringOrNull("backgroundMusicFilePath"),
                backgroundMusicLoop = o.optBoolean("backgroundMusicLoop", true),
                backgroundMusicVolume = o.optDouble("backgroundMusicVolume", 0.35).toFloat(),
                videoSubtitleColor = o.optString("videoSubtitleColor").ifBlank { "#FFFFFF" },
                lastVoiceSignature = o.optString("lastVoiceSignature"),
                lastVideoSignature = o.optString("lastVideoSignature")
            )
        }.getOrNull()
    }

    private fun scenePromptToJson(s: ScenePrompt): JSONObject = JSONObject()
        .put("sceneId", s.sceneId).put("ordinal", s.ordinal).put("summary", s.summary)
        .put("visualPrompt", s.visualPrompt).put("negativePrompt", s.negativePrompt)
        .put("aspectRatio", s.aspectRatio).put("voiceText", s.voiceText).put("onScreenText", s.onScreenText)
        .put("plannedDurationMs", s.plannedDurationMs).put("stockSearchQuery", s.stockSearchQuery)
        .put("visualDirection", s.visualDirection).put("imageSignature", s.imageSignature)

    private fun scenePromptFromJson(o: JSONObject): ScenePrompt = ScenePrompt(
        sceneId = o.optString("sceneId"), ordinal = o.optInt("ordinal"), summary = o.optString("summary"),
        visualPrompt = o.optString("visualPrompt"), negativePrompt = o.optStringOrNull("negativePrompt"),
        aspectRatio = o.optString("aspectRatio").ifBlank { "9:16" }, voiceText = o.optString("voiceText"),
        onScreenText = o.optString("onScreenText"), plannedDurationMs = o.optLong("plannedDurationMs"),
        stockSearchQuery = o.optString("stockSearchQuery"), visualDirection = o.optString("visualDirection"),
        imageSignature = o.optString("imageSignature")
    )

    private fun assetPlanToJson(a: VisualAssetPlan): JSONObject = JSONObject()
        .put("sceneId", a.sceneId).put("ordinal", a.ordinal).put("strategy", a.strategy)
        .put("preferredProviderId", a.preferredProviderId).put("assetQuery", a.assetQuery)
        .put("templateId", a.templateId).put("renderMode", a.renderMode).put("durationMs", a.durationMs)
        .put("rationale", a.rationale)

    private fun assetPlanFromJson(o: JSONObject): VisualAssetPlan = VisualAssetPlan(
        sceneId = o.optString("sceneId"), ordinal = o.optInt("ordinal"), strategy = o.optString("strategy"),
        preferredProviderId = o.optStringOrNull("preferredProviderId"), assetQuery = o.optString("assetQuery"),
        templateId = o.optString("templateId"), renderMode = o.optString("renderMode"),
        durationMs = o.optLong("durationMs"), rationale = o.optString("rationale")
    )

    private fun artifactToJson(a: AutomationSavedArtifact): JSONObject = JSONObject()
        .put("artifactId", a.artifactId).put("artifactType", a.artifactType).put("mimeType", a.mimeType)
        .put("uri", a.uri).put("sizeBytes", a.sizeBytes).put("sourceUrl", a.sourceUrl)
        .put("sceneId", a.sceneId).put("ordinal", a.ordinal ?: JSONObject.NULL)
        .put("providerRequestId", a.providerRequestId)

    private fun artifactFromJson(o: JSONObject): AutomationSavedArtifact = AutomationSavedArtifact(
        artifactId = o.optString("artifactId"), artifactType = o.optString("artifactType"),
        mimeType = o.optString("mimeType"), uri = o.optString("uri"), sizeBytes = o.optLong("sizeBytes"),
        sourceUrl = o.optStringOrNull("sourceUrl"), sceneId = o.optStringOrNull("sceneId"),
        ordinal = if (o.has("ordinal") && !o.isNull("ordinal")) o.optInt("ordinal") else null,
        providerRequestId = o.optStringOrNull("providerRequestId"), previewDataUrl = null
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }

    private fun reportProgress(
        jobId: String,
        topic: String,
        completedSteps: Int,
        state: String,
        message: String
    ) {
        runCatching {
            progressListener.onProgress(
                AutomationPipelineProgress(
                    jobId = jobId,
                    clientRequestId = runtimeProgressClientIds[jobId],
                    topic = topic,
                    completedSteps = completedSteps,
                    totalSteps = 11,
                    state = state,
                    message = message
                )
            )
        }
    }

    fun getFoundationStatus(): AutomationFoundationStatus = engine.getFoundationStatus()

    fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
        return credentialStore.getGeminiConfigurationStatus()
    }

    fun saveGeminiConfiguration(
        apiKey: String,
        model: String
    ): AutomationCredentialStatusSnapshot {
        val normalizedApiKey = apiKey.trim()
        val normalizedModel = model.trim()
        require(normalizedApiKey.isNotEmpty()) { "Can nhap API key Gemini." }
        require(normalizedModel.isNotEmpty()) { "Can nhap model Gemini." }

        credentialStore.saveGeminiConfiguration(
            apiKey = normalizedApiKey,
            model = normalizedModel
        )
        return credentialStore.getGeminiConfigurationStatus()
    }

    fun listImageProviders(): List<AutomationUiImageProviderSnapshot> {
        // KHONG tu cau hinh/chon lai o day - ham nay liet ke TOAN BO provider de
        // hien thi dropdown, tu dong luu+chon tung provider o day se lam "provider
        // dang chon" nhay lung tung sang provider duyet cuoi cung trong danh sach
        // moi lan mo lai man Cai dat. Tu phuc hoi chi nen ap dung cho DUNG provider
        // dang duoc yeu cau/chon (xem getImageProviderConfigurationStatus).
        val selectedProviderId = resolveSelectedImageProviderId()
        return imageProviderRegistry.allDefinitions().map { definition ->
            AutomationUiImageProviderSnapshot(
                providerId = definition.providerId,
                displayName = definition.displayName,
                authType = definition.authType.name,
                costType = definition.costType.name,
                health = definition.health.name,
                selected = definition.providerId == selectedProviderId,
                supportedModels = definition.capabilities.supportedModels,
                defaultModel = definition.capabilities.defaultModel,
                syncOrAsync = definition.capabilities.syncOrAsync,
                supportsPolling = definition.capabilities.supportsPolling,
                supportsAspectRatio = definition.capabilities.supportsAspectRatio,
                supportsNegativePrompt = definition.capabilities.supportsNegativePrompt,
                supportedOutputFormats = definition.capabilities.supportedOutputFormats,
                maxImagesPerJob = definition.capabilities.maxImagesPerJob,
                stabilityLevel = definition.capabilities.stabilityLevel,
                configurationStatus = credentialStore.getImageProviderConfigurationStatus(definition.providerId)
            )
        }
    }

    fun getImageProviderConfigurationStatus(
        providerId: String? = null
    ): AutomationCredentialStatusSnapshot {
        val resolvedProviderId = resolveRequestedProviderId(providerId) ?: AutomationImageProviders.OPENAI_IMAGES
        imageProviderRegistry.getDefinition(resolvedProviderId)?.let { definition ->
            if (definition.authType == ImageProviderAuthType.NONE) {
                ensureNoAuthImageProviderConfigured(definition)
            } else if (credentialStore.getImageProviderConfiguration(definition.providerId) != null) {
                // Khi nguoi dung doi qua lai giua cac provider da tung luu
                // (vd Cloudflare/Pexels), chi viec mo dropdown chon provider moi
                // cung phai du de native ghi nhan "dang chon provider nay", khong
                // bat nguoi dung phai bam Luu lai moi lan. Neu khong, pipeline van
                // chay bang provider cu va gay cam giac "doi provider khong an".
                credentialStore.setSelectedImageProviderId(definition.providerId)
            }
        }
        return credentialStore.getImageProviderConfigurationStatus(resolvedProviderId)
    }

    // Provider khong can credential (Tu dong/Openverse/Wikimedia...) khong nen bat
    // nguoi dung vao Cai dat luu tay truoc khi dung duoc - tu luu 1 ban ghi cau
    // hinh rong ngay khi duoc truy van trang thai/danh sach, thay vi phu thuoc vao
    // dung thoi diem JS goi refresh/luu (de xay ra sai lech neu co gang luu that
    // bai tam thoi truoc do).
    private fun ensureNoAuthImageProviderConfigured(definition: ImageProviderDefinition) {
        if (definition.authType != ImageProviderAuthType.NONE) return
        // Ham nay CHI duoc goi khi providerId duoc yeu cau tuong minh (nguoi dung
        // vua chon trong dropdown) - nen luon danh dau la "dang chon" o day, ke ca
        // khi da tung cau hinh truoc do. Neu chi cap nhat "selected" luc LUU LAN
        // DAU thi lan chon lai 1 provider da tung dung se khong bao gio duoc chon
        // lai (day la ly do dropdown bi ket cung mot provider truoc do).
        if (credentialStore.getImageProviderConfiguration(definition.providerId) != null) {
            credentialStore.setSelectedImageProviderId(definition.providerId)
            return
        }
        runCatching {
            credentialStore.saveImageProviderConfiguration(
                providerId = definition.providerId,
                apiKey = "",
                model = definition.capabilities.defaultModel.orEmpty(),
                accountId = null
            )
        }
    }

    private fun ensureNoAuthVoiceProviderConfigured(definition: VoiceProviderDefinition) {
        if (definition.authType != VoiceProviderAuthType.NONE) return
        if (credentialStore.getVoiceProviderConfiguration(definition.providerId) != null) {
            credentialStore.setSelectedVoiceProviderId(definition.providerId)
            return
        }
        runCatching {
            credentialStore.saveVoiceProviderConfiguration(
                VoiceProviderCredentialConfiguration(
                    providerId = definition.providerId,
                    locale = definition.capabilities.defaultLocale,
                    outputFormat = definition.capabilities.supportedOutputFormats.firstOrNull() ?: "wav"
                )
            )
        }
    }

    fun listVoiceProviders(): List<AutomationUiVoiceProviderSnapshot> {
        // Tuong tu listImageProviders: khong tu chon lai o day, chi hien thi.
        val selectedProviderId = resolveUiVoiceProviderId()
        return voiceProviderRegistry.allDefinitions().map { definition ->
            AutomationUiVoiceProviderSnapshot(
                providerId = definition.providerId,
                displayName = definition.displayName,
                authType = definition.authType.name,
                costType = definition.costType.name,
                health = definition.health.name,
                selected = definition.providerId == selectedProviderId,
                supportedLocales = definition.capabilities.supportedLocales,
                verifiedLocales = definition.capabilities.verifiedLocales,
                defaultLocale = definition.capabilities.defaultLocale,
                supportedOutputFormats = definition.capabilities.supportedOutputFormats,
                supportsPitch = definition.capabilities.supportsPitch,
                supportsSpeechRate = definition.capabilities.supportsSpeechRate,
                supportsSamplePreview = definition.capabilities.supportsSamplePreview,
                supportsChunking = definition.capabilities.supportsChunking,
                requiresCredentials = definition.capabilities.requiresCredentials,
                stabilityLevel = definition.capabilities.stabilityLevel,
                configurationStatus = credentialStore.getVoiceProviderConfigurationStatus(definition.providerId)
            )
        }
    }

    fun listVoiceDefinitions(providerId: String? = null): List<AutomationUiVoiceDefinitionSnapshot> {
        val definition = requireSupportedVoiceProviderDefinition(
            resolveRequestedVoiceProviderId(providerId) ?: resolveUiVoiceProviderId().orEmpty()
        )
        if (definition.health == VoiceProviderHealth.NOT_IMPLEMENTED) {
            return emptyList()
        }
        val connector = requireVoiceConnector(definition.providerId)
        return connector.listVoices().map {
            AutomationUiVoiceDefinitionSnapshot(
                voiceId = it.voiceId,
                displayName = it.displayName,
                locale = it.locale,
                engineName = it.engineName,
                networkRequired = it.networkRequired,
                installed = it.installed,
                isDefault = it.isDefault,
                genderHint = it.genderHint
            )
        }
    }

    fun getVoiceProviderConfigurationStatus(
        providerId: String? = null
    ): AutomationCredentialStatusSnapshot {
        val resolvedProviderId = resolveRequestedVoiceProviderId(providerId) ?: resolveUiVoiceProviderId().orEmpty()
        voiceProviderRegistry.getDefinition(resolvedProviderId)?.let(::ensureNoAuthVoiceProviderConfigured)
        return credentialStore.getVoiceProviderConfigurationStatus(resolvedProviderId)
    }

    fun saveImageProviderConfiguration(
        providerId: String,
        apiKey: String,
        model: String,
        accountId: String? = null
    ): AutomationCredentialStatusSnapshot {
        val definition = requireSupportedImageProviderDefinition(providerId)
        val normalizedApiKey = apiKey.trim()
        val normalizedModel = normalizeImageModel(definition, model)
        val normalizedAccountId = normalizeImageAccountId(definition, accountId)

        // Provider khong can credential (vd Openverse/Wikimedia tim anh mien phi,
        // khong key) thi KHONG duoc nhan API key, nguoc lai cac provider con lai
        // van bat buoc phai co — giong logic da ap dung cho voice provider.
        if (definition.authType == ImageProviderAuthType.NONE) {
            require(normalizedApiKey.isEmpty()) { "Provider local khong nhan API key." }
        } else {
            require(normalizedApiKey.isNotEmpty()) { "Can nhap API key image provider." }
        }
        validateImageProviderConfigFields(definition, normalizedModel, normalizedAccountId)

        credentialStore.saveImageProviderConfiguration(
            providerId = definition.providerId,
            apiKey = normalizedApiKey,
            model = normalizedModel,
            accountId = normalizedAccountId
        )
        return credentialStore.getImageProviderConfigurationStatus(definition.providerId)
    }

    fun saveVoiceProviderConfiguration(
        providerId: String,
        locale: String,
        voiceId: String?,
        model: String?,
        apiKey: String?,
        region: String?,
        engineName: String?,
        speechRate: Float,
        pitch: Float,
        outputFormat: String
    ): AutomationCredentialStatusSnapshot {
        val definition = requireSupportedVoiceProviderDefinition(providerId)
        val normalizedLocale = locale.trim().ifBlank { definition.capabilities.defaultLocale }
        val normalizedOutputFormat = outputFormat.trim().ifBlank { definition.capabilities.supportedOutputFormats.firstOrNull() ?: "wav" }
        val normalizedApiKey = apiKey?.trim()?.ifBlank { null }
        val normalizedRegion = region?.trim()?.ifBlank { null }
        val normalizedEngineName = engineName?.trim()?.ifBlank { null }
        val normalizedModel = model?.trim()?.ifBlank { null }
        validateVoiceProviderConfigFields(
            definition = definition,
            model = normalizedModel,
            apiKey = normalizedApiKey,
            region = normalizedRegion
        )
        credentialStore.saveVoiceProviderConfiguration(
            VoiceProviderCredentialConfiguration(
                providerId = definition.providerId,
                locale = normalizedLocale,
                voiceId = voiceId?.trim()?.ifBlank { null },
                model = normalizedModel,
                speechRate = speechRate,
                pitch = pitch,
                outputFormat = normalizedOutputFormat,
                engineName = normalizedEngineName,
                apiKey = normalizedApiKey,
                region = normalizedRegion
            )
        )
        return credentialStore.getVoiceProviderConfigurationStatus(definition.providerId)
    }

    suspend fun testGeminiConnection(): AutomationConnectionTestResult {
        val config = requireGeminiConfiguration()
        return try {
            val result = contentConnector.testConnection(
                ContentProviderConfig(
                    providerId = config.providerId,
                    apiKey = config.apiKey,
                    model = config.model,
                    maximumOutputLength = 128
                )
            )
            AutomationConnectionTestResult(
                state = AutomationCredentialStore.STATE_CONNECTED,
                providerId = result.providerId,
                model = result.model,
                message = "Da ket noi Gemini thanh cong."
            )
        } catch (error: ContentProviderException) {
            if (error.code == ContentProviderErrorCode.AUTHENTICATION) {
                credentialStore.markGeminiInvalid(error.message)
            }
            throw error
        }
    }

    suspend fun testImageProviderConnection(
        providerId: String? = null
    ): AutomationConnectionTestResult {
        val definition = requireRunnableImageProviderDefinition(providerId)
        val config = requireImageProviderConfiguration(definition.providerId)
        val connector = requireImageConnector(definition.providerId)
        return try {
            val result = connector.testConnection(
                ImageProviderConfig(
                    providerId = config.providerId,
                    apiKey = config.apiKey,
                    model = config.model,
                    accountId = config.accountId
                )
            )
            credentialStore.markImageProviderState(
                definition.providerId,
                AutomationCredentialStore.IMAGE_STATE_CREDENTIAL_VALIDATED,
                "Credential da duoc xac thuc voi ${definition.displayName}."
            )
            AutomationConnectionTestResult(
                state = AutomationCredentialStore.IMAGE_STATE_CREDENTIAL_VALIDATED,
                providerId = result.providerId,
                model = result.model,
                message = "Credential image provider hop le."
            )
        } catch (error: ImageProviderException) {
            updateImageProviderFailureState(definition.providerId, error)
            throw error
        }
    }

    suspend fun testVoiceProviderConnection(
        providerId: String? = null
    ): AutomationConnectionTestResult {
        val definition = requireRunnableVoiceProviderDefinition(providerId)
        val config = requireVoiceProviderConfiguration(definition.providerId)
        val connector = requireVoiceConnector(definition.providerId)
        return try {
            val result = connector.testConnection(
                config.toVoiceProviderConfig()
            )
            credentialStore.markVoiceProviderState(
                definition.providerId,
                AutomationCredentialStore.VOICE_STATE_VOICE_LIST_LOADED,
                "Da nap danh sach giong doc voi ${definition.displayName}."
            )
            AutomationConnectionTestResult(
                state = AutomationCredentialStore.VOICE_STATE_VOICE_LIST_LOADED,
                providerId = result.providerId,
                model = result.model ?: result.voiceId,
                message = "Voice provider da san sang va da nap danh sach giong."
            )
        } catch (error: VoiceProviderException) {
            updateVoiceProviderFailureState(definition.providerId, error)
            throw error
        }
    }

    suspend fun synthesizeVoiceSample(
        providerId: String? = null
    ): AutomationUiArtifactSnapshot {
        val definition = requireRunnableVoiceProviderDefinition(providerId)
        val config = requireVoiceProviderConfiguration(definition.providerId)
        val connector = requireVoiceConnector(definition.providerId)
        val result = connector.synthesizeSample(
            config.toVoiceProviderConfig(),
            when (definition.providerId) {
                AutomationVoiceProviders.FPT_AI_TTS -> FptAiVoiceConnector.SAMPLE_TEXT
                else -> AndroidSystemTtsConnector.SAMPLE_TEXT
            }
        )
        credentialStore.markVoiceProviderState(
            definition.providerId,
            AutomationCredentialStore.VOICE_STATE_SAMPLE_VERIFIED,
            "Da nghe thu giong doc voi ${definition.displayName}."
        )
        return AutomationUiArtifactSnapshot(
            artifactId = "voice-sample-${shortId()}",
            artifactType = "VOICE_SAMPLE",
            mimeType = result.mimeType,
            uri = "automation://voice-sample/${result.providerId}",
            sizeBytes = result.bytes.size.toLong(),
            sourceUrl = null,
            sceneId = null,
            ordinal = null,
            providerRequestId = null,
            previewDataUrl = "data:${result.mimeType};base64," + android.util.Base64.encodeToString(result.bytes, android.util.Base64.NO_WRAP)
        )
    }

    fun openVoiceProviderSettings(providerId: String? = null) {
        val definition = requireRunnableVoiceProviderDefinition(providerId)
        requireVoiceConnector(definition.providerId).openProviderSettings()
    }

    suspend fun generateAutomationContent(
        request: AutomationContentRunRequest
    ): AutomationUiJobSnapshot {
        val normalizedRequest = request.normalized()
        val usingWebContentSource = !normalizedRequest.preFetchedRawText.isNullOrBlank()
        val contentConfig = if (usingWebContentSource) {
            GeminiCredentialConfiguration(providerId = WEB_CONTENT_PROVIDER_ID, apiKey = "", model = "gemini-web")
        } else {
            requireGeminiConfiguration()
        }
        val selectedImageProviderId = resolveSelectedImageProviderId()
        val selectedImageDefinition = selectedImageProviderId?.let(imageProviderRegistry::getDefinition)

        ensureAutomationProject()
        ensureAutomationWorkflow()

        val createdAtEpochMs = System.currentTimeMillis()
        val jobId = "job-automation-${createdAtEpochMs}-${shortId()}"
        normalizedRequest.clientRequestId?.takeIf { it.isNotBlank() }?.let {
            runtimeProgressClientIds[jobId] = it
        }
        val workflowVersion = AUTOMATION_WORKFLOW_VERSION

        val imageBindingConnectorId = selectedImageDefinition?.providerId ?: "not-configured-image"

        val workflowStepSpecs = listOf(
            WorkflowStepSpec("topic", "TOPIC", "local-topic", "local"),
            WorkflowStepSpec("content", "CONTENT", contentConfig.providerId, "real"),
            WorkflowStepSpec("scene-prompts", "SCENE_PROMPTS", "local-scene-prompts", "local-clean-room"),
            WorkflowStepSpec("asset-plan", "ASSET_PLAN", "local-asset-planner", "local-heuristic"),
            WorkflowStepSpec("images-visuals", "IMAGES_VISUALS", imageBindingConnectorId, imageBindingMode(selectedImageDefinition)),
            WorkflowStepSpec("voice", "VOICE", "not-configured-voice", "not_configured"),
            WorkflowStepSpec("subtitles", "SUBTITLE", "local-subtitle-plan", "not_configured"),
            WorkflowStepSpec("video", "VIDEO", "not-configured-video", "not_configured"),
            WorkflowStepSpec("metadata", "METADATA", "not-configured-metadata", "not_configured"),
            WorkflowStepSpec("review", "REVIEW", "manual-review-gate", "manual"),
            WorkflowStepSpec("publish", "PUBLISH", "not-configured-publish", "not_configured")
        )

        val bindings = workflowStepSpecs.mapIndexed { index, spec ->
            connectorBinding(
                bindingId = "binding-${spec.stepKey}-$jobId",
                projectId = AUTOMATION_PROJECT_ID,
                jobId = jobId,
                connectorId = spec.connectorId,
                category = spec.stepType,
                mode = spec.mode,
                createdAtEpochMs = createdAtEpochMs + index
            )
        }

        val steps = workflowStepSpecs.mapIndexed { index, spec ->
            step(
                stepId = "step-${spec.stepKey}-$jobId",
                jobId = jobId,
                stepKey = spec.stepKey,
                stepType = spec.stepType,
                connectorBindingId = bindings[index].bindingId
            )
        }

        val dependencies = steps.zipWithNext().mapIndexed { index, pair ->
            dependency(
                dependencyId = "dep-${(index + 1).toString().padStart(2, '0')}-$jobId",
                jobId = jobId,
                fromStepId = pair.first.stepId,
                toStepId = pair.second.stepId
            )
        }

        val job = AutomationJobRecord(
            jobId = jobId,
            projectId = AUTOMATION_PROJECT_ID,
            workflowId = AUTOMATION_WORKFLOW_ID,
            workflowVersion = workflowVersion,
            status = AutomationJobStatus.DRAFT.name,
            createdAtEpochMs = createdAtEpochMs,
            scheduledAtEpochMs = null,
            startedAtEpochMs = null,
            completedAtEpochMs = null,
            currentStepId = null,
            lastErrorCode = null,
            lastErrorSummary = null,
            cancelRequested = false,
            pauseRequested = false,
            revision = 0L,
            retryOfJobId = null,
            scheduleOccurrenceAtEpochMs = null
        )

        val outbox = AutomationOutboxEventRecord(
            outboxEventId = "outbox-$jobId",
            eventType = "JOB_CREATED",
            aggregateType = "JOB",
            aggregateId = jobId,
            payloadJson = JSONObject()
                .put("jobId", jobId)
                .put("projectId", AUTOMATION_PROJECT_ID)
                .put("topic", normalizedRequest.topic)
                .put("language", normalizedRequest.language)
                .put("publishMode", DEFAULT_PUBLISH_MODE)
                .put("contentProvider", contentConfig.providerId)
                .put("imageProvider", imageBindingConnectorId)
                .put("runtimeMode", "script-scene-asset-template-voice-subtitle-review-publish")
                .toString(),
            payloadSchemaVersion = 1,
            dedupeKey = "job-created-$jobId",
            status = "PENDING",
            availableAtEpochMs = createdAtEpochMs,
            claimedAtEpochMs = null,
            claimOwner = null,
            claimExpiresAtEpochMs = null,
            dispatchedWorkName = null,
            processedAtEpochMs = null,
            attemptCount = 0,
            lastError = null,
            deadLetteredAtEpochMs = null,
            revision = 0L
        )

        val persistedSnapshot = repository.createJobGraph(
            CreateAutomationJobGraphCommand(
                job = job,
                connectorBindings = bindings,
                steps = steps,
                dependencies = dependencies,
                initialOutboxEvents = listOf(outbox)
            )
        )

        reportProgress(
            jobId = jobId,
            topic = normalizedRequest.topic,
            completedSteps = 1,
            state = AutomationJobStatus.RUNNING.name,
            message = "Đã nhận chủ đề, đang yêu cầu Gemini tạo nội dung."
        )

        val artifacts = mutableListOf<AutomationSavedArtifact>()
        val stepOverrides = mutableMapOf<String, StepOverride>()
        val scenePrompts = mutableListOf<ScenePrompt>()
        val assetPlans = mutableListOf<VisualAssetPlan>()
        val durationPolicy = ContentDurationPolicy.fromTopic(
            topic = normalizedRequest.topic,
            desiredDurationSeconds = normalizedRequest.desiredDurationSeconds
        )

        stepOverrides["TOPIC"] = StepOverride(
            status = AutomationStepStatus.COMPLETED.name,
            waitingReason = "TOPIC_ACCEPTED"
        )

        val contentResult = if (usingWebContentSource) {
            buildContentResultFromRawText(
                rawText = normalizedRequest.preFetchedRawText.orEmpty(),
                durationPolicy = durationPolicy,
                providerId = WEB_CONTENT_PROVIDER_ID,
                model = "gemini-web"
            )
        } else {
            try {
                generateCanonicalContent(
                    config = ContentProviderConfig(
                        providerId = contentConfig.providerId,
                        apiKey = contentConfig.apiKey,
                        model = contentConfig.model,
                        promptTemplate = normalizedRequest.promptTemplate,
                        maximumOutputLength = normalizedRequest.maximumOutputLength
                    ),
                    request = ContentGenerationRequest(
                        providerId = contentConfig.providerId,
                        model = contentConfig.model,
                        topic = normalizedRequest.topic,
                        language = normalizedRequest.language,
                        contentType = normalizedRequest.contentType,
                        promptTemplate = normalizedRequest.promptTemplate,
                        maximumOutputLength = normalizedRequest.maximumOutputLength,
                        durationPolicy = durationPolicy
                    )
                )
            } catch (error: ContentProviderException) {
                if (error.code == ContentProviderErrorCode.AUTHENTICATION) {
                    credentialStore.markGeminiInvalid(error.message)
                }
                throw error
            }
        }

        artifactStore.saveGeneratedTextArtifact(
            jobId = jobId,
            stepId = requireStepId(steps, "CONTENT"),
            text = contentResult.generatedText,
            providerId = contentResult.providerId,
            model = contentResult.model
        )?.let(artifacts::add)

        stepOverrides["CONTENT"] = StepOverride(
            status = AutomationStepStatus.COMPLETED.name,
            waitingReason = "REAL_CONTENT_READY"
        )
        reportProgress(jobId, normalizedRequest.topic, 2, AutomationJobStatus.RUNNING.name, "Gemini đã tạo xong nội dung, đang chia cảnh.")

        val expectedSceneCount = normalizedRequest.requestedSceneCount?.takeIf { it > 0 }
            ?: inferRequestedSceneCount(
                topic = normalizedRequest.topic,
                generatedScript = contentResult.generatedText
            )

        scenePrompts += scenePromptGenerator.generateScenePrompts(
            ScenePromptGenerationRequest(
                topic = normalizedRequest.topic,
                generatedScript = contentResult.generatedText,
                language = normalizedRequest.language,
                visualStyle = DEFAULT_VISUAL_STYLE,
                targetAspectRatio = normalizedRequest.aspectRatio,
                requestedSceneCount = expectedSceneCount,
                structuredScript = contentResult.structuredScript
            )
        )

        stepOverrides["SCENE_PROMPTS"] = StepOverride(
            status = AutomationStepStatus.COMPLETED.name,
            waitingReason = "SCENE_PROMPTS_READY"
        )
        reportProgress(jobId, normalizedRequest.topic, 3, AutomationJobStatus.RUNNING.name, "Đã chia xong ${scenePrompts.size} cảnh, đang lập kế hoạch tài nguyên.")

        assetPlans += visualAssetPlanner.planAssets(
            VisualAssetPlanRequest(
                topic = normalizedRequest.topic,
                contentType = normalizedRequest.contentType,
                scenePrompts = scenePrompts,
                selectedProviderId = selectedImageDefinition?.providerId,
                selectedProviderCostType = selectedImageDefinition?.costType?.name,
                targetAspectRatio = normalizedRequest.aspectRatio
            )
        )

        artifactStore.saveGeneratedTextArtifact(
            jobId = jobId,
            stepId = requireStepId(steps, "ASSET_PLAN"),
            text = VisualAssetPlanJson.encode(assetPlans),
            providerId = "local-asset-planner",
            model = "heuristic-v1"
        )?.let(artifacts::add)

        stepOverrides["ASSET_PLAN"] = StepOverride(
            status = AutomationStepStatus.COMPLETED.name,
            waitingReason = "ASSET_PLAN_READY"
        )
        reportProgress(jobId, normalizedRequest.topic, 4, AutomationJobStatus.RUNNING.name, "Đã có kế hoạch tài nguyên, đang tạo hoặc lấy ảnh.")

        val imageOutcome = executeImageStage(
            jobId = jobId,
            stepId = requireStepId(steps, "IMAGES_VISUALS"),
            scenePrompts = scenePrompts,
            assetPlans = assetPlans,
            providerId = selectedImageProviderId
        )

        stepOverrides["IMAGES_VISUALS"] = StepOverride(
            status = imageOutcome.stepStatus,
            waitingReason = imageOutcome.waitingReason
        )
        artifacts += imageOutcome.artifacts
        reportProgress(
            jobId,
            normalizedRequest.topic,
            5,
            if (imageOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) AutomationJobStatus.RUNNING.name else imageOutcome.jobStatus,
            imageOutcome.runtimeMessage
        )

        val voiceOutcome = if (imageOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
            executeVoiceStage(
                jobId = jobId,
                stepId = requireStepId(steps, "VOICE"),
                scenePrompts = scenePrompts,
                durationPolicy = durationPolicy,
                providerId = null
            )
        } else {
            VoiceStageOutcome(
                providerId = null,
                voiceId = null,
                locale = null,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = imageOutcome.jobStatus,
                waitingReason = "VOICE_WAITING_FOR_IMAGE",
                runtimeMessage = imageOutcome.runtimeMessage
            )
        }

        stepOverrides["VOICE"] = StepOverride(
            status = voiceOutcome.stepStatus,
            waitingReason = voiceOutcome.waitingReason
        )
        artifacts += voiceOutcome.artifacts
        reportProgress(
            jobId,
            normalizedRequest.topic,
            6,
            if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) AutomationJobStatus.RUNNING.name else voiceOutcome.jobStatus,
            voiceOutcome.runtimeMessage
        )

        // Nhoi thoi luong THAT do duoc tu buoc voice (tung canh) vao scenePrompts
        // de renderer dat anh dung theo loi doc. Neu buoc voice khong tra ve
        // (fallback/khong tach canh) thi giu nguyen uoc luong cu.
        val syncedScenePrompts = applyRealSceneDurations(scenePrompts, voiceOutcome.sceneDurationsMs)

        val videoOutcome = if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
            executeVideoStage(
                jobId = jobId,
                stepId = requireStepId(steps, "VIDEO"),
                generatedText = contentResult.generatedText,
                scenePrompts = syncedScenePrompts,
                assetPlans = assetPlans,
                artifacts = artifacts,
                videoRendererMode = normalizedRequest.videoRendererMode,
                videoWorkerUrl = normalizedRequest.videoWorkerUrl,
                videoQualityTier = normalizedRequest.videoQualityTier,
                videoBackgroundMode = normalizedRequest.videoBackgroundMode,
                videoMotionMode = normalizedRequest.videoMotionMode,
                backgroundMusicFilePath = normalizedRequest.backgroundMusicFilePath,
                backgroundMusicLoop = normalizedRequest.backgroundMusicLoop,
                backgroundMusicVolume = normalizedRequest.backgroundMusicVolume,
                videoSubtitleColor = normalizedRequest.videoSubtitleColor,
                videoTitle = deriveVideoTitle(normalizedRequest.topic)
            )
        } else {
            VideoStageOutcome(
                rendererId = null,
                plan = null,
                artifacts = emptyList(),
                subtitleStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                subtitleWaitingReason = "SUBTITLE_WAITING_FOR_VOICE",
                videoStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                videoWaitingReason = "VIDEO_WAITING_FOR_VOICE",
                jobStatus = voiceOutcome.jobStatus,
                runtimeMessage = voiceOutcome.runtimeMessage
            )
        }

        stepOverrides["SUBTITLE"] = StepOverride(
            status = videoOutcome.subtitleStepStatus,
            waitingReason = videoOutcome.subtitleWaitingReason
        )
        stepOverrides["VIDEO"] = StepOverride(
            status = videoOutcome.videoStepStatus,
            waitingReason = videoOutcome.videoWaitingReason
        )
        artifacts += videoOutcome.artifacts
        reportProgress(
            jobId,
            normalizedRequest.topic,
            8,
            if (videoOutcome.videoStepStatus == AutomationStepStatus.COMPLETED.name) AutomationJobStatus.RUNNING.name else videoOutcome.jobStatus,
            videoOutcome.runtimeMessage
        )
        val metadataOutcome = executeMetadataStage(
            jobId = jobId,
            stepId = requireStepId(steps, "METADATA"),
            topic = normalizedRequest.topic,
            generatedText = contentResult.generatedText,
            scenePrompts = scenePrompts,
            assetPlans = assetPlans,
            artifacts = artifacts
        )
        stepOverrides["METADATA"] = StepOverride(
            status = metadataOutcome.stepStatus,
            waitingReason = metadataOutcome.waitingReason
        )
        artifacts += metadataOutcome.artifacts
        reportProgress(jobId, normalizedRequest.topic, 9, AutomationJobStatus.RUNNING.name, "Đã tạo metadata, đang chuẩn bị kiểm duyệt.")

        val reviewOutcome = executeReviewStage(
            jobId = jobId,
            stepId = requireStepId(steps, "REVIEW"),
            metadataPlan = metadataOutcome.plan,
            scenePrompts = scenePrompts,
            artifacts = artifacts
        )
        stepOverrides["REVIEW"] = StepOverride(
            status = reviewOutcome.stepStatus,
            waitingReason = reviewOutcome.waitingReason
        )
        artifacts += reviewOutcome.artifacts
        reportProgress(jobId, normalizedRequest.topic, 10, AutomationJobStatus.RUNNING.name, "Đã chuẩn bị dữ liệu kiểm duyệt và kế hoạch đăng.")

        stepOverrides["PUBLISH"] = StepOverride(
            status = AutomationStepStatus.WAITING_USER.name,
            waitingReason = if (reviewOutcome.state != null) {
                "PUBLISH_WAITING_FOR_REVIEW_APPROVAL"
            } else {
                "PUBLISH_WAITING_FOR_VIDEO_MP4"
            }
        )

        val contentUsageMetadata = linkedMapOf<String, Long>().apply {
            putAll(contentResult.usageMetadata)
            put("maximumOutputLength", normalizedRequest.maximumOutputLength.toLong())
            put("expectedSceneCount", expectedSceneCount.toLong())
            put("parsedSceneCount", scenePrompts.size.toLong())
            put("requestedSceneCount", (normalizedRequest.requestedSceneCount ?: expectedSceneCount).toLong())
            put("desiredDurationSeconds", (normalizedRequest.desiredDurationSeconds ?: (durationPolicy.targetDurationMs / 1000L)).toLong())
        }

        runtimeJobs[jobId] = RuntimeAutomationJob(
            status = videoOutcome.jobStatus,
            generatedText = contentResult.generatedText,
            contentProviderId = contentResult.providerId,
            contentModel = contentResult.model,
            requestId = contentResult.requestId,
            usageMetadata = contentUsageMetadata,
            scenePrompts = syncedScenePrompts,
            assetPlans = assetPlans,
            videoRenderPlan = videoOutcome.plan,
            metadataPlan = metadataOutcome.plan,
            reviewState = reviewOutcome.state,
            publishPlan = null,
            artifacts = artifacts,
            runtimeMessage = videoOutcome.runtimeMessage,
            stepOverrides = stepOverrides,
            imageProviderId = imageOutcome.providerId,
            imageModel = imageOutcome.model,
            imageAttemptHistory = imageOutcome.providerId?.let(::listOf).orEmpty(),
            voiceProviderId = voiceOutcome.providerId,
            voiceId = voiceOutcome.voiceId,
            voiceLocale = voiceOutcome.locale,
            voiceAttemptHistory = voiceOutcome.providerId?.let(::listOf).orEmpty(),
            videoRendererId = videoOutcome.rendererId,
            videoAttemptHistory = videoOutcome.rendererId?.let(::listOf).orEmpty(),
            videoRendererMode = normalizedRequest.videoRendererMode,
            videoWorkerUrl = normalizedRequest.videoWorkerUrl,
            videoQualityTier = normalizedRequest.videoQualityTier,
            videoBackgroundMode = normalizedRequest.videoBackgroundMode,
            videoMotionMode = normalizedRequest.videoMotionMode,
            backgroundMusicFilePath = normalizedRequest.backgroundMusicFilePath,
            backgroundMusicLoop = normalizedRequest.backgroundMusicLoop,
            backgroundMusicVolume = normalizedRequest.backgroundMusicVolume,
            videoSubtitleColor = normalizedRequest.videoSubtitleColor
        )
        // Vua tao xong tu du lieu hien tai -> dong dau chu ky "moi" cho anh/giong/video.
        runtimeJobs[jobId]?.let { runtimeJobs[jobId] = stampFreshSignatures(it) }
        persistRuntimeJob(jobId)

        val finalRuntime = runtimeJobs[jobId]
        reportProgress(
            jobId = jobId,
            topic = normalizedRequest.topic,
            completedSteps = if (finalRuntime?.status == AutomationJobStatus.COMPLETED.name) 11 else 10,
            state = finalRuntime?.status ?: AutomationJobStatus.WAITING_USER.name,
            message = finalRuntime?.runtimeMessage ?: "Pipeline đã hoàn tất phần chạy tự động."
        )
        runtimeProgressClientIds.remove(jobId)
        return persistedSnapshot.toUiSnapshot(
            topicFallback = normalizedRequest.topic,
            runtimeJob = finalRuntime
        )
    }

    suspend fun retryImageStep(
        jobId: String,
        providerId: String? = null
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Job hien tai chua co runtime content de retry image.")
        require(runtimeJob.scenePrompts.isNotEmpty()) {
            "Job nay chua co scene prompts de retry image."
        }

        val imageStepId = persisted.steps.firstOrNull { it.stepType == "IMAGES_VISUALS" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc IMAGE trong job.")

        // THU LAI = chi lay anh cho cac canh CON THIEU (chua co IMAGE artifact) -
        // KHONG dung/xoa anh cua canh da co. Anh gan theo sceneId nen khong lech.
        val existingImageSceneIds = runtimeJob.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .mapNotNull { it.sceneId }.toSet()
        val missingScenes = runtimeJob.scenePrompts
            .sortedBy { it.ordinal }
            .filter { it.sceneId !in existingImageSceneIds }

        val resolvedProviderId = resolveRequestedProviderId(providerId)
        val imageOutcome = executeImageStage(
            jobId = jobId,
            stepId = imageStepId,
            // Chi truyen canh con thieu -> provider API chi fetch nhung canh do; web-scrape
            // van tra WAITING de JS cao tiep cac canh con thieu.
            scenePrompts = missingScenes.ifEmpty { runtimeJob.scenePrompts },
            assetPlans = runtimeJob.assetPlans,
            providerId = resolvedProviderId
        )

        // Giu nguyen anh cu (theo sceneId), chi bo VIDEO de render lai; them anh moi cua
        // cac canh con thieu (khong trung vi chi fetch canh thieu).
        val newImageSceneIds = imageOutcome.artifacts.mapNotNull { it.sceneId }.toSet()
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot {
                it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" ||
                    (it.artifactType == "IMAGE" && it.sceneId in newImageSceneIds)
            }
            .toMutableList()
            .apply { addAll(imageOutcome.artifacts) }

        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["IMAGES_VISUALS"] = StepOverride(
                status = imageOutcome.stepStatus,
                waitingReason = imageOutcome.waitingReason
            )
            this["SUBTITLE"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = if (imageOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    "SUBTITLE_WAITING_FOR_VIDEO_RETRY"
                } else {
                    "SUBTITLE_WAITING_FOR_IMAGE"
                }
            )
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = if (imageOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    "VIDEO_WAITING_FOR_RETRY"
                } else {
                    "VIDEO_WAITING_FOR_IMAGE"
                }
            )
            this["REVIEW"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "REVIEW_WAITING_FOR_VIDEO"
            )
            this["METADATA"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "METADATA_WAITING_FOR_VIDEO_MP4"
            )
            this["PUBLISH"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "PUBLISH_WAITING_FOR_VIDEO_MP4"
            )
        }

        val updatedRuntime = runtimeJob.copy(
            status = imageOutcome.jobStatus,
            videoRenderPlan = null,
            metadataPlan = null,
            reviewState = null,
            publishPlan = null,
            artifacts = updatedArtifacts,
            runtimeMessage = imageOutcome.runtimeMessage,
            stepOverrides = updatedStepOverrides,
            imageProviderId = imageOutcome.providerId,
            imageModel = imageOutcome.model,
            imageAttemptHistory = runtimeJob.imageAttemptHistory + listOfNotNull(imageOutcome.providerId),
            videoRendererId = null
        ).let { stampImageSignatures(it, newImageSceneIds) }
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    /**
     * Danh sach cau tim kiem anh cho tung canh cua job (dung thu tu ordinal), dung
     * cho luong cao anh tu web (Google Images/Pinterest) - JS goi bridge de lay
     * danh sach nay, mo WebView cao tung cau, roi nhap anh lai qua importImageArtifacts().
     */
    fun getSceneImageSearchQueries(jobId: String): List<String> {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de lay cau tim anh.")
        require(runtimeJob.scenePrompts.isNotEmpty()) {
            "Job nay chua co scene prompts de tim anh."
        }
        val assetPlanBySceneId = runtimeJob.assetPlans.associateBy { it.sceneId }
        return runtimeJob.scenePrompts
            .sortedBy { it.ordinal }
            .map { scene -> resolveImagePrompt(scene, assetPlanBySceneId[scene.sceneId]) }
    }

    /**
     * Cac canh CHUA co anh (resume): tra ve (chiSoGoc, query) - chiSoGoc = vi tri canh
     * trong danh sach da sap theo ordinal (khop importImageArtifactsByIndex). Dung de
     * chi cao them anh cho canh con thieu khi app bi ngat giua chung.
     */
    fun getMissingSceneImageQueries(jobId: String): List<Pair<Int, String>> {
        val runtimeJob = runtimeJobs[jobId] ?: return emptyList()
        if (runtimeJob.scenePrompts.isEmpty()) return emptyList()
        val scenes = runtimeJob.scenePrompts.sortedBy { it.ordinal }
        val sceneIdsWithImage = runtimeJob.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .map { it.sceneId }
            .toSet()
        val assetPlanBySceneId = runtimeJob.assetPlans.associateBy { it.sceneId }
        return scenes.mapIndexedNotNull { index, scene ->
            if (scene.sceneId in sceneIdsWithImage) null
            else index to resolveImagePrompt(scene, assetPlanBySceneId[scene.sceneId])
        }
    }

    /**
     * Ghi nhan buoc cao anh tu web that bai vao CHINH job (khong chi bao loi tam
     * thoi qua AutomationAsyncTaskStore roi bien mat) - neu khong, lan sau doc lai
     * job van thay IMAGES_VISUALS o trang thai WAITING_WEB_IMAGE_SCRAPE y het truoc
     * do, tao cam giac "quay lai cho nguoi dung" du thuc ra vua that bai that.
     */
    fun markWebImageScrapeFailed(jobId: String, message: String) {
        val runtimeJob = runtimeJobs[jobId] ?: return
        val updatedSteps = runtimeJob.stepOverrides.toMutableMap().apply {
            this["IMAGES_VISUALS"] = StepOverride(
                status = AutomationStepStatus.FAILED.name,
                waitingReason = "WEB_IMAGE_SCRAPE_FAILED"
            )
        }
        putRuntimeJob(jobId, runtimeJob.copy(
            status = AutomationJobStatus.FAILED.name,
            runtimeMessage = message,
            stepOverrides = updatedSteps
        ))
    }

    suspend fun retryVoiceStep(
        jobId: String,
        providerId: String? = null
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Job hien tai chua co runtime content de retry voice.")
        require(runtimeJob.generatedText.isNotBlank()) {
            "Job nay chua co generated text de retry voice."
        }
        require(runtimeJob.artifacts.any { it.artifactType == "IMAGE" }) {
            "Can co image artifact that truoc khi retry voice."
        }

        val voiceStepId = persisted.steps.firstOrNull { it.stepType == "VOICE" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc VOICE trong job.")

        val voiceOutcome = executeVoiceStage(
            jobId = jobId,
            stepId = voiceStepId,
            scenePrompts = runtimeJob.scenePrompts,
            durationPolicy = ContentDurationPolicy.fromTopic(
                topic = persisted.resolveTopicFromOutbox().ifBlank { runtimeJob.generatedText },
                desiredDurationSeconds = runtimeJob.usageMetadata["desiredDurationSeconds"]?.toInt()
            ),
            providerId = providerId
        )

        val syncedScenePrompts = applyRealSceneDurations(runtimeJob.scenePrompts, voiceOutcome.sceneDurationsMs)

        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "VOICE" || it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" }
            .toMutableList()
            .apply { addAll(voiceOutcome.artifacts) }

        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["VOICE"] = StepOverride(
                status = voiceOutcome.stepStatus,
                waitingReason = voiceOutcome.waitingReason
            )
            this["SUBTITLE"] = StepOverride(
                status = if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    AutomationStepStatus.NOT_CONFIGURED.name
                } else {
                    voiceOutcome.stepStatus
                },
                waitingReason = if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    "SUBTITLE_WAITING_FOR_VIDEO_RETRY"
                } else {
                    "SUBTITLE_WAITING_FOR_VOICE"
                }
            )
            this["VIDEO"] = StepOverride(
                status = if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    AutomationStepStatus.NOT_CONFIGURED.name
                } else {
                    voiceOutcome.stepStatus
                },
                waitingReason = if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) {
                    "VIDEO_WAITING_FOR_RETRY"
                } else {
                    "VIDEO_WAITING_FOR_VOICE"
                }
            )
            this["REVIEW"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "REVIEW_WAITING_FOR_VIDEO"
            )
            this["METADATA"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "METADATA_WAITING_FOR_VIDEO_MP4"
            )
            this["PUBLISH"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "PUBLISH_WAITING_FOR_VIDEO_MP4"
            )
        }

        val updatedRuntime = runtimeJob.copy(
            status = voiceOutcome.jobStatus,
            videoRenderPlan = null,
            metadataPlan = null,
            reviewState = null,
            publishPlan = null,
            artifacts = updatedArtifacts,
            runtimeMessage = voiceOutcome.runtimeMessage,
            stepOverrides = updatedStepOverrides,
            scenePrompts = syncedScenePrompts,
            voiceProviderId = voiceOutcome.providerId,
            voiceId = voiceOutcome.voiceId,
            voiceLocale = voiceOutcome.locale,
            voiceAttemptHistory = runtimeJob.voiceAttemptHistory + listOfNotNull(voiceOutcome.providerId),
            videoRendererId = null
        ).let { if (voiceOutcome.stepStatus == AutomationStepStatus.COMPLETED.name) stampFreshSignatures(it) else it }
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun retryVideoStep(
        jobId: String,
        videoRendererMode: String? = null,
        videoWorkerUrl: String? = null,
        videoQualityTier: String? = null,
        videoBackgroundMode: String? = null,
        videoMotionMode: String? = null,
        backgroundMusicFilePath: String? = null,
        backgroundMusicLoop: Boolean? = null,
        backgroundMusicVolume: Float? = null,
        videoSubtitleColor: String? = null
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Job hien tai chua co runtime content de retry video.")
        require(runtimeJob.generatedText.isNotBlank()) {
            "Job nay chua co generated text de retry video."
        }
        require(runtimeJob.scenePrompts.isNotEmpty()) {
            "Job nay chua co scene prompts de retry video."
        }
        require(runtimeJob.assetPlans.isNotEmpty()) {
            "Job nay chua co asset plan de retry video."
        }

        val videoStepId = persisted.steps.firstOrNull { it.stepType == "VIDEO" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc VIDEO trong job.")

        val videoOutcome = executeVideoStage(
            jobId = jobId,
            stepId = videoStepId,
            generatedText = runtimeJob.generatedText,
            scenePrompts = runtimeJob.scenePrompts,
            assetPlans = runtimeJob.assetPlans,
            artifacts = runtimeJob.artifacts,
            videoRendererMode = normalizeVideoRendererMode(videoRendererMode ?: runtimeJob.videoRendererMode),
            videoWorkerUrl = videoWorkerUrl ?: runtimeJob.videoWorkerUrl,
            videoQualityTier = normalizeVideoQualityTier(videoQualityTier ?: runtimeJob.videoQualityTier),
            videoBackgroundMode = normalizeVideoBackgroundMode(videoBackgroundMode ?: runtimeJob.videoBackgroundMode),
            videoMotionMode = normalizeVideoMotionMode(videoMotionMode ?: runtimeJob.videoMotionMode),
            backgroundMusicFilePath = backgroundMusicFilePath ?: runtimeJob.backgroundMusicFilePath,
            backgroundMusicLoop = backgroundMusicLoop ?: runtimeJob.backgroundMusicLoop,
            backgroundMusicVolume = (backgroundMusicVolume ?: runtimeJob.backgroundMusicVolume).coerceIn(0f, 1f),
            videoSubtitleColor = normalizeVideoSubtitleColor(videoSubtitleColor ?: runtimeJob.videoSubtitleColor),
            videoTitle = deriveVideoTitle(persisted.resolveTopicFromOutbox())
        )

        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" }
            .toMutableList()
            .apply { addAll(videoOutcome.artifacts) }

        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["SUBTITLE"] = StepOverride(
                status = videoOutcome.subtitleStepStatus,
                waitingReason = videoOutcome.subtitleWaitingReason
            )
            this["VIDEO"] = StepOverride(
                status = videoOutcome.videoStepStatus,
                waitingReason = videoOutcome.videoWaitingReason
            )
            this["REVIEW"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = if (videoOutcome.videoStepStatus == AutomationStepStatus.COMPLETED.name) {
                    if (videoOutcome.videoWaitingReason == "VIDEO_MP4_READY") {
                        "REVIEW_WAITING_FOR_APPROVAL"
                    } else {
                        "REVIEW_WAITING_FOR_VIDEO_RENDER_PLAN"
                    }
                } else {
                    "REVIEW_WAITING_FOR_VIDEO"
                }
            )
            this["METADATA"] = StepOverride(
                status = if (videoOutcome.videoWaitingReason == "VIDEO_MP4_READY") AutomationStepStatus.COMPLETED.name else AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = if (videoOutcome.videoWaitingReason == "VIDEO_MP4_READY") "METADATA_PLAN_READY" else "METADATA_WAITING_FOR_VIDEO_MP4"
            )
            this["PUBLISH"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = if (videoOutcome.videoWaitingReason == "VIDEO_MP4_READY") "PUBLISH_WAITING_FOR_REVIEW_APPROVAL" else "PUBLISH_WAITING_FOR_VIDEO_MP4"
            )
        }

        val metadataOutcome = executeMetadataStage(
            jobId = jobId,
            stepId = requireStepId(persisted.steps, "METADATA"),
            topic = persisted.resolveTopicFromOutbox().ifBlank { runtimeJob.generatedText },
            generatedText = runtimeJob.generatedText,
            scenePrompts = runtimeJob.scenePrompts,
            assetPlans = runtimeJob.assetPlans,
            artifacts = updatedArtifacts
        )
        val postMetadataArtifacts = updatedArtifacts
            .filterNot { it.artifactType == "METADATA_PLAN" || it.artifactType == "REVIEW_STATE" || it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
            .apply { addAll(metadataOutcome.artifacts) }
        val reviewOutcome = executeReviewStage(
            jobId = jobId,
            stepId = requireStepId(persisted.steps, "REVIEW"),
            metadataPlan = metadataOutcome.plan,
            scenePrompts = runtimeJob.scenePrompts,
            artifacts = postMetadataArtifacts
        )
        postMetadataArtifacts += reviewOutcome.artifacts
        updatedStepOverrides["METADATA"] = StepOverride(metadataOutcome.stepStatus, metadataOutcome.waitingReason)
        updatedStepOverrides["REVIEW"] = StepOverride(
            reviewOutcome.stepStatus,
            if (metadataOutcome.plan != null) "REVIEW_WAITING_FOR_APPROVAL" else "REVIEW_WAITING_FOR_METADATA"
        )

        val updatedRuntime = runtimeJob.copy(
            status = videoOutcome.jobStatus,
            videoRenderPlan = videoOutcome.plan,
            metadataPlan = metadataOutcome.plan,
            reviewState = reviewOutcome.state,
            publishPlan = null,
            artifacts = postMetadataArtifacts,
            runtimeMessage = videoOutcome.runtimeMessage,
            stepOverrides = updatedStepOverrides,
            videoRendererId = videoOutcome.rendererId,
            videoAttemptHistory = runtimeJob.videoAttemptHistory + listOfNotNull(videoOutcome.rendererId),
            videoRendererMode = normalizeVideoRendererMode(videoRendererMode ?: runtimeJob.videoRendererMode),
            videoWorkerUrl = videoWorkerUrl ?: runtimeJob.videoWorkerUrl,
            videoQualityTier = normalizeVideoQualityTier(videoQualityTier ?: runtimeJob.videoQualityTier),
            videoBackgroundMode = normalizeVideoBackgroundMode(videoBackgroundMode ?: runtimeJob.videoBackgroundMode),
            videoMotionMode = normalizeVideoMotionMode(videoMotionMode ?: runtimeJob.videoMotionMode),
            backgroundMusicFilePath = backgroundMusicFilePath ?: runtimeJob.backgroundMusicFilePath,
            backgroundMusicLoop = backgroundMusicLoop ?: runtimeJob.backgroundMusicLoop,
            backgroundMusicVolume = (backgroundMusicVolume ?: runtimeJob.backgroundMusicVolume).coerceIn(0f, 1f),
            videoSubtitleColor = normalizeVideoSubtitleColor(videoSubtitleColor ?: runtimeJob.videoSubtitleColor)
        ).let { if (videoOutcome.videoStepStatus == AutomationStepStatus.COMPLETED.name) stampFreshSignatures(it) else it }
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun testVideoRenderWorker(
        workerUrl: String
    ): AutomationConnectionTestResult {
        val health = videoRenderWorkerClient.testWorker(workerUrl)
        return AutomationConnectionTestResult(
            state = "CONNECTED",
            providerId = health.renderer,
            model = health.version,
            message = "Video render worker da san sang."
        )
    }

    suspend fun getAutomationJob(
        jobId: String
    ): AutomationUiJobSnapshot? {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId) ?: return null
        return persisted.toUiSnapshot(runtimeJob = runtimeJobs[jobId])
    }

    /**
     * Xoa hoan toan 1 phien: DB rows (job/step/artifact/connector binding/outbox) va
     * cac file that (anh/giong doc/video MP4) tren app-private storage. Truoc day JS
     * "xoa phien" chi xoa khoi danh sach hien thi (localStorage), du lieu that van
     * con vinh vien tren may.
     */
    suspend fun deleteAutomationJob(jobId: String) {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val artifactUris = repository.deleteJobGraph(jobId)
        artifactUris.forEach { uri ->
            runCatching { artifactStore.deleteArtifactByUri(uri) }
        }
        runtimeJobs.remove(jobId)
        runCatching { runtimeJobStore?.delete(jobId) }
        runtimeProgressClientIds.remove(jobId)
    }

    suspend fun exportVideoMp4ToDownloads(
        jobId: String
    ): AutomationVideoExportResult {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de export video.")
        val videoArtifact = runtimeJob.artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" }
            ?: throw IllegalArgumentException("Job nay chua co VIDEO_MP4 de export.")
        val exported = artifactStore.exportVideoArtifactToDownloads(videoArtifact, jobId)
            ?: throw IllegalStateException("Artifact store khong ho tro export VIDEO_MP4.")
        return exported.toUiVideoExportResult()
    }

    /**
     * Duong dan file MP4 app-private cua job de XEM TRUOC ngay trong app (VideoView),
     * khong can export ra Downloads. Null neu job chua co VIDEO_MP4.
     */
    fun resolvePreviewableVideoPath(jobId: String): String? {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val runtimeJob = runtimeJobs[jobId] ?: return null
        val videoArtifact = runtimeJob.artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" } ?: return null
        return artifactStore.resolveArtifactAbsolutePath(videoArtifact)
    }

    /**
     * Sua tay l-oi doc + tieu de cua 1 canh (runtimeJob.scenePrompts). Sau khi sua,
     * nguoi dung bam "Chay lai (giu noi dung & anh)" de tao lai giong + video. Tra
     * ve false neu khong tim thay job/canh.
     */
    fun updateSceneText(
        jobId: String,
        sceneId: String,
        voiceText: String?,
        onScreenText: String?,
        stockSearchQuery: String?
    ): Boolean {
        val runtimeJob = runtimeJobs[jobId] ?: return false
        if (runtimeJob.scenePrompts.none { it.sceneId == sceneId }) return false
        val updated = runtimeJob.scenePrompts.map { scene ->
            if (scene.sceneId != sceneId) scene
            else scene.copy(
                voiceText = voiceText?.trim()?.ifBlank { null } ?: scene.voiceText,
                onScreenText = onScreenText?.trim() ?: scene.onScreenText,
                stockSearchQuery = stockSearchQuery?.trim()?.ifBlank { null } ?: scene.stockSearchQuery
            )
        }
        putRuntimeJob(jobId, runtimeJob.copy(scenePrompts = updated))
        return true
    }

    /**
     * Timeline editor: dat THOI LUONG (ms) cho 1 canh - keo mep clip trong timeline.
     * Ghi vao plannedDurationMs; buoc render/xuat video se dung do dai nay. Kep toi
     * thieu 800ms. Danh dau video can render lai. Tra ve false neu khong tim thay.
     */
    fun setSceneDuration(jobId: String, sceneId: String, durationMs: Long): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        if (rj.scenePrompts.none { it.sceneId == sceneId }) return false
        val clamped = durationMs.coerceIn(800L, 120_000L)
        val updated = rj.scenePrompts.map { s ->
            if (s.sceneId == sceneId) s.copy(plannedDurationMs = clamped) else s
        }
        val steps = rj.stepOverrides.toMutableMap().apply {
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "VIDEO_WAITING_FOR_RENDER_RETRY"
            )
        }
        putRuntimeJob(jobId, rj.copy(
            scenePrompts = updated,
            stepOverrides = steps,
            videoRenderPlan = null
        ))
        return true
    }

    /**
     * Timeline: TACH 1 canh tai vi tri offsetMs (tinh tu dau canh) thanh 2 canh -
     * clip 1 giu do dai offsetMs, clip 2 do dai con lai. Ca 2 dung CHUNG anh (nhan
     * ban artifact IMAGE tro cung file), cung tieu de/loi doc. Danh lai ordinal.
     * Dung cho nut "Tach" tai vach playhead giua man hinh.
     */
    fun splitScene(jobId: String, sceneId: String, offsetMs: Long): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        val scene = rj.scenePrompts.firstOrNull { it.sceneId == sceneId } ?: return false
        val dur = scene.plannedDurationMs.takeIf { it > 0L } ?: 4_000L
        if (offsetMs < 400L || offsetMs > dur - 400L) return false
        val newSceneId = "scene-" + java.util.UUID.randomUUID().toString().substring(0, 8)
        val first = scene.copy(plannedDurationMs = offsetMs)
        val second = scene.copy(sceneId = newSceneId, plannedDurationMs = dur - offsetMs)
        // Chen second ngay sau first, danh lai ordinal toan bo.
        val ordered = rj.scenePrompts.sortedBy { it.ordinal }.toMutableList()
        val idx = ordered.indexOfFirst { it.sceneId == sceneId }
        ordered[idx] = first
        ordered.add(idx + 1, second)
        val renumbered = ordered.mapIndexed { i, s -> s.copy(ordinal = i + 1) }
        // Nhan ban asset plan.
        val plans = rj.assetPlans.toMutableList()
        rj.assetPlans.firstOrNull { it.sceneId == sceneId }?.let { p ->
            plans.add(p.copy(sceneId = newSceneId))
        }
        val renumberedPlans = renumbered.mapNotNull { s ->
            plans.firstOrNull { it.sceneId == s.sceneId }?.copy(ordinal = s.ordinal)
        }
        // Nhan ban anh: artifact moi tro cung file (cung uri) nhung sceneId moi.
        val artifacts = rj.artifacts.toMutableList()
        rj.artifacts.firstOrNull { it.artifactType == "IMAGE" && it.sceneId == sceneId }?.let { img ->
            artifacts.add(img.copy(
                artifactId = "artifact-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                sceneId = newSceneId
            ))
        }
        val cleaned = artifacts.filterNot { it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" }
        val steps = rj.stepOverrides.toMutableMap().apply {
            this["VIDEO"] = StepOverride(AutomationStepStatus.NOT_CONFIGURED.name, "VIDEO_WAITING_FOR_RENDER_RETRY")
        }
        putRuntimeJob(jobId, rj.copy(
            scenePrompts = renumbered,
            assetPlans = renumberedPlans,
            artifacts = cleaned,
            stepOverrides = steps,
            videoRenderPlan = null
        ))
        return true
    }

    /**
     * Them 1 canh moi (nguoi dung tu them) vao cuoi danh sach - kem asset plan de
     * buoc anh/video hoat dong. Sau khi them, nguoi dung cao anh cho canh moi roi
     * "Chay lai". Tra ve false neu khong tim thay job.
     */
    fun addScene(jobId: String, voiceText: String, onScreenText: String, stockSearchQuery: String): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        val aspect = rj.scenePrompts.firstOrNull()?.aspectRatio ?: DEFAULT_TARGET_ASPECT_RATIO
        val newSceneId = "scene-" + java.util.UUID.randomUUID().toString().substring(0, 8)
        val newOrdinal = (rj.scenePrompts.maxOfOrNull { it.ordinal } ?: 0) + 1
        val vt = voiceText.trim()
        val ost = onScreenText.trim()
        val q = stockSearchQuery.trim().ifBlank { ost.ifBlank { vt.take(60) } }.ifBlank { "video" }
        val scene = ScenePrompt(
            sceneId = newSceneId,
            ordinal = newOrdinal,
            summary = ost.ifBlank { vt.take(60) },
            visualPrompt = q,
            negativePrompt = null,
            aspectRatio = aspect,
            voiceText = vt,
            onScreenText = ost,
            plannedDurationMs = 4_000L,
            stockSearchQuery = q,
            visualDirection = vt
        )
        val template = rj.assetPlans.firstOrNull()
        val plan = VisualAssetPlan(
            sceneId = newSceneId,
            ordinal = newOrdinal,
            strategy = template?.strategy ?: "stock_search",
            preferredProviderId = null,
            assetQuery = q,
            templateId = template?.templateId ?: "default",
            renderMode = template?.renderMode ?: "image",
            durationMs = 4_000L,
            rationale = "Canh nguoi dung them tay"
        )
        putRuntimeJob(jobId, rj.copy(scenePrompts = rj.scenePrompts + scene, assetPlans = rj.assetPlans + plan))
        return true
    }

    /**
     * Xoa 1 canh (scenePrompt + asset plan + anh cua canh do), danh lai so thu tu
     * (ordinal) cho cac canh con lai. Anh gan theo sceneId nen khong lech.
     */
    fun deleteScene(jobId: String, sceneId: String): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        if (rj.scenePrompts.none { it.sceneId == sceneId }) return false
        val newScenes = rj.scenePrompts.filter { it.sceneId != sceneId }
            .sortedBy { it.ordinal }
            .mapIndexed { i, s -> s.copy(ordinal = i + 1) }
        val newPlans = rj.assetPlans.filter { it.sceneId != sceneId }
            .sortedBy { it.ordinal }
            .mapIndexed { i, p -> p.copy(ordinal = i + 1) }
        val newArtifacts = rj.artifacts.filterNot { it.artifactType == "IMAGE" && it.sceneId == sceneId }
        putRuntimeJob(jobId, rj.copy(scenePrompts = newScenes, assetPlans = newPlans, artifacts = newArtifacts))
        return true
    }

    /**
     * Doi thu tu 1 canh. direction < 0 = len tren, direction > 0 = xuong duoi.
     * Danh lai ordinal cho ca scenePrompts va assetPlans. Anh gan theo sceneId nen
     * khong lech; video se tu dong bi danh dau STALE (do currentVideoSignature phu
     * thuoc thu tu). Tra ve false neu khong the di chuyen (dau/cuoi danh sach).
     */
    fun moveScene(jobId: String, sceneId: String, direction: Int): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        val ordered = rj.scenePrompts.sortedBy { it.ordinal }.toMutableList()
        val idx = ordered.indexOfFirst { it.sceneId == sceneId }
        if (idx < 0) return false
        val target = idx + if (direction < 0) -1 else 1
        if (target < 0 || target >= ordered.size) return false
        val tmp = ordered[idx]; ordered[idx] = ordered[target]; ordered[target] = tmp
        val renumberedScenes = ordered.mapIndexed { i, s -> s.copy(ordinal = i + 1) }
        val planBySceneId = rj.assetPlans.associateBy { it.sceneId }
        val renumberedPlans = renumberedScenes.mapNotNull { s -> planBySceneId[s.sceneId]?.copy(ordinal = s.ordinal) }
        putRuntimeJob(jobId, rj.copy(scenePrompts = renumberedScenes, assetPlans = renumberedPlans))
        return true
    }

    /** Cau tim anh cua DUNG 1 canh (de JS cao lai rieng canh do qua Pinterest/web). */
    fun getSceneImageQuery(jobId: String, sceneId: String): String? {
        val rj = runtimeJobs[jobId] ?: return null
        val scene = rj.scenePrompts.firstOrNull { it.sceneId == sceneId } ?: return null
        val plan = rj.assetPlans.firstOrNull { it.sceneId == sceneId }
        return resolveImagePrompt(scene, plan)
    }

    /**
     * Thay anh cho DUNG 1 canh (cao web 1 anh hoac chon tu may) - giu nguyen anh
     * cac canh khac. Xoa anh cu cua rieng canh nay + video, danh dau canh nay co
     * anh moi (imageSignature) va video can render lai.
     */
    suspend fun replaceSceneImage(
        jobId: String,
        sceneId: String,
        image: ImportedAutomationImage
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val rj = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de thay anh.")
        val scene = rj.scenePrompts.firstOrNull { it.sceneId == sceneId }
            ?: throw IllegalArgumentException("Khong tim thay canh de thay anh.")
        val imageStepId = persisted.steps.firstOrNull { it.stepType == "IMAGES_VISUALS" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc IMAGE trong job.")
        val saved = artifactStore.saveGeneratedImageArtifact(
            jobId = jobId,
            stepId = imageStepId,
            bytes = image.bytes,
            providerId = MANUAL_IMAGE_IMPORT_PROVIDER_ID,
            model = "scene-replace",
            mimeType = image.mimeType,
            sourceUrl = "scene-replace;displayName=${image.displayName.replace(';', ',')}",
            sceneId = sceneId,
            ordinal = scene.ordinal,
            providerRequestId = null
        ) ?: throw IllegalStateException("Khong luu duoc anh thay the cho canh.")
        val updatedArtifacts = rj.artifacts
            .filterNot {
                (it.artifactType == "IMAGE" && it.sceneId == sceneId) ||
                    it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4"
            }
            .toMutableList()
            .apply { add(saved) }
        val updatedSteps = rj.stepOverrides.toMutableMap().apply {
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "VIDEO_WAITING_FOR_RENDER_RETRY"
            )
        }
        val updated = rj.copy(
            status = AutomationJobStatus.WAITING_USER.name,
            artifacts = updatedArtifacts,
            stepOverrides = updatedSteps,
            videoRenderPlan = null,
            metadataPlan = null,
            reviewState = null,
            publishPlan = null,
            runtimeMessage = "Da thay anh cho canh ${scene.ordinal}. Bam \"Cap nhat thay doi\" de render lai video."
        ).let { stampImageSignatures(it, setOf(sceneId)) }
        putRuntimeJob(jobId, updated)
        return persisted.toUiSnapshot(runtimeJob = updated)
    }

    /**
     * Xoa anh cua DUNG 1 canh (danh dau canh do "thieu anh") de luong cao web
     * missing-only tim lai dung canh do theo tu khoa moi - giu nguyen anh cac canh
     * khac. Video bi danh dau can render lai. Tra ve false neu khong tim thay.
     */
    fun clearSceneImage(jobId: String, sceneId: String): Boolean {
        val rj = runtimeJobs[jobId] ?: return false
        if (rj.scenePrompts.none { it.sceneId == sceneId }) return false
        val hadImage = rj.artifacts.any { it.artifactType == "IMAGE" && it.sceneId == sceneId }
        if (!hadImage) return true
        val newArtifacts = rj.artifacts.filterNot {
            (it.artifactType == "IMAGE" && it.sceneId == sceneId) ||
                it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4"
        }
        val newScenes = rj.scenePrompts.map {
            if (it.sceneId == sceneId) it.copy(imageSignature = "") else it
        }
        val steps = rj.stepOverrides.toMutableMap().apply {
            this["IMAGES_VISUALS"] = StepOverride(
                status = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "WAITING_WEB_IMAGE_SCRAPE"
            )
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "VIDEO_WAITING_FOR_RENDER_RETRY"
            )
        }
        putRuntimeJob(jobId, rj.copy(
            scenePrompts = newScenes,
            artifacts = newArtifacts,
            stepOverrides = steps,
            videoRenderPlan = null,
            metadataPlan = null,
            reviewState = null,
            publishPlan = null
        ))
        return true
    }

    /** Duong dan file that (app-private) cua anh 1 canh - de native tai ve may. */
    fun resolveSceneImagePath(jobId: String, sceneId: String): String? {
        val rj = runtimeJobs[jobId] ?: return null
        val art = rj.artifacts.firstOrNull { it.artifactType == "IMAGE" && it.sceneId == sceneId } ?: return null
        return artifactStore.resolveArtifactAbsolutePath(art)
    }

    /**
     * Timeline preview: tra ve giong doc tong duoi dang data URL (base64) de <audio>
     * trong WebView phat kem khi tua timeline. Null neu chua co giong. Chi dung cho
     * xem truoc (file WAV vai MB) - khong dung cho render.
     */
    suspend fun getVoiceDataUrl(jobId: String): String? {
        val rj = runtimeJobs[jobId] ?: return null
        val voice = rj.artifacts.firstOrNull { it.artifactType == "VOICE" } ?: return null
        val bytes = artifactStore.readArtifactBytes(voice) ?: return null
        val mime = voice.mimeType.ifBlank { "audio/wav" }
        return "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    data class YouTubePublishData(
        val videoFilePath: String,
        val title: String,
        val description: String,
        val tags: List<String>
    )

    /** Du lieu de auto-upload YouTube: file MP4 + title/description/tags tu metadataPlan. */
    fun getYouTubePublishData(jobId: String): YouTubePublishData? {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val runtimeJob = runtimeJobs[jobId] ?: return null
        val videoArtifact = runtimeJob.artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" } ?: return null
        val path = artifactStore.resolveArtifactAbsolutePath(videoArtifact) ?: return null
        val meta = runtimeJob.metadataPlan
        val title = meta?.title?.trim()?.ifBlank { null } ?: "Video"
        val description = meta?.description?.trim().orEmpty()
        val tags = meta?.hashtags.orEmpty().map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
        return YouTubePublishData(
            videoFilePath = path,
            title = title,
            description = description,
            tags = tags
        )
    }

    suspend fun listRecentAutomationJobs(
        projectId: String = AUTOMATION_PROJECT_ID,
        limit: Int = DEFAULT_RECENT_LIMIT
    ): List<AutomationUiRecentJob> {
        require(projectId.isNotBlank()) { "Project ID is required." }
        require(limit > 0) { "Limit must be positive." }

        return repository.listRecentJobs(projectId, limit).mapNotNull { job ->
            repository.getJobGraph(job.jobId)?.toRecentJob()
        }
    }

    private suspend fun generateCanonicalContent(
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ): ContentGenerationResult {
        var currentRequest = request
        var lastCheck: ContentSufficiencyCheck? = null

        repeat(MAX_CONTENT_GENERATION_ATTEMPTS) { attemptIndex ->
            val result = contentConnector.generateContent(config, currentRequest)
            val check = evaluateContentSufficiency(result, request.durationPolicy)
            lastCheck = check
            if (check.isSufficient) {
                return result
            }

            if (attemptIndex < MAX_CONTENT_GENERATION_ATTEMPTS - 1) {
                currentRequest = request.copy(
                    maximumOutputLength = maxOf(
                        request.maximumOutputLength,
                        recommendedRepairOutputLength(request.durationPolicy)
                    ).coerceAtMost(MAX_OUTPUT_LENGTH),
                    repairInstruction = buildContentRepairInstruction(check)
                )
            }
        }

        val check = lastCheck
        val durationMessage = if (check != null && check.minimumWordCount > 0) {
            " Loi doc uoc tinh moi dat ${check.estimatedDurationMs / 1000L}/${check.targetDurationMs / 1000L} giay (${check.wordCount}/${check.minimumWordCount} tu)."
        } else {
            ""
        }
        throw ContentProviderException(
            ContentProviderErrorCode.PROVIDER_FAILURE,
            "Gemini chua tao du noi dung theo yeu cau.$durationMessage Hay thu lai SCRIPT."
        )
    }

    suspend fun retryAutomationMetadata(
        jobId: String
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de retry metadata.")
        val metadataStepId = persisted.steps.firstOrNull { it.stepType == "METADATA" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc METADATA.")
        val reviewStepId = persisted.steps.firstOrNull { it.stepType == "REVIEW" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc REVIEW.")
        val metadataOutcome = executeMetadataStage(
            jobId = jobId,
            stepId = metadataStepId,
            topic = persisted.resolveTopicFromOutbox().ifBlank { runtimeJob.generatedText },
            generatedText = runtimeJob.generatedText,
            scenePrompts = runtimeJob.scenePrompts,
            assetPlans = runtimeJob.assetPlans,
            artifacts = runtimeJob.artifacts
        )
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "METADATA_PLAN" || it.artifactType == "REVIEW_STATE" || it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
            .apply { addAll(metadataOutcome.artifacts) }
        val reviewOutcome = executeReviewStage(
            jobId = jobId,
            stepId = reviewStepId,
            metadataPlan = metadataOutcome.plan,
            scenePrompts = runtimeJob.scenePrompts,
            artifacts = updatedArtifacts
        )
        updatedArtifacts += reviewOutcome.artifacts
        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["METADATA"] = StepOverride(metadataOutcome.stepStatus, metadataOutcome.waitingReason)
            this["REVIEW"] = StepOverride(reviewOutcome.stepStatus, reviewOutcome.waitingReason)
            this["PUBLISH"] = StepOverride(AutomationStepStatus.WAITING_USER.name, if (reviewOutcome.state != null) "PUBLISH_WAITING_FOR_REVIEW_APPROVAL" else "PUBLISH_WAITING_FOR_VIDEO_MP4")
        }
        val updatedRuntime = runtimeJob.copy(
            metadataPlan = metadataOutcome.plan,
            reviewState = reviewOutcome.state,
            publishPlan = null,
            artifacts = updatedArtifacts,
            stepOverrides = updatedStepOverrides,
            status = AutomationJobStatus.WAITING_USER.name,
            runtimeMessage = "Metadata da duoc tao lai. Can review truoc khi publish."
        )
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun approveAutomationReview(
        jobId: String
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de approve review.")
        val reviewState = requireNotNull(runtimeJob.reviewState) { "Job nay chua co REVIEW_STATE." }
        val reviewStepId = persisted.steps.firstOrNull { it.stepType == "REVIEW" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc REVIEW.")
        val publishStepId = persisted.steps.firstOrNull { it.stepType == "PUBLISH" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc PUBLISH.")
        val approvedReview = reviewState.copy(
            status = "APPROVED",
            approvedAtEpochMs = System.currentTimeMillis(),
            rejectedAtEpochMs = null,
            rejectedReason = null
        )
        val publishPlan = buildPublishPlan(runtimeJob, approvedReview)
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "REVIEW_STATE" || it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
        artifactStore.saveReviewStateArtifact(jobId, reviewStepId, reviewStateToJson(approvedReview).toString())?.let(updatedArtifacts::add)
        artifactStore.savePublishPlanArtifact(jobId, publishStepId, publishPlanToJson(publishPlan).toString())?.let(updatedArtifacts::add)
        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["REVIEW"] = StepOverride(AutomationStepStatus.COMPLETED.name, "REVIEW_APPROVED")
            this["PUBLISH"] = StepOverride(AutomationStepStatus.WAITING_USER.name, "PUBLISH_READY_MANUAL_ASSISTED")
        }
        val updatedRuntime = runtimeJob.copy(
            reviewState = approvedReview,
            publishPlan = publishPlan,
            artifacts = updatedArtifacts,
            stepOverrides = updatedStepOverrides,
            status = AutomationJobStatus.WAITING_USER.name,
            runtimeMessage = "Review da duoc duyet. Ban co the export/share va danh dau published thu cong."
        )
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun rejectAutomationReview(
        jobId: String,
        reason: String
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de reject review.")
        val reviewState = requireNotNull(runtimeJob.reviewState) { "Job nay chua co REVIEW_STATE." }
        val reviewStepId = persisted.steps.firstOrNull { it.stepType == "REVIEW" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc REVIEW.")
        val rejected = reviewState.copy(
            status = "REJECTED",
            rejectedAtEpochMs = System.currentTimeMillis(),
            rejectedReason = reason.trim().ifBlank { "User rejected review" },
            approvedAtEpochMs = null
        )
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "REVIEW_STATE" || it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
        artifactStore.saveReviewStateArtifact(jobId, reviewStepId, reviewStateToJson(rejected).toString())?.let(updatedArtifacts::add)
        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["REVIEW"] = StepOverride(AutomationStepStatus.FAILED.name, "REVIEW_REJECTED")
            this["PUBLISH"] = StepOverride(AutomationStepStatus.WAITING_USER.name, "PUBLISH_WAITING_FOR_REVIEW_APPROVAL")
        }
        val updatedRuntime = runtimeJob.copy(
            reviewState = rejected,
            publishPlan = null,
            artifacts = updatedArtifacts,
            stepOverrides = updatedStepOverrides,
            status = AutomationJobStatus.WAITING_USER.name,
            runtimeMessage = "Review da bi tu choi. Ban co the retry metadata truoc khi publish."
        )
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun preparePublishShare(
        jobId: String
    ): AutomationPublishShareResult {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de share publish.")
        val publishPlan = requireNotNull(runtimeJob.publishPlan) { "Can approve review truoc khi share publish." }
        val export = exportVideoMp4ToDownloads(jobId)
        val updatedPlan = publishPlan.copy(
            status = "MANUAL_ASSISTED",
            notes = publishPlan.notes + "Share sheet opened"
        )
        val publishStepId = persisted.steps.firstOrNull { it.stepType == "PUBLISH" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc PUBLISH.")
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
        artifactStore.savePublishPlanArtifact(jobId, publishStepId, publishPlanToJson(updatedPlan).toString())?.let(updatedArtifacts::add)
        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["PUBLISH"] = StepOverride(AutomationStepStatus.WAITING_USER.name, "PUBLISH_MANUAL_SHARE_OPENED")
        }
        val updatedRuntime = runtimeJob.copy(
            publishPlan = updatedPlan,
            artifacts = updatedArtifacts,
            stepOverrides = updatedStepOverrides,
            runtimeMessage = "Share sheet da duoc mo. Hay xac nhan dang trong app nen tang roi danh dau Published."
        )
        putRuntimeJob(jobId, updatedRuntime)
        return AutomationPublishShareResult(
            job = persisted.toUiSnapshot(runtimeJob = updatedRuntime),
            export = export,
            shareText = buildPublishShareText(updatedRuntime.metadataPlan),
            chooserTitle = "Share video automation"
        )
    }

    suspend fun markAutomationPublished(
        jobId: String
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de mark published.")
        val publishPlan = requireNotNull(runtimeJob.publishPlan) { "Job nay chua co PUBLISH_PLAN." }
        val publishStepId = persisted.steps.firstOrNull { it.stepType == "PUBLISH" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc PUBLISH.")
        val completedPlan = publishPlan.copy(
            status = "PUBLISHED_MARKED",
            publishedAtEpochMs = System.currentTimeMillis(),
            notes = publishPlan.notes + "Marked as published by user"
        )
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "PUBLISH_PLAN" }
            .toMutableList()
        artifactStore.savePublishPlanArtifact(jobId, publishStepId, publishPlanToJson(completedPlan).toString())?.let(updatedArtifacts::add)
        val updatedStepOverrides = runtimeJob.stepOverrides.toMutableMap().apply {
            this["PUBLISH"] = StepOverride(AutomationStepStatus.COMPLETED.name, "PUBLISH_MARKED_COMPLETED")
        }
        val updatedRuntime = runtimeJob.copy(
            publishPlan = completedPlan,
            artifacts = updatedArtifacts,
            stepOverrides = updatedStepOverrides,
            status = AutomationJobStatus.COMPLETED.name,
            runtimeMessage = "Publish da duoc danh dau hoan tat."
        )
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    /**
     * Giong importImageArtifacts() nhung nhan Map<sceneIndex, image> thay vi List
     * gan theo VI TRI - dung cho luong cao anh tu web (Google Images/Pinterest),
     * noi 1 vai canh co the cao that bai va bi bo qua. Neu dung List thuong va loai
     * bo canh loi, cac canh sau se bi day len sai vi tri (gan nham anh nhan vat nay
     * cho nhan vat khac) - Map giu dung sceneIndex nen khong bi loi nay.
     */
    suspend fun importImageArtifactsByIndex(
        jobId: String,
        imagesBySceneIndex: Map<Int, ImportedAutomationImage>
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        require(imagesBySceneIndex.isNotEmpty()) { "Can co it nhat mot anh cao duoc." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de import anh.")
        require(runtimeJob.scenePrompts.isNotEmpty()) {
            "Job nay chua co scene prompts de gan anh."
        }
        val imageStepId = persisted.steps.firstOrNull { it.stepType == "IMAGES_VISUALS" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc IMAGE trong job.")
        val targetScenes = runtimeJob.scenePrompts.sortedBy { it.ordinal }
        val existingImagesByScene = runtimeJob.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .associateBy { it.sceneId!! }
            .toMutableMap()
        val providerId = resolveSelectedImageProviderId() ?: AutomationImageProviders.CHATGPT_IMAGE_SEARCH_WEB
        val savedArtifacts = imagesBySceneIndex.mapNotNull { (index, image) ->
            val scene = targetScenes.getOrNull(index) ?: return@mapNotNull null
            artifactStore.saveGeneratedImageArtifact(
                jobId = jobId,
                stepId = imageStepId,
                bytes = image.bytes,
                providerId = providerId,
                model = "web-scrape",
                mimeType = image.mimeType,
                sourceUrl = "web-scrape;displayName=${image.displayName.replace(';', ',')}",
                sceneId = scene.sceneId,
                ordinal = scene.ordinal,
                providerRequestId = null
            )
        }
        savedArtifacts.forEach { artifact ->
            artifact.sceneId?.let { sceneId -> existingImagesByScene[sceneId] = artifact }
        }
        val mergedImages = targetScenes.mapNotNull { scene -> existingImagesByScene[scene.sceneId] }
        val complete = mergedImages.size >= targetScenes.size
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "IMAGE" || it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" }
            .toMutableList()
            .apply { addAll(mergedImages) }
        val updatedSteps = runtimeJob.stepOverrides.toMutableMap().apply {
            this["IMAGES_VISUALS"] = StepOverride(
                status = if (complete) AutomationStepStatus.COMPLETED.name else AutomationStepStatus.WAITING_USER.name,
                waitingReason = if (complete) "WEB_SCRAPE_IMAGES_READY" else "WAITING_WEB_IMAGE_SCRAPE"
            )
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "VIDEO_WAITING_FOR_RENDER_RETRY"
            )
        }
        val updatedRuntime = runtimeJob.copy(
            status = if (complete) AutomationJobStatus.WAITING_USER.name else runtimeJob.status,
            artifacts = updatedArtifacts,
            runtimeMessage = if (complete) {
                "Da cao du anh tu web. Ban co the tiep tuc tao giong doc/video cho phien nay."
            } else {
                "Da co ${mergedImages.size}/${targetScenes.size} anh trong phien; vua cao them ${savedArtifacts.size} anh tu web."
            },
            stepOverrides = updatedSteps,
            imageProviderId = providerId,
            imageModel = "web-scrape",
            imageAttemptHistory = runtimeJob.imageAttemptHistory + providerId,
            videoRenderPlan = null,
            metadataPlan = null,
            reviewState = null,
            publishPlan = null
        ).let { stampImageSignatures(it, savedArtifacts.mapNotNull { a -> a.sceneId }.toSet()) }
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }

    suspend fun importImageArtifacts(
        jobId: String,
        importedImages: List<ImportedAutomationImage>
    ): AutomationUiJobSnapshot {
        require(jobId.isNotBlank()) { "Job ID is required." }
        require(importedImages.isNotEmpty()) { "Can chon it nhat mot anh." }
        val persisted = repository.getJobGraph(jobId)
            ?: throw IllegalArgumentException("Khong tim thay job tu dong hoa.")
        val runtimeJob = runtimeJobs[jobId]
            ?: throw IllegalArgumentException("Khong tim thay runtime job de import anh.")
        require(runtimeJob.scenePrompts.isNotEmpty()) {
            "Job nay chua co scene prompts de gan anh."
        }
        val imageStepId = persisted.steps.firstOrNull { it.stepType == "IMAGES_VISUALS" }?.stepId
            ?: throw IllegalArgumentException("Khong tim thay buoc IMAGE trong job.")
        val targetScenes = runtimeJob.scenePrompts.sortedBy { it.ordinal }
        val existingImagesByScene = runtimeJob.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .associateBy { it.sceneId!! }
            .toMutableMap()
        val savedArtifacts = importedImages.take(targetScenes.size).mapIndexedNotNull { index, image ->
            val scene = targetScenes[index]
            artifactStore.saveGeneratedImageArtifact(
                jobId = jobId,
                stepId = imageStepId,
                bytes = image.bytes,
                providerId = MANUAL_IMAGE_IMPORT_PROVIDER_ID,
                model = "device-import",
                mimeType = image.mimeType,
                sourceUrl = "imported-from-device;displayName=${image.displayName.replace(';', ',')}",
                sceneId = scene.sceneId,
                ordinal = scene.ordinal,
                providerRequestId = null
            )
        }
        savedArtifacts.forEach { artifact ->
            artifact.sceneId?.let { sceneId -> existingImagesByScene[sceneId] = artifact }
        }
        val mergedImages = targetScenes.mapNotNull { scene -> existingImagesByScene[scene.sceneId] }
        val complete = mergedImages.size >= targetScenes.size
        val updatedArtifacts = runtimeJob.artifacts
            .filterNot { it.artifactType == "IMAGE" || it.artifactType == "VIDEO_RENDER_PLAN" || it.artifactType == "VIDEO_MP4" }
            .toMutableList()
            .apply { addAll(mergedImages) }
        val updatedSteps = runtimeJob.stepOverrides.toMutableMap().apply {
            this["IMAGES_VISUALS"] = StepOverride(
                status = if (complete) AutomationStepStatus.COMPLETED.name else AutomationStepStatus.WAITING_USER.name,
                waitingReason = if (complete) "MANUAL_IMAGES_READY" else "MANUAL_IMAGE_IMPORT_INCOMPLETE"
            )
            this["VIDEO"] = StepOverride(
                status = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "VIDEO_WAITING_FOR_RENDER_RETRY"
            )
        }
        val updatedRuntime = runtimeJob.copy(
            status = if (complete) AutomationJobStatus.WAITING_USER.name else runtimeJob.status,
            artifacts = updatedArtifacts,
            runtimeMessage = if (complete) {
                "Da nhap du anh tu may. Ban co the tiep tuc retry voice/video cho phien nay."
            } else {
                "Da co ${mergedImages.size}/${targetScenes.size} anh trong phien; vua nhap ${savedArtifacts.size} anh tu may."
            },
            stepOverrides = updatedSteps,
            imageProviderId = MANUAL_IMAGE_IMPORT_PROVIDER_ID,
            imageModel = "device-import",
            imageAttemptHistory = runtimeJob.imageAttemptHistory + MANUAL_IMAGE_IMPORT_PROVIDER_ID,
            videoRenderPlan = null
        ).let { stampImageSignatures(it, savedArtifacts.mapNotNull { a -> a.sceneId }.toSet()) }
        putRuntimeJob(jobId, updatedRuntime)
        return persisted.toUiSnapshot(runtimeJob = updatedRuntime)
    }


    private data class ContentSufficiencyCheck(
        val isSufficient: Boolean,
        val wordCount: Int,
        val minimumWordCount: Int,
        val estimatedDurationMs: Long,
        val targetDurationMs: Long
    )

    private fun evaluateContentSufficiency(
        result: ContentGenerationResult,
        durationPolicy: ContentDurationPolicy?
    ): ContentSufficiencyCheck {
        val narration = result.structuredScript?.fullVoiceText()
            ?.takeIf { it.isNotBlank() }
            ?: result.generatedText
        val wordCount = countNarrationWords(narration)
        val targetDurationMs = durationPolicy?.targetDurationMs?.coerceAtLeast(0L) ?: 0L
        val enforceDuration = durationPolicy?.explicitDuration == true
        val minimumWordCount = if (enforceDuration) minimumNarrationWordCount(targetDurationMs) else 0
        val estimatedDurationMs = estimateNarrationDurationMs(wordCount)
        // Khong con kiem tra "du so muc" - Gemini tu quyet so muc/co cau truc phu
        // hop, lqlq khong doan/ep buoc con so nao. Chi con dieu kien noi dung THAT
        // (co loi thoai) va DU THOI LUONG (word count/duration) - ca 2 deu khong
        // phu thuoc vao viec lqlq doan chu de la loai gi.
        val durationSufficient = !enforceDuration || wordCount >= minimumWordCount
        val hasNarration = narration.isNotBlank()
        return ContentSufficiencyCheck(
            isSufficient = hasNarration && durationSufficient,
            wordCount = wordCount,
            minimumWordCount = minimumWordCount,
            estimatedDurationMs = estimatedDurationMs,
            targetDurationMs = targetDurationMs
        )
    }

    private fun buildContentRepairInstruction(check: ContentSufficiencyCheck): String {
        val requirements = mutableListOf<String>()
        if (check.minimumWordCount > 0 && check.wordCount < check.minimumWordCount) {
            requirements += "Loi doc hien tai chi khoang ${check.estimatedDurationMs / 1000L} giay voi ${check.wordCount} tu. Hay viet lai va mo rong tu nhien de co IT NHAT ${check.minimumWordCount} tu, tuong duong toi thieu ${check.targetDurationMs / 1000L} giay doc binh thuong; dai hon duoc phep, ngan hon khong dat."
        }
        requirements += "Chi tra ve ket qua hoan chinh theo dung schema da yeu cau, khong giai thich them."
        return requirements.joinToString(" ")
    }

    private fun recommendedRepairOutputLength(durationPolicy: ContentDurationPolicy?): Int {
        // MOT gia tri co dinh - khong con moc theo thoi luong.
        return 12_000
    }

    private fun minimumNarrationWordCount(targetDurationMs: Long): Int {
        if (targetDurationMs <= 0L) return 0
        val targetSeconds = (targetDurationMs + 999L) / 1000L
        return (((targetSeconds * 23L) + 9L) / 10L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun estimateNarrationDurationMs(wordCount: Int): Long {
        if (wordCount <= 0) return 0L
        return ((wordCount.toLong() * 10_000L) + 22L) / 23L
    }

    private fun countNarrationWords(text: String): Int {
        return NARRATION_WORD_PATTERN.findAll(text).count()
    }

    // Nguon anh mien phi khong can API key (Openverse/Wikimedia) gioi han toc do
    // rat chat cho khach vang lai. Mot job co nhieu canh (vd "top 10") goi lien
    // tiep de bi RATE_LIMITED giua chung du tung anh rieng le van tim duoc. Thu
    // lai co nghi (backoff tang dan) truoc khi thuc su bao loi, thay vi hong ca
    // buoc anh chi vi 1 canh bi gioi han tam thoi.
    private suspend fun generateImageWithRateLimitRetry(
        connector: ImageGenerationConnector,
        config: ImageProviderConfig,
        request: ImageGenerationRequest
    ): ImageGenerationResult {
        var attempt = 0
        while (true) {
            try {
                return connector.generateImage(config, request)
            } catch (error: ImageProviderException) {
                if (error.code != ImageProviderErrorCode.RATE_LIMITED || attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw error
                }
                attempt += 1
                delay(RATE_LIMIT_RETRY_BASE_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun executeImageStage(
        jobId: String,
        stepId: String,
        scenePrompts: List<ScenePrompt>,
        assetPlans: List<VisualAssetPlan>,
        providerId: String?
    ): ImageStageOutcome {
        val definition = resolveRequestedProviderId(providerId)?.let(imageProviderRegistry::getDefinition)
        if (definition == null) {
            return ImageStageOutcome(
                providerId = null,
                model = null,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "PENDING_USER_IMAGE_CONFIGURATION",
                runtimeMessage = "Content da tao xong va scene prompts da san sang. Can chon va cau hinh image provider truoc khi tao anh."
            )
        }
        if (definition.health == ImageProviderHealth.NOT_IMPLEMENTED) {
            return ImageStageOutcome(
                providerId = definition.providerId,
                model = definition.capabilities.defaultModel,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "IMAGE_PROVIDER_NOT_IMPLEMENTED",
                runtimeMessage = "${definition.displayName} da co trong registry nhung connector native chua duoc port. Content va scene prompts duoc giu nguyen de ban co the chon provider khac hoac doi pass tiep theo."
            )
        }

        var config = credentialStore.getImageProviderConfiguration(definition.providerId)
        if (config == null && definition.authType == ImageProviderAuthType.NONE) {
            // Provider khong can credential (Tu dong/Openverse/Wikimedia...) khong nen
            // bat nguoi dung phai vao Cai dat luu tay truoc khi chay pipeline - tu luu
            // 1 ban ghi cau hinh rong ngay tai day de tu phuc hoi, tranh phu thuoc vao
            // dung thoi diem JS goi refresh/luu tren man Cai dat.
            credentialStore.saveImageProviderConfiguration(
                providerId = definition.providerId,
                apiKey = "",
                model = definition.capabilities.defaultModel.orEmpty(),
                accountId = null
            )
            config = credentialStore.getImageProviderConfiguration(definition.providerId)
        }
        if (config == null) {
            return ImageStageOutcome(
                providerId = definition.providerId,
                model = definition.capabilities.defaultModel,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "PENDING_USER_IMAGE_CONFIGURATION",
                runtimeMessage = "Content da tao xong va scene prompts da san sang. Can cau hinh ${definition.displayName} truoc khi tao anh."
            )
        }

        if (definition.providerId in WEB_SCRAPE_IMAGE_PROVIDER_IDS) {
            // Google Images/Pinterest can WebView foreground de cao anh (giong Gemini
            // web) - khong the goi nhu 1 ImageGenerationConnector chay ngam trong
            // WorkManager. Dung o day, giu nguyen scene prompts/asset plan, cho JS
            // goi rieng bridge scrapeWebImages() trong luc app dang mo, sau do
            // importImageArtifacts() se day buoc anh sang COMPLETED nhu binh thuong.
            return ImageStageOutcome(
                providerId = definition.providerId,
                model = definition.capabilities.defaultModel,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.WAITING_USER.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "WAITING_WEB_IMAGE_SCRAPE",
                runtimeMessage = "Content va scene prompts da san sang. Dang tu dong cao anh tu ${definition.displayName} (can giu app dang mo, khong can bam gi them)."
            )
        }

        val connector = requireImageConnector(definition.providerId)
        val assetPlanBySceneId = assetPlans.associateBy { it.sceneId }
        suspend fun runImageGeneration(
            effectiveDefinition: ImageProviderDefinition,
            effectiveConfig: ImageProviderCredentialConfiguration,
            effectiveConnector: ImageGenerationConnector
        ): List<AutomationSavedArtifact> {
            return scenePrompts
                .sortedBy { it.ordinal }
                .mapNotNull { scene ->
                    val assetPlan = assetPlanBySceneId[scene.sceneId]
                    val requestPrompt = resolveImagePrompt(scene, assetPlan)
                    val imageRequest = ImageGenerationRequest(
                        jobId = jobId,
                        sceneId = scene.sceneId,
                        ordinal = scene.ordinal,
                        prompt = requestPrompt,
                        aspectRatio = scene.aspectRatio,
                        negativePrompt = scene.negativePrompt
                    )
                    val imageConfig = ImageProviderConfig(
                        providerId = effectiveConfig.providerId,
                        apiKey = effectiveConfig.apiKey,
                        model = effectiveConfig.model,
                        accountId = effectiveConfig.accountId
                    )
                    // Nguon mien phi khong key (Openverse/Wikimedia) gioi han toc do rat
                    // chat cho khach vang lai - goi lien tiep nhieu canh trong 1 job de
                    // bi RATE_LIMITED giua chung. Thu lai co nghi (backoff) thay vi hong
                    // ca buoc anh chi vi 1 canh bi gioi han tam thoi.
                    val imageResult = generateImageWithRateLimitRetry(effectiveConnector, imageConfig, imageRequest)
                    artifactStore.saveGeneratedImageArtifact(
                        jobId = jobId,
                        stepId = stepId,
                        bytes = imageResult.bytes,
                        providerId = imageResult.providerId,
                        model = imageResult.model,
                        mimeType = imageResult.mimeType,
                        sourceUrl = buildImageArtifactDebugSource(requestPrompt, imageResult.providerRequestId),
                        sceneId = imageResult.sceneId,
                        ordinal = imageResult.ordinal,
                        providerRequestId = imageResult.providerRequestId
                    )
                }
        }
        return try {
            val imageArtifacts = runImageGeneration(definition, config, connector)

            credentialStore.markImageProviderState(
                definition.providerId,
                AutomationCredentialStore.IMAGE_STATE_GENERATION_VERIFIED,
                "Da tao va xac minh anh that bang ${definition.displayName}."
            )

            ImageStageOutcome(
                providerId = definition.providerId,
                model = config.model,
                artifacts = imageArtifacts,
                stepStatus = AutomationStepStatus.COMPLETED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "REAL_IMAGES_READY",
                runtimeMessage = "Content, scene prompts va anh that da duoc tao xong voi ${definition.displayName}. Voice, video, metadata va publish van chua duoc cau hinh trong pass nay."
            )
        } catch (error: ImageProviderException) {
            updateImageProviderFailureState(definition.providerId, error)
            val stepStatus = when (error.code) {
                ImageProviderErrorCode.NOT_CONFIGURED,
                ImageProviderErrorCode.USER_ACTION_REQUIRED -> AutomationStepStatus.NOT_CONFIGURED.name
                else -> AutomationStepStatus.FAILED.name
            }
            val jobStatus = if (stepStatus == AutomationStepStatus.NOT_CONFIGURED.name) {
                AutomationJobStatus.WAITING_USER.name
            } else {
                AutomationJobStatus.FAILED.name
            }
            ImageStageOutcome(
                providerId = definition.providerId,
                model = config.model,
                artifacts = emptyList(),
                stepStatus = stepStatus,
                jobStatus = jobStatus,
                waitingReason = mapImageWaitingReason(error.code),
                // Ghep them error.message (vd Openverse/Wikimedia deu khong tim thay
                // anh phu hop, ly do cu the tu tung nguon) thay vi chi hien cau chung
                // chung khong noi ro vi sao that bai.
                runtimeMessage = "Content va scene prompts da duoc giu nguyen, nhung ${definition.displayName} chua tao duoc anh that luc nay: ${error.message.orEmpty()} Ban co the sua cau hinh hien tai hoac thu lai voi provider khac ma khong can chay lai CONTENT."
            )
        }
    }

    /**
     * Gan thoi luong THAT do duoc tu buoc voice (theo ordinal tang dan) vao
     * plannedDurationMs cua tung ScenePrompt. Neu so luong khong khop hoac rong
     * (fallback khong tach canh) thi tra ve nguyen ban, giu uoc luong cu.
     */
    private fun applyRealSceneDurations(
        scenePrompts: List<ScenePrompt>,
        sceneDurationsMs: List<Long>
    ): List<ScenePrompt> {
        if (sceneDurationsMs.isEmpty()) return scenePrompts
        val ordered = scenePrompts.sortedBy { it.ordinal }
        if (sceneDurationsMs.size != ordered.size) return scenePrompts
        val updatedBySceneId = ordered.mapIndexed { index, scene ->
            scene.sceneId to sceneDurationsMs[index].coerceAtLeast(1L)
        }.toMap()
        return scenePrompts.map { scene ->
            val realDuration = updatedBySceneId[scene.sceneId] ?: return@map scene
            scene.copy(plannedDurationMs = realDuration)
        }
    }

    private suspend fun executeVoiceStage(
        jobId: String,
        stepId: String,
        scenePrompts: List<ScenePrompt>,
        durationPolicy: ContentDurationPolicy,
        providerId: String?
    ): VoiceStageOutcome {
        val definition = resolveRequestedVoiceProviderId(providerId)?.let(voiceProviderRegistry::getDefinition)
        if (definition == null) {
            return VoiceStageOutcome(
                providerId = null,
                voiceId = null,
                locale = null,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "VOICE_NOT_CONFIGURED",
                runtimeMessage = "Content, scene prompts va image artifact da san sang. Can cau hinh voice provider truoc khi tao giong doc."
            )
        }
        if (definition.health == VoiceProviderHealth.NOT_IMPLEMENTED) {
            return VoiceStageOutcome(
                providerId = definition.providerId,
                voiceId = null,
                locale = definition.capabilities.defaultLocale,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "VOICE_PROVIDER_NOT_IMPLEMENTED",
                runtimeMessage = "${definition.displayName} da co trong registry nhung connector native chua duoc port. Content, scene prompts va image artifact duoc giu nguyen de ban co the doi provider hoac doi pass tiep theo."
            )
        }

        var config = credentialStore.getVoiceProviderConfiguration(definition.providerId)
        if (config == null && definition.authType == VoiceProviderAuthType.NONE) {
            // Cung logic tu phuc hoi nhu image: provider khong can credential (vd
            // Google TTS tren may) khong nen bat cho nguoi dung vao Cai dat luu tay.
            credentialStore.saveVoiceProviderConfiguration(
                VoiceProviderCredentialConfiguration(
                    providerId = definition.providerId,
                    locale = definition.capabilities.defaultLocale,
                    outputFormat = definition.capabilities.supportedOutputFormats.firstOrNull() ?: "wav"
                )
            )
            config = credentialStore.getVoiceProviderConfiguration(definition.providerId)
        }
        if (config == null) {
            return VoiceStageOutcome(
                providerId = definition.providerId,
                voiceId = null,
                locale = definition.capabilities.defaultLocale,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "PENDING_USER_VOICE_CONFIGURATION",
                runtimeMessage = "Content, scene prompts va image artifact da san sang. Can cau hinh ${definition.displayName} truoc khi tao giong doc."
            )
        }

        val connector = requireVoiceConnector(definition.providerId)
        return try {
            val orderedVoiceScenes = scenePrompts.sortedBy { it.ordinal }
            // Sinh TTS TUNG CANH rieng (thay vi gop ca kich ban lam 1 file) de do
            // duoc thoi luong THAT cua tung canh -> renderer dat anh dung theo loi
            // doc. Canh voiceText rong tra ve null -> assembler chen khoang lang
            // placeholder giu timeline khong lech.
            val perSceneWavs = ArrayList<ByteArray?>(orderedVoiceScenes.size)
            var lastMetadataMime = "audio/wav"
            var lastVoiceId = config.voiceId
            var lastLocale = config.locale
            var totalChunkCount = 0
            var totalInputChars = 0
            orderedVoiceScenes.forEach { scene ->
                val sceneText = scene.voiceText.trim()
                if (sceneText.isEmpty()) {
                    perSceneWavs += null
                    return@forEach
                }
                val sceneResult = connector.generateVoice(
                    config = config.toVoiceProviderConfig(),
                    request = VoiceGenerationRequest(
                        jobId = jobId,
                        scriptArtifactId = "generated-script-$jobId-scene-${scene.ordinal}",
                        text = sceneText,
                        providerId = definition.providerId,
                        voiceId = config.voiceId,
                        locale = config.locale,
                        speechRate = config.speechRate,
                        pitch = config.pitch,
                        outputFormat = config.outputFormat
                    )
                )
                perSceneWavs += sceneResult.bytes
                lastMetadataMime = sceneResult.mimeType
                lastVoiceId = sceneResult.metadata.voiceId
                lastLocale = sceneResult.metadata.locale
                totalChunkCount += sceneResult.metadata.chunkCount
                totalInputChars += sceneText.length
            }
            require(perSceneWavs.any { it != null }) {
                "Tat ca cac canh deu rong voiceText, khong tao duoc giong doc."
            }

            // Chi Android TTS tra ve WAV nen chi ghep-theo-canh khi la WAV. Provider
            // khac (mp3...) tam thoi giu luong cu (chua tach canh) de khong lam hong;
            // se mo rong khi lam Edge TTS o workstream B.
            val combined = if (lastMetadataMime == "audio/wav") {
                runCatching { WavAudioAssembler.combine(perSceneWavs, silenceGapMs = SCENE_TRANSITION_SILENCE_MS) }.getOrNull()
            } else {
                null
            }

            val savedArtifact: AutomationSavedArtifact?
            val sceneDurationsMs: List<Long>
            if (combined != null) {
                savedArtifact = artifactStore.saveGeneratedVoiceArtifact(
                    jobId = jobId,
                    stepId = stepId,
                    bytes = combined.wavBytes,
                    providerId = definition.providerId,
                    voiceId = lastVoiceId,
                    locale = lastLocale,
                    mimeType = "audio/wav",
                    durationMs = combined.totalDurationMs,
                    chunkCount = totalChunkCount,
                    inputCharCount = totalInputChars,
                    inputSceneCount = orderedVoiceScenes.size
                )
                sceneDurationsMs = combined.sceneDurationsMs
            } else {
                // Fallback an toan: gop ca kich ban lam 1 lan nhu truoc (khong tach canh).
                val voiceInputText = orderedVoiceScenes.joinToString("\n\n") { it.voiceText.trim() }.trim()
                val result = connector.generateVoice(
                    config = config.toVoiceProviderConfig(),
                    request = VoiceGenerationRequest(
                        jobId = jobId,
                        scriptArtifactId = "generated-script-$jobId",
                        text = voiceInputText,
                        providerId = definition.providerId,
                        voiceId = config.voiceId,
                        locale = config.locale,
                        speechRate = config.speechRate,
                        pitch = config.pitch,
                        outputFormat = config.outputFormat
                    )
                )
                savedArtifact = artifactStore.saveGeneratedVoiceArtifact(
                    jobId = jobId,
                    stepId = stepId,
                    bytes = result.bytes,
                    providerId = result.metadata.providerId,
                    voiceId = result.metadata.voiceId,
                    locale = result.metadata.locale,
                    mimeType = result.mimeType,
                    durationMs = result.metadata.durationMs,
                    chunkCount = result.metadata.chunkCount,
                    inputCharCount = voiceInputText.length,
                    inputSceneCount = orderedVoiceScenes.size
                )
                sceneDurationsMs = emptyList()
            }

            credentialStore.markVoiceProviderState(
                definition.providerId,
                AutomationCredentialStore.VOICE_STATE_GENERATION_VERIFIED,
                "Da tao va xac minh giong doc that bang ${definition.displayName}."
            )

            VoiceStageOutcome(
                providerId = definition.providerId,
                voiceId = lastVoiceId,
                locale = lastLocale,
                artifacts = listOfNotNull(savedArtifact),
                stepStatus = AutomationStepStatus.COMPLETED.name,
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                waitingReason = "REAL_VOICE_READY",
                runtimeMessage = "Content, scene prompts, image artifact va voice artifact da san sang voi ${definition.displayName}. Video, metadata va publish van chua duoc cau hinh trong pass nay.",
                sceneDurationsMs = sceneDurationsMs
            )
        } catch (error: VoiceProviderException) {
            updateVoiceProviderFailureState(definition.providerId, error)
            val stepStatus = when (error.code) {
                VoiceProviderErrorCode.NOT_CONFIGURED,
                VoiceProviderErrorCode.USER_ACTION_REQUIRED,
                VoiceProviderErrorCode.VOICE_NOT_INSTALLED -> AutomationStepStatus.NOT_CONFIGURED.name
                else -> AutomationStepStatus.FAILED.name
            }
            val jobStatus = if (stepStatus == AutomationStepStatus.NOT_CONFIGURED.name) {
                AutomationJobStatus.WAITING_USER.name
            } else {
                AutomationJobStatus.FAILED.name
            }
            VoiceStageOutcome(
                providerId = definition.providerId,
                voiceId = config.voiceId,
                locale = config.locale,
                artifacts = emptyList(),
                stepStatus = stepStatus,
                jobStatus = jobStatus,
                waitingReason = mapVoiceWaitingReason(error.code),
                runtimeMessage = "Content, scene prompts va image artifact da duoc giu nguyen, nhung ${definition.displayName} chua tao duoc giong doc that luc nay. Ban co the sua cau hinh hien tai hoac thu lai ma khong can chay lai CONTENT hay IMAGE."
            )
        }
    }

    private suspend fun executeVideoStage(
        jobId: String,
        stepId: String,
        generatedText: String,
        scenePrompts: List<ScenePrompt>,
        assetPlans: List<VisualAssetPlan>,
        artifacts: List<AutomationSavedArtifact>,
        videoRendererMode: String,
        videoWorkerUrl: String?,
        videoQualityTier: String = "1080p",
        videoBackgroundMode: String = "blurred_fill",
        videoMotionMode: String = "auto_mix",
        backgroundMusicFilePath: String? = null,
        backgroundMusicLoop: Boolean = true,
        backgroundMusicVolume: Float = 0.35f,
        videoSubtitleColor: String = "#FFFFFF",
        videoTitle: String = ""
    ): VideoStageOutcome {
        val imageArtifacts = artifacts
            .filter { it.artifactType == "IMAGE" }
            .sortedBy { it.ordinal ?: Int.MAX_VALUE }
        if (imageArtifacts.isEmpty()) {
            return VideoStageOutcome(
                rendererId = null,
                plan = null,
                artifacts = emptyList(),
                subtitleStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                subtitleWaitingReason = "SUBTITLE_WAITING_FOR_IMAGE",
                videoStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                videoWaitingReason = "VIDEO_WAITING_FOR_IMAGE",
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                runtimeMessage = "Render plan video dang cho image artifacts thuc te truoc khi co the dung subtitle/video."
            )
        }

        val voiceArtifact = artifacts.firstOrNull { it.artifactType == "VOICE" }
            ?: return VideoStageOutcome(
                rendererId = null,
                plan = null,
                artifacts = emptyList(),
                subtitleStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                subtitleWaitingReason = "SUBTITLE_WAITING_FOR_VOICE",
                videoStepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                videoWaitingReason = "VIDEO_WAITING_FOR_VOICE",
                jobStatus = AutomationJobStatus.WAITING_USER.name,
                runtimeMessage = "Image artifacts da san sang nhung render plan video can voice artifact thuc te de tinh timeline."
            )

        val result = videoRenderer.createRenderPlan(
            VideoRenderRequest(
                jobId = jobId,
                generatedText = generatedText,
                scenePrompts = scenePrompts,
                assetPlans = assetPlans,
                imageArtifacts = imageArtifacts,
                voiceArtifact = voiceArtifact,
                videoRendererMode = videoRendererMode,
                videoWorkerUrl = videoWorkerUrl,
                videoQualityTier = videoQualityTier,
                videoBackgroundMode = videoBackgroundMode,
                videoMotionMode = videoMotionMode,
                backgroundMusicFilePath = backgroundMusicFilePath,
                backgroundMusicLoop = backgroundMusicLoop,
                backgroundMusicVolume = backgroundMusicVolume,
                videoSubtitleColor = videoSubtitleColor,
                videoTitle = videoTitle
            )
        )
        val planJson = VideoRenderPlanJson.encode(result.plan)
        val savedArtifact = artifactStore.saveGeneratedVideoRenderPlanArtifact(
            jobId = jobId,
            stepId = stepId,
            json = planJson,
            rendererId = result.rendererId,
            sourceSummary = "sceneCount=${result.plan.sceneCount};voiceArtifactUri=${result.plan.voiceArtifactUri};voiceDurationMs=${extractDebugLong(voiceArtifact.sourceUrl, "durationMs") ?: 0};plannedDurationMs=${result.plan.totalDurationMs}"
        )

        if (normalizeVideoRendererMode(videoRendererMode) == VIDEO_RENDERER_MODE_ANDROID_NATIVE) {
            return try {
                val nativeVideo = nativeVideoRenderer.renderVideo(
                    request = VideoRenderRequest(
                        jobId = jobId,
                        generatedText = generatedText,
                        scenePrompts = scenePrompts,
                        assetPlans = assetPlans,
                        imageArtifacts = imageArtifacts,
                        voiceArtifact = voiceArtifact,
                        videoRendererMode = videoRendererMode,
                        videoWorkerUrl = videoWorkerUrl,
                        videoQualityTier = videoQualityTier,
                        videoBackgroundMode = videoBackgroundMode,
                        videoMotionMode = videoMotionMode,
                        backgroundMusicFilePath = backgroundMusicFilePath,
                        backgroundMusicLoop = backgroundMusicLoop,
                        backgroundMusicVolume = backgroundMusicVolume,
                        videoSubtitleColor = videoSubtitleColor,
                        videoTitle = videoTitle
                    ),
                    plan = result.plan,
                    artifactStore = artifactStore
                )
                val videoArtifact = artifactStore.saveGeneratedVideoFileArtifact(
                    jobId = jobId,
                    stepId = stepId,
                    bytes = nativeVideo.bytes,
                    rendererId = nativeVideo.rendererId,
                    mimeType = nativeVideo.mimeType,
                    sourceUrl = listOf(
                        "rendererBackend=${nativeVideo.rendererBackend}",
                        "durationMs=${nativeVideo.durationMs}",
                        "actualWidth=${nativeVideo.width}",
                        "actualHeight=${nativeVideo.height}",
                        "fps=${nativeVideo.fps}",
                        "bitrate=${nativeVideo.bitrate}",
                        "totalFrames=${nativeVideo.totalFrames}",
                        "sceneCount=${nativeVideo.sceneCount}",
                        "voiceDurationMs=${nativeVideo.durationMs}",
                        "hasVideoTrack=${nativeVideo.hasVideoTrack}",
                        "hasAudioTrack=${nativeVideo.hasAudioTrack}",
                        "firstFrameExtracted=${nativeVideo.firstFrameExtracted}"
                    ).joinToString(";")
                )
                VideoStageOutcome(
                    rendererId = nativeVideo.rendererId,
                    plan = result.plan,
                    artifacts = listOfNotNull(savedArtifact, videoArtifact),
                    subtitleStepStatus = AutomationStepStatus.COMPLETED.name,
                    subtitleWaitingReason = "SUBTITLE_PLAN_READY",
                    videoStepStatus = AutomationStepStatus.COMPLETED.name,
                    videoWaitingReason = "VIDEO_MP4_READY",
                    jobStatus = AutomationJobStatus.WAITING_USER.name,
                    runtimeMessage = "Android da render slideshow MP4 noi bo tu IMAGE + VOICE. Ban co the review truoc khi lam metadata/publish."
                )
            } catch (error: Throwable) {
                VideoStageOutcome(
                    rendererId = result.rendererId,
                    plan = result.plan,
                    artifacts = listOfNotNull(savedArtifact),
                    subtitleStepStatus = AutomationStepStatus.COMPLETED.name,
                    subtitleWaitingReason = "SUBTITLE_PLAN_READY",
                    videoStepStatus = AutomationStepStatus.COMPLETED.name,
                    videoWaitingReason = "VIDEO_RENDER_PLAN_READY",
                    jobStatus = AutomationJobStatus.WAITING_USER.name,
                    runtimeMessage = "Android native renderer chua xuat duoc MP4 tren thiet bi hien tai: ${error.message ?: "unknown_error"}. App giu VIDEO_RENDER_PLAN de ban retry sau."
                )
            }
        }

        if (normalizeVideoRendererMode(videoRendererMode) == VIDEO_RENDERER_MODE_EXTERNAL && !videoWorkerUrl.isNullOrBlank()) {
            return try {
                val renderedVideo = videoRenderWorkerClient.renderVideo(
                    workerUrl = videoWorkerUrl,
                    plan = result.plan,
                    planJson = planJson,
                    voiceArtifact = voiceArtifact,
                    imageArtifacts = imageArtifacts,
                    artifactStore = artifactStore
                )
                val videoArtifact = artifactStore.saveGeneratedVideoFileArtifact(
                    jobId = jobId,
                    stepId = stepId,
                    bytes = renderedVideo.bytes,
                    rendererId = renderedVideo.rendererId,
                    mimeType = renderedVideo.mimeType,
                    sourceUrl = listOfNotNull(
                        renderedVideo.downloadUrl?.takeIf { it.isNotBlank() }?.let { "downloadUrl=$it" },
                        renderedVideo.durationMs?.let { "durationMs=$it" },
                        renderedVideo.width?.let { "width=$it" },
                        renderedVideo.height?.let { "height=$it" },
                        renderedVideo.fps?.let { "fps=$it" },
                        renderedVideo.sceneCount?.let { "sceneCount=$it" }
                    ).joinToString(";")
                )
                VideoStageOutcome(
                    rendererId = renderedVideo.rendererId,
                    plan = result.plan,
                    artifacts = listOfNotNull(savedArtifact, videoArtifact),
                    subtitleStepStatus = AutomationStepStatus.COMPLETED.name,
                    subtitleWaitingReason = "SUBTITLE_PLAN_READY",
                    videoStepStatus = AutomationStepStatus.COMPLETED.name,
                    videoWaitingReason = "VIDEO_MP4_READY",
                    jobStatus = AutomationJobStatus.WAITING_USER.name,
                    runtimeMessage = "Worker MoviePy ben ngoai da nhan IMAGE + VOICE + VIDEO_RENDER_PLAN va xuat MP4 that. Ban co the review truoc khi lam metadata/publish."
                )
            } catch (_: Throwable) {
                VideoStageOutcome(
                    rendererId = result.rendererId,
                    plan = result.plan,
                    artifacts = listOfNotNull(savedArtifact),
                    subtitleStepStatus = AutomationStepStatus.COMPLETED.name,
                    subtitleWaitingReason = "SUBTITLE_PLAN_READY",
                    videoStepStatus = AutomationStepStatus.COMPLETED.name,
                    videoWaitingReason = "VIDEO_RENDER_PLAN_READY",
                    jobStatus = AutomationJobStatus.WAITING_USER.name,
                    runtimeMessage = "Da co VIDEO_RENDER_PLAN. Worker MoviePy chua san sang hoac dang offline, nen app giu fallback render plan de ban retry MP4 sau."
                )
            }
        }

        return VideoStageOutcome(
            rendererId = result.rendererId,
            plan = result.plan,
            artifacts = listOfNotNull(savedArtifact),
            subtitleStepStatus = AutomationStepStatus.COMPLETED.name,
            subtitleWaitingReason = "SUBTITLE_PLAN_READY",
            videoStepStatus = AutomationStepStatus.COMPLETED.name,
            videoWaitingReason = result.waitingReason,
            jobStatus = AutomationJobStatus.WAITING_USER.name,
            runtimeMessage = result.runtimeMessage
                ?: "SCRIPT, SCENE_PROMPTS, ASSET_PLAN, IMAGE va VOICE da san sang. Android da tao VIDEO_RENDER_PLAN JSON de ban review hoac retry native MP4 render sau."
        )
    }

    private fun normalizeVideoRendererMode(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            VIDEO_RENDERER_MODE_ANDROID_NATIVE -> VIDEO_RENDERER_MODE_ANDROID_NATIVE
            VIDEO_RENDERER_MODE_LOCAL_PLAN_ONLY -> VIDEO_RENDERER_MODE_LOCAL_PLAN_ONLY
            VIDEO_RENDERER_MODE_EXTERNAL -> VIDEO_RENDERER_MODE_EXTERNAL
            else -> VIDEO_RENDERER_MODE_ANDROID_NATIVE
        }
    }

    private fun resolveRequestedProviderId(providerId: String?): String? {
        val normalized = providerId?.trim()?.lowercase()?.ifBlank { null }
        if (normalized != null) {
            return normalized
        }
        return resolveSelectedImageProviderId()
    }

    private fun resolveSelectedImageProviderId(): String? {
        val selected = credentialStore.getSelectedImageProviderId()?.trim()?.lowercase()?.ifBlank { null }
        if (selected != null && imageProviderRegistry.getDefinition(selected) != null) {
            return selected
        }
        return imageProviderRegistry
            .allDefinitions()
            .firstOrNull { credentialStore.getImageProviderConfiguration(it.providerId) != null }
            ?.providerId
    }

    private fun resolveRequestedVoiceProviderId(providerId: String?): String? {
        val normalized = providerId?.trim()?.lowercase()?.ifBlank { null }
        if (normalized != null) {
            return normalized
        }
        return resolveSelectedVoiceProviderId()
    }

    private fun resolveSelectedVoiceProviderId(): String? {
        val selected = credentialStore.getSelectedVoiceProviderId()?.trim()?.lowercase()?.ifBlank { null }
        if (selected != null && voiceProviderRegistry.getDefinition(selected) != null) {
            return selected
        }
        return voiceProviderRegistry
            .allDefinitions()
            .firstOrNull { credentialStore.getVoiceProviderConfiguration(it.providerId) != null }
            ?.providerId
    }

    private fun resolveUiVoiceProviderId(): String? {
        return resolveSelectedVoiceProviderId()
            ?: voiceProviderRegistry.allDefinitions().firstOrNull()?.providerId
    }

    private fun requireSupportedImageProviderDefinition(providerId: String): ImageProviderDefinition {
        val definition = imageProviderRegistry.getDefinition(providerId.trim().lowercase())
            ?: throw IllegalArgumentException("Image provider khong hop le.")
        require(definition.health != ImageProviderHealth.NOT_IMPLEMENTED) {
            "Provider ${definition.displayName} chua duoc port trong pass nay."
        }
        return definition
    }

    private fun requireRunnableImageProviderDefinition(providerId: String?): ImageProviderDefinition {
        val resolvedProviderId = resolveRequestedProviderId(providerId)
            ?: throw ImageProviderException(
                ImageProviderErrorCode.NOT_CONFIGURED,
                "Can chon image provider truoc khi kiem tra."
            )
        val definition = imageProviderRegistry.getDefinition(resolvedProviderId)
            ?: throw ImageProviderException(
                ImageProviderErrorCode.NOT_CONFIGURED,
                "Image provider khong hop le."
            )
        if (definition.health == ImageProviderHealth.NOT_IMPLEMENTED) {
            throw ImageProviderException(
                ImageProviderErrorCode.USER_ACTION_REQUIRED,
                "${definition.displayName} chua duoc port trong pass nay."
            )
        }
        return definition
    }

    private fun requireSupportedVoiceProviderDefinition(providerId: String): VoiceProviderDefinition {
        return voiceProviderRegistry.getDefinition(providerId.trim().lowercase())
            ?: throw IllegalArgumentException("Voice provider khong hop le.")
    }

    private fun requireRunnableVoiceProviderDefinition(providerId: String?): VoiceProviderDefinition {
        val resolvedProviderId = resolveRequestedVoiceProviderId(providerId)
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.NOT_CONFIGURED,
                "Can chon voice provider truoc khi kiem tra."
            )
        val definition = voiceProviderRegistry.getDefinition(resolvedProviderId)
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.NOT_CONFIGURED,
                "Voice provider khong hop le."
            )
        if (definition.health == VoiceProviderHealth.NOT_IMPLEMENTED) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.USER_ACTION_REQUIRED,
                "${definition.displayName} chua duoc port trong pass nay."
            )
        }
        return definition
    }

    private fun requireImageConnector(providerId: String): ImageGenerationConnector {
        return imageConnectors[providerId]
            ?: throw ImageProviderException(
                ImageProviderErrorCode.USER_ACTION_REQUIRED,
                "Connector image provider hien tai chua duoc implement."
            )
    }

    private fun requireVoiceConnector(providerId: String): VoiceGenerationConnector {
        return voiceConnectors[providerId]
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.USER_ACTION_REQUIRED,
                "Connector voice provider hien tai chua duoc implement."
            )
    }

    private fun requireGeminiConfiguration(): GeminiCredentialConfiguration {
        return credentialStore.getGeminiConfiguration()
            ?: throw ContentProviderException(
                ContentProviderErrorCode.NOT_CONFIGURED,
                "Can cau hinh Gemini truoc khi chay."
            )
    }

    private fun requireImageProviderConfiguration(
        providerId: String
    ): ImageProviderCredentialConfiguration {
        return credentialStore.getImageProviderConfiguration(providerId)
            ?: throw ImageProviderException(
                ImageProviderErrorCode.NOT_CONFIGURED,
                "Can cau hinh image provider truoc khi chay."
            )
    }

    private fun requireVoiceProviderConfiguration(
        providerId: String
    ): VoiceProviderCredentialConfiguration {
        return credentialStore.getVoiceProviderConfiguration(providerId)
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.NOT_CONFIGURED,
                "Can cau hinh voice provider truoc khi chay."
            )
    }

    private fun normalizeImageModel(
        definition: ImageProviderDefinition,
        model: String
    ): String {
        val requested = model.trim()
        return if (requested.isNotEmpty()) {
            requested
        } else {
            definition.capabilities.defaultModel
                ?: throw IllegalArgumentException("Can nhap model image provider.")
        }
    }

    private fun normalizeImageAccountId(
        definition: ImageProviderDefinition,
        accountId: String?
    ): String? {
        val normalized = accountId?.trim().orEmpty().ifBlank { null }
        return if (definition.providerId == AutomationImageProviders.CLOUDFLARE_WORKERS_AI) {
            normalized
        } else {
            null
        }
    }

    private fun validateImageProviderConfigFields(
        definition: ImageProviderDefinition,
        model: String,
        accountId: String?
    ) {
        when (definition.providerId) {
            AutomationImageProviders.OPENAI_IMAGES -> require(OpenAiImageConnector.isSafeModelName(model)) {
                "Model OpenAI Images khong hop le."
            }

            AutomationImageProviders.CLOUDFLARE_WORKERS_AI -> {
                require(accountId?.isNotBlank() == true) { "Can nhap Cloudflare Account ID." }
                require(CloudflareWorkersAiImageConnector.isSafeModelName(model)) {
                    "Model Cloudflare Workers AI khong hop le."
                }
            }

            else -> require(model.length <= 128 && !model.contains("://") && !model.contains("\\")) {
                "Model image provider khong hop le."
            }
        }
    }

    private fun validateVoiceProviderConfigFields(
        definition: VoiceProviderDefinition,
        model: String?,
        apiKey: String?,
        region: String?
    ) {
        if (definition.capabilities.requiresCredentials) {
            require(!apiKey.isNullOrBlank()) { "Can nhap credential cho ${definition.displayName}." }
        }
        if (definition.authType == VoiceProviderAuthType.API_KEY_AND_REGION) {
            require(!region.isNullOrBlank()) { "Can nhap region cho ${definition.displayName}." }
        }
        if (definition.authType == VoiceProviderAuthType.NONE) {
            require(apiKey.isNullOrBlank()) { "Provider local khong nhan API key." }
        }
        if (!model.isNullOrBlank()) {
            require(model.length <= 128 && !model.contains("://") && !model.contains("\\") && !model.contains("{") && !model.contains("}")) {
                "Model voice provider khong hop le."
            }
        }
    }

    private fun updateImageProviderFailureState(
        providerId: String,
        error: ImageProviderException
    ) {
        when (error.code) {
            ImageProviderErrorCode.INVALID_API_KEY,
            ImageProviderErrorCode.INVALID_API_TOKEN_OR_ACCOUNT_ACCESS,
            ImageProviderErrorCode.MODEL_NOT_AVAILABLE,
            ImageProviderErrorCode.MODEL_ACCESS_REQUIRED,
            ImageProviderErrorCode.ACCOUNT_VERIFICATION_REQUIRED -> {
                credentialStore.markImageProviderState(
                    providerId,
                    AutomationCredentialStore.STATE_INVALID,
                    error.message
                )
            }

            else -> {
                val current = credentialStore.getImageProviderConfigurationStatus(providerId)
                val fallbackState = current.state.takeIf {
                    it != AutomationCredentialStore.STATE_NOT_CONFIGURED
                } ?: AutomationCredentialStore.IMAGE_STATE_CONFIG_SAVED
                credentialStore.markImageProviderState(providerId, fallbackState, error.message)
            }
        }
    }

    private fun VoiceProviderCredentialConfiguration.toVoiceProviderConfig(): VoiceProviderConfig {
        return VoiceProviderConfig(
            providerId = providerId,
            locale = locale,
            voiceId = voiceId,
            model = model,
            speechRate = speechRate,
            pitch = pitch,
            outputFormat = outputFormat,
            engineName = engineName,
            apiKey = apiKey,
            region = region,
            credentialJson = credentialJson
        )
    }

    private fun updateVoiceProviderFailureState(
        providerId: String,
        error: VoiceProviderException
    ) {
        when (error.code) {
            VoiceProviderErrorCode.INVALID_API_KEY,
            VoiceProviderErrorCode.INVALID_ACCOUNT_CONFIGURATION,
            VoiceProviderErrorCode.INVALID_VOICE,
            VoiceProviderErrorCode.INVALID_SPEED,
            VoiceProviderErrorCode.INVALID_FORMAT,
            VoiceProviderErrorCode.MODEL_NOT_AVAILABLE,
            VoiceProviderErrorCode.MODEL_ACCESS_REQUIRED -> {
                credentialStore.markVoiceProviderState(
                    providerId,
                    AutomationCredentialStore.STATE_INVALID,
                    error.message
                )
            }

            VoiceProviderErrorCode.VOICE_NOT_INSTALLED -> {
                credentialStore.markVoiceProviderState(
                    providerId,
                    AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED,
                    error.message
                )
            }

            else -> {
                val current = credentialStore.getVoiceProviderConfigurationStatus(providerId)
                val fallbackState = current.state.takeIf {
                    it != AutomationCredentialStore.STATE_NOT_CONFIGURED
                } ?: AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED
                credentialStore.markVoiceProviderState(providerId, fallbackState, error.message)
            }
        }
    }

    private fun imageBindingMode(definition: ImageProviderDefinition?): String {
        return when {
            definition == null -> "not_configured"
            definition.health == ImageProviderHealth.NOT_IMPLEMENTED -> "not_implemented"
            else -> "real"
        }
    }

    private fun mapVoiceWaitingReason(code: VoiceProviderErrorCode): String {
        return when (code) {
            VoiceProviderErrorCode.NOT_CONFIGURED -> "PENDING_USER_VOICE_CONFIGURATION"
            VoiceProviderErrorCode.VOICE_NOT_INSTALLED -> "NO_VIETNAMESE_VOICE_INSTALLED"
            VoiceProviderErrorCode.LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
            VoiceProviderErrorCode.INVALID_API_KEY -> "INVALID_API_KEY_OR_MODEL_ACCESS"
            VoiceProviderErrorCode.INVALID_ACCOUNT_CONFIGURATION -> "INVALID_ACCOUNT_CONFIGURATION"
            VoiceProviderErrorCode.INVALID_VOICE -> "INVALID_VOICE"
            VoiceProviderErrorCode.INVALID_SPEED -> "INVALID_SPEED"
            VoiceProviderErrorCode.INVALID_FORMAT -> "INVALID_FORMAT"
            VoiceProviderErrorCode.TEXT_TOO_LONG -> "TEXT_TOO_LONG"
            VoiceProviderErrorCode.MODEL_ACCESS_REQUIRED -> "MODEL_ACCESS_REQUIRED"
            VoiceProviderErrorCode.MODEL_NOT_AVAILABLE -> "MODEL_NOT_AVAILABLE"
            VoiceProviderErrorCode.QUOTA_OR_BILLING_REQUIRED -> "QUOTA_OR_BILLING_REQUIRED"
            VoiceProviderErrorCode.DAILY_REQUEST_LIMIT -> "DAILY_REQUEST_LIMIT"
            VoiceProviderErrorCode.RATE_LIMITED -> "RATE_LIMITED"
            VoiceProviderErrorCode.NETWORK_TRANSIENT -> "NETWORK_TRANSIENT"
            VoiceProviderErrorCode.PROVIDER_BUSY -> "PROVIDER_BUSY"
            VoiceProviderErrorCode.PROVIDER_UNAVAILABLE -> "PROVIDER_UNAVAILABLE"
            VoiceProviderErrorCode.TIMEOUT -> "VOICE_PROVIDER_TIMEOUT"
            VoiceProviderErrorCode.PROVIDER_TIMEOUT -> "PROVIDER_TIMEOUT"
            VoiceProviderErrorCode.AUDIO_URL_INVALID -> "AUDIO_URL_INVALID"
            VoiceProviderErrorCode.AUDIO_DOWNLOAD_FAILED -> "AUDIO_DOWNLOAD_FAILED"
            VoiceProviderErrorCode.USER_ACTION_REQUIRED -> "USER_ACTION_REQUIRED"
            VoiceProviderErrorCode.INVALID_RESPONSE -> "INVALID_RESPONSE"
            VoiceProviderErrorCode.INVALID_AUDIO -> "INVALID_AUDIO"
            VoiceProviderErrorCode.CANCELLED -> "VOICE_PROVIDER_CANCELLED"
        }
    }

    private suspend fun ensureAutomationProject() {
        if (repository.getProject(AUTOMATION_PROJECT_ID) != null) {
            return
        }
        val now = System.currentTimeMillis()
        repository.createProject(
            AutomationProjectRecord(
                projectId = AUTOMATION_PROJECT_ID,
                name = "Automation Center",
                topicTemplate = "Tao noi dung tu chu de: {{topic}}",
                contentType = "text/plain",
                approvalPolicy = "REVIEW_BEFORE_POST",
                enabled = true,
                configJson = JSONObject()
                    .put("surface", "automation-center-shell")
                    .put("mode", "script-scene-asset-template-stage")
                    .put("publishMode", DEFAULT_PUBLISH_MODE)
                    .toString(),
                configSchemaVersion = 1,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                deletedAtEpochMs = null
            )
        )
    }

    private suspend fun ensureAutomationWorkflow() {
        if (repository.getWorkflowDefinition(AUTOMATION_WORKFLOW_ID, AUTOMATION_WORKFLOW_VERSION) != null) {
            return
        }
        repository.saveWorkflowDefinition(
            AutomationWorkflowDefinitionRecord(
                workflowId = AUTOMATION_WORKFLOW_ID,
                workflowVersion = AUTOMATION_WORKFLOW_VERSION,
                status = "ACTIVE",
                minimumAppVersionCode = 0,
                definitionSchemaVersion = 1,
                stepContractJson = JSONObject()
                    .put(
                        "steps",
                        JSONArray()
                            .put("TOPIC")
                            .put("CONTENT")
                            .put("SCENE_PROMPTS")
                            .put("ASSET_PLAN")
                            .put("IMAGES_VISUALS")
                            .put("VOICE")
                            .put("SUBTITLE")
                            .put("VIDEO")
                            .put("METADATA")
                            .put("REVIEW")
                            .put("PUBLISH")
                    )
                    .toString(),
                dependencyContractJson = JSONObject()
                    .put(
                        "edges",
                        JSONArray()
                            .put(JSONArray().put("TOPIC").put("CONTENT"))
                            .put(JSONArray().put("CONTENT").put("SCENE_PROMPTS"))
                            .put(JSONArray().put("SCENE_PROMPTS").put("ASSET_PLAN"))
                            .put(JSONArray().put("ASSET_PLAN").put("IMAGES_VISUALS"))
                            .put(JSONArray().put("IMAGES_VISUALS").put("VOICE"))
                            .put(JSONArray().put("VOICE").put("SUBTITLE"))
                            .put(JSONArray().put("SUBTITLE").put("VIDEO"))
                            .put(JSONArray().put("VIDEO").put("METADATA"))
                            .put(JSONArray().put("METADATA").put("REVIEW"))
                            .put(JSONArray().put("REVIEW").put("PUBLISH"))
                    )
                    .toString(),
                seededFromAppVersionCode = BuildConfig.VERSION_CODE,
                insertedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun requireStepId(
        steps: List<AutomationStepRecord>,
        stepType: String
    ): String {
        return steps.firstOrNull { it.stepType == stepType }?.stepId
            ?: throw IllegalStateException("Workflow missing step $stepType.")
    }

    private fun connectorBinding(
        bindingId: String,
        projectId: String,
        jobId: String,
        connectorId: String,
        category: String,
        mode: String,
        createdAtEpochMs: Long
    ): AutomationConnectorBindingRecord {
        return AutomationConnectorBindingRecord(
            bindingId = bindingId,
            bindingScope = "JOB",
            projectId = projectId,
            jobId = jobId,
            connectorId = connectorId,
            connectorVersion = 1,
            category = category,
            configSchemaVersion = 1,
            configJson = JSONObject()
                .put("mode", mode)
                .put("connectorId", connectorId)
                .toString(),
            capabilitySnapshotJson = JSONObject()
                .put("category", category)
                .put("mode", mode)
                .toString(),
            enabled = true,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    private fun step(
        stepId: String,
        jobId: String,
        stepKey: String,
        stepType: String,
        connectorBindingId: String
    ): AutomationStepRecord {
        return AutomationStepRecord(
            stepId = stepId,
            jobId = jobId,
            stepKey = stepKey,
            stepType = stepType,
            connectorBindingId = connectorBindingId,
            status = AutomationStepStatus.PENDING.name,
            attemptCount = 0,
            nextRetryAtEpochMs = null,
            startedAtEpochMs = null,
            completedAtEpochMs = null,
            errorCategory = null,
            redactedErrorDetail = null,
            executionLeaseOwner = null,
            executionLeaseExpiresAtEpochMs = null,
            executionLeaseBootMarker = null,
            revision = 0L,
            waitingReason = "WAITING_FOR_LOCAL_RUNTIME"
        )
    }

    private fun dependency(
        dependencyId: String,
        jobId: String,
        fromStepId: String,
        toStepId: String
    ): AutomationStepDependencyRecord {
        return AutomationStepDependencyRecord(
            dependencyId = dependencyId,
            jobId = jobId,
            fromStepId = fromStepId,
            toStepId = toStepId,
            dependencyKind = "SUCCESS",
            conditionType = "ALWAYS",
            conditionParamJson = null,
            allowSkippedUpstream = false
        )
    }

    private fun shortId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun AutomationContentRunRequest.normalized(): AutomationContentRunRequest {
        val normalizedTopic = topic.trim().replace(Regex("\\s+"), " ")
        val normalizedLanguage = language.trim().ifEmpty { "vi" }
        val normalizedContentType = contentType.trim().ifEmpty { DEFAULT_CONTENT_TYPE }
        val durationPolicy = ContentDurationPolicy.fromTopic(
            topic = normalizedTopic,
            desiredDurationSeconds = desiredDurationSeconds
        )
        require(normalizedTopic.isNotBlank()) { "Topic is required." }
        require(normalizedTopic.length <= MAX_AUTOMATION_CONTENT_LENGTH) { "Topic is too long." }
        require(maximumOutputLength in MIN_OUTPUT_LENGTH..MAX_OUTPUT_LENGTH) {
            "Maximum output length is outside the safe range."
        }
        require(desiredDurationSeconds == null || desiredDurationSeconds in 5..7_200) {
            "Desired duration is outside the safe range."
        }
        require(requestedSceneCount == null || requestedSceneCount in 1..24) {
            "Requested scene count is outside the safe range."
        }
        // MOT logic duy nhat cho MOI thoi luong: KHONG con bom maximumOutputLength theo
        // moc thoi luong (truoc day >=120 -> 16000 chinh la "case an" khien >=120s hong).
        // Thoi luong chi la con so target_duration_ms; giu nguyen maximumOutputLength.
        val adjustedMaximumOutputLength = maximumOutputLength
        return copy(
            topic = normalizedTopic,
            language = normalizedLanguage,
            contentType = normalizedContentType,
            promptTemplate = promptTemplate.trim(),
            maximumOutputLength = adjustedMaximumOutputLength,
            desiredDurationSeconds = desiredDurationSeconds,
            requestedSceneCount = requestedSceneCount,
            aspectRatio = normalizeAspectRatio(aspectRatio),
            clientRequestId = clientRequestId?.trim()?.ifBlank { null },
            videoRendererMode = normalizeVideoRendererMode(videoRendererMode),
            videoWorkerUrl = videoWorkerUrl?.trim()?.ifBlank { null },
            videoQualityTier = normalizeVideoQualityTier(videoQualityTier),
            videoBackgroundMode = normalizeVideoBackgroundMode(videoBackgroundMode),
            videoMotionMode = normalizeVideoMotionMode(videoMotionMode),
            backgroundMusicFilePath = backgroundMusicFilePath?.trim()?.ifBlank { null },
            backgroundMusicVolume = backgroundMusicVolume.coerceIn(0f, 2f),
            videoSubtitleColor = normalizeVideoSubtitleColor(videoSubtitleColor)
        )
    }

    private fun normalizeVideoQualityTier(value: String): String {
        return if (value.trim().equals("720p", ignoreCase = true)) "720p" else "1080p"
    }

    /** Rut TIEU DE ngan gon tu chu de nguoi dung nhap (KHONG hot ca mo ta dai). */
    private fun deriveVideoTitle(topic: String): String {
        val normalized = topic.replace("\r", "\n").trim()
        if (normalized.isEmpty()) return ""
        // Uu tien dong "Chu de:" neu co; neu khong lay dong/cau dau tien.
        val firstLine = normalized.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        val candidate = firstLine.substringAfter("chu de:", firstLine)
            .substringAfter("Chủ đề:", firstLine)
            .trim()
            .ifBlank { firstLine }
        // Cat theo cau/gioi han ~70 ky tu de la TIEU DE, khong phai ca doan mo ta.
        val bySentence = candidate.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim().orEmpty().ifBlank { candidate }
        return if (bySentence.length <= 70) bySentence else bySentence.take(67).trimEnd() + "..."
    }

    private fun normalizeVideoSubtitleColor(value: String): String {
        val v = value.trim()
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) v.uppercase() else "#FFFFFF"
    }

    private fun normalizeVideoBackgroundMode(value: String): String {
        return if (value.trim().equals("black_bars", ignoreCase = true)) "black_bars" else "blurred_fill"
    }

    private fun normalizeVideoMotionMode(value: String): String {
        return when (value.trim().lowercase()) {
            "zoom_in", "zoom_out", "pan_left_to_right", "pan_right_to_left", "none" -> value.trim().lowercase()
            else -> "auto_mix"
        }
    }

    private fun normalizeAspectRatio(value: String): String {
        val normalized = value.trim()
        return if (normalized in SUPPORTED_ASPECT_RATIOS) normalized else DEFAULT_TARGET_ASPECT_RATIO
    }

    /**
     * Dung cho nguon noi dung "Gemini web" (khong qua API): van ban da lay san tu
     * phien Gemini web (WebView) duoc bien thanh ContentGenerationResult y het
     * dinh dang duong API tra ve, de toan bo pipeline phia sau (chia canh, anh,
     * giong, video) chay lai HOAN TOAN giong nhau, khong phai viet lai gi ca.
     */
    private fun buildContentResultFromRawText(
        rawText: String,
        durationPolicy: ContentDurationPolicy,
        providerId: String,
        model: String
    ): ContentGenerationResult {
        require(rawText.isNotBlank()) { "Gemini web chua tra ve noi dung nao." }
        // Luon thu parse JSON co cau truc (Gemini web dung chung prompt yeu cau JSON
        // voi duong API, xem buildWebContentPrompt) - khong con gioi han chi cho
        // listicle, de Gemini tu quyet so canh cho MOI loai noi dung.
        //
        // Duong web KHONG co vong lap kiem tra du noi dung + tu retry nhu duong API
        // (generateCanonicalContent/evaluateContentSufficiency) - cao JSON tu trang
        // web kem tin cay hon API that (de bi cat cut/parse thieu). Neu JSON parse
        // duoc nhung qua ngan so voi chinh rawText (vd chi bat duoc 1 doan do JSON
        // bi cat), uu tien dung rawText tho thay vi cau truc thieu, tranh mat phan
        // lon noi dung that Gemini da viet.
        val structured = StructuredScriptParser.parse(rawText, durationPolicy)
        // Neu response CO DANG JSON ("{...") thi LUON dung structured da parse -
        // tuyet doi khong duoc do raw JSON vao narration (se lot key "voiceText"/
        // "visualQuery"/"onScreenText"... vao ca giong doc lan phu de).
        // StructuredScriptParser da tu xu ly JSON day du / thieu / cat cut qua 3
        // tang parse (parseJson -> parseJsonLike regex tung item -> parseFallback),
        // nen o day khong so mat noi dung.
        //
        // Guard so-sanh so tu (chong mat noi dung khi JSON bi cat) chi con ap dung
        // cho truong hop rawText la VAN XUOI thuc su (khong bat dau bang "{") - luc
        // do so tu structured vs raw moi cung don vi de so. Truoc day so structured
        // (chi voiceText) voi raw (ca key JSON + visualQuery + onScreenText...) la
        // sai don vi, khien listicle JSON hop le bi vut bo oan -> lot key.
        val isJsonShaped = rawText.trim().startsWith("{")
        val structuredWordCount = structured?.fullVoiceText()?.let(::countNarrationWords) ?: 0
        val rawWordCount = countNarrationWords(rawText)
        val useStructured = structured != null &&
            (isJsonShaped || rawWordCount == 0 || structuredWordCount >= rawWordCount / 2)
        // Khi dung structured: generatedText = narration SACH (fullVoiceText) giong
        // het duong API (GeminiContentConnector), khong luu raw JSON - de JSON khong
        // lot sang metadata/hien thi transcript. Chi giu raw khi that su la van xuoi.
        val cleanNarration = structured?.fullVoiceText()?.takeUnless { it.isNullOrBlank() }
        val generatedText = if (useStructured && cleanNarration != null) cleanNarration else rawText
        return ContentGenerationResult(
            generatedText = generatedText,
            providerId = providerId,
            model = model,
            structuredScript = if (useStructured) structured else null
        )
    }

    private fun AutomationJobGraphSnapshot.toUiSnapshot(
        topicFallback: String? = null,
        runtimeJob: RuntimeAutomationJob? = null
    ): AutomationUiJobSnapshot {
        val topic = resolveTopicFromOutbox().ifBlank { topicFallback.orEmpty() }
        val sortedSteps = steps
            .sortedBy { STEP_DISPLAY_ORDER[it.stepType] ?: Int.MAX_VALUE }
            .map { step ->
                val override = runtimeJob?.stepOverrides?.get(step.stepType)
                AutomationUiStepSnapshot(
                    stepId = step.stepId,
                    stepKey = step.stepKey,
                    stepType = step.stepType,
                    status = override?.status ?: step.status,
                    connectorBindingId = step.connectorBindingId,
                    waitingReason = override?.waitingReason ?: step.waitingReason
                )
            }

        return AutomationUiJobSnapshot(
            jobId = job.jobId,
            projectId = job.projectId,
            workflowId = job.workflowId,
            workflowVersion = job.workflowVersion,
            topic = topic,
            status = runtimeJob?.status ?: job.status,
            createdAtEpochMs = job.createdAtEpochMs,
            publishMode = resolvePublishModeFromOutbox().ifBlank { DEFAULT_PUBLISH_MODE },
            steps = sortedSteps,
            dependencies = dependencies.map { dependency ->
                AutomationUiDependencySnapshot(
                    dependencyId = dependency.dependencyId,
                    fromStepId = dependency.fromStepId,
                    toStepId = dependency.toStepId
                )
            },
            generatedText = runtimeJob?.generatedText,
            providerId = runtimeJob?.contentProviderId,
            model = runtimeJob?.contentModel,
            requestId = runtimeJob?.requestId,
            usageMetadata = runtimeJob?.usageMetadata.orEmpty(),
            scenePrompts = runtimeJob?.let { rj ->
                val plans = rj.assetPlans.associateBy { it.sceneId }
                rj.scenePrompts.map { it.toUiScenePrompt(sceneImageStatus(rj, it, plans)) }
            }.orEmpty(),
            assetPlans = runtimeJob?.assetPlans?.map { it.toUiAssetPlan() }.orEmpty(),
            videoRenderPlan = runtimeJob?.videoRenderPlan?.toUiVideoRenderPlan(),
            metadataPlan = runtimeJob?.metadataPlan,
            reviewState = runtimeJob?.reviewState,
            publishPlan = runtimeJob?.publishPlan,
            artifacts = runtimeJob?.artifacts?.map { it.toUiArtifact() }.orEmpty(),
            runtimeMessage = runtimeJob?.runtimeMessage,
            voiceStatus = runtimeJob?.let { jobVoiceStatus(it) } ?: "MISSING",
            videoStatus = runtimeJob?.let { jobVideoStatus(it) } ?: "MISSING"
        )
    }

    private fun ScenePrompt.toUiScenePrompt(imageStatus: String = "READY"): AutomationUiScenePromptSnapshot {
        return AutomationUiScenePromptSnapshot(
            sceneId = sceneId,
            ordinal = ordinal,
            summary = summary,
            visualPrompt = visualPrompt,
            negativePrompt = negativePrompt,
            aspectRatio = aspectRatio,
            voiceText = voiceText,
            onScreenText = onScreenText,
            plannedDurationMs = plannedDurationMs,
            stockSearchQuery = stockSearchQuery,
            visualDirection = visualDirection,
            imageStatus = imageStatus
        )
    }

    private fun resolveImagePrompt(
        scene: ScenePrompt,
        assetPlan: VisualAssetPlan?
    ): String {
        return listOf(
            // Uu tien stockSearchQuery TRUOC (nguoi dung co the sua tay tu khoa nay)
            // roi moi den asset plan tu dong.
            scene.stockSearchQuery.takeIf { it.isNotBlank() },
            assetPlan?.assetQuery,
            scene.visualDirection.takeIf { it.isNotBlank() },
            scene.visualPrompt
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }

    // ------------------------------------------------------------------
    // STALE / chu ky dau vao: biet phan nao (anh/giong/video) da cu sau khi
    // nguoi dung sua canh, de chi tao lai dung phan do - KHONG goi lai
    // Gemini/Pinterest, KHONG dung sach anh cu.
    // ------------------------------------------------------------------
    private fun shortSignature(raw: String): String {
        return runCatching {
            val digest = java.security.MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }.take(12)
        }.getOrElse { raw.hashCode().toString() }
    }

    private fun currentImageSignature(scene: ScenePrompt, assetPlan: VisualAssetPlan?): String =
        shortSignature("q=" + resolveImagePrompt(scene, assetPlan))

    private fun currentVoiceSignature(job: RuntimeAutomationJob): String {
        val body = job.scenePrompts.sortedBy { it.ordinal }
            .joinToString("~") { "${it.sceneId}:${it.voiceText}" }
        return shortSignature("v=${job.voiceProviderId}|${job.voiceId}|$body")
    }

    private fun currentVideoSignature(job: RuntimeAutomationJob): String {
        val imageBySceneId = job.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .associateBy { it.sceneId!! }
        val body = job.scenePrompts.sortedBy { it.ordinal }.joinToString("~") { s ->
            "${s.sceneId}:${s.ordinal}:${s.onScreenText}:${imageBySceneId[s.sceneId]?.artifactId.orEmpty()}"
        }
        return shortSignature(
            "vid=$body|${job.videoBackgroundMode}|${job.videoMotionMode}|${job.videoQualityTier}|${job.videoSubtitleColor}|${job.backgroundMusicFilePath}"
        )
    }

    /**
     * Dong dau "moi" cho toan bo chu ky: goi sau khi vua tao lai giong + video tu
     * du lieu HIEN TAI (pipeline day du / retry voice / retry video). Moi canh co
     * anh se duoc dong dau imageSignature theo dau vao hien tai; job duoc dong dau
     * voice/video signature. Sau do neu nguoi dung sua canh, hash lech -> STALE.
     */
    private fun stampFreshSignatures(job: RuntimeAutomationJob): RuntimeAutomationJob {
        val plans = job.assetPlans.associateBy { it.sceneId }
        val imagedSceneIds = job.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .mapNotNull { it.sceneId }.toSet()
        val stampedScenes = job.scenePrompts.map { s ->
            if (s.sceneId in imagedSceneIds) s.copy(imageSignature = currentImageSignature(s, plans[s.sceneId]))
            else s.copy(imageSignature = "")
        }
        val withScenes = job.copy(scenePrompts = stampedScenes)
        return withScenes.copy(
            lastVoiceSignature = currentVoiceSignature(withScenes),
            lastVideoSignature = currentVideoSignature(withScenes)
        )
    }

    /**
     * Chi dong dau lai imageSignature cho cac canh vua co anh moi (import/cao/thay
     * anh 1 canh). KHONG dung voice/video signature -> nen chung tu dong STALE de
     * nguoi dung biet can render lai video.
     */
    private fun stampImageSignatures(job: RuntimeAutomationJob, sceneIds: Set<String>): RuntimeAutomationJob {
        if (sceneIds.isEmpty()) return job
        val plans = job.assetPlans.associateBy { it.sceneId }
        val stamped = job.scenePrompts.map { s ->
            if (s.sceneId in sceneIds) s.copy(imageSignature = currentImageSignature(s, plans[s.sceneId])) else s
        }
        return job.copy(scenePrompts = stamped)
    }

    private fun sceneImageStatus(job: RuntimeAutomationJob, scene: ScenePrompt, plans: Map<String, VisualAssetPlan>): String {
        val hasImage = job.artifacts.any { it.artifactType == "IMAGE" && it.sceneId == scene.sceneId }
        if (!hasImage) return "MISSING"
        if (scene.imageSignature.isBlank()) return "READY" // job cu chua co chu ky -> khong bao dong gia
        return if (scene.imageSignature == currentImageSignature(scene, plans[scene.sceneId])) "READY" else "STALE"
    }

    private fun jobVoiceStatus(job: RuntimeAutomationJob): String {
        val hasVoice = job.artifacts.any { it.artifactType == "VOICE" }
        if (!hasVoice) return "MISSING"
        if (job.lastVoiceSignature.isBlank()) return "READY"
        return if (job.lastVoiceSignature == currentVoiceSignature(job)) "READY" else "STALE"
    }

    private fun jobVideoStatus(job: RuntimeAutomationJob): String {
        val hasVideo = job.artifacts.any { it.artifactType == "VIDEO_MP4" }
        if (!hasVideo) return "MISSING"
        // Video phu thuoc giong + anh: neu giong cu, hoac co anh thieu/cu -> video cu.
        if (jobVoiceStatus(job) == "STALE") return "STALE"
        val plans = job.assetPlans.associateBy { it.sceneId }
        val anyImageStale = job.scenePrompts.any {
            val st = sceneImageStatus(job, it, plans); st == "STALE" || st == "MISSING"
        }
        if (anyImageStale) return "STALE"
        if (job.lastVideoSignature.isBlank()) return "READY"
        return if (job.lastVideoSignature == currentVideoSignature(job)) "READY" else "STALE"
    }

    private fun buildImageArtifactDebugSource(
        query: String,
        providerRequestId: String?
    ): String {
        val compactQuery = query.replace(';', ',').replace(Regex("\\s+"), " ").trim()
        return listOfNotNull(
            "query=$compactQuery".takeIf { compactQuery.isNotBlank() },
            providerRequestId?.takeIf { it.isNotBlank() }
        ).joinToString(";")
    }

    private suspend fun executeMetadataStage(
        jobId: String,
        stepId: String,
        topic: String,
        generatedText: String,
        scenePrompts: List<ScenePrompt>,
        assetPlans: List<VisualAssetPlan>,
        artifacts: List<AutomationSavedArtifact>
    ): MetadataStageOutcome {
        val videoArtifact = artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" }
            ?: return MetadataStageOutcome(
                plan = null,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.NOT_CONFIGURED.name,
                waitingReason = "METADATA_WAITING_FOR_VIDEO_MP4"
            )
        val voiceArtifact = artifacts.firstOrNull { it.artifactType == "VOICE" }
        val request = MetadataGenerationRequest(
            topic = topic,
            generatedText = generatedText,
            sceneCount = scenePrompts.size,
            imageCount = artifacts.count { it.artifactType == "IMAGE" },
            voiceDurationMs = extractDebugLong(voiceArtifact?.sourceUrl, "durationMs") ?: 0L,
            videoDurationMs = extractDebugLong(videoArtifact.sourceUrl, "durationMs") ?: 0L,
            language = "vi"
        )
        val plan = metadataGenerator.generate(request)
        val artifact = artifactStore.saveMetadataPlanArtifact(
            jobId = jobId,
            stepId = stepId,
            json = metadataPlanToJson(plan).toString()
        )
        return MetadataStageOutcome(
            plan = plan,
            artifacts = listOfNotNull(artifact),
            stepStatus = AutomationStepStatus.COMPLETED.name,
            waitingReason = "METADATA_PLAN_READY"
        )
    }

    private suspend fun executeReviewStage(
        jobId: String,
        stepId: String,
        metadataPlan: MetadataPlan?,
        scenePrompts: List<ScenePrompt>,
        artifacts: List<AutomationSavedArtifact>
    ): ReviewStageOutcome {
        if (metadataPlan == null) {
            return ReviewStageOutcome(
                state = null,
                artifacts = emptyList(),
                stepStatus = AutomationStepStatus.WAITING_USER.name,
                waitingReason = "REVIEW_WAITING_FOR_METADATA"
            )
        }
        val reviewState = buildReviewState(metadataPlan, scenePrompts, artifacts)
        val artifact = artifactStore.saveReviewStateArtifact(
            jobId = jobId,
            stepId = stepId,
            json = reviewStateToJson(reviewState).toString()
        )
        return ReviewStageOutcome(
            state = reviewState,
            artifacts = listOfNotNull(artifact),
            stepStatus = AutomationStepStatus.WAITING_USER.name,
            waitingReason = "REVIEW_WAITING_FOR_APPROVAL"
        )
    }

    private fun buildReviewState(
        metadataPlan: MetadataPlan,
        scenePrompts: List<ScenePrompt>,
        artifacts: List<AutomationSavedArtifact>
    ): ReviewState {
        val videoArtifact = artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" }
        val voiceArtifact = artifacts.firstOrNull { it.artifactType == "VOICE" }
        val imageCount = artifacts.count { it.artifactType == "IMAGE" }
        val voiceDurationMs = extractDebugLong(voiceArtifact?.sourceUrl, "durationMs") ?: 0L
        val videoDurationMs = extractDebugLong(videoArtifact?.sourceUrl, "durationMs") ?: 0L
        val checks = listOf(
            ReviewCheck("video_mp4", "Có VIDEO_MP4", videoArtifact != null, if (videoArtifact != null) "Video artifact đã sẵn sàng." else "Thiếu VIDEO_MP4."),
            ReviewCheck("metadata_plan", "Có METADATA_PLAN", true, "Metadata plan đã được tạo."),
            ReviewCheck("scene_count", "Có scene", scenePrompts.isNotEmpty(), "Scene count = ${scenePrompts.size}."),
            ReviewCheck("image_count", "Có image", imageCount > 0, "Image count = $imageCount."),
            ReviewCheck("video_duration", "Video duration > 0", videoDurationMs > 0L, "Video duration = ${videoDurationMs}ms."),
            ReviewCheck("voice_duration", "Voice duration > 0", voiceDurationMs > 0L, "Voice duration = ${voiceDurationMs}ms."),
            ReviewCheck("title", "Title không rỗng", metadataPlan.title.isNotBlank(), metadataPlan.title),
            ReviewCheck("description", "Description không rỗng", metadataPlan.description.isNotBlank(), metadataPlan.description.take(120)),
            ReviewCheck("hashtags", "Hashtags không rỗng", metadataPlan.hashtags.isNotEmpty(), metadataPlan.hashtags.joinToString(" "))
        )
        val warnings = mutableListOf<String>()
        if (scenePrompts.size <= 1) warnings += "Số scene quá thấp."
        if (imageCount < scenePrompts.size) warnings += "Image count ít hơn scene count."
        if (videoDurationMs in 1 until 10_000L) warnings += "Video khá ngắn dưới 10 giây."
        if (metadataPlan.title.length > 70) warnings += "Metadata title khá dài."
        val subtitleArtifact = artifacts.firstOrNull { it.artifactType == "SUBTITLE" }
        if (subtitleArtifact == null) warnings += "Chưa có subtitle thật, hiện mới dừng ở plan hoặc render text."
        return ReviewState(
            status = "WAITING_USER",
            checks = checks,
            warnings = warnings
        )
    }

    private fun buildPublishPlan(
        runtimeJob: RuntimeAutomationJob,
        reviewState: ReviewState
    ): PublishPlan {
        val videoArtifact = runtimeJob.artifacts.firstOrNull { it.artifactType == "VIDEO_MP4" }
            ?: throw IllegalArgumentException("Job nay chua co VIDEO_MP4.")
        val metadataArtifact = runtimeJob.artifacts.firstOrNull { it.artifactType == "METADATA_PLAN" }
            ?: throw IllegalArgumentException("Job nay chua co METADATA_PLAN.")
        return PublishPlan(
            status = "READY",
            targets = listOf("youtube_shorts", "tiktok", "facebook_reels"),
            videoArtifactUri = videoArtifact.uri,
            metadataArtifactUri = metadataArtifact.uri,
            reviewStatus = reviewState.status,
            publishMode = "ANDROID_SHARE_SHEET",
            createdAtEpochMs = System.currentTimeMillis(),
            notes = listOf("Publish MVP dùng share sheet/manual assisted, chưa auto-upload API.")
        )
    }

    private fun buildPublishShareText(metadataPlan: MetadataPlan?): String {
        if (metadataPlan == null) return ""
        return buildString {
            append(metadataPlan.title)
            append("\n\n")
            append(metadataPlan.description)
            if (metadataPlan.hashtags.isNotEmpty()) {
                append("\n\n")
                append(metadataPlan.hashtags.joinToString(" "))
            }
        }
    }

    private fun VisualAssetPlan.toUiAssetPlan(): AutomationUiAssetPlanSnapshot {
        return AutomationUiAssetPlanSnapshot(
            sceneId = sceneId,
            ordinal = ordinal,
            strategy = strategy,
            preferredProviderId = preferredProviderId,
            assetQuery = assetQuery,
            templateId = templateId,
            renderMode = renderMode,
            durationMs = durationMs,
            rationale = rationale
        )
    }

    private fun VideoRenderPlan.toUiVideoRenderPlan(): AutomationUiVideoRenderPlanSnapshot {
        return AutomationUiVideoRenderPlanSnapshot(
            rendererId = rendererId,
            planVersion = planVersion,
            renderTarget = renderTarget,
            width = width,
            height = height,
            fps = fps,
            voiceArtifactUri = voiceArtifactUri,
            voiceMimeType = voiceMimeType,
            sceneCount = sceneCount,
            totalDurationMs = totalDurationMs,
            handoffHints = handoffHints,
            scenes = scenes.map { scene ->
                AutomationUiVideoRenderSceneSnapshot(
                    sceneId = scene.sceneId,
                    ordinal = scene.ordinal,
                    summary = scene.summary,
                    visualPrompt = scene.visualPrompt,
                    imageArtifactUri = scene.imageArtifactUri,
                    imageArtifactId = scene.imageArtifactId,
                    renderMode = scene.renderMode,
                    templateId = scene.templateId,
                    strategy = scene.strategy,
                    durationMs = scene.durationMs,
                    subtitleText = scene.subtitleText
                )
            }
        )
    }

    private fun AutomationExportedArtifact.toUiVideoExportResult(): AutomationVideoExportResult {
        return AutomationVideoExportResult(
            displayName = displayName,
            mimeType = mimeType,
            contentUri = contentUri,
            displayPath = displayPath,
            sizeBytes = sizeBytes
        )
    }

    private fun AutomationSavedArtifact.toUiArtifact(): AutomationUiArtifactSnapshot {
        return AutomationUiArtifactSnapshot(
            artifactId = artifactId,
            artifactType = artifactType,
            mimeType = mimeType,
            uri = uri,
            sizeBytes = sizeBytes,
            sourceUrl = sourceUrl,
            sceneId = sceneId,
            ordinal = ordinal,
            providerRequestId = providerRequestId,
            previewDataUrl = previewDataUrl
        )
    }

    private fun metadataPlanToJson(plan: MetadataPlan): JSONObject {
        return JSONObject()
            .put("schema", plan.schema)
            .put("title", plan.title)
            .put("shortTitle", plan.shortTitle)
            .put("description", plan.description)
            .put("hashtags", JSONArray(plan.hashtags))
            .put("language", plan.language)
            .put("category", plan.category)
            .put("thumbnailText", plan.thumbnailText)
            .put(
                "platforms",
                JSONObject().apply {
                    plan.platforms.forEach { (key, value) ->
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
            .put("sourceSceneCount", plan.sourceSceneCount)
            .put("sourceVoiceDurationMs", plan.sourceVoiceDurationMs)
            .put("sourceVideoDurationMs", plan.sourceVideoDurationMs)
            .put("safetyNotes", JSONArray(plan.safetyNotes))
    }

    private fun reviewStateToJson(state: ReviewState): JSONObject {
        return JSONObject()
            .put("schema", state.schema)
            .put("status", state.status)
            .put(
                "checks",
                JSONArray().apply {
                    state.checks.forEach { check ->
                        put(
                            JSONObject()
                                .put("key", check.key)
                                .put("label", check.label)
                                .put("passed", check.passed)
                                .put("detail", check.detail)
                        )
                    }
                }
            )
            .put("warnings", JSONArray(state.warnings))
            .put("approvedAtEpochMs", state.approvedAtEpochMs)
            .put("rejectedAtEpochMs", state.rejectedAtEpochMs)
            .put("rejectedReason", state.rejectedReason)
    }

    private fun publishPlanToJson(plan: PublishPlan): JSONObject {
        return JSONObject()
            .put("schema", plan.schema)
            .put("status", plan.status)
            .put("targets", JSONArray(plan.targets))
            .put("videoArtifactUri", plan.videoArtifactUri)
            .put("metadataArtifactUri", plan.metadataArtifactUri)
            .put("reviewStatus", plan.reviewStatus)
            .put("publishMode", plan.publishMode)
            .put("createdAtEpochMs", plan.createdAtEpochMs)
            .put("publishedAtEpochMs", plan.publishedAtEpochMs)
            .put("notes", JSONArray(plan.notes))
    }

    private fun AutomationJobGraphSnapshot.toRecentJob(): AutomationUiRecentJob {
        return AutomationUiRecentJob(
            jobId = job.jobId,
            topic = resolveTopicFromOutbox(),
            status = runtimeJobs[job.jobId]?.status ?: job.status,
            createdAtEpochMs = job.createdAtEpochMs
        )
    }

    private fun AutomationJobGraphSnapshot.resolveTopicFromOutbox(): String {
        val payload = outboxEvents.firstOrNull { it.eventType == "JOB_CREATED" }?.payloadJson ?: return ""
        return runCatching { JSONObject(payload).optString("topic") }.getOrDefault("")
    }

    private fun AutomationJobGraphSnapshot.resolvePublishModeFromOutbox(): String {
        val payload = outboxEvents.firstOrNull { it.eventType == "JOB_CREATED" }?.payloadJson ?: return ""
        return runCatching { JSONObject(payload).optString("publishMode") }.getOrDefault("")
    }

    private fun mapImageWaitingReason(code: ImageProviderErrorCode): String {
        return when (code) {
            ImageProviderErrorCode.NOT_CONFIGURED -> "PENDING_USER_IMAGE_CONFIGURATION"
            ImageProviderErrorCode.INVALID_API_KEY -> "INVALID_API_KEY"
            ImageProviderErrorCode.INVALID_API_TOKEN_OR_ACCOUNT_ACCESS -> "INVALID_API_TOKEN_OR_ACCOUNT_ACCESS"
            ImageProviderErrorCode.BILLING_REQUIRED -> "BILLING_REQUIRED"
            ImageProviderErrorCode.CREDIT_EXHAUSTED -> "CREDIT_EXHAUSTED"
            ImageProviderErrorCode.FREE_ALLOCATION_EXHAUSTED -> "FREE_ALLOCATION_EXHAUSTED"
            ImageProviderErrorCode.MODEL_ACCESS_REQUIRED -> "MODEL_ACCESS_REQUIRED"
            ImageProviderErrorCode.MODEL_NOT_AVAILABLE -> "MODEL_NOT_AVAILABLE"
            ImageProviderErrorCode.ACCOUNT_VERIFICATION_REQUIRED -> "ACCOUNT_VERIFICATION_REQUIRED"
            ImageProviderErrorCode.RATE_LIMITED -> "RATE_LIMITED"
            ImageProviderErrorCode.PROVIDER_UNAVAILABLE -> "PROVIDER_UNAVAILABLE"
            ImageProviderErrorCode.COMMUNITY_QUEUE_DELAY -> "COMMUNITY_QUEUE_DELAY"
            ImageProviderErrorCode.TIMEOUT -> "IMAGE_PROVIDER_TIMEOUT"
            ImageProviderErrorCode.USER_ACTION_REQUIRED -> "USER_ACTION_REQUIRED"
            ImageProviderErrorCode.INVALID_RESPONSE -> "INVALID_RESPONSE"
            ImageProviderErrorCode.INVALID_IMAGE -> "INVALID_IMAGE"
            ImageProviderErrorCode.CANCELLED -> "IMAGE_PROVIDER_CANCELLED"
        }
    }

    private fun inferRequestedSceneCount(
        topic: String,
        generatedScript: String
    ): Int {
        val candidates = listOf(topic, generatedScript).map(::normalizeSceneCountText)
        val explicitSceneCount = candidates.firstNotNullOfOrNull { text ->
            Regex("(\\d{1,2})\\s*(scene|canh)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (explicitSceneCount != null) {
            return explicitSceneCount.coerceIn(1, 20)
        }
        val listicleCount = candidates.firstNotNullOfOrNull { text ->
            Regex("(\\d{1,2})\\s*(cau|phan|muc)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (listicleCount != null) {
            val withIntroOutro = if (listicleCount >= 10) listicleCount + 2 else listicleCount
            return withIntroOutro.coerceIn(1, 20)
        }
        return DEFAULT_SCENE_COUNT
    }

    private fun normalizeSceneCountText(value: String): String {
        return buildString(value.length) {
            value.lowercase().forEach { character ->
                append(
                    when (character) {
                        'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                        'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                        'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                        'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                        'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                        'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                        'đ' -> 'd'
                        else -> character
                    }
                )
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractDebugLong(source: String?, key: String): Long? {
        val normalized = source.orEmpty()
        val prefix = "$key="
        return normalized.split(';')
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.toLongOrNull()
    }

    private data class WorkflowStepSpec(
        val stepKey: String,
        val stepType: String,
        val connectorId: String,
        val mode: String
    )

    private data class RuntimeAutomationJob(
        val status: String,
        val generatedText: String,
        val contentProviderId: String,
        val contentModel: String,
        val requestId: String?,
        val usageMetadata: Map<String, Long>,
        val scenePrompts: List<ScenePrompt>,
        val assetPlans: List<VisualAssetPlan>,
        val videoRenderPlan: VideoRenderPlan?,
        val metadataPlan: MetadataPlan?,
        val reviewState: ReviewState?,
        val publishPlan: PublishPlan?,
        val artifacts: List<AutomationSavedArtifact>,
        val runtimeMessage: String,
        val stepOverrides: Map<String, StepOverride>,
        val imageProviderId: String?,
        val imageModel: String?,
        val imageAttemptHistory: List<String>,
        val voiceProviderId: String?,
        val voiceId: String?,
        val voiceLocale: String?,
        val voiceAttemptHistory: List<String>,
        val videoRendererId: String?,
        val videoAttemptHistory: List<String>,
        val videoRendererMode: String,
        val videoWorkerUrl: String?,
        val videoQualityTier: String,
        val videoBackgroundMode: String,
        val videoMotionMode: String,
        val backgroundMusicFilePath: String?,
        val backgroundMusicLoop: Boolean,
        val backgroundMusicVolume: Float,
        val videoSubtitleColor: String = "#FFFFFF",
        // Chu ky dau vao GIONG DOC / VIDEO tai lan tao gan nhat. Khac hash hien
        // tai -> phan do da CU (STALE) sau khi nguoi dung sua canh. Rong = chua tao.
        val lastVoiceSignature: String = "",
        val lastVideoSignature: String = ""
    )

    private data class StepOverride(
        val status: String,
        val waitingReason: String
    )

    private data class ImageStageOutcome(
        val providerId: String?,
        val model: String?,
        val artifacts: List<AutomationSavedArtifact>,
        val stepStatus: String,
        val jobStatus: String,
        val waitingReason: String,
        val runtimeMessage: String
    )

    private data class VoiceStageOutcome(
        val providerId: String?,
        val voiceId: String?,
        val locale: String?,
        val artifacts: List<AutomationSavedArtifact>,
        val stepStatus: String,
        val jobStatus: String,
        val waitingReason: String,
        val runtimeMessage: String,
        // Thoi luong THAT do duoc cua tung canh (theo ordinal tang dan), da gom
        // khoang lang chuyen canh. Rong neu buoc voice chua thanh cong. Dung de
        // nhoi vao ScenePrompt.plannedDurationMs cho renderer dat anh dung theo
        // loi doc thay vi uoc luong cua Gemini.
        val sceneDurationsMs: List<Long> = emptyList()
    )

    private data class VideoStageOutcome(
        val rendererId: String?,
        val plan: VideoRenderPlan?,
        val artifacts: List<AutomationSavedArtifact>,
        val subtitleStepStatus: String,
        val subtitleWaitingReason: String,
        val videoStepStatus: String,
        val videoWaitingReason: String,
        val jobStatus: String,
        val runtimeMessage: String
    )

    private data class MetadataStageOutcome(
        val plan: MetadataPlan?,
        val artifacts: List<AutomationSavedArtifact>,
        val stepStatus: String,
        val waitingReason: String
    )

    private data class ReviewStageOutcome(
        val state: ReviewState?,
        val artifacts: List<AutomationSavedArtifact>,
        val stepStatus: String,
        val waitingReason: String
    )

    data class AutomationPublishShareResult(
        val job: AutomationUiJobSnapshot,
        val export: AutomationVideoExportResult,
        val shareText: String,
        val chooserTitle: String
    )

    companion object {
        const val AUTOMATION_PROJECT_ID: String = "automation-center"
        const val AUTOMATION_WORKFLOW_ID: String = "automation-center-local-canonical"
        const val AUTOMATION_WORKFLOW_VERSION: Int = 4
        const val DEFAULT_RECENT_LIMIT: Int = 8
        const val MAX_AUTOMATION_CONTENT_LENGTH: Int = 50_000
        const val DEFAULT_CONTENT_TYPE: String = "video_script"
        const val DEFAULT_PUBLISH_MODE: String = "approval_required"
        const val DEFAULT_SCENE_COUNT: Int = 12
        const val DEFAULT_TARGET_ASPECT_RATIO: String = "9:16"
        val SUPPORTED_ASPECT_RATIOS: Set<String> = setOf("9:16", "16:9", "1:1", "3:4", "4:3", "21:9")
        const val DEFAULT_VISUAL_STYLE: String = ScriptScenePromptGenerator.DEFAULT_VISUAL_STYLE
        const val MANUAL_IMAGE_IMPORT_PROVIDER_ID: String = "device-local-image"
        const val WEB_CONTENT_PROVIDER_ID: String = "gemini-web"
        const val MAX_RATE_LIMIT_RETRIES: Int = 3
        const val RATE_LIMIT_RETRY_BASE_DELAY_MS: Long = 2_000L
        val WEB_SCRAPE_IMAGE_PROVIDER_IDS: Set<String> = setOf(
            AutomationImageProviders.CHATGPT_IMAGE_SEARCH_WEB,
            AutomationImageProviders.PINTEREST_IMAGE_SEARCH_WEB
        )

        // Khoang lang chen SAU moi canh (tru canh cuoi) khi ghep giong doc tung
        // canh - tao nhip "dung mot chut" khi chuyen canh thay vi doc lien tuc.
        private const val SCENE_TRANSITION_SILENCE_MS: Int = 350
        private const val MIN_OUTPUT_LENGTH: Int = 128
        private const val MAX_OUTPUT_LENGTH: Int = 50_000
        private const val MAX_CONTENT_GENERATION_ATTEMPTS: Int = 3
        private val NARRATION_WORD_PATTERN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’_-][\\p{L}\\p{M}\\p{N}]+)*")

        private val STEP_DISPLAY_ORDER = mapOf(
            "TOPIC" to 0,
            "CONTENT" to 1,
            "SCENE_PROMPTS" to 2,
            "ASSET_PLAN" to 3,
            "IMAGES_VISUALS" to 4,
            "VOICE" to 5,
            "SUBTITLE" to 6,
            "VIDEO" to 7,
            "METADATA" to 8,
            "REVIEW" to 9,
            "PUBLISH" to 10
        )

        fun create(
            repository: AutomationRepository,
            artifactStore: AutomationArtifactStore = AutomationArtifactStore.empty(),
            connectorRegistry: AutomationConnectorRegistry = AutomationConnectorRegistry.empty(),
            credentialStore: AutomationCredentialStore = AutomationCredentialStore.empty(),
            contentConnector: ContentGenerationConnector = GeminiContentConnector(),
            scenePromptGenerator: ScenePromptGenerator = ScriptScenePromptGenerator(),
            visualAssetPlanner: VisualAssetPlanner = HeuristicVisualAssetPlanner(),
            imageProviderRegistry: ImageProviderRegistry = DefaultImageProviderRegistry(
                implementedProviderIds = setOf(
                    AutomationImageProviders.OPENAI_IMAGES,
                    AutomationImageProviders.CLOUDFLARE_WORKERS_AI,
                    AutomationImageProviders.PEXELS
                )
            ),
            imageConnectors: Map<String, ImageGenerationConnector> = mapOf(
                AutomationImageProviders.OPENAI_IMAGES to OpenAiImageConnector(),
                AutomationImageProviders.CLOUDFLARE_WORKERS_AI to CloudflareWorkersAiImageConnector(),
                AutomationImageProviders.PEXELS to PexelsStockImageConnector()
            ),
            voiceProviderRegistry: VoiceProviderRegistry = DefaultVoiceProviderRegistry(
                implementedProviderIds = setOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
            ),
            voiceConnectors: Map<String, VoiceGenerationConnector> = emptyMap(),
            videoRenderer: VideoRenderer = LocalPlanVideoRenderer(),
            nativeVideoRenderer: NativeVideoRenderer = AndroidNativeSlideshowVideoRenderer(),
            videoRenderWorkerClient: VideoRenderWorkerClient = ExternalMoviePyVideoRenderer(),
            metadataGenerator: MetadataGenerator = HeuristicMetadataGenerator(),
            progressListener: AutomationPipelineProgressListener = AutomationPipelineProgressListener.NONE,
            runtimeJobStore: RuntimeJobStore? = null
        ): AutomationFacade {
            val engine = AutomationEngine(
                repository = repository,
                artifactStore = artifactStore,
                connectorRegistry = connectorRegistry
            )
            return AutomationFacade(
                engine = engine,
                repository = repository,
                artifactStore = artifactStore,
                credentialStore = credentialStore,
                contentConnector = contentConnector,
                scenePromptGenerator = scenePromptGenerator,
                visualAssetPlanner = visualAssetPlanner,
                imageProviderRegistry = imageProviderRegistry,
                imageConnectors = imageConnectors.mapKeys { it.key.trim().lowercase() },
                voiceProviderRegistry = voiceProviderRegistry,
                voiceConnectors = voiceConnectors.mapKeys { it.key.trim().lowercase() },
                videoRenderer = videoRenderer,
                nativeVideoRenderer = nativeVideoRenderer,
                videoRenderWorkerClient = videoRenderWorkerClient,
                metadataGenerator = metadataGenerator,
                progressListener = progressListener,
                runtimeJobStore = runtimeJobStore
            )
        }

        fun createDefault(): AutomationFacade {
            return create(
                repository = AutomationRepository.empty(),
                artifactStore = AutomationArtifactStore.empty(),
                connectorRegistry = AutomationConnectorRegistry.empty(),
                credentialStore = AutomationCredentialStore.empty(),
                contentConnector = GeminiContentConnector(),
                scenePromptGenerator = ScriptScenePromptGenerator(),
                nativeVideoRenderer = NoOpNativeVideoRenderer,
                videoRenderWorkerClient = NoOpVideoRenderWorkerClient
            )
        }

        const val VIDEO_RENDERER_MODE_ANDROID_NATIVE: String = "android_native_render"
        const val VIDEO_RENDERER_MODE_LOCAL_PLAN_ONLY: String = "local_plan_only"
        const val VIDEO_RENDERER_MODE_EXTERNAL: String = "external_moviepy_worker"
    }
}
