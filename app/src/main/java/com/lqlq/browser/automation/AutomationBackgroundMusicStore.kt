package com.lqlq.browser.automation

import android.content.Context
import java.io.File

/**
 * Nhac nen la 1 CAI DAT TOAN CUC (khong gan voi tung job) - nguoi dung chon 1 file
 * tu may trong Cai dat tu dong, ap dung cho MOI video render/render lai sau do cho
 * toi khi doi/xoa. Luu bytes truc tiep trong app-private storage (khong dung
 * AutomationArtifactStore vi day khong phai artifact gan voi 1 job/step cu the).
 */
object AutomationBackgroundMusicStore {
    private const val PREFS_NAME = "automation_background_music"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_MIME_TYPE = "mimeType"
    private const val KEY_LOOP = "loop"
    private const val KEY_VOLUME = "volume"
    private const val FILE_NAME = "background_music_track"
    const val MAX_BACKGROUND_MUSIC_BYTES: Int = 20 * 1024 * 1024
    const val DEFAULT_VOLUME: Float = 0.35f

    data class BackgroundMusicSettings(
        val hasMusic: Boolean,
        val displayName: String,
        val mimeType: String,
        val loop: Boolean,
        val volume: Float
    )

    fun save(context: Context, bytes: ByteArray, mimeType: String, displayName: String): BackgroundMusicSettings {
        require(bytes.isNotEmpty()) { "File nhac nen rong." }
        require(bytes.size <= MAX_BACKGROUND_MUSIC_BYTES) { "File nhac nen qua lon (toi da 20MB)." }
        val appContext = context.applicationContext
        musicFile(appContext).writeBytes(bytes)
        prefs(appContext).edit()
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_MIME_TYPE, mimeType)
            .apply()
        return getSettings(appContext)
    }

    fun clear(context: Context): BackgroundMusicSettings {
        val appContext = context.applicationContext
        musicFile(appContext).delete()
        prefs(appContext).edit()
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_MIME_TYPE)
            .apply()
        return getSettings(appContext)
    }

    fun setOptions(context: Context, loop: Boolean, volume: Float): BackgroundMusicSettings {
        val appContext = context.applicationContext
        prefs(appContext).edit()
            .putBoolean(KEY_LOOP, loop)
            .putFloat(KEY_VOLUME, volume.coerceIn(0f, 2f))
            .apply()
        return getSettings(appContext)
    }

    fun getSettings(context: Context): BackgroundMusicSettings {
        val appContext = context.applicationContext
        val file = musicFile(appContext)
        val stored = prefs(appContext)
        return BackgroundMusicSettings(
            hasMusic = file.exists() && file.length() > 0L,
            displayName = stored.getString(KEY_DISPLAY_NAME, "") ?: "",
            mimeType = stored.getString(KEY_MIME_TYPE, "") ?: "",
            loop = stored.getBoolean(KEY_LOOP, true),
            volume = stored.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        )
    }

    /** Duong dan file that neu da co nhac nen hop le, dung de truyen vao VideoRenderRequest. */
    fun getFilePathIfPresent(context: Context): String? {
        val file = musicFile(context.applicationContext)
        return if (file.exists() && file.length() > 0L) file.absolutePath else null
    }

    private fun musicFile(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
