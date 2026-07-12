package com.lqlq.browser.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.artifact.AutomationExportedArtifact
import com.lqlq.browser.automation.artifact.AutomationSavedArtifact
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.connector.content.ContentGenerationConnector
import com.lqlq.browser.automation.connector.content.ContentGenerationRequest
import com.lqlq.browser.automation.connector.content.ContentGenerationResult
import com.lqlq.browser.automation.connector.content.ContentProviderConfig
import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.connector.image.DefaultImageProviderRegistry
import com.lqlq.browser.automation.connector.image.ImageGenerationConnector
import com.lqlq.browser.automation.connector.image.ImageGenerationRequest
import com.lqlq.browser.automation.connector.image.ImageGenerationResult
import com.lqlq.browser.automation.connector.image.ImageProviderConfig
import com.lqlq.browser.automation.connector.image.ImageProviderConnectionResult
import com.lqlq.browser.automation.connector.image.OpenAiImageConnector
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import com.lqlq.browser.automation.connector.voice.VoiceArtifactMetadata
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceGenerationConnector
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceGenerationResult
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import com.lqlq.browser.automation.connector.voice.VoiceProviderConnectionResult
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.image.ScenePromptGenerationRequest
import com.lqlq.browser.automation.image.ScenePromptGenerator
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import com.lqlq.browser.automation.video.ExternalRenderedVideo
import com.lqlq.browser.automation.video.VideoRenderPlan
import com.lqlq.browser.automation.video.VideoRenderWorkerClient
import com.lqlq.browser.automation.video.VideoRenderWorkerHealth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationVoiceWorkflowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var facade: AutomationFacade
    private lateinit var credentialStore: FakeCredentialStore
    private lateinit var contentConnector: FakeContentConnector
    private lateinit var imageConnector: FakeImageConnector
    private lateinit var voiceConnector: FakeVoiceConnector
    private lateinit var videoWorkerClient: FakeVideoWorkerClient

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        credentialStore = FakeCredentialStore().apply {
            saveImageProviderConfiguration("openai-images", "image-key", OpenAiImageConnector.DEFAULT_MODEL)
            saveVoiceProviderConfiguration(
                VoiceProviderCredentialConfiguration(
                    providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                    locale = "vi-VN",
                    voiceId = "vi-offline",
                    outputFormat = "wav"
                )
            )
        }
        contentConnector = FakeContentConnector()
        imageConnector = FakeImageConnector()
        voiceConnector = FakeVoiceConnector()
        videoWorkerClient = FakeVideoWorkerClient()
        facade = AutomationFacade.create(
            repository = RoomAutomationRepository(database),
            artifactStore = FakeArtifactStore(),
            connectorRegistry = AutomationConnectorRegistry.of(
                "gemini",
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS
            ),
            credentialStore = credentialStore,
            contentConnector = contentConnector,
            scenePromptGenerator = FakeScenePromptGenerator(),
            imageProviderRegistry = DefaultImageProviderRegistry(
                implementedProviderIds = setOf(AutomationImageProviders.OPENAI_IMAGES)
            ),
            imageConnectors = mapOf(
                AutomationImageProviders.OPENAI_IMAGES to imageConnector
            ),
            voiceProviderRegistry = DefaultVoiceProviderRegistry(
                implementedProviderIds = setOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
            ),
            voiceConnectors = mapOf(
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS to voiceConnector
            ),
            videoRenderWorkerClient = videoWorkerClient
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun configuredVoiceProviderRunsAfterImageAndCreatesRealVoiceArtifact() = runBlocking {
        val snapshot = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "Chu de test voice",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000
            )
        )

        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VOICE" }.status)
        assertEquals("REAL_VOICE_READY", snapshot.steps.first { it.stepType == "VOICE" }.waitingReason)
        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VIDEO" }.status)
        assertEquals("VIDEO_RENDER_PLAN_READY", snapshot.steps.first { it.stepType == "VIDEO" }.waitingReason)
        assertEquals(1, voiceConnector.generateCount)
        assertEquals(3, imageConnector.requestOrdinals.size)
        assertTrue(snapshot.artifacts.any { it.artifactType == "VOICE" })
        assertTrue(snapshot.artifacts.any { it.artifactType == "VIDEO_RENDER_PLAN" })
        assertEquals("GENERATION_VERIFIED", credentialStore.getVoiceProviderConfigurationStatus(AutomationVoiceProviders.ANDROID_SYSTEM_TTS).state)
    }

    @Test
    fun retryVoiceDoesNotRerunContentOrImage() = runBlocking {
        val initial = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "Thu lai voice",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000
            )
        )

        val afterRetry = facade.retryVoiceStep(initial.jobId, AutomationVoiceProviders.ANDROID_SYSTEM_TTS)

        assertEquals(1, contentConnector.generateCount)
        assertEquals(3, imageConnector.requestOrdinals.size)
        assertEquals(2, voiceConnector.generateCount)
        assertEquals("COMPLETED", afterRetry.steps.first { it.stepType == "VOICE" }.status)
        assertTrue(afterRetry.artifacts.any { it.artifactType == "VOICE" })
        assertEquals("NOT_CONFIGURED", afterRetry.steps.first { it.stepType == "VIDEO" }.status)
    }

    @Test
    fun externalWorkerProducesVideoMp4ArtifactWhenConfigured() = runBlocking {
        val snapshot = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "Chu de test render worker",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000,
                videoRendererMode = AutomationFacade.VIDEO_RENDERER_MODE_EXTERNAL,
                videoWorkerUrl = "http://127.0.0.1:8787"
            )
        )

        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VIDEO" }.status)
        assertEquals("VIDEO_MP4_READY", snapshot.steps.first { it.stepType == "VIDEO" }.waitingReason)
        assertTrue(snapshot.artifacts.any { it.artifactType == "VIDEO_MP4" })
        assertEquals(1, videoWorkerClient.renderCount)
    }

    @Test
    fun externalWorkerOfflineFallsBackToRenderPlanReady() = runBlocking {
        videoWorkerClient.throwOnRender = true

        val snapshot = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "Chu de fallback worker",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000,
                videoRendererMode = AutomationFacade.VIDEO_RENDERER_MODE_EXTERNAL,
                videoWorkerUrl = "http://127.0.0.1:8787"
            )
        )

        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VIDEO" }.status)
        assertEquals("VIDEO_RENDER_PLAN_READY", snapshot.steps.first { it.stepType == "VIDEO" }.waitingReason)
        assertTrue(snapshot.artifacts.any { it.artifactType == "VIDEO_RENDER_PLAN" })
        assertTrue(snapshot.artifacts.none { it.artifactType == "VIDEO_MP4" })
    }

    private class FakeCredentialStore : AutomationCredentialStore {
        private var gemini = GeminiCredentialConfiguration(apiKey = "gemini-key", model = "gemini-2.5-flash")
        private val imageConfigs = linkedMapOf<String, ImageProviderCredentialConfiguration>()
        private val imageStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        private val voiceConfigs = linkedMapOf<String, VoiceProviderCredentialConfiguration>()
        private val voiceStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        private var selectedImageProviderId: String? = null
        private var selectedVoiceProviderId: String? = null

        override fun saveGeminiConfiguration(apiKey: String, model: String) {
            gemini = GeminiCredentialConfiguration(apiKey = apiKey, model = model)
        }
        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = gemini
        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONNECTED", "gemini", gemini.model)
        override fun markGeminiInvalid(message: String?) = Unit
        override fun clearGeminiConfiguration() = Unit

        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) {
            val id = providerId.lowercase()
            imageConfigs[id] = ImageProviderCredentialConfiguration(id, apiKey, model, accountId)
            imageStatuses[id] = AutomationCredentialStatusSnapshot("CONFIG_SAVED", id, model)
            selectedImageProviderId = id
        }

        override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? = imageConfigs[providerId.lowercase()]
        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = imageStatuses[providerId.lowercase()] ?: AutomationCredentialStatusSnapshot("NOT_CONFIGURED", providerId)
        override fun markImageProviderState(providerId: String, state: String, message: String?) {
            val id = providerId.lowercase()
            val config = imageConfigs[id]
            imageStatuses[id] = AutomationCredentialStatusSnapshot(state, id, config?.model, message)
        }
        override fun clearImageProviderConfiguration(providerId: String) = Unit
        override fun setSelectedImageProviderId(providerId: String) { selectedImageProviderId = providerId.lowercase() }
        override fun getSelectedImageProviderId(): String? = selectedImageProviderId

        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
            val id = configuration.providerId.lowercase()
            voiceConfigs[id] = configuration.copy(providerId = id)
            voiceStatuses[id] = AutomationCredentialStatusSnapshot(
                state = "CONFIG_SAVED",
                providerId = id,
                model = configuration.model,
                voiceId = configuration.voiceId,
                locale = configuration.locale,
                engineName = configuration.engineName
            )
            selectedVoiceProviderId = id
        }

        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = voiceConfigs[providerId.lowercase()]
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = voiceStatuses[providerId.lowercase()] ?: AutomationCredentialStatusSnapshot("NOT_CONFIGURED", providerId)
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) {
            val id = providerId.lowercase()
            val config = voiceConfigs[id]
            voiceStatuses[id] = AutomationCredentialStatusSnapshot(
                state = state,
                providerId = id,
                model = config?.model,
                message = message,
                voiceId = config?.voiceId,
                locale = config?.locale,
                engineName = config?.engineName
            )
        }
        override fun clearVoiceProviderConfiguration(providerId: String) = Unit
        override fun setSelectedVoiceProviderId(providerId: String) { selectedVoiceProviderId = providerId.lowercase() }
        override fun getSelectedVoiceProviderId(): String? = selectedVoiceProviderId
    }

    private class FakeArtifactStore : AutomationArtifactStore {
        override fun isFoundationReady(): Boolean = true
        override suspend fun saveGeneratedTextArtifact(jobId: String, stepId: String, text: String, providerId: String, model: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("text-$jobId", "TEXT", "text/plain", "automation://text/$jobId", text.length.toLong())
        }
        override suspend fun saveGeneratedImageArtifact(jobId: String, stepId: String, bytes: ByteArray, providerId: String, model: String, mimeType: String, sourceUrl: String?, sceneId: String, ordinal: Int, providerRequestId: String?): AutomationSavedArtifact {
            return AutomationSavedArtifact("image-$ordinal", "IMAGE", mimeType, "automation://image/$ordinal", bytes.size.toLong(), sceneId = sceneId, ordinal = ordinal, previewDataUrl = "data:$mimeType;base64,preview")
        }
        override suspend fun saveGeneratedVoiceArtifact(jobId: String, stepId: String, bytes: ByteArray, providerId: String, voiceId: String?, locale: String, mimeType: String, durationMs: Long?, chunkCount: Int, inputCharCount: Int, inputSceneCount: Int): AutomationSavedArtifact {
            return AutomationSavedArtifact("voice-$jobId", "VOICE", mimeType, "automation://voice/$jobId", bytes.size.toLong(), sourceUrl = "voice=$voiceId;durationMs=${durationMs ?: 0};chunks=$chunkCount;inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount", previewDataUrl = "data:$mimeType;base64,voice")
        }
        override suspend fun saveGeneratedVideoRenderPlanArtifact(jobId: String, stepId: String, json: String, rendererId: String, sourceSummary: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-plan-$jobId", "VIDEO_RENDER_PLAN", "application/json", "automation://video-plan/$jobId", json.length.toLong(), sourceUrl = "renderer=$rendererId;$sourceSummary")
        }
        override suspend fun saveGeneratedVideoFileArtifact(jobId: String, stepId: String, bytes: ByteArray, rendererId: String, mimeType: String, sourceUrl: String?): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-file-$jobId", "VIDEO_MP4", mimeType, "automation://video-file/$jobId", bytes.size.toLong(), sourceUrl = sourceUrl)
        }
        override suspend fun saveMetadataPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun saveReviewStateArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun savePublishPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun readArtifactBytes(artifact: AutomationSavedArtifact): ByteArray? = when (artifact.artifactType) {
            "IMAGE" -> byteArrayOf(1, 2, 3)
            "VOICE" -> byteArrayOf(7, 8, 9)
            else -> null
        }

        override suspend fun exportVideoArtifactToDownloads(
            artifact: AutomationSavedArtifact,
            jobId: String
        ): AutomationExportedArtifact? = null

        override suspend fun deleteArtifactByUri(uri: String): Boolean = true
    }

    private class FakeContentConnector : ContentGenerationConnector {
        var generateCount = 0
        override suspend fun testConnection(config: ContentProviderConfig): ContentGenerationResult = ContentGenerationResult("OK", config.providerId, config.model)
        override suspend fun generateContent(config: ContentProviderConfig, request: ContentGenerationRequest): ContentGenerationResult {
            generateCount += 1
            return ContentGenerationResult("Noi dung that tu Gemini", config.providerId, config.model)
        }
    }

    private class FakeScenePromptGenerator : ScenePromptGenerator {
        override suspend fun generateScenePrompts(request: ScenePromptGenerationRequest): List<ScenePrompt> {
            return listOf(
                ScenePrompt("scene-1", 1, "Summary 1", "Prompt anh 1", "Negative 1", "9:16"),
                ScenePrompt("scene-2", 2, "Summary 2", "Prompt anh 2", "Negative 2", "9:16"),
                ScenePrompt("scene-3", 3, "Summary 3", "Prompt anh 3", "Negative 3", "9:16")
            )
        }
    }

    private class FakeImageConnector : ImageGenerationConnector {
        val requestOrdinals = mutableListOf<Int>()
        override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult = ImageProviderConnectionResult(config.providerId, config.model, "image-ok")
        override suspend fun generateImage(config: ImageProviderConfig, request: ImageGenerationRequest): ImageGenerationResult {
            requestOrdinals += request.ordinal
            return ImageGenerationResult(request.sceneId, request.ordinal, config.providerId, config.model, "req-${request.ordinal}", "image/png", byteArrayOf(1, 2, 3))
        }
    }

    private class FakeVoiceConnector : VoiceGenerationConnector {
        var generateCount = 0
        override fun listVoices(): List<VoiceDefinition> = listOf(
            VoiceDefinition("vi-offline", "Tieng Viet Offline", "vi-VN", "Android System TTS", false, true, true)
        )
        override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
            return VoiceProviderConnectionResult(config.providerId, config.model, config.voiceId, config.locale, "Android System TTS", 1)
        }
        override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String) =
            com.lqlq.browser.automation.connector.voice.VoiceSampleResult(byteArrayOf(1), "audio/wav", config.providerId, config.voiceId, config.locale, "Android System TTS")
        override suspend fun generateVoice(config: VoiceProviderConfig, request: VoiceGenerationRequest): VoiceGenerationResult {
            generateCount += 1
            return VoiceGenerationResult(
                bytes = byteArrayOf(7, 8, 9),
                mimeType = "audio/wav",
                metadata = VoiceArtifactMetadata(config.providerId, config.voiceId, config.locale, 1, 1000L, "checksum", "Android System TTS")
            )
        }

        override fun openProviderSettings() = Unit
    }

    private class FakeVideoWorkerClient : VideoRenderWorkerClient {
        var renderCount = 0
        var throwOnRender = false

        override suspend fun testWorker(workerUrl: String): VideoRenderWorkerHealth {
            return VideoRenderWorkerHealth("ok", "moviepy", "worker-v1")
        }

        override suspend fun renderVideo(
            workerUrl: String,
            plan: VideoRenderPlan,
            planJson: String,
            voiceArtifact: AutomationSavedArtifact,
            imageArtifacts: List<AutomationSavedArtifact>,
            artifactStore: AutomationArtifactStore
        ): ExternalRenderedVideo {
            renderCount += 1
            if (throwOnRender) {
                throw IllegalStateException("offline")
            }
            return ExternalRenderedVideo(
                rendererId = "external-moviepy-worker",
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "video/mp4",
                downloadUrl = "http://127.0.0.1:8787/videos/test.mp4",
                durationMs = 1000L,
                width = 1080,
                height = 1920,
                fps = 30,
                sceneCount = plan.sceneCount
            )
        }
    }
}
