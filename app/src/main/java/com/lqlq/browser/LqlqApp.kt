package com.lqlq.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.lqlq.browser.automation.AutomationFacade
import com.lqlq.browser.automation.AutomationJobNotificationPublisher
import com.lqlq.browser.automation.artifact.AppPrivateAutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.connector.content.GeminiContentConnector
import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.connector.image.CloudflareWorkersAiImageConnector
import com.lqlq.browser.automation.connector.image.DefaultImageProviderRegistry
import com.lqlq.browser.automation.connector.image.OpenAiImageConnector
import com.lqlq.browser.automation.connector.image.OpenverseImageConnector
import com.lqlq.browser.automation.connector.image.PexelsStockImageConnector
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsConnector
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.AzureSpeechConnector
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import com.lqlq.browser.automation.connector.voice.FptAiVoiceConnector
import com.lqlq.browser.automation.connector.voice.VbeeVoiceConnector
import com.lqlq.browser.automation.connector.voice.VietelAiVoiceConnector
import com.lqlq.browser.automation.credential.AndroidKeystoreAutomationCredentialStore
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.image.ScriptScenePromptGenerator
import com.lqlq.browser.automation.repository.RoomAutomationRepository

class LqlqApp : Application() {
    lateinit var automationFacade: AutomationFacade
        private set
    private lateinit var automationJobNotificationPublisher: AutomationJobNotificationPublisher
    val automationDatabase: AutomationDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AutomationDatabase.create(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        automationJobNotificationPublisher = AutomationJobNotificationPublisher(applicationContext)
        automationFacade = AutomationFacade.create(
            repository = RoomAutomationRepository(automationDatabase),
            artifactStore = AppPrivateAutomationArtifactStore(applicationContext),
            connectorRegistry = AutomationConnectorRegistry.of(
                GeminiContentConnector.GEMINI_PROVIDER_ID,
                OpenAiImageConnector.PROVIDER_ID,
                CloudflareWorkersAiImageConnector.PROVIDER_ID,
                PexelsStockImageConnector.PROVIDER_ID,
                OpenverseImageConnector.PROVIDER_ID,
                AutomationVoiceProviders.VBEE_TTS,
                AutomationVoiceProviders.FPT_AI_TTS,
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS
            ),
            credentialStore = AndroidKeystoreAutomationCredentialStore(applicationContext),
            contentConnector = GeminiContentConnector(),
            scenePromptGenerator = ScriptScenePromptGenerator(),
            imageProviderRegistry = DefaultImageProviderRegistry(
                implementedProviderIds = setOf(
                    AutomationImageProviders.OPENAI_IMAGES,
                    AutomationImageProviders.CLOUDFLARE_WORKERS_AI,
                    AutomationImageProviders.PEXELS,
                    AutomationImageProviders.OPENVERSE,
                    // "Tim anh qua ChatGPT" chua co ImageGenerationConnector thong
                    // thuong (dung ChatGptWebPocController + WebView foreground de lay
                    // anh that tu khung tra loi, xem AutomationBridge.scrapeWebImages) -
                    // danh dau AVAILABLE de chon duoc trong dropdown, nhung KHONG dang ky
                    // trong imageConnectors ben duoi. executeImageStage se tu dung o
                    // WAITING_USER cho toi khi JS goi scrapeWebImages.
                    AutomationImageProviders.CHATGPT_IMAGE_SEARCH_WEB,
                    // Pinterest: cung co che WebView-scrape nhu ChatGPT nhung tim-san
                    // (nhanh hon) + gom canh cung nhan vat de do so lan tim.
                    AutomationImageProviders.PINTEREST_IMAGE_SEARCH_WEB
                )
            ),
            imageConnectors = mapOf(
                AutomationImageProviders.OPENAI_IMAGES to OpenAiImageConnector(),
                AutomationImageProviders.CLOUDFLARE_WORKERS_AI to CloudflareWorkersAiImageConnector(),
                AutomationImageProviders.PEXELS to PexelsStockImageConnector(),
                AutomationImageProviders.OPENVERSE to OpenverseImageConnector()
            ),
            // Edge Neural da go (endpoint Microsoft chan client khong phai trinh
            // duyet - HTTP 403). Muon bat lai: them EDGE_NEURAL_TTS vao 2 map duoi
            // + definition trong DefaultVoiceProviderRegistry.
            voiceProviderRegistry = DefaultVoiceProviderRegistry(
                implementedProviderIds = setOf(
                    AutomationVoiceProviders.AZURE_SPEECH,
                    AutomationVoiceProviders.VIETTEL_AI_TTS,
                    AutomationVoiceProviders.VBEE_TTS,
                    AutomationVoiceProviders.FPT_AI_TTS,
                    AutomationVoiceProviders.ANDROID_SYSTEM_TTS
                )
            ),
            voiceConnectors = mapOf(
                AutomationVoiceProviders.AZURE_SPEECH to AzureSpeechConnector(),
                AutomationVoiceProviders.VIETTEL_AI_TTS to VietelAiVoiceConnector(),
                AutomationVoiceProviders.VBEE_TTS to VbeeVoiceConnector(),
                AutomationVoiceProviders.FPT_AI_TTS to FptAiVoiceConnector(),
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS to AndroidSystemTtsConnector(applicationContext)
            ),
            progressListener = automationJobNotificationPublisher,
            runtimeJobStore = com.lqlq.browser.automation.RuntimeJobStore(applicationContext)
        )
        // Khoi phuc trang thai pipeline da luu (scenePrompts/anh/chinh sua) tu dia -
        // app bi kill/thoat dot ngot roi mo lai van chay tiep duoc, khong lam lai tu dau.
        automationFacade.restorePersistedRuntimeJobs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                PlaybackService.CHANNEL_ID,
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_playback_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)

            val automationChannel = NotificationChannel(
                com.lqlq.browser.automation.worker.AutomationNotifications.CHANNEL_ID,
                "Automation tự động hoá",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tiến độ tạo nội dung/ảnh/giọng đọc/video chạy nền."
            }
            manager.createNotificationChannel(automationChannel)
        }
        automationJobNotificationPublisher.ensureChannel()
    }
}
