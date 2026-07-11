package com.lqlq.browser.automation.video

import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.artifact.AutomationSavedArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class VideoRenderWorkerHealth(
    val status: String,
    val renderer: String,
    val version: String
)

data class ExternalRenderedVideo(
    val rendererId: String,
    val bytes: ByteArray,
    val mimeType: String,
    val downloadUrl: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val fps: Int?,
    val sceneCount: Int?
)

interface VideoRenderWorkerClient {
    suspend fun testWorker(workerUrl: String): VideoRenderWorkerHealth

    suspend fun renderVideo(
        workerUrl: String,
        plan: VideoRenderPlan,
        planJson: String,
        voiceArtifact: AutomationSavedArtifact,
        imageArtifacts: List<AutomationSavedArtifact>,
        artifactStore: AutomationArtifactStore
    ): ExternalRenderedVideo
}

class ExternalMoviePyVideoRenderer : VideoRenderWorkerClient {

    override suspend fun testWorker(workerUrl: String): VideoRenderWorkerHealth = withContext(Dispatchers.IO) {
        val connection = openConnection(resolveUrl(workerUrl, "/health"), "GET")
        try {
            val body = readResponseBody(connection)
            require(connection.responseCode in 200..299) { "Worker tra ve HTTP ${connection.responseCode}." }
            val json = JSONObject(body)
            VideoRenderWorkerHealth(
                status = json.optString("status", "unknown"),
                renderer = json.optString("renderer", "unknown"),
                version = json.optString("version", "unknown")
            )
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun renderVideo(
        workerUrl: String,
        plan: VideoRenderPlan,
        planJson: String,
        voiceArtifact: AutomationSavedArtifact,
        imageArtifacts: List<AutomationSavedArtifact>,
        artifactStore: AutomationArtifactStore
    ): ExternalRenderedVideo = withContext(Dispatchers.IO) {
        val boundary = "----lqlq-${UUID.randomUUID()}"
        val requestBytes = ByteArrayOutputStream().use { output ->
            appendTextPart(output, boundary, "renderPlanJson", planJson)
            appendTextPart(output, boundary, "metadataJson", JSONObject().put("jobId", plan.scenes.firstOrNull()?.sceneId ?: "").toString())

            val voiceBytes = requireNotNull(artifactStore.readArtifactBytes(voiceArtifact)) {
                "Khong doc duoc voice artifact de gui sang worker."
            }
            appendFilePart(
                output = output,
                boundary = boundary,
                fieldName = "voice",
                fileName = buildArtifactFileName("voice", voiceArtifact.mimeType, 0),
                mimeType = voiceArtifact.mimeType,
                bytes = voiceBytes
            )

            imageArtifacts.sortedBy { it.ordinal ?: Int.MAX_VALUE }.forEachIndexed { index, artifact ->
                val bytes = requireNotNull(artifactStore.readArtifactBytes(artifact)) {
                    "Khong doc duoc image artifact ordinal ${artifact.ordinal ?: index + 1}."
                }
                appendFilePart(
                    output = output,
                    boundary = boundary,
                    fieldName = "images",
                    fileName = buildArtifactFileName("scene", artifact.mimeType, artifact.ordinal ?: index + 1),
                    mimeType = artifact.mimeType,
                    bytes = bytes
                )
            }

            output.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
            output.toByteArray()
        }

        val renderConnection = openConnection(resolveUrl(workerUrl, "/render"), "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            renderConnection.outputStream.use { it.write(requestBytes) }
            val renderBody = readResponseBody(renderConnection)
            require(renderConnection.responseCode in 200..299) {
                "Worker render that bai voi HTTP ${renderConnection.responseCode}."
            }
            val json = JSONObject(renderBody)
            require(json.optString("status") == "completed") {
                "Worker chua hoan tat render MP4."
            }
            val downloadUrl = resolveDownloadUrl(workerUrl, json.optString("downloadUrl"))
            require(downloadUrl != null) { "Worker khong tra ve downloadUrl hop le." }

            val videoBytes = downloadBytes(downloadUrl)
            ExternalRenderedVideo(
                rendererId = WORKER_RENDERER_ID,
                bytes = videoBytes,
                mimeType = "video/mp4",
                downloadUrl = downloadUrl,
                durationMs = json.optLong("durationMs").takeIf { it > 0L },
                width = json.optInt("width").takeIf { it > 0 },
                height = json.optInt("height").takeIf { it > 0 },
                fps = json.optInt("fps").takeIf { it > 0 },
                sceneCount = json.optInt("sceneCount").takeIf { it > 0 }
            )
        } finally {
            renderConnection.disconnect()
        }
    }

    private fun appendTextPart(
        output: ByteArrayOutputStream,
        boundary: String,
        fieldName: String,
        value: String
    ) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"\r\n")
            writer.append("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            writer.append(value)
            writer.append("\r\n")
            writer.flush()
        }
    }

    private fun appendFilePart(
        output: ByteArrayOutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(fileName).append("\"\r\n")
            writer.append("Content-Type: ").append(mimeType).append("\r\n\r\n")
            writer.flush()
        }
        output.write(bytes)
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun buildArtifactFileName(prefix: String, mimeType: String, ordinal: Int): String {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            "audio/wav" -> "wav"
            "audio/mpeg" -> "mp3"
            else -> "bin"
        }
        return "${prefix}_${ordinal.toString().padStart(3, '0')}.$extension"
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = openConnection(url, "GET")
        try {
            require(connection.responseCode in 200..299) {
                "Khong tai duoc MP4 tu worker. HTTP ${connection.responseCode}."
            }
            return BufferedInputStream(connection.inputStream).use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            useCaches = false
        }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        val trimmedBase = baseUrl.trim().removeSuffix("/")
        return if (path.startsWith("/")) "$trimmedBase$path" else "$trimmedBase/$path"
    }

    private fun resolveDownloadUrl(baseUrl: String, downloadUrl: String?): String? {
        val normalized = downloadUrl?.trim()?.ifBlank { null } ?: return null
        return if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            normalized
        } else {
            val baseUri = URI(baseUrl.trim().removeSuffix("/") + "/")
            baseUri.resolve(normalized.removePrefix("/")).toString()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        val stream = try {
            connection.inputStream
        } catch (_: FileNotFoundException) {
            connection.errorStream
        }
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    companion object {
        const val WORKER_RENDERER_ID: String = "external-moviepy-worker"
    }
}

object NoOpVideoRenderWorkerClient : VideoRenderWorkerClient {
    override suspend fun testWorker(workerUrl: String): VideoRenderWorkerHealth {
        throw UnsupportedOperationException("Video render worker client chua duoc cau hinh.")
    }

    override suspend fun renderVideo(
        workerUrl: String,
        plan: VideoRenderPlan,
        planJson: String,
        voiceArtifact: AutomationSavedArtifact,
        imageArtifacts: List<AutomationSavedArtifact>,
        artifactStore: AutomationArtifactStore
    ): ExternalRenderedVideo {
        throw UnsupportedOperationException("Video render worker client chua duoc cau hinh.")
    }
}
