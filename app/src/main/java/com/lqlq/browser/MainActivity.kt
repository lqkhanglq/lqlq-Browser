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
import android.content.ComponentName
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHELL_HOST = "appassets.androidapp.com"
        private const val SHELL_URL = "https://$SHELL_HOST/assets/www/index.html"

        // Các trang công cụ tìm kiếm/chatbot AI mà người dùng chủ yếu vào để
        // TRA CỨU — không phù hợp để Linh Thạch/Linh Thú/Thẻ Kỳ Vật xuất hiện
        // (khác với các trang đọc truyện/tin tức thông thường).
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

        // Việc "chặn quảng cáo/nhảy trang giống metruyenchu_clipper_android_project"
        // (v0.23.11): project tham khảo chặn TUYỆT ĐỐI theo danh sách domain quảng
        // cáo/redirect đã biết (rules.json của Chapter Clipper gốc), không phụ
        // thuộc gesture — vì vậy trang web không hề bị đổi/nhảy dù chỉ 1 khoảnh
        // khắc. Đây là danh sách mạng quảng cáo/redirect phổ biến (mở rộng từ
        // rules.json gốc: doubleclick, googlesyndication, googleadservices,
        // popads, popcash + vài mạng phổ biến khác hay gặp trên site đọc truyện/
        // xem phim lậu). Khớp theo domain gốc (endsWith) nên bao gồm mọi subdomain.
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

        // Việc (v0.23.14): công cụ tìm kiếm/trợ lý AI — người dùng bấm vào
        // kết quả tìm kiếm/liên kết do chính các trang này tạo ra LÀ mục
        // đích sử dụng bình thường, không phải bị quảng cáo lừa nhảy trang.
        // Khi tab đang ở 1 trong các domain này, bỏ qua lớp khoá domain
        // (Việc v0.23.13) — vẫn giữ nguyên chặn danh sách quảng cáo đã biết.
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
     * Hệ thống thẻ native: BrowserTabStore là nguồn dữ liệu duy nhất.
     * pageWebViews chỉ là bộ nhớ đệm renderer cho một số thẻ gần đây.
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
     * Các tab đã bật Chapter Clipper (việc 1, v0.23.5): một khi người dùng
     * bật cho tab này, script phải tự tiêm lại ở MỌI trang mới trong tab đó
     * (kể cả sau khi next-chapter) cho đến khi người dùng bấm tắt hẳn.
     */
    private val chapterClipperEnabledTabs = mutableSetOf<String>()

    /**
     * Domain gốc "hợp lệ" của từng tab (v0.23.12) — ghi lại mỗi khi 1 trang
     * THẬT SỰ tải xong (onPageFinished), không phải đoán từ view.url() giữa
     * chừng 1 redirect (không đáng tin trong shouldInterceptRequest). Đây là
     * cơ sở để chặn TUYỆT ĐỐI mọi điều hướng không do người dùng bấm thật
     * sang domain khác — giống hệt cách metruyenchu_clipper_android_project
     * chỉ whitelist 1 domain và chặn mọi thứ khác, thay vì đoán theo danh
     * sách domain quảng cáo đã biết (danh sách tĩnh không theo kịp domain
     * rác đổi liên tục kiểu "scouplayen.qp").
     */
    private val tabRootDomain = ConcurrentHashMap<String, String>()

    /**
     * Bật/tắt lớp chặn quảng cáo domain đã biết + chặn nhảy trang sang domain
     * lạ (root-domain guard). Mặc định BẬT. KHÔNG xét gesture: một cú chạm
     * thật có thể bị lớp phủ quảng cáo vô hình "đánh cắp", nên không dùng làm
     * căn cứ cho phép nhảy domain — xem ShellBridge.setDomainGuardEnabled().
     */
    @Volatile
    var domainGuardEnabled: Boolean = true

    /**
     * Tự bấm Back khi trang chính tải xong nhưng trả mã lỗi HTTP ≥ 400 (xem
     * onReceivedHttpError bên dưới). Mặc định TẮT: lỗi 503/502 thường chỉ là
     * server đích đang quá tải/bảo trì tạm thời, không phải quảng cáo hay
     * nguy hiểm gì cả — xem ShellBridge.setBadLoadRecoveryEnabled().
     */
    @Volatile
    var badLoadRecoveryEnabled: Boolean = false

    /**
     * Hiện Toast mỗi khi chặn quảng cáo/chuyển hướng lạ hoặc tự quay lại do
     * trang lỗi. Mặc định BẬT. Tắt cờ này không tắt việc CHẶN — chỉ ẩn dòng
     * thông báo — xem ShellBridge.setBlockNoticeToastsEnabled().
     */
    @Volatile
    var blockNoticeToastsEnabled: Boolean = true

    private fun showBlockNoticeToast(message: String) {
        if (!blockNoticeToastsEnabled) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Ghi một lần bảo vệ đủ điều kiện cho Hồ sơ Phiêu lưu. Chỉ điều hướng
     * main-frame mới gọi hàm này; request ảnh/script/iframe không được thưởng.
     * Key tab + host và cửa sổ 2,2 giây loại bỏ trường hợp cùng redirect bị
     * báo ở cả shouldOverrideUrlLoading và shouldInterceptRequest.
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
    ): String = if (result?.rewarded == true) "$message · +1 Linh Thạch" else message

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

    /** Lớp phủ của giao diện (menu, panel...) đang mở → phải nổi trên trang web. */
    @Volatile
    private var overlayOpen = false

    private var toolbarHeightPx = 0

    private lateinit var assetLoader: WebViewAssetLoader
    private var ttsBridge: TtsBridge? = null
    private lateinit var shellBridge: ShellBridge
    private lateinit var adventureProfileStore: AdventureProfileStore
    private lateinit var adventureProfileBridge: AdventureProfileBridge
    private lateinit var dynamicLootStore: DynamicLootStore
    private lateinit var dynamicLootRepository: DynamicLootRepository
    private lateinit var dynamicLootImageCache: DynamicLootImageCache
    private lateinit var characterPortraitStore: CharacterPortraitStore
    private lateinit var pageToolsBridge: PageToolsBridge
    private lateinit var pageBridge: PageBridge
    private lateinit var faviconStore: FaviconStore

    /** Chống cộng Linh Thạch hai lần khi cùng redirect đi qua nhiều callback. */
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
            lastNativeMediaError = error.localizedMessage ?: "Không phát được nội dung này."
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
    // Chọn tệp (input type=file) — dùng chung cho shell và trang web
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
            // Khôi phục đúng trạng thái player trước đó nếu người dùng hủy chọn.
            publishNativeMediaState(nativeMediaController)
            return@registerForActivityResult
        }

        val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (takeFlags != 0) {
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Một số document provider chỉ cấp quyền trong phiên hiện tại.
            }
        }

        val displayName = queryOpenableDisplayName(uri)
            .ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "Tệp media" }
        val mimeType = inferMediaMimeType(
            contentResolver.getType(uri).orEmpty(),
            displayName
        )
        if (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/")) {
            Toast.makeText(this, "Tệp đã chọn không phải MP3/MP4 hoặc media được hỗ trợ.", Toast.LENGTH_SHORT).show()
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
            // Một số provider chỉ cấp quyền trong phiên hiện tại.
        }

        playNativeMediaFolder(treeUri)
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

    // Video toàn màn hình (onShowCustomView của WebChromeClient — video HTML5
    // fullscreen của BẤT KỲ website nào, khác với "Nhạc và video nền" của
    // riêng app). Trước đây chỉ MATCH_PARENT view của trang, không ẩn thanh
    // trạng thái/điều hướng Android và không có nút xoay/khoá.
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

        // Nút âm lượng vật lý điều khiển kênh media (nhạc + TTS).
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
            contentDescription = "Linh Thạch có thể nhặt"
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
            contentDescription = "Linh Thú Vạn Giới"
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
            contentDescription = "Ảnh Kỳ Vật Vạn Giới"
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
            contentDescription = "Kỳ Vật Vạn Giới động"
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
                    // "Hiệu ứng nhận Linh Thạch" (adventureEffectsToggle) tắt thì
                    // Toast hệ thống này cũng phải tắt theo — trước đây quên gate.
                    if (result.snapshot.effectsEnabled) {
                        if (result.rewarded) {
                            Toast.makeText(
                                this@MainActivity,
                                if (host.isNotBlank()) "Nhặt được 1 Linh Thạch tại $host" else "Nhặt được 1 Linh Thạch",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (result.dailyLimitReached) {
                            Toast.makeText(
                                this@MainActivity,
                                "Bạn đã đầy hạn mức Linh Thạch hôm nay, nhưng vùng đất này vẫn được ghi nhận.",
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
        // TẠM THỜI ĐỂ TEST (v0.32.1): luôn 100% để người dùng thấy Kỳ Vật ở
        // mọi trang khi thử nghiệm. Đổi lại "if (dynamicCount == 0) 0.86 else
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
                    locale = "vi"
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
            roll < 0.003 -> "Thần Thoại"
            roll < 0.020 -> "Huyền Thoại"
            roll < 0.080 -> "Sử Thi"
            roll < 0.280 -> "Hiếm"
            else -> "Thường"
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
        dynamicLootMeta.text = "${item.category} · ${item.rarity} · ${"★".repeat(item.stars.coerceIn(1, 5))}"
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
                if (adventureProfileStore.snapshot().effectsEnabled) {
                    Toast.makeText(this, "Kỳ Vật ${item.name} đã tan vào Vạn Giới.", Toast.LENGTH_SHORT).show()
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
            text = "${item.category} · ${item.rarity} · ${"★".repeat(item.stars.coerceIn(1, 5))}\n\n${item.description}\n\nTìm thấy tại: ${pendingDynamicLootDomain.ifBlank { "Vạn Giới" }}\nNguồn: ${dynamicSourceLabel(item)}"
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
            .setTitle("Kỳ Vật xuất hiện: ${item.name}")
            .setView(content)
            .setPositiveButton("Thu thập thẻ") { _, _ -> collectDynamicLoot(item, bitmap) }
            .setNegativeButton("Bỏ qua") { _, _ -> hideDynamicLootEncounter() }
            .setOnCancelListener { hideDynamicLootEncounter() }
        if (item.sourceUrl.startsWith("http")) {
            builder.setNeutralButton("Xem nguồn") { _, _ -> openExternalUri(Uri.parse(item.sourceUrl)) }
        }
        builder.show()
    }

    private fun collectDynamicLoot(item: DynamicLootItem, bitmap: Bitmap) {
        val localName = runCatching { dynamicLootImageCache.save(item.id, bitmap) }.getOrDefault("")
        val storedItem = item.copy(
            localImageName = localName,
            imageUrl = if (item.imageUrl.startsWith("data:image/")) "" else item.imageUrl
        )
        val result = dynamicLootStore.collect(storedItem, pendingDynamicLootDomain)
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
            .setTitle("Đã ghi vào Vạn Giới Đồ Giám!")
            .setMessage("${item.name}\n${item.category} · ${item.rarity} · ${"★".repeat(item.stars.coerceIn(1, 5))}\n\n${item.description}")
            .setPositiveButton("Tuyệt vời", null)
            .show()
    }

    private fun dynamicSourceLabel(item: DynamicLootItem): String = when (item.sourceType.lowercase(Locale.ROOT)) {
        "ai", "workers-ai" -> "AI tạo nguyên bản"
        "wikimedia", "wikipedia", "knowledge" -> "Wikipedia / Wikimedia"
        else -> item.sourceType.ifBlank { "Vạn Giới" }
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

        // TẠM THỜI ĐỂ TEST (v0.32.1): luôn 100%. Đổi lại
        // "if (snapshot.totalBeastEncounters < 3 && snapshot.collection.isEmpty()) 0.78 else 0.32"
        // khi test xong (những lần đầu cho tỷ lệ cao để hiểu hệ thống, sau đó
        // về mức 32% để Linh Thú vẫn có cảm giác bất ngờ và giá trị sưu tầm).
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
        spiritBeastMeta.text = "${beast.family} · ${beast.rarity}"
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
                if (adventureProfileStore.snapshot().effectsEnabled) {
                    Toast.makeText(this, "${beast.name} đã rời khỏi vùng đất.", Toast.LENGTH_SHORT).show()
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
        val domain = pendingSpiritBeastDomain.ifBlank { "vùng đất chưa biết" }
        val options = arrayOf(
            "Linh Cầu Thô ×${snapshot.orbBasic}  •  ${(beast.baseCatchChance * 100).toInt()}%",
            "Linh Cầu Bạc ×${snapshot.orbSilver}  •  ${(minOf(0.95, beast.baseCatchChance * 1.35) * 100).toInt()}%",
            "Linh Cầu Hoàng Kim ×${snapshot.orbGold}  •  ${(minOf(0.95, beast.baseCatchChance * 1.8) * 100).toInt()}%"
        )
        AlertDialog.Builder(this)
            .setTitle("${beast.icon} ${beast.name} — ${beast.rarity}")
            .setMessage("${beast.family} · Hệ ${beast.habitat}\n\n${beast.description}\n\nNơi gặp: $domain\nChọn Linh Cầu để thử thu phục.")
            .setItems(options) { _, index ->
                val type = when (index) {
                    1 -> "silver"
                    2 -> "gold"
                    else -> "basic"
                }
                attemptSpiritBeastCapture(beast, domain, type)
            }
            .setNegativeButton("Bỏ qua") { _, _ -> hideSpiritBeastEncounter() }
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
                .setTitle("Thu phục thành công!")
                .setMessage("${beast.icon} ${beast.name} đã gia nhập Đồ Giám Vạn Giới của bạn.\n\nĐộ hiếm: ${beast.rarity}\nHệ: ${beast.habitat}")
                .setPositiveButton("Tuyệt vời", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Linh Thú đã thoát")
                .setMessage("${beast.name} phá vỡ phong ấn và biến mất. Những lần thất bại liên tiếp sẽ tăng nhẹ cơ hội ở lần thu phục sau.")
                .setPositiveButton("Tiếp tục hành trình", null)
                .show()
        }
    }

    private fun rarityColors(rarity: String): Triple<Int, Int, Int> = when (rarity) {
        "Thần Thoại" -> Triple(Color.rgb(255, 239, 250), Color.rgb(210, 83, 180), Color.rgb(112, 34, 94))
        "Huyền Thoại" -> Triple(Color.rgb(255, 247, 222), Color.rgb(224, 163, 46), Color.rgb(118, 73, 12))
        "Sử Thi" -> Triple(Color.rgb(244, 235, 255), Color.rgb(143, 85, 210), Color.rgb(82, 40, 128))
        "Hiếm" -> Triple(Color.rgb(230, 245, 255), Color.rgb(69, 147, 206), Color.rgb(31, 84, 124))
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

        // Trình chọn tệp dùng Storage Access Framework và lưu trang dùng
        // MediaStore, nên Android 13+ không cần quyền đọc toàn bộ ảnh/video/
        // âm thanh. Chỉ xin quyền thật sự cần: thông báo và ghi Downloads cũ.
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
    // Shell WebView (giao diện lqlq)
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupShellWebView() {
        applyCommonSettings(shellWebView.settings)

        // Việc 3 (v0.23.4): đặt nền trong suốt NGAY từ đầu, giữ nguyên mãi —
        // không toggle WHITE/TRANSPARENT lúc mở/đóng menu nữa. Lý do: nếu
        // WebView từng vẽ 1 khung hình với nền trắng đục (ngay cả trong quá
        // khứ), khung hình đó có thể bị hiển thị lại y hệt trong khoảnh khắc
        // bringChildToFront() trước khi CSS kịp vẽ lại nội dung thật (chớp
        // trắng). Nền trong suốt cố định + html,body{background:...} đục
        // trong CSS (bình thường) đảm bảo không còn khung hình trắng "cũ"
        // nào có thể lộ ra. z-order vẫn do bringChildToFront() quyết định.
        shellWebView.setBackgroundColor(Color.TRANSPARENT)
        applyBackgroundRendererPriority(shellWebView)

        ttsBridge = TtsBridge(applicationContext)
        shellBridge = ShellBridge(this)
        adventureProfileBridge = AdventureProfileBridge(this, adventureProfileStore, dynamicLootStore, characterPortraitStore)
        pageToolsBridge = PageToolsBridge(shellBridge)
        pageBridge = PageBridge { currentPageWebView() }
        shellWebView.addJavascriptInterface(shellBridge, "LqlqAndroid")
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
                // Giữ shell trong asset; link http bấm trong shell mở như một trang.
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
    // Media native: file MP3/MP4 + URL media trực tiếp, chạy ngoài nền
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
                            ?: "Không kết nối được trình phát Android."
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
            Toast.makeText(this, "Không mở được trình chọn tệp.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Không mở được trình chọn thư mục.", Toast.LENGTH_SHORT).show()
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
            "Thư mục hiện tại • ${playlist.size} tệp • tự chuyển bài"
        } else {
            "Tệp trong máy • phát nền Android"
        }
        playNativePlaylist(playlist, selectedIndex, subtitle)
    }

    private fun playNativeMediaFolder(treeUri: Uri) {
        val playlist = queryTreeFolderPlaylist(treeUri)
        if (playlist.isEmpty()) {
            Toast.makeText(
                this,
                "Thư mục này không có tệp nhạc hoặc video được hỗ trợ.",
                Toast.LENGTH_SHORT
            ).show()
            publishNativeMediaState(nativeMediaController)
            return
        }
        playNativePlaylist(
            playlist,
            0,
            "Thư mục đã chọn • ${playlist.size} tệp • tự chuyển bài"
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

        // Một số document provider trả URI khác với URI MediaStore của cùng tệp.
        // Bảo đảm bài người dùng vừa chọn luôn nằm trong hàng đợi, kể cả khi truy
        // vấn thư mục chỉ trả về các tệp còn lại hoặc provider không ánh xạ URI.
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

        // ACTION_OPEN_DOCUMENT đôi khi trả URI của DownloadsProvider thay vì
        // URI MediaStore. Khi đó đối chiếu tên + kích thước để tìm lại bản ghi
        // MediaStore và lấy RELATIVE_PATH, tránh bắt người dùng chọn lại thư mục.
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
            Toast.makeText(this, "Liên kết media không hợp lệ.", Toast.LENGTH_SHORT).show()
            return
        }
        val safeTitle = title.ifBlank {
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "Media trực tiếp"
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
            "Tệp trong máy • phát nền Android"
        } else {
            "Liên kết media trực tiếp • phát nền Android"
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
                    .setTitle(entry.title.ifBlank { "Nội dung đang phát" })
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
    // YouTube Picture-in-Picture: giữ nguyên iframe chính thức trong Activity
    // ------------------------------------------------------------------

    fun setYoutubePlaybackState(active: Boolean, playing: Boolean, url: String) {
        youtubePipActive = active
        youtubePipPlaying = active && playing
        youtubePipUrl = if (active) url else ""
        updateYoutubePictureInPictureParams()
    }

    fun enterYoutubePictureInPicture() {
        if (!youtubePipActive) {
            Toast.makeText(this, "Chưa có video YouTube đang mở.", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Thiết bị cần Android 8.0 trở lên để dùng PiP.", Toast.LENGTH_SHORT).show()
            return
        }
        prepareYoutubePipSurface(enterAfterPrepare = true)
    }

    fun openYoutubeExternally(url: String) {
        val target = url.ifBlank { youtubePipUrl }
        val uri = try { Uri.parse(target) } catch (_: Exception) { null }
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            Toast.makeText(this, "Liên kết YouTube không hợp lệ.", Toast.LENGTH_SHORT).show()
            return
        }
        val youtubeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Mở app YouTube là một thao tác điều hướng có chủ ý, không phải bấm Home.
        // Chặn đúng lần onUserLeaveHint kế tiếp để không tạo thêm cửa sổ PiP trùng.
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
                Toast.makeText(this, "Không mở được YouTube.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Không thể mở Picture-in-Picture.", Toast.LENGTH_SHORT).show()
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
    // Page WebViews (website thật)
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
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.allowContentAccess = true

        // Không cấp quyền file:// cho mọi website. Chỉ WebView đang mở MHT cục
        // bộ mới bật tạm allowFileAccess trong openOfflineMhtFile().
        settings.allowFileAccess = false

        // Giữ hành vi media hiện có của ứng dụng.
        settings.mediaPlaybackRequiresUserGesture = false
    }

    /**
     * Việc "nhạc/video tắt khi rời app" (v0.23.15): khi Activity không còn
     * hiển thị (ra nền), tiến trình renderer của WebView (Chromium, tách
     * riêng khỏi tiến trình app) mặc định bị hệ điều hành hạ mức ưu tiên,
     * khiến JS/âm thanh HTML5 bên trong dễ bị hệ thống tạm dừng dù tiến
     * trình app chính vẫn sống (nhờ PlaybackService foreground). Giữ mức ưu
     * tiên renderer ở "IMPORTANT" (như thể đang hiển thị) ngay cả khi ở nền,
     * để nhạc/video có cơ hội tiếp tục chạy giống hành vi TTS (vốn là service
     * native, không phụ thuộc renderer của WebView).
     *
     * Lưu ý: đây là API tốt nhất WebView cung cấp cho mục đích này — không
     * đảm bảo tuyệt đối 100% như TTS (TTS dùng engine hệ thống, hoàn toàn
     * tách khỏi WebView), và không giữ được nếu hệ điều hành/ người dùng
     * đóng hẳn ứng dụng (vuốt khỏi Recents) vì khi đó Activity/WebView bị
     * huỷ thật sự, không còn gì để giữ ưu tiên nữa.
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
    // Hệ thống thẻ native — nguồn dữ liệu duy nhất + WebView cache giới hạn
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
     * Snapshot đồng bộ dùng bởi nút "Lưu trang hiện tại". Nguồn dữ liệu là
     * TabStore native nên không phụ thuộc callback JavaScript có đến kịp hay
     * không. Favicon chỉ lấy từ cache cục bộ do WebChromeClient thu được.
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

    /** Favicon cache cục bộ cho trang thẻ mới; tuyệt đối không phát request mạng. */
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

        // openPage() là điều hướng chủ động, vì vậy domain đích trở thành
        // domain hợp lệ mới của thẻ trước khi WebView bắt đầu tải.
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
            Toast.makeText(this, "Hãy mở một trang web trước khi in.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Không mở được trình in Android.", Toast.LENGTH_SHORT).show()
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
            // Không load about:blank trước khi destroy: callback của trang trắng
            // có thể chạy sau đó và ghi đè URL đã lưu của thẻ vừa bị đẩy khỏi
            // cache, khiến lần chọn lại chỉ mở một trang trắng.
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
     * Việc "Lấy chương đang mở" (v0.23.28): gọi từ ShellBridge khi người
     * dùng bấm nút trong Đọc TXT — chạy bất đồng bộ (không CountDownLatch
     * chặn luồng), tránh treo/timeout âm thầm của bản @JavascriptInterface
     * đồng bộ cũ. Kết quả trả về reader.js qua LqlqGlue.onChapterExtracted().
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
    // Việc 4 (v0.23.4): Chapter Clipper — content-script thật, tiêm trực
    // tiếp vào WebView của trang web đang mở (không round-trip qua
    // PageBridge/menu như kiến trúc cũ, tránh đơ/lag).
    // ------------------------------------------------------------------

    private fun readAssetText(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /**
     * Nút bấm trong menu: bật/tắt Chapter Clipper CHO CẢ TAB hiện tại (không
     * chỉ tiêm 1 lần). Khi bật, tab được ghi nhớ trong chapterClipperEnabledTabs
     * để onPageFinished() của WebView tự tiêm lại ở mọi trang kế tiếp.
     */
    fun injectChapterClipper() {
        val tabId = activeTabId
        val webView = currentPageWebView()
        if (tabId == null || webView == null || !pageVisible) {
            Toast.makeText(
                this,
                "Hãy mở một trang web thật rồi bật Chapter Clipper.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (chapterClipperEnabledTabs.contains(tabId)) {
            chapterClipperEnabledTabs.remove(tabId)
            // Ẩn panel/nút nổi trên trang hiện tại ngay lập tức — không cần gỡ
            // hẳn script khỏi DOM, chỉ cần nó không hiện ra nữa và không tự
            // tiêm lại ở trang kế tiếp (đã xử lý ở createPageWebView()).
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
            Toast.makeText(this, "Đã tắt Chapter Clipper trên trang này.", Toast.LENGTH_SHORT).show()
        } else {
            chapterClipperEnabledTabs.add(tabId)
            injectChapterClipperInto(webView)
            Toast.makeText(this, "Đã bật Chapter Clipper trên trang này.", Toast.LENGTH_SHORT).show()
        }
    }

    fun isChapterClipperEnabled(): Boolean {
        val tabId = activeTabId ?: return false
        return chapterClipperEnabledTabs.contains(tabId)
    }

    /** Tiêm content-script + CSS của Chapter Clipper vào 1 WebView cụ thể. */
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
            Toast.makeText(this, "Không nạp được Chapter Clipper.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Việc C (v0.23.6): tiêm script ẩn quảng cáo DOM riêng biệt, LUÔN chạy
     * cho MỌI trang web thật (gọi từ onPageFinished của createPageWebView()),
     * không phụ thuộc Chapter Clipper bật/tắt theo tab.
     *
     * Việc (v0.23.20): người dùng yêu cầu MỌI hình thức chặn quảng cáo phải
     * KHÔNG có tác dụng gì trên trang tìm kiếm/AI (Google, ChatGPT, Claude...)
     * — không tiêm script này vào các trang đó, thay vì chỉ tắt 1 phần bên
     * trong JS như trước.
     */
    /**
     * Việc (v0.23.28): lớp ẩn quảng cáo DOM (MutationObserver, tốn CPU trên
     * trang nhiều quảng cáo động) giờ có thể tắt riêng, MẶC ĐỊNH TẮT — khác
     * với lớp chặn domain/redirect ở shouldOverrideUrlLoading/shouldInterceptRequest
     * (luôn bật, không đổi). Đặt bởi ShellBridge.setAdblockDomEnabled().
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
            // Im lặng bỏ qua — đây là tính năng nền, không cần báo lỗi cho người dùng.
        }
    }

    /**
     * So sánh 2 host có cùng "domain gốc" hay không, bỏ qua tiền tố phổ biến
     * www./m. để không chặn nhầm site tự chuẩn hoá domain của chính nó
     * (vd example.com -> www.example.com). Đơn giản hoá bằng cách so sánh
     * 2 nhãn cuối cùng của host (registrable domain gần đúng) thay vì phụ
     * thuộc thư viện Public Suffix List đầy đủ.
     */
    private fun isSameRootDomain(hostA: String, hostB: String): Boolean {
        fun rootOf(host: String): String {
            val stripped = host.removePrefix("www.").removePrefix("m.")
            val parts = stripped.split(".")
            return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else stripped
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
            else -> Color.rgb(0x18, 0xa6, 0x4a) // emerald (mặc định)
        }
        if (::nativeTabSwitcher.isInitialized) {
            nativeTabSwitcher.applyTheme(dark, accentColor)
        }

        // Đồng bộ các vùng hệ thống Android với giao diện đang chọn, tránh
        // thanh trạng thái/điều hướng sáng chói khi app ở chế độ tối.
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

        // Linh Thạch/Linh Thú/Kỳ Vật (adventureLootLayer) chỉ được thêm vào
        // root MỘT LẦN lúc khởi động — mỗi lần trang web thật được đưa lên
        // trên (bringChildToFront(pageContainer) ở trên) sẽ đè luôn lớp này
        // xuống dưới, nên icon/thẻ dù có hiện (visibility = VISIBLE) vẫn bị
        // trang web che kín, không bao giờ thấy được. Phải tự đưa lên lại
        // mỗi lần, giống cách mediaBubble đã làm đúng bên dưới.
        if (::adventureLootLayer.isInitialized) {
            root.bringChildToFront(adventureLootLayer)
        }

        if (::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen) {
            // Bộ chuyển thẻ là View native toàn màn hình, luôn đứng trên WebView
            // và cả bọt media để không xảy ra lỗi chạm xuyên/z-index như bản cũ.
            root.bringChildToFront(nativeTabSwitcher)
        } else if (::mediaBubble.isInitialized) {
            root.bringChildToFront(mediaBubble)
        }
        root.invalidate()
    }

    /**
     * Nút bọt nhạc nổi native (v0.23.17) — khác với menu/panel (chiếm toàn
     * màn hình bằng z-order shellWebView), đây là 1 View nhỏ độc lập chỉ
     * chiếm đúng góc màn hình, để KHÔNG chặn bấm/cuộn phần còn lại của trang
     * web thật đang mở. Nhờ vậy nhạc/video nền vẫn "nổi" được trên MỌI trang,
     * không cần mở menu ☰, mà trang web vẫn tương tác bình thường xung quanh.
     */
    private lateinit var mediaBubble: android.widget.TextView

    private fun setupMediaBubble() {
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()
        mediaBubble = android.widget.TextView(this).apply {
            text = "♫"
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

    /** Gọi từ ShellBridge.onMediaState() — cùng tín hiệu đang dùng cho thông báo. */
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

        // Việc A (v0.23.6): chặn quảng cáo tự mở cửa sổ/tab mới ở tầng
        // WebView (mạnh hơn nhiều so với chỉ override window.open bằng JS,
        // JS override có thể bị bypass). App hiện KHÔNG có onCreateWindow()
        // xử lý target=_blank nên tắt hẳn 2 cờ này không phá tính năng nào
        // đang hoạt động (setSupportMultipleWindows mặc định đã là false).
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false

        // Chapter Clipper cần duy nhất API saveTextFile(). Website bên ngoài
        // chỉ nhận PageToolsBridge tối thiểu, không được thấy API quản lý thẻ
        // và các quyền đặc biệt khác của ShellBridge.
        webView.addJavascriptInterface(pageToolsBridge, "LqlqAndroid")

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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

                // Việc (v0.23.11 + v0.23.20): chặn TUYỆT ĐỐI domain quảng
                // cáo/redirect đã biết, không xét gesture — một cú chạm thật
                // có thể bị lớp phủ quảng cáo vô hình "đánh cắp", nên gesture
                // không phải căn cứ đáng tin để cho qua (xem domainGuardEnabled
                // ở trên). TRỪ KHI đang ở trang tìm kiếm/AI (Google có thể tự
                // dùng doubleclick.net cho quảng cáo tìm kiếm CHÍNH THỐNG của
                // nó — không nên chặn, tránh phá chức năng thật).
                if (domainGuardEnabled &&
                    !isSearchOrAiToolHost(tabRootDomain[tabId]) &&
                    isBlockedAdHost(request.url.host)
                ) {
                    val reward = if (request.isForMainFrame) {
                        recordAdventureShieldProtection(tabId, request.url.host, "ad-host")
                    } else null
                    showBlockNoticeToast(
                        withAdventureReward("Đã chặn quảng cáo: ${request.url.host}", reward)
                    )
                    return true
                }

                // Chặn TUYỆT ĐỐI mọi điều hướng main-frame tự phát sang domain
                // khác domain gốc hiện tại của tab, KHÔNG xét gesture. Chỉ áp
                // dụng cho khung chính; iframe/tài nguyên con không bị lớp này
                // can thiệp. Công cụ tìm kiếm/AI được miễn trừ để kết quả tìm
                // kiếm hoạt động bình thường. Điều hướng chủ động từ thanh địa
                // chỉ/trang chủ đi qua openPage() và cập nhật domain trước.
                val safeHost = tabRootDomain[tabId]
                val targetHost = request.url.host
                if (domainGuardEnabled && request.isForMainFrame &&
                    !isSearchOrAiToolHost(safeHost) && !isSearchOrAiToolHost(targetHost) &&
                    !safeHost.isNullOrEmpty() && !targetHost.isNullOrEmpty() &&
                    !isSameRootDomain(safeHost, targetHost)
                ) {
                    val reward = recordAdventureShieldProtection(tabId, targetHost, "cross-domain")
                    showBlockNoticeToast(
                        withAdventureReward("Đã chặn chuyển hướng lạ: $targetHost", reward)
                    )
                    return true
                }
                return false
            }

            // shouldInterceptRequest() còn kiểm tra từng bước redirect phía
            // server. Cả 2 lớp chặn (danh sách host quảng cáo + root-domain
            // guard) đều tắt hoàn toàn khi domainGuardEnabled = false.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val targetHost = request.url.host

                // v0.23.20: không chặn gì cả (kể cả danh sách domain quảng
                // cáo đã biết) khi đang ở trang tìm kiếm/AI — trang đó có
                // thể tự dùng hạ tầng quảng cáo của chính nó cho chức năng
                // thật (vd kết quả quảng cáo tìm kiếm của Google).
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
                                    "Đã chặn quảng cáo/chuyển hướng lạ: $targetHost",
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
                    hideAdventureLoot()
                    hideSpiritBeastEncounter()
                    hideDynamicLootEncounter()
                    notifyShellPage(tabId, url, view.title ?: "", loading = true)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                notifyShellPage(tabId, url, view.title ?: "", loading = false)

                // Việc (v0.23.12): ghi lại domain gốc "hợp lệ" của tab này —
                // chỉ khi 1 trang THẬT SỰ tải xong (không phải bị chặn giữa
                // chừng), làm cơ sở để chặn tuyệt đối điều hướng lạ sau này.
                Uri.parse(url).host?.let { tabRootDomain[tabId] = it }

                // Việc C (v0.23.6): ẩn quảng cáo DOM LUÔN BẬT cho MỌI trang,
                // độc lập với Chapter Clipper (không đi qua chapterClipperEnabledTabs).
                injectAdblock(view)

                // Việc 1 (v0.23.5): nếu người dùng đã bật Chapter Clipper cho
                // tab này, tự tiêm lại ở MỌI trang mới (kể cả next-chapter tự
                // động) — không bắt người dùng bấm lại nút mỗi lần chuyển trang.
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
                // QUAN TRỌNG: doUpdateVisitedHistory() có thể được gọi nhiều lần
                // trong một chuỗi chuyển hướng (redirect), TRƯỚC KHI trang đích
                // cuối cùng thực sự tải xong (ví dụ gõ từ khóa → tạm qua
                // www.google.com/search?q=... rồi mới điều hướng tiếp).
                // Nếu báo loading=false ở đây, Nhật ký sẽ ghi nhầm URL trung
                // gian thay vì trang đích thật. Chỉ cập nhật thanh địa chỉ/tiêu
                // đề tạm thời (loading=true) — mục Nhật ký chỉ được ghi thật sự
                // tại onPageFinished(), khi URL cuối cùng đã tải xong.
                notifyShellPage(tabId, url, view.title ?: "", loading = true)
            }

            // Việc D (v0.23.7): nhiều quảng cáo dùng redirect HTTP phía server
            // (Location header) thay vì JS location.href — kiểu này KHÔNG đi
            // qua shouldOverrideUrlLoading() ở trên (WebView tự âm thầm theo
            // chuỗi redirect của 1 request đã được cho phép), nên lọt qua
            // "Việc B" và trang đích cuối cùng (thường hỏng/trắng) vẫn hiện
            // ra. Bắt mã lỗi HTTP của trang CHÍNH ở đây và tự quay lại trang
            // trước đó thay vì để màn hình trắng.
            //
            // TẮT MẶC ĐỊNH (badLoadRecoveryEnabled): mã lỗi HTTP như 503/502
            // thường chỉ là server đích đang quá tải/bảo trì tạm thời, không
            // liên quan gì tới quảng cáo — tự bấm Back trong trường hợp đó
            // gây khó chịu hơn là hữu ích. Không còn bắt lỗi kết nối
            // (onReceivedError) nữa vì lỗi mạng tạm thời (mất sóng, DNS chậm)
            // cũng bị coi nhầm là "trang hỏng do quảng cáo".
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
                                "Trang đích không tải được (HTTP ${errorResponse.statusCode}) — đã quay lại trang trước."
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
                // Renderer chết không được xóa thẻ. Chỉ hủy WebView lỗi; URL và
                // tiêu đề vẫn nằm trong TabStore để thẻ có thể tải lại an toàn.
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
                    "Trang web gặp sự cố. Đang tải lại thẻ…",
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

        return webView
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
            Toast.makeText(this, "Tệp này cần lưu bằng nút lưu trong ứng dụng.", Toast.LENGTH_SHORT).show()
            return
        }
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
            Toast.makeText(this, "Đang tải xuống: $fileName", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không tải xuống được tệp này.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Website có thể tự điều hướng sang scheme khác http(s) (vd fb://,
     * intent://...) để "nhảy" thẳng sang app ngoài mà người dùng KHÔNG hề
     * bấm vào link đó — trước đây hàm này mở app ngay lập tức, không hỏi,
     * khiến trình duyệt tự động bật Facebook/app khác. Giờ luôn hỏi xác
     * nhận trước, không bao giờ tự ý chuyển app.
     */
    private fun openExternalUri(uri: Uri) {
        val intent = try {
            Intent(Intent.ACTION_VIEW, uri)
        } catch (_: Exception) {
            Toast.makeText(this, "Không có ứng dụng nào mở được liên kết này.", Toast.LENGTH_SHORT).show()
            return
        }
        val appLabel = try {
            val resolved = packageManager.resolveActivity(intent, 0)
            resolved?.loadLabel(packageManager)?.toString()
        } catch (_: Exception) {
            null
        }
        val message = if (appLabel.isNullOrBlank()) {
            "Trang web muốn mở một ứng dụng khác. Bạn có muốn chuyển qua không?"
        } else {
            "Trang web muốn mở ứng dụng \"$appLabel\". Bạn có muốn chuyển qua không?"
        }
        AlertDialog.Builder(this)
            .setTitle("Chuyển sang ứng dụng khác?")
            .setMessage(message)
            .setNegativeButton("Ở lại trang web", null)
            .setPositiveButton("Mở ứng dụng") { _, _ ->
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Không có ứng dụng nào mở được liên kết này.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Trang ngoại tuyến: mở tệp HTML đã lưu + lưu trang web thật
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
                    // Tệp .mht/.mhtml lưu bằng saveWebArchive() (giữ ảnh/CSS).
                    "application/x-mimearchive",
                    "message/rfc822",
                    "multipart/related"
                )
            )
        }
        try {
            htmlFileLauncher.launch(Intent.createChooser(intent, "Chọn tệp HTML/MHT đã lưu"))
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được hộp chọn tệp.", Toast.LENGTH_SHORT).show()
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

    /** Mở bản lưu ngoại tuyến; nếu tệp đã bị xóa thì quay lại URL online. */
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
            Toast.makeText(this, "Bản ngoại tuyến không còn. Đã mở trang online.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Không mở được tệp đã lưu.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Đọc/copy MHT ở luồng nền. Bản MHT đã mở được cache theo Uri để lần mở
     * sau gần như tức thì; cache cũ được dọn theo tuổi và giới hạn số lượng.
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
        val tab = tabStore.createTab(url = offlineUrl, title = "Trang ngoại tuyến")
        activeTabId = tab.id
        val webView = pageWebViews.getOrPut(tab.id) { createPageWebView(tab.id) }
        bringPageToFront(webView)
        webView.settings.allowFileAccess = true
        webView.loadUrl(offlineUrl)
        setPageVisible(true)
        trimPageWebViewCache()
        notifyShellTabsChanged()
        Toast.makeText(this, "Đã mở trang ngoại tuyến.", Toast.LENGTH_SHORT).show()
    }

    private fun openOfflineHtmlString(html: String) {
        val baseUrl = "https://offline.lqlq.local/"
        val tab = tabStore.createTab(url = baseUrl, title = "Trang ngoại tuyến")
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
        Toast.makeText(this, "Đã mở trang ngoại tuyến.", Toast.LENGTH_SHORT).show()
    }

    fun saveActivePageOffline() {
        if (offlineSaveInProgress) {
            Toast.makeText(this, "Đang lưu trang trước đó…", Toast.LENGTH_SHORT).show()
            return
        }

        val page = currentPageWebView()
        if (page == null || !pageVisible) {
            Toast.makeText(this, "Chưa có trang web nào đang mở.", Toast.LENGTH_SHORT).show()
            return
        }

        // Chụp metadata trước khi saveWebArchive chạy bất đồng bộ; người dùng có
        // thể chuyển trang/tab trong lúc file đang được tạo.
        val sourceUrl = page.url.orEmpty()
        val sourceTitle = (page.title ?: tabStore.currentTab().title)
            .take(80)
            .ifBlank { "trang-web" }
        val safeTitle = sourceTitle.replace(Regex("[\\/:*?\"<>|]"), "_")
            .trim().ifBlank { "trang-web" }

        offlineSaveInProgress = true
        Toast.makeText(this, "Đang lưu trang ngoại tuyến…", Toast.LENGTH_SHORT).show()

        val cacheMhtDir = File(cacheDir, "offline_mht").apply { mkdirs() }
        cleanupOfflineTempFiles(cacheMhtDir)
        val tempPath = File(cacheMhtDir, "page_${System.currentTimeMillis()}.mht").absolutePath

        page.saveWebArchive(tempPath, false) { savedPath ->
            if (savedPath.isNullOrBlank()) {
                offlineSaveInProgress = false
                Toast.makeText(this, "Không lưu được nội dung trang.", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "Không ghi được tệp trang ngoại tuyến.", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    Toast.makeText(this, "Đã lưu trang vào Download.", Toast.LENGTH_SHORT).show()
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

    /** Ghi stream trực tiếp ra Download, không đọc toàn bộ MHT vào RAM. */
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
    // WebChromeClient chung: chọn tệp + video toàn màn hình
    // ------------------------------------------------------------------

    private fun createChromeClient(pageTabId: String? = null) = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
            val tabId = pageTabId ?: return
            val url = view.url.orEmpty()
            if (url.isNotBlank()) {
                // Chỉ cập nhật metadata; lịch sử vẫn chỉ ghi ở onPageFinished.
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

            // Tự dựng intent thay vì fileChooserParams.createIntent() vì hàm đó
            // xử lý sai accept dạng ".txt" hoặc "audio/*,video/*" trên nhiều máy
            // → hộp chọn tệp không mở hoặc không chọn được gì.
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
                fileChooserLauncher.launch(Intent.createChooser(intent, "Chọn tệp"))
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

        // Việc 4 (v0.23.4): Chapter Clipper (content-script tiêm vào trang
        // thật) dùng alert()/confirm() thường — WebView mặc định không hiện
        // hộp thoại nếu không override 2 hàm này (onJsConfirm mặc định trả
        // false ngay), nên phải tự vẽ AlertDialog native ở đây.
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
                    .setNegativeButton("Hủy") { _, _ -> result.cancel() }
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
        exitImmersiveFullscreen()
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    // ------------------------------------------------------------------
    // Video toàn màn hình (bất kỳ website nào): ẩn thật sự thanh trạng
    // thái/điều hướng Android, không chỉ lấp đầy layout — trước đây video
    // "toàn màn hình" vẫn bị 2 thanh hệ thống che vì không có phần này.
    // ------------------------------------------------------------------

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitImmersiveFullscreen() {
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    private fun fullscreenDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Vùng góc trên-phải: luôn tồn tại suốt phiên fullscreen (không gỡ ra),
     * chỉ ẩn/hiện 2 nút xoay + khoá bên trong. Nhờ vậy chạm vào đúng vùng
     * này khi nút đang ẩn sẽ tự hiện lại — không cần một lớp phủ toàn màn
     * hình nào khác, nên không đụng tới thao tác chạm/tua của video bên
     * dưới (chỉ đúng góc nhỏ 130x70dp mới bắt chạm).
     */
    private fun addFullscreenControls() {
        fullscreenLocked = false

        val rotateBtn = TextView(this).apply {
            text = "⟳"
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
            text = "🔒"
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

    /** Hiện nút xoay/khoá rồi tự ẩn sau vài giây, giống mọi trình phát video. */
    private fun showFullscreenControls() {
        fullscreenLockBtn?.visibility = View.VISIBLE
        fullscreenRotateBtn?.visibility = if (fullscreenLocked) View.GONE else View.VISIBLE
        scheduleFullscreenControlsAutoHide()
    }

    private fun scheduleFullscreenControlsAutoHide() {
        fullscreenHideRunnable?.let { fullscreenHandler.removeCallbacks(it) }
        // Khi đã khoá màn hình, giữ nút khoá hiện luôn để còn chỗ bấm mở lại.
        if (fullscreenLocked) return
        val runnable = Runnable {
            fullscreenRotateBtn?.visibility = View.GONE
            fullscreenLockBtn?.visibility = View.GONE
        }
        fullscreenHideRunnable = runnable
        fullscreenHandler.postDelayed(runnable, 2500)
    }

    /**
     * Khoá màn hình (v0.28.1): chỉ 1 chạm để khoá, chạm lại đúng nút đó để
     * mở — bỏ hẳn kiểu "giữ để mở khoá" rườm rà trước đây. Khi khoá, một lớp
     * phủ trong suốt chặn toàn bộ thao tác lên video; nút khoá vẫn nổi trên
     * lớp đó nên luôn bấm mở lại được.
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
        // Thêm NGAY SAU customView nhưng TRƯỚC vùng nút góc — vùng nút vẫn ở
        // trên cùng nên nút khoá luôn bấm được để mở lại.
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
    // Nút back: thoát video → lùi trang → về trang chủ → nền
    // ------------------------------------------------------------------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val page = currentPageWebView()
                when {
                    customView != null -> exitCustomView()
                    ::nativeTabSwitcher.isInitialized && nativeTabSwitcher.isOpen ->
                        nativeTabSwitcher.hide()
                    // Menu / panel của giao diện đang mở → đóng nó trước
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
    // Vòng đời: KHÔNG tạm dừng WebView khi ra nền để nhạc/TTS tiếp tục
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

        // Chuẩn bị bề mặt video trước rồi chủ động vào PiP. autoEnterEnabled
        // trên Android 12+ vẫn được giữ làm lưới an toàn cho thao tác vuốt Home.
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
        // Cố ý không gọi webView.onPause()/pauseTimers() để media chạy nền.
        shellWebView.resumeTimers()
    }

    override fun onResume() {
        super.onResume()
        shellWebView.resumeTimers()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            notifyShellPipMode(false)
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
        // Ứng dụng bị đóng hẳn → WebView không còn, dừng service và gỡ thông báo
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
