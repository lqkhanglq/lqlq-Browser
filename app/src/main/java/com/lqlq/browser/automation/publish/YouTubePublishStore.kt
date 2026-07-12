package com.lqlq.browser.automation.publish

import android.content.Context

/**
 * Luu cau hinh + token YouTube auto-upload. Client ID/Secret la cua OAuth Client
 * nguoi dung tu tao tren Google Cloud (1 lan); refresh_token co duoc sau khi cap
 * quyen. access_token duoc cache kem han su dung, tu lam moi khi het han.
 *
 * Luu bang SharedPreferences (khong phai credential nhay cam kieu mat khau nguoi
 * khac - la token cua chinh tai khoan nguoi dung tren may cua ho).
 */
object YouTubePublishStore {
    private const val PREFS_NAME = "automation_youtube_publish"
    private const val KEY_CLIENT_ID = "clientId"
    private const val KEY_CLIENT_SECRET = "clientSecret"
    private const val KEY_REFRESH_TOKEN = "refreshToken"
    private const val KEY_ACCESS_TOKEN = "accessToken"
    private const val KEY_ACCESS_EXPIRY = "accessTokenExpiryEpochMs"
    private const val KEY_PKCE_VERIFIER = "pkceVerifier"
    private const val KEY_PRIVACY = "privacyStatus"

    // Redirect URI CO DINH - nguoi dung phai them CHINH XAC gia tri nay vao "Authorized
    // redirect URIs" cua OAuth Client (Web application) tren Google Cloud. App chan
    // dieu huong toi URL nay trong WebView de lay ?code=.
    const val REDIRECT_URI = "http://localhost/lqlq-youtube-oauth"

    data class Config(
        val clientId: String,
        val clientSecret: String,
        val refreshToken: String?,
        val accessToken: String?,
        val accessExpiryEpochMs: Long,
        val privacyStatus: String
    ) {
        val hasCredentials: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
        val isConnected: Boolean get() = hasCredentials && !refreshToken.isNullOrBlank()
    }

    fun getConfig(context: Context): Config {
        val p = prefs(context)
        return Config(
            clientId = p.getString(KEY_CLIENT_ID, "") ?: "",
            clientSecret = p.getString(KEY_CLIENT_SECRET, "") ?: "",
            refreshToken = p.getString(KEY_REFRESH_TOKEN, null),
            accessToken = p.getString(KEY_ACCESS_TOKEN, null),
            accessExpiryEpochMs = p.getLong(KEY_ACCESS_EXPIRY, 0L),
            privacyStatus = p.getString(KEY_PRIVACY, "private") ?: "private"
        )
    }

    fun saveCredentials(context: Context, clientId: String, clientSecret: String) {
        prefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun savePrivacy(context: Context, privacyStatus: String) {
        prefs(context).edit().putString(KEY_PRIVACY, privacyStatus.trim().lowercase()).apply()
    }

    fun savePkceVerifier(context: Context, verifier: String) {
        prefs(context).edit().putString(KEY_PKCE_VERIFIER, verifier).apply()
    }

    fun getPkceVerifier(context: Context): String? = prefs(context).getString(KEY_PKCE_VERIFIER, null)

    fun saveTokens(context: Context, refreshToken: String?, accessToken: String, expiryEpochMs: Long) {
        val editor = prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ACCESS_EXPIRY, expiryEpochMs)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
    }

    fun saveAccessToken(context: Context, accessToken: String, expiryEpochMs: Long) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ACCESS_EXPIRY, expiryEpochMs)
            .apply()
    }

    fun disconnect(context: Context) {
        prefs(context).edit()
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_ACCESS_EXPIRY)
            .remove(KEY_PKCE_VERIFIER)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
