/*
 * android-glue.js — lqlq Browser v0.23.1 APK
 *
 * Cầu nối giữa giao diện HTML và lớp native Android.
 * Chỉ hoạt động khi chạy trong APK (window.LqlqAndroid tồn tại);
 * mở bằng trình duyệt máy tính thì tự tắt, bản web không bị ảnh hưởng.
 */
(() => {
  "use strict";

  const native = window.LqlqAndroid;
  if (!native) return;

  const $ = id => document.getElementById(id);

  function safeCall(fn) {
    try { fn(); } catch (error) { console.warn("lqlq glue:", error); }
  }

  // ==================================================================
  // 1. Điều hướng: nối giao diện với WebView trang web thật
  // ==================================================================

  const origNavigate = window.navigate;
  if (typeof origNavigate === "function") {
    window.navigate = function (url, addHistory = true) {
      origNavigate(url, addHistory);
      safeCall(() => {
        const tab = window.activeTab?.();
        if (tab && /^https?:\/\//i.test(tab.url)) {
          // Trang web thật sắp mở có thể tự phát video/âm thanh riêng —
          // tạm dừng media đang phát trong panel của shell trước, tránh
          // chồng tiếng giữa 2 nguồn phát cùng lúc.
          safeCall(() => window.ShieldMedia?.pause?.());
          native.openPage(String(tab.id), tab.url);
        }
      });
    };
  }

  // Khôi phục thẻ gần nhất: khi shell vừa nạp xong, nếu phiên trước có
  // thẻ đang mở với URL http(s) thật, tự mở lại thẻ đó trong pageContainer
  // (giống Chrome mở lại tab cuối cùng khi khởi động ứng dụng).
  safeCall(() => {
    const tab = window.activeTab?.();
    if (tab && /^https?:\/\//i.test(tab.url)) {
      native.openPage(String(tab.id), tab.url);
    }
  });

  const origNewTab = window.newTab;
  if (typeof origNewTab === "function") {
    window.newTab = function (url) {
      origNewTab(url);
      safeCall(() => native.showHome());
    };
  }

  const origSwitchTab = window.switchTab;
  if (typeof origSwitchTab === "function") {
    window.switchTab = function (id) {
      origSwitchTab(id);
      safeCall(() => native.switchPage(String(id)));
    };
  }

  const origCloseTab = window.closeTab;
  if (typeof origCloseTab === "function") {
    window.closeTab = function (id) {
      origCloseTab(id);
      safeCall(() => {
        native.closePage(String(id));
        const tab = window.activeTab?.();
        if (tab) native.switchPage(String(tab.id));
      });
    };
  }

  // ==================================================================
  // 2. Danh sách lớp phủ (menu, panel, modal) của giao diện
  //    → dùng cho việc ẩn tạm WebView trang web và cho nút Back
  // ==================================================================

  const OVERLAYS = [
    { id: "searchModal", kind: "hidden", closeBtn: null },
    { id: "warpModal", kind: "hidden", closeBtn: "closeWarpModal" },
    { id: "videoPanel", kind: "hidden", closeBtn: "closeVideoPanel" },
    { id: "mediaCenterOverlay", kind: "hidden", closeBtn: "mediaClosePanelBtn" },
    { id: "imageEditorShell", kind: "aria", closeBtn: "closeImageEditor" },
    { id: "chapterClipperPanel", kind: "aria", closeBtn: "chapterClipperClose" },
    { id: "storyReaderPanel", kind: "aria", closeBtn: "closeStoryReader" },
    { id: "securityCenterOverlay", kind: "hidden", closeBtn: "closeSecurityCenter" },
    { id: "mobileTabsOverlay", kind: "hidden", closeBtn: "mobileTabsClose" },
    { id: "chromeMenu", kind: "hidden", closeBtn: null },
    { id: "toolsMenu", kind: "hidden", closeBtn: null },
    { id: "sideDrawer", kind: "open", closeBtn: "closeDrawer" }
  ];

  function overlayIsOpen(spec) {
    const el = $(spec.id);
    if (!el) return false;
    if (spec.kind === "open") return el.classList.contains("open");
    if (spec.kind === "aria") return el.getAttribute("aria-hidden") === "false";
    return !el.classList.contains("hidden");
  }

  function closeOverlay(spec) {
    const el = $(spec.id);
    if (!el) return;
    if (spec.closeBtn) {
      const btn = $(spec.closeBtn);
      if (btn) { btn.click(); return; }
    }
    if (spec.id === "searchModal" && typeof window.closeSearchModal === "function") {
      window.closeSearchModal();
      return;
    }
    if (spec.kind === "open") el.classList.remove("open");
    else if (spec.kind === "aria") el.setAttribute("aria-hidden", "true");
    else el.classList.add("hidden");
  }

  let lastOverlayOpen = null;

  function reportOverlayState() {
    safeCall(() => {
      const open = OVERLAYS.some(overlayIsOpen);
      if (open === lastOverlayOpen) return;
      lastOverlayOpen = open;
      native.setOverlayOpen(open);
    });
  }

  // Theo dõi thay đổi class / aria-hidden của từng lớp phủ
  OVERLAYS.forEach(spec => {
    const el = $(spec.id);
    if (!el) return;
    new MutationObserver(reportOverlayState).observe(el, {
      attributes: true,
      attributeFilter: ["class", "aria-hidden"]
    });
  });
  // Lưới an toàn: kiểm tra sau mỗi cú chạm và định kỳ
  document.addEventListener("click", () => setTimeout(reportOverlayState, 80), true);
  setInterval(reportOverlayState, 900);

  function closeAllMenus() {
    ["chromeMenu", "toolsMenu"].forEach(id => $(id)?.classList.add("hidden"));
    $("sideDrawer")?.classList.remove("open");
    $("mobileTabsOverlay")?.classList.add("hidden");
    setTimeout(reportOverlayState, 60);
  }

  // ==================================================================
  // 3. API cho native gọi vào (window.LqlqGlue)
  // ==================================================================

  const glue = {
    // Nút Back của điện thoại: đóng lớp phủ trên cùng nếu có
    closeTopOverlay() {
      let closed = false;
      safeCall(() => {
        const spec = OVERLAYS.find(overlayIsOpen);
        if (spec) {
          closeOverlay(spec);
          closed = true;
        }
      });
      setTimeout(reportOverlayState, 120);
      return closed;
    },

    onNativePage(info) {
      safeCall(() => {
        const profile = window.currentProfile?.();
        if (!profile) return;
        const tab = profile.tabs.find(item => String(item.id) === String(info.tabId));
        if (!tab) return;

        const changed = tab.url !== info.url;
        tab.url = info.url || tab.url;
        if (info.title) tab.title = info.title;

        if (!info.loading && changed && /^https?:\/\//i.test(info.url)) {
          profile.history.unshift({
            title: info.title || tab.title,
            url: info.url,
            favicon: window.faviconForUrl ? window.faviconForUrl(info.url) : "",
            time: new Date().toLocaleString("vi-VN")
          });
          profile.history = profile.history.slice(0, 200);
        }

        window.saveState?.();
        if (String(profile.activeTabId) === String(info.tabId)) {
          const address = $("addressInput");
          if (address && document.activeElement !== address) {
            address.value = info.url;
          }
          window.render?.();
        }
      });
    },

    onNativePageVisibility(visible) {
      document.body.classList.toggle("lqlq-native-page-open", Boolean(visible));
      if (visible) {
        // Trang web thật vừa hiện ra (mở tab mới, chuyển tab, hoặc quay lại
        // trang đang mở) — dừng media panel để không chồng tiếng với video/
        // âm thanh của chính trang web đó.
        safeCall(() => window.ShieldMedia?.pause?.());
      }
    },

    // Lệnh từ thanh thông báo → Đọc truyện TXT
    readerCmd(cmd) {
      safeCall(() => {
        if (cmd === "toggle") {
          const state = $("readerStatus")?.dataset.state;
          if (state === "speaking" || state === "paused") {
            $("readerPauseBtn")?.click();
          } else {
            $("readerPlayBtn")?.click();
          }
        } else if (cmd === "next") {
          $("readerNextBtn")?.click();
        } else if (cmd === "prev") {
          $("readerPrevBtn")?.click();
        } else if (cmd === "stop") {
          $("readerStopBtn")?.click();
        }
      });
    },

    // Lệnh từ thanh thông báo → trình phát nhạc/video
    mediaCmd(cmd) {
      safeCall(() => {
        if (cmd === "stop") {
          window.ShieldMedia?.stop?.();
          setTimeout(sendMediaState, 100);
          return;
        }
        if (cmd === "toggle") {
          const player = mainAudiblePlayer();
          if (!player) return;
          if (player.paused) player.play().catch(() => {});
          else player.pause();
        }
      });
    }
  };

  window.LqlqGlue = glue;

  // ==================================================================
  // 4. Báo chiều cao thanh công cụ cho native
  // ==================================================================

  function reportToolbarHeight() {
    safeCall(() => {
      const shell = document.querySelector(".top-shell");
      const bottom = shell ? Math.max(0, shell.getBoundingClientRect().bottom) : 0;
      native.setToolbarHeight(bottom);
    });
  }

  window.addEventListener("load", reportToolbarHeight);
  window.addEventListener("resize", reportToolbarHeight);
  window.addEventListener("lqlq-mobile-toolbar-change", () => {
    setTimeout(reportToolbarHeight, 320);
  });
  setTimeout(reportToolbarHeight, 400);
  setTimeout(reportToolbarHeight, 1500);

  // ==================================================================
  // 5. Đọc truyện TXT → thông báo nền
  // ==================================================================

  let lastReaderJson = "";

  function sendReaderState() {
    safeCall(() => {
      const state = $("readerStatus")?.dataset.state || "idle";
      const active = state === "speaking" || state === "paused";
      const payload = JSON.stringify({
        active,
        playing: state === "speaking",
        title: ($("readerSourceTitle")?.textContent || "Đọc truyện TXT").trim(),
        text: ($("readerNowReading")?.textContent || "").trim().slice(0, 160)
      });
      if (payload === lastReaderJson) return;
      lastReaderJson = payload;
      native.onReaderState(payload);
    });
  }

  const readerStatus = $("readerStatus");
  if (readerStatus) {
    new MutationObserver(sendReaderState).observe(readerStatus, {
      attributes: true,
      attributeFilter: ["data-state"]
    });
  }
  const nowReading = $("readerNowReading");
  if (nowReading) {
    new MutationObserver(sendReaderState).observe(nowReading, {
      childList: true,
      characterData: true,
      subtree: true
    });
  }

  // ==================================================================
  // 6. Nhạc và video nền → thông báo nền
  //    Bắt sự kiện ở cấp document (capture) nên KHÔNG bỏ sót
  //    bất kỳ phần tử audio/video nào, kể cả tạo sau này.
  // ==================================================================

  function allAudiblePlayers() {
    return Array.from(document.querySelectorAll("audio, video")).filter(
      player => player.currentSrc && !player.muted
    );
  }

  function mainAudiblePlayer() {
    const players = allAudiblePlayers();
    return (
      players.find(player => !player.paused && !player.ended) ||
      players.find(player => player.currentTime > 0 && !player.ended) ||
      players[0] ||
      null
    );
  }

  let lastMediaJson = "";

  function sendMediaState() {
    safeCall(() => {
      const players = allAudiblePlayers();
      const playing = players.some(player => !player.paused && !player.ended);
      const started = players.some(
        player => player.currentTime > 0 && !player.ended
      );
      const payload = JSON.stringify({
        active: playing || started,
        playing,
        title: ($("mediaNowTitle")?.textContent || "Nhạc và video nền").trim(),
        text: ($("mediaNowSubtitle")?.textContent || "").trim().slice(0, 160)
      });
      if (payload === lastMediaJson) return;
      lastMediaJson = payload;
      native.onMediaState(payload);
    });
  }

  ["play", "playing", "pause", "ended", "emptied", "volumechange", "loadedmetadata"].forEach(type => {
    document.addEventListener(
      type,
      event => {
        const el = event.target;
        if (!(el instanceof HTMLMediaElement)) return;
        // Chốt chặn kép cho lỗi âm thanh phát 2 lần:
        // trình phát mini luôn phải câm tiếng.
        if (el.id === "globalMiniVideo") el.muted = true;
        sendMediaState();
        setTimeout(sendMediaState, 60);
      },
      true
    );
  });

  // Lưới an toàn: một số thiết bị không phát đủ sự kiện play/pause khi
  // nguồn media được gán lại nhanh (chuyển bài liên tục) — đối chiếu định
  // kỳ để thanh thông báo không bị treo sai trạng thái, giống cơ chế
  // MutationObserver dùng cho Đọc TXT ở trên.
  setInterval(sendMediaState, 2000);

  // ==================================================================
  // 7. Trang ngoại tuyến: nút MỞ tệp HTML + LƯU trang web thật
  // ==================================================================

  const openOfflineBtn = $("openOfflineHtmlBtn");
  if (openOfflineBtn && typeof native.openHtmlFile === "function") {
    openOfflineBtn.classList.remove("hidden");
    openOfflineBtn.addEventListener(
      "click",
      event => {
        event.preventDefault();
        event.stopPropagation();
        closeAllMenus();
        safeCall(() => native.openHtmlFile());
      },
      true
    );
  }

  // Khi đang mở website thật: "Lưu trang ngoại tuyến" phải lưu HTML
  // của chính website đó (do native lấy), không phải giao diện lqlq.
  document.addEventListener(
    "click",
    event => {
      const btn = event.target?.closest?.('[data-action="save-page-html"]');
      if (!btn) return;
      if (!document.body.classList.contains("lqlq-native-page-open")) return;
      if (typeof native.savePageOffline !== "function") return;
      event.preventDefault();
      event.stopPropagation();
      closeAllMenus();
      safeCall(() => native.savePageOffline());
      // Đồng thời thêm trang vào danh sách "Trang đã lưu" (bookmark) để
      // người dùng thấy ngay, tránh cảm giác "lưu xong nhưng danh sách
      // trống" (2 tính năng khác nhau: tệp .mht ngoại tuyến vs bookmark).
      safeCall(() => window.LqlqSavedPages?.saveCurrent());
    },
    true
  );

  // ==================================================================
  // 8. Nút Home: về trang chủ lqlq
  // ==================================================================

  document.addEventListener(
    "click",
    event => {
      const homeButton = event.target?.closest?.("#mobileHomeBtn");
      if (!homeButton) return;
      event.preventDefault();
      event.stopPropagation();
      safeCall(() => native.showHome());
      closeAllMenus();
    },
    true
  );

  // ==================================================================
  // 8b. Chapter Clipper (v0.23.4): tiêm content-script thật thẳng vào
  //     WebView của trang web đang mở, thay cho kiến trúc PageBridge
  //     round-trip cũ (v15-chapter-clipper.js, đã tắt trong index.html).
  //     Nút menu vẫn giữ nguyên vị trí trong chrome-menu.
  // ==================================================================

  document.addEventListener(
    "click",
    event => {
      const btn = event.target?.closest?.("#chapterClipperMenuBtn");
      if (!btn) return;
      event.preventDefault();
      event.stopPropagation();
      closeAllMenus();
      if (typeof native.injectChapterClipper === "function") {
        safeCall(() => native.injectChapterClipper());
        // Cập nhật trạng thái nút ngay sau khi bật/tắt (trễ 1 nhịp để native
        // kịp cập nhật chapterClipperEnabledTabs trước khi ta đọc lại).
        setTimeout(updateChapterClipperMenuBtnState, 50);
      } else {
        safeCall(() => native.showToast?.("Bản này chưa hỗ trợ Chapter Clipper."));
      }
    },
    true
  );

  // Phản ánh trạng thái bật/tắt của tab hiện tại lên nút menu mỗi khi mở
  // menu chính, để người dùng biết Chapter Clipper đang bật hay tắt.
  function updateChapterClipperMenuBtnState() {
    const btn = document.getElementById("chapterClipperMenuBtn");
    if (!btn || typeof native.isChapterClipperEnabled !== "function") return;
    let enabled = false;
    try {
      enabled = !!native.isChapterClipperEnabled();
    } catch (e) {}
    btn.classList.toggle("chapter-clipper-active", enabled);
    const label = btn.querySelector(".menu-copy b");
    if (label) {
      label.textContent = enabled ? "Chapter Clipper (đang bật)" : "Chapter Clipper";
    }
  }

  document.addEventListener(
    "click",
    event => {
      if (event.target?.closest?.("#menuBtn")) {
        setTimeout(updateChapterClipperMenuBtnState, 0);
      }
    },
    true
  );

  // ==================================================================
  // 9. Lưu tệp: chuyển link tải blob:/data: sang thư mục Download
  // ==================================================================

  function base64FromBlob(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
      reader.onerror = () => reject(new Error("Không đọc được dữ liệu."));
      reader.readAsDataURL(blob);
    });
  }

  document.addEventListener(
    "click",
    event => {
      const anchor = event.target?.closest?.("a[download]");
      if (!anchor) return;
      const href = anchor.getAttribute("href") || "";
      if (!href.startsWith("blob:") && !href.startsWith("data:")) return;

      event.preventDefault();
      event.stopPropagation();

      const name = anchor.getAttribute("download") || "lqlq-file";

      if (href.startsWith("data:")) {
        const match = href.match(/^data:([^;,]*)(;base64)?,(.*)$/);
        if (match) {
          const mime = match[1] || "application/octet-stream";
          const b64 = match[2]
            ? match[3]
            : btoa(unescape(decodeURIComponent(match[3])));
          safeCall(() => native.saveBase64File(name, mime, b64));
        }
        return;
      }

      fetch(href)
        .then(response => response.blob())
        .then(blob =>
          base64FromBlob(blob).then(b64 =>
            native.saveBase64File(name, blob.type || "application/octet-stream", b64)
          )
        )
        .catch(() => safeCall(() => native.showToast("Không lưu được tệp.")));
    },
    true
  );
})();
