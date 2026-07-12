package com.lqlq.browser.automation.publish

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import android.util.Base64

/**
 * Client dang video len YouTube bang YouTube Data API v3 CHINH THUC (giong donor
 * gemini-youtube-automation/src/uploader.py nhung port sang Android/Kotlin, dung
 * REST + OkHttp thay vi thu vien Python).
 *
 * Luong: (1) OAuth 2.0 Authorization Code + PKCE -> lay access_token + refresh_token
 * (nguoi dung cap quyen 1 LAN); (2) tu lam moi access_token bang refresh_token; (3)
 * upload resumable video + metadata (title/description/tags/privacy).
 *
 * Scope youtube.upload. Nguoi dung tu tao OAuth Client (Web application) tren Google
 * Cloud Console 1 lan, dan client_id + client_secret vao app.
 */
class YouTubePublishClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
) {

    data class Pkce(val verifier: String, val challenge: String)

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long
    )

    class YouTubePublishException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Sinh cap PKCE: verifier ngau nhien + challenge = base64url(sha256(verifier)). */
    fun generatePkce(): Pkce {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        val verifier = base64Url(bytes)
        val challengeBytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = base64Url(challengeBytes)
        return Pkce(verifier, challenge)
    }

    /** URL man hinh dong y Google - mo trong WebView, chan redirect de lay ?code=. */
    fun buildAuthUrl(clientId: String, redirectUri: String, codeChallenge: String): String {
        val scope = "https://www.googleapis.com/auth/youtube.upload"
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=" + urlEncode(clientId) +
            "&redirect_uri=" + urlEncode(redirectUri) +
            "&response_type=code" +
            "&scope=" + urlEncode(scope) +
            "&access_type=offline" +
            "&prompt=consent" +
            "&code_challenge=" + urlEncode(codeChallenge) +
            "&code_challenge_method=S256"
    }

    /** Doi authorization code -> access_token + refresh_token. */
    fun exchangeCode(
        clientId: String,
        clientSecret: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): TokenResult {
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", codeVerifier)
            .build()
        val json = postToken(form)
        return TokenResult(
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { null },
            expiresInSeconds = json.optLong("expires_in", 3600L)
        )
    }

    /** Lam moi access_token bang refresh_token (khong tra ve refresh_token moi). */
    fun refreshAccessToken(clientId: String, clientSecret: String, refreshToken: String): TokenResult {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val json = postToken(form)
        return TokenResult(
            accessToken = json.optString("access_token"),
            refreshToken = null,
            expiresInSeconds = json.optLong("expires_in", 3600L)
        )
    }

    private fun postToken(form: FormBody): JSONObject {
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw YouTubePublishException("Google OAuth loi (HTTP ${response.code}): ${shortError(body)}")
            }
            return runCatching { JSONObject(body) }.getOrElse {
                throw YouTubePublishException("Google OAuth tra ve JSON khong hop le.")
            }
        }
    }

    /**
     * Upload video (resumable) + metadata. Tra ve videoId. Video app-private nen file
     * doc truc tiep duoc. Videos ~10-30MB nen PUT mot lan la du (khong can chia chunk).
     */
    fun uploadVideo(
        accessToken: String,
        videoFile: File,
        title: String,
        description: String,
        tags: List<String>,
        privacyStatus: String
    ): String {
        require(videoFile.exists() && videoFile.length() > 0L) { "File video khong ton tai de dang." }
        val metadata = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", title.take(100).ifBlank { "Video" })
                put("description", description.take(4900))
                put("tags", JSONArray(tags.filter { it.isNotBlank() }.take(30)))
                put("categoryId", "22") // 22 = People & Blogs (an toan cho moi noi dung)
            })
            put("status", JSONObject().apply {
                put("privacyStatus", normalizePrivacy(privacyStatus))
                put("selfDeclaredMadeForKids", false)
            })
        }

        // Buoc 1: khoi tao phien resumable, lay upload URL tu header Location.
        val initRequest = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Type", "video/*")
            .header("X-Upload-Content-Length", videoFile.length().toString())
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val uploadUrl: String
        client.newCall(initRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw YouTubePublishException("YouTube tu choi khoi tao upload (HTTP ${response.code}): ${shortError(response.body?.string().orEmpty())}")
            }
            uploadUrl = response.header("Location")
                ?: throw YouTubePublishException("YouTube khong tra ve upload URL.")
        }

        // Buoc 2: PUT toan bo bytes video len upload URL.
        val mediaBody: RequestBody = videoFile.asRequestBody("video/*".toMediaType())
        val putRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $accessToken")
            .put(mediaBody)
            .build()
        client.newCall(putRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw YouTubePublishException("YouTube upload that bai (HTTP ${response.code}): ${shortError(body)}")
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
            return json?.optString("id").orEmpty().ifBlank {
                throw YouTubePublishException("YouTube khong tra ve video ID sau upload.")
            }
        }
    }

    private fun normalizePrivacy(value: String): String {
        return when (value.trim().lowercase()) {
            "public" -> "public"
            "unlisted" -> "unlisted"
            else -> "private"
        }
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun shortError(body: String): String {
        val trimmed = body.trim().take(300)
        return runCatching {
            val json = JSONObject(trimmed)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("error_description").ifBlank { json.optString("error") }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: trimmed
    }
}
