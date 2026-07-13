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
    private var onProgress: ((Int, String) -> Unit)? = null

    fun run(prompt: String, requestId: String = "", timeoutMs: Long = 180_000L, onProgress: ((Int, String) -> Unit)? = null, onResult: (ChatGptWebResult) -> Unit) {
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
        this.onProgress = onProgress
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
            "PROGRESS" -> {
                val pct = obj.optInt("percent", 0)
                val note = obj.optString("note").ifBlank { "Đang lấy nội dung từ ChatGPT web..." }
                if (pct > 0) onProgress?.invoke(pct, note)
            }
            "DONE" -> {
                val urlsArray = obj.optJSONArray("imageUrls")
                val urls = mutableListOf<String>()
                if (urlsArray != null) {
                    for (i in 0 until urlsArray.length()) {
                        val u = urlsArray.optString(i).trim()
                        if (u.isNotBlank()) urls.add(u)
                    }
                }
                // codeBlock la nguon chinh (giong Gemini); responseText la fallback cu.
                val domText = obj.optString("codeBlock").trim().ifBlank { obj.optString("responseText") }
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
        onProgress = null
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
  var __pct = 0;
  function prog(p, note){ if (p > __pct){ __pct = p; report({ step: 'PROGRESS', percent: p, note: note }); } }
  prog(5, 'Trang ChatGPT đã sẵn sàng');

  // Quet ngoac CO NHAN BIET CHUOI -> lay JSON hoan chinh (giong Gemini).
  function extractBalancedJsonFrom(text, from){
    if (!text) return null;
    var start = text.indexOf('{', from || 0);
    if (start < 0) return null;
    var depth = 0, inStr = false, esc = false;
    for (var i = start; i < text.length; i++){
      var ch = text.charAt(i);
      if (inStr){
        if (esc) { esc = false; }
        else if (ch === '\\') { esc = true; }
        else if (ch === '"') { inStr = false; }
      } else {
        if (ch === '"') { inStr = true; }
        else if (ch === '{') { depth++; }
        else if (ch === '}') { depth--; if (depth === 0) return { json: text.substring(start, i + 1), end: i + 1 }; }
      }
    }
    return null;
  }
  function lastBalancedJson(full){
    var best = null, bestSchema = null, pos = 0, guard = 0, incomplete = false;
    while (guard++ < 200){
      var j = extractBalancedJsonFrom(full, pos);
      if (!j){ if (full.indexOf('{', pos) >= 0) incomplete = true; break; }
      best = j.json;
      if (j.json.indexOf('"items"') >= 0 || j.json.indexOf('"intro"') >= 0) bestSchema = j.json;
      pos = j.end;
      if (pos >= full.length) break;
    }
    var chosen = bestSchema || best;
    var hasOutro = !!(chosen && chosen.indexOf('"outro"') >= 0 && chosen.indexOf('"items"') >= 0);
    return { json: chosen, incomplete: incomplete, hasOutro: hasOutro };
  }
  // Gom TAT CA code block trong node da khoa + textContent (khong phu thuoc layout).
  function extractLocked(node){
    if (!node) return '';
    var parts = [];
    var pres = node.querySelectorAll('pre');
    if (pres.length){ for (var i=0;i<pres.length;i++) parts.push(pres[i].textContent || ''); }
    else { var codes = node.querySelectorAll('code'); for (var j=0;j<codes.length;j++) parts.push(codes[j].textContent || ''); }
    var codeTxt = parts.join('\n').trim();
    return codeTxt || (node.textContent || '');
  }

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

  // Khoa dung LUOT tra loi moi cua prompt vua gui (khong lay node cuoi trang tuy tien).
  var LOCKED = null;
  function waitForResponse(baseline){
    var longest = '';
    var idle = 0;
    var hard = 0;
    var seenOutro = 0, bestOutro = '';
    var seenJson = 0, bestJson = '';
    var outroReadyAt = -1;
    var finalized = false;
    var lastDiag = 'init';
    function finalizeNow(){
      if (finalized) return;
      if (!bestOutro || bestOutro.length === 0) return;
      finalized = true;
      clearInterval(poll);
      var payload = bestOutro.slice(0, 400000);
      try {
        LqlqChatGpt.report(JSON.stringify({ step: 'DONE', codeBlock: payload }));
      } catch(e){
        finalized = false;
        try { LqlqChatGpt.report(JSON.stringify({ step: 'PROGRESS', percent: 35, note: 'DONE-ERR ' + (e && e.message ? e.message : ('' + e)) + ' len=' + payload.length })); } catch(_){}
      }
    }
    var poll = setInterval(function(){
     try {
      hard += 800;
      if (!LOCKED){
        var list = document.querySelectorAll('div[data-message-author-role="assistant"]');
        var count = list ? list.length : 0;
        if (count > baseline){ LOCKED = list[count - 1]; prog(25, 'Model bắt đầu trả lời'); }
        else if (count > 0 && baseline === 0){ LOCKED = list[count - 1]; prog(25, 'Model bắt đầu trả lời'); }
        if (!LOCKED){
          lastDiag = 'waitNewTurn base=' + baseline + ' now=' + count;
          if (hard > __TIMEOUT_MS__){ clearInterval(poll); report({ step:'TIMEOUT', lastText:'[chan doan] ' + lastDiag }); }
          return;
        }
      }
      var full = extractLocked(LOCKED);
      if (full.length > longest.length){ longest = full; idle = 0; if (longest.length > 0) prog(30, 'Nội dung đang tăng'); } else { idle += 800; }
      var streaming = isStillStreaming();
      var r = lastBalancedJson(longest);
      if (r.json){
        seenJson++;
        if (r.json.length > bestJson.length) bestJson = r.json;
        if (r.hasOutro){ seenOutro++; if (r.json.length > bestOutro.length) bestOutro = r.json; if (outroReadyAt < 0){ outroReadyAt = hard; setTimeout(finalizeNow, 1600); } prog(34, 'Nội dung đã ổn định, sắp chốt'); }
      }
      lastDiag = 'lockedLen=' + longest.length + ' streaming=' + streaming + ' jsonLen=' + (r.json ? r.json.length : 0) + ' outro=' + r.hasOutro + ' seenOutro=' + seenOutro + ' seenJson=' + seenJson;
      if (hard % 4800 < 800){ report({ step: 'PROGRESS', percent: (__pct || 34), note: 'CD ' + lastDiag }); }
      // Chot: JSON hoan chinh (co outro) -> 1.6s sau chot theo thoi gian.
      if (outroReadyAt >= 0 && bestOutro.length > 0 && (hard - outroReadyAt) >= 1600){ finalizeNow(); return; }
      // Phong ho: JSON can ngoac khong co outro giu lau + het stream -> nha longest.
      if (seenJson >= 20 && !streaming){
        clearInterval(poll);
        report({ step: 'DONE', codeBlock: bestJson.slice(0, 400000) });
        return;
      }
      if ((idle > __TIMEOUT_MS__ && !streaming) || hard > 900000){
        clearInterval(poll);
        report({ step: 'TIMEOUT', lastText: '[chan doan] ' + lastDiag });
      }
     } catch(e){
       report({ step: 'PROGRESS', percent: (__pct || 34), note: 'CD-ERR ' + (e && e.message ? e.message : ('' + e)) + ' | ' + lastDiag });
     }
    }, 800);
  }

  var tries = 0;
  var readyTimer = setInterval(function(){
    tries++;
    var input = findInput();
    if (input){
      clearInterval(readyTimer);
      prog(10, 'Đã tìm thấy ô nhập');
      var baseline = document.querySelectorAll('div[data-message-author-role="assistant"]').length;
      setText(input.el, PROMPT);
      prog(15, 'Đã đổ prompt');
      setTimeout(function(){
        var send = findSend();
        if (send){
          send.el.click();
        } else {
          input.el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
        }
        prog(20, 'Đã bấm gửi');
        waitForResponse(baseline);
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
