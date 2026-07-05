/*
 * adblock-content.js — lqlq Browser v0.23.6
 *
 * Content-script RIÊNG, tách từ hideAdsFast()/AD_SELECTORS trong
 * chapter-clipper-content.js. Mục đích: ẩn quảng cáo DOM cơ bản trên
 * MỌI trang web thật, LUÔN BẬT mặc định — không phụ thuộc việc người
 * dùng có bật Chapter Clipper cho tab đó hay không.
 *
 * Được tiêm bởi native.injectAdblock() (MainActivity.kt) tại
 * onPageFinished() của MỌI page WebView, độc lập với
 * chapterClipperEnabledTabs. Không chứa panel/lưu chương — chỉ có phần
 * ẩn quảng cáo bằng CSS selector + rà lại bằng MutationObserver.
 */
(() => {
  if (window.__lqlqAdblockLoaded) return;
  window.__lqlqAdblockLoaded = true;

  const AD_SELECTORS = [
    "iframe[src*='ads']",
    "iframe[src*='doubleclick']",
    "iframe[src*='googlesyndication']",
    "iframe[src*='googleadservices']",
    "[id*='ads' i]",
    "[class*='ads' i]",
    "[id*='advert' i]",
    "[class*='advert' i]",
    "[id*='banner' i]",
    "[class*='banner' i]",
    "[class*='popup' i]",
    "[id*='popup' i]",
    "[class*='float' i][class*='ad' i]"
  ];

  function normalizeLine(s) {
    return String(s || "")
      .replace(/ /g, " ")
      .replace(/[ \t\f\v]+/g, " ")
      .trim();
  }

  function hideAdsFast() {
    try {
      AD_SELECTORS.forEach(sel => {
        document.querySelectorAll(sel).forEach(el => {
          if (el.closest && el.closest("#cc-panel")) return;
          const text = normalizeLine(el.innerText || el.textContent || "");
          if (/Chrome|Trình tiết kiệm bộ nhớ|hoạt động nhanh hơn/i.test(text)) return;
          el.style.setProperty("display", "none", "important");
          el.style.setProperty("visibility", "hidden", "important");
        });
      });

      document.querySelectorAll("a, div").forEach(el => {
        if (el.closest && el.closest("#cc-panel")) return;
        let style;
        try {
          style = getComputedStyle(el);
        } catch (e) {
          return;
        }
        if (style.position !== "fixed") return;
        const text = normalizeLine(el.innerText || el.textContent || "");
        const html = (el.outerHTML || "").slice(0, 500).toLowerCase();
        if (/ads?|advert|banner|popup|siêu|500k|casino|promo|click/.test(text.toLowerCase() + " " + html)) {
          el.style.setProperty("display", "none", "important");
        }
      });
    } catch (e) {}
  }

  try {
    hideAdsFast();
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", hideAdsFast);
    }
    window.addEventListener("load", hideAdsFast);

    const observer = new MutationObserver(() => hideAdsFast());
    const startObserving = () => {
      if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
      }
    };
    if (document.body) {
      startObserving();
    } else {
      document.addEventListener("DOMContentLoaded", startObserving);
    }
  } catch (e) {}
})();
