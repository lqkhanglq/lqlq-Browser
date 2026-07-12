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
        this.onResult = onResult
        // Giu man hinh sang - WebView bat buoc app dang mo moi chay duoc, so anh
        // cang nhieu (vd 50 anh) cang mat thoi gian, neu man hinh tu khoa giua
        // chung se lam WebView bi Android tam dung va dung im mai khong tiep tuc.
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val wv = WebView(activity)
        webView = wv
        configure(wv)
        // Chay AN: WebView 1x1px, van gan vao cay view that de chac chan render/chay
        // JS day du (giong Gemini web an).
        content.addView(wv, FrameLayout.LayoutParams(1, 1))
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
                dismiss(ChatGptWebResult(ok = true, imageUrls = urls, responseText = obj.optString("responseText")))
            }
            "ERROR" -> {
                dismiss(ChatGptWebResult(ok = false, errorMessage = obj.optString("error").ifBlank { "ChatGPT web báo lỗi không rõ." }))
            }
            "TIMEOUT" -> {
                dismiss(ChatGptWebResult(ok = false, errorMessage = "ChatGPT web quá thời gian chờ phản hồi (${pendingTimeoutMs / 1000}s)."))
            }
        }
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
    var rich = Array.from(node.querySelectorAll('[data-message-content] *, .markdown *, .prose *'))
      .map(function(el){ return (el.innerText || '').trim(); })
      .filter(Boolean)
      .join('\n')
      .trim();
    return (rich || node.innerText || '').trim();
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
    var waited = 0;
    var poll = setInterval(function(){
      waited += 800;
      var node = lastAssistantMessage();
      if (node){
        var txt = extractResponseText(node);
        if (txt === lastText && txt.length > 0){ stable++; }
        else { stable = 0; lastText = txt; }
        if (stable >= 5 && !isStillStreaming()){
          clearInterval(poll);
          var urls = extractImageUrls(node);
          report({ step: 'DONE', imageUrls: urls, responseText: txt.slice(0, 50000) });
        }
      }
      if (waited > __TIMEOUT_MS__){
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
