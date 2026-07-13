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
    private var onProgress: ((Int, String) -> Unit)? = null
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
    fun run(prompt: String, requestId: String = "", visible: Boolean = true, timeoutMs: Long = 90_000L, onProgress: ((Int, String) -> Unit)? = null, onResult: (GeminiWebResult) -> Unit) {
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
        this.onProgress = onProgress
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
            // Chạy ẩn nhưng PHẢI có kích thước THẬT. Trước đây 1x1px khiến câu trả
            // lời DÀI (thời lượng >=120s) không được layout hết -> innerText bị cắt
            // -> JSON không đủ ngoặc đóng -> treo. Nay để full màn hình rồi ĐẨY RA
            // NGOÀI (translationX) nên vô hình + không chắn thao tác, mà DOM vẫn
            // layout đầy đủ toàn bộ nội dung.
            val dm = activity.resources.displayMetrics
            content.addView(wv, FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels))
            wv.translationX = dm.widthPixels.toFloat() * 2f
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
            "PROGRESS" -> {
                val pct = obj.optInt("percent", 0)
                val note = obj.optString("note").ifBlank { "Đang lấy nội dung từ Gemini web..." }
                if (pct > 0) onProgress?.invoke(pct, note)
            }
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
                dismiss(GeminiWebResult(ok = false, errorMessage = "Gemini web ngừng phản hồi (im lặng >${pendingTimeoutMs / 1000}s hoặc quá trần 15 phút). ${diag}"))
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
        onProgress = null
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
  // Bao % tung buoc len UI (chi bao khi % tang de tranh spam).
  var __pct = 0;
  function prog(p, note){ if (p > __pct){ __pct = p; report({ step: 'PROGRESS', percent: p, note: note }); } }
  prog(5, 'Trang Gemini đã sẵn sàng');

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

  // ==== CAPTURE DUNG LUOT TRA LOI (khong quet toan trang) ====
  // Truoc day quet ca '.markdown' toan trang -> gom ca response cu + node khong lien
  // quan; maxLen bi nhieu nen 300s (Gemini render lai/chia khoi nhieu lan) de sai.
  // Nay KHOA dung LUOT model-response moi cua prompt vua gui, chi doc trong no.
  var LOCKED = null;

  function newestResponseNode(){
    var list = document.querySelectorAll('model-response');
    if (list && list.length) return list[list.length - 1];
    var alt = document.querySelectorAll('.model-response-text, message-content');
    return (alt && alt.length) ? alt[alt.length - 1] : null;
  }

  function isGeminiStreaming(){
    // Con dang sinh -> co nut "Dung tao".
    var sels = ['button[aria-label*="Stop" i]','button[aria-label*="Dừng" i]','button[aria-label*="Ngừng" i]','.stop-icon'];
    for (var i=0;i<sels.length;i++){
      var el = document.querySelector(sels[i]);
      if (el && el.offsetParent !== null) return true;
    }
    return false;
  }

  // Gom TAT CA code block trong node da khoa (khong chi cai dau) + textContent.
  function extractLocked(node){
    if (!node) return '';
    // Uu tien <pre> (da bao gom text cua <code> ben trong) -> tranh nhan doi khi
    // vua match 'pre' vua match 'pre code'. Khong co <pre> moi dung <code>.
    var parts = [];
    var pres = node.querySelectorAll('pre');
    if (pres.length){ for (var i=0;i<pres.length;i++) parts.push(pres[i].textContent || ''); }
    else { var codes = node.querySelectorAll('code'); for (var j=0;j<codes.length;j++) parts.push(codes[j].textContent || ''); }
    var codeTxt = parts.join('\n').trim();
    return codeTxt || (node.textContent || '');
  }

  // Lay JSON can ngoac CUOI CUNG (cau tra loi that nam sau echo schema neu co).
  function lastBalancedJson(full){
    var best = null, bestSchema = null, pos = 0, guard = 0, incomplete = false;
    while (guard++ < 200){
      var j = extractBalancedJsonFrom(full, pos);
      if (!j){ if (full.indexOf('{', pos) >= 0) incomplete = true; break; }
      best = j.json;
      // Uu tien JSON DUNG SCHEMA noi dung (co "items" hoac "intro") -> khoa dung
      // cau tra loi that, bo qua echo schema / khoi JSON phu / goi y phia sau lam
      // chuoi "cuoi cung" doi lien tuc khien khong bao gio chot.
      if (j.json.indexOf('"items"') >= 0 || j.json.indexOf('"intro"') >= 0) bestSchema = j.json;
      pos = j.end;
      if (pos >= full.length) break;
    }
    var chosen = bestSchema || best;
    var hasOutro = !!(chosen && chosen.indexOf('"outro"') >= 0 && chosen.indexOf('"items"') >= 0);
    return { json: chosen, incomplete: incomplete, hasOutro: hasOutro };
  }

  function waitForResponse(baseline){
    var longest = '';    // snapshot dai nhat cua RIENG node da khoa
    var idle = 0;        // node da khoa khong dai them
    var hard = 0;        // tong thoi gian (chan tuyet doi)
    // Dem TICH LUY (khong reset khi DOM nhap nhay) -> tranh ket 34% khi chuoi JSON
    // doi tung nhip. Chi can thay JSON du du lieu on dinh vai nhip la chot.
    var seenOutro = 0, bestOutro = '';
    var seenJson = 0, bestJson = '';
    var outroReadyAt = -1;   // moc 'hard' luc bat duoc JSON hoan chinh (co outro) dau tien
    var finalized = false;
    var lastDiag = 'init';
    // Chot 1 lan duy nhat. Dung ca tu vong poll LAN tu hen gio doc lap -> neu vong
    // poll bi nem loi giua chung (truoc day ket 34%), hen gio van chot duoc.
    function finalizeNow(){
      if (finalized) return;
      if (!bestOutro || bestOutro.length === 0) return;
      finalized = true;
      clearInterval(poll);
      var payload = bestOutro.slice(0, 400000);
      try {
        // Chi gui MOT ban qua codeBlock (handleReport dung codeBlock.ifBlank) -> nho
        // payload phan nua, tranh gioi han cau noi voi noi dung dai (300s).
        LqlqPoc.report(JSON.stringify({ step: 'DONE', codeBlock: payload }));
      } catch(e){
        // Loi gui DONE -> cho vong sau thu lai (khong hien log).
        finalized = false;
      }
    }
    var poll = setInterval(function(){
     try {
      hard += 800;
      // (1) Chua khoa: cho LUOT model-response moi (nhieu hon baseline) roi khoa.
      if (!LOCKED){
        var list = document.querySelectorAll('model-response');
        var count = list ? list.length : 0;
        if (count > baseline){ LOCKED = list[count - 1]; prog(25, 'Model bắt đầu trả lời'); }
        else if (count > 0 && baseline === 0){ LOCKED = list[count - 1]; prog(25, 'Model bắt đầu trả lời'); }
        if (!LOCKED){
          lastDiag = 'waitNewTurn base=' + baseline + ' now=' + count;
          if (hard > __TIMEOUT_MS__){ clearInterval(poll); report({ step:'TIMEOUT', lastText:'[chan doan] ' + lastDiag }); }
          return;
        }
      }
      // (2) Da khoa: CHI doc trong node do.
      var full = extractLocked(LOCKED);
      if (full.length > longest.length){ longest = full; idle = 0; if (longest.length > 0) prog(30, 'Nội dung đang tăng'); } else { idle += 800; }
      var streaming = isGeminiStreaming();
      var r = lastBalancedJson(longest);
      if (r.json){
        seenJson++;
        if (r.json.length > bestJson.length) bestJson = r.json;
        if (r.hasOutro){ seenOutro++; if (r.json.length > bestOutro.length) bestOutro = r.json; if (outroReadyAt < 0){ outroReadyAt = hard; setTimeout(finalizeNow, 1600); } prog(34, 'Nội dung đã ổn định, sắp chốt'); }
      }
      lastDiag = 'lockedLen=' + longest.length + ' streaming=' + streaming +
                 ' jsonLen=' + (r.json ? r.json.length : 0) + ' outro=' + r.hasOutro +
                 ' seenOutro=' + seenOutro + ' seenJson=' + seenJson;
      // CHOT chinh: da bat duoc 1 JSON HOAN CHINH (co ca "items" va "outro" = Gemini
      // da viet xong) thi chi can xac nhan them 1 nhip HOAC noi dung ngung dai them
      // ~2.4s la chot ngay. Truoc day doi >=3 nhip lien tiep -> khi DOM nhap nhay,
      // co outro luc co luc khong nen dem khong leo toi 3 -> ket mai o 34%.
      // Da bat duoc JSON hoan chinh (co outro) -> 1.6s sau chot LUON, theo THOI GIAN
      // (khong phu thuoc dem nhip hay noi dung con lan tan) -> chac chan qua 36%.
      if (outroReadyAt >= 0 && bestOutro.length > 0 && (hard - outroReadyAt) >= 1600){
        finalizeNow();
        return;
      }
      // CHOT phong ho: co JSON can ngoac (khong thay "outro" - vd prompt khac schema)
      // giu rat lau + het stream -> nha longest de khong treo.
      if (seenJson >= 20 && !streaming){
        clearInterval(poll);
        report({ step: 'DONE', responseText: bestJson.slice(0, 400000), codeBlock: bestJson.slice(0, 400000) });
        return;
      }
      // Bo cuoc khi node dung hoat dong that lau (idle) HOAC vuot tran cung 15 phut.
      if ((idle > __TIMEOUT_MS__ && !streaming) || hard > 900000){
        clearInterval(poll);
        report({ step: 'TIMEOUT', lastText: '[chan doan] ' + lastDiag });
      }
     } catch(e){ /* nuot loi 1 nhip, vong sau chay tiep */ }
    }, 800);
  }

  var tries = 0;
  var readyTimer = setInterval(function(){
    tries++;
    var input = findInput();
    if (input){
      clearInterval(readyTimer);
      prog(10, 'Đã tìm thấy ô nhập');
      // Dem so LUOT tra loi TRUOC khi gui -> chi chap nhan luot moi xuat hien sau.
      var baseline = document.querySelectorAll('model-response').length;
      var mode = setText(input.el, PROMPT);
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
      report({ step: 'ERROR', error: 'Khong tim thay o nhap sau 20s. Co the chua dang nhap Gemini hoac DOM khac.' });
    }
  }, 500);
})();
"""
    }
}
