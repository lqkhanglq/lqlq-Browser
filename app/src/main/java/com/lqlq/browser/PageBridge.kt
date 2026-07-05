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

    /**
     * Việc "Lấy chương đang mở chưa lấy đúng nội dung" (v0.23.28): bản
     * @JavascriptInterface đồng bộ ở trên phải CHỜ (CountDownLatch, tối đa
     * 8s) trên luồng JavaBridge — nếu evaluateJavascript() vì lý do gì đó
     * (trang nặng, WebView bận) không kịp gọi callback trong 8s, hàm trả về
     * rỗng ÂM THẦM (không có lỗi gì cả), khiến reader.js tưởng nhầm là
     * "không lấy được nội dung" hoặc rơi xuống nhánh dự phòng yếu hơn. Bản
     * KHÔNG chặn này chạy đúng script trích xuất y hệt (EXTRACTOR_JS, thuật
     * toán y hệt Chapter Clipper) nhưng KHÔNG có giới hạn 8 giây — dùng cho
     * MainActivity gọi trực tiếp rồi trả kết quả về reader.js qua callback,
     * giống hệt cách injectChapterClipper() vẫn dùng cho content-script thật.
     */
    fun extractCurrentChapterAsync(onResult: (String) -> Unit) {
        mainHandler.post {
            val webView = activePageWebView()
            if (webView == null) {
                onResult("")
                return@post
            }
            try {
                webView.evaluateJavascript(EXTRACTOR_JS) { value -> onResult(unquote(value)) }
            } catch (_: Exception) {
                onResult("")
            }
        }
    }

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
         * Việc "Đọc TXT lấy chương chưa sạch" (v0.23.9): thuật toán cũ ở đây
         * (BAD-selector generic) yếu hơn nhiều so với bộ máy trích xuất thật
         * dùng trong Chapter Clipper (extractByPageBoundaries +
         * extractByContainerFallback trong chapter-clipper-content.js).
         * reader.js chạy trong shellWebView nên KHÔNG thể gọi thẳng
         * window.LqlqChapterClipper của trang thật (khác WebView = khác JS
         * realm) — PageBridge là đường ĐÚNG để tái sử dụng logic đó, vì
         * evaluateJavascript chạy ngay trong ngữ cảnh trang thật. Cổng y hệt
         * thuật toán trong chapter-clipper-content.js để 2 nơi cho cùng 1
         * chất lượng trích xuất.
         */
        private val EXTRACTOR_JS = """
(function () {
  try {
    var DROP_LINE_PATTERNS = [
      /^Mê\s*Truyện\s*Chữ${'$'}/i, /^Danh\s+sách${'$'}/i, /^Thể\s+loại${'$'}/i,
      /^Tìm\s+kiếm\s+truyện/i, /^Chương\s+trước${'$'}/i, /^Chương\s+tiếp${'$'}/i,
      /^《?\s*Chương\s+trước\s*${'$'}/i, /^Chương\s+tiếp\s*》?${'$'}/i,
      /^《\s*Chương\s+trước${'$'}/i, /^Tải\s+Ebook${'$'}/i, /^Báo\s+lỗi${'$'}/i,
      /^Bình\s+luận${'$'}/i, /^Activate\s+Windows/i, /^Go\s+to\s+Settings/i,
      /^SIÊU/i, /^\d+\s+chương${'$'}/i, /^©/i, /^Menu${'$'}/i, /^Trang chủ${'$'}/i,
      /^Toggle navigation${'$'}/i
    ];
    var TOP_UI_PATTERNS = [
      /^Mê\s*Truyện\s*Chữ${'$'}/i, /^Danh\s+sách${'$'}/i, /^Thể\s+loại${'$'}/i,
      /^Tìm\s+kiếm\s+truyện/i, /^Nữ\s+Đế/i, /^Chương\s+\d+/i,
      /^Chương\s+trước${'$'}/i, /^Chương\s+tiếp${'$'}/i, /^《?\s*Chương\s+trước/i,
      /^Chương\s+tiếp\s*》?${'$'}/i, /^Tải\s+Ebook${'$'}/i, /^Menu${'$'}/i,
      /^Trang chủ${'$'}/i, /^Toggle navigation${'$'}/i
    ];
    var END_PATTERNS = [
      /^《?\s*Chương\s+trước/i, /^Chương\s+tiếp\s*》?${'$'}/i,
      /^Bạn có thể dùng phím/i, /^Báo lỗi${'$'}/i, /^Bình luận${'$'}/i,
      /^TRUYỆN HOT/i, /^Truyện hot/i, /^Danh sách truyện/i,
      /^Truyện đề cử/i, /^Có thể bạn thích/i, /^Mời bạn đọc/i,
      /^Các truyện/i, /^Đọc truyện/i
    ];

    function normalizeLine(s) {
      return String(s || "").replace(/ /g, " ").replace(/[ \t\f\v]+/g, " ").trim();
    }

    function bodyTextLines() {
      var raw = document.body ? (document.body.innerText || document.body.textContent || "") : "";
      return raw.replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/ /g, " ")
        .split("\n").map(normalizeLine);
    }

    function getStoryTitle() {
      var breadcrumb = Array.from(document.querySelectorAll(
        ".breadcrumb a, .breadcrumb span, [class*='breadcrumb'] a, [class*='breadcrumb'] span"
      )).map(function (x) { return normalizeLine(x.innerText || x.textContent); }).filter(Boolean);
      var good = breadcrumb.filter(function (x) {
        return !/^Mê\s*Truyện\s*Chữ${'$'}/i.test(x) && !/^Chương\s+\d+/i.test(x);
      });
      if (good.length >= 1) return good[good.length - 1];
      var headings = Array.from(document.querySelectorAll("h1"));
      for (var i = 0; i < headings.length; i++) {
        var t = normalizeLine(headings[i].innerText || headings[i].textContent);
        if (t && !/^Chương\s+\d+/i.test(t)) return t;
      }
      return normalizeLine((document.title || "").split("|")[0].split("- Chương")[0]);
    }

    function getChapterTitle() {
      var selectors = [
        "h2", "h1", ".chapter-title", ".chapter-name", "[class*='chapter'][class*='title']",
        "[class*='chapter'][class*='name']", ".title"
      ];
      for (var s = 0; s < selectors.length; s++) {
        var nodes = Array.from(document.querySelectorAll(selectors[s]));
        for (var n = 0; n < nodes.length; n++) {
          var t = normalizeLine(nodes[n].innerText || nodes[n].textContent);
          if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(t)) return t;
        }
      }
      var lines = bodyTextLines().slice(0, 50);
      for (var l = 0; l < lines.length; l++) {
        if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(lines[l])) return lines[l];
      }
      var fromTitle = normalizeLine((document.title || "").split("|")[0]);
      var m = fromTitle.match(/(Chương|Chuong|Chapter)\s*\d+[^-|]*/i);
      if (m) return normalizeLine(m[0]);
      var urlM = location.href.match(/chuong[-_\/]?(\d+)/i);
      if (urlM) return "Chương " + parseInt(urlM[1], 10);
      return "Chương chưa rõ số";
    }

    function getChapterNumber(title) {
      var m1 = String(title || "").match(/(?:Chương|Chuong|Chapter)\s*0*(\d+)/i);
      if (m1) return parseInt(m1[1], 10);
      var m2 = location.href.match(/chuong[-_\/]?(\d+)/i);
      if (m2) return parseInt(m2[1], 10);
      return null;
    }

    function lineHasStoryTitle(line, storyTitle) {
      if (!storyTitle) return false;
      var a = normalizeLine(line).toLowerCase();
      var b = normalizeLine(storyTitle).toLowerCase();
      return a === b || a.indexOf(b) >= 0;
    }

    function isLikelyContentLine(line) {
      if (!line) return false;
      if (DROP_LINE_PATTERNS.some(function (p) { return p.test(line); })) return false;
      if (TOP_UI_PATTERNS.some(function (p) { return p.test(line); })) return false;
      if (!/[a-zA-ZÀ-ỹ]/.test(line)) return false;
      if (line.length < 12) return false;
      return true;
    }

    function cleanFinalLines(lines, chapterTitle, storyTitle) {
      var out = [];
      for (var i = 0; i < lines.length; i++) {
        var line = normalizeLine(lines[i]);
        if (!line) {
          if (out.length && out[out.length - 1] !== "") out.push("");
          continue;
        }
        if (DROP_LINE_PATTERNS.some(function (p) { return p.test(line); })) continue;
        if (chapterTitle && line.toLowerCase() === chapterTitle.toLowerCase()) continue;
        if (lineHasStoryTitle(line, storyTitle)) continue;
        out.push(line);
      }
      return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
    }

    function extractByPageBoundaries(chapterTitle, storyTitle) {
      var lines = bodyTextLines();
      var titleIndexes = [];
      var chapNum = getChapterNumber(chapterTitle);
      var exactTitle = normalizeLine(chapterTitle).toLowerCase();

      for (var i = 0; i < Math.min(lines.length, 160); i++) {
        var l = normalizeLine(lines[i]);
        if (!l) continue;
        var low = l.toLowerCase();
        if (exactTitle && low === exactTitle) titleIndexes.push(i);
        else if (chapNum != null && new RegExp("^chương\\s*0*" + chapNum + "\\b", "i").test(l)) titleIndexes.push(i);
      }

      var scanFrom = titleIndexes.length ? titleIndexes[titleIndexes.length - 1] + 1 : 0;
      var start = -1;
      for (var j = scanFrom; j < Math.min(lines.length, scanFrom + 100); j++) {
        var lj = normalizeLine(lines[j]);
        if (!lj) continue;
        if (!isLikelyContentLine(lj)) continue;
        if (lineHasStoryTitle(lj, storyTitle)) continue;
        start = j;
        break;
      }
      if (start < 0) return "";

      var kept = [];
      var charCount = 0;
      for (var k = start; k < lines.length; k++) {
        var lk = normalizeLine(lines[k]);
        if (charCount > 800 && lk && END_PATTERNS.some(function (p) { return p.test(lk); })) break;
        kept.push(lines[k]);
        if (lk) charCount += lk.length + 1;
        if (charCount > 800 && /^TRUYỆN HOT|^Truyện hot/i.test(lk)) break;
      }
      return cleanFinalLines(kept, chapterTitle, storyTitle);
    }

    function getVisibleText(el) {
      if (!el) return "";
      var clone = el.cloneNode(true);
      clone.querySelectorAll([
        "script", "style", "noscript", "svg", "canvas", "iframe",
        "nav", "header", "footer", "aside", "form", "button",
        ".ads", ".ad", ".advertisement", ".banner", ".comment", ".comments",
        ".breadcrumb", ".navbar", ".menu", ".search", ".recommend", ".hot",
        "[class*='ads' i]", "[id*='ads' i]", "[class*='comment' i]", "[id*='comment' i]",
        "[class*='banner' i]", "[id*='banner' i]"
      ].join(",")).forEach(function (n) { n.remove(); });
      return clone.innerText || clone.textContent || "";
    }

    function normalizeText(raw, chapterTitle, storyTitle) {
      var lines = String(raw || "").replace(/\r\n/g, "\n").replace(/\r/g, "\n")
        .replace(/ /g, " ").split("\n").map(normalizeLine);
      var kept = [];
      var charCount = 0;
      for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        if (charCount > 800 && line && END_PATTERNS.some(function (p) { return p.test(line); })) break;
        kept.push(line);
        if (line) charCount += line.length + 1;
      }
      return cleanFinalLines(kept, chapterTitle, storyTitle);
    }

    function scoreText(text) {
      var s = String(text || "");
      var lines = s.split("\n").map(normalizeLine).filter(Boolean);
      var longLines = lines.filter(function (l) { return l.length >= 30; }).length;
      var punc = (s.match(/[,.!?;:。！？…]/g) || []).length;
      var bad = (s.match(/Chương trước|Chương tiếp|Tải Ebook|Bình luận|TRUYỆN HOT|Danh sách|Thể loại/gi) || []).length;
      return s.length + longLines * 220 + punc * 5 - bad * 1200;
    }

    function extractByContainerFallback(chapterTitle, storyTitle) {
      var selectors = [
        "#chapter-content", ".chapter-content", ".chapter-c", "#chapter-c",
        ".chapter-body", ".chapter-text", ".content-chapter", ".reading-content",
        ".entry-content", "article", "main", ".container", ".chapter", "body"
      ];
      var best = "";
      var bestScore = -Infinity;
      for (var s = 0; s < selectors.length; s++) {
        var els = Array.from(document.querySelectorAll(selectors[s]));
        for (var e = 0; e < els.length; e++) {
          var raw = getVisibleText(els[e]);
          var cleaned = normalizeText(raw, chapterTitle, storyTitle);
          var sc = scoreText(cleaned);
          if (cleaned.length > 120 && sc > bestScore) { best = cleaned; bestScore = sc; }
        }
      }
      if (!best || best.length < 300) {
        var ps = Array.from(document.querySelectorAll("p"))
          .map(function (p) { return normalizeLine(p.innerText || p.textContent); })
          .filter(function (t) { return t.length > 20 && !DROP_LINE_PATTERNS.some(function (p) { return p.test(t); }); });
        if (ps.length) best = normalizeText(ps.join("\n\n"), chapterTitle, storyTitle);
      }
      return best;
    }

    function extractBody(chapterTitle, storyTitle) {
      var byBoundary = extractByPageBoundaries(chapterTitle, storyTitle);
      var byContainer = extractByContainerFallback(chapterTitle, storyTitle);
      if (byBoundary.length >= byContainer.length * 0.85) return byBoundary;
      if (byContainer.length > byBoundary.length + 1000) return byContainer;
      return byBoundary || byContainer;
    }

    var storyTitle = getStoryTitle();
    var chapterTitle = getChapterTitle();
    var chapterNumber = getChapterNumber(chapterTitle);
    var body = extractBody(chapterTitle, storyTitle);

    return JSON.stringify({
      storyTitle: storyTitle,
      chapterTitle: chapterTitle,
      chapterNumber: chapterNumber,
      title: chapterTitle || storyTitle,
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
