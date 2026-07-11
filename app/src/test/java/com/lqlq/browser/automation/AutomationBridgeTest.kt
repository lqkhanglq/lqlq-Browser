package com.lqlq.browser.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.AutomationBridge
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
import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.connector.image.DefaultImageProviderRegistry
import com.lqlq.browser.automation.connector.image.ImageGenerationConnector
import com.lqlq.browser.automation.connector.image.ImageGenerationRequest
import com.lqlq.browser.automation.connector.image.ImageGenerationResult
import com.lqlq.browser.automation.connector.image.ImageProviderConfig
import com.lqlq.browser.automation.connector.image.ImageProviderConnectionResult
import com.lqlq.browser.automation.connector.image.ImageProviderErrorCode
import com.lqlq.browser.automation.connector.image.ImageProviderException
import com.lqlq.browser.automation.connector.image.OpenAiImageConnector
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceGenerationConnector
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceGenerationResult
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import com.lqlq.browser.automation.connector.voice.VoiceProviderConnectionResult
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.image.ScenePromptGenerationRequest
import com.lqlq.browser.automation.image.ScenePromptGenerator
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import com.lqlq.browser.automation.video.ExternalRenderedVideo
import com.lqlq.browser.automation.video.VideoRenderPlan
import com.lqlq.browser.automation.video.VideoRenderWorkerClient
import com.lqlq.browser.automation.video.VideoRenderWorkerHealth
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationBridgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var credentialStore: InMemoryCredentialStore
    private lateinit var connector: FakeContentConnector
    private lateinit var openAiConnector: FakeImageConnector
    private lateinit var cloudflareConnector: FakeImageConnector
    private lateinit var voiceConnector: FakeVoiceConnector
    private lateinit var videoWorkerClient: FakeVideoWorkerClient
    private lateinit var bridge: AutomationBridge

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        credentialStore = InMemoryCredentialStore()
        connector = FakeContentConnector()
        openAiConnector = FakeImageConnector(AutomationImageProviders.OPENAI_IMAGES)
        cloudflareConnector = FakeImageConnector(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)
        voiceConnector = FakeVoiceConnector()
        videoWorkerClient = FakeVideoWorkerClient()
        bridge = AutomationBridge(
            context,
            AutomationFacade.create(
                repository = RoomAutomationRepository(database),
                artifactStore = RecordingArtifactStore(),
                connectorRegistry = AutomationConnectorRegistry.of(
                    "gemini",
                    AutomationImageProviders.OPENAI_IMAGES,
                    AutomationImageProviders.CLOUDFLARE_WORKERS_AI,
                    AutomationVoiceProviders.ANDROID_SYSTEM_TTS
                ),
                credentialStore = credentialStore,
                contentConnector = connector,
                scenePromptGenerator = FakeScenePromptGenerator(),
                imageProviderRegistry = DefaultImageProviderRegistry(
                    implementedProviderIds = setOf(
                        AutomationImageProviders.OPENAI_IMAGES,
                        AutomationImageProviders.CLOUDFLARE_WORKERS_AI
                    )
                ),
                imageConnectors = mapOf(
                    AutomationImageProviders.OPENAI_IMAGES to openAiConnector,
                    AutomationImageProviders.CLOUDFLARE_WORKERS_AI to cloudflareConnector
                ),
                voiceProviderRegistry = DefaultVoiceProviderRegistry(
                    implementedProviderIds = setOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
                ),
                voiceConnectors = mapOf(
                    AutomationVoiceProviders.ANDROID_SYSTEM_TTS to voiceConnector
                ),
                videoRenderWorkerClient = videoWorkerClient
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun statusContractNeverExposesApiKeys() {
        val geminiSave = JSONObject(
            bridge.saveGeminiConfiguration(
                JSONObject()
                    .put("apiKey", "gemini-secret-key")
                    .put("model", "gemini-2.5-flash")
                    .toString()
            )
        )
        val imageSave = JSONObject(
            bridge.saveImageProviderConfiguration(
                JSONObject()
                    .put("providerId", AutomationImageProviders.OPENAI_IMAGES)
                    .put("apiKey", "openai-secret-key")
                    .put("model", OpenAiImageConnector.DEFAULT_MODEL)
                    .toString()
            )
        )
        val geminiStatus = JSONObject(bridge.getGeminiConfigurationStatus())
        val imageStatus = JSONObject(bridge.getImageProviderConfigurationStatus())

        assertTrue(geminiSave.getBoolean("ok"))
        assertTrue(imageSave.getBoolean("ok"))
        assertTrue(geminiStatus.getBoolean("ok"))
        assertTrue(imageStatus.getBoolean("ok"))
        assertFalse(geminiStatus.toString().contains("gemini-secret-key"))
        assertFalse(imageStatus.toString().contains("openai-secret-key"))
        assertEquals("CONNECTED", geminiStatus.getJSONObject("status").getString("state"))
        assertEquals("CONFIG_SAVED", imageStatus.getJSONObject("status").getString("state"))
    }

    @Test
    fun listImageProvidersShowsOpenAiAsOptionalAndCloudflareAsImplemented() {
        val response = JSONObject(bridge.listImageProviders())
        val providers = response.getJSONArray("providers")

        assertTrue(response.getBoolean("ok"))
        assertTrue(providers.toString().contains(AutomationImageProviders.OPENAI_IMAGES))
        assertTrue(providers.toString().contains(AutomationImageProviders.CLOUDFLARE_WORKERS_AI))
        assertTrue(providers.toString().contains("\"health\":\"AVAILABLE\""))
    }

    @Test
    fun emptyTopicRejected() {
        val response = JSONObject(bridge.generateAutomationContent(validRequestJson("   ")))

        assertFalse(response.getBoolean("ok"))
        assertEquals("VALIDATION", response.getString("errorCode"))
    }

    @Test
    fun topicLongerThan4000ButWithin50000Accepted() {
        saveValidGeminiConfig()
        val longTopic = "a".repeat(12_000)

        val response = JSONObject(bridge.generateAutomationContent(validRequestJson(longTopic)))

        assertTrue(response.getBoolean("ok"))
        assertEquals(longTopic, connector.lastRequest?.topic)
    }

    @Test
    fun topicLongerThan50000Rejected() {
        saveValidGeminiConfig()
        val longTopic = "a".repeat(AutomationFacade.MAX_AUTOMATION_CONTENT_LENGTH + 1)

        val response = JSONObject(bridge.generateAutomationContent(validRequestJson(longTopic)))

        assertFalse(response.getBoolean("ok"))
        assertEquals("VALIDATION", response.getString("errorCode"))
    }

    @Test
    fun cloudflareConfigRequiresAccountId() {
        val response = JSONObject(
            bridge.saveImageProviderConfiguration(
                JSONObject()
                    .put("providerId", AutomationImageProviders.CLOUDFLARE_WORKERS_AI)
                    .put("apiKey", "cf-token")
                    .put("model", "@cf/black-forest-labs/flux-1-schnell")
                    .toString()
            )
        )

        assertFalse(response.getBoolean("ok"))
        assertEquals("VALIDATION", response.getString("errorCode"))
    }

    @Test
    fun htmlAndScriptAreTreatedAsPlainText() {
        saveValidGeminiConfig()
        val topic = "<script>alert('x')</script><b>hello</b>"

        val response = JSONObject(bridge.generateAutomationContent(validRequestJson(topic)))

        assertTrue(response.getBoolean("ok"))
        assertEquals(topic, connector.lastRequest?.topic)
    }

    @Test
    fun imageConnectionBillingFailureIsMappedWithoutLeakingApiKey() {
        saveValidOpenAiConfig()
        openAiConnector.connectionFailure = ImageProviderException(
            ImageProviderErrorCode.BILLING_REQUIRED,
            "OpenAI Images can han muc thanh toan hoac billing hop le."
        )

        val response = JSONObject(
            bridge.testImageProviderConnection(
                JSONObject().put("providerId", AutomationImageProviders.OPENAI_IMAGES).toString()
            )
        )

        assertFalse(response.getBoolean("ok"))
        assertEquals("BILLING_REQUIRED", response.getString("errorCode"))
        assertFalse(response.toString().contains("openai-secret-key"))
    }

    @Test
    fun voiceConfigurationAndVoiceListCanBeLoadedWithoutLeakingSecret() {
        val save = JSONObject(
            bridge.saveVoiceProviderConfiguration(
                JSONObject()
                    .put("providerId", AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
                    .put("locale", "vi-VN")
                    .put("voiceId", "vi-vn-offline")
                    .put("speechRate", 1.0)
                    .put("pitch", 1.0)
                    .put("outputFormat", "wav")
                    .toString()
            )
        )
        val list = JSONObject(
            bridge.listVoiceDefinitions(
                JSONObject().put("providerId", AutomationVoiceProviders.ANDROID_SYSTEM_TTS).toString()
            )
        )
        val status = JSONObject(
            bridge.getVoiceProviderConfigurationStatus(
                JSONObject().put("providerId", AutomationVoiceProviders.ANDROID_SYSTEM_TTS).toString()
            )
        )

        assertTrue(save.getBoolean("ok"))
        assertTrue(list.getBoolean("ok"))
        assertTrue(status.getBoolean("ok"))
        assertEquals("CONFIG_SAVED", status.getJSONObject("status").getString("state"))
        assertEquals("vi-vn-offline", status.getJSONObject("status").getString("voiceId"))
        assertTrue(list.getJSONArray("voices").length() >= 1)
    }

    @Test
    fun testVideoWorkerValidatesAndReturnsHealth() {
        val response = JSONObject(
            bridge.testVideoRenderWorker(
                JSONObject().put("videoWorkerUrl", "http://127.0.0.1:8787").toString()
            )
        )

        assertTrue(response.getBoolean("ok"))
        assertEquals("moviepy", response.getJSONObject("status").getString("providerId"))
        assertEquals("worker-v1", response.getJSONObject("status").getString("model"))
    }

    @Test
    fun exportVideoMp4ReturnsDownloadsLocationWhenArtifactExists() {
        saveValidGeminiConfig()
        saveValidOpenAiConfig()
        saveValidVoiceConfig()

        val create = JSONObject(
            bridge.generateAutomationContent(
                JSONObject()
                    .put("topic", "Video export test")
                    .put("language", "vi")
                    .put("contentType", "video_script")
                    .put("maximumOutputLength", 4000)
                    .put("videoRendererMode", AutomationFacade.VIDEO_RENDERER_MODE_EXTERNAL)
                    .put("videoWorkerUrl", "http://127.0.0.1:8787")
                    .toString()
            )
        )
        assertTrue(create.getBoolean("ok"))
        val jobId = create.getJSONObject("job").getString("jobId")

        val export = JSONObject(
            bridge.exportVideoMp4(
                JSONObject().put("jobId", jobId).toString()
            )
        )

        assertTrue(export.getBoolean("ok"))
        assertTrue(export.getJSONObject("export").getString("displayPath").contains("Downloads/LQLQAutomation"))
        assertTrue(export.getJSONObject("export").getString("contentUri").isNotBlank())
    }

    private fun saveValidGeminiConfig() {
        val response = JSONObject(
            bridge.saveGeminiConfiguration(
                JSONObject()
                    .put("apiKey", "gemini-secret-key")
                    .put("model", "gemini-2.5-flash")
                    .toString()
            )
        )
        assertTrue(response.getBoolean("ok"))
    }

    private fun saveValidOpenAiConfig() {
        val response = JSONObject(
            bridge.saveImageProviderConfiguration(
                JSONObject()
                    .put("providerId", AutomationImageProviders.OPENAI_IMAGES)
                    .put("apiKey", "openai-secret-key")
                    .put("model", OpenAiImageConnector.DEFAULT_MODEL)
                    .toString()
            )
        )
        assertTrue(response.getBoolean("ok"))
    }

    private fun saveValidVoiceConfig() {
        val response = JSONObject(
            bridge.saveVoiceProviderConfiguration(
                JSONObject()
                    .put("providerId", AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
                    .put("locale", "vi-VN")
                    .put("voiceId", "vi-vn-offline")
                    .put("speechRate", 1.0)
                    .put("pitch", 1.0)
                    .put("outputFormat", "wav")
                    .toString()
            )
        )
        assertTrue(response.getBoolean("ok"))
    }

    private fun validRequestJson(topic: String): String {
        return JSONObject()
            .put("topic", topic)
            .put("language", "vi")
            .put("contentType", "video_script")
            .put("maximumOutputLength", 4000)
            .toString()
    }

    private class InMemoryCredentialStore : AutomationCredentialStore {
        private var geminiConfig: GeminiCredentialConfiguration? = null
        private var geminiStatus = AutomationCredentialStore.STATE_NOT_CONFIGURED
        private var geminiStatusMessage: String? = null
        private val imageConfigs = linkedMapOf<String, ImageProviderCredentialConfiguration>()
        private val imageStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        private var selectedImageProviderId: String? = null
        private val voiceConfigs = linkedMapOf<String, VoiceProviderCredentialConfiguration>()
        private val voiceStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        private var selectedVoiceProviderId: String? = null

        override fun saveGeminiConfiguration(apiKey: String, model: String) {
            geminiConfig = GeminiCredentialConfiguration(
                apiKey = apiKey.trim(),
                model = model.trim()
            )
            geminiStatus = AutomationCredentialStore.STATE_CONNECTED
            geminiStatusMessage = null
        }

        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = geminiConfig

        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot(
                state = geminiStatus,
                providerId = AutomationCredentialStore.GEMINI_PROVIDER_ID,
                model = geminiConfig?.model,
                message = geminiStatusMessage
            )
        }

        override fun markGeminiInvalid(message: String?) {
            geminiStatus = AutomationCredentialStore.STATE_INVALID
            geminiStatusMessage = message
        }

        override fun clearGeminiConfiguration() {
            geminiConfig = null
            geminiStatus = AutomationCredentialStore.STATE_NOT_CONFIGURED
            geminiStatusMessage = null
        }

        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) {
            val normalizedProviderId = providerId.trim().lowercase()
            imageConfigs[normalizedProviderId] = ImageProviderCredentialConfiguration(
                providerId = normalizedProviderId,
                apiKey = apiKey.trim(),
                model = model.trim(),
                accountId = accountId?.trim()?.ifBlank { null }
            )
            imageStatuses[normalizedProviderId] = AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.IMAGE_STATE_CONFIG_SAVED,
                providerId = normalizedProviderId,
                model = model.trim(),
                accountId = accountId?.trim()?.ifBlank { null }
            )
            selectedImageProviderId = normalizedProviderId
        }

        override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? {
            return imageConfigs[providerId.trim().lowercase()]
        }

        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            val normalizedProviderId = providerId.trim().lowercase()
            return imageStatuses[normalizedProviderId] ?: AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = normalizedProviderId
            )
        }

        override fun markImageProviderState(providerId: String, state: String, message: String?) {
            val normalizedProviderId = providerId.trim().lowercase()
            val existing = imageStatuses[normalizedProviderId]
            imageStatuses[normalizedProviderId] = AutomationCredentialStatusSnapshot(
                state = state,
                providerId = normalizedProviderId,
                model = existing?.model ?: imageConfigs[normalizedProviderId]?.model,
                message = message,
                accountId = existing?.accountId ?: imageConfigs[normalizedProviderId]?.accountId
            )
        }

        override fun clearImageProviderConfiguration(providerId: String) {
            val normalizedProviderId = providerId.trim().lowercase()
            imageConfigs.remove(normalizedProviderId)
            imageStatuses.remove(normalizedProviderId)
        }

        override fun setSelectedImageProviderId(providerId: String) {
            selectedImageProviderId = providerId.trim().lowercase()
        }

        override fun getSelectedImageProviderId(): String? = selectedImageProviderId

        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
            val providerId = configuration.providerId.trim().lowercase()
            voiceConfigs[providerId] = configuration.copy(providerId = providerId)
            voiceStatuses[providerId] = AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED,
                providerId = providerId,
                model = configuration.model,
                voiceId = configuration.voiceId,
                locale = configuration.locale,
                engineName = configuration.engineName
            )
            selectedVoiceProviderId = providerId
        }

        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? {
            return voiceConfigs[providerId.trim().lowercase()]
        }

        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            val normalizedProviderId = providerId.trim().lowercase()
            return voiceStatuses[normalizedProviderId] ?: AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = normalizedProviderId
            )
        }

        override fun markVoiceProviderState(providerId: String, state: String, message: String?) {
            val normalizedProviderId = providerId.trim().lowercase()
            val existing = voiceStatuses[normalizedProviderId]
            val config = voiceConfigs[normalizedProviderId]
            voiceStatuses[normalizedProviderId] = AutomationCredentialStatusSnapshot(
                state = state,
                providerId = normalizedProviderId,
                model = config?.model,
                message = message,
                voiceId = config?.voiceId,
                locale = config?.locale,
                engineName = config?.engineName
            )
        }

        override fun clearVoiceProviderConfiguration(providerId: String) {
            val normalizedProviderId = providerId.trim().lowercase()
            voiceConfigs.remove(normalizedProviderId)
            voiceStatuses.remove(normalizedProviderId)
        }

        override fun setSelectedVoiceProviderId(providerId: String) {
            selectedVoiceProviderId = providerId.trim().lowercase()
        }

        override fun getSelectedVoiceProviderId(): String? = selectedVoiceProviderId
    }

    private class RecordingArtifactStore : AutomationArtifactStore {
        override fun isFoundationReady(): Boolean = true

        override suspend fun saveGeneratedTextArtifact(
            jobId: String,
            stepId: String,
            text: String,
            providerId: String,
            model: String
        ): AutomationSavedArtifact {
            return AutomationSavedArtifact(
                artifactId = "artifact-$jobId",
                artifactType = "TEXT",
                mimeType = "text/plain",
                uri = "automation://artifact/$jobId",
                sizeBytes = text.length.toLong()
            )
        }

        override suspend fun saveGeneratedImageArtifact(
            jobId: String,
            stepId: String,
            bytes: ByteArray,
            providerId: String,
            model: String,
            mimeType: String,
            sourceUrl: String?,
            sceneId: String,
            ordinal: Int,
            providerRequestId: String?
        ): AutomationSavedArtifact {
            return AutomationSavedArtifact(
                artifactId = "artifact-$jobId-image-$ordinal",
                artifactType = "IMAGE",
                mimeType = mimeType,
                uri = "automation://artifact/$jobId-image-$ordinal",
                sizeBytes = bytes.size.toLong(),
                sourceUrl = sourceUrl,
                sceneId = sceneId,
                ordinal = ordinal,
                providerRequestId = providerRequestId,
                previewDataUrl = "data:$mimeType;base64,preview-$ordinal"
            )
        }

        override suspend fun saveGeneratedVoiceArtifact(
            jobId: String,
            stepId: String,
            bytes: ByteArray,
            providerId: String,
            voiceId: String?,
            locale: String,
            mimeType: String,
            durationMs: Long?,
            chunkCount: Int,
            inputCharCount: Int,
            inputSceneCount: Int
        ): AutomationSavedArtifact {
            return AutomationSavedArtifact(
                artifactId = "artifact-$jobId-voice",
                artifactType = "VOICE",
                mimeType = mimeType,
                uri = "automation://artifact/$jobId-voice",
                sizeBytes = bytes.size.toLong(),
                sourceUrl = "voice=$voiceId;locale=$locale;chunks=$chunkCount;durationMs=${durationMs ?: 0};inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount",
                previewDataUrl = "data:$mimeType;base64,voice-preview"
            )
        }

        override suspend fun saveGeneratedVideoRenderPlanArtifact(
            jobId: String,
            stepId: String,
            json: String,
            rendererId: String,
            sourceSummary: String
        ): AutomationSavedArtifact {
            return AutomationSavedArtifact(
                artifactId = "artifact-$jobId-video-plan",
                artifactType = "VIDEO_RENDER_PLAN",
                mimeType = "application/json",
                uri = "automation://artifact/$jobId-video-plan",
                sizeBytes = json.length.toLong(),
                sourceUrl = "renderer=$rendererId;$sourceSummary"
            )
        }

        override suspend fun saveGeneratedVideoFileArtifact(
            jobId: String,
            stepId: String,
            bytes: ByteArray,
            rendererId: String,
            mimeType: String,
            sourceUrl: String?
        ): AutomationSavedArtifact {
            return AutomationSavedArtifact(
                artifactId = "artifact-$jobId-video-file",
                artifactType = "VIDEO_MP4",
                mimeType = mimeType,
                uri = "automation://artifact/$jobId-video-file",
                sizeBytes = bytes.size.toLong(),
                sourceUrl = sourceUrl
            )
        }

        override suspend fun saveMetadataPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun saveReviewStateArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun savePublishPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun readArtifactBytes(artifact: AutomationSavedArtifact): ByteArray? = when (artifact.artifactType) {
            "IMAGE" -> byteArrayOf(0x01, 0x02, 0x03)
            "VOICE" -> byteArrayOf(0x04, 0x05, 0x06)
            else -> null
        }

        override suspend fun exportVideoArtifactToDownloads(
            artifact: AutomationSavedArtifact,
            jobId: String
        ): AutomationExportedArtifact? {
            return if (artifact.artifactType == "VIDEO_MP4") {
                AutomationExportedArtifact(
                    displayName = "lqlq_video_${jobId}_123456.mp4",
                    mimeType = artifact.mimeType,
                    contentUri = "content://downloads/lqlq_video_${jobId}_123456.mp4",
                    displayPath = "Downloads/LQLQAutomation/lqlq_video_${jobId}_123456.mp4",
                    sizeBytes = artifact.sizeBytes
                )
            } else {
                null
            }
        }
    }

    private class FakeContentConnector : ContentGenerationConnector {
        var lastRequest: ContentGenerationRequest? = null
        var generateFailure: ContentProviderException? = null

        override suspend fun testConnection(config: ContentProviderConfig): ContentGenerationResult {
            return ContentGenerationResult(
                generatedText = "KET_NOI_THANH_CONG",
                providerId = config.providerId,
                model = config.model
            )
        }

        override suspend fun generateContent(
            config: ContentProviderConfig,
            request: ContentGenerationRequest
        ): ContentGenerationResult {
            generateFailure?.let { throw it }
            lastRequest = request
            return ContentGenerationResult(
                generatedText = "Script that duoc tao boi Gemini",
                providerId = config.providerId,
                model = config.model,
                requestId = "request-1",
                usageMetadata = mapOf("totalTokenCount" to 123L)
            )
        }
    }

    private class FakeScenePromptGenerator : ScenePromptGenerator {
        override suspend fun generateScenePrompts(
            request: ScenePromptGenerationRequest
        ): List<ScenePrompt> {
            return listOf(
                ScenePrompt("scene-1", 1, "Summary 1", "Prompt anh 1", "Negative 1", "9:16"),
                ScenePrompt("scene-2", 2, "Summary 2", "Prompt anh 2", "Negative 2", "9:16"),
                ScenePrompt("scene-3", 3, "Summary 3", "Prompt anh 3", "Negative 3", "9:16")
            )
        }
    }

    private class FakeImageConnector(
        private val providerId: String
    ) : ImageGenerationConnector {
        var connectionFailure: ImageProviderException? = null

        override suspend fun testConnection(
            config: ImageProviderConfig
        ): ImageProviderConnectionResult {
            connectionFailure?.let { throw it }
            return ImageProviderConnectionResult(
                providerId = config.providerId,
                model = config.model,
                providerRequestId = "image-connection-ok"
            )
        }

        override suspend fun generateImage(
            config: ImageProviderConfig,
            request: ImageGenerationRequest
        ): ImageGenerationResult {
            return ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = providerId,
                model = config.model,
                providerRequestId = "IMAGE_PROVIDER_REQUEST_${request.ordinal}",
                mimeType = "image/png",
                bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
        }
    }

    private class FakeVoiceConnector : VoiceGenerationConnector {
        override fun listVoices(): List<VoiceDefinition> {
            return listOf(
                VoiceDefinition(
                    voiceId = "vi-vn-offline",
                    displayName = "Tieng Viet Offline",
                    locale = "vi-VN",
                    engineName = "Android System TTS",
                    networkRequired = false,
                    installed = true,
                    isDefault = true
                )
            )
        }

        override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
            return VoiceProviderConnectionResult(
                providerId = config.providerId,
                model = null,
                voiceId = config.voiceId ?: "vi-vn-offline",
                locale = config.locale,
                engineName = "Android System TTS",
                voiceCount = 1
            )
        }

        override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String) =
            com.lqlq.browser.automation.connector.voice.VoiceSampleResult(
                bytes = byteArrayOf(1, 2, 3),
                mimeType = "audio/wav",
                providerId = config.providerId,
                voiceId = config.voiceId ?: "vi-vn-offline",
                locale = config.locale,
                engineName = "Android System TTS"
            )

        override suspend fun generateVoice(
            config: VoiceProviderConfig,
            request: VoiceGenerationRequest
        ): VoiceGenerationResult {
            return VoiceGenerationResult(
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "audio/wav",
                metadata = com.lqlq.browser.automation.connector.voice.VoiceArtifactMetadata(
                    providerId = config.providerId,
                    voiceId = config.voiceId ?: "vi-vn-offline",
                    locale = config.locale,
                    chunkCount = 1,
                    durationMs = 1000L,
                    checksum = "checksum",
                    engineName = "Android System TTS"
                )
            )
        }

        override fun openProviderSettings() = Unit
    }

    private class FakeVideoWorkerClient : VideoRenderWorkerClient {
        override suspend fun testWorker(workerUrl: String): VideoRenderWorkerHealth {
            return VideoRenderWorkerHealth(status = "ok", renderer = "moviepy", version = "worker-v1")
        }

        override suspend fun renderVideo(
            workerUrl: String,
            plan: VideoRenderPlan,
            planJson: String,
            voiceArtifact: AutomationSavedArtifact,
            imageArtifacts: List<AutomationSavedArtifact>,
            artifactStore: AutomationArtifactStore
        ): ExternalRenderedVideo {
            return ExternalRenderedVideo(
                rendererId = "external-moviepy-worker",
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "video/mp4",
                downloadUrl = "http://127.0.0.1:8787/videos/test.mp4",
                durationMs = 1_000L,
                width = 1080,
                height = 1920,
                fps = 30,
                sceneCount = 3
            )
        }
    }
}
