package com.lqlq.browser

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cầu nối "LqlqPageBridge" theo ANDROID_PAGE_BRIDGE_SPEC.md.
 *
 * Kiểu A: chạy thuật toán trích chương ngay trong WebView của trang web đang mở
 * (evaluateJavascript) rồi trả JSON về giao diện — tránh chuyển cả HTML lớn.
 *
 * Các hàm @JavascriptInterface chạy trên luồng "JavaBridge" (không phải UI),
 * nên có thể chờ kết quả bằng CountDownLatch mà không làm treo giao diện.
 */
class PageBridge(
    private val activePageWebView: () -> WebView?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnPage(script: String, timeoutSeconds: Long = 8): String? {
        val latch = CountDownLatch(1)
        var output: String? = null

        mainHandler.post {
            val webView = activePageWebView()
            if (webView == null) {
                latch.countDown()
                return@post
            }
            try {
                webView.evaluateJavascript(script) { value ->
                    output = value
                    latch.countDown()
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        }

        try {
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        return output
    }

    /** evaluateJavascript trả chuỗi đã mã hóa JSON — giải mã một lớp. */
    private fun unquote(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            val value = JSONTokener(raw).nextValue()
            value?.toString() ?: ""
        } catch (_: Exception) {
            raw
        }
    }

    // ------------------------------------------------------------------
    // Kiểu A — chương đã trích xuất
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun extractCurrentChapter(): String = unquote(runOnPage(EXTRACTOR_JS))

    @JavascriptInterface
    fun getCurrentChapter(): String = extractCurrentChapter()

    @JavascriptInterface
    fun extractReadableText(): String = extractCurrentChapter()

    @JavascriptInterface
    fun getReadablePageText(): String = extractCurrentChapter()

    @JavascriptInterface
    fun getCurrentPageText(): String = extractCurrentChapter()

    // ------------------------------------------------------------------
    // Kiểu B — HTML và URL thô
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun getCurrentPageHtml(): String =
        unquote(runOnPage("(function(){return document.documentElement.outerHTML;})()"))

    @JavascriptInterface
    fun getPageHtml(): String = getCurrentPageHtml()

    @JavascriptInterface
    fun getHtml(): String = getCurrentPageHtml()

    @JavascriptInterface
    fun getCurrentPageUrl(): String =
        unquote(runOnPage("(function(){return location.href;})()", 4))

    @JavascriptInterface
    fun getUrl(): String = getCurrentPageUrl()

    @JavascriptInterface
    fun getCurrentUrl(): String = getCurrentPageUrl()

    companion object {
        /**
         * Thuật toán trích chương tự chứa, chạy trong ngữ cảnh trang web.
         * Trả về chuỗi JSON: storyTitle, chapterTitle, chapterNumber, body,
         * url, charCount.
         */
        private val EXTRACTOR_JS = """
(function () {
  try {
    var BAD = /(^|[-_ ])(nav|menu|header|footer|sidebar|comment|breadcrumb|share|social|related|recommend|banner|advert|ads?|qc|quangcao|popup|login|paging|pagination|toolbar|button|btn)([-_ ]|${'$'})/i;

    function textLen(node) {
      return (node.innerText || "").replace(/\s+/g, " ").trim().length;
    }

    function isVisible(el) {
      var style = window.getComputedStyle(el);
      return style && style.display !== "none" && style.visibility !== "hidden";
    }

    var candidates = [];
    var selectors = [
      "#chapter-content", ".chapter-content", "#chapter-c", ".chapter-c",
      "#content", ".content", ".reading-content", ".entry-content",
      ".post-content", "article", "#article", ".article", "main",
      ".box-chap", ".truyen", "#noidung", ".noidung"
    ];
    selectors.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (el) {
        candidates.push(el);
      });
    });
    document.querySelectorAll("div,section").forEach(function (el) {
      if (textLen(el) > 800) candidates.push(el);
    });

    var best = null;
    var bestScore = 0;
    candidates.forEach(function (el) {
      if (!isVisible(el)) return;
      var id = (el.id || "") + " " + (el.className || "");
      if (BAD.test(id)) return;
      var len = textLen(el);
      if (len < 200) return;
      var pCount = el.querySelectorAll("p,br").length;
      var linkLen = 0;
      el.querySelectorAll("a").forEach(function (a) {
        linkLen += (a.innerText || "").length;
      });
      var linkDensity = len ? linkLen / len : 1;
      if (linkDensity > 0.4) return;
      var depthPenalty = 0;
      var parent = el.parentElement;
      var depth = 0;
      while (parent && depth < 20) { depth++; parent = parent.parentElement; }
      var score = len + pCount * 30 - depth * 5 - linkDensity * len * 0.5;
      if (score > bestScore) { bestScore = score; best = el; }
    });

    if (!best) best = document.body;

    var clone = best.cloneNode(true);
    clone.querySelectorAll(
      "script,style,noscript,iframe,nav,header,footer,form,button,select,input," +
      "[class*='ad' i],[id*='ad' i],[class*='banner' i],[class*='share' i]," +
      "[class*='comment' i],[id*='comment' i],[class*='recommend' i]"
    ).forEach(function (el) { el.remove(); });

    clone.querySelectorAll("br").forEach(function (br) {
      br.replaceWith(document.createTextNode("\n"));
    });
    clone.querySelectorAll("p,div,h1,h2,h3,h4,li").forEach(function (el) {
      el.append(document.createTextNode("\n"));
    });

    var body = (clone.innerText || clone.textContent || "")
      .replace(/\u00a0/g, " ")
      .split(/\n+/)
      .map(function (line) { return line.replace(/\s+/g, " ").trim(); })
      .filter(function (line) { return line.length > 0; })
      .join("\n\n");

    var heading = document.querySelector("h1,h2,.chapter-title,#chapter-title,.chapter_title");
    var chapterTitle = heading ? (heading.innerText || "").trim() : "";
    var pageTitle = (document.title || "").trim();
    if (!chapterTitle) {
      var match = pageTitle.match(/ch[uươ][ơo]?ng\s*\d+[^|–-]*/i);
      chapterTitle = match ? match[0].trim() : pageTitle;
    }
    var numMatch = (chapterTitle + " " + pageTitle).match(/ch[uươ][ơo]?ng\s*(\d+)/i);
    var chapterNumber = numMatch ? parseInt(numMatch[1], 10) : 0;

    var storyTitle = pageTitle
      .replace(chapterTitle, "")
      .replace(/^[\s|–—:-]+|[\s|–—:-]+${'$'}/g, "")
      .trim() || pageTitle;

    return JSON.stringify({
      storyTitle: storyTitle,
      chapterTitle: chapterTitle,
      chapterNumber: chapterNumber,
      title: chapterTitle || pageTitle,
      body: body,
      text: body,
      url: location.href,
      charCount: body.length
    });
  } catch (error) {
    return JSON.stringify({ error: String(error) });
  }
})();
""".trimIndent()
    }
}
