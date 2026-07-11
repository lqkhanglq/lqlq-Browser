package com.lqlq.browser.automation.artifact

import android.content.Context
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

data class AutomationSavedArtifact(
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

data class AutomationExportedArtifact(
    val displayName: String,
    val mimeType: String,
    val contentUri: String,
    val displayPath: String,
    val sizeBytes: Long
)

interface AutomationArtifactStore {
    fun isFoundationReady(): Boolean

    suspend fun saveGeneratedTextArtifact(
        jobId: String,
        stepId: String,
        text: String,
        providerId: String,
        model: String
    ): AutomationSavedArtifact?

    suspend fun saveGeneratedImageArtifact(
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
    ): AutomationSavedArtifact?

    suspend fun saveGeneratedVoiceArtifact(
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
    ): AutomationSavedArtifact?

    suspend fun saveGeneratedVideoRenderPlanArtifact(
        jobId: String,
        stepId: String,
        json: String,
        rendererId: String,
        sourceSummary: String
    ): AutomationSavedArtifact?

    suspend fun saveGeneratedVideoFileArtifact(
        jobId: String,
        stepId: String,
        bytes: ByteArray,
        rendererId: String,
        mimeType: String,
        sourceUrl: String?
    ): AutomationSavedArtifact?

    suspend fun saveMetadataPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact?

    suspend fun saveReviewStateArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact?

    suspend fun savePublishPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact?

    suspend fun readArtifactBytes(
        artifact: AutomationSavedArtifact
    ): ByteArray?

    suspend fun exportVideoArtifactToDownloads(
        artifact: AutomationSavedArtifact,
        jobId: String
    ): AutomationExportedArtifact?

    companion object {
        fun empty(): AutomationArtifactStore = EmptyAutomationArtifactStore
    }
}

class AppPrivateAutomationArtifactStore(
    context: Context
) : AutomationArtifactStore {

    private val appContext = context.applicationContext
    private val artifactRoot = File(appContext.filesDir, "automation-artifacts")

    override fun isFoundationReady(): Boolean = true

    override suspend fun saveGeneratedTextArtifact(
        jobId: String,
        stepId: String,
        text: String,
        providerId: String,
        model: String
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val file = File(artifactRoot, "$artifactId.txt")
        val payload = buildString {
            appendLine("# provider=$providerId")
            appendLine("# model=$model")
            appendLine("# jobId=$jobId")
            appendLine("# stepId=$stepId")
            appendLine()
            append(text)
        }
        file.writeText(payload, StandardCharsets.UTF_8)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = "TEXT",
            mimeType = "text/plain",
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length()
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
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(artifactRoot, "$artifactId.$extension")
        file.writeBytes(bytes)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = "IMAGE",
            mimeType = mimeType,
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length(),
            sourceUrl = sourceUrl,
            sceneId = sceneId,
            ordinal = ordinal,
            providerRequestId = providerRequestId,
            previewDataUrl = "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
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
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val extension = when (mimeType) {
            "audio/wav" -> "wav"
            "audio/mpeg" -> "mp3"
            else -> "bin"
        }
        val file = File(artifactRoot, "$artifactId.$extension")
        file.writeBytes(bytes)
        validateAudioArtifact(file, durationMs)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = "VOICE",
            mimeType = mimeType,
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length(),
            sourceUrl = "provider=$providerId;voice=$voiceId;locale=$locale;chunks=$chunkCount;durationMs=${durationMs ?: 0};inputCharCount=$inputCharCount;inputSceneCount=$inputSceneCount"
        )
    }

    override suspend fun saveGeneratedVideoRenderPlanArtifact(
        jobId: String,
        stepId: String,
        json: String,
        rendererId: String,
        sourceSummary: String
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val file = File(artifactRoot, "$artifactId.json")
        file.writeText(json, StandardCharsets.UTF_8)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = "VIDEO_RENDER_PLAN",
            mimeType = "application/json",
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length(),
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
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val extension = when (mimeType) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            else -> "bin"
        }
        val file = File(artifactRoot, "$artifactId.$extension")
        file.writeBytes(bytes)
        validateVideoArtifact(file)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = "VIDEO_MP4",
            mimeType = mimeType,
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length(),
            sourceUrl = "renderer=$rendererId;${sourceUrl.orEmpty()}".trimEnd(';')
        )
    }

    override suspend fun saveMetadataPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact = saveJsonArtifact(jobId, stepId, "METADATA_PLAN", json)

    override suspend fun saveReviewStateArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact = saveJsonArtifact(jobId, stepId, "REVIEW_STATE", json)

    override suspend fun savePublishPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact = saveJsonArtifact(jobId, stepId, "PUBLISH_PLAN", json)

    override suspend fun readArtifactBytes(
        artifact: AutomationSavedArtifact
    ): ByteArray? = withContext(Dispatchers.IO) {
        findArtifactFile(artifact)?.readBytes()
    }

    override suspend fun exportVideoArtifactToDownloads(
        artifact: AutomationSavedArtifact,
        jobId: String
    ): AutomationExportedArtifact? = withContext(Dispatchers.IO) {
        require(artifact.artifactType == "VIDEO_MP4") { "Chi ho tro export VIDEO_MP4." }
        val sourceFile = findArtifactFile(artifact)
            ?: throw IllegalStateException("Khong tim thay video artifact trong app-private storage.")
        require(sourceFile.exists() && sourceFile.length() > 0L) {
            "Video artifact khong hop le de export."
        }
        val safeJobId = jobId
            .trim()
            .ifBlank { "job" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "job" }
        val fileName = "lqlq_video_${safeJobId}_${System.currentTimeMillis()}.mp4"
        val mimeType = artifact.mimeType.ifBlank { "video/mp4" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/LQLQAutomation"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Khong tao duoc muc Downloads trong MediaStore.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Khong mo duoc output stream cho file export.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                AutomationExportedArtifact(
                    displayName = fileName,
                    mimeType = mimeType,
                    contentUri = uri.toString(),
                    displayPath = "Downloads/LQLQAutomation/$fileName",
                    sizeBytes = sourceFile.length()
                )
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "LQLQAutomation")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            require(targetDir.exists() && targetDir.isDirectory) {
                "Khong tao duoc thu muc Downloads/LQLQAutomation."
            }
            val targetFile = File(targetDir, fileName)
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            AutomationExportedArtifact(
                displayName = fileName,
                mimeType = mimeType,
                contentUri = Uri.fromFile(targetFile).toString(),
                displayPath = targetFile.absolutePath,
                sizeBytes = targetFile.length()
            )
        }
    }

    private fun findArtifactFile(artifact: AutomationSavedArtifact): File? {
        val artifactId = artifact.uri.substringAfterLast('/')
            .ifBlank { artifact.artifactId }
        return artifactRoot.listFiles()
            ?.firstOrNull { it.nameWithoutExtension == artifactId }
    }

    private suspend fun saveJsonArtifact(
        jobId: String,
        stepId: String,
        artifactType: String,
        json: String
    ): AutomationSavedArtifact = withContext(Dispatchers.IO) {
        artifactRoot.mkdirs()
        val artifactId = "artifact-${UUID.randomUUID().toString().substring(0, 8)}"
        val file = File(artifactRoot, "$artifactId.json")
        file.writeText(json, StandardCharsets.UTF_8)
        AutomationSavedArtifact(
            artifactId = artifactId,
            artifactType = artifactType,
            mimeType = "application/json",
            uri = "automation://artifact/$artifactId",
            sizeBytes = file.length(),
            sourceUrl = "jobId=$jobId;stepId=$stepId"
        )
    }

    private fun validateAudioArtifact(file: File, expectedDurationMs: Long?) {
        require(file.exists() && file.length() > 0) { "Voice artifact file is empty." }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
            require(duration > 0L || (expectedDurationMs ?: 0L) > 0L) {
                "Voice artifact duration is invalid."
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun validateVideoArtifact(file: File) {
        require(file.exists() && file.length() > 0) { "Video artifact file is empty." }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration > 0L) { "Video artifact duration is invalid." }
        } finally {
            runCatching { retriever.release() }
        }
    }
}

private object EmptyAutomationArtifactStore : AutomationArtifactStore {
    override fun isFoundationReady(): Boolean = true

    override suspend fun saveGeneratedTextArtifact(
        jobId: String,
        stepId: String,
        text: String,
        providerId: String,
        model: String
    ): AutomationSavedArtifact? = null

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

    override suspend fun readArtifactBytes(
        artifact: AutomationSavedArtifact
    ): ByteArray? = null

    override suspend fun saveMetadataPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact? = null

    override suspend fun saveReviewStateArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact? = null

    override suspend fun savePublishPlanArtifact(
        jobId: String,
        stepId: String,
        json: String
    ): AutomationSavedArtifact? = null

    override suspend fun exportVideoArtifactToDownloads(
        artifact: AutomationSavedArtifact,
        jobId: String
    ): AutomationExportedArtifact? = null
}
