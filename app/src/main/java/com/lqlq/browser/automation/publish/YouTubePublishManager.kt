package com.lqlq.browser.automation.publish

import android.content.Context
import java.io.File

/**
 * Dieu phoi auto-upload YouTube: giu access_token luon hop le (tu lam moi bang
 * refresh_token), chay OAuth handshake, va upload video. Tach rieng khoi UI/bridge
 * de tai su dung tu WebView (OAuth) lan Worker (upload nen).
 */
object YouTubePublishManager {
    private val client = YouTubePublishClient()

    class NotConnectedException : Exception("Chua ket noi YouTube. Hay ket noi tai khoan truoc.")

    /** URL man hinh dong y Google + luu PKCE verifier de doi code sau. */
    fun startAuthUrl(context: Context): String {
        val cfg = YouTubePublishStore.getConfig(context)
        require(cfg.hasCredentials) { "Chua nhap Client ID/Secret cua YouTube OAuth." }
        val pkce = client.generatePkce()
        YouTubePublishStore.savePkceVerifier(context, pkce.verifier)
        return client.buildAuthUrl(cfg.clientId, YouTubePublishStore.REDIRECT_URI, pkce.challenge)
    }

    /** Doi authorization code (lay tu redirect) -> luu refresh_token + access_token. */
    fun completeAuth(context: Context, code: String) {
        val cfg = YouTubePublishStore.getConfig(context)
        require(cfg.hasCredentials) { "Chua nhap Client ID/Secret." }
        val verifier = YouTubePublishStore.getPkceVerifier(context)
            ?: throw IllegalStateException("Thieu PKCE verifier - hay bam Ket noi lai.")
        val tokens = client.exchangeCode(
            clientId = cfg.clientId,
            clientSecret = cfg.clientSecret,
            code = code,
            codeVerifier = verifier,
            redirectUri = YouTubePublishStore.REDIRECT_URI
        )
        if (tokens.refreshToken.isNullOrBlank()) {
            throw IllegalStateException("Google khong tra ve refresh_token. Hay vao tai khoan Google go quyen app roi Ket noi lai (can prompt=consent).")
        }
        val expiry = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
        YouTubePublishStore.saveTokens(context, tokens.refreshToken, tokens.accessToken, expiry)
    }

    private fun ensureAccessToken(context: Context): String {
        val cfg = YouTubePublishStore.getConfig(context)
        if (!cfg.isConnected) throw NotConnectedException()
        val now = System.currentTimeMillis()
        if (!cfg.accessToken.isNullOrBlank() && cfg.accessExpiryEpochMs > now + 60_000L) {
            return cfg.accessToken
        }
        val refreshed = client.refreshAccessToken(cfg.clientId, cfg.clientSecret, cfg.refreshToken!!)
        val expiry = now + refreshed.expiresInSeconds * 1000L
        YouTubePublishStore.saveAccessToken(context, refreshed.accessToken, expiry)
        return refreshed.accessToken
    }

    /** Upload 1 video (app-private) + metadata len YouTube. Tra ve videoId. */
    fun upload(
        context: Context,
        videoFilePath: String,
        title: String,
        description: String,
        tags: List<String>
    ): String {
        val cfg = YouTubePublishStore.getConfig(context)
        if (!cfg.isConnected) throw NotConnectedException()
        val token = ensureAccessToken(context)
        return client.uploadVideo(
            accessToken = token,
            videoFile = File(videoFilePath),
            title = title,
            description = description,
            tags = tags,
            privacyStatus = cfg.privacyStatus
        )
    }
}
