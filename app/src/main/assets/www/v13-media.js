(() => {
  /**
   * v0.26.0 — hai backend rõ ràng:
   * - native: MP3/MP4 trong máy và URL media trực tiếp, do Media3 service sở hữu.
   * - web: YouTube iframe chính thức và fallback HTMLMediaElement ngoài Android.
   *
   * Trạng thái native chỉ được nhận từ Android. WebView không còn cố mở
   * content:// bằng URL.createObjectURL(), nguyên nhân khiến file local lúc
   * chạy lúc không và tắt ngay khi Activity ra nền.
   */
  const refs = {
    toolbarBtn: document.getElementById("mediaToolbarBtn"),
    liveIndicator: document.getElementById("mediaLiveIndicator"),
    overlay: document.getElementById("mediaCenterOverlay"),
    closePanel: document.getElementById("mediaClosePanelBtn"),
    minimize: document.getElementById("mediaMinimizeBtn"),
    urlInput: document.getElementById("mediaUrlInput"),
    openUrl: document.getElementById("mediaOpenUrlBtn"),
    chooseFile: document.getElementById("mediaChooseFileBtn"),
    fileInput: document.getElementById("mediaFileInput"),
    sourceBadge: document.getElementById("mediaSourceBadge"),
    stage: document.getElementById("mediaStage"),
    empty: document.getElementById("mediaEmptyState"),
    youtube: document.getElementById("mediaYoutubeFrame"),
    htmlPlayer: document.getElementById("mediaHtmlPlayer"),
    nowIcon: document.getElementById("mediaNowIcon"),
    nowTitle: document.getElementById("mediaNowTitle"),
    nowSubtitle: document.getElementById("mediaNowSubtitle"),
    playingPill: document.getElementById("mediaPlayingPill"),
    minimizeMode: document.getElementById("mediaMiniModeBtn"),
    audioMode: document.getElementById("mediaAudioModeBtn"),
    pip: document.getElementById("mediaPipBtn"),
    stop: document.getElementById("mediaStopBtn"),
    volume: document.getElementById("mediaVolumeRange"),
    volumeLabel: document.getElementById("mediaVolumeLabel")
  };

  const emptyIcon = refs.empty?.querySelector("span");
  const emptyTitle = refs.empty?.querySelector("b");
  const emptyText = refs.empty?.querySelector("small");

  const state = {
    backend: "none", // none | native | web
    type: "none",    // none | audio | video | youtube
    title: "",
    subtitle: "",
    source: "",
    youtubeId: "",
    objectUrl: "",
    audioOnly: false,
    isPlaying: false,
    isLoading: false,
    volume: 1,
    toolEnabled: false
  };

  let lastNativeError = "";

  function show(element) {
    element?.classList.remove("hidden");
  }

  function hide(element) {
    element?.classList.add("hidden");
  }

  function notify(message) {
    if (message && typeof toast === "function") toast(message);
  }

  function nativeBridge() {
    return window.LqlqAndroid || null;
  }

  function hasNativePlayer() {
    const bridge = nativeBridge();
    return Boolean(
      bridge
      && typeof bridge.openNativeMediaFile === "function"
      && typeof bridge.playNativeMediaUrl === "function"
      && typeof bridge.nativeMediaCommand === "function"
    );
  }

  function callNative(name, ...args) {
    try {
      const fn = nativeBridge()?.[name];
      if (typeof fn === "function") return fn.apply(nativeBridge(), args);
    } catch (error) {
      console.warn(`Native media call failed: ${name}`, error);
    }
    return undefined;
  }

  function youtubeIdFromUrl(value) {
    const raw = String(value || "").trim();
    if (/^[\w-]{11}$/.test(raw)) return raw;

    try {
      const url = new URL(raw);
      const host = url.hostname.replace(/^www\./, "");
      if (host === "youtu.be") {
        return url.pathname.split("/").filter(Boolean)[0] || "";
      }
      if (["youtube.com", "m.youtube.com", "music.youtube.com"].includes(host)) {
        if (url.pathname === "/watch") return url.searchParams.get("v") || "";
        const parts = url.pathname.split("/").filter(Boolean);
        const marker = parts.findIndex(part => ["embed", "shorts", "live"].includes(part));
        if (marker >= 0) return parts[marker + 1] || "";
      }
    } catch {}
    return "";
  }

  function youtubeEmbedUrl(id, autoplay = true) {
    const params = new URLSearchParams({
      autoplay: autoplay ? "1" : "0",
      playsinline: "1",
      enablejsapi: "1",
      rel: "0",
      modestbranding: "1"
    });
    return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(id)}?${params}`;
  }

  function isDirectMediaUrl(value) {
    return /\.(mp3|m4a|m4b|aac|ogg|oga|wav|flac|mp4|m4v|webm|mov|mkv|3gp)(?:[?#].*)?$/i
      .test(String(value || ""));
  }

  function directMediaKind(value, mime = "") {
    if (/^audio\//i.test(mime)) return "audio";
    if (/^video\//i.test(mime)) return "video";
    if (/\.(mp3|m4a|m4b|aac|ogg|oga|wav|flac)(?:[?#].*)?$/i.test(value)) return "audio";
    return "video";
  }

  function inferMime(value) {
    const clean = String(value || "").split(/[?#]/)[0].toLowerCase();
    if (clean.endsWith(".mp3")) return "audio/mpeg";
    if (clean.endsWith(".m4a") || clean.endsWith(".m4b")) return "audio/mp4";
    if (clean.endsWith(".aac")) return "audio/aac";
    if (clean.endsWith(".ogg") || clean.endsWith(".oga")) return "audio/ogg";
    if (clean.endsWith(".wav")) return "audio/wav";
    if (clean.endsWith(".flac")) return "audio/flac";
    if (clean.endsWith(".mp4") || clean.endsWith(".m4v")) return "video/mp4";
    if (clean.endsWith(".webm")) return "video/webm";
    if (clean.endsWith(".mkv")) return "video/x-matroska";
    if (clean.endsWith(".mov")) return "video/quicktime";
    if (clean.endsWith(".3gp")) return "video/3gpp";
    return "";
  }

  function clearBrowserMediaSession() {
    if (!("mediaSession" in navigator)) return;
    try { navigator.mediaSession.metadata = null; } catch {}
  }

  function setMetadata(title, subtitle, icon = "♫", publishWebSession = true) {
    state.title = title || "Nội dung đang phát";
    state.subtitle = subtitle || "Trình phát nền";
    refs.nowTitle.textContent = state.title;
    refs.nowSubtitle.textContent = state.subtitle;
    refs.nowIcon.textContent = icon;

    if (publishWebSession && "mediaSession" in navigator) {
      try {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: state.title,
          artist: "lqlq Browser",
          album: state.subtitle
        });
      } catch {}
    }
  }

  function setPlaying(playing) {
    state.isPlaying = Boolean(playing);
    const active = state.type !== "none";
    refs.playingPill.classList.toggle("hidden", !active);
    refs.liveIndicator.classList.toggle("hidden", !active);
    refs.playingPill.textContent = state.isLoading
      ? "Đang tải"
      : state.isPlaying ? "Đang phát" : "Đã tạm dừng";
  }

  function setEmptyCopy(icon, title, text) {
    if (emptyIcon) emptyIcon.textContent = icon;
    if (emptyTitle) emptyTitle.textContent = title;
    if (emptyText) emptyText.textContent = text;
  }

  function clearWebFrames() {
    try {
      refs.youtube.src = "";
      refs.htmlPlayer.pause();
      refs.htmlPlayer.removeAttribute("src");
      refs.htmlPlayer.load();
    } catch {}

    hide(refs.youtube);
    hide(refs.htmlPlayer);

    if (state.objectUrl) {
      try { URL.revokeObjectURL(state.objectUrl); } catch {}
      state.objectUrl = "";
    }
  }

  function syncVolume(value, reportNative = false) {
    const normalized = Math.max(0, Math.min(1, Number(value)));
    state.volume = Number.isFinite(normalized) ? normalized : 1;

    if (state.backend !== "native") {
      try { refs.htmlPlayer.volume = state.volume; } catch {}
    }

    refs.volume.value = String(Math.round(state.volume * 100));
    refs.volumeLabel.textContent = `${Math.round(state.volume * 100)}%`;

    if (reportNative && state.backend === "native") {
      callNative("setNativeMediaVolume", state.volume);
    }
  }

  function resetUi({ hidePanel = false } = {}) {
    clearWebFrames();
    state.backend = "none";
    state.type = "none";
    state.title = "";
    state.subtitle = "";
    state.source = "";
    state.youtubeId = "";
    state.audioOnly = false;
    state.isPlaying = false;
    state.isLoading = false;

    refs.stage.classList.remove("audio-only");
    show(refs.empty);
    hide(refs.playingPill);
    hide(refs.liveIndicator);
    if (hidePanel) hide(refs.overlay);

    refs.sourceBadge.textContent = "Chưa có nguồn";
    refs.nowTitle.textContent = "Chưa phát nội dung";
    refs.nowSubtitle.textContent = "MP3/MP4 có thể tiếp tục phát khi tắt màn hình hoặc chuyển ứng dụng.";
    refs.nowIcon.textContent = "♫";
    setEmptyCopy(
      "♫",
      "Chưa có nội dung đang phát",
      "File trong máy và liên kết media trực tiếp dùng trình phát Android; YouTube dùng trình nhúng chính thức."
    );
    clearBrowserMediaSession();
  }

  function stopNativeSilently() {
    if (hasNativePlayer()) callNative("nativeMediaCommand", "stop");
  }

  // YouTube iframe đôi khi tự câm do autoplay policy. Thử unmute sau khi nạp.
  function forceYoutubeUnmute() {
    let attempts = 0;
    const send = () => {
      try {
        refs.youtube.contentWindow?.postMessage(
          JSON.stringify({ event: "command", func: "unMute", args: [] }),
          "*"
        );
        refs.youtube.contentWindow?.postMessage(
          JSON.stringify({ event: "command", func: "setVolume", args: [100] }),
          "*"
        );
      } catch {}
    };
    send();
    const timer = setInterval(() => {
      send();
      attempts += 1;
      if (attempts >= 8) clearInterval(timer);
    }, 350);
  }

  function loadYoutube(id) {
    if (!id) {
      notify("Không nhận diện được liên kết YouTube.");
      return;
    }

    stopNativeSilently();
    clearWebFrames();
    state.backend = "web";
    state.type = "youtube";
    state.youtubeId = id;
    state.source = id;
    state.audioOnly = false;
    state.isLoading = false;

    refs.youtube.src = youtubeEmbedUrl(id, true);
    forceYoutubeUnmute();
    show(refs.youtube);
    hide(refs.empty);
    refs.stage.classList.remove("audio-only");
    refs.sourceBadge.textContent = "YouTube";
    setMetadata(`YouTube · ${id}`, "Phát bằng trình nhúng chính thức", "▶", true);
    setPlaying(true);
  }

  /** Fallback dành cho trình duyệt desktop; Android ưu tiên Media3 native. */
  function loadDirectSource(source, title, mime = "") {
    stopNativeSilently();
    clearWebFrames();

    const kind = directMediaKind(source, mime);
    state.backend = "web";
    state.type = kind;
    state.source = source;
    state.youtubeId = "";
    state.audioOnly = kind === "audio";
    state.isLoading = true;

    refs.htmlPlayer.src = source;
    refs.htmlPlayer.muted = false;
    syncVolume(state.volume);
    show(refs.htmlPlayer);
    hide(refs.empty);
    refs.stage.classList.toggle("audio-only", state.audioOnly);
    refs.sourceBadge.textContent = kind === "audio" ? "Âm thanh" : "Video";

    setMetadata(
      title || (kind === "audio" ? "Tệp âm thanh" : "Video trực tiếp"),
      kind === "audio" ? "Đang phát bằng WebView" : "Video trực tiếp trong ứng dụng",
      kind === "audio" ? "♫" : "▶",
      true
    );
    setPlaying(false);

    const startPlayback = () => {
      state.isLoading = false;
      refs.htmlPlayer.play()?.catch(() => {
        setPlaying(false);
        notify("Hãy nhấn Play để bắt đầu nội dung này.");
      });
    };
    if (refs.htmlPlayer.readyState >= 1) startPlayback();
    else refs.htmlPlayer.addEventListener("loadedmetadata", startPlayback, { once: true });
  }

  function showNativePending(title, source, kind, sourceKind) {
    clearWebFrames();
    clearBrowserMediaSession();
    state.backend = "native";
    state.type = kind || "audio";
    state.source = source || "";
    state.youtubeId = "";
    state.audioOnly = true;
    state.isLoading = true;
    state.isPlaying = false;

    show(refs.empty);
    refs.stage.classList.add("audio-only");
    refs.sourceBadge.textContent = sourceKind === "file" ? "Tệp trong máy" : "Media trực tiếp";
    setEmptyCopy(
      kind === "video" ? "▶" : "♫",
      "Đang mở bằng trình phát Android…",
      "Bạn có thể chuyển ứng dụng hoặc khóa màn hình sau khi nội dung bắt đầu phát."
    );
    setMetadata(
      title || "Đang mở media",
      sourceKind === "file" ? "Tệp trong máy • đang chuẩn bị" : "Liên kết trực tiếp • đang chuẩn bị",
      kind === "video" ? "▶" : "♫",
      false
    );
    setPlaying(false);
  }

  function openUrl() {
    const value = String(refs.urlInput.value || "").trim();
    if (!value) {
      notify("Hãy dán liên kết YouTube hoặc media trực tiếp.");
      return;
    }

    const youtubeId = youtubeIdFromUrl(value);
    if (youtubeId) {
      loadYoutube(youtubeId);
      return;
    }

    if (!/^https?:\/\//i.test(value) || !isDirectMediaUrl(value)) {
      notify("Chỉ nhận YouTube hoặc liên kết trực tiếp .mp3/.mp4/.webm…");
      return;
    }

    let name = value.split("/").pop()?.split(/[?#]/)[0] || "Media trực tiếp";
    try { name = decodeURIComponent(name); } catch {}
    const mime = inferMime(value);
    const kind = directMediaKind(value, mime);

    if (hasNativePlayer()) {
      showNativePending(name, value, kind, "url");
      callNative("playNativeMediaUrl", value, name, mime);
    } else {
      loadDirectSource(value, name, mime);
    }
  }

  function openFileFallback(file) {
    if (!file) return;
    let isMedia = /^audio\/|^video\//i.test(file.type);
    if (!isMedia) {
      const ext = String(file.name || "").split(".").pop()?.toLowerCase() || "";
      isMedia = [
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "wav", "flac",
        "mp4", "webm", "mov", "mkv", "3gp", "m4v"
      ].includes(ext);
    }
    if (!isMedia) {
      notify("File đã chọn không phải âm thanh hoặc video.");
      return;
    }

    setTimeout(() => {
      const objectUrl = URL.createObjectURL(file);
      state.objectUrl = objectUrl;
      loadDirectSource(objectUrl, file.name, file.type);
    }, 0);
  }

  function requestNativeFile() {
    // Chỉ mở picker; giữ nguyên nguồn đang phát cho tới khi người dùng thật sự
    // chọn tệp mới. Hủy picker không làm mất YouTube/bài đang nghe.
    callNative("openNativeMediaFile");
  }

  function refreshNativeState() {
    if (!hasNativePlayer()) return;
    try {
      const raw = callNative("getNativeMediaState");
      if (raw) onNativeState(raw);
    } catch {}
  }

  function openCenter() {
    closeMenus?.();
    show(refs.overlay);
    refreshNativeState();
  }

  function closeCenter() {
    hide(refs.overlay);
  }

  function toggleAudioOnly() {
    if (state.type === "none") {
      notify("Chưa có nội dung đang phát.");
      return;
    }
    if (state.backend === "native") {
      notify("Trình phát Android đang dùng chế độ âm thanh nền. MP4 vẫn phát phần âm thanh khi khóa màn hình.");
      return;
    }
    if (state.type === "youtube") {
      notify("YouTube phải giữ khung video chính thức.");
      return;
    }

    state.audioOnly = !state.audioOnly;
    refs.stage.classList.toggle("audio-only", state.audioOnly);
    refs.nowSubtitle.textContent = state.audioOnly ? "Chế độ chỉ âm thanh" : "Video trực tiếp trong ứng dụng";
    notify(state.audioOnly ? "Đã chuyển sang chỉ âm thanh." : "Đã hiện lại video.");
  }

  async function openPictureInPicture() {
    if (state.type === "none") {
      notify("Chưa có video đang phát.");
      return;
    }
    if (state.backend === "native") {
      notify("Media native hiện phát nền bằng âm thanh; Picture-in-Picture chưa áp dụng cho nguồn này.");
      return;
    }
    if (state.type === "youtube") {
      notify("PiP hệ thống không điều khiển được khung YouTube này.");
      return;
    }
    if (state.type === "audio") {
      notify("Tệp âm thanh không cần Picture-in-Picture.");
      return;
    }

    const video = refs.htmlPlayer;
    if (!document.pictureInPictureEnabled || !video.requestPictureInPicture) {
      notify("Trình duyệt này không hỗ trợ Picture-in-Picture.");
      return;
    }
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await video.requestPictureInPicture();
    } catch {
      notify("Không thể mở Picture-in-Picture cho video này.");
    }
  }

  function stopAll({ silent = false } = {}) {
    if (state.backend === "native") callNative("nativeMediaCommand", "stop");
    resetUi({ hidePanel: true });
    if (!silent) notify("Đã dừng trình phát nền.");
  }

  function installMediaSession() {
    if (!("mediaSession" in navigator)) return;
    try {
      navigator.mediaSession.setActionHandler("play", async () => {
        if (state.backend === "native") callNative("nativeMediaCommand", "play");
        else if (state.type === "audio" || state.type === "video") await refs.htmlPlayer.play();
      });
      navigator.mediaSession.setActionHandler("pause", () => {
        if (state.backend === "native") callNative("nativeMediaCommand", "pause");
        else if (state.type === "audio" || state.type === "video") refs.htmlPlayer.pause();
      });
      navigator.mediaSession.setActionHandler("stop", () => stopAll());
    } catch {}
  }

  function setToolEnabled(enabled) {
    state.toolEnabled = Boolean(enabled);
    callNative("setMediaToolEnabled", state.toolEnabled);

    const menuBtn = document.querySelector(".media-menu-entry");
    if (menuBtn) {
      menuBtn.classList.toggle("media-tool-active", state.toolEnabled);
      const label = menuBtn.querySelector(".menu-copy b");
      if (label) {
        label.textContent = state.toolEnabled
          ? "Nhạc và video nền (đang bật)"
          : "Nhạc và video nền";
      }
    }
  }

  function onNativeState(raw) {
    let data = raw;
    if (typeof raw === "string") {
      try { data = JSON.parse(raw); } catch { return; }
    }
    if (!data || data.backend !== "native") return;

    const error = String(data.error || "");
    if (error && error !== lastNativeError) {
      lastNativeError = error;
      notify(`Không phát được media: ${error}`);
    }
    if (!error) lastNativeError = "";

    if (!data.active) {
      if (state.backend === "native") resetUi({ hidePanel: false });
      return;
    }

    clearWebFrames();
    clearBrowserMediaSession();
    state.backend = "native";
    state.type = data.kind === "video" ? "video" : "audio";
    state.title = String(data.title || "Nội dung đang phát");
    state.subtitle = String(data.text || "Phát nền Android");
    state.source = String(data.source || "");
    state.youtubeId = "";
    state.audioOnly = true;
    state.isLoading = Boolean(data.loading);
    state.isPlaying = Boolean(data.playing);

    const isLocal = state.source.startsWith("content://") || state.source.startsWith("file://");
    refs.sourceBadge.textContent = isLocal ? "Tệp trong máy" : "Media trực tiếp";
    refs.stage.classList.add("audio-only");
    show(refs.empty);
    hide(refs.youtube);
    hide(refs.htmlPlayer);
    setEmptyCopy(
      state.type === "video" ? "▶" : "♫",
      state.isLoading ? "Đang tải bằng Android…" : "Đang phát ngoài nền điện thoại",
      state.type === "video"
        ? "MP4 đang phát phần âm thanh nền; có thể chuyển ứng dụng hoặc khóa màn hình."
        : "Bạn có thể chuyển ứng dụng, khóa màn hình và điều khiển từ thông báo."
    );
    setMetadata(state.title, state.subtitle, state.type === "video" ? "▶" : "♫", false);
    syncVolume(Number(data.volume ?? state.volume), false);
    setPlaying(state.isPlaying);

    if (!state.toolEnabled) setToolEnabled(true);
  }

  const originalHandleAction = handleAction;
  handleAction = function handleMediaAction(action) {
    if (action === "background-media") {
      if (state.toolEnabled) {
        stopAll();
        setToolEnabled(false);
      } else {
        setToolEnabled(true);
        openCenter();
      }
      return;
    }
    originalHandleAction(action);
  };

  refs.toolbarBtn?.addEventListener("click", openCenter);
  refs.closePanel?.addEventListener("click", closeCenter);
  refs.minimize?.addEventListener("click", closeCenter);
  refs.openUrl?.addEventListener("click", openUrl);
  refs.urlInput?.addEventListener("keydown", event => {
    if (event.key === "Enter") openUrl();
  });

  refs.chooseFile?.addEventListener("click", event => {
    if (!hasNativePlayer()) return;
    // Ngăn label kích hoạt input WebView lần thứ hai.
    event.preventDefault();
    requestNativeFile();
  });

  refs.fileInput?.addEventListener("change", () => {
    openFileFallback(refs.fileInput.files?.[0]);
    refs.fileInput.value = "";
  });

  document.querySelectorAll("[data-media-example]").forEach(button => {
    button.addEventListener("click", () => {
      const type = button.dataset.mediaExample;
      if (type === "youtube") refs.urlInput.placeholder = "Ví dụ: https://www.youtube.com/watch?v=...";
      else if (type === "audio") refs.urlInput.placeholder = "Ví dụ: https://example.com/music.mp3";
      else refs.urlInput.placeholder = "Ví dụ: https://example.com/video.mp4";
      refs.urlInput.focus();
    });
  });

  refs.minimizeMode?.addEventListener("click", closeCenter);
  refs.audioMode?.addEventListener("click", toggleAudioOnly);
  refs.pip?.addEventListener("click", openPictureInPicture);
  refs.stop?.addEventListener("click", () => stopAll());

  refs.volume?.addEventListener("input", () => {
    syncVolume(Number(refs.volume.value) / 100, true);
  });

  refs.overlay?.addEventListener("click", event => {
    if (event.target === refs.overlay) closeCenter();
  });

  refs.htmlPlayer?.addEventListener("play", () => {
    state.isLoading = false;
    setPlaying(true);
  });
  refs.htmlPlayer?.addEventListener("pause", () => setPlaying(false));
  refs.htmlPlayer?.addEventListener("volumechange", () => {
    if (state.backend !== "native") syncVolume(refs.htmlPlayer.volume, false);
  });
  refs.htmlPlayer?.addEventListener("ended", () => setPlaying(false));
  refs.htmlPlayer?.addEventListener("error", () => {
    if (state.backend === "web") notify("Không phát được nguồn media này.");
  });

  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !refs.overlay.classList.contains("hidden")) closeCenter();
    if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "m") {
      event.preventDefault();
      openCenter();
    }
  });

  syncVolume(1);
  installMediaSession();
  resetUi();

  window.ShieldMedia = {
    open: openCenter,
    minimize: closeCenter,
    stop: stopAll,
    pause: () => {
      // Media native phải tiếp tục chạy khi người dùng mở một trang web khác.
      if (state.backend === "native") return;
      if (state.type === "youtube") {
        try {
          refs.youtube.contentWindow?.postMessage(
            JSON.stringify({ event: "command", func: "pauseVideo", args: [] }),
            "*"
          );
          setPlaying(false);
        } catch {}
        return;
      }
      try { refs.htmlPlayer.pause(); } catch {}
    },
    toggle: () => {
      if (state.backend === "native") {
        callNative("nativeMediaCommand", "toggle");
        return;
      }
      if (state.type === "youtube") {
        const cmd = state.isPlaying ? "pauseVideo" : "playVideo";
        try {
          refs.youtube.contentWindow?.postMessage(
            JSON.stringify({ event: "command", func: cmd, args: [] }),
            "*"
          );
        } catch {}
        setPlaying(!state.isPlaying);
        return;
      }
      if (state.type === "audio" || state.type === "video") {
        if (refs.htmlPlayer.paused) refs.htmlPlayer.play().catch(() => {});
        else refs.htmlPlayer.pause();
      }
    },
    loadYoutube,
    loadDirectSource,
    onNativeState,
    state
  };

  // Khi Activity/shell được tạo lại, nối vào MediaSession đang phát và khôi
  // phục giao diện mà không tải lại file hay URL.
  setTimeout(refreshNativeState, 0);
})();
