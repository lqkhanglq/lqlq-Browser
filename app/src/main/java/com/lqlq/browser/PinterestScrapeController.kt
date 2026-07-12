package com.lqlq.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Cao anh tu Pinterest bang WebView (dung phien dang nhap san co qua CookieManager
 * chung cua app - nen dang nhap Pinterest 1 lan cho ket qua tot hon). Nhanh hon
 * ChatGPT vi Pinterest TIM-SAN anh (hien tuc thi) thay vi ChatGPT phai TAO anh.
 *
 * Nhan danh sach NHOM {query, count}: moi nhan vat/chu de tim 1 lan, lay `count`
 * anh (bang so canh con cung nhan vat do) -> giam so lan tim. Tra ve danh sach URL
 * PHANG dung thu tu canh (moi nhom dung dung `count` phan tu, thieu thi lap lai anh
 * cuoi de giu khop chi so).
 */
@SuppressLint("SetJavaScriptEnabled")
class PinterestScrapeController(private val activity: MainActivity) {

    data class PinterestResult(
        val ok: Boolean,
        val imageUrls: List<String> = emptyList(),
        val errorMessage: String? = null
    )

    private data class Group(val query: String, val count: Int)

    private var webView: WebView? = null
    private var finished = false
    private var groups: List<Group> = emptyList()
    private var currentIndex = 0
    private var injectedForCurrent = false
    private val flatUrls = mutableListOf<String>()
    private var groupTimeoutMs: Long = 25_000L
    private var onResult: ((PinterestResult) -> Unit)? = null

    fun run(groupsJson: String, groupTimeoutMs: Long = 25_000L, onResult: (PinterestResult) -> Unit) {
        if (webView != null) {
            onResult(PinterestResult(ok = false, errorMessage = "Đang có phiên Pinterest khác chạy, đợi xong đã."))
            return
        }
        groups = parseGroups(groupsJson)
        if (groups.isEmpty()) {
            onResult(PinterestResult(ok = false, errorMessage = "Không có nhóm tìm ảnh nào."))
            return
        }
        finished = false
        currentIndex = 0
        flatUrls.clear()
        this.groupTimeoutMs = groupTimeoutMs
        this.onResult = onResult
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val wv = WebView(activity)
        webView = wv
        configure(wv)
        content.addView(wv, FrameLayout.LayoutParams(1, 1))
        loadCurrentGroup()
    }

    private fun parseGroups(json: String): List<Group> {
        val out = mutableListOf<Group>()
        runCatching {
            val arr = JSONObject(json).optJSONArray("groups") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val q = o.optString("query").trim().ifBlank { "anime" }
                val c = o.optInt("count", 1).coerceIn(1, 20)
                out.add(Group(q, c))
            }
        }
        return out
    }

    private fun loadCurrentGroup() {
        val group = groups.getOrNull(currentIndex) ?: run { dismiss(PinterestResult(true, flatUrls.toList())); return }
        injectedForCurrent = false
        val encoded = URLEncoder.encode(group.query, "UTF-8")
        webView?.loadUrl("https://www.pinterest.com/search/pins/?q=$encoded&rs=typed")
    }

    private fun configure(wv: WebView) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString?.replace(Regex(";\\s*wv\\)"), ")") ?: s.userAgentString
        wv.addJavascriptInterface(ResultBridge(), "LqlqPinterest")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (!injectedForCurrent && url != null && url.contains("/search/pins")) {
                    injectedForCurrent = true
                    val count = groups.getOrNull(currentIndex)?.count ?: 1
                    val js = EXTRACT_SCRIPT
                        .replace("__COUNT__", count.toString())
                        .replace("__GROUP_TIMEOUT__", groupTimeoutMs.toString())
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
        if (obj.optString("step") != "GROUP_DONE") return
        val group = groups.getOrNull(currentIndex) ?: return
        val urls = mutableListOf<String>()
        obj.optJSONArray("urls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val u = arr.optString(i).trim()
                if (u.startsWith("http")) urls.add(u)
            }
        }
        // Dam bao dung `count` phan tu de khop chi so canh: thieu thi lap lai anh cuoi.
        val padded = ArrayList<String>(group.count)
        for (i in 0 until group.count) {
            padded.add(urls.getOrNull(i) ?: urls.lastOrNull() ?: "")
        }
        flatUrls.addAll(padded)

        currentIndex++
        if (currentIndex < groups.size) {
            loadCurrentGroup()
        } else {
            dismiss(PinterestResult(ok = true, imageUrls = flatUrls.toList()))
        }
    }

    private fun dismiss(result: PinterestResult) {
        if (finished) return
        finished = true
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

    companion object {
        // Cho pin i.pinimg.com xuat hien; nang thumbnail (236x/474x...) len 736x.
        private const val EXTRACT_SCRIPT = """
(function(){
  var NEED = __COUNT__;
  function report(o){ try { LqlqPinterest.report(JSON.stringify(o)); } catch(e){} }
  var waited = 0;
  var timer = setInterval(function(){
    waited += 800;
    if (waited % 2400 === 0) { try { window.scrollBy(0, 1400); } catch(e){} }
    var imgs = Array.from(document.querySelectorAll('img'));
    var urls = [];
    imgs.forEach(function(img){
      var s = img.currentSrc || img.src || '';
      if (s.indexOf('i.pinimg.com') >= 0) {
        var hi = s.replace(/\/\d+x\d*\//, '/736x/');
        if (urls.indexOf(hi) < 0) urls.push(hi);
      }
    });
    if (urls.length >= NEED || waited > __GROUP_TIMEOUT__) {
      clearInterval(timer);
      report({ step: 'GROUP_DONE', urls: urls.slice(0, Math.max(NEED, 1)) });
    }
  }, 800);
})();
"""
    }
}
