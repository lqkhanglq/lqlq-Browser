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
import com.lqlq.browser.automation.connector.image.ImageProviderErrorCode
import com.lqlq.browser.automation.connector.image.ImageProviderException
import com.lqlq.browser.automation.connector.image.OpenAiImageConnector
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import com.lqlq.browser.automation.connector.voice.VoiceArtifactMetadata
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceGenerationResult
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import com.lqlq.browser.automation.connector.voice.VoiceProviderConnectionResult
import com.lqlq.browser.automation.connector.voice.VoiceGenerationConnector
import com.lqlq.browser.automation.connector.voice.VoiceSampleResult
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.image.ScenePromptGenerationRequest
import com.lqlq.browser.automation.image.ScenePromptGenerator
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationImageWorkflowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var repository: RoomAutomationRepository
    private lateinit var credentialStore: FakeCredentialStore
    private lateinit var contentConnector: FakeContentConnector
    private lateinit var openAiConnector: FakeImageConnector
    private lateinit var cloudflareConnector: FakeImageConnector
    private lateinit var voiceConnector: FakeVoiceConnector
    private lateinit var facade: AutomationFacade

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAutomationRepository(database)
        credentialStore = FakeCredentialStore().apply {
            saveImageProviderConfiguration(
                providerId = AutomationImageProviders.OPENAI_IMAGES,
                apiKey = "openai-secret",
                model = OpenAiImageConnector.DEFAULT_MODEL
            )
        }
        contentConnector = FakeContentConnector()
        openAiConnector = FakeImageConnector(AutomationImageProviders.OPENAI_IMAGES)
        cloudflareConnector = FakeImageConnector(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)
        voiceConnector = FakeVoiceConnector()
        facade = createFacade()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validRequestCreatesCanonicalEightStepWorkflowAndThreeOrderedImages() = runBlocking {
        val snapshot = facade.generateAutomationContent(validRequest())
        val persisted = repository.getJobGraph(snapshot.jobId)
        val reloaded = facade.getAutomationJob(snapshot.jobId)

        requireNotNull(persisted)
        requireNotNull(reloaded)

        assertEquals(
            listOf("TOPIC", "CONTENT", "SCENE_PROMPTS", "ASSET_PLAN", "IMAGES_VISUALS", "VOICE", "SUBTITLE", "VIDEO", "METADATA", "REVIEW", "PUBLISH"),
            snapshot.steps.map { it.stepType }
        )
        assertEquals(
            listOf("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "WAITING_USER", "WAITING_USER"),
            snapshot.steps.map { it.status }
        )
        assertEquals(3, snapshot.scenePrompts.size)
        assertEquals(listOf(1, 2, 3), snapshot.scenePrompts.map { it.ordinal })
        assertEquals(5, snapshot.artifacts.size)
        assertEquals(listOf(1, 2, 3), snapshot.artifacts.filter { it.artifactType == "IMAGE" }.mapNotNull { it.ordinal })
        assertEquals(listOf(1, 2, 3), openAiConnector.requestOrdinals)
        assertEquals("Noi dung that tu Gemini", snapshot.generatedText)
        assertEquals("Noi dung that tu Gemini", reloaded.generatedText)
        assertEquals(11, persisted.steps.size)
        assertEquals(
            listOf("local-topic", "gemini", "local-scene-prompts", "local-asset-planner", "openai-images", "not-configured-voice", "local-subtitle-plan", "not-configured-video", "not-configured-metadata", "manual-review-gate", "not-configured-publish"),
            persisted.connectorBindings.map { it.connectorId }.distinct()
        )
        assertEquals("WAITING_USER", snapshot.steps.first { it.stepType == "REVIEW" }.status)
        assertEquals("GENERATION_VERIFIED", credentialStore.getImageProviderConfigurationStatus(AutomationImageProviders.OPENAI_IMAGES).state)
        assertEquals("JOB_CREATED", persisted.outboxEvents.single().eventType)
    }

    @Test
    fun firstSceneFailureStopsRemainingSceneRequestsAndCreatesNoImageArtifacts() = runBlocking {
        openAiConnector.failureOnOrdinal = 1

        val snapshot = facade.generateAutomationContent(validRequest("Fail fast image flow"))

        assertEquals("FAILED", snapshot.status)
        assertEquals("FAILED", snapshot.steps.first { it.stepType == "IMAGES_VISUALS" }.status)
        assertEquals(listOf(1), openAiConnector.requestOrdinals)
        assertEquals(2, snapshot.artifacts.count { it.artifactType == "TEXT" })
        assertEquals(0, snapshot.artifacts.count { it.artifactType == "IMAGE" })
    }

    @Test
    fun retryImageStepWithDifferentProviderPreservesContentAndScenePrompts() = runBlocking {
        val firstRun = facade.generateAutomationContent(validRequest("Retry image provider"))
        credentialStore.saveImageProviderConfiguration(
            providerId = AutomationImageProviders.CLOUDFLARE_WORKERS_AI,
            apiKey = "cf-secret",
            model = "@cf/black-forest-labs/flux-1-schnell",
            accountId = "account-1"
        )

        val retried = facade.retryImageStep(
            jobId = firstRun.jobId,
            providerId = AutomationImageProviders.CLOUDFLARE_WORKERS_AI
        )

        assertEquals("Noi dung that tu Gemini", retried.generatedText)
        assertEquals(listOf(1, 2, 3), retried.scenePrompts.map { it.ordinal })
        assertEquals(1, contentConnector.generateCount)
        assertEquals(3, cloudflareConnector.requestOrdinals.size)
        assertEquals(3, retried.artifacts.count { it.artifactType == "IMAGE" })
        assertEquals("COMPLETED", retried.steps.first { it.stepType == "IMAGES_VISUALS" }.status)
        assertEquals("GENERATION_VERIFIED", credentialStore.getImageProviderConfigurationStatus(AutomationImageProviders.CLOUDFLARE_WORKERS_AI).state)
    }

    @Test
    fun imageStageUsesSceneSpecificQueriesInsteadOfFullPrompt() = runBlocking {
        val snapshot = facade.generateAutomationContent(
            validRequest("Tao video Shorts 9:16. Bat buoc tao dung 12 scene voi format chi tiet.\nChu de: giao tiep cong so tu tin")
        )

        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "IMAGES_VISUALS" }.status)
        assertEquals(listOf("active listening meeting", "friendly conversation", "respectful feedback"), openAiConnector.requestPrompts)
        assertTrue(openAiConnector.requestPrompts.none { it.contains("Bat buoc tao dung 12 scene") })
    }

    @Test
    fun voiceReadyCreatesVideoRenderPlanWithoutMp4Artifact() = runBlocking {
        credentialStore.saveVoiceProviderConfiguration(
            VoiceProviderCredentialConfiguration(
                providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                locale = "vi-VN",
                voiceId = "google-vi-1",
                outputFormat = "wav"
            )
        )

        val snapshot = createFacade().generateAutomationContent(validRequest("Video render plan"))

        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VOICE" }.status)
        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "SUBTITLE" }.status)
        assertEquals("COMPLETED", snapshot.steps.first { it.stepType == "VIDEO" }.status)
        assertEquals("VIDEO_RENDER_PLAN_READY", snapshot.steps.first { it.stepType == "VIDEO" }.waitingReason)
        assertEquals(1, snapshot.artifacts.count { it.artifactType == "VIDEO_RENDER_PLAN" })
        assertEquals(0, snapshot.artifacts.count { it.mimeType == "video/mp4" })
        assertEquals(1, voiceConnector.generateCount)
        assertEquals(3, snapshot.videoRenderPlan?.sceneCount)
        assertEquals(3, snapshot.videoRenderPlan?.scenes?.size)
    }

    @Test
    fun providerNotImplementedDoesNotAutoFallbackToPaidProvider() = runBlocking {
        credentialStore.setSelectedImageProviderId(AutomationImageProviders.HUGGINGFACE_INFERENCE)

        val snapshot = facade.generateAutomationContent(validRequest("Do not fallback"))

        assertEquals("WAITING_USER", snapshot.status)
        assertEquals("NOT_CONFIGURED", snapshot.steps.first { it.stepType == "IMAGES_VISUALS" }.status)
        assertEquals("IMAGE_PROVIDER_NOT_IMPLEMENTED", snapshot.steps.first { it.stepType == "IMAGES_VISUALS" }.waitingReason)
        assertTrue(openAiConnector.requestOrdinals.isEmpty())
        assertTrue(cloudflareConnector.requestOrdinals.isEmpty())
    }

    private fun validRequest(topic: String = "Meo quay video mon an tai nha"): AutomationContentRunRequest {
        return AutomationContentRunRequest(
            topic = topic,
            language = "vi",
            contentType = "video_script",
            promptTemplate = "",
            maximumOutputLength = 4000
        )
    }

    private fun createFacade(): AutomationFacade {
        return AutomationFacade.create(
            repository = repository,
            artifactStore = FakeArtifactStore(),
            connectorRegistry = AutomationConnectorRegistry.of(
                "gemini",
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationImageProviders.CLOUDFLARE_WORKERS_AI
            ),
            credentialStore = credentialStore,
            contentConnector = contentConnector,
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
            )
        )
    }

    private class FakeCredentialStore : AutomationCredentialStore {
        private var geminiConfig: GeminiCredentialConfiguration? = GeminiCredentialConfiguration(
            apiKey = "gemini-secret",
            model = "gemini-2.5-flash"
        )
        private val imageConfigs = linkedMapOf<String, ImageProviderCredentialConfiguration>()
        private val imageStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        private var selectedImageProviderId: String? = null

        override fun saveGeminiConfiguration(apiKey: String, model: String) {
            geminiConfig = GeminiCredentialConfiguration(apiKey = apiKey.trim(), model = model.trim())
        }

        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = geminiConfig

        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_CONNECTED,
                providerId = AutomationCredentialStore.GEMINI_PROVIDER_ID,
                model = geminiConfig?.model
            )
        }

        override fun markGeminiInvalid(message: String?) = Unit

        override fun clearGeminiConfiguration() {
            geminiConfig = null
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
            return imageStatuses[normalizedProviderId]
                ?: AutomationCredentialStatusSnapshot(
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

        private var selectedVoiceProviderId: String? = null
        private val voiceConfigs = linkedMapOf<String, VoiceProviderCredentialConfiguration>()
        private val voiceStatuses = linkedMapOf<String, AutomationCredentialStatusSnapshot>()
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            val normalizedProviderId = providerId.trim().lowercase()
            return voiceStatuses[normalizedProviderId]
                ?: AutomationCredentialStatusSnapshot(
                    state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                    providerId = normalizedProviderId
                )
        }
        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
            val normalizedProviderId = configuration.providerId.trim().lowercase()
            voiceConfigs[normalizedProviderId] = configuration.copy(providerId = normalizedProviderId)
            voiceStatuses[normalizedProviderId] = AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED,
                providerId = normalizedProviderId,
                model = configuration.model,
                voiceId = configuration.voiceId,
                locale = configuration.locale,
                engineName = configuration.engineName
            )
            selectedVoiceProviderId = normalizedProviderId
        }
        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? {
            return voiceConfigs[providerId.trim().lowercase()]
        }
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) {
            val normalizedProviderId = providerId.trim().lowercase()
            val existing = voiceStatuses[normalizedProviderId]
            val config = voiceConfigs[normalizedProviderId]
            voiceStatuses[normalizedProviderId] = AutomationCredentialStatusSnapshot(
                state = state,
                providerId = normalizedProviderId,
                model = existing?.model ?: config?.model,
                message = message,
                voiceId = existing?.voiceId ?: config?.voiceId,
                locale = existing?.locale ?: config?.locale,
                engineName = existing?.engineName ?: config?.engineName
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

    private class FakeArtifactStore : AutomationArtifactStore {
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
                artifactId = "artifact-$jobId-image-$ordinal-$providerId",
                artifactType = "IMAGE",
                mimeType = mimeType,
                uri = "automation://artifact/$jobId-image-$ordinal-$providerId",
                sizeBytes = bytes.size.toLong(),
                sourceUrl = sourceUrl,
                sceneId = sceneId,
                ordinal = ordinal,
                providerRequestId = providerRequestId,
                previewDataUrl = "data:$mimeType;base64,preview-$ordinal-$providerId"
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
                sourceUrl = "provider=$providerId;voice=$voiceId;locale=$locale;durationMs=${durationMs ?: 0};chunks=$chunkCount;inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount"
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
        var generateCount: Int = 0

        override suspend fun testConnection(config: ContentProviderConfig): ContentGenerationResult {
            return ContentGenerationResult(
                generatedText = "OK",
                providerId = config.providerId,
                model = config.model
            )
        }

        override suspend fun generateContent(
            config: ContentProviderConfig,
            request: ContentGenerationRequest
        ): ContentGenerationResult {
            generateCount += 1
            return ContentGenerationResult(
                generatedText = "Noi dung that tu Gemini",
                providerId = config.providerId,
                model = config.model,
                requestId = "request-1"
            )
        }
    }

    private class FakeScenePromptGenerator : ScenePromptGenerator {
        override suspend fun generateScenePrompts(
            request: ScenePromptGenerationRequest
        ): List<ScenePrompt> {
            return listOf(
                ScenePrompt("scene-1", 1, "Summary 1", "Prompt anh 1", "Negative 1", "9:16", stockSearchQuery = "active listening meeting"),
                ScenePrompt("scene-2", 2, "Summary 2", "Prompt anh 2", "Negative 2", "9:16", stockSearchQuery = "friendly conversation"),
                ScenePrompt("scene-3", 3, "Summary 3", "Prompt anh 3", "Negative 3", "9:16", stockSearchQuery = "respectful feedback")
            )
        }
    }

    private class FakeImageConnector(
        private val providerId: String
    ) : ImageGenerationConnector {
        var failureOnOrdinal: Int? = null
        val requestOrdinals = mutableListOf<Int>()
        val requestPrompts = mutableListOf<String>()

        override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult {
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
            requestOrdinals += request.ordinal
            requestPrompts += request.prompt
            if (failureOnOrdinal == request.ordinal) {
                throw ImageProviderException(
                    ImageProviderErrorCode.INVALID_API_KEY,
                    "Provider tu choi credential hien tai."
                )
            }
            return ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = providerId,
                model = config.model,
                providerRequestId = "request-${providerId}-${request.ordinal}",
                mimeType = "image/png",
                bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
        }
    }

    private class FakeVoiceConnector : VoiceGenerationConnector {
        var generateCount: Int = 0

        override fun listVoices(): List<VoiceDefinition> {
            return listOf(
                VoiceDefinition(
                    voiceId = "google-vi-1",
                    displayName = "Google VI",
                    locale = "vi-VN",
                    engineName = "Google TTS",
                    networkRequired = false,
                    installed = true,
                    isDefault = true
                )
            )
        }

        override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
            return VoiceProviderConnectionResult(
                providerId = config.providerId,
                model = config.model,
                voiceId = config.voiceId,
                locale = config.locale,
                engineName = "Google TTS",
                voiceCount = 1
            )
        }

        override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String): VoiceSampleResult {
            return VoiceSampleResult(
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "audio/wav",
                providerId = config.providerId,
                voiceId = config.voiceId,
                locale = config.locale,
                engineName = "Google TTS"
            )
        }

        override suspend fun generateVoice(
            config: VoiceProviderConfig,
            request: VoiceGenerationRequest
        ): VoiceGenerationResult {
            generateCount += 1
            return VoiceGenerationResult(
                bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
                mimeType = "audio/wav",
                metadata = VoiceArtifactMetadata(
                    providerId = config.providerId,
                    voiceId = config.voiceId,
                    locale = config.locale,
                    chunkCount = 1,
                    durationMs = 9_000L,
                    checksum = "checksum-test",
                    engineName = "Google TTS"
                )
            )
        }

        override fun openProviderSettings() = Unit
    }
}

