package com.lqlq.browser

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Rational
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JsResult
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHELL_HOST = "appassets.androidapp.com"
        private const val MAX_SHARED_IMAGE_BYTES = 25L * 1024L * 1024L
        private const val MAX_SHARED_IMAGE_CACHE_BYTES = 100L * 1024L * 1024L
        private const val MAX_SHARED_IMAGE_CACHE_FILES = 24

        /** Cá»­a sá»• tin cáº­y sau 1 cÃº cháº¡m tháº­t Ä‘á»ƒ cho qua cÃ¡c bÆ°á»›c redirect khÃ´ng mang gesture (OAuth, link rÃºt gá»n...). */
        private const val TRUSTED_REDIRECT_WINDOW_MS = 20_000L

        // Háº­u tá»‘ nhiá»u nhÃ£n phá»• biáº¿n â€” KHÃ”NG pháº£i Public Suffix List Ä‘áº§y Ä‘á»§,
        // nhÆ°ng Ä‘á»§ Ä‘á»ƒ domain guard khÃ´ng gá»™p nháº§m 2 site khÃ¡c nhau vÃ o 1
        // "domain gá»‘c" (vd trÆ°á»›c Ä‘Ã¢y a.co.uk vÃ  b.co.uk bá»‹ coi lÃ  cÃ¹ng site
        // vÃ¬ chá»‰ cáº¯t 2 nhÃ£n cuá»‘i "co.uk").
        private val MULTI_LABEL_SUFFIXES = setOf(
            "co.uk", "org.uk", "gov.uk", "ac.uk", "me.uk", "net.uk",
            "com.vn", "net.vn", "org.vn", "edu.vn", "gov.vn",
            "com.br", "com.au", "net.au", "org.au", "gov.au",
            "co.jp", "co.kr", "co.in", "co.nz", "co.za", "co.id", "co.th",
            "com.cn", "com.hk", "com.tw", "com.sg", "com.my",
            "github.io", "gitlab.io", "blogspot.com", "herokuapp.com",
            "pages.dev", "netlify.app", "vercel.app", "web.app", "firebaseapp.com"
        )
        private const val SHELL_URL = "https://$SHELL_HOST/assets/www/index.html"

        // CÃ¡c trang cÃ´ng cá»¥ tÃ¬m kiáº¿m/chatbot AI mÃ  ngÆ°á»i dÃ¹ng chá»§ yáº¿u vÃ o Ä‘á»ƒ
        // TRA Cá»¨U â€” khÃ´ng phÃ¹ há»£p Ä‘á»ƒ Linh Tháº¡ch/Linh ThÃº/Tháº» Ká»³ Váº­t xuáº¥t hiá»‡n
        // (khÃ¡c vá»›i cÃ¡c trang Ä‘á»c truyá»‡n/tin tá»©c thÃ´ng thÆ°á»ng).
        private val ADVENTURE_LOOT_EXCLUDED_DOMAINS = setOf(
            "google.com", "google.com.vn", "bing.com", "duckduckgo.com",
            "search.yahoo.com", "yandex.com", "baidu.com", "you.com",
            "chatgpt.com", "chat.openai.com", "openai.com",
            "claude.ai", "anthropic.com",
            "gemini.google.com", "bard.google.com",
            "copilot.microsoft.com", "perplexity.ai", "poe.com"
        )

        private fun isAdventureLootExcludedUrl(url: String): Boolean {
            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return false
            return ADVENTURE_LOOT_EXCLUDED_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        }

        // Viá»‡c "cháº·n quáº£ng cÃ¡o/nháº£y trang giá»‘ng metruyenchu_clipper_android_project"
        // (v0.23.11): project tham kháº£o cháº·n TUYá»†T Äá»I theo danh sÃ¡ch domain quáº£ng
        // cÃ¡o/redirect Ä‘Ã£ biáº¿t (rules.json cá»§a Chapter Clipper gá»‘c), khÃ´ng phá»¥
        // thuá»™c gesture â€” vÃ¬ váº­y trang web khÃ´ng há» bá»‹ Ä‘á»•i/nháº£y dÃ¹ chá»‰ 1 khoáº£nh
        // kháº¯c. ÄÃ¢y lÃ  danh sÃ¡ch máº¡ng quáº£ng cÃ¡o/redirect phá»• biáº¿n (má»Ÿ rá»™ng tá»«
        // rules.json gá»‘c: doubleclick, googlesyndication, googleadservices,
        // popads, popcash + vÃ i máº¡ng phá»• biáº¿n khÃ¡c hay gáº·p trÃªn site Ä‘á»c truyá»‡n/
        // xem phim láº­u). Khá»›p theo domain gá»‘c (endsWith) nÃªn bao gá»“m má»i subdomain.
        private val AD_HOST_BLOCKLIST = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adnxs.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "adsterra.com",
            "exoclick.com",
            "exosrv.com",
            "mgid.com",
            "taboola.com",
            "outbrain.com",
            "revcontent.com",
            "adservice.google.com",
            "clickadu.com",
            "hilltopads.net",
            "adcash.com",
            "juicyads.com"
        )

        private fun isBlockedAdHost(host: String?): Boolean {
            if (host.isNullOrEmpty()) return false
            val h = host.lowercase()
            return AD_HOST_BLOCKLIST.any { h == it || h.endsWith(".$it") }
        }

        // Viá»‡c (v0.23.14): cÃ´ng cá»¥ tÃ¬m kiáº¿m/trá»£ lÃ½ AI â€” ngÆ°á»i dÃ¹ng báº¥m vÃ o
        // káº¿t quáº£ tÃ¬m kiáº¿m/liÃªn káº¿t do chÃ­nh cÃ¡c trang nÃ y táº¡o ra LÃ€ má»¥c
        // Ä‘Ã­ch sá»­ dá»¥ng bÃ¬nh thÆ°á»ng, khÃ´ng pháº£i bá»‹ quáº£ng cÃ¡o lá»«a nháº£y trang.
        // Khi tab Ä‘ang á»Ÿ 1 trong cÃ¡c domain nÃ y, bá» qua lá»›p khoÃ¡ domain
        // (Viá»‡c v0.23.13) â€” váº«n giá»¯ nguyÃªn cháº·n danh sÃ¡ch quáº£ng cÃ¡o Ä‘Ã£ biáº¿t.
        private val SEARCH_AND_AI_TOOL_HOSTS = listOf(
            "google.com",
            "google.com.vn",
            "bing.com",
            "search.brave.com",
            "duckduckgo.com",
            "yahoo.com",
            "coccoc.com",
            "cnn.coccoc.com",
            "yandex.com",
            "baidu.com",
            "chatgpt.com",
            "chat.openai.com",
            "openai.com",
            "gemini.google.com",
            "bard.google.com",
            "claude.ai",
            "perplexity.ai",
            "you.com",
            "copilot.microsoft.com"
        )

        private fun isSearchOrAiToolHost(host: String?): Boolean {
            if (host.isNullOrEmpty()) return false
            val h = host.lowercase()
            return SEARCH_AND_AI_TOOL_HOSTS.any { h == it || h.endsWith(".$it") }
        }
    }

    private lateinit var root: FrameLayout
    private lateinit var shellWebView: WebView
    private lateinit var pageContainer: FrameLayout

    /**
     * Há»‡ thá»‘ng tháº» native: BrowserTabStore lÃ  nguá»“n dá»¯ liá»‡u duy nháº¥t.
     * pageWebViews chá»‰ lÃ  bá»™ nhá»› Ä‘á»‡m renderer cho má»™t sá»‘ tháº» gáº§n Ä‘Ã¢y.
     */
    private lateinit var tabStore: BrowserTabStore
    private lateinit var nativeTabSwitcher: NativeTabSwitcherView
    private val pageWebViews = LinkedHashMap<String, WebView>()
    private val pageLastUsedAt = mutableMapOf<String, Long>()
    private var activeTabId: String? = null
    @Volatile
    private var pageVisible = false
    private var shellReady = false

    private val maxCachedPageWebViews: Int by lazy {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.isLowRamDevice == true) 2 else 4
    }

    /**
     * CÃ¡c tab Ä‘Ã£ báº­t Chapter Clipper (viá»‡c 1, v0.23.5): má»™t khi ngÆ°á»i dÃ¹ng
     * báº­t cho tab nÃ y, script pháº£i tá»± tiÃªm láº¡i á»Ÿ Má»ŒI trang má»›i trong tab Ä‘Ã³
     * (ká»ƒ cáº£ sau khi next-chapter) cho Ä‘áº¿n khi ngÆ°á»i dÃ¹ng báº¥m táº¯t háº³n.
     */
    private val chapterClipperEnabledTabs = mutableSetOf<String>()

    /**
     * Domain gá»‘c "há»£p lá»‡" cá»§a tá»«ng tab (v0.23.12) â€” ghi láº¡i má»—i khi 1 trang
     * THáº¬T Sá»° táº£i xong (onPageFinished), khÃ´ng pháº£i Ä‘oÃ¡n tá»« view.url() giá»¯a
     * chá»«ng 1 redirect (khÃ´ng Ä‘Ã¡ng tin trong shouldInterceptRequest). ÄÃ¢y lÃ 
     * cÆ¡ sá»Ÿ Ä‘á»ƒ cháº·n TUYá»†T Äá»I má»i Ä‘iá»u hÆ°á»›ng khÃ´ng do ngÆ°á»i dÃ¹ng báº¥m tháº­t
     * sang domain khÃ¡c â€” giá»‘ng há»‡t cÃ¡ch metruyenchu_clipper_android_project
     * chá»‰ whitelist 1 domain vÃ  cháº·n má»i thá»© khÃ¡c, thay vÃ¬ Ä‘oÃ¡n theo danh
     * sÃ¡ch domain quáº£ng cÃ¡o Ä‘Ã£ biáº¿t (danh sÃ¡ch tÄ©nh khÃ´ng theo ká»‹p domain
     * rÃ¡c Ä‘á»•i liÃªn tá»¥c kiá»ƒu "scouplayen.qp").
     */
    private val tabRootDomain = ConcurrentHashMap<String, String>()

    /** Thá»i Ä‘iá»ƒm gáº§n nháº¥t má»—i tab cÃ³ 1 cÃº cháº¡m tháº­t (WebResourceRequest.hasGesture()), dÃ¹ng cho cá»­a sá»• redirect tin cáº­y cá»§a domain guard. */
    private val lastUserGestureNavAt = ConcurrentHashMap<String, Long>()
    private val lastCleartextWarningUrl = ConcurrentHashMap<String, String>()

    /**
     * Báº­t/táº¯t lá»›p cháº·n quáº£ng cÃ¡o domain Ä‘Ã£ biáº¿t + cháº·n nháº£y trang sang domain
     * láº¡ (root-domain guard). Máº·c Ä‘á»‹nh Báº¬T. KHÃ”NG xÃ©t gesture: má»™t cÃº cháº¡m
     * tháº­t cÃ³ thá»ƒ bá»‹ lá»›p phá»§ quáº£ng cÃ¡o vÃ´ hÃ¬nh "Ä‘Ã¡nh cáº¯p", nÃªn khÃ´ng dÃ¹ng lÃ m
     * cÄƒn cá»© cho phÃ©p nháº£y domain â€” xem ShellBridge.setDomainGuardEnabled().
     */
    @Volatile
    var domainGuardEnabled: Boolean = true

    /**
     * Tá»± báº¥m Back khi trang chÃ­nh táº£i xong nhÆ°ng tráº£ mÃ£ lá»—i HTTP â‰¥ 400 (xem
     * onReceivedHttpError bÃªn dÆ°á»›i). Máº·c Ä‘á»‹nh Táº®T: lá»—i 503/502 thÆ°á»ng chá»‰ lÃ 
     * server Ä‘Ã­ch Ä‘ang quÃ¡ táº£i/báº£o trÃ¬ táº¡m thá»i, khÃ´ng pháº£i quáº£ng cÃ¡o hay
     * nguy hiá»ƒm gÃ¬ cáº£ â€” xem ShellBridge.setBadLoadRecoveryEnabled().
     */
    @Volatile
    var badLoadRecoveryEnabled: Boolean = false

    /**
     * Hiá»‡n Toast má»—i khi cháº·n quáº£ng cÃ¡o/chuyá»ƒn hÆ°á»›ng láº¡ hoáº·c tá»± quay láº¡i do
     * trang lá»—i. Máº·c Ä‘á»‹nh Báº¬T. Táº¯t cá» nÃ y khÃ´ng táº¯t viá»‡c CHáº¶N â€” chá»‰ áº©n dÃ²ng
     * thÃ´ng bÃ¡o â€” xem ShellBridge.setBlockNoticeToastsEnabled().
     */
    @Volatile
    var blockNoticeToastsEnabled: Boolean = true

    private fun showBlockNoticeToast(message: String) {
        if (!blockNoticeToastsEnabled) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isCleartextHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true)

    private fun maybeWarnCleartextPage(tabId: String, url: String) {
        if (!isCleartextHttpUrl(url)) return
        if (lastCleartextWarningUrl[tabId] == url) return
        lastCleartextWarningUrl[tabId] = url
        Toast.makeText(
            this,
            "Cáº£nh bÃ¡o: trang nÃ y Ä‘ang dÃ¹ng HTTP khÃ´ng mÃ£ hÃ³a.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Ghi má»™t láº§n báº£o vá»‡ Ä‘á»§ Ä‘iá»u kiá»‡n cho Há»“ sÆ¡ PhiÃªu lÆ°u. Chá»‰ Ä‘iá»u hÆ°á»›ng
     * main-frame má»›i gá»i hÃ m nÃ y; request áº£nh/script/iframe khÃ´ng Ä‘Æ°á»£c thÆ°á»Ÿng.
     * Key tab + host vÃ  cá»­a sá»• 2,2 giÃ¢y loáº¡i bá» trÆ°á»ng há»£p cÃ¹ng redirect bá»‹
     * bÃ¡o á»Ÿ cáº£ shouldOverrideUrlLoading vÃ  shouldInterceptRequest.
     */
    private fun recordAdventureShieldProtection(
        tabId: String,
        targetHost: String?,
        reason: String
    ): AdventureProfileStore.RewardResult? {
        if (!::adventureProfileStore.isInitialized || !adventureProfileStore.hasProfile()) {
            return null
        }

        val now = System.currentTimeMillis()
        val host = targetHost.orEmpty().lowercase(Locale.ROOT).take(160)
        val eventKey = "$tabId|$host"
        val previous = recentAdventureShieldEvents.put(eventKey, now)
        if (previous != null && now - previous < 2_200L) return null

        if (recentAdventureShieldEvents.size > 128) {
            recentAdventureShieldEvents.entries.removeIf { now - it.value > 10_000L }
        }

        val result = adventureProfileStore.recordShieldProtection()
        if (result.profileCreated) {
            dispatchAdventureRewardResult(result, reason, host)
        }
        return result
    }

    private fun withAdventureReward(
        message: String,
        result: AdventureProfileStore.RewardResult?
    ): String = if (result?.rewarded == true) "$message Â· +1 Linh Tháº¡ch" else message

    internal fun dispatchAdventureProfileState(snapshot: AdventureProfileStore.Snapshot) {
        val payloadObject = snapshot.toJson()
        if (::dynamicLootStore.isInitialized) {
            dynamicLootStore.appendTo(payloadObject, snapshot.equippedCardIds.toSet())
        }
        val payload = payloadObject.toString()
        runOnUiThread {
            if (!snapshot.exists || !snapshot.lootEnabled) {
                hideAdventureLoot()
            }
            if (!snapshot.exists || !snapshot.spiritBeastsEnabled) {
                hideSpiritBeastEncounter()
            }
            if (!snapshot.exists || (::dynamicLootStore.isInitialized && !dynamicLootStore.isEnabled())) {
                hideDynamicLootEncounter()
            }
            if (shellReady && ::shellWebView.isInitialized && !isDestroyed) {
                shellWebView.evaluateJavascript(
                    "window.LqlqAdventureUI && LqlqAdventureUI.applyNativeState($payload);",
                    null
                )
            }
        }
    }

    private fun dispatchAdventureRewardResult(
        result: AdventureProfileStore.RewardResult,
        reason: String,
        host: String
    ) {
        if (!shellReady || !::shellWebView.isInitialized || isDestroyed) return
        val payload = result.toJson().apply {
            put("reason", reason)
            put("host", host)
        }.toString()
        runOnUiThread {
            if (!isDestroyed) {
                shellWebView.evaluateJavascript(
                    "window.LqlqAdventureUI && LqlqAdventureUI.onShieldProtection($payload);",
                    null
                )
            }
        }
    }

    /** Lá»›p phá»§ cá»§a giao diá»‡n (menu, panel...) Ä‘ang má»Ÿ â†’ pháº£i ná»•i trÃªn trang web. */
    @Volatile
    private var overlayOpen = false

    private var toolbarHeightPx = 0

    private lateinit var assetLoader: WebViewAssetLoader
    private var ttsBridge: TtsBridge? = null
    private lateinit var shellBridge: ShellBridge
    private lateinit var automationBridge: AutomationBridge
    private lateinit var adventureProfileStore: AdventureProfileStore
    private lateinit var adventureProfileBridge: AdventureProfileBridge
    private lateinit var dynamicLootStore: DynamicLootStore
    private lateinit var dynamicLootRepository: DynamicLootRepository
    private lateinit var dynamicLootImageCache: DynamicLootImageCache
    private lateinit var characterPortraitStore: CharacterPortraitStore
    private lateinit var pageBridge: PageBridge
    private lateinit var faviconStore: FaviconStore

    /** Chá»‘ng cá»™ng Linh Tháº¡ch hai láº§n khi cÃ¹ng redirect Ä‘i qua nhiá»u callback. */
    private val recentAdventureShieldEvents = ConcurrentHashMap<String, Long>()

    private lateinit var adventureLootLayer: FrameLayout
    private lateinit var adventureLootIcon: ImageView
    private var pendingAdventureLootUrl: String? = null
    private var pendingAdventureLootTabId: String? = null
    private val lastAdventureLootUrlByTab = ConcurrentHashMap<String, String>()
    private var adventureLootShowRunnable: Runnable? = null

    private lateinit var spiritBeastCard: LinearLayout
    private lateinit var spiritBeastIcon: TextView
    private lateinit var spiritBeastName: TextView
    private lateinit var spiritBeastMeta: TextView
    private var pendingSpiritBeast: SpiritBeastCatalog.Beast? = null
    private var pendingSpiritBeastDomain: String = ""
    private var pendingSpiritBeastUrl: String = ""
    private var pendingSpiritBeastTabId: String = ""
    private val lastSpiritBeastUrlByTab = ConcurrentHashMap<String, String>()
    private var spiritBeastShowRunnable: Runnable? = null
    private var spiritBeastHideRunnable: Runnable? = null

    private lateinit var dynamicLootCard: LinearLayout
    private lateinit var dynamicLootImage: ImageView
    private lateinit var dynamicLootName: TextView
    private lateinit var dynamicLootMeta: TextView
    private var pendingDynamicLoot: DynamicLootItem? = null
    private var pendingDynamicLootBitmap: Bitmap? = null
    private var pendingDynamicLootDomain: String = ""
    private var pendingDynamicLootUrl: String = ""
    private var pendingDynamicLootTabId: String = ""
    private val lastDynamicLootUrlByTab = ConcurrentHashMap<String, String>()
    private var dynamicLootShowRunnable: Runnable? = null
    private var dynamicLootHideRunnable: Runnable? = null
    private var dynamicLootFetchTask: Future<*>? = null
    private val dynamicLootExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var nativeMediaControllerFuture: ListenableFuture<MediaController>? = null
    private var nativeMediaController: MediaController? = null
    private val pendingNativeMediaActions = mutableListOf<(MediaController) -> Unit>()
    private var lastNativeMediaError = ""
    @Volatile
    private var lastNativeMediaStateJson = "{\"active\":false,\"playing\":false,\"backend\":\"native\"}"

    private data class LocalMediaEntry(
        val uri: Uri,
        val title: String,
        val mimeType: String
    )

    private data class PendingMediaSelection(
        val uri: Uri,
        val displayName: String,
        val mimeType: String
    )

    private var pendingMediaSelection: PendingMediaSelection? = null
    private var pendingAutomationImageImportJobId: String? = null

    @Volatile
    private var youtubePipActive = false
    @Volatile
    private var youtubePipPlaying = false
    private var youtubePipUrl = ""
    private var suppressNextYoutubePip = false

    private val nativeMediaListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishNativeMediaState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            lastNativeMediaError = error.localizedMessage ?: "KhÃ´ng phÃ¡t Ä‘Æ°á»£c ná»™i dung nÃ y."
            publishNativeMediaState(nativeMediaController)
        }
    }

    @Volatile
    private var offlineSaveInProgress = false

    private val htmlFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            loadOfflineHtml(uri)
        }
    }

    // ------------------------------------------------------------------
    // Chá»n tá»‡p (input type=file) â€” dÃ¹ng chung cho shell vÃ  trang web
    // ------------------------------------------------------------------
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK || data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    private val nativeMediaFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != RESULT_OK || uri == null) {
            // KhÃ´i phá»¥c Ä‘Ãºng tráº¡ng thÃ¡i player trÆ°á»›c Ä‘Ã³ náº¿u ngÆ°á»i dÃ¹ng há»§y chá»n.
            publishNativeMediaState(nativeMediaController)
            return@registerForActivityResult
        }

        val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (takeFlags != 0) {
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Má»™t sá»‘ document provider chá»‰ cáº¥p quyá»n trong phiÃªn hiá»‡n táº¡i.
            }
        }

        val displayName = queryOpenableDisplayName(uri)
            .ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "Tá»‡p media" }
        val mimeType = inferMediaMimeType(
            contentResolver.getType(uri).orEmpty(),
            displayName
        )
        if (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/")) {
            Toast.makeText(this, "Tá»‡p Ä‘Ã£ chá»n khÃ´ng pháº£i MP3/MP4 hoáº·c media Ä‘Æ°á»£c há»— trá»£.", Toast.LENGTH_SHORT).show()
            publishNativeMediaState(nativeMediaController)
            return@registerForActivityResult
        }
        handleSelectedNativeMedia(uri, displayName, mimeType)
    }

    private val nativeMediaFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val treeUri = data?.data
        if (result.resultCode != RESULT_OK || treeUri == null) {
            publishNativeMediaState(nativeMediaController)
            return@registerForActivityResult
        }

        val takeFlags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Má»™t sá»‘ provider chá»‰ cáº¥p quyá»n trong phiÃªn hiá»‡n táº¡i.
        }

        playNativeMediaFolder(treeUri)
    }

    private val automationImageImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val jobId = pendingAutomationImageImportJobId
        pendingAutomationImageImportJobId = null
        if (jobId.isNullOrBlank() || uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val imported = mutableListOf<com.lqlq.browser.automation.ImportedAutomationImage>()
                var importedTotalBytes = 0L
                for (uri in uris.take(24)) {
                    val displayName = queryOpenableDisplayName(uri)
                        .ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "Anh tu may" }
                    val mimeType = inferMediaMimeType(
                        contentResolver.getType(uri).orEmpty(),
                        displayName
                    ).ifBlank { "image/jpeg" }
                    if (!mimeType.startsWith("image/")) continue
                    val bytes = runCatching {
                        contentResolver.openInputStream(uri)?.use { input ->
                            ByteArrayOutputStream().use { output ->
                                copyStreamWithLimit(input, output, MAX_SHARED_IMAGE_BYTES)
                                output.toByteArray()
                            }
                        }
                    }.getOrNull() ?: continue
                    if (importedTotalBytes + bytes.size > MAX_SHARED_IMAGE_CACHE_BYTES) continue
                    importedTotalBytes += bytes.size
                    imported += com.lqlq.browser.automation.ImportedAutomationImage(
                        displayName = displayName,
                        mimeType = mimeType,
                        bytes = bytes
                    )
                }
                if (imported.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Khong co anh hop le de nhap.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                (application as LqlqApp).automationFacade.importImageArtifacts(jobId, imported)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Da nhap ${imported.size} anh vao phien hien tai.", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        error.localizedMessage ?: "Khong the nhap anh cho phien nay.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    private val mediaLibraryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val pending = pendingMediaSelection
        pendingMediaSelection = null
        if (pending != null) {
            playSelectedMediaWithFolderPlaylist(
                pending.uri,
                pending.displayName,
                pending.mimeType
            )
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Video toÃ n mÃ n hÃ¬nh (onShowCustomView cá»§a WebChromeClient â€” video HTML5
    // fullscreen cá»§a Báº¤T Ká»² website nÃ o, khÃ¡c vá»›i "Nháº¡c vÃ  video ná»n" cá»§a
    // riÃªng app). TrÆ°á»›c Ä‘Ã¢y chá»‰ MATCH_PARENT view cá»§a trang, khÃ´ng áº©n thanh
    // tráº¡ng thÃ¡i/Ä‘iá»u hÆ°á»›ng Android vÃ  khÃ´ng cÃ³ nÃºt xoay/khoÃ¡.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenControlsOverlay: FrameLayout? = null
    private var fullscreenRotateBtn: TextView? = null
    private var fullscreenLockBtn: TextView? = null
    private var fullscreenLockOverlay: View? = null
    private var fullscreenLocked = false
    private var fullscreenHideRunnable: Runnable? = null
    private val fullscreenHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NÃºt Ã¢m lÆ°á»£ng váº­t lÃ½ Ä‘iá»u khiá»ƒn kÃªnh media (nháº¡c + TTS).
        volumeControlStream = AudioManager.STREAM_MUSIC

        tabStore = BrowserTabStore(this)
        faviconStore = FaviconStore(this)
        adventureProfileStore = AdventureProfileStore(applicationContext)
        dynamicLootStore = DynamicLootStore(applicationContext)
        dynamicLootRepository = DynamicLootRepository()
        dynamicLootImageCache = DynamicLootImageCache(applicationContext)
        characterPortraitStore = CharacterPortraitStore(applicationContext)
        activeTabId = tabStore.activeTabId()

        root = FrameLayout(this)
        shellWebView = WebView(this)
        pageContainer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            shellWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            pageContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setupAdventureLootOverlay()
        root.addView(
            adventureLootLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setupMediaBubble()
        setupNativeTabSwitcher()
        setContentView(root)
        installBrowserWindowInsets()

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(SHELL_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/dynamic-loot/", DynamicLootAssetHandler(dynamicLootImageCache))
            .addPathHandler("/character-portrait/", CharacterPortraitAssetHandler(characterPortraitStore))
            .build()

        setupShellWebView()
        registerBridgeHub()
        requestStartupPermissions()
        setupBackHandling()

        shellWebView.loadUrl(SHELL_URL)
    }

    /**
     * Quáº£n lÃ½ thanh há»‡ thá»‘ng riÃªng cho trÃ¬nh duyá»‡t thÆ°á»ng (khÃ´ng pháº£i video
     * fullscreen):
     * - dá»c: giá»¯ nguyÃªn status bar + navigation bar nhÆ° giao diá»‡n á»•n Ä‘á»‹nh cÅ©;
     * - ngang: áº©n riÃªng status bar Ä‘á»ƒ láº¥y láº¡i chiá»u cao, váº«n giá»¯ cÃ¡c nÃºt há»‡
     *   thá»‘ng Android vÃ  chá»«a Ä‘Ãºng pháº§n navigation bar á»Ÿ cáº¡nh tÆ°Æ¡ng á»©ng.
     *
     * Activity target SDK 35 cháº¡y edge-to-edge, vÃ¬ váº­y khÃ´ng Ä‘Æ°á»£c dá»±a vÃ o
     * khoáº£ng Ä‘á»‡m máº·c Ä‘á»‹nh cá»§a há»‡ thá»‘ng. Root tá»± nháº­n insets Ä‘á»ƒ trang web khÃ´ng
     * náº±m dÆ°á»›i Back/Home/Äa nhiá»‡m, nhÆ°ng cÅ©ng khÃ´ng táº¡o dáº£i tráº¯ng giáº£ á»Ÿ cáº¡nh
     * Ä‘á»‘i diá»‡n. VÃ¹ng camera/cutout Ä‘Æ°á»£c phÃ©p dÃ¹ng á»Ÿ cáº¡nh ngáº¯n; cÃ¡c nÃºt quan
     * trá»ng cá»§a giao diá»‡n náº±m bÃªn pháº£i váº«n Ä‘Æ°á»£c báº£o vá»‡ bá»Ÿi navigation inset.
     */
    private fun installBrowserWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (customView != null) {
                view.setPadding(0, 0, 0, 0)
                return@setOnApplyWindowInsetsListener insets
            }

            val landscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (landscape) {
                // Chá»‰ chá»«a Ä‘Ãºng cáº¡nh Ä‘ang chá»©a navigation bar. KhÃ´ng cá»™ng
                // status-bar/cutout inset nÃªn khÃ´ng sinh dáº£i tráº¯ng bÃªn trÃ¡i.
                val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(
                    navigation.left,
                    0,
                    navigation.right,
                    navigation.bottom
                )
            } else {
                // Giá»¯ nguyÃªn bá»‘ cá»¥c dá»c trÆ°á»›c Ä‘Ã¢y: ná»™i dung náº±m giá»¯a hai thanh
                // há»‡ thá»‘ng, khÃ´ng Ä‘á»¥ng tá»›i kÃ­ch thÆ°á»›c toolbar HTML hiá»‡n cÃ³.
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )
            }

            // Root Ä‘Ã£ tá»± chá»«a system bars; khÃ´ng truyá»n láº¡i cÃ¹ng cÃ¡c inset
            // nÃ y cho WebView, trÃ¡nh CSS env(safe-area-inset-*) cá»™ng láº§n hai
            // vÃ  táº¡o thanh tráº¯ng/thanh Ä‘á»‡m giáº£. IME váº«n Ä‘Æ°á»£c giá»¯ nguyÃªn.
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    androidx.core.graphics.Insets.NONE
                )
                .setInsets(
                    WindowInsetsCompat.Type.displayCutout(),
                    androidx.core.graphics.Insets.NONE
                )
                .build()
        }

        applyBrowserSystemBars()
    }

    private fun applyBrowserSystemBars() {
        if (!::root.isInitialized || customView != null) return

        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(WindowInsetsCompat.Type.navigationBars())
            if (landscape) {
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
        }
        ViewCompat.requestApplyInsets(root)
        notifyShellOrientationChanged(landscape)
    }

    private fun notifyShellOrientationChanged(landscape: Boolean) {
        if (!::shellWebView.isInitialized || !shellReady || isDestroyed) return
        val script =
            "window.dispatchEvent(new CustomEvent('lqlq-orientation-changed'," +
                "{detail:{landscape:${landscape}}}));"
        shellWebView.post { shellWebView.evaluateJavascript(script, null) }
    }

    fun setBrowserOrientation(mode: String) {
        val target = when (mode.lowercase(Locale.ROOT)) {
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        requestedOrientation = target
    }

    private fun setupAdventureLootOverlay() {
        adventureLootLayer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        adventureLootIcon = ImageView(this).apply {
            setImageResource(R.drawable.adventure_crystal_loot)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            contentDescription = "Linh Tháº¡ch cÃ³ thá»ƒ nháº·t"
            setOnClickListener { collectAdventureLoot() }
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            background = ContextCompat.getDrawable(context, android.R.color.transparent)
        }
        val size = dp(72)
        adventureLootLayer.addView(
            adventureLootIcon,
            FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(20)
                topMargin = dp(160)
            }
        )

        spiritBeastIcon = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 42f
            includeFontPadding = false
        }
        spiritBeastName = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.rgb(29, 69, 50))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        spiritBeastMeta = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.rgb(82, 104, 93))
            maxLines = 1
        }
        spiritBeastCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            alpha = 0f
            scaleX = 0.76f
            scaleY = 0.76f
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = "Linh ThÃº Váº¡n Giá»›i"
            elevation = dp(10).toFloat()
            addView(spiritBeastIcon, LinearLayout.LayoutParams(dp(72), dp(60)))
            addView(spiritBeastName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))
            addView(spiritBeastMeta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
            setOnClickListener { openSpiritBeastEncounter() }
        }
        adventureLootLayer.addView(
            spiritBeastCard,
            FrameLayout.LayoutParams(dp(132), dp(124)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(22)
                topMargin = dp(250)
            }
        )

        dynamicLootImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "áº¢nh Ká»³ Váº­t Váº¡n Giá»›i"
            background = roundedBackground(Color.rgb(235, 245, 240), Color.rgb(92, 168, 130))
            clipToOutline = true
        }
        dynamicLootName = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.rgb(29, 58, 45))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
        }
        dynamicLootMeta = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 10.5f
            setTextColor(Color.rgb(73, 94, 84))
            maxLines = 1
        }
        dynamicLootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            alpha = 0f
            scaleX = 0.76f
            scaleY = 0.76f
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = "Ká»³ Váº­t Váº¡n Giá»›i Ä‘á»™ng"
            elevation = dp(12).toFloat()
            background = roundedBackground(Color.rgb(244, 251, 247), Color.rgb(60, 157, 107))
            addView(dynamicLootImage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(98)))
            addView(dynamicLootName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
            addView(dynamicLootMeta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
            setOnClickListener { openDynamicLootEncounter() }
        }
        adventureLootLayer.addView(
            dynamicLootCard,
            FrameLayout.LayoutParams(dp(158), dp(178)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(28)
                topMargin = dp(310)
            }
        )
    }

    private fun updateAdventureOverlayVisibility() {
        if (!::adventureLootLayer.isInitialized) return
        val hasCrystal = ::adventureLootIcon.isInitialized && adventureLootIcon.visibility == View.VISIBLE
        val hasBeast = ::spiritBeastCard.isInitialized && spiritBeastCard.visibility == View.VISIBLE
        val hasDynamic = ::dynamicLootCard.isInitialized && dynamicLootCard.visibility == View.VISIBLE
        adventureLootLayer.visibility = if (hasCrystal || hasBeast || hasDynamic) View.VISIBLE else View.GONE
    }

    private fun hideAdventureLoot() {
        adventureLootShowRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        adventureLootShowRunnable = null
        pendingAdventureLootUrl = null
        pendingAdventureLootTabId = null
        if (::adventureLootIcon.isInitialized) {
            adventureLootIcon.clearAnimation()
            adventureLootIcon.animate().cancel()
            adventureLootIcon.alpha = 0f
            adventureLootIcon.translationX = 0f
            adventureLootIcon.translationY = 0f
            adventureLootIcon.scaleX = 0.92f
            adventureLootIcon.scaleY = 0.92f
            adventureLootIcon.visibility = View.GONE
        }
        updateAdventureOverlayVisibility()
    }

    private fun hideSpiritBeastEncounter() {
        spiritBeastShowRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        spiritBeastHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        spiritBeastShowRunnable = null
        spiritBeastHideRunnable = null
        pendingSpiritBeast = null
        pendingSpiritBeastDomain = ""
        pendingSpiritBeastUrl = ""
        pendingSpiritBeastTabId = ""
        if (::spiritBeastCard.isInitialized) {
            spiritBeastCard.animate().cancel()
            spiritBeastCard.alpha = 0f
            spiritBeastCard.scaleX = 0.76f
            spiritBeastCard.scaleY = 0.76f
            spiritBeastCard.visibility = View.GONE
        }
        updateAdventureOverlayVisibility()
    }

    private fun hideDynamicLootEncounter() {
        dynamicLootShowRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        dynamicLootHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        dynamicLootShowRunnable = null
        dynamicLootHideRunnable = null
        dynamicLootFetchTask?.cancel(true)
        dynamicLootFetchTask = null
        pendingDynamicLoot = null
        pendingDynamicLootBitmap = null
        pendingDynamicLootDomain = ""
        pendingDynamicLootUrl = ""
        pendingDynamicLootTabId = ""
        if (::dynamicLootCard.isInitialized) {
            dynamicLootCard.animate().cancel()
            dynamicLootCard.alpha = 0f
            dynamicLootCard.scaleX = 0.76f
            dynamicLootCard.scaleY = 0.76f
            dynamicLootCard.visibility = View.GONE
        }
        updateAdventureOverlayVisibility()
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(22).toFloat()
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    private fun maybeScheduleAdventureLoot(tabId: String, url: String) {
        if (!::adventureProfileStore.isInitialized || !adventureProfileStore.hasProfile()) {
            hideAdventureLoot()
            return
        }
        val snapshot = adventureProfileStore.snapshot()
        if (!snapshot.lootEnabled || !snapshot.exists) {
            hideAdventureLoot()
            return
        }
        if (!pageVisible || activeTabId != tabId) {
            hideAdventureLoot()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            hideAdventureLoot()
            return
        }
        if (isAdventureLootExcludedUrl(url)) {
            hideAdventureLoot()
            return
        }
        val normalized = url.trim()
        if (lastAdventureLootUrlByTab[tabId] == normalized) {
            hideAdventureLoot()
            return
        }
        lastAdventureLootUrlByTab[tabId] = normalized
        pendingAdventureLootUrl = normalized
        pendingAdventureLootTabId = tabId
        adventureLootShowRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        val runnable = Runnable {
            showAdventureLoot(normalized)
        }
        adventureLootShowRunnable = runnable
        adventureLootLayer.postDelayed(runnable, 420L)
    }

    private fun showAdventureLoot(url: String) {
        if (!::adventureLootLayer.isInitialized || !::adventureLootIcon.isInitialized) return
        val snapshot = adventureProfileStore.snapshot()
        if (!snapshot.exists || !snapshot.lootEnabled || pendingAdventureLootUrl != url || !pageVisible) {
            hideAdventureLoot()
            return
        }
        adventureLootIcon.visibility = View.VISIBLE
        updateAdventureOverlayVisibility()
        val size = dp(72)
        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val minX = dp(18)
        val maxX = (width - size - dp(18)).coerceAtLeast(minX)
        val minY = (toolbarHeightPx + dp(96)).coerceAtLeast(dp(120))
        val maxY = (height - size - dp(112)).coerceAtLeast(minY)
        val x = if (maxX > minX) Random.nextInt(minX, maxX + 1) else minX
        val y = if (maxY > minY) Random.nextInt(minY, maxY + 1) else minY
        (adventureLootIcon.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = size
            this.height = size
            leftMargin = x
            topMargin = y
        }.also { adventureLootIcon.layoutParams = it }
        adventureLootIcon.alpha = 0f
        adventureLootIcon.scaleX = 0.7f
        adventureLootIcon.scaleY = 0.7f
        adventureLootIcon.translationX = 0f
        adventureLootIcon.translationY = 0f
        adventureLootIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun collectAdventureLoot() {
        val url = pendingAdventureLootUrl ?: return
        val result = adventureProfileStore.collectRealmCrystal()
        pendingAdventureLootUrl = null
        pendingAdventureLootTabId = null
        dispatchAdventureProfileState(result.snapshot)
        animateAdventureLootToHeader(result, url)
    }

    private fun animateAdventureLootToHeader(result: AdventureProfileStore.RewardResult, url: String) {
        if (!::adventureLootIcon.isInitialized) return
        val startX = adventureLootIcon.x
        val startY = adventureLootIcon.y
        val targetX = (root.width - dp(38) - adventureLootIcon.width).toFloat().coerceAtLeast(0f)
        val targetY = dp(18).toFloat()
        val moveX = ObjectAnimator.ofFloat(adventureLootIcon, View.X, startX, targetX)
        val moveY = ObjectAnimator.ofFloat(adventureLootIcon, View.Y, startY, targetY)
        val scaleX = ObjectAnimator.ofFloat(adventureLootIcon, View.SCALE_X, 1f, 0.35f)
        val scaleY = ObjectAnimator.ofFloat(adventureLootIcon, View.SCALE_Y, 1f, 0.35f)
        val fade = ObjectAnimator.ofFloat(adventureLootIcon, View.ALPHA, 1f, 0f)
        AnimatorSet().apply {
            duration = 760L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(moveX, moveY, scaleX, scaleY, fade)
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) = Unit
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    hideAdventureLoot()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    hideAdventureLoot()
                    if (result.rewarded) {
                        runOnUiThread {
                            shellWebView.evaluateJavascript(
                                "window.LqlqAdventureUI && LqlqAdventureUI.onRealmCrystalCollected(${result.toJson().toString()});",
                                null
                            )
                        }
                    }
                    val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                    // Toast nÃ y pháº£i táº¯t khi "Hiá»‡u á»©ng nháº­n Linh Tháº¡ch"
                    // (adventureEffectsToggle) HOáº¶C "ThÃ´ng bÃ¡o khi cháº·n"
                    // (blockNoticeToastsEnabled, menu CÃ´ng cá»¥ â†’ Cháº·n quáº£ng
                    // cÃ¡o) táº¯t â€” ngÆ°á»i dÃ¹ng coi Ä‘Ã¢y lÃ  cÃ´ng táº¯c táº¯t-thÃ´ng-bÃ¡o
                    // chung, khÃ´ng chá»‰ Ã¡p dá»¥ng cho riÃªng thÃ´ng bÃ¡o cháº·n quáº£ng cÃ¡o.
                    if (result.snapshot.effectsEnabled && blockNoticeToastsEnabled) {
                        if (result.rewarded) {
                            Toast.makeText(
                                this@MainActivity,
                                if (host.isNotBlank()) "Nháº·t Ä‘Æ°á»£c 1 Linh Tháº¡ch táº¡i $host" else "Nháº·t Ä‘Æ°á»£c 1 Linh Tháº¡ch",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (result.dailyLimitReached) {
                            Toast.makeText(
                                this@MainActivity,
                                "Báº¡n Ä‘Ã£ Ä‘áº§y háº¡n má»©c Linh Tháº¡ch hÃ´m nay, nhÆ°ng vÃ¹ng Ä‘áº¥t nÃ y váº«n Ä‘Æ°á»£c ghi nháº­n.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            })
            start()
        }
    }

    private fun maybeScheduleDynamicLoot(tabId: String, url: String, title: String): Boolean {
        if (!::adventureProfileStore.isInitialized || !adventureProfileStore.hasProfile()) {
            hideDynamicLootEncounter()
            return false
        }
        if (!::dynamicLootStore.isInitialized || !dynamicLootStore.isEnabled()) {
            hideDynamicLootEncounter()
            return false
        }
        if (!pageVisible || activeTabId != tabId || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            hideDynamicLootEncounter()
            return false
        }
        if (isAdventureLootExcludedUrl(url)) {
            hideDynamicLootEncounter()
            return false
        }

        val normalized = url.trim()
        if (lastDynamicLootUrlByTab[tabId] == normalized) return false
        lastDynamicLootUrlByTab[tabId] = normalized

        val dynamicCount = dynamicLootStore.stateJson().optInt("dynamicCollectionCount", 0)
        // Táº M THá»œI Äá»‚ TEST (v0.32.1): luÃ´n 100% Ä‘á»ƒ ngÆ°á»i dÃ¹ng tháº¥y Ká»³ Váº­t á»Ÿ
        // má»i trang khi thá»­ nghiá»‡m. Äá»•i láº¡i "if (dynamicCount == 0) 0.86 else
        // 0.24" khi test xong.
        val encounterChance = 1.0
        val chanceRoll = deterministicRoll("$normalized|${adventureProfileStore.currentDayKey()}|dynamic-encounter")
        if (chanceRoll > encounterChance) return false

        val rarity = rollDynamicRarity("$normalized|$title|${adventureProfileStore.currentDayKey()}")
        pendingDynamicLootUrl = normalized
        pendingDynamicLootTabId = tabId
        pendingDynamicLootDomain = runCatching { Uri.parse(normalized).host.orEmpty() }.getOrDefault("")
        dynamicLootStore.noteEncounter()

        dynamicLootFetchTask?.cancel(true)
        dynamicLootFetchTask = dynamicLootExecutor.submit {
            try {
                val fetched = dynamicLootRepository.fetchRandom(
                    seed = "$normalized|$title|${System.currentTimeMillis() / 86_400_000L}",
                    rarity = rarity,
                    locale = "vi",
                    theme = adventureProfileStore.snapshot().collectionTheme
                )
                mainHandler.post {
                    if (isDestroyed || pendingDynamicLootUrl != normalized || activeTabId != tabId || !pageVisible) return@post
                    pendingDynamicLoot = fetched.item
                    pendingDynamicLootBitmap = fetched.bitmap
                    showDynamicLootEncounter(fetched.item, fetched.bitmap, normalized)
                }
            } catch (_: Throwable) {
                mainHandler.post {
                    if (pendingDynamicLootUrl == normalized) {
                        pendingDynamicLootUrl = ""
                        pendingDynamicLootTabId = ""
                        pendingDynamicLootDomain = ""
                    }
                }
            }
        }
        return true
    }

    private fun deterministicRoll(seed: String): Double {
        val value = seed.hashCode().toLong() and 0x7fffffffL
        return (value % 100_000L) / 100_000.0
    }

    private fun rollDynamicRarity(seed: String): String {
        val roll = deterministicRoll("$seed|rarity")
        return when {
            roll < 0.003 -> "Tháº§n Thoáº¡i"
            roll < 0.020 -> "Huyá»n Thoáº¡i"
            roll < 0.080 -> "Sá»­ Thi"
            roll < 0.280 -> "Hiáº¿m"
            else -> "ThÆ°á»ng"
        }
    }

    private fun showDynamicLootEncounter(item: DynamicLootItem, bitmap: Bitmap, url: String) {
        if (!::dynamicLootCard.isInitialized || pendingDynamicLootUrl != url || !pageVisible) {
            hideDynamicLootEncounter()
            return
        }
        if (!dynamicLootStore.isEnabled()) {
            hideDynamicLootEncounter()
            return
        }

        val colors = rarityColors(item.rarity)
        dynamicLootCard.background = roundedBackground(colors.first, colors.second)
        dynamicLootImage.setImageBitmap(bitmap)
        dynamicLootName.text = item.name
        dynamicLootMeta.text = "${item.category} Â· ${item.rarity} Â· ${"â˜…".repeat(item.stars.coerceIn(1, 5))}"
        dynamicLootName.setTextColor(colors.third)
        dynamicLootMeta.setTextColor(colors.third)

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val cardWidth = dp(158)
        val cardHeight = dp(178)
        val minX = dp(16)
        val maxX = (width - cardWidth - dp(16)).coerceAtLeast(minX)
        val minY = (toolbarHeightPx + dp(140)).coerceAtLeast(dp(180))
        val maxY = (height - cardHeight - dp(110)).coerceAtLeast(minY)
        var x = if (maxX > minX) Random.nextInt(minX, maxX + 1) else minX
        var y = if (maxY > minY) Random.nextInt(minY, maxY + 1) else minY

        if (::spiritBeastCard.isInitialized && spiritBeastCard.visibility == View.VISIBLE) {
            val otherX = spiritBeastCard.x + spiritBeastCard.width / 2f
            val otherY = spiritBeastCard.y + spiritBeastCard.height / 2f
            if (kotlin.math.abs(otherX - (x + cardWidth / 2f)) < dp(150) &&
                kotlin.math.abs(otherY - (y + cardHeight / 2f)) < dp(190)) {
                x = if (otherX < width / 2f) maxX else minX
                y = (y + dp(150)).coerceAtMost(maxY)
            }
        }

        (dynamicLootCard.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = x
            topMargin = y
        }.also { dynamicLootCard.layoutParams = it }
        dynamicLootCard.visibility = View.VISIBLE
        updateAdventureOverlayVisibility()
        dynamicLootCard.alpha = 0f
        dynamicLootCard.scaleX = 0.72f
        dynamicLootCard.scaleY = 0.72f
        dynamicLootCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        dynamicLootHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        val hide = Runnable {
            if (pendingDynamicLoot?.id == item.id) {
                if (adventureProfileStore.snapshot().effectsEnabled && blockNoticeToastsEnabled) {
                    Toast.makeText(this, "Ká»³ Váº­t ${item.name} Ä‘Ã£ tan vÃ o Váº¡n Giá»›i.", Toast.LENGTH_SHORT).show()
                }
                hideDynamicLootEncounter()
            }
        }
        dynamicLootHideRunnable = hide
        adventureLootLayer.postDelayed(hide, 30_000L)
    }

    private fun openDynamicLootEncounter() {
        val item = pendingDynamicLoot ?: return
        val bitmap = pendingDynamicLootBitmap ?: return
        dynamicLootHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        dynamicLootHideRunnable = null

        val preview = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            background = roundedBackground(Color.rgb(239, 247, 243), rarityColors(item.rarity).second)
        }
        val textView = TextView(this).apply {
            text = "${item.category} Â· ${item.rarity} Â· ${"â˜…".repeat(item.stars.coerceIn(1, 5))}\n\n${item.description}\n\nTÃ¬m tháº¥y táº¡i: ${pendingDynamicLootDomain.ifBlank { "Váº¡n Giá»›i" }}\nNguá»“n: ${dynamicSourceLabel(item)}"
            setPadding(dp(4), dp(14), dp(4), dp(4))
            textSize = 14f
            setTextColor(Color.rgb(42, 58, 50))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
            addView(textView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Ká»³ Váº­t xuáº¥t hiá»‡n: ${item.name}")
            .setView(content)
            .setPositiveButton("Thu tháº­p tháº»") { _, _ -> collectDynamicLoot(item, bitmap) }
            .setNegativeButton("Bá» qua") { _, _ -> hideDynamicLootEncounter() }
            .setOnCancelListener { hideDynamicLootEncounter() }
        if (item.sourceUrl.startsWith("http")) {
            builder.setNeutralButton("Xem nguá»“n") { _, _ -> openExternalUri(Uri.parse(item.sourceUrl)) }
        }
        builder.show()
    }

    private fun collectDynamicLoot(item: DynamicLootItem, bitmap: Bitmap) {
        val localName = runCatching { dynamicLootImageCache.save(item.id, bitmap) }.getOrDefault("")
        val storedItem = item.copy(
            localImageName = localName,
            imageUrl = if (item.imageUrl.startsWith("data:image/")) "" else item.imageUrl
        )
        val result = dynamicLootStore.collect(
            storedItem,
            pendingDynamicLootDomain,
            adventureProfileStore.snapshot().equippedCardIds.toSet()
        )
        hideDynamicLootEncounter()
        val profileSnapshot = adventureProfileStore.snapshot()
        dispatchAdventureProfileState(profileSnapshot)

        if (shellReady && !isDestroyed) {
            val payload = result.toJson().apply {
                put("state", dynamicLootStore.appendTo(profileSnapshot.toJson(), profileSnapshot.equippedCardIds.toSet()))
            }.toString()
            shellWebView.evaluateJavascript(
                "window.LqlqAdventureUI && LqlqAdventureUI.onDynamicLootCollected($payload);",
                null
            )
        }

        if (!result.ok) {
            Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("ÄÃ£ ghi vÃ o Váº¡n Giá»›i Äá»“ GiÃ¡m!")
            .setMessage("${item.name}\n${item.category} Â· ${item.rarity} Â· ${"â˜…".repeat(item.stars.coerceIn(1, 5))}\n\n${item.description}")
            .setPositiveButton("Tuyá»‡t vá»i", null)
            .show()
    }

    private fun dynamicSourceLabel(item: DynamicLootItem): String = when (item.sourceType.lowercase(Locale.ROOT)) {
        "ai", "workers-ai" -> "AI táº¡o nguyÃªn báº£n"
        "wikimedia", "wikipedia", "knowledge" -> "Wikipedia / Wikimedia"
        else -> item.sourceType.ifBlank { "Váº¡n Giá»›i" }
    }

    private fun maybeScheduleSpiritBeast(tabId: String, url: String, title: String) {
        if (!::adventureProfileStore.isInitialized || !adventureProfileStore.hasProfile()) {
            hideSpiritBeastEncounter()
            return
        }
        val snapshot = adventureProfileStore.snapshot()
        if (!snapshot.exists || !snapshot.spiritBeastsEnabled || !pageVisible || activeTabId != tabId) {
            hideSpiritBeastEncounter()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            hideSpiritBeastEncounter()
            return
        }
        if (isAdventureLootExcludedUrl(url)) {
            hideSpiritBeastEncounter()
            return
        }
        val normalized = url.trim()
        if (lastSpiritBeastUrlByTab[tabId] == normalized) return
        lastSpiritBeastUrlByTab[tabId] = normalized

        // Táº M THá»œI Äá»‚ TEST (v0.32.1): luÃ´n 100%. Äá»•i láº¡i
        // "if (snapshot.totalBeastEncounters < 3 && snapshot.collection.isEmpty()) 0.78 else 0.32"
        // khi test xong (nhá»¯ng láº§n Ä‘áº§u cho tá»· lá»‡ cao Ä‘á»ƒ hiá»ƒu há»‡ thá»‘ng, sau Ä‘Ã³
        // vá» má»©c 32% Ä‘á»ƒ Linh ThÃº váº«n cÃ³ cáº£m giÃ¡c báº¥t ngá» vÃ  giÃ¡ trá»‹ sÆ°u táº§m).
        val chance = 1.0
        val roll = (("$normalized|${adventureProfileStore.currentDayKey()}|encounter".hashCode().toLong() and 0x7fffffffL) % 10_000L) / 10_000.0
        if (roll > chance) return

        val beast = SpiritBeastCatalog.choose(normalized, title, adventureProfileStore.currentDayKey())
        pendingSpiritBeast = beast
        pendingSpiritBeastDomain = runCatching { Uri.parse(normalized).host.orEmpty() }.getOrDefault("")
        pendingSpiritBeastUrl = normalized
        pendingSpiritBeastTabId = tabId
        spiritBeastShowRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        val runnable = Runnable { showSpiritBeastEncounter(beast, normalized) }
        spiritBeastShowRunnable = runnable
        adventureLootLayer.postDelayed(runnable, 1_350L)
    }

    private fun showSpiritBeastEncounter(beast: SpiritBeastCatalog.Beast, url: String) {
        val snapshot = adventureProfileStore.snapshot()
        if (!snapshot.exists || !snapshot.spiritBeastsEnabled || pendingSpiritBeastUrl != url || !pageVisible) {
            hideSpiritBeastEncounter()
            return
        }
        val colors = rarityColors(beast.rarity)
        spiritBeastCard.background = roundedBackground(colors.first, colors.second)
        spiritBeastIcon.text = beast.icon
        spiritBeastName.text = beast.name
        spiritBeastMeta.text = "${beast.family} Â· ${beast.rarity}"
        spiritBeastName.setTextColor(colors.third)
        spiritBeastMeta.setTextColor(colors.third)

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val cardWidth = dp(132)
        val cardHeight = dp(124)
        val minX = dp(18)
        val maxX = (width - cardWidth - dp(18)).coerceAtLeast(minX)
        val minY = (toolbarHeightPx + dp(180)).coerceAtLeast(dp(210))
        val maxY = (height - cardHeight - dp(120)).coerceAtLeast(minY)
        var x = if (maxX > minX) Random.nextInt(minX, maxX + 1) else minX
        var y = if (maxY > minY) Random.nextInt(minY, maxY + 1) else minY

        if (::adventureLootIcon.isInitialized && adventureLootIcon.visibility == View.VISIBLE) {
            val crystalCenterX = adventureLootIcon.x + adventureLootIcon.width / 2f
            val crystalCenterY = adventureLootIcon.y + adventureLootIcon.height / 2f
            val beastCenterX = x + cardWidth / 2f
            val beastCenterY = y + cardHeight / 2f
            if (kotlin.math.abs(crystalCenterX - beastCenterX) < dp(120) && kotlin.math.abs(crystalCenterY - beastCenterY) < dp(150)) {
                x = if (crystalCenterX < width / 2f) maxX else minX
                y = (y + dp(120)).coerceAtMost(maxY)
            }
        }

        (spiritBeastCard.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = x
            topMargin = y
        }.also { spiritBeastCard.layoutParams = it }
        spiritBeastCard.visibility = View.VISIBLE
        updateAdventureOverlayVisibility()
        spiritBeastCard.alpha = 0f
        spiritBeastCard.scaleX = 0.7f
        spiritBeastCard.scaleY = 0.7f
        spiritBeastCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        spiritBeastHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        val hide = Runnable {
            if (pendingSpiritBeast?.id == beast.id) {
                if (adventureProfileStore.snapshot().effectsEnabled && blockNoticeToastsEnabled) {
                    Toast.makeText(this, "${beast.name} Ä‘Ã£ rá»i khá»i vÃ¹ng Ä‘áº¥t.", Toast.LENGTH_SHORT).show()
                }
                hideSpiritBeastEncounter()
            }
        }
        spiritBeastHideRunnable = hide
        adventureLootLayer.postDelayed(hide, 24_000L)
    }

    private fun openSpiritBeastEncounter() {
        val beast = pendingSpiritBeast ?: return
        spiritBeastHideRunnable?.let { adventureLootLayer.removeCallbacks(it) }
        spiritBeastHideRunnable = null
        val snapshot = adventureProfileStore.snapshot()
        val domain = pendingSpiritBeastDomain.ifBlank { "vÃ¹ng Ä‘áº¥t chÆ°a biáº¿t" }
        val options = arrayOf(
            "Linh Cáº§u ThÃ´ Ã—${snapshot.orbBasic}  â€¢  ${(beast.baseCatchChance * 100).toInt()}%",
            "Linh Cáº§u Báº¡c Ã—${snapshot.orbSilver}  â€¢  ${(minOf(0.95, beast.baseCatchChance * 1.35) * 100).toInt()}%",
            "Linh Cáº§u HoÃ ng Kim Ã—${snapshot.orbGold}  â€¢  ${(minOf(0.95, beast.baseCatchChance * 1.8) * 100).toInt()}%"
        )
        AlertDialog.Builder(this)
            .setTitle("${beast.icon} ${beast.name} â€” ${beast.rarity}")
            .setMessage("${beast.family} Â· Há»‡ ${beast.habitat}\n\n${beast.description}\n\nNÆ¡i gáº·p: $domain\nChá»n Linh Cáº§u Ä‘á»ƒ thá»­ thu phá»¥c.")
            .setItems(options) { _, index ->
                val type = when (index) {
                    1 -> "silver"
                    2 -> "gold"
                    else -> "basic"
                }
                attemptSpiritBeastCapture(beast, domain, type)
            }
            .setNegativeButton("Bá» qua") { _, _ -> hideSpiritBeastEncounter() }
            .setOnCancelListener { hideSpiritBeastEncounter() }
            .show()
    }

    private fun attemptSpiritBeastCapture(beast: SpiritBeastCatalog.Beast, domain: String, orbType: String) {
        val result = adventureProfileStore.attemptCapture(beast.id, domain, orbType)
        if (result.error.isNotBlank()) {
            Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
            return
        }
        hideSpiritBeastEncounter()
        dispatchAdventureProfileState(result.snapshot)
        val payload = result.toJson().toString()
        if (shellReady && !isDestroyed) {
            shellWebView.evaluateJavascript(
                "window.LqlqAdventureUI && LqlqAdventureUI.onSpiritBeastCapture($payload);",
                null
            )
        }
        if (result.success) {
            AlertDialog.Builder(this)
                .setTitle("Thu phá»¥c thÃ nh cÃ´ng!")
                .setMessage("${beast.icon} ${beast.name} Ä‘Ã£ gia nháº­p Äá»“ GiÃ¡m Váº¡n Giá»›i cá»§a báº¡n.\n\nÄá»™ hiáº¿m: ${beast.rarity}\nHá»‡: ${beast.habitat}")
                .setPositiveButton("Tuyá»‡t vá»i", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Linh ThÃº Ä‘Ã£ thoÃ¡t")
                .setMessage("${beast.name} phÃ¡ vá»¡ phong áº¥n vÃ  biáº¿n máº¥t. Nhá»¯ng láº§n tháº¥t báº¡i liÃªn tiáº¿p sáº½ tÄƒng nháº¹ cÆ¡ há»™i á»Ÿ láº§n thu phá»¥c sau.")
                .setPositiveButton("Tiáº¿p tá»¥c hÃ nh trÃ¬nh", null)
                .show()
        }
    }

    private fun rarityColors(rarity: String): Triple<Int, Int, Int> = when (rarity) {
        "Tháº§n Thoáº¡i" -> Triple(Color.rgb(255, 239, 250), Color.rgb(210, 83, 180), Color.rgb(112, 34, 94))
        "Huyá»n Thoáº¡i" -> Triple(Color.rgb(255, 247, 222), Color.rgb(224, 163, 46), Color.rgb(118, 73, 12))
        "Sá»­ Thi" -> Triple(Color.rgb(244, 235, 255), Color.rgb(143, 85, 210), Color.rgb(82, 40, 128))
        "Hiáº¿m" -> Triple(Color.rgb(230, 245, 255), Color.rgb(69, 147, 206), Color.rgb(31, 84, 124))
        else -> Triple(Color.rgb(233, 250, 241), Color.rgb(72, 171, 119), Color.rgb(32, 91, 60))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun registerBridgeHub() {
        BridgeHub.jsRunner = { script ->
            runOnUiThread {
                if (!isDestroyed) {
                    shellWebView.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val wanted = mutableListOf<String>()

        // TrÃ¬nh chá»n tá»‡p dÃ¹ng Storage Access Framework vÃ  lÆ°u trang dÃ¹ng
        // MediaStore, nÃªn Android 13+ khÃ´ng cáº§n quyá»n Ä‘á»c toÃ n bá»™ áº£nh/video/
        // Ã¢m thanh. Chá»‰ xin quyá»n tháº­t sá»± cáº§n: thÃ´ng bÃ¡o vÃ  ghi Downloads cÅ©.
        if (Build.VERSION.SDK_INT >= 33) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT <= 28) {
            wanted += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ------------------------------------------------------------------
    // Shell WebView (giao diá»‡n lqlq)
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupShellWebView() {
        applyCommonSettings(shellWebView.settings)
        // Giao diá»‡n shell (index.html/CSS/JS) náº±m trong chÃ­nh APK â€” khÃ´ng cÃ³
        // lÃ½ do gÃ¬ Ä‘á»ƒ cache HTTP giá»¯a cÃ¡c phiÃªn báº£n app, vÃ  cache CÃ“ THá»‚
        // khiáº¿n WebView tiáº¿p tá»¥c hiá»ƒn thá»‹ html/css/js CÅ¨ sau khi app Ä‘Ã£ Ä‘Æ°á»£c
        // cáº­p nháº­t (giao diá»‡n Há»“ sÆ¡ PhiÃªu lÆ°u bá»‹ "káº¹t" á»Ÿ báº£n cÅ© dÃ¹ APK má»›i Ä‘Ã£
        // cÃ i Ä‘Ã¨). LuÃ´n táº£i tháº³ng tá»« APK, khÃ´ng qua cache HTTP.
        shellWebView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

        // Viá»‡c 3 (v0.23.4): Ä‘áº·t ná»n trong suá»‘t NGAY tá»« Ä‘áº§u, giá»¯ nguyÃªn mÃ£i â€”
        // khÃ´ng toggle WHITE/TRANSPARENT lÃºc má»Ÿ/Ä‘Ã³ng menu ná»¯a. LÃ½ do: náº¿u
        // WebView tá»«ng váº½ 1 khung hÃ¬nh vá»›i ná»n tráº¯ng Ä‘á»¥c (ngay cáº£ trong quÃ¡
        // khá»©), khung hÃ¬nh Ä‘Ã³ cÃ³ thá»ƒ bá»‹ hiá»ƒn thá»‹ láº¡i y há»‡t trong khoáº£nh kháº¯c
        // bringChildToFront() trÆ°á»›c khi CSS ká»‹p váº½ láº¡i ná»™i dung tháº­t (chá»›p
        // tráº¯ng). Ná»n trong suá»‘t cá»‘ Ä‘á»‹nh + html,body{background:...} Ä‘á»¥c
        // trong CSS (bÃ¬nh thÆ°á»ng) Ä‘áº£m báº£o khÃ´ng cÃ²n khung hÃ¬nh tráº¯ng "cÅ©"
        // nÃ o cÃ³ thá»ƒ lá»™ ra. z-order váº«n do bringChildToFront() quyáº¿t Ä‘á»‹nh.
        shellWebView.setBackgroundColor(Color.TRANSPARENT)
        applyBackgroundRendererPriority(shellWebView)

        ttsBridge = TtsBridge(applicationContext)
        shellBridge = ShellBridge(this)
        automationBridge = AutomationBridge(
            context = applicationContext,
            facade = (application as LqlqApp).automationFacade,
            onSharePublish = { request ->
                shareAutomationPublish(request)
            },
            onImportImages = onImportImages@{ jobId ->
                pendingAutomationImageImportJobId = jobId
                return@onImportImages try {
                    automationImageImportLauncher.launch(arrayOf("image/*"))
                    true
                } catch (_: Exception) {
                    pendingAutomationImageImportJobId = null
                    false
                }
            }
        )
        adventureProfileBridge = AdventureProfileBridge(this, adventureProfileStore, dynamicLootStore, characterPortraitStore)
        pageBridge = PageBridge { currentPageWebView() }
        shellWebView.addJavascriptInterface(shellBridge, "LqlqAndroid")
        shellWebView.addJavascriptInterface(automationBridge, "LqlqAutomation")
        shellWebView.addJavascriptInterface(adventureProfileBridge, "LqlqAdventure")
        shellWebView.addJavascriptInterface(ttsBridge!!, "LqlqTtsBridge")
        shellWebView.addJavascriptInterface(pageBridge, "LqlqPageBridge")

        shellWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // Giá»¯ shell trong asset; link http báº¥m trong shell má»Ÿ nhÆ° má»™t trang.
                if (request.url.host == SHELL_HOST) return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val tabId = activeTabId ?: tabStore.activeTabId()
                    openPage(tabId, url)
                    return true
                }
                openExternalUri(request.url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith(SHELL_URL)) {
                    shellReady = true
                    notifyShellTabsChanged()
                    dispatchAdventureProfileState(adventureProfileStore.snapshot())
                    restoreActiveTabAfterShellLoad()
                    publishNativeMediaState(nativeMediaController)
                }
            }
        }

        shellWebView.webChromeClient = createChromeClient()
    }

    // ------------------------------------------------------------------
    // Media native: file MP3/MP4 + URL media trá»±c tiáº¿p, cháº¡y ngoÃ i ná»n
    // ------------------------------------------------------------------

    private fun connectNativeMediaController() {
        if (nativeMediaController != null || nativeMediaControllerFuture != null) return

        val token = SessionToken(
            this,
            ComponentName(this, NativeMediaPlaybackService::class.java)
        )
        val future = MediaController.Builder(this, token).buildAsync()
        nativeMediaControllerFuture = future
        future.addListener(
            {
                if (nativeMediaControllerFuture === future) {
                    try {
                        val controller = future.get()
                        nativeMediaController = controller
                        controller.addListener(nativeMediaListener)
                        val pending = pendingNativeMediaActions.toList()
                        pendingNativeMediaActions.clear()
                        pending.forEach { action -> action(controller) }
                        publishNativeMediaState(controller)
                    } catch (error: Exception) {
                        pendingNativeMediaActions.clear()
                        nativeMediaControllerFuture = null
                        lastNativeMediaError = error.localizedMessage
                            ?: "KhÃ´ng káº¿t ná»‘i Ä‘Æ°á»£c trÃ¬nh phÃ¡t Android."
                        publishNativeMediaState(null)
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun releaseNativeMediaController() {
        nativeMediaController?.removeListener(nativeMediaListener)
        nativeMediaController = null
        nativeMediaControllerFuture?.let { MediaController.releaseFuture(it) }
        nativeMediaControllerFuture = null
    }

    private fun withNativeMediaController(action: (MediaController) -> Unit) {
        val controller = nativeMediaController
        if (controller != null) {
            action(controller)
            return
        }
        pendingNativeMediaActions += action
        connectNativeMediaController()
    }

    fun launchNativeMediaFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            nativeMediaFileLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c trÃ¬nh chá»n tá»‡p.", Toast.LENGTH_SHORT).show()
            publishNativeMediaState(nativeMediaController)
        }
    }

    fun launchNativeMediaFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        try {
            nativeMediaFolderLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c trÃ¬nh chá»n thÆ° má»¥c.", Toast.LENGTH_SHORT).show()
            publishNativeMediaState(nativeMediaController)
        }
    }

    private fun handleSelectedNativeMedia(
        uri: Uri,
        displayName: String,
        mimeType: String
    ) {
        val permission = requiredMediaLibraryPermission(mimeType)
        if (
            permission != null &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMediaSelection = PendingMediaSelection(uri, displayName, mimeType)
            mediaLibraryPermissionLauncher.launch(arrayOf(permission))
            return
        }
        playSelectedMediaWithFolderPlaylist(uri, displayName, mimeType)
    }

    private fun requiredMediaLibraryPermission(mimeType: String): String? {
        return when {
            Build.VERSION.SDK_INT >= 33 && mimeType.startsWith("audio/") ->
                Manifest.permission.READ_MEDIA_AUDIO
            Build.VERSION.SDK_INT >= 33 && mimeType.startsWith("video/") ->
                Manifest.permission.READ_MEDIA_VIDEO
            Build.VERSION.SDK_INT in 23..32 -> Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
    }

    private fun playSelectedMediaWithFolderPlaylist(
        selectedUri: Uri,
        displayName: String,
        mimeType: String
    ) {
        val selected = LocalMediaEntry(selectedUri, displayName, mimeType)
        val playlist = queryContainingFolderPlaylist(selected)
            .ifEmpty { listOf(selected) }
        val selectedIndex = playlist.indexOfFirst {
            it.uri == selectedUri || it.title.equals(displayName, ignoreCase = true)
        }.coerceAtLeast(0)

        val subtitle = if (playlist.size > 1) {
            "ThÆ° má»¥c hiá»‡n táº¡i â€¢ ${playlist.size} tá»‡p â€¢ tá»± chuyá»ƒn bÃ i"
        } else {
            "Tá»‡p trong mÃ¡y â€¢ phÃ¡t ná»n Android"
        }
        playNativePlaylist(playlist, selectedIndex, subtitle)
    }

    private fun playNativeMediaFolder(treeUri: Uri) {
        val playlist = queryTreeFolderPlaylist(treeUri)
        if (playlist.isEmpty()) {
            Toast.makeText(
                this,
                "ThÆ° má»¥c nÃ y khÃ´ng cÃ³ tá»‡p nháº¡c hoáº·c video Ä‘Æ°á»£c há»— trá»£.",
                Toast.LENGTH_SHORT
            ).show()
            publishNativeMediaState(nativeMediaController)
            return
        }
        playNativePlaylist(
            playlist,
            0,
            "ThÆ° má»¥c Ä‘Ã£ chá»n â€¢ ${playlist.size} tá»‡p â€¢ tá»± chuyá»ƒn bÃ i"
        )
    }

    private fun queryTreeFolderPlaylist(treeUri: Uri): List<LocalMediaEntry> {
        val entries = mutableListOf<LocalMediaEntry>()
        try {
            val parentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val documentId = if (idIndex >= 0) cursor.getString(idIndex).orEmpty() else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    val rawMime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    if (documentId.isBlank() || name.isBlank()) continue
                    val mime = inferMediaMimeType(rawMime, name)
                    if (!isSupportedMedia(mime, name)) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    entries += LocalMediaEntry(documentUri, name, mime)
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return sortLocalMedia(entries)
    }

    private fun queryContainingFolderPlaylist(selected: LocalMediaEntry): List<LocalMediaEntry> {
        val entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryModernContainingFolderPlaylist(selected)
        } else {
            queryLegacyContainingFolderPlaylist(selected)
        }.toMutableList()

        // Má»™t sá»‘ document provider tráº£ URI khÃ¡c vá»›i URI MediaStore cá»§a cÃ¹ng tá»‡p.
        // Báº£o Ä‘áº£m bÃ i ngÆ°á»i dÃ¹ng vá»«a chá»n luÃ´n náº±m trong hÃ ng Ä‘á»£i, ká»ƒ cáº£ khi truy
        // váº¥n thÆ° má»¥c chá»‰ tráº£ vá» cÃ¡c tá»‡p cÃ²n láº¡i hoáº·c provider khÃ´ng Ã¡nh xáº¡ URI.
        if (entries.none {
                it.uri == selected.uri ||
                    it.title.equals(selected.title, ignoreCase = true)
            }
        ) {
            entries += selected
        }
        return sortLocalMedia(entries)
    }

    private fun queryModernContainingFolderPlaylist(
        selected: LocalMediaEntry
    ): List<LocalMediaEntry> {
        val location = resolveSelectedMediaLocation(selected) ?: return emptyList()
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val wantedMediaType = if (selected.mimeType.startsWith("video/")) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        } else {
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = buildString {
            append("${MediaStore.Files.FileColumns.RELATIVE_PATH}=?")
            append(" AND ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?")
            append(" AND ${MediaStore.MediaColumns.IS_PENDING}=0")
        }
        val args = arrayOf(location, wantedMediaType.toString())
        val entries = mutableListOf<LocalMediaEntry>()
        try {
            contentResolver.query(filesUri, projection, selection, args, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0) continue
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val rawMime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    val mime = inferMediaMimeType(rawMime, name)
                    if (!isSupportedMedia(mime, name)) continue
                    entries += LocalMediaEntry(
                        ContentUris.withAppendedId(filesUri, id),
                        name,
                        mime
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return entries
    }

    @Suppress("DEPRECATION")
    private fun queryLegacyContainingFolderPlaylist(
        selected: LocalMediaEntry
    ): List<LocalMediaEntry> {
        val selectedPath = resolveLegacyMediaPath(selected.uri) ?: return emptyList()
        val parentPath = File(selectedPath).parentFile?.absolutePath ?: return emptyList()
        val collection = if (selected.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val entries = mutableListOf<LocalMediaEntry>()
        try {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("$parentPath/%"),
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (cursor.moveToNext()) {
                    if (idIndex < 0 || nameIndex < 0 || pathIndex < 0) continue
                    val path = cursor.getString(pathIndex).orEmpty()
                    if (File(path).parentFile?.absolutePath != parentPath) continue
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val rawMime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    val mime = inferMediaMimeType(rawMime, name)
                    if (!isSupportedMedia(mime, name)) continue
                    entries += LocalMediaEntry(
                        ContentUris.withAppendedId(collection, id),
                        name,
                        mime
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return entries
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacyMediaPath(uri: Uri): String? {
        if (
            DocumentsContract.isDocumentUri(this, uri) &&
            uri.authority == "com.android.externalstorage.documents"
        ) {
            try {
                val documentId = DocumentsContract.getDocumentId(uri)
                val parts = documentId.split(":", limit = 2)
                if (parts.firstOrNull().equals("primary", ignoreCase = true)) {
                    val relative = parts.getOrNull(1).orEmpty()
                    if (relative.isNotBlank()) {
                        return File(Environment.getExternalStorageDirectory(), relative).absolutePath
                    }
                }
            } catch (_: Exception) {
            }
        }

        val candidates = mutableListOf(uri)
        if (
            DocumentsContract.isDocumentUri(this, uri) &&
            uri.authority == "com.android.providers.media.documents"
        ) {
            try {
                val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                val id = parts.getOrNull(1)?.toLongOrNull()
                val collection = when (parts.firstOrNull()) {
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                if (collection != null && id != null) {
                    candidates += ContentUris.withAppendedId(collection, id)
                }
            } catch (_: Exception) {
            }
        }

        for (candidate in candidates) {
            try {
                contentResolver.query(
                    candidate,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        val path = if (index >= 0) cursor.getString(index).orEmpty() else ""
                        if (path.isNotBlank()) return path
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun resolveSelectedMediaLocation(selected: LocalMediaEntry): String? {
        val directCandidates = mutableListOf(selected.uri)
        if (
            DocumentsContract.isDocumentUri(this, selected.uri) &&
            selected.uri.authority == "com.android.providers.media.documents"
        ) {
            try {
                val documentId = DocumentsContract.getDocumentId(selected.uri)
                val parts = documentId.split(":", limit = 2)
                val id = parts.getOrNull(1)?.toLongOrNull()
                val collection = when (parts.firstOrNull()) {
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                if (collection != null && id != null) {
                    directCandidates += ContentUris.withAppendedId(collection, id)
                }
            } catch (_: Exception) {
            }
        }

        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        for (candidate in directCandidates) {
            try {
                contentResolver.query(candidate, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val value = if (index >= 0) cursor.getString(index).orEmpty() else ""
                        if (value.isNotBlank()) return value
                    }
                }
            } catch (_: Exception) {
            }
        }

        // ACTION_OPEN_DOCUMENT Ä‘Ã´i khi tráº£ URI cá»§a DownloadsProvider thay vÃ¬
        // URI MediaStore. Khi Ä‘Ã³ Ä‘á»‘i chiáº¿u tÃªn + kÃ­ch thÆ°á»›c Ä‘á»ƒ tÃ¬m láº¡i báº£n ghi
        // MediaStore vÃ  láº¥y RELATIVE_PATH, trÃ¡nh báº¯t ngÆ°á»i dÃ¹ng chá»n láº¡i thÆ° má»¥c.
        val collection = if (selected.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selectedSize = queryOpenableSize(selected.uri)
        val lookupProjection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        val lookupSelection: String
        val lookupArgs: Array<String>
        if (selectedSize > 0L) {
            lookupSelection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?"
            lookupArgs = arrayOf(selected.title, selectedSize.toString())
        } else {
            lookupSelection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            lookupArgs = arrayOf(selected.title)
        }
        try {
            contentResolver.query(
                collection,
                lookupProjection,
                lookupSelection,
                lookupArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val value = if (index >= 0) cursor.getString(index).orEmpty() else ""
                    if (value.isNotBlank()) return value
                }
            }
        } catch (_: Exception) {
        }

        if (
            DocumentsContract.isDocumentUri(this, selected.uri) &&
            selected.uri.authority == "com.android.externalstorage.documents"
        ) {
            try {
                val documentId = DocumentsContract.getDocumentId(selected.uri)
                val relative = documentId.substringAfter(':', "")
                val folder = relative.substringBeforeLast('/', "")
                if (folder.isNotBlank()) return "$folder/"
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun sortLocalMedia(entries: List<LocalMediaEntry>): List<LocalMediaEntry> {
        val collator = Collator.getInstance(Locale("vi", "VN")).apply {
            strength = Collator.PRIMARY
        }
        return entries.distinctBy { it.uri.toString() }
            .sortedWith { first, second -> collator.compare(first.title, second.title) }
    }

    private fun isSupportedMedia(mimeType: String, name: String): Boolean {
        if (mimeType.startsWith("audio/") || mimeType.startsWith("video/")) return true
        return name.substringBeforeLast('?').substringBeforeLast('#')
            .lowercase(Locale.ROOT)
            .matches(Regex(".*\\.(mp3|m4a|m4b|aac|ogg|oga|wav|flac|mp4|m4v|webm|mov|mkv|3gp)$"))
    }

    fun playNativeMediaUrl(url: String, title: String, mimeType: String) {
        val uri = try { Uri.parse(url.trim()) } catch (_: Exception) { null }
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(this, "LiÃªn káº¿t media khÃ´ng há»£p lá»‡.", Toast.LENGTH_SHORT).show()
            return
        }
        val safeTitle = title.ifBlank {
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "Media trá»±c tiáº¿p"
        }
        playNativeMedia(
            uri,
            safeTitle,
            inferMediaMimeType(mimeType, safeTitle),
            sourceKind = "url"
        )
    }

    private fun playNativeMedia(
        uri: Uri,
        title: String,
        mimeType: String,
        sourceKind: String
    ) {
        val subtitle = if (sourceKind == "file") {
            "Tá»‡p trong mÃ¡y â€¢ phÃ¡t ná»n Android"
        } else {
            "LiÃªn káº¿t media trá»±c tiáº¿p â€¢ phÃ¡t ná»n Android"
        }
        playNativePlaylist(
            listOf(LocalMediaEntry(uri, title, mimeType)),
            0,
            subtitle
        )
    }

    private fun playNativePlaylist(
        entries: List<LocalMediaEntry>,
        startIndex: Int,
        subtitle: String
    ) {
        if (entries.isEmpty()) return
        lastNativeMediaError = ""
        withNativeMediaController { controller ->
            val items = entries.map { entry ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(entry.title.ifBlank { "Ná»™i dung Ä‘ang phÃ¡t" })
                    .setArtist("lqlq Browser")
                    .setAlbumTitle(subtitle)
                    .build()
                val itemBuilder = MediaItem.Builder()
                    .setMediaId(entry.uri.toString())
                    .setUri(entry.uri)
                    .setMediaMetadata(metadata)
                if (entry.mimeType.isNotBlank()) itemBuilder.setMimeType(entry.mimeType)
                itemBuilder.build()
            }
            controller.setMediaItems(
                items,
                startIndex.coerceIn(0, items.lastIndex),
                0L
            )
            controller.prepare()
            controller.play()
            publishNativeMediaState(controller)
        }
    }

    fun nativeMediaCommand(command: String) {
        withNativeMediaController { controller ->
            when (command.lowercase()) {
                "play" -> controller.play()
                "pause" -> controller.pause()
                "toggle" -> if (controller.isPlaying) controller.pause() else controller.play()
                "next" -> if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                "previous" -> if (controller.hasPreviousMediaItem()) {
                    controller.seekToPreviousMediaItem()
                } else {
                    controller.seekTo(0L)
                }
                "stop" -> {
                    controller.stop()
                    controller.clearMediaItems()
                    lastNativeMediaError = ""
                }
                "seekback" -> controller.seekBack()
                "seekforward" -> controller.seekForward()
            }
            publishNativeMediaState(controller)
        }
    }

    fun setNativeMediaRepeatOne(enabled: Boolean) {
        getSharedPreferences("native_media_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("repeat_one", enabled)
            .apply()
        withNativeMediaController { controller ->
            controller.repeatMode = if (enabled) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            publishNativeMediaState(controller)
        }
    }

    fun setNativeMediaVolume(value: Float) {
        withNativeMediaController { controller ->
            controller.volume = value.coerceIn(0f, 1f)
            publishNativeMediaState(controller)
        }
    }

    fun getNativeMediaStateJson(): String = lastNativeMediaStateJson

    private fun publishNativeMediaState(player: Player?) {
        lastNativeMediaStateJson = buildNativeMediaStateJson(player)
        if (!shellReady || !::shellWebView.isInitialized || isDestroyed) return
        val quoted = JSONObject.quote(lastNativeMediaStateJson)
        shellWebView.evaluateJavascript(
            "window.ShieldMedia && window.ShieldMedia.onNativeState($quoted);",
            null
        )
    }

    private fun buildNativeMediaStateJson(player: Player?): String {
        val item = player?.currentMediaItem
        val local = item?.localConfiguration
        val source = local?.uri?.toString().orEmpty()
        val mime = local?.mimeType.orEmpty()
        val title = item?.mediaMetadata?.title?.toString().orEmpty()
        val subtitle = item?.mediaMetadata?.albumTitle?.toString().orEmpty()
        val active = item != null && player.mediaItemCount > 0
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L } ?: -1L
        val kind = if (
            mime.startsWith("video/", ignoreCase = true) ||
            source.substringBefore('?').substringBefore('#')
                .lowercase().matches(Regex(".*\\.(mp4|webm|mkv|mov|3gp|m4v)$"))
        ) "video" else "audio"

        return JSONObject()
            .put("backend", "native")
            .put("active", active)
            .put("playing", active && player?.isPlaying == true)
            .put("loading", active && player?.playbackState == Player.STATE_BUFFERING)
            .put("title", title)
            .put("text", subtitle)
            .put("source", source)
            .put("mime", mime)
            .put("kind", kind)
            .put("volume", player?.volume?.toDouble() ?: 1.0)
            .put("positionMs", player?.currentPosition ?: 0L)
            .put("durationMs", duration)
            .put("repeatOne", player?.repeatMode == Player.REPEAT_MODE_ONE)
            .put("autoNext", true)
            .put("hasNext", active && player?.hasNextMediaItem() == true)
            .put("hasPrevious", active && player?.hasPreviousMediaItem() == true)
            .put("itemIndex", if (active) player?.currentMediaItemIndex ?: 0 else -1)
            .put("itemCount", player?.mediaItemCount ?: 0)
            .put("error", lastNativeMediaError)
            .toString()
    }

    // ------------------------------------------------------------------
    // YouTube Picture-in-Picture: giá»¯ nguyÃªn iframe chÃ­nh thá»©c trong Activity
    // ------------------------------------------------------------------

    fun setYoutubePlaybackState(active: Boolean, playing: Boolean, url: String) {
        youtubePipActive = active
        youtubePipPlaying = active && playing
        youtubePipUrl = if (active) url else ""
        updateYoutubePictureInPictureParams()
    }

    fun enterYoutubePictureInPicture() {
        if (!youtubePipActive) {
            Toast.makeText(this, "ChÆ°a cÃ³ video YouTube Ä‘ang má»Ÿ.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Thiáº¿t bá»‹ cáº§n Android 8.0 trá»Ÿ lÃªn Ä‘á»ƒ dÃ¹ng PiP.", Toast.LENGTH_SHORT).show()
            return
        }
        prepareYoutubePipSurface(enterAfterPrepare = true)
    }

    fun openYoutubeExternally(url: String) {
        val target = url.ifBlank { youtubePipUrl }
        val uri = try { Uri.parse(target) } catch (_: Exception) { null }
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(this, "LiÃªn káº¿t YouTube khÃ´ng há»£p lá»‡.", Toast.LENGTH_SHORT).show()
            return
        }
        val youtubeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Má»Ÿ app YouTube lÃ  má»™t thao tÃ¡c Ä‘iá»u hÆ°á»›ng cÃ³ chá»§ Ã½, khÃ´ng pháº£i báº¥m Home.
        // Cháº·n Ä‘Ãºng láº§n onUserLeaveHint káº¿ tiáº¿p Ä‘á»ƒ khÃ´ng táº¡o thÃªm cá»­a sá»• PiP trÃ¹ng.
        suppressNextYoutubePip = true
        if (::shellWebView.isInitialized) {
            shellWebView.postDelayed({ suppressNextYoutubePip = false }, 2_000L)
        }
        try {
            startActivity(youtubeIntent)
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                suppressNextYoutubePip = false
                Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c YouTube.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildYoutubePictureInPictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (::shellWebView.isInitialized) {
            val sourceRect = Rect()
            if (shellWebView.getGlobalVisibleRect(sourceRect) && !sourceRect.isEmpty) {
                builder.setSourceRectHint(sourceRect)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(youtubePipActive && youtubePipPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updateYoutubePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            setPictureInPictureParams(buildYoutubePictureInPictureParams())
        } catch (_: Exception) {
        }
    }

    private fun prepareYoutubePipSurface(enterAfterPrepare: Boolean) {
        if (!youtubePipActive || !::shellWebView.isInitialized || isDestroyed) return
        overlayOpen = true
        updatePageVisibility()
        if (::mediaBubble.isInitialized) mediaBubble.visibility = View.GONE
        shellWebView.evaluateJavascript(
            "window.ShieldMedia && window.ShieldMedia.prepareForPip && window.ShieldMedia.prepareForPip();"
        ) {
            if (enterAfterPrepare) enterYoutubePipNow()
        }
    }

    private fun enterYoutubePipNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isDestroyed) return
        if (isInPictureInPictureMode) return
        try {
            enterPictureInPictureMode(buildYoutubePictureInPictureParams())
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng thá»ƒ má»Ÿ Picture-in-Picture.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyShellPipMode(inPip: Boolean) {
        if (!shellReady || !::shellWebView.isInitialized || isDestroyed) return
        shellWebView.evaluateJavascript(
            "window.ShieldMedia && window.ShieldMedia.onPipModeChanged && " +
                "window.ShieldMedia.onPipModeChanged(${inPip});",
            null
        )
    }

    private fun queryOpenableDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index).orEmpty() else ""
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }


    private fun queryOpenableSize(uri: Uri): Long {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use -1L
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun inferMediaMimeType(rawMime: String, nameOrUrl: String): String {
        val normalized = rawMime.substringBefore(';').trim().lowercase()
        if (normalized.startsWith("audio/") || normalized.startsWith("video/")) {
            return normalized
        }
        val extension = nameOrUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "m4a", "m4b" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            else -> normalized.takeUnless {
                it.isBlank() || it == "application/octet-stream"
            }.orEmpty()
        }
    }

    // ------------------------------------------------------------------
    // Page WebViews (website tháº­t)
    // ------------------------------------------------------------------

    private fun applyCommonSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.allowContentAccess = true

        // KhÃ´ng cáº¥p quyá»n file:// cho má»i website. Chá»‰ WebView Ä‘ang má»Ÿ MHT cá»¥c
        // bá»™ má»›i báº­t táº¡m allowFileAccess trong openOfflineMhtFile().
        settings.allowFileAccess = false

        settings.mediaPlaybackRequiresUserGesture = true
    }

    /**
     * Viá»‡c "nháº¡c/video táº¯t khi rá»i app" (v0.23.15): khi Activity khÃ´ng cÃ²n
     * hiá»ƒn thá»‹ (ra ná»n), tiáº¿n trÃ¬nh renderer cá»§a WebView (Chromium, tÃ¡ch
     * riÃªng khá»i tiáº¿n trÃ¬nh app) máº·c Ä‘á»‹nh bá»‹ há»‡ Ä‘iá»u hÃ nh háº¡ má»©c Æ°u tiÃªn,
     * khiáº¿n JS/Ã¢m thanh HTML5 bÃªn trong dá»… bá»‹ há»‡ thá»‘ng táº¡m dá»«ng dÃ¹ tiáº¿n
     * trÃ¬nh app chÃ­nh váº«n sá»‘ng (nhá» PlaybackService foreground). Giá»¯ má»©c Æ°u
     * tiÃªn renderer á»Ÿ "IMPORTANT" (nhÆ° thá»ƒ Ä‘ang hiá»ƒn thá»‹) ngay cáº£ khi á»Ÿ ná»n,
     * Ä‘á»ƒ nháº¡c/video cÃ³ cÆ¡ há»™i tiáº¿p tá»¥c cháº¡y giá»‘ng hÃ nh vi TTS (vá»‘n lÃ  service
     * native, khÃ´ng phá»¥ thuá»™c renderer cá»§a WebView).
     *
     * LÆ°u Ã½: Ä‘Ã¢y lÃ  API tá»‘t nháº¥t WebView cung cáº¥p cho má»¥c Ä‘Ã­ch nÃ y â€” khÃ´ng
     * Ä‘áº£m báº£o tuyá»‡t Ä‘á»‘i 100% nhÆ° TTS (TTS dÃ¹ng engine há»‡ thá»‘ng, hoÃ n toÃ n
     * tÃ¡ch khá»i WebView), vÃ  khÃ´ng giá»¯ Ä‘Æ°á»£c náº¿u há»‡ Ä‘iá»u hÃ nh/ ngÆ°á»i dÃ¹ng
     * Ä‘Ã³ng háº³n á»©ng dá»¥ng (vuá»‘t khá»i Recents) vÃ¬ khi Ä‘Ã³ Activity/WebView bá»‹
     * huá»· tháº­t sá»±, khÃ´ng cÃ²n gÃ¬ Ä‘á»ƒ giá»¯ Æ°u tiÃªn ná»¯a.
     */
    private fun applyBackgroundRendererPriority(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            } catch (_: Exception) {
            }
        }
    }

    private fun currentPageWebView(): WebView? =
        activeTabId?.let { pageWebViews[it] }

    // ------------------------------------------------------------------
    // Há»‡ thá»‘ng tháº» native â€” nguá»“n dá»¯ liá»‡u duy nháº¥t + WebView cache giá»›i háº¡n
    // ------------------------------------------------------------------

    private fun setupNativeTabSwitcher() {
        nativeTabSwitcher = NativeTabSwitcherView(
            this,
            object : NativeTabSwitcherView.Callbacks {
                override fun onNewTab() {
                    createNativeTab("", tabStore.currentMode)
                    nativeTabSwitcher.hide(notify = false)
                    updatePageVisibility()
                }

                override fun onSelectTab(tabId: String) {
                    selectNativeTab(tabId, tabStore.currentMode)
                    nativeTabSwitcher.hide(notify = false)
                    updatePageVisibility()
                }

                override fun onCloseTab(tabId: String) {
                    closeNativeTab(tabId, tabStore.currentMode)
                }

                override fun onCloseOtherTabs() {
                    closeOtherNativeTabs()
                }

                override fun onCloseAllTabs() {
                    closeAllNativeTabs()
                }

                override fun onDismiss() {
                    updatePageVisibility()
                }

                override fun getFaviconBitmap(url: String): android.graphics.Bitmap? =
                    faviconStore.getBitmap(url)
            }
        )
        root.addView(
            nativeTabSwitcher,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun getTabStateJson(): String = tabStore.stateJson()

    /**
     * Snapshot Ä‘á»“ng bá»™ dÃ¹ng bá»Ÿi nÃºt "LÆ°u trang hiá»‡n táº¡i". Nguá»“n dá»¯ liá»‡u lÃ 
     * TabStore native nÃªn khÃ´ng phá»¥ thuá»™c callback JavaScript cÃ³ Ä‘áº¿n ká»‹p hay
     * khÃ´ng. Favicon chá»‰ láº¥y tá»« cache cá»¥c bá»™ do WebChromeClient thu Ä‘Æ°á»£c.
     */
    fun getActivePageSnapshotJson(): String {
        val tab = tabStore.currentTab()
        val url = tab.url.trim()
        return JSONObject().apply {
            put("tabId", tab.id)
            put("url", url)
            put("title", tab.title)
            put("loading", tab.isLoading)
            put("visible", pageVisible && activeTabId == tab.id)
            put("faviconData", faviconStore.peekDataUrl(url))
        }.toString()
    }

    /** Favicon cache cá»¥c bá»™ cho trang tháº» má»›i; tuyá»‡t Ä‘á»‘i khÃ´ng phÃ¡t request máº¡ng. */
    fun getCachedFaviconData(url: String): String = faviconStore.getDataUrl(url)

    fun showNativeTabSwitcher() {
        nativeTabSwitcher.show(tabStore.tabs(), tabStore.activeTabId())
        root.bringChildToFront(nativeTabSwitcher)
    }

    fun setNativeTabMode(mode: String) {
        val tab = tabStore.setMode(mode)
        activeTabId = tab.id
        activateTab(tab)
        notifyShellTabsChanged()
    }

    fun createNativeTab(url: String, mode: String = tabStore.currentMode): String {
        tabStore.setMode(mode)
        val tab = tabStore.createTab(url = url)
        activeTabId = tab.id
        activateTab(tab, forceReload = url.isNotBlank())
        notifyShellTabsChanged()
        return tab.id
    }

    fun selectNativeTab(tabId: String, mode: String = tabStore.currentMode) {
        tabStore.setMode(mode)
        val tab = tabStore.selectTab(tabId) ?: return
        activateTab(tab)
        notifyShellTabsChanged()
    }

    fun closeNativeTab(tabId: String, mode: String = tabStore.currentMode) {
        tabStore.setMode(mode)
        destroyPageWebView(tabId)
        chapterClipperEnabledTabs.remove(tabId)
        tabRootDomain.remove(tabId)
        lastUserGestureNavAt.remove(tabId)
        lastCleartextWarningUrl.remove(tabId)
        val next = tabStore.closeTab(tabId)
        activeTabId = next.id
        activateTab(next)
        notifyShellTabsChanged()
    }

    fun closeOtherNativeTabs() {
        val keepId = tabStore.activeTabId()
        val removedIds = tabStore.tabs().map { it.id }.filter { it != keepId }
        removedIds.forEach { id ->
            destroyPageWebView(id)
            chapterClipperEnabledTabs.remove(id)
            tabRootDomain.remove(id)
            lastUserGestureNavAt.remove(id)
            lastCleartextWarningUrl.remove(id)
        }
        val keep = tabStore.closeOtherTabs(keepId)
        activeTabId = keep.id
        activateTab(keep)
        notifyShellTabsChanged()
    }

    fun closeAllNativeTabs() {
        val closingIds = tabStore.tabs().map { it.id }
        closingIds.forEach { id ->
            destroyPageWebView(id)
            chapterClipperEnabledTabs.remove(id)
            tabRootDomain.remove(id)
            lastUserGestureNavAt.remove(id)
            lastCleartextWarningUrl.remove(id)
        }
        val blank = tabStore.closeAllTabs()
        activeTabId = blank.id
        activateTab(blank)
        notifyShellTabsChanged()
    }

    fun openPage(tabId: String, url: String) {
        val tab = tabStore.ensureTab(tabId, url = url)
        tabStore.selectTab(tab.id)
        tabStore.updateTab(tab.id, url = url, loading = true)
        activeTabId = tab.id

        // openPage() lÃ  Ä‘iá»u hÆ°á»›ng chá»§ Ä‘á»™ng, vÃ¬ váº­y domain Ä‘Ã­ch trá»Ÿ thÃ nh
        // domain há»£p lá»‡ má»›i cá»§a tháº» trÆ°á»›c khi WebView báº¯t Ä‘áº§u táº£i.
        Uri.parse(url).host?.let { tabRootDomain[tab.id] = it }

        val webView = pageWebViews.getOrPut(tab.id) { createPageWebView(tab.id) }
        bringPageToFront(webView)
        webView.settings.allowFileAccess = url.startsWith("file:", ignoreCase = true)
        webView.loadUrl(url)
        setPageVisible(true)
        trimPageWebViewCache()
        notifyShellTabsChanged()
    }

    fun showHome() {
        hideAdventureLoot()
        hideSpiritBeastEncounter()
        hideDynamicLootEncounter()
        setPageVisible(false)
        if (shellReady) {
            shellWebView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('lqlq-home-shown'));",
                null
            )
        }
    }

    fun reloadActivePage() {
        currentPageWebView()?.reload()
    }

    fun printActivePage() {
        val page = currentPageWebView()
        if (page == null || !pageVisible) {
            Toast.makeText(this, "HÃ£y má»Ÿ má»™t trang web trÆ°á»›c khi in.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val title = (page.title ?: "lqlq Browser").take(80)
            val adapter = page.createPrintDocumentAdapter(title)
            manager.print(
                "lqlq-$title",
                adapter,
                PrintAttributes.Builder().build()
            )
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c trÃ¬nh in Android.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreActiveTabAfterShellLoad() {
        val tab = tabStore.currentTab()
        activeTabId = tab.id
        activateTab(tab)
    }

    private fun activateTab(tab: BrowserTab, forceReload: Boolean = false) {
        activeTabId = tab.id

        if (tab.url.isBlank()) {
            deactivateAllPageWebViews()
            setPageVisible(false)
            notifyShellPage(tab.id, "", tab.title, loading = false)
            return
        }

        val webView = pageWebViews.getOrPut(tab.id) { createPageWebView(tab.id) }
        bringPageToFront(webView)
        val loadedUrl = webView.url.orEmpty()
        if (forceReload || loadedUrl.isBlank() || loadedUrl != tab.url) {
            Uri.parse(tab.url).host?.let { tabRootDomain[tab.id] = it }
            webView.settings.allowFileAccess = tab.url.startsWith("file:", ignoreCase = true)
            webView.loadUrl(tab.url)
        } else {
            notifyShellPage(tab.id, loadedUrl, webView.title ?: tab.title, loading = false)
        }
        setPageVisible(true)
        trimPageWebViewCache()
    }

    private fun deactivateAllPageWebViews() {
        pageWebViews.values.forEach { view ->
            view.visibility = View.GONE
            setPageRendererPriority(view, active = false)
        }
    }

    private fun destroyPageWebView(tabId: String) {
        val webView = pageWebViews.remove(tabId) ?: return
        pageLastUsedAt.remove(tabId)
        try {
            pageContainer.removeView(webView)
            webView.stopLoading()
            // KhÃ´ng load about:blank trÆ°á»›c khi destroy: callback cá»§a trang tráº¯ng
            // cÃ³ thá»ƒ cháº¡y sau Ä‘Ã³ vÃ  ghi Ä‘Ã¨ URL Ä‘Ã£ lÆ°u cá»§a tháº» vá»«a bá»‹ Ä‘áº©y khá»i
            // cache, khiáº¿n láº§n chá»n láº¡i chá»‰ má»Ÿ má»™t trang tráº¯ng.
            webView.removeJavascriptInterface("LqlqAndroid")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {
        }
    }

    private fun trimPageWebViewCache() {
        if (pageWebViews.size <= maxCachedPageWebViews) return
        val active = activeTabId
        val candidates = pageWebViews.keys
            .filter { it != active }
            .sortedBy { pageLastUsedAt[it] ?: 0L }
        val removeCount = pageWebViews.size - maxCachedPageWebViews
        candidates.take(removeCount).forEach { tabId ->
            pageWebViews[tabId]?.let { webView ->
                tabStore.updateTab(
                    tabId,
                    url = webView.url,
                    title = webView.title,
                    loading = false
                )
            }
            destroyPageWebView(tabId)
        }
    }

    private fun notifyShellTabsChanged() {
        if (::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen) {
            nativeTabSwitcher.submitTabs(tabStore.tabs(), tabStore.activeTabId())
        }
        if (!shellReady) return
        val state = tabStore.stateJson()
        shellWebView.evaluateJavascript(
            "window.LqlqGlue && LqlqGlue.onNativeTabsChanged($state);",
            null
        )
    }

    /**
     * Viá»‡c "Láº¥y chÆ°Æ¡ng Ä‘ang má»Ÿ" (v0.23.28): gá»i tá»« ShellBridge khi ngÆ°á»i
     * dÃ¹ng báº¥m nÃºt trong Äá»c TXT â€” cháº¡y báº¥t Ä‘á»“ng bá»™ (khÃ´ng CountDownLatch
     * cháº·n luá»“ng), trÃ¡nh treo/timeout Ã¢m tháº§m cá»§a báº£n @JavascriptInterface
     * Ä‘á»“ng bá»™ cÅ©. Káº¿t quáº£ tráº£ vá» reader.js qua LqlqGlue.onChapterExtracted().
     */
    fun extractChapterForReader() {
        pageBridge.extractCurrentChapterAsync { json ->
            val safeJson = if (json.isBlank()) "null" else JSONObject.quote(json)
            shellWebView.evaluateJavascript(
                "window.LqlqGlue && LqlqGlue.onChapterExtracted($safeJson);",
                null
            )
        }
    }

    // ------------------------------------------------------------------
    // Viá»‡c 4 (v0.23.4): Chapter Clipper â€” content-script tháº­t, tiÃªm trá»±c
    // tiáº¿p vÃ o WebView cá»§a trang web Ä‘ang má»Ÿ (khÃ´ng round-trip qua
    // PageBridge/menu nhÆ° kiáº¿n trÃºc cÅ©, trÃ¡nh Ä‘Æ¡/lag).
    // ------------------------------------------------------------------

    private fun readAssetText(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /**
     * NÃºt báº¥m trong menu: báº­t/táº¯t Chapter Clipper CHO Cáº¢ TAB hiá»‡n táº¡i (khÃ´ng
     * chá»‰ tiÃªm 1 láº§n). Khi báº­t, tab Ä‘Æ°á»£c ghi nhá»› trong chapterClipperEnabledTabs
     * Ä‘á»ƒ onPageFinished() cá»§a WebView tá»± tiÃªm láº¡i á»Ÿ má»i trang káº¿ tiáº¿p.
     */
    fun injectChapterClipper() {
        val tabId = activeTabId
        val webView = currentPageWebView()
        if (tabId == null || webView == null || !pageVisible) {
            Toast.makeText(
                this,
                "HÃ£y má»Ÿ má»™t trang web tháº­t rá»“i báº­t Chapter Clipper.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (chapterClipperEnabledTabs.contains(tabId)) {
            chapterClipperEnabledTabs.remove(tabId)
            // áº¨n panel/nÃºt ná»•i trÃªn trang hiá»‡n táº¡i ngay láº­p tá»©c â€” khÃ´ng cáº§n gá»¡
            // háº³n script khá»i DOM, chá»‰ cáº§n nÃ³ khÃ´ng hiá»‡n ra ná»¯a vÃ  khÃ´ng tá»±
            // tiÃªm láº¡i á»Ÿ trang káº¿ tiáº¿p (Ä‘Ã£ xá»­ lÃ½ á»Ÿ createPageWebView()).
            webView.evaluateJavascript(
                """
                (function () {
                  try {
                    var panel = document.getElementById('cc-panel');
                    if (panel) panel.style.setProperty('display', 'none', 'important');
                  } catch (e) {}
                })();
                """.trimIndent(),
                null
            )
            Toast.makeText(this, "ÄÃ£ táº¯t Chapter Clipper trÃªn trang nÃ y.", Toast.LENGTH_SHORT).show()
        } else {
            chapterClipperEnabledTabs.add(tabId)
            injectChapterClipperInto(webView)
            Toast.makeText(this, "ÄÃ£ báº­t Chapter Clipper trÃªn trang nÃ y.", Toast.LENGTH_SHORT).show()
        }
    }

    fun isChapterClipperEnabled(): Boolean {
        val tabId = activeTabId ?: return false
        return chapterClipperEnabledTabs.contains(tabId)
    }

    /** TiÃªm content-script + CSS cá»§a Chapter Clipper vÃ o 1 WebView cá»¥ thá»ƒ. */
    private fun injectChapterClipperInto(webView: WebView) {
        try {
            val css = readAssetText("www/tools/chapter-clipper-styles.css")
            val js = readAssetText("www/tools/chapter-clipper-content.js")
            val injected = """
(function () {
  try {
    if (!document.getElementById('lqlq-chapter-clipper-style')) {
      var style = document.createElement('style');
      style.id = 'lqlq-chapter-clipper-style';
      style.textContent = ${JSONObject.quote(css)};
      (document.head || document.documentElement).appendChild(style);
    }
  } catch (e) {}
})();
$js
"""
            webView.evaluateJavascript(injected, null)
        } catch (error: Exception) {
            Toast.makeText(this, "KhÃ´ng náº¡p Ä‘Æ°á»£c Chapter Clipper.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Viá»‡c C (v0.23.6): tiÃªm script áº©n quáº£ng cÃ¡o DOM riÃªng biá»‡t, LUÃ”N cháº¡y
     * cho Má»ŒI trang web tháº­t (gá»i tá»« onPageFinished cá»§a createPageWebView()),
     * khÃ´ng phá»¥ thuá»™c Chapter Clipper báº­t/táº¯t theo tab.
     *
     * Viá»‡c (v0.23.20): ngÆ°á»i dÃ¹ng yÃªu cáº§u Má»ŒI hÃ¬nh thá»©c cháº·n quáº£ng cÃ¡o pháº£i
     * KHÃ”NG cÃ³ tÃ¡c dá»¥ng gÃ¬ trÃªn trang tÃ¬m kiáº¿m/AI (Google, ChatGPT, Claude...)
     * â€” khÃ´ng tiÃªm script nÃ y vÃ o cÃ¡c trang Ä‘Ã³, thay vÃ¬ chá»‰ táº¯t 1 pháº§n bÃªn
     * trong JS nhÆ° trÆ°á»›c.
     */
    /**
     * Viá»‡c (v0.23.28): lá»›p áº©n quáº£ng cÃ¡o DOM (MutationObserver, tá»‘n CPU trÃªn
     * trang nhiá»u quáº£ng cÃ¡o Ä‘á»™ng) giá» cÃ³ thá»ƒ táº¯t riÃªng, Máº¶C Äá»ŠNH Táº®T â€” khÃ¡c
     * vá»›i lá»›p cháº·n domain/redirect á»Ÿ shouldOverrideUrlLoading/shouldInterceptRequest
     * (luÃ´n báº­t, khÃ´ng Ä‘á»•i). Äáº·t bá»Ÿi ShellBridge.setAdblockDomEnabled().
     */
    @Volatile
    var adblockDomEnabled: Boolean = false

    private fun injectAdblock(webView: WebView) {
        if (!adblockDomEnabled) return
        if (isSearchOrAiToolHost(webView.url?.let { Uri.parse(it).host })) return
        try {
            val js = readAssetText("www/tools/adblock-content.js")
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
            // Im láº·ng bá» qua â€” Ä‘Ã¢y lÃ  tÃ­nh nÄƒng ná»n, khÃ´ng cáº§n bÃ¡o lá»—i cho ngÆ°á»i dÃ¹ng.
        }
    }

    /**
     * So sÃ¡nh 2 host cÃ³ cÃ¹ng "domain gá»‘c" hay khÃ´ng, bá» qua tiá»n tá»‘ phá»• biáº¿n
     * www./m. Ä‘á»ƒ khÃ´ng cháº·n nháº§m site tá»± chuáº©n hoÃ¡ domain cá»§a chÃ­nh nÃ³
     * (vd example.com -> www.example.com). ÄÆ¡n giáº£n hoÃ¡ báº±ng cÃ¡ch so sÃ¡nh
     * 2 nhÃ£n cuá»‘i cÃ¹ng cá»§a host (registrable domain gáº§n Ä‘Ãºng) thay vÃ¬ phá»¥
     * thuá»™c thÆ° viá»‡n Public Suffix List Ä‘áº§y Ä‘á»§.
     */
    private fun isSameRootDomain(hostA: String, hostB: String): Boolean {
        fun rootOf(host: String): String {
            val stripped = host.removePrefix("www.").removePrefix("m.").lowercase(Locale.ROOT)
            val parts = stripped.split(".")
            if (parts.size < 2) return stripped
            val lastTwo = parts.takeLast(2).joinToString(".")
            // "co.uk"/"com.vn"/"github.io"... khÃ´ng pháº£i domain Ä‘Äƒng kÃ½ tháº­t
            // â€” pháº£i láº¥y thÃªm 1 nhÃ£n ná»¯a (vd "example.co.uk") má»›i Ä‘Ãºng.
            if (MULTI_LABEL_SUFFIXES.contains(lastTwo) && parts.size >= 3) {
                return parts.takeLast(3).joinToString(".")
            }
            return lastTwo
        }
        return rootOf(hostA).equals(rootOf(hostB), ignoreCase = true)
    }

    fun setToolbarHeightCss(cssPx: Float) {
        val density = resources.displayMetrics.density
        toolbarHeightPx = (cssPx * density).toInt().coerceAtLeast(0)
        val params = pageContainer.layoutParams as FrameLayout.LayoutParams
        params.topMargin = toolbarHeightPx
        pageContainer.layoutParams = params
    }

    private fun bringPageToFront(webView: WebView) {
        if (webView.parent == null) {
            pageContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        pageWebViews.forEach { (tabId, view) ->
            val active = view === webView
            view.visibility = if (active) View.VISIBLE else View.GONE
            setPageRendererPriority(view, active)
            if (active) pageLastUsedAt[tabId] = System.currentTimeMillis()
        }
    }

    private fun setPageRendererPriority(webView: WebView, active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            webView.setRendererPriorityPolicy(
                if (active) WebView.RENDERER_PRIORITY_IMPORTANT else WebView.RENDERER_PRIORITY_BOUND,
                false
            )
        } catch (_: Exception) {
        }
    }

    fun setOverlayOpen(open: Boolean) {
        overlayOpen = open
        updatePageVisibility()
    }

    fun applyNativeAppearance(theme: String, accent: String) {
        val dark = theme == "dark"
        val accentColor = when (accent) {
            "ocean" -> Color.rgb(0x25, 0x85, 0xc7)
            "amber" -> Color.rgb(0xd9, 0x87, 0x20)
            "violet" -> Color.rgb(0x7a, 0x5a, 0xc8)
            "rose" -> Color.rgb(0xc4, 0x54, 0x76)
            "graphite" -> Color.rgb(0x56, 0x63, 0x6b)
            else -> Color.rgb(0x18, 0xa6, 0x4a) // emerald (máº·c Ä‘á»‹nh)
        }
        if (::nativeTabSwitcher.isInitialized) {
            nativeTabSwitcher.applyTheme(dark, accentColor)
        }

        // Äá»“ng bá»™ cÃ¡c vÃ¹ng há»‡ thá»‘ng Android vá»›i giao diá»‡n Ä‘ang chá»n, trÃ¡nh
        // thanh tráº¡ng thÃ¡i/Ä‘iá»u hÆ°á»›ng sÃ¡ng chÃ³i khi app á»Ÿ cháº¿ Ä‘á»™ tá»‘i.
        val systemBarColor = if (dark) Color.rgb(0x0d, 0x15, 0x10) else Color.rgb(0xf8, 0xfb, 0xff)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun updatePageVisibility() {
        pageContainer.visibility = if (pageVisible) View.VISIBLE else View.GONE

        if (overlayOpen) {
            root.bringChildToFront(shellWebView)
        } else if (pageVisible) {
            root.bringChildToFront(pageContainer)
        } else {
            root.bringChildToFront(shellWebView)
        }

        // Linh Tháº¡ch/Linh ThÃº/Ká»³ Váº­t (adventureLootLayer) chá»‰ Ä‘Æ°á»£c thÃªm vÃ o
        // root Má»˜T Láº¦N lÃºc khá»Ÿi Ä‘á»™ng â€” má»—i láº§n trang web tháº­t Ä‘Æ°á»£c Ä‘Æ°a lÃªn
        // trÃªn (bringChildToFront(pageContainer) á»Ÿ trÃªn) sáº½ Ä‘Ã¨ luÃ´n lá»›p nÃ y
        // xuá»‘ng dÆ°á»›i, nÃªn icon/tháº» dÃ¹ cÃ³ hiá»‡n (visibility = VISIBLE) váº«n bá»‹
        // trang web che kÃ­n, khÃ´ng bao giá» tháº¥y Ä‘Æ°á»£c. Pháº£i tá»± Ä‘Æ°a lÃªn láº¡i
        // má»—i láº§n, giá»‘ng cÃ¡ch mediaBubble Ä‘Ã£ lÃ m Ä‘Ãºng bÃªn dÆ°á»›i.
        if (::adventureLootLayer.isInitialized) {
            root.bringChildToFront(adventureLootLayer)
        }

        if (::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen) {
            // Bá»™ chuyá»ƒn tháº» lÃ  View native toÃ n mÃ n hÃ¬nh, luÃ´n Ä‘á»©ng trÃªn WebView
            // vÃ  cáº£ bá»t media Ä‘á»ƒ khÃ´ng xáº£y ra lá»—i cháº¡m xuyÃªn/z-index nhÆ° báº£n cÅ©.
            root.bringChildToFront(nativeTabSwitcher)
        } else if (::mediaBubble.isInitialized) {
            root.bringChildToFront(mediaBubble)
        }
        root.invalidate()
    }

    /**
     * NÃºt bá»t nháº¡c ná»•i native (v0.23.17) â€” khÃ¡c vá»›i menu/panel (chiáº¿m toÃ n
     * mÃ n hÃ¬nh báº±ng z-order shellWebView), Ä‘Ã¢y lÃ  1 View nhá» Ä‘á»™c láº­p chá»‰
     * chiáº¿m Ä‘Ãºng gÃ³c mÃ n hÃ¬nh, Ä‘á»ƒ KHÃ”NG cháº·n báº¥m/cuá»™n pháº§n cÃ²n láº¡i cá»§a trang
     * web tháº­t Ä‘ang má»Ÿ. Nhá» váº­y nháº¡c/video ná»n váº«n "ná»•i" Ä‘Æ°á»£c trÃªn Má»ŒI trang,
     * khÃ´ng cáº§n má»Ÿ menu â˜°, mÃ  trang web váº«n tÆ°Æ¡ng tÃ¡c bÃ¬nh thÆ°á»ng xung quanh.
     */
    private lateinit var mediaBubble: android.widget.TextView

    private fun setupMediaBubble() {
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()
        mediaBubble = android.widget.TextView(this).apply {
            text = "â™«"
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#1DA64A"))
            }
            elevation = 8f * density
            visibility = View.GONE
            setOnClickListener {
                shellWebView.evaluateJavascript(
                    "window.ShieldMedia && window.ShieldMedia.open();",
                    null
                )
            }
        }
        val params = FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            marginEnd = (16 * density).toInt()
            bottomMargin = (16 * density).toInt()
        }
        root.addView(mediaBubble, params)
    }

    /** Gá»i tá»« ShellBridge.onMediaState() â€” cÃ¹ng tÃ­n hiá»‡u Ä‘ang dÃ¹ng cho thÃ´ng bÃ¡o. */
    fun setMediaBubbleVisible(visible: Boolean) {
        if (!::mediaBubble.isInitialized) return
        mediaBubble.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible && (!::nativeTabSwitcher.isInitialized || !nativeTabSwitcher.isOpen)) {
            root.bringChildToFront(mediaBubble)
        }
    }

    private fun setPageVisible(visible: Boolean) {
        pageVisible = visible
        updatePageVisibility()
        shellWebView.evaluateJavascript(
            "window.LqlqGlue && LqlqGlue.onNativePageVisibility(${visible});",
            null
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPageWebView(tabId: String): WebView {
        val webView = WebView(this)
        applyCommonSettings(webView.settings)

        // Viá»‡c A (v0.23.6): cháº·n quáº£ng cÃ¡o tá»± má»Ÿ cá»­a sá»•/tab má»›i á»Ÿ táº§ng
        // WebView (máº¡nh hÆ¡n nhiá»u so vá»›i chá»‰ override window.open báº±ng JS,
        // JS override cÃ³ thá»ƒ bá»‹ bypass). App hiá»‡n KHÃ”NG cÃ³ onCreateWindow()
        // xá»­ lÃ½ target=_blank nÃªn táº¯t háº³n 2 cá» nÃ y khÃ´ng phÃ¡ tÃ­nh nÄƒng nÃ o
        // Ä‘ang hoáº¡t Ä‘á»™ng (setSupportMultipleWindows máº·c Ä‘á»‹nh Ä‘Ã£ lÃ  false).
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false

        // Chapter Clipper cáº§n duy nháº¥t API saveTextFile(). Website bÃªn ngoÃ i
        // chá»‰ nháº­n PageToolsBridge tá»‘i thiá»ƒu, khÃ´ng Ä‘Æ°á»£c tháº¥y API quáº£n lÃ½ tháº»
        // vÃ  cÃ¡c quyá»n Ä‘áº·c biá»‡t khÃ¡c cá»§a ShellBridge. Má»—i tab cÃ³ 1 instance
        // riÃªng, chá»‰ cho lÆ°u khi CHÃNH tab Ä‘Ã³ Ä‘Ã£ Ä‘Æ°á»£c ngÆ°á»i dÃ¹ng báº­t Chapter
        // Clipper â€” trÆ°á»›c Ä‘Ã¢y gáº¯n chung 1 instance khÃ´ng kiá»ƒm tra gÃ¬, nÃªn
        // Má»ŒI website Ä‘á»u gá»i Ä‘Æ°á»£c saveTextFile() dÃ¹ chÆ°a báº­t cÃ´ng cá»¥.
        webView.addJavascriptInterface(
            PageToolsBridge(shellBridge) { chapterClipperEnabledTabs.contains(tabId) },
            "LqlqAndroid"
        )

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme != "http" && scheme != "https") {
                    openExternalUri(request.url)
                    return true
                }

                // Viá»‡c (v0.23.11 + v0.23.20): cháº·n TUYá»†T Äá»I domain quáº£ng
                // cÃ¡o/redirect Ä‘Ã£ biáº¿t, khÃ´ng xÃ©t gesture â€” má»™t cÃº cháº¡m tháº­t
                // cÃ³ thá»ƒ bá»‹ lá»›p phá»§ quáº£ng cÃ¡o vÃ´ hÃ¬nh "Ä‘Ã¡nh cáº¯p", nÃªn gesture
                // khÃ´ng pháº£i cÄƒn cá»© Ä‘Ã¡ng tin Ä‘á»ƒ cho qua (xem domainGuardEnabled
                // á»Ÿ trÃªn). TRá»ª KHI Ä‘ang á»Ÿ trang tÃ¬m kiáº¿m/AI (Google cÃ³ thá»ƒ tá»±
                // dÃ¹ng doubleclick.net cho quáº£ng cÃ¡o tÃ¬m kiáº¿m CHÃNH THá»NG cá»§a
                // nÃ³ â€” khÃ´ng nÃªn cháº·n, trÃ¡nh phÃ¡ chá»©c nÄƒng tháº­t).
                if (domainGuardEnabled &&
                    !isSearchOrAiToolHost(tabRootDomain[tabId]) &&
                    isBlockedAdHost(request.url.host)
                ) {
                    val reward = if (request.isForMainFrame) {
                        recordAdventureShieldProtection(tabId, request.url.host, "ad-host")
                    } else null
                    showBlockNoticeToast(
                        withAdventureReward("ÄÃ£ cháº·n quáº£ng cÃ¡o: ${request.url.host}", reward)
                    )
                    return true
                }

                // CÃº cháº¡m tháº­t cá»§a ngÆ°á»i dÃ¹ng (khÃ´ng pháº£i quáº£ng cÃ¡o, Ä‘Ã£ qua
                // Ä‘Æ°á»£c lá»›p cháº·n ad-host á»Ÿ trÃªn) Ä‘Æ°á»£c ghi láº¡i thá»i Ä‘iá»ƒm, Ä‘á»ƒ
                // cho phÃ©p cÃ¡c bÆ°á»›c redirect KHÃ”NG mang gesture (OAuth,
                // link rÃºt gá»n, cá»•ng thanh toÃ¡n...) báº¯t nguá»“n tá»« Ä‘Ãºng cÃº
                // cháº¡m Ä‘Ã³ váº«n Ä‘i qua trong 1 cá»­a sá»• ngáº¯n â€” khÃ´ng Ä‘Ã¡nh Ä‘á»“ng
                // vá»›i Ä‘iá»u hÆ°á»›ng tá»± phÃ¡t cá»§a trang.
                if (request.hasGesture()) {
                    lastUserGestureNavAt[tabId] = System.currentTimeMillis()
                }

                // Cháº·n Ä‘iá»u hÆ°á»›ng main-frame tá»± phÃ¡t sang domain khÃ¡c domain
                // gá»‘c hiá»‡n táº¡i cá»§a tab â€” TRá»ª KHI Ä‘Ã¢y lÃ  cÃº cháº¡m tháº­t cá»§a
                // ngÆ°á»i dÃ¹ng, hoáº·c náº±m trong cá»­a sá»• 20 giÃ¢y ngay sau 1 cÃº
                // cháº¡m tháº­t (redirect chuá»—i OAuth/link rÃºt gá»n/www). TrÆ°á»›c
                // Ä‘Ã¢y cháº·n tuyá»‡t Ä‘á»‘i khÃ´ng xÃ©t gesture, khiáº¿n cáº£ link ngÆ°á»i
                // dÃ¹ng báº¥m tháº­t ra site khÃ¡c cÅ©ng bá»‹ cháº·n nháº§m (Ä‘Äƒng nháº­p
                // Google/Facebook, cá»•ng thanh toÃ¡n, link ngoÃ i Wikipedia...).
                // Chá»‰ Ã¡p dá»¥ng cho khung chÃ­nh; iframe/tÃ i nguyÃªn con khÃ´ng bá»‹
                // lá»›p nÃ y can thiá»‡p. CÃ´ng cá»¥ tÃ¬m kiáº¿m/AI Ä‘Æ°á»£c miá»…n trá»« Ä‘á»ƒ káº¿t
                // quáº£ tÃ¬m kiáº¿m hoáº¡t Ä‘á»™ng bÃ¬nh thÆ°á»ng. Äiá»u hÆ°á»›ng chá»§ Ä‘á»™ng tá»«
                // thanh Ä‘á»‹a chá»‰/trang chá»§ Ä‘i qua openPage() vÃ  cáº­p nháº­t
                // domain trÆ°á»›c.
                val safeHost = tabRootDomain[tabId]
                val targetHost = request.url.host
                val recentGesture = request.hasGesture() ||
                    (System.currentTimeMillis() - (lastUserGestureNavAt[tabId] ?: 0L) < TRUSTED_REDIRECT_WINDOW_MS)
                if (domainGuardEnabled && request.isForMainFrame && !recentGesture &&
                    !isSearchOrAiToolHost(safeHost) && !isSearchOrAiToolHost(targetHost) &&
                    !safeHost.isNullOrEmpty() && !targetHost.isNullOrEmpty() &&
                    !isSameRootDomain(safeHost, targetHost)
                ) {
                    val reward = recordAdventureShieldProtection(tabId, targetHost, "cross-domain")
                    showBlockNoticeToast(
                        withAdventureReward("ÄÃ£ cháº·n chuyá»ƒn hÆ°á»›ng láº¡: $targetHost", reward)
                    )
                    return true
                }
                return false
            }

            // shouldInterceptRequest() cÃ²n kiá»ƒm tra tá»«ng bÆ°á»›c redirect phÃ­a
            // server. Cáº£ 2 lá»›p cháº·n (danh sÃ¡ch host quáº£ng cÃ¡o + root-domain
            // guard) Ä‘á»u táº¯t hoÃ n toÃ n khi domainGuardEnabled = false.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val targetHost = request.url.host

                // v0.23.20: khÃ´ng cháº·n gÃ¬ cáº£ (ká»ƒ cáº£ danh sÃ¡ch domain quáº£ng
                // cÃ¡o Ä‘Ã£ biáº¿t) khi Ä‘ang á»Ÿ trang tÃ¬m kiáº¿m/AI â€” trang Ä‘Ã³ cÃ³
                // thá»ƒ tá»± dÃ¹ng háº¡ táº§ng quáº£ng cÃ¡o cá»§a chÃ­nh nÃ³ cho chá»©c nÄƒng
                // tháº­t (vd káº¿t quáº£ quáº£ng cÃ¡o tÃ¬m kiáº¿m cá»§a Google).
                if (!domainGuardEnabled) return null
                if (isSearchOrAiToolHost(tabRootDomain[tabId])) return null

                val blockedByList = isBlockedAdHost(targetHost)
                var blockedByRootDomainGuard = false

                if (!blockedByList && request.isForMainFrame) {
                    val safeHost = tabRootDomain[tabId]
                    if (!isSearchOrAiToolHost(safeHost) && !isSearchOrAiToolHost(targetHost) &&
                        !safeHost.isNullOrEmpty() && !targetHost.isNullOrEmpty() &&
                        !isSameRootDomain(safeHost, targetHost)
                    ) {
                        blockedByRootDomainGuard = true
                    }
                }

                if (blockedByList || blockedByRootDomainGuard) {
                    if (request.isForMainFrame) {
                        val reason = if (blockedByList) "ad-host" else "cross-domain"
                        val reward = recordAdventureShieldProtection(tabId, targetHost, reason)
                        runOnUiThread {
                            try { view.stopLoading() } catch (_: Exception) {}
                            showBlockNoticeToast(
                                withAdventureReward(
                                    "ÄÃ£ cháº·n quáº£ng cÃ¡o/chuyá»ƒn hÆ°á»›ng láº¡: $targetHost",
                                    reward
                                )
                            )
                        }
                    }
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                if (tabId == activeTabId) {
                    maybeWarnCleartextPage(tabId, url)
                    hideAdventureLoot()
                    hideSpiritBeastEncounter()
                    hideDynamicLootEncounter()
                    notifyShellPage(tabId, url, view.title ?: "", loading = true)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                notifyShellPage(tabId, url, view.title ?: "", loading = false)

                // Viá»‡c (v0.23.12): ghi láº¡i domain gá»‘c "há»£p lá»‡" cá»§a tab nÃ y â€”
                // chá»‰ khi 1 trang THáº¬T Sá»° táº£i xong (khÃ´ng pháº£i bá»‹ cháº·n giá»¯a
                // chá»«ng), lÃ m cÆ¡ sá»Ÿ Ä‘á»ƒ cháº·n tuyá»‡t Ä‘á»‘i Ä‘iá»u hÆ°á»›ng láº¡ sau nÃ y.
                Uri.parse(url).host?.let { tabRootDomain[tabId] = it }

                // Viá»‡c C (v0.23.6): áº©n quáº£ng cÃ¡o DOM LUÃ”N Báº¬T cho Má»ŒI trang,
                // Ä‘á»™c láº­p vá»›i Chapter Clipper (khÃ´ng Ä‘i qua chapterClipperEnabledTabs).
                injectAdblock(view)

                // Viá»‡c 1 (v0.23.5): náº¿u ngÆ°á»i dÃ¹ng Ä‘Ã£ báº­t Chapter Clipper cho
                // tab nÃ y, tá»± tiÃªm láº¡i á»Ÿ Má»ŒI trang má»›i (ká»ƒ cáº£ next-chapter tá»±
                // Ä‘á»™ng) â€” khÃ´ng báº¯t ngÆ°á»i dÃ¹ng báº¥m láº¡i nÃºt má»—i láº§n chuyá»ƒn trang.
                if (chapterClipperEnabledTabs.contains(tabId)) {
                    injectChapterClipperInto(view)
                }

                if (tabId == activeTabId) {
                    maybeScheduleAdventureLoot(tabId, url)
                    val dynamicTriggered = maybeScheduleDynamicLoot(tabId, url, view.title.orEmpty())
                    if (!dynamicTriggered) {
                        maybeScheduleSpiritBeast(tabId, url, view.title.orEmpty())
                    }
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String,
                isReload: Boolean
            ) {
                // QUAN TRá»ŒNG: doUpdateVisitedHistory() cÃ³ thá»ƒ Ä‘Æ°á»£c gá»i nhiá»u láº§n
                // trong má»™t chuá»—i chuyá»ƒn hÆ°á»›ng (redirect), TRÆ¯á»šC KHI trang Ä‘Ã­ch
                // cuá»‘i cÃ¹ng thá»±c sá»± táº£i xong (vÃ­ dá»¥ gÃµ tá»« khÃ³a â†’ táº¡m qua
                // www.google.com/search?q=... rá»“i má»›i Ä‘iá»u hÆ°á»›ng tiáº¿p).
                // Náº¿u bÃ¡o loading=false á»Ÿ Ä‘Ã¢y, Nháº­t kÃ½ sáº½ ghi nháº§m URL trung
                // gian thay vÃ¬ trang Ä‘Ã­ch tháº­t. Chá»‰ cáº­p nháº­t thanh Ä‘á»‹a chá»‰/tiÃªu
                // Ä‘á» táº¡m thá»i (loading=true) â€” má»¥c Nháº­t kÃ½ chá»‰ Ä‘Æ°á»£c ghi tháº­t sá»±
                // táº¡i onPageFinished(), khi URL cuá»‘i cÃ¹ng Ä‘Ã£ táº£i xong.
                notifyShellPage(tabId, url, view.title ?: "", loading = true)
            }

            // Viá»‡c D (v0.23.7): nhiá»u quáº£ng cÃ¡o dÃ¹ng redirect HTTP phÃ­a server
            // (Location header) thay vÃ¬ JS location.href â€” kiá»ƒu nÃ y KHÃ”NG Ä‘i
            // qua shouldOverrideUrlLoading() á»Ÿ trÃªn (WebView tá»± Ã¢m tháº§m theo
            // chuá»—i redirect cá»§a 1 request Ä‘Ã£ Ä‘Æ°á»£c cho phÃ©p), nÃªn lá»t qua
            // "Viá»‡c B" vÃ  trang Ä‘Ã­ch cuá»‘i cÃ¹ng (thÆ°á»ng há»ng/tráº¯ng) váº«n hiá»‡n
            // ra. Báº¯t mÃ£ lá»—i HTTP cá»§a trang CHÃNH á»Ÿ Ä‘Ã¢y vÃ  tá»± quay láº¡i trang
            // trÆ°á»›c Ä‘Ã³ thay vÃ¬ Ä‘á»ƒ mÃ n hÃ¬nh tráº¯ng.
            //
            // Táº®T Máº¶C Äá»ŠNH (badLoadRecoveryEnabled): mÃ£ lá»—i HTTP nhÆ° 503/502
            // thÆ°á»ng chá»‰ lÃ  server Ä‘Ã­ch Ä‘ang quÃ¡ táº£i/báº£o trÃ¬ táº¡m thá»i, khÃ´ng
            // liÃªn quan gÃ¬ tá»›i quáº£ng cÃ¡o â€” tá»± báº¥m Back trong trÆ°á»ng há»£p Ä‘Ã³
            // gÃ¢y khÃ³ chá»‹u hÆ¡n lÃ  há»¯u Ã­ch. KhÃ´ng cÃ²n báº¯t lá»—i káº¿t ná»‘i
            // (onReceivedError) ná»¯a vÃ¬ lá»—i máº¡ng táº¡m thá»i (máº¥t sÃ³ng, DNS cháº­m)
            // cÅ©ng bá»‹ coi nháº§m lÃ  "trang há»ng do quáº£ng cÃ¡o".
            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse
            ) {
                if (badLoadRecoveryEnabled &&
                    request.isForMainFrame &&
                    errorResponse.statusCode >= 400
                ) {
                    runOnUiThread {
                        if (view.canGoBack()) {
                            showBlockNoticeToast(
                                "Trang Ä‘Ã­ch khÃ´ng táº£i Ä‘Æ°á»£c (HTTP ${errorResponse.statusCode}) â€” Ä‘Ã£ quay láº¡i trang trÆ°á»›c."
                            )
                            view.stopLoading()
                            view.goBack()
                        }
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                // Renderer cháº¿t khÃ´ng Ä‘Æ°á»£c xÃ³a tháº». Chá»‰ há»§y WebView lá»—i; URL vÃ 
                // tiÃªu Ä‘á» váº«n náº±m trong TabStore Ä‘á»ƒ tháº» cÃ³ thá»ƒ táº£i láº¡i an toÃ n.
                val tab = tabStore.findTab(tabId)
                pageWebViews.remove(tabId)
                pageLastUsedAt.remove(tabId)
                try {
                    pageContainer.removeView(view)
                    view.destroy()
                } catch (_: Exception) {
                }
                Toast.makeText(
                    this@MainActivity,
                    "Trang web gáº·p sá»± cá»‘. Äang táº£i láº¡i tháº»â€¦",
                    Toast.LENGTH_SHORT
                ).show()
                if (tabId == activeTabId && tab != null) {
                    setPageVisible(false)
                    pageContainer.post { activateTab(tab, forceReload = true) }
                }
                notifyShellTabsChanged()
                return true
            }
        }

        webView.webChromeClient = createChromeClient(tabId)

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        // TrÆ°á»›c Ä‘Ã¢y khÃ´ng xá»­ lÃ½ gÃ¬ cáº£ khi giá»¯ (long-press) lÃªn áº£nh â€” WebView
        // váº«n tá»± rung pháº£n há»“i máº·c Ä‘á»‹nh rá»“i cá»‘ má»Ÿ context menu há»‡ thá»‘ng,
        // nhÆ°ng vÃ¬ app chÆ°a bao giá» registerForContextMenu()/onCreateContextMenu()
        // nÃªn khÃ´ng cÃ³ gÃ¬ hiá»‡n ra, vÃ  tráº¡ng thÃ¡i gesture bÃªn trong WebView bá»‹
        // káº¹t láº¡i, khiáº¿n Má»ŒI cÃº cháº¡m sau Ä‘Ã³ trÃªn tab Ä‘Ã³ khÃ´ng cÃ²n nháº­n Ä‘Æ°á»£c
        // ná»¯a (khÃ´ng pháº£i treo tab, chá»‰ lÃ  khÃ´ng nháº­n thao tÃ¡c). Tá»± báº¯t sá»±
        // kiá»‡n long-press trÃªn áº£nh vÃ  luÃ´n return true Ä‘á»ƒ tiÃªu thá»¥ sá»± kiá»‡n,
        // Ä‘á»“ng thá»i hiá»‡n menu "Táº£i áº£nh / Má»Ÿ tab má»›i / Sao chÃ©p liÃªn káº¿t"
        // giá»‘ng Chrome.
        webView.setOnLongClickListener { view ->
            val hitTestResult = (view as WebView).hitTestResult
            val type = hitTestResult.type
            if (type == WebView.HitTestResult.IMAGE_TYPE ||
                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                val handler = Handler(Looper.getMainLooper()) { message ->
                    val imageUrl = message.data.getString("url") ?: hitTestResult.extra
                    if (!imageUrl.isNullOrBlank()) showImageLongPressMenu(imageUrl)
                    true
                }
                view.requestImageRef(handler.obtainMessage())
                true
            } else {
                false
            }
        }

        return webView
    }

    private fun showImageLongPressMenu(imageUrl: String) {
        val options = arrayOf(
            "Má»Ÿ áº£nh trong tháº» má»›i",
            "Xem trÆ°á»›c hÃ¬nh áº£nh",
            "Sao chÃ©p áº£nh",
            "Táº£i hÃ¬nh áº£nh xuá»‘ng",
            "TÃ¬m hÃ¬nh áº£nh báº±ng Google á»ng KÃ­nh",
            "Chia sáº» hÃ¬nh áº£nh"
        )
        AlertDialog.Builder(this)
            .setTitle(imageUrl.take(80))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createNativeTab(imageUrl, tabStore.currentMode)
                    1 -> previewImage(imageUrl)
                    2 -> copyImageToClipboard(imageUrl)
                    3 -> startDownload(imageUrl, "", "", "image/*")
                    4 -> {
                        val lensUrl = "https://lens.google.com/uploadbyurl?url=" + Uri.encode(imageUrl)
                        createNativeTab(lensUrl, tabStore.currentMode)
                    }
                    5 -> shareImage(imageUrl)
                }
            }
            .show()
    }

    /** Táº£i áº£nh vá» 1 File táº¡m trong cache (dÃ¹ng chung cho xem trÆ°á»›c/sao chÃ©p/chia sáº» áº£nh). */
    private fun fetchImageToCacheFile(imageUrl: String, onReady: (File?) -> Unit) {
        dynamicLootExecutor.execute {
            val file = try {
                val uri = Uri.parse(imageUrl)
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                if (scheme != "http" && scheme != "https") {
                    mainHandler.post { onReady(null) }
                    return@execute
                }
                val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "lqlq-browser-android/0.32 (image-menu)")
                    setRequestProperty("Cookie", CookieManager.getInstance().getCookie(imageUrl) ?: "")
                }
                connection.connect()
                val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                val declaredLength = connection.contentLengthLong
                if (connection.responseCode !in 200..299 ||
                    contentType.isNullOrBlank() ||
                    !contentType.startsWith("image/") ||
                    declaredLength > MAX_SHARED_IMAGE_BYTES
                ) {
                    connection.disconnect()
                    null
                } else {
                    val dir = File(cacheDir, "shared_images").apply {
                        mkdirs()
                        trimSharedImageCache(this)
                    }
                    val extension = URLUtil.guessFileName(imageUrl, null, connection.contentType).substringAfterLast('.', "jpg")
                    val target = File(dir, "img_${System.currentTimeMillis()}.$extension")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            copyStreamWithLimit(input, output, MAX_SHARED_IMAGE_BYTES)
                        }
                    }
                    connection.disconnect()
                    trimSharedImageCache(dir)
                    target
                }
            } catch (_: Exception) {
                null
            }
            mainHandler.post { onReady(file) }
        }
    }

    private fun previewImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this).setView(ProgressBar(this).apply {
            val pad = dp(24)
            setPadding(pad, pad, pad, pad)
        }).setCancelable(true).show()
        fetchImageToCacheFile(imageUrl) { file ->
            dialog.dismiss()
            if (file == null) {
                Toast.makeText(this, "KhÃ´ng táº£i Ä‘Æ°á»£c áº£nh Ä‘á»ƒ xem trÆ°á»›c.", Toast.LENGTH_SHORT).show()
                return@fetchImageToCacheFile
            }
            val bitmap = try {
                decodePreviewBitmap(file)
            } catch (_: Exception) {
                null
            }
            if (bitmap == null) {
                Toast.makeText(this, "KhÃ´ng xem trÆ°á»›c Ä‘Æ°á»£c áº£nh nÃ y.", Toast.LENGTH_SHORT).show()
                return@fetchImageToCacheFile
            }
            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            AlertDialog.Builder(this)
                .setView(imageView)
                .setPositiveButton("ÄÃ³ng", null)
                .show()
        }
    }

    private fun copyStreamWithLimit(
        input: InputStream,
        output: java.io.OutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalStateException("image too large")
            }
            output.write(buffer, 0, read)
        }
    }

    private fun trimSharedImageCache(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalBytes = files.sumOf { it.length() }
        var totalFiles = files.size
        for (file in files) {
            if (totalBytes <= MAX_SHARED_IMAGE_CACHE_BYTES && totalFiles <= MAX_SHARED_IMAGE_CACHE_FILES) {
                break
            }
            totalBytes -= file.length()
            totalFiles -= 1
            runCatching { file.delete() }
        }
    }

    private fun decodePreviewBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val metrics = resources.displayMetrics
        val reqWidth = (metrics.widthPixels.coerceAtLeast(1) * 2).coerceAtLeast(1080)
        val reqHeight = (metrics.heightPixels.coerceAtLeast(1) * 2).coerceAtLeast(1920)
        val sampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
        )
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun imageContentUri(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun copyImageToClipboard(imageUrl: String) {
        Toast.makeText(this, "Äang sao chÃ©p áº£nhâ€¦", Toast.LENGTH_SHORT).show()
        fetchImageToCacheFile(imageUrl) { file ->
            if (file == null) {
                Toast.makeText(this, "KhÃ´ng sao chÃ©p Ä‘Æ°á»£c áº£nh nÃ y.", Toast.LENGTH_SHORT).show()
                return@fetchImageToCacheFile
            }
            val uri = imageContentUri(file)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "image", uri))
            Toast.makeText(this, "ÄÃ£ sao chÃ©p áº£nh.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(imageUrl: String) {
        Toast.makeText(this, "Äang chuáº©n bá»‹ chia sáº»â€¦", Toast.LENGTH_SHORT).show()
        fetchImageToCacheFile(imageUrl) { file ->
            if (file == null) {
                Toast.makeText(this, "KhÃ´ng chia sáº» Ä‘Æ°á»£c áº£nh nÃ y.", Toast.LENGTH_SHORT).show()
                return@fetchImageToCacheFile
            }
            val uri = imageContentUri(file)
            val mime = contentResolver.getType(uri) ?: "image/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Chia sáº» hÃ¬nh áº£nh"))
        }
    }

    private fun shareAutomationPublish(request: AutomationShareSheetRequest) {
        runOnUiThread {
            runCatching {
                val uri = Uri.parse(request.contentUri)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = request.mimeType.ifBlank { "video/mp4" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, request.text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, request.chooserTitle))
            }.onFailure {
                Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c share sheet cho video automation.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun notifyShellPage(tabId: String, url: String, title: String, loading: Boolean) {
        tabStore.updateTab(
            tabId,
            url = url,
            title = title.takeIf { it.isNotBlank() },
            loading = loading,
            persist = !loading
        )
        if (::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen) {
            nativeTabSwitcher.submitTabs(tabStore.tabs(), tabStore.activeTabId())
        }
        if (!shellReady) return

        val payload = JSONObject().apply {
            put("tabId", tabId)
            put("url", url)
            put("title", title)
            put("loading", loading)
            if (!loading) put("faviconData", faviconStore.peekDataUrl(url))
        }
        shellWebView.evaluateJavascript(
            "window.LqlqGlue && LqlqGlue.onNativePage($payload);",
            null
        )
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "Tá»‡p nÃ y cáº§n lÆ°u báº±ng nÃºt lÆ°u trong á»©ng dá»¥ng.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCleartextHttpUrl(url)) {
            AlertDialog.Builder(this)
                .setTitle("Táº£i xuá»‘ng qua HTTP?")
                .setMessage("LiÃªn káº¿t nÃ y khÃ´ng Ä‘Æ°á»£c mÃ£ hÃ³a. Ná»™i dung táº£i xuá»‘ng cÃ³ thá»ƒ bá»‹ Ä‘á»c hoáº·c sá»­a trÃªn Ä‘Æ°á»ng truyá»n.")
                .setNegativeButton("Há»§y", null)
                .setPositiveButton("Váº«n táº£i") { _, _ ->
                    enqueueDownload(url, userAgent, contentDisposition, mimeType)
                }
                .show()
            return
        }
        enqueueDownload(url, userAgent, contentDisposition, mimeType)
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                setTitle(fileName)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Äang táº£i xuá»‘ng: $fileName", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng táº£i xuá»‘ng Ä‘Æ°á»£c tá»‡p nÃ y.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Website cÃ³ thá»ƒ tá»± Ä‘iá»u hÆ°á»›ng sang scheme khÃ¡c http(s) (vd fb://,
     * intent://...) Ä‘á»ƒ "nháº£y" tháº³ng sang app ngoÃ i mÃ  ngÆ°á»i dÃ¹ng KHÃ”NG há»
     * báº¥m vÃ o link Ä‘Ã³ â€” trÆ°á»›c Ä‘Ã¢y hÃ m nÃ y má»Ÿ app ngay láº­p tá»©c, khÃ´ng há»i,
     * khiáº¿n trÃ¬nh duyá»‡t tá»± Ä‘á»™ng báº­t Facebook/app khÃ¡c. Giá» luÃ´n há»i xÃ¡c
     * nháº­n trÆ°á»›c, khÃ´ng bao giá» tá»± Ã½ chuyá»ƒn app.
     */
    private fun openExternalUri(uri: Uri) {
        val intent = try {
            Intent(Intent.ACTION_VIEW, uri)
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng cÃ³ á»©ng dá»¥ng nÃ o má»Ÿ Ä‘Æ°á»£c liÃªn káº¿t nÃ y.", Toast.LENGTH_SHORT).show()
            return
        }
        val appLabel = try {
            val resolved = packageManager.resolveActivity(intent, 0)
            resolved?.loadLabel(packageManager)?.toString()
        } catch (_: Exception) {
            null
        }
        val message = if (appLabel.isNullOrBlank()) {
            "Trang web muá»‘n má»Ÿ má»™t á»©ng dá»¥ng khÃ¡c. Báº¡n cÃ³ muá»‘n chuyá»ƒn qua khÃ´ng?"
        } else {
            "Trang web muá»‘n má»Ÿ á»©ng dá»¥ng \"$appLabel\". Báº¡n cÃ³ muá»‘n chuyá»ƒn qua khÃ´ng?"
        }
        AlertDialog.Builder(this)
            .setTitle("Chuyá»ƒn sang á»©ng dá»¥ng khÃ¡c?")
            .setMessage(message)
            .setNegativeButton("á»ž láº¡i trang web", null)
            .setPositiveButton("Má»Ÿ á»©ng dá»¥ng") { _, _ ->
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "KhÃ´ng cÃ³ á»©ng dá»¥ng nÃ o má»Ÿ Ä‘Æ°á»£c liÃªn káº¿t nÃ y.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Trang ngoáº¡i tuyáº¿n: má»Ÿ tá»‡p HTML Ä‘Ã£ lÆ°u + lÆ°u trang web tháº­t
    // ------------------------------------------------------------------

    fun launchOpenHtmlFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/html",
                    "application/xhtml+xml",
                    "text/plain",
                    "application/octet-stream",
                    // Tá»‡p .mht/.mhtml lÆ°u báº±ng saveWebArchive() (giá»¯ áº£nh/CSS).
                    "application/x-mimearchive",
                    "message/rfc822",
                    "multipart/related"
                )
            )
        }
        try {
            htmlFileLauncher.launch(Intent.createChooser(intent, "Chá»n tá»‡p HTML/MHT Ä‘Ã£ lÆ°u"))
        } catch (_: Exception) {
            Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c há»™p chá»n tá»‡p.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    /** Má»Ÿ báº£n lÆ°u ngoáº¡i tuyáº¿n; náº¿u tá»‡p Ä‘Ã£ bá»‹ xÃ³a thÃ¬ quay láº¡i URL online. */
    fun openOfflineUriFromShell(uriString: String, fallbackUrl: String) {
        try {
            loadOfflineHtml(Uri.parse(uriString), fallbackUrl)
        } catch (_: Exception) {
            fallbackToOnlinePage(fallbackUrl)
        }
    }

    private fun fallbackToOnlinePage(fallbackUrl: String) {
        if (fallbackUrl.startsWith("http://", true) || fallbackUrl.startsWith("https://", true)) {
            val tabId = activeTabId ?: tabStore.activeTabId()
            openPage(tabId, fallbackUrl)
            Toast.makeText(this, "Báº£n ngoáº¡i tuyáº¿n khÃ´ng cÃ²n. ÄÃ£ má»Ÿ trang online.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c tá»‡p Ä‘Ã£ lÆ°u.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Äá»c/copy MHT á»Ÿ luá»“ng ná»n. Báº£n MHT Ä‘Ã£ má»Ÿ Ä‘Æ°á»£c cache theo Uri Ä‘á»ƒ láº§n má»Ÿ
     * sau gáº§n nhÆ° tá»©c thÃ¬; cache cÅ© Ä‘Æ°á»£c dá»n theo tuá»•i vÃ  giá»›i háº¡n sá»‘ lÆ°á»£ng.
     */
    private fun loadOfflineHtml(uri: Uri, fallbackUrl: String = "") {
        Thread {
            try {
                val mimeType = contentResolver.getType(uri) ?: ""
                val name = queryDisplayName(uri) ?: ""
                val isMht = mimeType.contains("mimearchive", true) ||
                    mimeType.contains("rfc822", true) ||
                    mimeType.contains("multipart/related", true) ||
                    name.endsWith(".mht", true) ||
                    name.endsWith(".mhtml", true)

                if (isMht) {
                    val openCache = File(cacheDir, "offline_open_v1").apply { mkdirs() }
                    trimOfflineOpenCache(openCache)
                    val cacheName = "${uri.toString().hashCode().toUInt().toString(16)}.mht"
                    val tempFile = File(openCache, cacheName)
                    if (!tempFile.isFile || tempFile.length() <= 0L) {
                        val partial = File(openCache, "$cacheName.part")
                        contentResolver.openInputStream(uri)?.use { input ->
                            partial.outputStream().buffered().use { output ->
                                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                            }
                        } ?: throw IllegalStateException("empty")
                        if (!partial.renameTo(tempFile)) {
                            partial.copyTo(tempFile, overwrite = true)
                            partial.delete()
                        }
                    }
                    tempFile.setLastModified(System.currentTimeMillis())
                    runOnUiThread { openOfflineMhtFile(tempFile) }
                } else {
                    val html = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("empty")
                    if (html.isBlank()) throw IllegalStateException("blank")
                    runOnUiThread { openOfflineHtmlString(html) }
                }
            } catch (_: Exception) {
                runOnUiThread { fallbackToOnlinePage(fallbackUrl) }
            }
        }.start()
    }

    private fun trimOfflineOpenCache(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        files.drop(8).forEach { try { it.delete() } catch (_: Exception) {} }
        directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach {
            if (System.currentTimeMillis() - it.lastModified() > 60_000L) {
                try { it.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun openOfflineMhtFile(tempFile: File) {
        val offlineUrl = "file://${tempFile.absolutePath}"
        val tab = tabStore.createTab(url = offlineUrl, title = "Trang ngoáº¡i tuyáº¿n")
        activeTabId = tab.id
        val webView = pageWebViews.getOrPut(tab.id) { createPageWebView(tab.id) }
        bringPageToFront(webView)
        webView.settings.allowFileAccess = true
        webView.loadUrl(offlineUrl)
        setPageVisible(true)
        trimPageWebViewCache()
        notifyShellTabsChanged()
        Toast.makeText(this, "ÄÃ£ má»Ÿ trang ngoáº¡i tuyáº¿n.", Toast.LENGTH_SHORT).show()
    }

    private fun openOfflineHtmlString(html: String) {
        val baseUrl = "https://offline.lqlq.local/"
        val tab = tabStore.createTab(url = baseUrl, title = "Trang ngoáº¡i tuyáº¿n")
        activeTabId = tab.id
        val webView = pageWebViews.getOrPut(tab.id) { createPageWebView(tab.id) }
        bringPageToFront(webView)
        webView.loadDataWithBaseURL(
            baseUrl,
            html,
            "text/html",
            "utf-8",
            null
        )
        setPageVisible(true)
        trimPageWebViewCache()
        notifyShellTabsChanged()
        Toast.makeText(this, "ÄÃ£ má»Ÿ trang ngoáº¡i tuyáº¿n.", Toast.LENGTH_SHORT).show()
    }

    fun saveActivePageOffline() {
        if (offlineSaveInProgress) {
            Toast.makeText(this, "Äang lÆ°u trang trÆ°á»›c Ä‘Ã³â€¦", Toast.LENGTH_SHORT).show()
            return
        }

        val page = currentPageWebView()
        if (page == null || !pageVisible) {
            Toast.makeText(this, "ChÆ°a cÃ³ trang web nÃ o Ä‘ang má»Ÿ.", Toast.LENGTH_SHORT).show()
            return
        }

        // Chá»¥p metadata trÆ°á»›c khi saveWebArchive cháº¡y báº¥t Ä‘á»“ng bá»™; ngÆ°á»i dÃ¹ng cÃ³
        // thá»ƒ chuyá»ƒn trang/tab trong lÃºc file Ä‘ang Ä‘Æ°á»£c táº¡o.
        val sourceUrl = page.url.orEmpty()
        val sourceTitle = (page.title ?: tabStore.currentTab().title)
            .take(80)
            .ifBlank { "trang-web" }
        val safeTitle = sourceTitle.replace(Regex("[\\/:*?\"<>|]"), "_")
            .trim().ifBlank { "trang-web" }

        offlineSaveInProgress = true
        Toast.makeText(this, "Äang lÆ°u trang ngoáº¡i tuyáº¿nâ€¦", Toast.LENGTH_SHORT).show()

        val cacheMhtDir = File(cacheDir, "offline_mht").apply { mkdirs() }
        cleanupOfflineTempFiles(cacheMhtDir)
        val tempPath = File(cacheMhtDir, "page_${System.currentTimeMillis()}.mht").absolutePath

        page.saveWebArchive(tempPath, false) { savedPath ->
            if (savedPath.isNullOrBlank()) {
                offlineSaveInProgress = false
                Toast.makeText(this, "KhÃ´ng lÆ°u Ä‘Æ°á»£c ná»™i dung trang.", Toast.LENGTH_SHORT).show()
                return@saveWebArchive
            }

            Thread {
                var savedUri: String? = null
                try {
                    val archive = File(savedPath)
                    if (!archive.isFile || archive.length() <= 0L) {
                        throw IllegalStateException("empty archive")
                    }
                    FileInputStream(archive).use { input ->
                        savedUri = saveStreamToDownloads(
                            "${safeTitle}_offline_${System.currentTimeMillis()}.mht",
                            "application/x-mimearchive",
                            input
                        )
                    }
                } catch (_: Exception) {
                    savedUri = null
                } finally {
                    try { File(savedPath).delete() } catch (_: Exception) {}
                }

                runOnUiThread {
                    offlineSaveInProgress = false
                    if (savedUri == null) {
                        Toast.makeText(this, "KhÃ´ng ghi Ä‘Æ°á»£c tá»‡p trang ngoáº¡i tuyáº¿n.", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    Toast.makeText(this, "ÄÃ£ lÆ°u trang vÃ o Download.", Toast.LENGTH_SHORT).show()
                    val payload = JSONObject().apply {
                        put("url", sourceUrl)
                        put("title", sourceTitle)
                        put("offlineUri", savedUri)
                        put("faviconData", faviconStore.peekDataUrl(sourceUrl))
                    }
                    shellWebView.evaluateJavascript(
                        "window.LqlqGlue && LqlqGlue.onOfflinePageSaved($payload);",
                        null
                    )
                }
            }.start()
        }
    }

    /** Ghi stream trá»±c tiáº¿p ra Download, khÃ´ng Ä‘á»c toÃ n bá»™ MHT vÃ o RAM. */
    private fun saveStreamToDownloads(fileName: String, mimeType: String, input: InputStream): String? {
        val safeName = fileName.replace(Regex("[\\/:*?\"<>|]"), "_")
            .ifBlank { "lqlq-page.mht" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                try {
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    } ?: throw IllegalStateException("no output stream")
                    val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    contentResolver.update(uri, ready, null, null)
                    uri.toString()
                } catch (error: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .apply { mkdirs() }
                val file = File(dir, safeName)
                file.outputStream().buffered().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                Uri.fromFile(file).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupOfflineTempFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    // ------------------------------------------------------------------
    // WebChromeClient chung: chá»n tá»‡p + video toÃ n mÃ n hÃ¬nh
    // ------------------------------------------------------------------

    private fun createChromeClient(pageTabId: String? = null) = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
            val tabId = pageTabId ?: return
            val url = view.url.orEmpty()
            if (url.isNotBlank()) {
                // Chá»‰ cáº­p nháº­t metadata; lá»‹ch sá»­ váº«n chá»‰ ghi á»Ÿ onPageFinished.
                notifyShellPage(tabId, url, title, loading = true)
            }
        }

        override fun onReceivedIcon(view: WebView, icon: android.graphics.Bitmap) {
            if (pageTabId == null) return
            faviconStore.put(view.url, icon)
        }
        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback

            // Tá»± dá»±ng intent thay vÃ¬ fileChooserParams.createIntent() vÃ¬ hÃ m Ä‘Ã³
            // xá»­ lÃ½ sai accept dáº¡ng ".txt" hoáº·c "audio/*,video/*" trÃªn nhiá»u mÃ¡y
            // â†’ há»™p chá»n tá»‡p khÃ´ng má»Ÿ hoáº·c khÃ´ng chá»n Ä‘Æ°á»£c gÃ¬.
            val accepts = (fileChooserParams.acceptTypes ?: emptyArray())
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val mimes = accepts.mapNotNull { accept ->
                when {
                    accept.contains("/") -> accept
                    accept.startsWith(".") -> MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(accept.removePrefix(".").lowercase())
                        ?: if (accept.equals(".txt", true)) "text/plain" else null
                    else -> null
                }
            }.distinct()

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimes.size == 1) mimes[0] else "*/*"
                if (mimes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                }
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            return try {
                fileChooserLauncher.launch(Intent.createChooser(intent, "Chá»n tá»‡p"))
                true
            } catch (_: Exception) {
                filePathCallback = null
                false
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.setBackgroundColor(Color.BLACK)
            enterImmersiveFullscreen()
            addFullscreenControls()
        }

        override fun onHideCustomView() {
            exitCustomView()
        }

        // Viá»‡c 4 (v0.23.4): Chapter Clipper (content-script tiÃªm vÃ o trang
        // tháº­t) dÃ¹ng alert()/confirm() thÆ°á»ng â€” WebView máº·c Ä‘á»‹nh khÃ´ng hiá»‡n
        // há»™p thoáº¡i náº¿u khÃ´ng override 2 hÃ m nÃ y (onJsConfirm máº·c Ä‘á»‹nh tráº£
        // false ngay), nÃªn pháº£i tá»± váº½ AlertDialog native á»Ÿ Ä‘Ã¢y.
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(true)
                    .show()
            } catch (_: Exception) {
                result.confirm()
            }
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Há»§y") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(true)
                    .show()
            } catch (_: Exception) {
                result.cancel()
            }
            return true
        }
    }

    private fun exitCustomView() {
        removeFullscreenControls()
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        exitImmersiveFullscreen()
    }

    // ------------------------------------------------------------------
    // Video toÃ n mÃ n hÃ¬nh (báº¥t ká»³ website nÃ o): áº©n tháº­t sá»± thanh tráº¡ng
    // thÃ¡i/Ä‘iá»u hÆ°á»›ng Android, khÃ´ng chá»‰ láº¥p Ä‘áº§y layout â€” trÆ°á»›c Ä‘Ã¢y video
    // "toÃ n mÃ n hÃ¬nh" váº«n bá»‹ 2 thanh há»‡ thá»‘ng che vÃ¬ khÃ´ng cÃ³ pháº§n nÃ y.
    // ------------------------------------------------------------------

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitImmersiveFullscreen() {
        applyBrowserSystemBars()
    }

    private fun fullscreenDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * VÃ¹ng gÃ³c trÃªn-pháº£i: luÃ´n tá»“n táº¡i suá»‘t phiÃªn fullscreen (khÃ´ng gá»¡ ra),
     * chá»‰ áº©n/hiá»‡n 2 nÃºt xoay + khoÃ¡ bÃªn trong. Nhá» váº­y cháº¡m vÃ o Ä‘Ãºng vÃ¹ng
     * nÃ y khi nÃºt Ä‘ang áº©n sáº½ tá»± hiá»‡n láº¡i â€” khÃ´ng cáº§n má»™t lá»›p phá»§ toÃ n mÃ n
     * hÃ¬nh nÃ o khÃ¡c, nÃªn khÃ´ng Ä‘á»¥ng tá»›i thao tÃ¡c cháº¡m/tua cá»§a video bÃªn
     * dÆ°á»›i (chá»‰ Ä‘Ãºng gÃ³c nhá» 130x70dp má»›i báº¯t cháº¡m).
     */
    private fun addFullscreenControls() {
        fullscreenLocked = false

        val rotateBtn = TextView(this).apply {
            text = "âŸ³"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setOnClickListener {
                requestedOrientation = if (
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                ) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                scheduleFullscreenControlsAutoHide()
            }
        }

        val lockBtn = TextView(this).apply {
            text = "ðŸ”’"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setOnClickListener { setFullscreenLocked(!fullscreenLocked) }
        }

        fullscreenRotateBtn = rotateBtn
        fullscreenLockBtn = lockBtn

        val cornerZone = FrameLayout(this).apply {
            isClickable = true
            setOnClickListener { showFullscreenControls() }
            addView(
                rotateBtn,
                FrameLayout.LayoutParams(fullscreenDp(44), fullscreenDp(44)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = fullscreenDp(16)
                    rightMargin = fullscreenDp(68)
                }
            )
            addView(
                lockBtn,
                FrameLayout.LayoutParams(fullscreenDp(44), fullscreenDp(44)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = fullscreenDp(16)
                    rightMargin = fullscreenDp(16)
                }
            )
        }

        fullscreenControlsOverlay = cornerZone
        root.addView(
            cornerZone,
            FrameLayout.LayoutParams(fullscreenDp(130), fullscreenDp(70)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        )

        showFullscreenControls()
    }

    private fun removeFullscreenControls() {
        fullscreenHideRunnable?.let { fullscreenHandler.removeCallbacks(it) }
        fullscreenHideRunnable = null
        setFullscreenLocked(false)
        fullscreenControlsOverlay?.let { root.removeView(it) }
        fullscreenControlsOverlay = null
        fullscreenRotateBtn = null
        fullscreenLockBtn = null
    }

    /** Hiá»‡n nÃºt xoay/khoÃ¡ rá»“i tá»± áº©n sau vÃ i giÃ¢y, giá»‘ng má»i trÃ¬nh phÃ¡t video. */
    private fun showFullscreenControls() {
        fullscreenLockBtn?.visibility = View.VISIBLE
        fullscreenRotateBtn?.visibility = if (fullscreenLocked) View.GONE else View.VISIBLE
        scheduleFullscreenControlsAutoHide()
    }

    private fun scheduleFullscreenControlsAutoHide() {
        fullscreenHideRunnable?.let { fullscreenHandler.removeCallbacks(it) }
        // Khi Ä‘Ã£ khoÃ¡ mÃ n hÃ¬nh, giá»¯ nÃºt khoÃ¡ hiá»‡n luÃ´n Ä‘á»ƒ cÃ²n chá»— báº¥m má»Ÿ láº¡i.
        if (fullscreenLocked) return
        val runnable = Runnable {
            fullscreenRotateBtn?.visibility = View.GONE
            fullscreenLockBtn?.visibility = View.GONE
        }
        fullscreenHideRunnable = runnable
        fullscreenHandler.postDelayed(runnable, 2500)
    }

    /**
     * KhoÃ¡ mÃ n hÃ¬nh (v0.28.1): chá»‰ 1 cháº¡m Ä‘á»ƒ khoÃ¡, cháº¡m láº¡i Ä‘Ãºng nÃºt Ä‘Ã³ Ä‘á»ƒ
     * má»Ÿ â€” bá» háº³n kiá»ƒu "giá»¯ Ä‘á»ƒ má»Ÿ khoÃ¡" rÆ°á»m rÃ  trÆ°á»›c Ä‘Ã¢y. Khi khoÃ¡, má»™t lá»›p
     * phá»§ trong suá»‘t cháº·n toÃ n bá»™ thao tÃ¡c lÃªn video; nÃºt khoÃ¡ váº«n ná»•i trÃªn
     * lá»›p Ä‘Ã³ nÃªn luÃ´n báº¥m má»Ÿ láº¡i Ä‘Æ°á»£c.
     */
    private fun setFullscreenLocked(locked: Boolean) {
        fullscreenLocked = locked
        fullscreenLockOverlay?.let { root.removeView(it) }
        fullscreenLockOverlay = null
        fullscreenRotateBtn?.visibility = if (locked) View.GONE else View.VISIBLE

        if (!locked) {
            scheduleFullscreenControlsAutoHide()
            return
        }

        fullscreenHideRunnable?.let { fullscreenHandler.removeCallbacks(it) }
        val blocker = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
        }
        fullscreenLockOverlay = blocker
        // ThÃªm NGAY SAU customView nhÆ°ng TRÆ¯á»šC vÃ¹ng nÃºt gÃ³c â€” vÃ¹ng nÃºt váº«n á»Ÿ
        // trÃªn cÃ¹ng nÃªn nÃºt khoÃ¡ luÃ´n báº¥m Ä‘Æ°á»£c Ä‘á»ƒ má»Ÿ láº¡i.
        val index = fullscreenControlsOverlay?.let { root.indexOfChild(it) } ?: -1
        if (index >= 0) {
            root.addView(
                blocker,
                index,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // NÃºt back: thoÃ¡t video â†’ lÃ¹i trang â†’ vá» trang chá»§ â†’ ná»n
    // ------------------------------------------------------------------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val page = currentPageWebView()
                when {
                    customView != null -> exitCustomView()
                    ::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen ->
                        nativeTabSwitcher.hide()
                    // Menu / panel cá»§a giao diá»‡n Ä‘ang má»Ÿ â†’ Ä‘Ã³ng nÃ³ trÆ°á»›c
                    overlayOpen -> shellWebView.evaluateJavascript(
                        "window.LqlqGlue && LqlqGlue.closeTopOverlay();",
                        null
                    )
                    pageVisible && page?.canGoBack() == true -> page.goBack()
                    pageVisible -> showHome()
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // VÃ²ng Ä‘á»i: KHÃ”NG táº¡m dá»«ng WebView khi ra ná»n Ä‘á»ƒ nháº¡c/TTS tiáº¿p tá»¥c
    // ------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        connectNativeMediaController()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (suppressNextYoutubePip) {
            suppressNextYoutubePip = false
            return
        }
        if (!youtubePipActive || !youtubePipPlaying) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Chuáº©n bá»‹ bá» máº·t video trÆ°á»›c rá»“i chá»§ Ä‘á»™ng vÃ o PiP. autoEnterEnabled
        // trÃªn Android 12+ váº«n Ä‘Æ°á»£c giá»¯ lÃ m lÆ°á»›i an toÃ n cho thao tÃ¡c vuá»‘t Home.
        prepareYoutubePipSurface(enterAfterPrepare = true)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        notifyShellPipMode(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            if (::mediaBubble.isInitialized) {
                mediaBubble.visibility = if (youtubePipActive) View.VISIBLE else mediaBubble.visibility
            }
            updateYoutubePictureInPictureParams()
        }
    }

    override fun onStop() {
        releaseNativeMediaController()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        // Cá»‘ Ã½ khÃ´ng gá»i webView.onPause()/pauseTimers() Ä‘á»ƒ media cháº¡y ná»n.
        shellWebView.resumeTimers()
    }

    override fun onResume() {
        super.onResume()
        shellWebView.resumeTimers()
        if (!isInPictureInPictureMode) {
            applyBrowserSystemBars()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            notifyShellPipMode(false)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (customView == null && !isInPictureInPictureMode) {
            applyBrowserSystemBars()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val active = activeTabId
            pageWebViews.keys.filter { it != active }.toList().forEach { destroyPageWebView(it) }
        }
        faviconStore.trimAsync()
    }

    override fun onDestroy() {
        dynamicLootFetchTask?.cancel(true)
        dynamicLootExecutor.shutdownNow()
        releaseNativeMediaController()
        BridgeHub.jsRunner = null
        ttsBridge?.shutdown()
        if (::faviconStore.isInitialized) faviconStore.shutdown()
        // á»¨ng dá»¥ng bá»‹ Ä‘Ã³ng háº³n â†’ WebView khÃ´ng cÃ²n, dá»«ng service vÃ  gá»¡ thÃ´ng bÃ¡o
        try {
            stopService(Intent(this, PlaybackService::class.java))
        } catch (_: Exception) {
        }
        for (webView in pageWebViews.values) {
            webView.destroy()
        }
        pageWebViews.clear()
        shellWebView.destroy()
        super.onDestroy()
    }
}

