package com.lqlq.browser

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Điều khiển Gemini web (đã đăng nhập sẵn qua CookieManager chung của app) để
 * gõ 1 prompt và lấy nội dung trả về — dùng cho lựa chọn "Nguồn nội dung: Gemini
 * web (miễn phí)" thay cho Gemini API.
 *
 * Giao diện có thanh toolbar trên cùng với nút đóng "×" theo đúng phong cách
 * header/nút đóng của màn "Hồ sơ Phiêu lưu" (#adventureProfileClose trong
 * styles.css) — không phải nút tròn nổi, không hiện bảng log/mã chạy.
 */
@SuppressLint("SetJavaScriptEnabled")
class GeminiWebPocController(private val activity: MainActivity) {

    private var overlay: View? = null
    private var webView: WebView? = null
    private var injected = false
    private var pendingPrompt: String = ""
    private var pendingTimeoutMs: Long = 90_000L
    private var activeRequestId: String = ""
    private var initialClipboardText: String = ""
    private var onResult: ((GeminiWebResult) -> Unit)? = null
    private var finished = false

    data class GeminiWebResult(
        val ok: Boolean,
        val rawText: String? = null,
        val errorMessage: String? = null
    )

    /**
     * @param visible true = hiện overlay đầy màn hình (dùng cho nút debug "🧪 POC
     * web" để xem tận mắt). false = chạy ẩn, WebView 1x1px không hiện gì — dùng
     * cho luồng thật khi bấm "Tạo nội dung" chọn nguồn Gemini web.
     * @param timeoutMs Thời gian chờ tối đa Gemini phản hồi. Mặc định 90s đủ cho
     * việc soạn kịch bản, nhưng câu hỏi xin URL ảnh thật (có thể khiến Gemini phải
     * tìm kiếm web/grounding) thường chậm hơn nhiều — luồng cào ảnh web dùng
     * timeout dài hơn qua tham số này thay vì đổi hằng số dùng chung.
     */
    fun run(prompt: String, requestId: String = "", visible: Boolean = true, timeoutMs: Long = 90_000L, onResult: (GeminiWebResult) -> Unit) {
        if (overlay != null) {
            onResult(GeminiWebResult(ok = false, errorMessage = "Đang có phiên Gemini web khác chạy, đợi xong đã."))
            return
        }
        injected = false
        finished = false
        activeRequestId = requestId.trim()
        pendingPrompt = prompt
        pendingTimeoutMs = timeoutMs
        initialClipboardText = readClipboardText().trim()
        this.onResult = onResult
        // Giu man hinh sang trong luc chay - WebView bat buoc app dang mo moi chay
        // duoc, neu man hinh tu khoa giua chung (vd cho nhieu anh, mat vai phut)
        // WebView bi Android tam dung va dung im, khong bao gio tu tiep tuc.
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val density = activity.resources.displayMetrics.density

        val wv = WebView(activity)
        webView = wv
        configure(wv)

        if (visible) {
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }

            // Toolbar trên cùng — đúng phong cách .adventure-profile-head +
            // #adventureProfileClose (nút vuông bo góc, nền xám nhạt) thay vì
            // nút tròn nổi kiểu mediaBubble.
            val toolbarHeightPx = (56 * density).toInt()
            val toolbar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                val border = GradientDrawable()
                border.setColor(Color.WHITE)
                border.setStroke(0, Color.TRANSPARENT)
                elevation = 2f * density
            }
            val title = TextView(activity).apply {
                text = "Gemini web"
                textSize = 16f
                setTextColor(Color.parseColor("#1C2D23"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            toolbar.addView(
                title,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val closeSizePx = (40 * density).toInt()
            val closeButton = TextView(activity).apply {
                text = "×"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#526159"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 13f * density
                    setColor(Color.parseColor("#EDF3EF"))
                }
                setOnClickListener {
                    dismiss(GeminiWebResult(ok = false, errorMessage = "Người dùng đã đóng phiên Gemini web."))
                }
            }
            toolbar.addView(closeButton, LinearLayout.LayoutParams(closeSizePx, closeSizePx))

            // Đường viền dưới toolbar giống border-bottom của .adventure-profile-head.
            val toolbarWrapper = FrameLayout(activity).apply {
                setBackgroundColor(Color.WHITE)
            }
            toolbarWrapper.addView(
                toolbar,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeightPx)
            )
            val divider = View(activity).apply {
                setBackgroundColor(Color.parseColor("#E2EBE5"))
            }
            toolbarWrapper.addView(
                divider,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                    gravity = Gravity.BOTTOM
                }
            )

            column.addView(
                toolbarWrapper,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeightPx)
            )
            column.addView(
                wv,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )

            content.addView(
                column,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlay = column
        } else {
            // Chạy ẩn: WebView 1x1px, không chắn thao tác người dùng trên phần còn
            // lại của app. Vẫn phải gắn vào cây view thật thì WebView mới chắc chắn
            // render/chạy JS đầy đủ (không dùng View.GONE).
            content.addView(wv, FrameLayout.LayoutParams(1, 1))
            overlay = wv
        }

        wv.loadUrl(GEMINI_URL)
    }

    private fun configure(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        wv.addJavascriptInterface(ResultBridge(), "LqlqPoc")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!injected && url != null && url.contains("gemini.google.com")) {
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
                val codeBlock = obj.optString("codeBlock").trim()
                val responseText = obj.optString("responseText").trim()
                val domText = codeBlock.ifBlank { responseText }
                // NGUON CHINH: nut Copy cua cau tra loi. Doc clipboard co retry + 2 khoa
                // an toan (khac clipboard ban dau, KHAC prompt da gui). Khong dat -> DOM.
                val copied = if (obj.optBoolean("copyAttempted", false)) waitForValidCopiedText() else null
                dismiss(GeminiWebResult(ok = true, rawText = copied ?: domText))
            }
            "ERROR" -> {
                dismiss(GeminiWebResult(ok = false, errorMessage = obj.optString("error").ifBlank { "Gemini web bao loi khong ro." }))
            }
            "TIMEOUT" -> {
                val diag = obj.optString("lastText").trim()
                dismiss(GeminiWebResult(ok = false, errorMessage = "Gemini web quá giờ (${pendingTimeoutMs / 1000}s). ${diag}"))
            }
            // Các step trung gian (INPUT_FOUND/TEXT_SET/SEND_CLICKED) chỉ để chẩn
            // đoán lúc POC — không cần hiển thị nữa vì đã bỏ bảng log.
        }
    }

    private fun readClipboardText(): String {
        return runCatching {
            val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()?.trim().orEmpty()
        }.getOrDefault("")
    }

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

    private fun dismiss(result: GeminiWebResult) {
        if (finished) return
        finished = true
        activeRequestId = ""
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView?.let {
            runCatching { it.loadUrl("about:blank") }
            (it.parent as? ViewGroup)?.removeView(it)
            runCatching { it.destroy() }
        }
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        overlay = null
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
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        overlay = null
        webView = null
        return true
    }

    companion object {
        private const val GEMINI_URL = "https://gemini.google.com/app"

        // Nhiều selector dự phòng vì DOM Gemini có thể đổi theo thời gian.
        private const val POC_SCRIPT = """
(function(){
  var PROMPT = __PROMPT_JSON__;
  function report(o){ try { LqlqPoc.report(JSON.stringify(o)); } catch(e){} }

  function findInput(){
    var sels = [
      'div.ql-editor[contenteditable="true"]',
      'rich-textarea div[contenteditable="true"]',
      'div[contenteditable="true"][role="textbox"]',
      'div[contenteditable="true"]',
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
      'button[aria-label*="Send" i]',
      'button[aria-label*="Gửi" i]',
      'button.send-button',
      'button[mattooltip*="Send" i]',
      'button[aria-label*="Submit" i]'
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
      // QUAN TRỌNG: prompt thật có nhiều dòng (system prompt + schema JSON).
      // document.execCommand('insertText', ...) với chuỗi nhiều dòng trên ô
      // Quill-editor của Gemini bị hiểu nhầm ký tự xuống dòng như phím Enter
      // (gửi tin nhắn), khiến chỉ dòng đầu tiên ("# Role: ...") được gửi đi,
      // cắt cụt toàn bộ phần còn lại — đây là lý do lấy sai nội dung. Gán
      // thẳng textContent (không qua execCommand) để không bị hiểu nhầm.
      el.textContent = text;
      el.dispatchEvent(new InputEvent('input', { bubbles: true, data: text }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return 'contenteditable-direct';
    } else {
      try {
        var setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
        setter.call(el, text);
      } catch(e) { el.value = text; }
      el.dispatchEvent(new Event('input', { bubbles: true }));
      return 'textarea';
    }
  }

  // Quet ngoac CO NHAN BIET CHUOI, bat dau tu vi tri 'from'. Tra ve {json, end}
  // (end = vi tri sau '}' dong) de con quet TIEP cac JSON khac trong cung text -
  // vi Gemini response dai hay ECHO LAI SCHEMA MAU truoc JSON that.
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
    return null; // chua dong du ngoac -> con dang stream
  }

  function countDots(s){ var m = s.match(/"\.\.\."/g); return m ? m.length : 0; }

  // Quet cac node cau tra loi cua model + TRA VE CHAN DOAN de biet tai sao khong chot.
  function scanResponses(){
    var sels = ['message-content', '.model-response-text', 'model-response', '.markdown'];
    var nodes = [];
    for (var i=0;i<sels.length;i++){
      var found = document.querySelectorAll(sels[i]);
      for (var j=0;j<found.length;j++) nodes.push(found[j]);
    }
    var maxLen = 0, sawBrace = false, incomplete = false, schemaRejected = 0;
    for (var k=nodes.length-1;k>=0;k--){
      var n = nodes[k];
      var code = n.querySelector('code, pre');
      var codeTxt = code ? (code.innerText || '') : '';
      var full = codeTxt || (n.innerText || '');
      if (full.length > maxLen) maxLen = full.length;
      if (full.indexOf('{') >= 0) sawBrace = true;
      // MOT QUY TAC DUY NHAT: lay JSON hoan chinh CUOI CUNG trong node. Cau tra loi
      // that luon nam SAU phan echo schema (neu co) -> cai cuoi = cau tra loi that.
      // Khong chia truong hop, khong doan schema.
      var best = null, pos = 0, guard = 0;
      while (guard++ < 60){
        var j = extractBalancedJsonFrom(full, pos);
        if (!j){ if (full.indexOf('{', pos) >= 0) incomplete = true; break; }
        best = j.json;
        pos = j.end;
        if (pos >= full.length) break;
      }
      if (best){ return { json: best, maxLen: maxLen, diag: 'ok jsonLen=' + best.length }; }
    }
    return {
      json: null,
      maxLen: maxLen,
      diag: 'nodes=' + nodes.length + ' maxLen=' + maxLen + ' sawBrace=' + sawBrace +
            ' incompleteJson=' + incomplete + ' schemaRejected=' + schemaRejected
    };
  }

  function waitForResponse(){
    var lastJson = '';
    var stable = 0;
    var idle = 0;        // thoi gian Gemini KHONG con san sinh them chu (khong hoat dong)
    var lastMax = 0;     // do dai noi dung lon nhat da thay -> tin hieu "con dang stream"
    var lastDiag = 'init';
    var poll = setInterval(function(){
      var s = scanResponses();
      lastDiag = s.diag;
      // Neu Gemini VAN dang viet them (noi dung dai ra) thi reset dong ho cho.
      // Nho vay 300s (JSON dai) va 60s (JSON ngan) deu cho theo HOAT DONG, khong
      // theo tong thoi gian -> khong con "cang dai cang de timeout".
      var m = s.maxLen || 0;
      if (m > lastMax){ lastMax = m; idle = 0; } else { idle += 800; }
      if (s.json){
        if (s.json === lastJson){ stable++; } else { stable = 0; lastJson = s.json; }
        if (stable >= 2){
          clearInterval(poll);
          report({ step: 'DONE', responseText: s.json.slice(0, 400000), codeBlock: s.json.slice(0, 400000) });
          return;
        }
      }
      // Chi bo cuoc khi Gemini THUC SU dung viet (idle qua lau), khong phai vi
      // noi dung dai. __TIMEOUT_MS__ luc nay la nguong "khong hoat dong".
      if (idle > __TIMEOUT_MS__){
        clearInterval(poll);
        report({ step: 'TIMEOUT', lastText: '[chan doan] ' + lastDiag });
      }
    }, 800);
  }

  var tries = 0;
  var readyTimer = setInterval(function(){
    tries++;
    var input = findInput();
    if (input){
      clearInterval(readyTimer);
      var mode = setText(input.el, PROMPT);
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
      report({ step: 'ERROR', error: 'Khong tim thay o nhap sau 20s. Co the chua dang nhap Gemini hoac DOM khac.' });
    }
  }, 500);
})();
"""
    }
}
