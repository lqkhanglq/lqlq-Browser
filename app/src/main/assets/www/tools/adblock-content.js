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

  // Việc (v0.23.18): SỬA LỖI NGHIÊM TRỌNG — regex/selector cũ dùng so khớp
  // CHUỖI CON không có ranh giới từ (vd "ads?" khớp cả "load", "already",
  // "gradient", "header"; "[class*='ads' i]" khớp cả class "loads-spinner").
  // Trên trang thật (vd Google), điều này từng ẩn NHẦM một phần giao diện
  // thật của trang (ô tìm kiếm tự nhiên biến mất) vì tình cờ khớp 1 trong
  // các mẫu quá rộng đó. Giờ chỉ coi là "quảng cáo" khi từ khoá xuất hiện
  // dưới dạng 1 TOKEN RIÊNG BIỆT (ngăn cách bởi dấu gạch/khoảng trắng/đầu
  // cuối chuỗi trong kiểu đặt tên kebab-case phổ biến của class/id CSS),
  // không phải là 1 chuỗi con nằm giữa từ khác.
  const AD_TOKEN_RE = /(?:^|[-_ ])(ads?|advert(?:isement)?s?|banner|sponsored|popunder)(?:[-_ ]|$)/i;

  function hasAdToken(value) {
    return AD_TOKEN_RE.test(String(value || ""));
  }

  function isLikelyAdElement(el) {
    return hasAdToken(el.id) || hasAdToken(el.className);
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
