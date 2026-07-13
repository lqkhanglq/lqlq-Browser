package com.lqlq.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * Dieu khien ChatGPT web (dung phien dang nhap san co qua CookieManager chung cua
 * app, giong GeminiWebPocController) de hoi 1 prompt va lay ra CAC ANH THAT ma
 * ChatGPT nhung truc tiep vao khung chat tra loi - khac voi Gemini web (chi dua
 * link "tim kiem tren Pinterest" dang text, khong phai anh that).
 *
 * Nguoi dung da tu kiem chung bang tay: hoi ChatGPT xin anh 10 nhan vat anime,
 * ChatGPT hien anh that ngay trong khung chat (co the tai duoc), trong khi Gemini
 * chi dua link search khong tai duoc. Vi vay doi sang ChatGPT cho luong tim anh.
 *
 * Luon chay AN (WebView 1x1px) - khong co che do hien overlay debug rieng nhu
 * Gemini (chua can, co the them sau neu can chuan doan).
 */
@SuppressLint("SetJavaScriptEnabled")
class ChatGptWebPocController(private val activity: MainActivity) {

    data class ChatGptWebResult(
        val ok: Boolean,
        val imageUrls: List<String> = emptyList(),
        val responseText: String? = null,
        val errorMessage: String? = null
    )

    private var webView: WebView? = null
    private var injected = false
    private var finished = false
    private var pendingPrompt: String = ""
    private var pendingTimeoutMs: Long = 180_000L
    private var activeRequestId: String = ""
    private var initialClipboardText: String = ""
    private var onResult: ((ChatGptWebResult) -> Unit)? = null

    fun run(prompt: String, requestId: String = "", timeoutMs: Long = 180_000L, onResult: (ChatGptWebResult) -> Unit) {
        if (webView != null) {
            onResult(ChatGptWebResult(ok = false, errorMessage = "Đang có phiên ChatGPT web khác chạy, đợi xong đã."))
            return
        }
        injected = false
        finished = false
        activeRequestId = requestId.trim()
        pendingPrompt = prompt
        pendingTimeoutMs = timeoutMs
        // Nho clipboard TRUOC khi chay de biet nut "Sao chep" co that su cap nhat khong.
        initialClipboardText = readClipboardText().trim()
        this.onResult = onResult
        // Giu man hinh sang - WebView bat buoc app dang mo moi chay duoc, so anh
        // cang nhieu (vd 50 anh) cang mat thoi gian, neu man hinh tu khoa giua
        // chung se lam WebView bi Android tam dung va dung im mai khong tiep tuc.
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val wv = WebView(activity)
        webView = wv
        configure(wv)
        // Chay AN nhung PHAI co kich thuoc THAT roi day ra ngoai man hinh: 1x1px
        // khien cau tra loi DAI khong layout het -> innerText/textContent thieu ->
        // JSON thieu ngoac -> treo. Full size + translationX = vo hinh, khong chan
        // thao tac, DOM van layout day du.
        val dm = activity.resources.displayMetrics
        content.addView(wv, FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels))
        wv.translationX = dm.widthPixels.toFloat() * 2f
        wv.loadUrl(CHATGPT_URL)
    }

    private fun configure(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        // Google chan dang nhap OAuth ("Dang nhap bang Google") ben trong WebView
        // nhung (nhan dien qua User-Agent co dau hieu "; wv)" cua Android WebView),
        // coi day la trinh duyet khong dang tin ("disallowed_useragent"). Doi User-
        // Agent giong Chrome mobile that (bo dau hieu WebView) de vuot qua kiem tra
        // nay - day la cach lam pho bien cho cac app nhung dang nhap OAuth cua ben
        // thu 3 (khong phai chinh Google nhu Gemini, von dung chung cookie co san
        // nen khong can OAuth lai).
        s.userAgentString = s.userAgentString?.replace(Regex(";\\s*wv\\)"), ")") ?: s.userAgentString
        wv.addJavascriptInterface(ResultBridge(), "LqlqChatGpt")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!injected && url != null && url.contains("chatgpt.com")) {
                    injected = true
                    val js = POC_SCRIPT
                        .replace("__PROMPT_JSON__", JSONObject.quote(pendingPrompt))
                        .replace("__TIMEOUT_MS__", pendingTimeoutMs.toString())
                    view.evaluateJavascript(js, null)
                }
            }
        }
    }

    private inner class ResultBridge {
        @JavascriptInterface
        fun report(json: String) {
            activity.runOnUiThread { handleReport(json) }
        }
    }

    private fun handleReport(json: String) {
        if (finished) return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (obj.optString("step")) {
            "DONE" -> {
                val urlsArray = obj.optJSONArray("imageUrls")
                val urls = mutableListOf<String>()
                if (urlsArray != null) {
                    for (i in 0 until urlsArray.length()) {
                        val u = urlsArray.optString(i).trim()
                        if (u.isNotBlank()) urls.add(u)
                    }
                }
                val domText = obj.optString("responseText")
                // NGUON CHINH: nut "Sao chep phan hoi". Doc clipboard co retry + 2 khoa
                // an toan (khac clipboard ban dau, KHAC prompt da gui). Neu khong dat
                // -> fallback ve DOM text.
                val copied = if (obj.optBoolean("copyAttempted", false)) waitForValidCopiedText() else null
                dismiss(ChatGptWebResult(ok = true, imageUrls = urls, responseText = copied ?: domText))
            }
            "ERROR" -> {
                dismiss(ChatGptWebResult(ok = false, errorMessage = obj.optString("error").ifBlank { "ChatGPT web báo lỗi không rõ." }))
            }
            "TIMEOUT" -> {
                dismiss(ChatGptWebResult(ok = false, errorMessage = "ChatGPT web quá thời gian chờ phản hồi (${pendingTimeoutMs / 1000}s)."))
            }
        }
    }

    private fun readClipboardText(): String {
        return runCatching {
            val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

    // Doi clipboard duoc cap nhat sau khi bam "Sao chep phan hoi" (toi ~2.5s), voi 2
    // khoa an toan: (1) khac clipboard truoc khi chay, (2) KHAC prompt da gui. Tra ve
    // null neu khong lay duoc noi dung hop le -> caller dung DOM text lam fallback.
    private fun waitForValidCopiedText(): String? {
        val prompt = pendingPrompt.trim()
        val deadline = android.os.SystemClock.elapsedRealtime() + 2_500L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val c = readClipboardText().trim()
            val looksLikePrompt = c == prompt || (prompt.length > 40 && c.startsWith(prompt.take(60)))
            if (c.isNotBlank() && c != initialClipboardText && !looksLikePrompt) {
                return c
            }
            android.os.SystemClock.sleep(150L)
        }
        return null
    }

    private fun dismiss(result: ChatGptWebResult) {
        if (finished) return
        finished = true
        activeRequestId = ""
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView?.let {
            runCatching { it.loadUrl("about:blank") }
            (it.parent as? ViewGroup)?.removeView(it)
            runCatching { it.destroy() }
        }
        webView = null
        val callback = onResult
        onResult = null
        callback?.invoke(result)
    }

    fun cancel(requestId: String): Boolean {
        val normalizedId = requestId.trim()
        if (normalizedId.isBlank() || normalizedId != activeRequestId || finished) return false
        finished = true
        activeRequestId = ""
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onResult = null
        webView?.let {
            runCatching { it.stopLoading() }
            runCatching { it.loadUrl("about:blank") }
            (it.parent as? ViewGroup)?.removeView(it)
            runCatching { it.destroy() }
        }
        webView = null
        return true
    }

    companion object {
        private const val CHATGPT_URL = "https://chatgpt.com/"

        // Nhieu selector du phong vi DOM ChatGPT co the doi theo thoi gian, giong
        // cach lam voi Gemini web.
        private const val POC_SCRIPT = """
(function(){
  var PROMPT = __PROMPT_JSON__;
  function report(o){ try { LqlqChatGpt.report(JSON.stringify(o)); } catch(e){} }

  function findInput(){
    var sels = [
      '#prompt-textarea',
      'div[contenteditable="true"][id="prompt-textarea"]',
      'div[contenteditable="true"]',
      'textarea[data-testid="prompt-textarea"]',
      'textarea'
    ];
    for (var i=0;i<sels.length;i++){
      var el = document.querySelector(sels[i]);
      if (el) return { el: el, sel: sels[i] };
    }
    return null;
  }

  function findSend(){
    var sels = [
      'button[data-testid="send-button"]',
      'button[aria-label*="Send" i]',
      'button[aria-label*="Gửi" i]'
    ];
    for (var i=0;i<sels.length;i++){
      var el = document.querySelector(sels[i]);
      if (el && !el.disabled) return { el: el, sel: sels[i] };
    }
    return null;
  }

  function setText(el, text){
    el.focus();
    if (el.getAttribute('contenteditable') === 'true'){
      el.textContent = text;
      el.dispatchEvent(new InputEvent('input', { bubbles: true, data: text }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    } else {
      try {
        var setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
        setter.call(el, text);
      } catch(e) { el.value = text; }
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  function lastAssistantMessage(){
    var nodes = document.querySelectorAll('div[data-message-author-role="assistant"]');
    if (nodes.length) return nodes[nodes.length - 1];
    return null;
  }

  function extractImageUrls(node){
    if (!node) return [];
    var imgs = Array.from(node.querySelectorAll('img'));
    var urls = [];
    imgs.forEach(function(img){
      var candidate = img.currentSrc || img.src;
      if (img.srcset) {
        var parts = img.srcset.split(',').map(function(s){ return s.trim().split(' ')[0]; }).filter(Boolean);
        if (parts.length) candidate = parts[parts.length - 1];
      }
      if (candidate && candidate.indexOf('http') === 0 && (img.naturalWidth||0) >= 100 && (img.naturalHeight||0) >= 100) {
        urls.push(candidate);
      }
    });
    return urls;
  }

  function extractResponseText(node){
    if (!node) return '';
    // Uu tien code block (noi ChatGPT dat JSON) - lay innerText MOT LAN.
    // KHONG duyet '*' roi join innerText tung phan tu con -> se NHAN BAN text
    // (cha chua text con, con lai lay lai) lam JSON roi loan, parse hong.
    var code = node.querySelector('pre code, code, pre');
    if (code) {
      var ct = (code.textContent || '').trim();
      if (ct) return ct;
    }
    return (node.textContent || '').trim();
  }

  function isStillStreaming(){
    var sels = [
      'button[data-testid="stop-button"]',
      'button[aria-label*="Stop" i]',
      'button[aria-label*="Dừng" i]'
    ];
    for (var i=0;i<sels.length;i++){
      var el = document.querySelector(sels[i]);
      if (el && el.offsetParent !== null) return true;
    }
    return false;
  }

  function waitForResponse(){
    var lastText = '';
    var stable = 0;
    var idle = 0;      // thoi gian ChatGPT KHONG hoat dong (khong stream, khong dai them)
    var lastLen = 0;
    // Bam nut "Sao chep phan hoi" NGAY TRONG khoi cau tra loi moi nhat (KHONG tim
    // toan trang de tranh bam nham nut cua prompt/code block). Node la tin nhan
    // assistant; thanh cong cu Copy thuong nam trong turn wrapper (article) chua no.
    function clickCopyForResponse(node){
      var scope = (node.closest && (node.closest('article') || node.closest('[data-testid^="conversation-turn"]'))) || node.parentElement || node;
      var btns = scope ? Array.prototype.slice.call(scope.querySelectorAll('button')) : [];
      var btn = null;
      for (var i=0;i<btns.length;i++){
        var b = btns[i];
        var label = ((b.getAttribute('data-testid')||'') + ' ' + (b.getAttribute('aria-label')||'') + ' ' + (b.getAttribute('title')||'') + ' ' + (b.innerText||'')).toLowerCase();
        if (label.indexOf('copy') >= 0 || label.indexOf('sao chép') >= 0 || label.indexOf('sao chep') >= 0){ btn = b; break; }
      }
      if (btn){ try { btn.click(); return true; } catch(e){} }
      return false;
    }
    function finish(node, txt){
      clearInterval(poll);
      var urls = extractImageUrls(node);
      // Nguon CHINH: nut "Sao chep phan hoi" (native doc clipboard). DOM text la fallback.
      var copyAttempted = clickCopyForResponse(node);
      setTimeout(function(){
        report({ step: 'DONE', copyAttempted: copyAttempted, imageUrls: urls, responseText: (txt||'').slice(0, 50000) });
      }, copyAttempted ? 450 : 0);
    }
    var poll = setInterval(function(){
      var node = lastAssistantMessage();
      var streaming = isStillStreaming();
      if (node){
        var txt = extractResponseText(node);
        // Con stream hoac con dai them -> dang hoat dong -> reset dong ho cho.
        // 60s va 300s deu cho theo HOAT DONG, khong theo tong thoi gian.
        if (streaming || txt.length > lastLen){ idle = 0; lastLen = Math.max(lastLen, txt.length); }
        else { idle += 800; }
        if (txt === lastText && txt.length > 0){ stable++; }
        else { stable = 0; lastText = txt; }
        if (stable >= 5 && !streaming){ finish(node, txt); return; }
      } else {
        idle += 800;
      }
      // Chi bo cuoc khi ChatGPT thuc su dung viet (idle qua lau), khong phai vi dai.
      if (idle > __TIMEOUT_MS__){
        clearInterval(poll);
        report({ step: 'TIMEOUT' });
      }
    }, 800);
  }

  var tries = 0;
  var readyTimer = setInterval(function(){
    tries++;
    var input = findInput();
    if (input){
      clearInterval(readyTimer);
      setText(input.el, PROMPT);
      setTimeout(function(){
        var send = findSend();
        if (send){
          send.el.click();
        } else {
          input.el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
        }
        waitForResponse();
      }, 700);
    } else if (tries > 40){
      clearInterval(readyTimer);
      report({ step: 'ERROR', error: 'Khong tim thay o nhap sau 20s. Co the chua dang nhap ChatGPT hoac DOM khac.' });
    }
  }, 500);
})();
"""
    }
}
