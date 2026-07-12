package com.lqlq.browser.automation.credential

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreAutomationCredentialStore(
    context: Context
) : AutomationCredentialStore {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveGeminiConfiguration(apiKey: String, model: String) {
        val normalizedApiKey = apiKey.trim()
        val normalizedModel = model.trim()
        require(normalizedApiKey.isNotEmpty()) { "Gemini API key is required." }
        require(normalizedModel.isNotEmpty()) { "Gemini model is required." }

        val payload = JSONObject()
            .put("apiKey", normalizedApiKey)
            .put("model", normalizedModel)
            .toString()

        val encryptedPayload = encrypt(payload, GEMINI_KEY_ALIAS)
        preferences.edit()
            .putString(geminiKey("ciphertext"), encryptedPayload.ciphertextBase64)
            .putString(geminiKey("iv"), encryptedPayload.ivBase64)
            .putString(geminiKey("model"), normalizedModel)
            .putString(geminiKey("status"), AutomationCredentialStore.STATE_CONNECTED)
            .remove(geminiKey("status_message"))
            .apply()
    }

    override fun getGeminiConfiguration(): GeminiCredentialConfiguration? {
        val ciphertext = preferences.getString(geminiKey("ciphertext"), null)
        val iv = preferences.getString(geminiKey("iv"), null)
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) {
            return null
        }

        return try {
            val payload = JSONObject(decrypt(ciphertext, iv, GEMINI_KEY_ALIAS))
            val apiKey = payload.optString("apiKey").trim()
            val model = payload.optString("model").trim()
            if (apiKey.isEmpty() || model.isEmpty()) {
                markGeminiInvalid("Gemini credential payload is incomplete.")
                null
            } else {
                GeminiCredentialConfiguration(apiKey = apiKey, model = model)
            }
        } catch (_: Throwable) {
            markGeminiInvalid("Stored Gemini credential could not be decrypted.")
            null
        }
    }

    override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
        if (!hasStoredGemini()) {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED
            )
        }

        return AutomationCredentialStatusSnapshot(
            state = preferences.getString(geminiKey("status"), AutomationCredentialStore.STATE_CONNECTED)
                ?: AutomationCredentialStore.STATE_CONNECTED,
            providerId = AutomationCredentialStore.GEMINI_PROVIDER_ID,
            model = preferences.getString(geminiKey("model"), null),
            message = preferences.getString(geminiKey("status_message"), null)
        )
    }

    override fun markGeminiInvalid(message: String?) {
        if (!hasStoredGemini()) {
            return
        }

        preferences.edit()
            .putString(geminiKey("status"), AutomationCredentialStore.STATE_INVALID)
            .putString(geminiKey("status_message"), message?.trim().orEmpty())
            .apply()
    }

    override fun clearGeminiConfiguration() {
        preferences.edit()
            .remove(geminiKey("ciphertext"))
            .remove(geminiKey("iv"))
            .remove(geminiKey("model"))
            .remove(geminiKey("status"))
            .remove(geminiKey("status_message"))
            .apply()
    }

    override fun saveImageProviderConfiguration(
        providerId: String,
        apiKey: String,
        model: String,
        accountId: String?
    ) {
        val normalizedProviderId = normalizeProviderId(providerId)
        val normalizedApiKey = apiKey.trim()
        val normalizedModel = model.trim()
        val normalizedAccountId = accountId?.trim().orEmpty().ifBlank { null }

        // API key co the rong voi provider khong can credential (vd Openverse/
        // Wikimedia/Auto-nhieu-nguon) - viec bat buoc phai co key da duoc kiem tra
        // rieng cho tung loai authType o tang facade (AutomationFacade), store nay
        // chi can dam bao co model.
        require(normalizedModel.isNotEmpty()) { "Image provider model is required." }

        val payload = JSONObject()
            .put("providerId", normalizedProviderId)
            .put("apiKey", normalizedApiKey)
            .put("model", normalizedModel)
            .put("accountId", normalizedAccountId)
            .toString()

        val encryptedPayload = encrypt(payload, providerKeyAlias(normalizedProviderId))
        preferences.edit()
            .putString(providerPref(normalizedProviderId, "ciphertext"), encryptedPayload.ciphertextBase64)
            .putString(providerPref(normalizedProviderId, "iv"), encryptedPayload.ivBase64)
            .putString(providerPref(normalizedProviderId, "model"), normalizedModel)
            .putString(providerPref(normalizedProviderId, "account_id"), normalizedAccountId)
            .putString(providerPref(normalizedProviderId, "status"), AutomationCredentialStore.IMAGE_STATE_CONFIG_SAVED)
            .remove(providerPref(normalizedProviderId, "status_message"))
            .putString(SELECTED_IMAGE_PROVIDER_KEY, normalizedProviderId)
            .apply()
    }

    override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? {
        val normalizedProviderId = normalizeProviderId(providerId)
        val ciphertext = preferences.getString(providerPref(normalizedProviderId, "ciphertext"), null)
        val iv = preferences.getString(providerPref(normalizedProviderId, "iv"), null)
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) {
            return null
        }

        return try {
            val payload = JSONObject(decrypt(ciphertext, iv, providerKeyAlias(normalizedProviderId)))
            val apiKey = payload.optString("apiKey").trim()
            val model = payload.optString("model").trim()
            val accountId = payload.optString("accountId").trim().ifBlank { null }
            // apiKey rong la HOP LE cho provider khong can credential (Openverse/
            // Wikimedia/Auto-nhieu-nguon...) - viec bat buoc co key da chuyen ve
            // tang facade theo authType, o day chi con model la bat buoc.
            if (model.isEmpty()) {
                markImageProviderState(
                    normalizedProviderId,
                    AutomationCredentialStore.STATE_INVALID,
                    "Image provider credential payload is incomplete."
                )
                null
            } else {
                ImageProviderCredentialConfiguration(
                    providerId = normalizedProviderId,
                    apiKey = apiKey,
                    model = model,
                    accountId = accountId
                )
            }
        } catch (_: Throwable) {
            markImageProviderState(
                normalizedProviderId,
                AutomationCredentialStore.STATE_INVALID,
                "Stored image provider credential could not be decrypted."
            )
            null
        }
    }

    override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
        val normalizedProviderId = normalizeProviderId(providerId)
        if (!hasStoredImageProvider(normalizedProviderId)) {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = normalizedProviderId
            )
        }

        return AutomationCredentialStatusSnapshot(
            state = preferences.getString(
                providerPref(normalizedProviderId, "status"),
                AutomationCredentialStore.IMAGE_STATE_CONFIG_SAVED
            ) ?: AutomationCredentialStore.IMAGE_STATE_CONFIG_SAVED,
            providerId = normalizedProviderId,
            model = preferences.getString(providerPref(normalizedProviderId, "model"), null),
            message = preferences.getString(providerPref(normalizedProviderId, "status_message"), null),
            accountId = preferences.getString(providerPref(normalizedProviderId, "account_id"), null)
        )
    }

    override fun markImageProviderState(providerId: String, state: String, message: String?) {
        val normalizedProviderId = normalizeProviderId(providerId)
        if (!hasStoredImageProvider(normalizedProviderId)) {
            return
        }

        preferences.edit()
            .putString(providerPref(normalizedProviderId, "status"), state)
            .putString(providerPref(normalizedProviderId, "status_message"), message?.trim().orEmpty())
            .apply()
    }

    override fun clearImageProviderConfiguration(providerId: String) {
        val normalizedProviderId = normalizeProviderId(providerId)
        preferences.edit()
            .remove(providerPref(normalizedProviderId, "ciphertext"))
            .remove(providerPref(normalizedProviderId, "iv"))
            .remove(providerPref(normalizedProviderId, "model"))
            .remove(providerPref(normalizedProviderId, "account_id"))
            .remove(providerPref(normalizedProviderId, "status"))
            .remove(providerPref(normalizedProviderId, "status_message"))
            .apply()
    }

    override fun setSelectedImageProviderId(providerId: String) {
        preferences.edit()
            .putString(SELECTED_IMAGE_PROVIDER_KEY, normalizeProviderId(providerId))
            .apply()
    }

    override fun getSelectedImageProviderId(): String? {
        return preferences.getString(SELECTED_IMAGE_PROVIDER_KEY, null)?.trim()?.ifBlank { null }
    }

    override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
        val providerId = normalizeProviderId(configuration.providerId)
        val payload = JSONObject()
            .put("providerId", providerId)
            .put("locale", configuration.locale.trim())
            .put("voiceId", configuration.voiceId?.trim()?.ifBlank { null })
            .put("model", configuration.model?.trim()?.ifBlank { null })
            .put("speechRate", configuration.speechRate.toDouble())
            .put("pitch", configuration.pitch.toDouble())
            .put("outputFormat", configuration.outputFormat.trim())
            .put("engineName", configuration.engineName?.trim()?.ifBlank { null })
            .put("apiKey", configuration.apiKey?.trim()?.ifBlank { null })
            .put("region", configuration.region?.trim()?.ifBlank { null })
            .put("credentialJson", configuration.credentialJson?.trim()?.ifBlank { null })
            .toString()

        val encryptedPayload = encrypt(payload, voiceProviderKeyAlias(providerId))
        preferences.edit()
            .putString(voiceProviderPref(providerId, "ciphertext"), encryptedPayload.ciphertextBase64)
            .putString(voiceProviderPref(providerId, "iv"), encryptedPayload.ivBase64)
            .putString(voiceProviderPref(providerId, "locale"), configuration.locale.trim())
            .putString(voiceProviderPref(providerId, "voice_id"), configuration.voiceId?.trim()?.ifBlank { null })
            .putString(voiceProviderPref(providerId, "model"), configuration.model?.trim()?.ifBlank { null })
            .putString(voiceProviderPref(providerId, "engine_name"), configuration.engineName?.trim()?.ifBlank { null })
            .putString(voiceProviderPref(providerId, "status"), AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED)
            .remove(voiceProviderPref(providerId, "status_message"))
            .putString(SELECTED_VOICE_PROVIDER_KEY, providerId)
            .apply()
    }

    override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? {
        val normalizedProviderId = normalizeProviderId(providerId)
        val ciphertext = preferences.getString(voiceProviderPref(normalizedProviderId, "ciphertext"), null)
        val iv = preferences.getString(voiceProviderPref(normalizedProviderId, "iv"), null)
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) {
            return null
        }

        return try {
            val payload = JSONObject(decrypt(ciphertext, iv, voiceProviderKeyAlias(normalizedProviderId)))
            val locale = payload.optString("locale").trim()
            if (locale.isEmpty()) {
                markVoiceProviderState(
                    normalizedProviderId,
                    AutomationCredentialStore.STATE_INVALID,
                    "Voice provider configuration is incomplete."
                )
                null
            } else {
                VoiceProviderCredentialConfiguration(
                    providerId = normalizedProviderId,
                    locale = locale,
                    voiceId = payload.optString("voiceId").trim().ifBlank { null },
                    model = payload.optString("model").trim().ifBlank { null },
                    speechRate = payload.optDouble("speechRate", 1.0).toFloat(),
                    pitch = payload.optDouble("pitch", 1.0).toFloat(),
                    outputFormat = payload.optString("outputFormat").trim().ifBlank { "wav" },
                    engineName = payload.optString("engineName").trim().ifBlank { null },
                    apiKey = payload.optString("apiKey").trim().ifBlank { null },
                    region = payload.optString("region").trim().ifBlank { null },
                    credentialJson = payload.optString("credentialJson").trim().ifBlank { null }
                )
            }
        } catch (_: Throwable) {
            markVoiceProviderState(
                normalizedProviderId,
                AutomationCredentialStore.STATE_INVALID,
                "Stored voice provider configuration could not be decrypted."
            )
            null
        }
    }

    override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
        val normalizedProviderId = normalizeProviderId(providerId)
        if (!hasStoredVoiceProvider(normalizedProviderId)) {
            return AutomationCredentialStatusSnapshot(
                state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
                providerId = normalizedProviderId
            )
        }

        return AutomationCredentialStatusSnapshot(
            state = preferences.getString(
                voiceProviderPref(normalizedProviderId, "status"),
                AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED
            ) ?: AutomationCredentialStore.VOICE_STATE_CONFIG_SAVED,
            providerId = normalizedProviderId,
            model = preferences.getString(voiceProviderPref(normalizedProviderId, "model"), null),
            message = preferences.getString(voiceProviderPref(normalizedProviderId, "status_message"), null),
            voiceId = preferences.getString(voiceProviderPref(normalizedProviderId, "voice_id"), null),
            locale = preferences.getString(voiceProviderPref(normalizedProviderId, "locale"), null),
            engineName = preferences.getString(voiceProviderPref(normalizedProviderId, "engine_name"), null)
        )
    }

    override fun markVoiceProviderState(providerId: String, state: String, message: String?) {
        val normalizedProviderId = normalizeProviderId(providerId)
        if (!hasStoredVoiceProvider(normalizedProviderId)) {
            return
        }

        preferences.edit()
            .putString(voiceProviderPref(normalizedProviderId, "status"), state)
            .putString(voiceProviderPref(normalizedProviderId, "status_message"), message?.trim().orEmpty())
            .apply()
    }

    override fun clearVoiceProviderConfiguration(providerId: String) {
        val normalizedProviderId = normalizeProviderId(providerId)
        preferences.edit()
            .remove(voiceProviderPref(normalizedProviderId, "ciphertext"))
            .remove(voiceProviderPref(normalizedProviderId, "iv"))
            .remove(voiceProviderPref(normalizedProviderId, "locale"))
            .remove(voiceProviderPref(normalizedProviderId, "voice_id"))
            .remove(voiceProviderPref(normalizedProviderId, "model"))
            .remove(voiceProviderPref(normalizedProviderId, "engine_name"))
            .remove(voiceProviderPref(normalizedProviderId, "status"))
            .remove(voiceProviderPref(normalizedProviderId, "status_message"))
            .apply()
    }

    override fun setSelectedVoiceProviderId(providerId: String) {
        preferences.edit()
            .putString(SELECTED_VOICE_PROVIDER_KEY, normalizeProviderId(providerId))
            .apply()
    }

    override fun getSelectedVoiceProviderId(): String? {
        return preferences.getString(SELECTED_VOICE_PROVIDER_KEY, null)?.trim()?.ifBlank { null }
    }

    private fun hasStoredGemini(): Boolean {
        return preferences.contains(geminiKey("ciphertext")) && preferences.contains(geminiKey("iv"))
    }

    private fun hasStoredImageProvider(providerId: String): Boolean {
        return preferences.contains(providerPref(providerId, "ciphertext")) &&
            preferences.contains(providerPref(providerId, "iv"))
    }

    private fun hasStoredVoiceProvider(providerId: String): Boolean {
        return preferences.contains(voiceProviderPref(providerId, "ciphertext")) &&
            preferences.contains(voiceProviderPref(providerId, "iv"))
    }

    private fun geminiKey(suffix: String): String = "gemini_$suffix"

    private fun providerPref(providerId: String, suffix: String): String {
        return "image_${normalizeProviderId(providerId)}_$suffix"
    }

    private fun providerKeyAlias(providerId: String): String {
        return "lqlq_automation_image_${normalizeProviderId(providerId)}_key"
    }

    private fun voiceProviderPref(providerId: String, suffix: String): String {
        return "voice_${normalizeProviderId(providerId)}_$suffix"
    }

    private fun voiceProviderKeyAlias(providerId: String): String {
        return "lqlq_automation_voice_${normalizeProviderId(providerId)}_key"
    }

    private fun normalizeProviderId(providerId: String): String {
        return providerId.trim().lowercase()
    }

    private fun encrypt(plainText: String, keyAlias: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(keyAlias))
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    private fun decrypt(ciphertextBase64: String, ivBase64: String, keyAlias: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivBase64, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(keyAlias), spec)
        val decoded = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        val plainBytes = cipher.doFinal(decoded)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            "Android Keystore AES support requires API 23+."
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private data class EncryptedPayload(
        val ciphertextBase64: String,
        val ivBase64: String
    )

    companion object {
        private const val PREFS_NAME = "automation_credentials"
        private const val GEMINI_KEY_ALIAS = "lqlq_automation_gemini_key"
        private const val SELECTED_IMAGE_PROVIDER_KEY = "selected_image_provider"
        private const val SELECTED_VOICE_PROVIDER_KEY = "selected_voice_provider"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
