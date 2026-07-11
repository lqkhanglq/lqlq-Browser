package com.lqlq.browser.automation.metadata

data class MetadataGenerationRequest(
    val topic: String,
    val generatedText: String,
    val sceneCount: Int,
    val imageCount: Int,
    val voiceDurationMs: Long,
    val videoDurationMs: Long,
    val language: String
)

data class PlatformMetadata(
    val title: String? = null,
    val description: String? = null,
    val caption: String? = null,
    val hashtags: List<String> = emptyList(),
    val visibility: String? = null
)

data class MetadataPlan(
    val schema: String = "lqlq.metadata_plan.v1",
    val title: String,
    val shortTitle: String,
    val description: String,
    val hashtags: List<String>,
    val language: String,
    val category: String,
    val thumbnailText: String,
    val platforms: Map<String, PlatformMetadata>,
    val sourceSceneCount: Int,
    val sourceVoiceDurationMs: Long,
    val sourceVideoDurationMs: Long,
    val safetyNotes: List<String>
)

interface MetadataGenerator {
    fun generate(request: MetadataGenerationRequest): MetadataPlan
}
