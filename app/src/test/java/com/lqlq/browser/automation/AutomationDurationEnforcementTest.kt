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
import com.lqlq.browser.automation.connector.voice.VoiceSampleResult
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
import com.lqlq.browser.automation.script.ContentDurationPolicy
import com.lqlq.browser.automation.script.ScriptSegmentKind
import com.lqlq.browser.automation.script.StructuredScript
import com.lqlq.browser.automation.script.StructuredScriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationDurationEnforcementTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var facade: AutomationFacade
    private lateinit var contentConnector: RepairAwareContentConnector
    private lateinit var voiceConnector: RecordingVoiceConnector

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contentConnector = RepairAwareContentConnector()
        voiceConnector = RecordingVoiceConnector()
        facade = AutomationFacade.create(
            repository = RoomAutomationRepository(database),
            artifactStore = RecordingArtifactStore(),
            connectorRegistry = AutomationConnectorRegistry.of(
                "gemini",
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS
            ),
            credentialStore = DurationCredentialStore(),
            contentConnector = contentConnector,
            scenePromptGenerator = StructuredScenePromptGenerator(),
            imageProviderRegistry = DefaultImageProviderRegistry(
                implementedProviderIds = setOf(AutomationImageProviders.OPENAI_IMAGES)
            ),
            imageConnectors = mapOf(AutomationImageProviders.OPENAI_IMAGES to RecordingImageConnector()),
            voiceProviderRegistry = DefaultVoiceProviderRegistry(
                implementedProviderIds = setOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS)
            ),
            voiceConnectors = mapOf(AutomationVoiceProviders.ANDROID_SYSTEM_TTS to voiceConnector)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Gemini (khong phai lqlq) tu quyet dinh so canh - lqlq khong con doan/ep buoc
     * so muc theo tu khoa chu de nua (vd "top 10"/"10 cau noi" KHONG con ep phai
     * dung 10 canh). Dieu duy nhat lqlq con kiem tra la TONG THOI LUONG loi doc co
     * dat toi thieu theo giay nguoi dung yeu cau hay khong (khong phu thuoc vao
     * doan chu de la loai gi) - test nay xac nhan retry van hoat dong dung khi
     * Gemini tra ve qua ngan so voi thoi luong dich, bat ke so canh la bao nhieu.
     */
    @Test
    fun insufficientDurationTriggersRepairRegardlessOfSceneCount() = runBlocking {
        val snapshot = facade.generateAutomationContent(
            AutomationContentRunRequest(
                topic = "10 cau noi giup ban giao tiep tot hon",
                language = "vi",
                contentType = "video_script",
                promptTemplate = "",
                maximumOutputLength = 4000,
                desiredDurationSeconds = 60
            )
        )

        assertTrue(contentConnector.callCount >= 2)
        assertTrue(snapshot.generatedText.orEmpty().length > 200)
        assertEquals(12_000L, snapshot.usageMetadata["maximumOutputLength"])

        val voiceArtifact = snapshot.artifacts.first { it.artifactType == "VOICE" }
        val inputCharCount = extractDebugLong(voiceArtifact.sourceUrl, "inputCharCount")
        val voiceDurationMs = extractDebugLong(voiceArtifact.sourceUrl, "durationMs")
        assertTrue((inputCharCount ?: 0) > 200)
        assertTrue((voiceDurationMs ?: 0) >= 60_000L)

        val plannedDurationMs = snapshot.videoRenderPlan?.totalDurationMs ?: 0L
        assertTrue(kotlin.math.abs(plannedDurationMs - (voiceDurationMs ?: 0L)) <= ((voiceDurationMs ?: 0L) * 0.15).toLong())
    }

    private fun extractDebugLong(source: String?, key: String): Long? {
        val prefix = "$key="
        return source.orEmpty().split(';')
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.toLongOrNull()
    }

    private class DurationCredentialStore : AutomationCredentialStore {
        private val gemini = GeminiCredentialConfiguration(apiKey = "gemini-key", model = "gemini-2.5-flash")
        private val image = ImageProviderCredentialConfiguration(AutomationImageProviders.OPENAI_IMAGES, "image-key", OpenAiImageConnector.DEFAULT_MODEL, null)
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
            return AutomationSavedArtifact("voice-$jobId", "VOICE", mimeType, "automation://voice/$jobId", bytes.size.toLong(), "provider=$providerId;voice=$voiceId;locale=$locale;chunks=$chunkCount;durationMs=${durationMs ?: 0};inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount")
        }
        override suspend fun saveGeneratedVideoRenderPlanArtifact(jobId: String, stepId: String, json: String, rendererId: String, sourceSummary: String): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-$jobId", "VIDEO_RENDER_PLAN", "application/json", "automation://video/$jobId", json.length.toLong(), "renderer=$rendererId;$sourceSummary")
        }
        override suspend fun saveGeneratedVideoFileArtifact(jobId: String, stepId: String, bytes: ByteArray, rendererId: String, mimeType: String, sourceUrl: String?): AutomationSavedArtifact {
            return AutomationSavedArtifact("video-file-$jobId", "VIDEO_MP4", mimeType, "automation://video-file/$jobId", bytes.size.toLong(), sourceUrl)
        }
        override suspend fun saveMetadataPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun saveReviewStateArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun savePublishPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null
        override suspend fun readArtifactBytes(artifact: AutomationSavedArtifact): ByteArray? = when (artifact.artifactType) {
            "IMAGE" -> byteArrayOf(1, 2, 3)
            "VOICE" -> byteArrayOf(4, 5, 6)
            else -> null
        }

        override suspend fun exportVideoArtifactToDownloads(
            artifact: AutomationSavedArtifact,
            jobId: String
        ): AutomationExportedArtifact? = null

        override suspend fun deleteArtifactByUri(uri: String): Boolean = true
    }

    private class RepairAwareContentConnector : ContentGenerationConnector {
        var callCount = 0

        override suspend fun testConnection(config: ContentProviderConfig): ContentGenerationResult {
            return ContentGenerationResult("OK", config.providerId, config.model)
        }

        override suspend fun generateContent(config: ContentProviderConfig, request: ContentGenerationRequest): ContentGenerationResult {
            callCount += 1
            val policy = request.durationPolicy ?: error("policy required")
            val itemCount = if (request.repairInstruction.isNullOrBlank()) 3 else (policy.targetItemCount ?: 10)
            val structured = StructuredScript(
                policy = policy,
                segments = buildSegments(itemCount, policy),
                rawResponse = "synthetic"
            )
            return ContentGenerationResult(
                generatedText = structured.fullVoiceText(),
                providerId = config.providerId,
                model = config.model,
                requestId = "req-$callCount",
                structuredScript = structured
            )
        }

        private fun buildSegments(itemCount: Int, policy: ContentDurationPolicy): List<StructuredScriptSegment> {
            val segments = mutableListOf<StructuredScriptSegment>()
            if (policy.includeIntro) {
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.INTRO,
                    index = null,
                    title = "Intro",
                    voiceText = "Hom nay chung ta di qua day du danh sach de ban khong bo lo bat ky cau nao.",
                    onScreenText = "Day du 10 cau noi",
                    visualQuery = "intro giao tiep",
                    durationMs = 4_000L
                )
            }
            repeat(itemCount) { index ->
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.ITEM,
                    index = index + 1,
                    title = "Cau noi ${index + 1}",
                    voiceText = "Cau noi so ${index + 1}. Day la giai thich ngan nhung ro rang cho cau noi so ${index + 1}, giup nguoi xem ap dung ngay vao giao tiep hang ngay.",
                    onScreenText = "Cau ${index + 1}",
                    visualQuery = "giao tiep cau ${index + 1}",
                    durationMs = 5_200L
                )
            }
            if (policy.includeOutro) {
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.OUTRO,
                    index = null,
                    title = "Outro",
                    voiceText = "Hay luu lai danh sach nay va luyen tap tung cau noi moi ngay.",
                    onScreenText = "Luyen tap moi ngay",
                    visualQuery = "outro giao tiep",
                    durationMs = 4_000L
                )
            }
            return segments
        }
    }

    private class StructuredScenePromptGenerator : ScenePromptGenerator {
        override suspend fun generateScenePrompts(request: ScenePromptGenerationRequest): List<ScenePrompt> {
            return request.structuredScript!!.segments.mapIndexed { index, segment ->
                ScenePrompt(
                    sceneId = "scene-${index + 1}",
                    ordinal = index + 1,
                    summary = when (segment.kind) {
                        ScriptSegmentKind.INTRO -> "Intro"
                        ScriptSegmentKind.OUTRO -> "Outro"
                        ScriptSegmentKind.ITEM -> "Muc ${segment.index}"
                    },
                    visualPrompt = segment.visualQuery,
                    negativePrompt = null,
                    aspectRatio = request.targetAspectRatio,
                    voiceText = segment.voiceText,
                    onScreenText = segment.onScreenText,
                    plannedDurationMs = segment.durationMs
                )
            }
        }
    }

    private class RecordingImageConnector : ImageGenerationConnector {
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

    private class RecordingVoiceConnector : VoiceGenerationConnector {
        var lastInputText: String = ""

        override fun listVoices(): List<VoiceDefinition> = listOf(
            VoiceDefinition("google-vi", "Google VI", "vi-VN", "Google TTS", false, true, true)
        )

        override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
            return VoiceProviderConnectionResult(config.providerId, config.model, config.voiceId, config.locale, "Google TTS", 1)
        }

        override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String): VoiceSampleResult {
            return VoiceSampleResult(byteArrayOf(1, 2, 3), "audio/wav", config.providerId, config.voiceId, config.locale, "Google TTS")
        }

        override suspend fun generateVoice(config: VoiceProviderConfig, request: VoiceGenerationRequest): VoiceGenerationResult {
            lastInputText = request.text
            return VoiceGenerationResult(
                bytes = byteArrayOf(1, 2, 3, 4, 5),
                mimeType = "audio/wav",
                metadata = VoiceArtifactMetadata(
                    providerId = config.providerId,
                    voiceId = config.voiceId,
                    locale = config.locale,
                    chunkCount = 3,
                    durationMs = 60_000L,
                    checksum = "voice-checksum",
                    engineName = "Google TTS"
                )
            )
        }

        override fun openProviderSettings() = Unit
    }
}
