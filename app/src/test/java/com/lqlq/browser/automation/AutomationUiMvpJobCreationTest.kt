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
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.image.ScenePromptGenerationRequest
import com.lqlq.browser.automation.image.ScenePromptGenerator
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationUiMvpJobCreationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var repository: RoomAutomationRepository
    private lateinit var facade: AutomationFacade

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAutomationRepository(database)
        facade = AutomationFacade.create(
            repository = repository,
            artifactStore = FakeArtifactStore(),
            connectorRegistry = AutomationConnectorRegistry.of("gemini", AutomationImageProviders.OPENAI_IMAGES),
            credentialStore = FakeCredentialStoreWithoutImage(),
            contentConnector = FakeContentConnector(),
            scenePromptGenerator = FakeScenePromptGenerator()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validRequestCreatesPersistedEightStepJobGraphAndLeavesImageNotConfiguredWithoutKey() = runBlocking {
        val snapshot = facade.generateAutomationContent(validRequest())
        val persisted = repository.getJobGraph(snapshot.jobId)
        val reloaded = facade.getAutomationJob(snapshot.jobId)

        requireNotNull(persisted)
        requireNotNull(reloaded)

        assertEquals("Meo quay video mon an tai nha", snapshot.topic)
        assertEquals(
            listOf("TOPIC", "CONTENT", "SCENE_PROMPTS", "ASSET_PLAN", "IMAGES_VISUALS", "VOICE", "SUBTITLE", "VIDEO", "METADATA", "REVIEW", "PUBLISH"),
            snapshot.steps.map { it.stepType }
        )
        assertEquals(
            listOf("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "WAITING_USER", "WAITING_USER"),
            snapshot.steps.map { it.status }
        )
        assertEquals("Noi dung that tu Gemini", snapshot.generatedText)
        assertEquals("Noi dung that tu Gemini", reloaded.generatedText)
        assertEquals(3, snapshot.scenePrompts.size)
        assertEquals(2, snapshot.artifacts.size)
        assertEquals("WAITING_USER", snapshot.status)
        assertEquals(11, persisted.steps.size)
        assertEquals("JOB_CREATED", persisted.outboxEvents.single().eventType)
        assertTrue(snapshot.runtimeMessage.orEmpty().contains("image provider", ignoreCase = true))
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

    private class FakeCredentialStoreWithoutImage : AutomationCredentialStore {
        override fun saveGeminiConfiguration(apiKey: String, model: String) = Unit

        override fun getGeminiConfiguration(): GeminiCredentialConfiguration {
            return GeminiCredentialConfiguration(
                apiKey = "gemini-secret",
                model = "gemini-2.5-flash"
            )
        }

        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_CONNECTED,
                providerId = AutomationCredentialStore.GEMINI_PROVIDER_ID,
                model = "gemini-2.5-flash"
            )
        }

        override fun markGeminiInvalid(message: String?) = Unit
        override fun clearGeminiConfiguration() = Unit
        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) = Unit
        override fun getImageProviderConfiguration(providerId: String) = null

        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = providerId
            )
        }

        override fun markImageProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearImageProviderConfiguration(providerId: String) = Unit
        override fun setSelectedImageProviderId(providerId: String) = Unit
        override fun getSelectedImageProviderId(): String? = null
        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) = Unit
        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = null
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = providerId
            )
        }
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearVoiceProviderConfiguration(providerId: String) = Unit
        override fun setSelectedVoiceProviderId(providerId: String) = Unit
        override fun getSelectedVoiceProviderId(): String? = null
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
        ): AutomationSavedArtifact? = null

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
        ): AutomationSavedArtifact? = null

        override suspend fun saveGeneratedVideoRenderPlanArtifact(
            jobId: String,
            stepId: String,
            json: String,
            rendererId: String,
            sourceSummary: String
        ): AutomationSavedArtifact? = null

        override suspend fun saveGeneratedVideoFileArtifact(
            jobId: String,
            stepId: String,
            bytes: ByteArray,
            rendererId: String,
            mimeType: String,
            sourceUrl: String?
        ): AutomationSavedArtifact? = null

        override suspend fun saveMetadataPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun saveReviewStateArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun savePublishPlanArtifact(jobId: String, stepId: String, json: String): AutomationSavedArtifact? = null

        override suspend fun readArtifactBytes(
            artifact: AutomationSavedArtifact
        ): ByteArray? = null

        override suspend fun exportVideoArtifactToDownloads(
            artifact: AutomationSavedArtifact,
            jobId: String
        ): AutomationExportedArtifact? = null

        override suspend fun deleteArtifactByUri(uri: String): Boolean = true
    }

    private class FakeContentConnector : ContentGenerationConnector {
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
                ScenePrompt("scene-1", 1, "Summary 1", "Prompt anh 1", "Negative 1", "9:16"),
                ScenePrompt("scene-2", 2, "Summary 2", "Prompt anh 2", "Negative 2", "9:16"),
                ScenePrompt("scene-3", 3, "Summary 3", "Prompt anh 3", "Negative 3", "9:16")
            )
        }
    }
}

