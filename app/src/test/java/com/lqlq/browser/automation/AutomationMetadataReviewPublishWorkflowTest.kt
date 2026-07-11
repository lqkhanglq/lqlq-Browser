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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationMetadataReviewPublishWorkflowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var facade: AutomationFacade

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        facade = AutomationFacade.create(
            repository = RoomAutomationRepository(database),
            artifactStore = RecordingArtifactStore(),
            connectorRegistry = AutomationConnectorRegistry.of(
                "gemini",
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS
            ),
            credentialStore = WorkflowCredentialStore(),
            contentConnector = FakeContentConnector(),
            scenePromptGenerator = FakeScenePromptGenerator(),
            imageProviderRegistry = DefaultImageProviderRegistry(
                implementedProviderIds = setOf(AutomationImageProviders.OPENAI_IMAGES)
            ),
            imageConnectors = mapOf(
                AutomationImageProviders.OPENAI_IMAGES to FakeImageConnector()
            ),
            voiceProviderRegistry = DefaultVoiceProviderRegistry(
                implementedProviderIds = setOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
            ),
            voiceConnectors = mapOf(
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS to FakeVoiceConnector()
            ),
            videoRenderWorkerClient = FakeVideoWorkerClient()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun videoReadyCreatesMetadataAndReviewAndApproveEnablesPublish() = runBlocking {
        val created = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "Chu de review publish mvp",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000,
                videoRendererMode = AutomationFacade.VIDEO_RENDERER_MODE_EXTERNAL,
                videoWorkerUrl = "http://127.0.0.1:8787"
            )
        )

        assertEquals("COMPLETED", created.steps.first { it.stepType == "VIDEO" }.status)
        assertEquals("VIDEO_MP4_READY", created.steps.first { it.stepType == "VIDEO" }.waitingReason)
        assertEquals("COMPLETED", created.steps.first { it.stepType == "METADATA" }.status)
        assertEquals("METADATA_PLAN_READY", created.steps.first { it.stepType == "METADATA" }.waitingReason)
        assertEquals("WAITING_USER", created.steps.first { it.stepType == "REVIEW" }.status)
        assertEquals("REVIEW_WAITING_FOR_APPROVAL", created.steps.first { it.stepType == "REVIEW" }.waitingReason)
        assertEquals("WAITING_USER", created.steps.first { it.stepType == "PUBLISH" }.status)
        assertEquals("PUBLISH_WAITING_FOR_REVIEW_APPROVAL", created.steps.first { it.stepType == "PUBLISH" }.waitingReason)
        assertNotNull(created.metadataPlan)
        assertNotNull(created.reviewState)
        assertTrue(created.artifacts.any { it.artifactType == "METADATA_PLAN" })
        assertTrue(created.artifacts.any { it.artifactType == "REVIEW_STATE" })

        val approved = facade.approveAutomationReview(created.jobId)

        assertEquals("APPROVED", approved.reviewState?.status)
        assertEquals("COMPLETED", approved.steps.first { it.stepType == "REVIEW" }.status)
        assertEquals("REVIEW_APPROVED", approved.steps.first { it.stepType == "REVIEW" }.waitingReason)
        assertEquals("WAITING_USER", approved.steps.first { it.stepType == "PUBLISH" }.status)
        assertEquals("PUBLISH_READY_MANUAL_ASSISTED", approved.steps.first { it.stepType == "PUBLISH" }.waitingReason)
        assertNotNull(approved.publishPlan)
        assertTrue(approved.artifacts.any { it.artifactType == "PUBLISH_PLAN" })

        val share = facade.preparePublishShare(created.jobId)
        assertTrue(share.shareText.contains(approved.metadataPlan?.title.orEmpty()))
        assertTrue(share.export.displayPath.contains("Downloads/LQLQAutomation"))

        val published = facade.markAutomationPublished(created.jobId)
        assertEquals("COMPLETED", published.steps.first { it.stepType == "PUBLISH" }.status)
        assertEquals("PUBLISH_MARKED_COMPLETED", published.steps.first { it.stepType == "PUBLISH" }.waitingReason)
        assertEquals("PUBLISHED_MARKED", published.publishPlan?.status)
    }

    private class WorkflowCredentialStore : AutomationCredentialStore {
        private val gemini = GeminiCredentialConfiguration(apiKey = "gemini-key", model = "gemini-2.5-flash")
        private val image = ImageProviderCredentialConfiguration(
            AutomationImageProviders.OPENAI_IMAGES,
            "image-key",
            OpenAiImageConnector.DEFAULT_MODEL,
            null
        )
        private val voice = VoiceProviderCredentialConfiguration(
            providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
            locale = "vi-VN",
            voiceId = "google-vi",
            outputFormat = "wav"
        )

        override fun saveGeminiConfiguration(apiKey: String, model: String) = Unit
        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = gemini
        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONNECTED", "gemini", gemini.model)
        override fun markGeminiInvalid(message: String?) = Unit
        override fun clearGeminiConfiguration() = Unit
        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) = Unit
        override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? = image
        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONFIG_SAVED", providerId, image.model)
        override fun markImageProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearImageProviderConfiguration(providerId: String) = Unit
        override fun setSelectedImageProviderId(providerId: String) = Unit
        override fun getSelectedImageProviderId(): String? = AutomationImageProviders.OPENAI_IMAGES
        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) = Unit
        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = voice
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONFIG_SAVED", providerId, voice.model, voiceId = voice.voiceId, locale = voice.locale)
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearVoiceProviderConfiguration(providerId: String) = Unit
        override fun setSelectedVoiceProviderId(providerId: String) = Unit
        override fun getSelectedVoiceProviderId(): String? = AutomationVoiceProviders.ANDROID_SYSTEM_TTS
    }

    private class RecordingArtifactStore : AutomationArtifactStore {
        override fun isFoundationReady(): Boolean = true

        override suspend fun saveGeneratedTextArtifact(jobId: String, stepId: String, text: String, providerId: String, model: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("text-$stepId", "TEXT", "text/plain", "automation://text/$stepId", text.length.toLong())
        }

        override suspend fun saveGeneratedImageArtifact(jobId: String, stepId: String, bytes: ByteArray, providerId: String, model: String, mimeType: String, sourceUrl: String?, sceneId: String, ordinal: Int, providerRequestId: String?): AutomationSavedArtifact {
            return AutomationSavedArtifact("image-$ordinal", "IMAGE", mimeType, "automation://image/$ordinal", bytes.size.toLong(), sourceUrl, sceneId, ordinal, providerRequestId, "data:$mimeType;base64,preview")
        }

        override suspend fun saveGeneratedVoiceArtifact(jobId: String, stepId: String, bytes: ByteArray, providerId: String, voiceId: String?, locale: String, mimeType: String, durationMs: Long?, chunkCount: Int, inputCharCount: Int, inputSceneCount: Int): AutomationSavedArtifact {
            return AutomationSavedArtifact("voice-$jobId", "VOICE", mimeType, "automation://voice/$jobId", bytes.size.toLong(), "provider=$providerId;voice=$voiceId;locale=$locale;durationMs=${durationMs ?: 0};chunks=$chunkCount;inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount")
        }

        override suspend fun saveGeneratedVideoRenderPlanArtifact(jobId: String, stepId: String, json: String, rendererId: String, sourceSummary: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-plan-$jobId", "VIDEO_RENDER_PLAN", "application/json", "automation://video-plan/$jobId", json.length.toLong(), "renderer=$rendererId;$sourceSummary")
        }

        override suspend fun saveGeneratedVideoFileArtifact(jobId: String, stepId: String, bytes: ByteArray, rendererId: String, mimeType: String, sourceUrl: String?): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-file-$jobId", "VIDEO_MP4", mimeType, "automation://video-file/$jobId", bytes.size.toLong(), sourceUrl)
        }

        override suspend fun saveMetadataPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("metadata-$jobId", "METADATA_PLAN", "application/json", "automation://metadata/$jobId", json.length.toLong())
        }

        override suspend fun saveReviewStateArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("review-$jobId", "REVIEW_STATE", "application/json", "automation://review/$jobId", json.length.toLong())
        }

        override suspend fun savePublishPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("publish-$jobId", "PUBLISH_PLAN", "application/json", "automation://publish/$jobId", json.length.toLong())
        }

        override suspend fun readArtifactBytes(artifact: AutomationSavedArtifact): ByteArray? = when (artifact.artifactType) {
            "IMAGE" -> byteArrayOf(1, 2, 3)
            "VOICE" -> byteArrayOf(4, 5, 6)
            else -> null
        }

        override suspend fun exportVideoArtifactToDownloads(artifact: AutomationSavedArtifact, jobId: String): AutomationExportedArtifact? {
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
        override suspend fun testConnection(config: ContentProviderConfig): ContentGenerationResult {
            return ContentGenerationResult("OK", config.providerId, config.model)
        }

        override suspend fun generateContent(config: ContentProviderConfig, request: ContentGenerationRequest): ContentGenerationResult {
            return ContentGenerationResult(
                generatedText = "Noi dung that duoc tao boi Gemini cho video ngan.",
                providerId = config.providerId,
                model = config.model,
                requestId = "request-1"
            )
        }
    }

    private class FakeScenePromptGenerator : ScenePromptGenerator {
        override suspend fun generateScenePrompts(request: ScenePromptGenerationRequest): List<ScenePrompt> {
            return listOf(
                ScenePrompt("scene-1", 1, "Intro", "Prompt anh 1", "Negative 1", "9:16", voiceText = "Mo dau ngan gon", plannedDurationMs = 4_000L),
                ScenePrompt("scene-2", 2, "Muc 1", "Prompt anh 2", "Negative 2", "9:16", voiceText = "Noi dung chinh cho muc mot", plannedDurationMs = 8_000L),
                ScenePrompt("scene-3", 3, "Outro", "Prompt anh 3", "Negative 3", "9:16", voiceText = "Ket lai ngan gon", plannedDurationMs = 4_000L)
            )
        }
    }

    private class FakeImageConnector : ImageGenerationConnector {
        override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult {
            return ImageProviderConnectionResult(config.providerId, config.model, "ok")
        }

        override suspend fun generateImage(config: ImageProviderConfig, request: ImageGenerationRequest): ImageGenerationResult {
            return ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = config.providerId,
                model = config.model,
                providerRequestId = "img-${request.ordinal}",
                mimeType = "image/png",
                bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
            )
        }
    }

    private class FakeVoiceConnector : VoiceGenerationConnector {
        override fun listVoices(): List<VoiceDefinition> = listOf(
            VoiceDefinition("google-vi", "Google VI", "vi-VN", "Google TTS", false, true, true)
        )

        override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
            return VoiceProviderConnectionResult(config.providerId, config.model, config.voiceId, config.locale, "Google TTS", 1)
        }

        override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String) =
            com.lqlq.browser.automation.connector.voice.VoiceSampleResult(byteArrayOf(1, 2, 3), "audio/wav", config.providerId, config.voiceId, config.locale, "Google TTS")

        override suspend fun generateVoice(config: VoiceProviderConfig, request: VoiceGenerationRequest): VoiceGenerationResult {
            return VoiceGenerationResult(
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "audio/wav",
                metadata = VoiceArtifactMetadata(
                    providerId = config.providerId,
                    voiceId = config.voiceId,
                    locale = config.locale,
                    chunkCount = 1,
                    durationMs = 16_000L,
                    checksum = "checksum",
                    engineName = "Google TTS"
                )
            )
        }

        override fun openProviderSettings() = Unit
    }

    private class FakeVideoWorkerClient : VideoRenderWorkerClient {
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
            return ExternalRenderedVideo(
                rendererId = "external-moviepy-worker",
                bytes = byteArrayOf(1, 2, 3, 4),
                mimeType = "video/mp4",
                downloadUrl = "http://127.0.0.1:8787/videos/test.mp4",
                durationMs = 16_000L,
                width = 1080,
                height = 1920,
                fps = 30,
                sceneCount = plan.sceneCount
            )
        }
    }
}





