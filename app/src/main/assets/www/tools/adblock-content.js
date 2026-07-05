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

  // Chỉ chặn theo URL nguồn cụ thể của mạng quảng cáo đã biết (chính xác,
  // không đoán mò) — an toàn cho MỌI trang.
  const AD_IFRAME_SELECTORS = [
    "iframe[src*='doubleclick.net']",
    "iframe[src*='googlesyndication.com']",
    "iframe[src*='googleadservices.com']",
    "iframe[src*='adnxs.com']",
    "iframe[src*='popads.net']",
    "iframe[src*='popcash.net']"
  ];

  // Việc (v0.23.18): class/id chỉ coi là quảng cáo khi từ khoá xuất hiện
  // dưới dạng 1 TOKEN RIÊNG BIỆT (ngăn cách bởi dấu gạch/khoảng trắng/đầu
  // cuối chuỗi) — an toàn hơn so khớp chuỗi con cũ ("ads?" khớp cả "load").
  const AD_TOKEN_RE = /(?:^|[-_ ])(ads?|advert(?:isement)?s?|banner|sponsored|popunder)(?:[-_ ]|$)/i;

  function hasAdToken(value) {
    return AD_TOKEN_RE.test(String(value || ""));
  }

  function isLikelyAdElement(el) {
    return hasAdToken(el.id) || hasAdToken(el.className);
  }

  // Việc (v0.23.19): người dùng muốn GIỮ NGUYÊN cách chặn "tự do" theo
  // văn bản/HTML (bắt được nhiều quảng cáo hơn ở các trang thường), CHỈ tắt
  // riêng nó ở các trang tìm kiếm/AI (Google, ChatGPT, Gemini, Cốc Cốc...) —
  // vì đây là nơi giao diện thật của trang dễ bị ẩn nhầm nhất do class/id
  // nội bộ phức tạp. Không tắt hẳn cho mọi trang như bản trước.
  const EXEMPT_HOSTS = [
    "google.com", "google.com.vn", "bing.com", "search.brave.com",
    "duckduckgo.com", "yahoo.com", "coccoc.com", "yandex.com", "baidu.com",
    "chatgpt.com", "chat.openai.com", "openai.com", "gemini.google.com",
    "bard.google.com", "claude.ai", "perplexity.ai", "you.com",
    "copilot.microsoft.com"
  ];

  function isExemptHost() {
    const h = (location.hostname || "").toLowerCase();
    return EXEMPT_HOSTS.some(host => h === host || h.endsWith("." + host));
  }

  function normalizeLine(s) {
    return String(s || "")
      .replace(/ /g, " ")
      .replace(/[ \t\f\v]+/g, " ")
      .trim();
  }

  function hideAdsFast() {
    try {
      AD_IFRAME_SELECTORS.forEach(sel => {
        document.querySelectorAll(sel).forEach(el => {
          el.style.setProperty("display", "none", "important");
          el.style.setProperty("visibility", "hidden", "important");
        });
      });

      document.querySelectorAll("div, section, aside, iframe").forEach(el => {
        if (!isLikelyAdElement(el)) return;
        el.style.setProperty("display", "none", "important");
        el.style.setProperty("visibility", "hidden", "important");
      });

      // Cách chặn "tự do" (fixed-position + từ khoá trong văn bản/HTML) —
      // bắt được nhiều quảng cáo hơn nhưng dễ khớp nhầm giao diện thật của
      // trang phức tạp, nên chỉ chạy khi KHÔNG phải trang tìm kiếm/AI.
      if (!isExemptHost()) {
        document.querySelectorAll("a, div").forEach(el => {
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
      }
    } catch (e) {}
  }

  // Việc (v0.23.27 — tối ưu hiệu năng): trang có nhiều thay đổi DOM động
  // (quảng cáo tự nạp lại, live chat, v.v.) từng khiến hideAdsFast() chạy
  // NGAY LẬP TỨC trên MỖI mutation — rất tốn CPU. Gộp nhiều mutation liên
  // tiếp trong ~200ms thành 1 lần chạy duy nhất bằng debounce timer.
  let debounceTimer = 0;
  function scheduleHideAdsFast() {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debounceTimer = 0;
      hideAdsFast();
    }, 200);
  }

  try {
    hideAdsFast();
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", hideAdsFast);
    }
    window.addEventListener("load", hideAdsFast);

    const observer = new MutationObserver(() => scheduleHideAdsFast());
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
