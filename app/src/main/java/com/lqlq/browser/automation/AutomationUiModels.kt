package com.lqlq.browser.automation

import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.metadata.MetadataPlan
import com.lqlq.browser.automation.publish.PublishPlan
import com.lqlq.browser.automation.review.ReviewState

data class AutomationContentRunRequest(
    val topic: String,
    val language: String,
    val contentType: String,
    val promptTemplate: String,
    val maximumOutputLength: Int,
    val desiredDurationSeconds: Int? = null,
    val requestedSceneCount: Int? = null,
    val clientRequestId: String? = null,
    val videoRendererMode: String = "android_native_render",
    val videoWorkerUrl: String? = null
)

data class AutomationConnectionTestResult(
    val state: String,
    val providerId: String,
    val model: String?,
    val message: String
)

data class AutomationVideoExportResult(
    val displayName: String,
    val mimeType: String,
    val contentUri: String,
    val displayPath: String,
    val sizeBytes: Long
)

data class ImportedAutomationImage(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class AutomationUiImageProviderSnapshot(
    val providerId: String,
    val displayName: String,
    val authType: String,
    val costType: String,
    val health: String,
    val selected: Boolean,
    val supportedModels: List<String>,
    val defaultModel: String?,
    val syncOrAsync: String,
    val supportsPolling: Boolean,
    val supportsAspectRatio: Boolean,
    val supportsNegativePrompt: Boolean,
    val supportedOutputFormats: List<String>,
    val maxImagesPerJob: Int,
    val stabilityLevel: String,
    val configurationStatus: AutomationCredentialStatusSnapshot
)

data class AutomationUiVoiceProviderSnapshot(
    val providerId: String,
    val displayName: String,
    val authType: String,
    val costType: String,
    val health: String,
    val selected: Boolean,
    val supportedLocales: List<String>,
    val verifiedLocales: List<String>,
    val defaultLocale: String,
    val supportedOutputFormats: List<String>,
    val supportsPitch: Boolean,
    val supportsSpeechRate: Boolean,
    val supportsSamplePreview: Boolean,
    val supportsChunking: Boolean,
    val requiresCredentials: Boolean,
    val stabilityLevel: String,
    val configurationStatus: AutomationCredentialStatusSnapshot
)

data class AutomationUiVoiceDefinitionSnapshot(
    val voiceId: String,
    val displayName: String,
    val locale: String,
    val engineName: String,
    val networkRequired: Boolean,
    val installed: Boolean,
    val isDefault: Boolean,
    val genderHint: String? = null
)

data class AutomationUiJobSnapshot(
    val jobId: String,
    val projectId: String,
    val workflowId: String,
    val workflowVersion: Int,
    val topic: String,
    val status: String,
    val createdAtEpochMs: Long,
    val publishMode: String,
    val steps: List<AutomationUiStepSnapshot>,
    val dependencies: List<AutomationUiDependencySnapshot>,
    val generatedText: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val requestId: String? = null,
    val usageMetadata: Map<String, Long> = emptyMap(),
    val scenePrompts: List<AutomationUiScenePromptSnapshot> = emptyList(),
    val assetPlans: List<AutomationUiAssetPlanSnapshot> = emptyList(),
    val videoRenderPlan: AutomationUiVideoRenderPlanSnapshot? = null,
    val metadataPlan: MetadataPlan? = null,
    val reviewState: ReviewState? = null,
    val publishPlan: PublishPlan? = null,
    val artifacts: List<AutomationUiArtifactSnapshot> = emptyList(),
    val runtimeMessage: String? = null
)

data class AutomationUiStepSnapshot(
    val stepId: String,
    val stepKey: String,
    val stepType: String,
    val status: String,
    val connectorBindingId: String?,
    val waitingReason: String?
)

data class AutomationUiDependencySnapshot(
    val dependencyId: String,
    val fromStepId: String,
    val toStepId: String
)

data class AutomationUiArtifactSnapshot(
    val artifactId: String,
    val artifactType: String,
    val mimeType: String,
    val uri: String,
    val sizeBytes: Long,
    val sourceUrl: String? = null,
    val sceneId: String? = null,
    val ordinal: Int? = null,
    val providerRequestId: String? = null,
    val previewDataUrl: String? = null
)

data class AutomationUiScenePromptSnapshot(
    val sceneId: String,
    val ordinal: Int,
    val summary: String,
    val visualPrompt: String,
    val negativePrompt: String?,
    val aspectRatio: String,
    val voiceText: String,
    val onScreenText: String,
    val plannedDurationMs: Long,
    val stockSearchQuery: String,
    val visualDirection: String
)

data class AutomationUiAssetPlanSnapshot(
    val sceneId: String,
    val ordinal: Int,
    val strategy: String,
    val preferredProviderId: String?,
    val assetQuery: String,
    val templateId: String,
    val renderMode: String,
    val durationMs: Long,
    val rationale: String
)

data class AutomationUiVideoRenderPlanSnapshot(
    val rendererId: String,
    val planVersion: Int,
    val renderTarget: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val voiceArtifactUri: String,
    val voiceMimeType: String,
    val sceneCount: Int,
    val totalDurationMs: Long,
    val handoffHints: List<String>,
    val scenes: List<AutomationUiVideoRenderSceneSnapshot>
)

data class AutomationUiVideoRenderSceneSnapshot(
    val sceneId: String,
    val ordinal: Int,
    val summary: String,
    val visualPrompt: String,
    val imageArtifactUri: String,
    val imageArtifactId: String,
    val renderMode: String,
    val templateId: String,
    val strategy: String,
    val durationMs: Long,
    val subtitleText: String
)

data class AutomationUiRecentJob(
    val jobId: String,
    val topic: String,
    val status: String,
    val createdAtEpochMs: Long
)
