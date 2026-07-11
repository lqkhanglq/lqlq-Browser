/*
 * android-glue.js — lqlq Browser v0.25.0 APK
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
  // 0b. Bật/tắt lớp ẩn quảng cáo DOM (v0.23.28) — MẶC ĐỊNH TẮT cho mượt.
  //     Lớp chặn domain/redirect (native, luôn bật) KHÔNG bị ảnh hưởng bởi
  //     công tắc này — đây chỉ tắt phần quét/ẩn phần tử quảng cáo trong DOM
  //     (MutationObserver), vốn có thể tốn CPU trên trang nhiều quảng cáo
  //     động.
  // ==================================================================
  const ADBLOCK_DOM_KEY = "lqlqAdblockDomEnabledV1";

  window.lqlqGetAdblockDomEnabled = () => {
    try {
      return localStorage.getItem(ADBLOCK_DOM_KEY) === "1";
    } catch {
      return false;
    }
  };

  window.lqlqSetAdblockDomEnabled = enabled => {
    safeCall(() => {
      try {
        localStorage.setItem(ADBLOCK_DOM_KEY, enabled ? "1" : "0");
      } catch {}
      native.setAdblockDomEnabled?.(Boolean(enabled));
    });
  };

  // Báo cho native biết trạng thái ngay khi shell nạp xong, vì mặc định
  // native cũng để tắt — chỉ cần đồng bộ khi người dùng đã từng bật trước đó.
  safeCall(() => native.setAdblockDomEnabled?.(window.lqlqGetAdblockDomEnabled()));

  // ==================================================================
  // 0b2. Bật/tắt lớp chặn domain quảng cáo/redirect + chặn nhảy trang sang
  //      domain lạ — MẶC ĐỊNH BẬT. Đây là lớp chặn ở tầng mạng native
  //      (shouldOverrideUrlLoading/shouldInterceptRequest), khác với lớp ẩn
  //      quảng cáo DOM ở trên. Không xét gesture: một cú chạm thật có thể bị
  //      lớp phủ quảng cáo vô hình "đánh cắp", nên tắt hẳn khi người dùng
  //      chủ động chọn, không dựa vào gesture để tự nới lỏng.
  // ==================================================================
  const DOMAIN_GUARD_KEY = "lqlqDomainGuardEnabledV1";

  window.lqlqGetDomainGuardEnabled = () => {
    try {
      const raw = localStorage.getItem(DOMAIN_GUARD_KEY);
      return raw === null ? true : raw === "1";
    } catch {
      return true;
    }
  };

  window.lqlqSetDomainGuardEnabled = enabled => {
    safeCall(() => {
      try {
        localStorage.setItem(DOMAIN_GUARD_KEY, enabled ? "1" : "0");
      } catch {}
      native.setDomainGuardEnabled?.(Boolean(enabled));
    });
  };

  safeCall(() => native.setDomainGuardEnabled?.(window.lqlqGetDomainGuardEnabled()));

  // ==================================================================
  // 0b3. Tự quay lại (Back) khi trang chính trả mã lỗi HTTP ≥ 400 — MẶC ĐỊNH
  //      TẮT. Lỗi 503/502 thường chỉ là server đích quá tải/bảo trì tạm
  //      thời, không phải quảng cáo — tự bấm Back trong trường hợp đó gây
  //      khó chịu hơn là hữu ích.
  // ==================================================================
  const BAD_LOAD_RECOVERY_KEY = "lqlqBadLoadRecoveryEnabledV1";

  window.lqlqGetBadLoadRecoveryEnabled = () => {
    try {
      return localStorage.getItem(BAD_LOAD_RECOVERY_KEY) === "1";
    } catch {
      return false;
    }
  };

  window.lqlqSetBadLoadRecoveryEnabled = enabled => {
    safeCall(() => {
      try {
        localStorage.setItem(BAD_LOAD_RECOVERY_KEY, enabled ? "1" : "0");
      } catch {}
      native.setBadLoadRecoveryEnabled?.(Boolean(enabled));
    });
  };

  safeCall(() => native.setBadLoadRecoveryEnabled?.(window.lqlqGetBadLoadRecoveryEnabled()));

  // ==================================================================
  // 0b4. Thông báo (Toast) khi chặn quảng cáo/chuyển hướng lạ hoặc tự quay
  //      lại do trang lỗi — MẶC ĐỊNH BẬT. Không ảnh hưởng tới việc CÓ chặn
  //      hay không, chỉ ẩn/hiện dòng chữ thông báo.
  // ==================================================================
  const BLOCK_NOTICE_TOASTS_KEY = "lqlqBlockNoticeToastsEnabledV1";

  window.lqlqGetBlockNoticeToastsEnabled = () => {
    try {
      const raw = localStorage.getItem(BLOCK_NOTICE_TOASTS_KEY);
      return raw === null ? true : raw === "1";
    } catch {
      return true;
    }
  };

  window.lqlqSetBlockNoticeToastsEnabled = enabled => {
    safeCall(() => {
      try {
        localStorage.setItem(BLOCK_NOTICE_TOASTS_KEY, enabled ? "1" : "0");
      } catch {}
      native.setBlockNoticeToastsEnabled?.(Boolean(enabled));
    });
  };

  safeCall(() => native.setBlockNoticeToastsEnabled?.(window.lqlqGetBlockNoticeToastsEnabled()));

  // Android Chrome không hiện Tiện ích/Task Manager/DevTools trong menu di
  // động. Tab groups chưa có model native nên ẩn thay vì để nút bấm không làm
  // gì — mọi mục còn hiển thị đều phải có hành vi thật.
  ["extensions", "task-manager", "developer-tools", "tab-groups"].forEach(action => {
    document.querySelectorAll(`[data-action="${action}"]`).forEach(element => element.remove());
  });

  // ==================================================================
  // 0c. Đồng bộ chủ đề (v09-theme.js) sang bộ chuyển thẻ native, để màu
  //     lưới thẻ luôn khớp với chế độ sáng/tối + màu chủ đạo người dùng
  //     đã chọn trong Cài đặt, thay vì cố định một bảng màu.
  // ==================================================================

  function reportAppearance() {
    safeCall(() => {
      if (typeof native.setAppearance !== "function") return;
      const root = document.documentElement;
      const theme = root.dataset.theme === "dark" ? "dark" : "light";
      const accent = root.dataset.accent || "emerald";
      native.setAppearance(theme, accent);
    });
  }

  document.addEventListener("shield:appearance-change", reportAppearance);
  reportAppearance();

  // ==================================================================
  // 1. Hệ thống thẻ native — Android là nguồn dữ liệu duy nhất
  // ==================================================================

  function currentMode() {
    try {
      return store.mode === "private" || store.mode === "incognito"
        ? store.mode
        : "normal";
    } catch {
      return "normal";
    }
  }

  function applyNativeTabState(rawState) {
    safeCall(() => {
      const state = typeof rawState === "string" ? JSON.parse(rawState) : rawState;
      if (!state || !Array.isArray(state.tabs)) return;
      const mode = state.mode === "private" || state.mode === "incognito"
        ? state.mode
        : "normal";
      const profile = store[mode];
      profile.tabs = state.tabs.map(tab => ({
        id: String(tab.id),
        title: String(tab.title || "Thẻ mới"),
        url: String(tab.url || ""),
        loading: Boolean(tab.loading)
      }));
      if (!profile.tabs.length) {
        profile.tabs = [{ id: "native-empty", title: "Thẻ mới", url: "" }];
      }
      profile.activeTabId = String(state.activeTabId || profile.tabs[0].id);
      window.saveState?.();
      if (currentMode() === mode) {
        window.render?.();
        window.ShieldMobileChrome?.update?.();
      }
      window.dispatchEvent(new CustomEvent("lqlq-tab-state-changed", { detail: state }));
    });
  }

  window.lqlqApplyNativeTabState = applyNativeTabState;

  // Đồng bộ ngay khi shell nạp. Mảng tabs trong localStorage chỉ còn là bản
  // sao để các chức năng giao diện cũ đọc, không còn quyền tạo/đóng/chọn thẻ.
  safeCall(() => applyNativeTabState(native.getTabState()));

  const origNavigate = window.navigate;
  if (typeof origNavigate === "function") {
    window.navigate = function (url, addHistory = true) {
      origNavigate(url, addHistory);
      safeCall(() => {
        const tab = window.activeTab?.();
        if (tab && /^https?:\/\//i.test(tab.url)) {
          safeCall(() => window.ShieldMedia?.pause?.());
          native.openPage(String(tab.id), tab.url);
        }
      });
    };
  }

  const origNewTab = window.newTab;
  window.newTab = function (url = "") {
    safeCall(() => {
      const finalUrl = url ? window.normalizeUrl?.(url) || String(url) : "";
      native.createTab(finalUrl, currentMode());
      // createTab trả về sau khi UI thread đã hoàn tất; đọc lại snapshot native
      // để thanh đếm/địa chỉ cập nhật ngay cả trước callback evaluateJavascript.
      applyNativeTabState(native.getTabState());
    });
  };

  const origSwitchTab = window.switchTab;
  window.switchTab = function (id) {
    // Cập nhật bản sao giao diện ngay để thao tác có phản hồi tức thì; Android
    // vẫn là nơi quyết định cuối cùng và sẽ gửi lại onNativeTabsChanged().
    if (typeof origSwitchTab === "function") origSwitchTab(id);
    safeCall(() => native.selectTab(String(id), currentMode()));
  };

  window.closeTab = function (id) {
    safeCall(() => native.closeTab(String(id), currentMode()));
  };

  // Chế độ riêng tư/ẩn danh mỗi cái dùng một phiên tab native riêng. Thay vì
  // bọc từng hàm chuyển mode riêng lẻ (enterPrivateMode/enterIncognito/
  // exitIncognito/nút "Quay lại chế độ thường"...), bọc render() — hàm DUY
  // NHẤT luôn được gọi lại ngay sau mọi lần đổi store.mode — và so sánh mode
  // hiện tại với lần trước để báo cho Android biết ngay khi đổi.
  let lastKnownMode = currentMode();
  const origRender = window.render;
  if (typeof origRender === "function") {
    window.render = function () {
      origRender();
      const mode = currentMode();
      if (mode !== lastKnownMode) {
        lastKnownMode = mode;
        safeCall(() => native.setTabMode(mode));
      }
    };
  }

  // ==================================================================
  // 2. Danh sách lớp phủ (menu, panel, modal) của giao diện
  //    → dùng cho việc ẩn tạm WebView trang web và cho nút Back
  // ==================================================================

  const OVERLAYS = [
    { id: "privateModal", kind: "hidden", closeBtn: null },
    { id: "warpModal", kind: "hidden", closeBtn: "closeWarpModal" },
    { id: "videoPanel", kind: "hidden", closeBtn: "closeVideoPanel" },
    { id: "mediaCenterOverlay", kind: "hidden", closeBtn: "mediaClosePanelBtn" },
    { id: "imageEditorShell", kind: "aria", closeBtn: "closeImageEditor" },
    { id: "chapterClipperPanel", kind: "aria", closeBtn: "chapterClipperClose" },
    { id: "storyReaderPanel", kind: "aria", closeBtn: "closeStoryReader" },
    { id: "securityCenterOverlay", kind: "hidden", closeBtn: "closeSecurityCenter" },
    // Back Android ở màn chính của Hồ sơ Phiêu lưu phải lùi về Menu chức
    // năng (giống nút "‹"), KHÔNG đóng thẳng ra trang web như nút "×".
    { id: "adventureProfileOverlay", kind: "hidden", closeBtn: "adventureProfileBack" },
    { id: "chromeMenu", kind: "hidden", closeBtn: null },
    { id: "toolsMenu", kind: "hidden", closeBtn: null },
    { id: "sideDrawer", kind: "open", closeBtn: "closeDrawer" },
    { id: "videoAutomationWorkspaceRoot", kind: "hidden", closeBtn: "videoWorkspaceCloseBtn" }
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
    if (spec.id === "privateModal" && typeof window.closePrivateModal === "function") {
      window.closePrivateModal();
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
          // Hồ sơ Phiêu lưu có màn con riêng (Đồ Giám/Túi Hành Trang/Cửa
          // Hàng/Thẻ Khoe) bên trong overlay của nó. Cho JS tự lùi về
          // dashboard trước — chỉ đóng hẳn overlay khi JS báo đã ở cấp
          // ngoài cùng rồi (không còn màn con nào để lùi về nữa).
          if (
            spec.id === "adventureProfileOverlay" &&
            typeof window.LqlqAdventureUI?.handleBackPress === "function" &&
            window.LqlqAdventureUI.handleBackPress()
          ) {
            closed = true;
          } else {
            closeOverlay(spec);
            closed = true;
          }
        }
      });
      setTimeout(reportOverlayState, 120);
      return closed;
    },

    onNativeTabsChanged(info) {
      applyNativeTabState(info);
    },

    onNativePage(info) {
      safeCall(() => {
        const profile = window.currentProfile?.();
        if (!profile) return;
        const tab = profile.tabs.find(item => String(item.id) === String(info.tabId));
        if (!tab) return;

        // Lưu ý (v0.23.24 — Vấn đề 3): KHÔNG dùng "tab.url !== info.url" để
        // xác định "đã đổi trang" — doUpdateVisitedHistory() gọi onNativePage
        // với loading=true và URL ĐÍCH CUỐI CÙNG *trước khi* onPageFinished()
        // gọi lại với loading=false cùng URL đó. Vì tab.url đã được gán ở lần
        // gọi loading=true, đến lần loading=false thì tab.url === info.url
        // sẵn rồi → "changed" luôn false → Nhật ký KHÔNG BAO GIỜ được ghi.
        // Sửa: theo dõi URL đã ghi lịch sử gần nhất riêng (_lastHistoryUrl),
        // chỉ so sánh với biến đó để quyết định có ghi nhật ký hay không.
        tab.url = info.url || tab.url;
        if (info.title) tab.title = info.title;

        if (!info.loading && /^https?:\/\//i.test(info.url) && tab._lastHistoryUrl !== info.url) {
          tab._lastHistoryUrl = info.url;
          profile.history.unshift({
            title: info.title || tab.title,
            url: info.url,
            // Favicon nằm trong cache native theo URL; không ghi base64
            // vào lịch sử/localStorage.
            favicon: "",
            time: new Date().toLocaleString("vi-VN")
          });
          profile.history = profile.history.slice(0, 200);
        }

        // URL loading tạm thời không cần ghi localStorage; native TabStore
        // mới là nguồn dữ liệu thật. Chỉ chốt trạng thái khi trang tải xong.
        if (!info.loading) window.saveState?.();
        if (String(profile.activeTabId) === String(info.tabId)) {
          const address = $("addressInput");
          if (address && document.activeElement !== address) {
            address.value = info.url;
          }
          // Không dựng lại toàn bộ dải tab desktop trong mỗi callback tải
          // trang; chỉ cập nhật phần trang/toolbar đang nhìn thấy.
          window.renderPage?.();
          window.ShieldMobileChrome?.update?.();
          if (!info.loading) {
            window.dispatchEvent(new CustomEvent("lqlq-active-page-changed", { detail: info }));
          }
        }
      });
    },

    // Việc (v0.23.24 — Vấn đề 2): native báo Uri thật của tệp .mht vừa lưu
    // ngoại tuyến — gắn Uri đó vào mục bookmark tương ứng (đã được thêm bởi
    // saveCurrent() ngay khi bấm "Lưu trang ngoại tuyến") để "Trang đã lưu"
    // biết đây là 1 trang có bản offline, và mở lại ĐÚNG tệp đó thay vì chỉ
    // điều hướng online lại URL gốc.
    onOfflinePageSaved(info) {
      safeCall(() => {
        window.LqlqSavedPages?.attachOfflineUri?.(
          info.url,
          info.offlineUri,
          info.title,
          info.faviconData || ""
        );
      });
    },

    // Việc "Lấy chương đang mở" (v0.23.28): kết quả trích xuất BẤT ĐỒNG BỘ
    // (native.extractChapterForReader(), không còn CountDownLatch chặn luồng
    // dễ timeout âm thầm) — phát sự kiện để reader.js tự lắng nghe, không
    // ràng buộc trực tiếp 2 file với nhau.
    onChapterExtracted(json) {
      safeCall(() => {
        window.dispatchEvent(new CustomEvent("lqlq-chapter-extracted", { detail: json }));
      });
    },

    onNativePageVisibility(visible) {
      document.body.classList.toggle("lqlq-native-page-open", Boolean(visible));
      if (visible) {
        // Chỉ tạm dừng backend WebView để tránh chồng tiếng. Backend native
        // (MP3/MP4 ngoài nền) tự xử lý audio focus và phải tiếp tục phát.
        safeCall(() => window.ShieldMedia?.pause?.());
      }
    },

    // Lệnh từ thanh thông báo → Đọc truyện TXT
    // Việc "nút dừng/phát trên thông báo không có tác dụng" (v0.23.30): kể từ
    // khi gộp 3 nút Đọc/Tạm dừng/Dừng thành 1 nút duy nhất
    // (readerPlayPauseBtn, xem reader.js v0.23.25), các id cũ readerPlayBtn/
    // readerPauseBtn/readerStopBtn không còn tồn tại trong index.html nữa —
    // $(...) trả về null, ?.click() không làm gì cả, âm thầm không lỗi.
    readerCmd(cmd) {
      safeCall(() => {
        if (cmd === "toggle") {
          $("readerPlayPauseBtn")?.click();
        } else if (cmd === "next") {
          $("readerNextBtn")?.click();
        } else if (cmd === "prev") {
          $("readerPrevBtn")?.click();
        } else if (cmd === "stop") {
          // Không còn nút "Dừng" riêng — nếu đang đọc/tạm dừng thì bấm nút
          // gộp để tạm dừng (tương đương "dừng" theo thiết kế mới).
          const state = $("readerStatus")?.dataset.state;
          if (state === "speaking") {
            $("readerPlayPauseBtn")?.click();
          }
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
          // ShieldMedia.toggle() xử lý thống nhất cho cả YouTube (iframe,
          // không có thẻ <audio>/<video> để dò) lẫn media trực tiếp.
          if (typeof window.ShieldMedia?.toggle === "function") {
            window.ShieldMedia.toggle();
          } else {
            const player = mainAudiblePlayer();
            if (!player) return;
            if (player.paused) player.play().catch(() => {});
            else player.pause();
          }
          setTimeout(sendMediaState, 100);
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
      // Ưu tiên đọc trạng thái thật từ window.ShieldMedia (v13-media.js) —
      // đây là nguồn ĐÚNG vì bao quát cả YouTube (chạy trong iframe, KHÔNG
      // có thẻ <audio>/<video> để quét DOM được) lẫn media trực tiếp. Quét
      // DOM chỉ dùng làm dự phòng nếu vì lý do gì đó module media chưa nạp.
      const shieldMedia = window.ShieldMedia;
      let active;
      let playing;
      let title;
      let text;
      let backend = "web";

      if (shieldMedia && shieldMedia.state && shieldMedia.state.type !== "none") {
        active = true;
        playing = Boolean(shieldMedia.state.isPlaying);
        title = shieldMedia.state.title || "Nhạc và video nền";
        text = shieldMedia.state.subtitle || "";
        backend = shieldMedia.state.backend || "web";
      } else {
        const players = allAudiblePlayers();
        playing = players.some(player => !player.paused && !player.ended);
        const started = players.some(
          player => player.currentTime > 0 && !player.ended
        );
        active = playing || started;
        title = ($("mediaNowTitle")?.textContent || "Nhạc và video nền").trim();
        text = ($("mediaNowSubtitle")?.textContent || "").trim().slice(0, 160);
      }

      const payload = JSON.stringify({
        active,
        playing,
        title,
        text: String(text).slice(0, 160),
        backend
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
