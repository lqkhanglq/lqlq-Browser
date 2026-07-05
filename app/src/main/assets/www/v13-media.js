(() => {
  /**
   * v0.27.0 — Media native + playlist thư mục + YouTube PiP.
   *
   * - File/URL media trực tiếp: Media3 service, notification Android, playlist native.
   * - YouTube: iframe chính thức, Picture-in-Picture ở cấp Activity.
   * - Không trích xuất luồng YouTube và không chuyển YouTube sang ExoPlayer.
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
    chooseFolder: document.getElementById("mediaChooseFolderBtn"),
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
    previous: document.getElementById("mediaPreviousBtn"),
    next: document.getElementById("mediaNextBtn"),
    repeatOne: document.getElementById("mediaRepeatOneBtn"),
    openYoutube: document.getElementById("mediaOpenYoutubeBtn"),
    stop: document.getElementById("mediaStopBtn"),
    volume: document.getElementById("mediaVolumeRange"),
    volumeLabel: document.getElementById("mediaVolumeLabel")
  };

  const emptyIcon = refs.empty?.querySelector("span");
  const emptyTitle = refs.empty?.querySelector("b");
  const emptyText = refs.empty?.querySelector("small");
  const REPEAT_KEY = "lqlqMediaRepeatOneV1";

  const state = {
    backend: "none", // none | native | web
    type: "none",    // none | audio | video | youtube
    title: "",
    subtitle: "",
    source: "",
    youtubeId: "",
    youtubePlaylistId: "",
    youtubeIndex: 0,
    youtubeUrl: "",
    objectUrl: "",
    audioOnly: false,
    isPlaying: false,
    isLoading: false,
    volume: 1,
    toolEnabled: false,
    repeatOne: localStorage.getItem(REPEAT_KEY) === "1",
    autoNext: true,
    itemCount: 0,
    itemIndex: -1,
    hasNext: false,
    hasPrevious: false,
    pipMode: false
  };

  let lastNativeError = "";
  let youtubeListenTimer = 0;
  let youtubeEndedTimer = 0;

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

  function callNative(name, ...args) {
    try {
      const bridge = nativeBridge();
      const fn = bridge?.[name];
      if (typeof fn === "function") return fn.apply(bridge, args);
    } catch (error) {
      console.warn(`Native media call failed: ${name}`, error);
    }
    return undefined;
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

  function parseYoutubeSource(value) {
    const raw = String(value || "").trim();
    if (/^[\w-]{11}$/.test(raw)) {
      return {
        id: raw,
        playlistId: "",
        index: 0,
        url: `https://www.youtube.com/watch?v=${encodeURIComponent(raw)}`
      };
    }

    try {
      const url = new URL(raw);
      const host = url.hostname.replace(/^www\./, "").toLowerCase();
      if (!["youtu.be", "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com"].includes(host)) {
        return null;
      }

      let id = "";
      const playlistId = url.searchParams.get("list") || "";
      const parsedIndex = Number.parseInt(url.searchParams.get("index") || "0", 10);
      const index = Number.isFinite(parsedIndex) ? Math.max(0, parsedIndex - 1) : 0;

      if (host === "youtu.be") {
        id = url.pathname.split("/").filter(Boolean)[0] || "";
      } else if (url.pathname === "/watch") {
        id = url.searchParams.get("v") || "";
      } else {
        const parts = url.pathname.split("/").filter(Boolean);
        const marker = parts.findIndex(part => ["embed", "shorts", "live"].includes(part));
        if (marker >= 0 && parts[marker + 1] && parts[marker + 1] !== "videoseries") {
          id = parts[marker + 1];
        }
      }

      if (!id && !playlistId) return null;
      return { id, playlistId, index, url: raw };
    } catch {
      return null;
    }
  }

  function youtubeEmbedUrl(source) {
    const params = new URLSearchParams({
      autoplay: "1",
      playsinline: "1",
      enablejsapi: "1",
      rel: "0",
      modestbranding: "1",
      origin: window.location.origin
    });

    if (source.playlistId) {
      params.set("listType", "playlist");
      params.set("list", source.playlistId);
      if (source.index > 0) params.set("index", String(source.index));
    }
    if (state.repeatOne && source.id && !source.playlistId) {
      params.set("loop", "1");
      params.set("playlist", source.id);
    }

    const path = source.id
      ? `/embed/${encodeURIComponent(source.id)}`
      : "/embed/videoseries";
    return `https://www.youtube-nocookie.com${path}?${params}`;
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

  function notifyNativeYoutubeState() {
    callNative(
      "setYoutubePlaybackState",
      state.type === "youtube",
      state.type === "youtube" && state.isPlaying,
      state.youtubeUrl || state.source || ""
    );
  }

  function setPlaying(playing) {
    state.isPlaying = Boolean(playing);
    const active = state.type !== "none";
    refs.playingPill?.classList.toggle("hidden", !active);
    refs.liveIndicator?.classList.toggle("hidden", !active);
    if (refs.playingPill) {
      refs.playingPill.textContent = state.isLoading
        ? "Đang tải"
        : state.isPlaying ? "Đang phát" : "Đã tạm dừng";
    }
    if (state.type === "youtube") notifyNativeYoutubeState();
  }

  function setEmptyCopy(icon, title, text) {
    if (emptyIcon) emptyIcon.textContent = icon;
    if (emptyTitle) emptyTitle.textContent = title;
    if (emptyText) emptyText.textContent = text;
  }

  function stopYoutubeListener() {
    if (youtubeListenTimer) clearInterval(youtubeListenTimer);
    youtubeListenTimer = 0;
    if (youtubeEndedTimer) clearTimeout(youtubeEndedTimer);
    youtubeEndedTimer = 0;
  }

  function sendYoutubeCommand(func, args = []) {
    try {
      refs.youtube?.contentWindow?.postMessage(
        JSON.stringify({ event: "command", func, args }),
        "*"
      );
    } catch {}
  }

  function registerYoutubeListener() {
    stopYoutubeListener();
    let attempts = 0;
    const send = () => {
      try {
        refs.youtube?.contentWindow?.postMessage(
          JSON.stringify({ event: "listening", id: "mediaYoutubeFrame" }),
          "*"
        );
      } catch {}
      attempts += 1;
      if (attempts >= 16 && youtubeListenTimer) {
        clearInterval(youtubeListenTimer);
        youtubeListenTimer = 0;
      }
    };
    send();
    youtubeListenTimer = setInterval(send, 500);
  }

  function forceYoutubeUnmute() {
    let attempts = 0;
    let timer = 0;
    const send = () => {
      sendYoutubeCommand("unMute");
      sendYoutubeCommand("setVolume", [Math.round(state.volume * 100)]);
      attempts += 1;
      if (attempts >= 8 && timer) {
        clearInterval(timer);
        timer = 0;
      }
    };
    send();
    timer = setInterval(send, 350);
  }

  function clearWebFrames({ reportYoutube = true } = {}) {
    const wasYoutube = state.type === "youtube";
    stopYoutubeListener();
    try {
      if (wasYoutube) sendYoutubeCommand("stopVideo");
      if (refs.youtube) refs.youtube.src = "";
      refs.htmlPlayer?.pause();
      refs.htmlPlayer?.removeAttribute("src");
      refs.htmlPlayer?.load();
    } catch {}

    hide(refs.youtube);
    hide(refs.htmlPlayer);

    if (state.objectUrl) {
      try { URL.revokeObjectURL(state.objectUrl); } catch {}
      state.objectUrl = "";
    }
    if (wasYoutube && reportYoutube) {
      callNative("setYoutubePlaybackState", false, false, "");
    }
  }

  function syncVolume(value, reportNative = false) {
    const normalized = Math.max(0, Math.min(1, Number(value)));
    state.volume = Number.isFinite(normalized) ? normalized : 1;

    if (state.backend !== "native") {
      try { refs.htmlPlayer.volume = state.volume; } catch {}
      if (state.type === "youtube") {
        sendYoutubeCommand("setVolume", [Math.round(state.volume * 100)]);
      }
    }

    if (refs.volume) refs.volume.value = String(Math.round(state.volume * 100));
    if (refs.volumeLabel) refs.volumeLabel.textContent = `${Math.round(state.volume * 100)}%`;

    if (reportNative && state.backend === "native") {
      callNative("setNativeMediaVolume", state.volume);
    }
  }

  function setRepeatUi() {
    refs.repeatOne?.classList.toggle("is-active", state.repeatOne);
    refs.repeatOne?.setAttribute("aria-pressed", state.repeatOne ? "true" : "false");
    const copy = refs.repeatOne?.querySelector("small");
    if (copy) {
      copy.textContent = state.repeatOne
        ? "Đang bật — bài hiện tại sẽ phát lại"
        : "Phát lại đúng nội dung hiện tại";
    }
  }

  function setNavigationUi() {
    const youtube = state.type === "youtube";
    const native = state.backend === "native";
    refs.previous.disabled = state.type === "none";
    refs.next.disabled = state.type === "none" || (native && !state.hasNext);
    refs.openYoutube?.classList.toggle("hidden", !youtube);
    refs.pip.disabled = state.type !== "youtube" && state.type !== "video";
  }

  function resetUi({ hidePanel = false } = {}) {
    clearWebFrames();
    state.pipMode = false;
    document.documentElement.classList.remove("lqlq-youtube-pip");
    state.backend = "none";
    state.type = "none";
    state.title = "";
    state.subtitle = "";
    state.source = "";
    state.youtubeId = "";
    state.youtubePlaylistId = "";
    state.youtubeIndex = 0;
    state.youtubeUrl = "";
    state.audioOnly = false;
    state.isPlaying = false;
    state.isLoading = false;
    state.itemCount = 0;
    state.itemIndex = -1;
    state.hasNext = false;
    state.hasPrevious = false;

    refs.stage?.classList.remove("audio-only");
    show(refs.empty);
    hide(refs.playingPill);
    hide(refs.liveIndicator);
    hide(refs.openYoutube);
    if (hidePanel) hide(refs.overlay);

    if (refs.sourceBadge) refs.sourceBadge.textContent = "Chưa có nguồn";
    if (refs.nowTitle) refs.nowTitle.textContent = "Chưa phát nội dung";
    if (refs.nowSubtitle) {
      refs.nowSubtitle.textContent = "MP3/MP4 phát nền Android; YouTube tiếp tục bằng Picture-in-Picture.";
    }
    if (refs.nowIcon) refs.nowIcon.textContent = "♫";
    setEmptyCopy(
      "♫",
      "Chưa có nội dung đang phát",
      "Chọn file/thư mục để tạo playlist local hoặc dán link YouTube/playlist."
    );
    clearBrowserMediaSession();
    setNavigationUi();
    setRepeatUi();
    callNative("setYoutubePlaybackState", false, false, "");
  }

  function stopNativeSilently() {
    if (hasNativePlayer()) callNative("nativeMediaCommand", "stop");
  }

  function loadYoutube(source) {
    if (!source || (!source.id && !source.playlistId)) {
      notify("Không nhận diện được liên kết YouTube.");
      return;
    }

    stopNativeSilently();
    clearWebFrames();
    if (!state.toolEnabled) setToolEnabled(true);
    state.backend = "web";
    state.type = "youtube";
    state.youtubeId = source.id || "";
    state.youtubePlaylistId = source.playlistId || "";
    state.youtubeIndex = source.index || 0;
    state.youtubeUrl = source.url || "";
    state.source = state.youtubeUrl || state.youtubeId || state.youtubePlaylistId;
    state.audioOnly = false;
    state.isLoading = true;
    state.itemCount = source.playlistId ? 2 : 1;
    state.itemIndex = source.index || 0;
    state.hasNext = true;
    state.hasPrevious = Boolean(source.playlistId || source.index > 0);

    refs.youtube.src = youtubeEmbedUrl(source);
    show(refs.youtube);
    hide(refs.empty);
    hide(refs.htmlPlayer);
    refs.stage?.classList.remove("audio-only");
    refs.sourceBadge.textContent = source.playlistId ? "Playlist YouTube" : "YouTube";
    setMetadata(
      source.playlistId ? "Playlist YouTube" : `YouTube · ${source.id}`,
      source.playlistId
        ? "Tự chuyển video tiếp theo trong playlist"
        : "Phát bằng trình nhúng chính thức · bấm Home để mở PiP",
      "▶",
      true
    );
    setNavigationUi();
    setRepeatUi();
    setPlaying(true);
    forceYoutubeUnmute();
    registerYoutubeListener();
  }

  /** Fallback dành cho desktop; Android ưu tiên Media3 native. */
  function loadDirectSource(source, title, mime = "") {
    stopNativeSilently();
    clearWebFrames();

    const kind = directMediaKind(source, mime);
    state.backend = "web";
    state.type = kind;
    state.source = source;
    state.youtubeId = "";
    state.youtubePlaylistId = "";
    state.youtubeUrl = "";
    state.audioOnly = kind === "audio";
    state.isLoading = true;
    state.itemCount = 1;
    state.itemIndex = 0;
    state.hasNext = false;
    state.hasPrevious = false;

    refs.htmlPlayer.src = source;
    refs.htmlPlayer.muted = false;
    refs.htmlPlayer.loop = state.repeatOne;
    syncVolume(state.volume);
    show(refs.htmlPlayer);
    hide(refs.empty);
    refs.stage?.classList.toggle("audio-only", state.audioOnly);
    refs.sourceBadge.textContent = kind === "audio" ? "Âm thanh" : "Video";

    setMetadata(
      title || (kind === "audio" ? "Tệp âm thanh" : "Video trực tiếp"),
      kind === "audio" ? "Đang phát bằng WebView" : "Video trực tiếp trong ứng dụng",
      kind === "audio" ? "♫" : "▶",
      true
    );
    setNavigationUi();
    setRepeatUi();
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
    state.youtubePlaylistId = "";
    state.youtubeUrl = "";
    state.audioOnly = true;
    state.isLoading = true;
    state.isPlaying = false;
    state.itemCount = 1;
    state.itemIndex = 0;
    state.hasNext = false;
    state.hasPrevious = false;

    show(refs.empty);
    refs.stage?.classList.add("audio-only");
    refs.sourceBadge.textContent = sourceKind === "file" ? "Tệp trong máy" : "Media trực tiếp";
    setEmptyCopy(
      kind === "video" ? "▶" : "♫",
      "Đang mở bằng trình phát Android…",
      "Playlist sẽ tự chuyển bài và tiếp tục khi khóa màn hình."
    );
    setMetadata(
      title || "Đang mở media",
      sourceKind === "file" ? "Tệp trong máy • đang chuẩn bị" : "Liên kết trực tiếp • đang chuẩn bị",
      kind === "video" ? "▶" : "♫",
      false
    );
    setNavigationUi();
    setRepeatUi();
    setPlaying(false);
  }

  function openUrl() {
    const value = String(refs.urlInput.value || "").trim();
    if (!value) {
      notify("Hãy dán liên kết YouTube hoặc media trực tiếp.");
      return;
    }

    const youtube = parseYoutubeSource(value);
    if (youtube) {
      loadYoutube(youtube);
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
      callNative("setNativeMediaRepeatOne", state.repeatOne);
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
    callNative("setNativeMediaRepeatOne", state.repeatOne);
    callNative("openNativeMediaFile");
  }

  function requestNativeFolder() {
    if (typeof nativeBridge()?.openNativeMediaFolder !== "function") {
      notify("Bản Android này chưa hỗ trợ chọn thư mục.");
      return;
    }
    callNative("setNativeMediaRepeatOne", state.repeatOne);
    callNative("openNativeMediaFolder");
  }

  function refreshNativeState() {
    if (!hasNativePlayer()) return;
    try {
      const raw = callNative("getNativeMediaState");
      if (raw) onNativeState(raw);
    } catch {}
  }

  function openCenter() {
    if (typeof closeMenus === "function") closeMenus();
    show(refs.overlay);
    refreshNativeState();
  }

  function closeCenter() {
    if (state.pipMode) return;
    hide(refs.overlay);
  }

  function toggleAudioOnly() {
    if (state.type === "none") {
      notify("Chưa có nội dung đang phát.");
      return;
    }
    if (state.backend === "native") {
      notify("Trình phát Android tự tiếp tục phần âm thanh khi ứng dụng ra nền.");
      return;
    }
    if (state.type === "youtube") {
      notify("YouTube phải giữ khung video chính thức; hãy dùng Picture-in-Picture.");
      return;
    }

    state.audioOnly = !state.audioOnly;
    refs.stage?.classList.toggle("audio-only", state.audioOnly);
    refs.nowSubtitle.textContent = state.audioOnly ? "Chế độ chỉ âm thanh" : "Video trực tiếp trong ứng dụng";
    notify(state.audioOnly ? "Đã chuyển sang chỉ âm thanh." : "Đã hiện lại video.");
  }

  async function openPictureInPicture() {
    if (state.type === "none") {
      notify("Chưa có video đang phát.");
      return;
    }
    if (state.type === "youtube") {
      if (typeof nativeBridge()?.enterYoutubePictureInPicture === "function") {
        callNative("enterYoutubePictureInPicture");
      } else {
        notify("Picture-in-Picture YouTube chỉ có trong bản Android.");
      }
      return;
    }
    if (state.backend === "native") {
      notify("Media native đã phát ngoài nền và có điều khiển trên thông báo.");
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

  function toggleRepeatOne() {
    state.repeatOne = !state.repeatOne;
    localStorage.setItem(REPEAT_KEY, state.repeatOne ? "1" : "0");
    setRepeatUi();

    if (state.backend === "native") {
      callNative("setNativeMediaRepeatOne", state.repeatOne);
    } else if (state.type === "audio" || state.type === "video") {
      refs.htmlPlayer.loop = state.repeatOne;
    }

    notify(state.repeatOne ? "Đã bật lặp lại một bài." : "Đã tắt lặp lại một bài.");
  }

  function previousTrack() {
    if (state.backend === "native") {
      callNative("nativeMediaCommand", "previous");
      return;
    }
    if (state.type === "youtube") {
      sendYoutubeCommand("previousVideo");
      sendYoutubeCommand("playVideo");
      return;
    }
    if (state.type === "audio" || state.type === "video") {
      refs.htmlPlayer.currentTime = 0;
      refs.htmlPlayer.play().catch(() => {});
    }
  }

  function nextTrack() {
    if (state.backend === "native") {
      callNative("nativeMediaCommand", "next");
      return;
    }
    if (state.type === "youtube") {
      sendYoutubeCommand("nextVideo");
      sendYoutubeCommand("playVideo");
      return;
    }
    notify("Nguồn này không có bài tiếp theo.");
  }

  function openYoutubeExternally() {
    if (state.type !== "youtube") {
      notify("Nội dung hiện tại không phải YouTube.");
      return;
    }
    if (typeof nativeBridge()?.openYoutubeExternally === "function") {
      callNative("openYoutubeExternally", state.youtubeUrl || state.source || "");
    } else {
      window.open(state.youtubeUrl || state.source || "https://www.youtube.com", "_blank", "noopener");
    }
  }

  function stopAll({ silent = false } = {}) {
    if (state.backend === "native") callNative("nativeMediaCommand", "stop");
    if (state.type === "youtube") sendYoutubeCommand("stopVideo");
    resetUi({ hidePanel: true });
    if (!silent) notify("Đã dừng trình phát nền.");
  }

  function installMediaSession() {
    if (!("mediaSession" in navigator)) return;
    try {
      navigator.mediaSession.setActionHandler("play", async () => {
        if (state.backend === "native") callNative("nativeMediaCommand", "play");
        else if (state.type === "youtube") sendYoutubeCommand("playVideo");
        else if (state.type === "audio" || state.type === "video") await refs.htmlPlayer.play();
      });
      navigator.mediaSession.setActionHandler("pause", () => {
        if (state.backend === "native") callNative("nativeMediaCommand", "pause");
        else if (state.type === "youtube") sendYoutubeCommand("pauseVideo");
        else if (state.type === "audio" || state.type === "video") refs.htmlPlayer.pause();
      });
      navigator.mediaSession.setActionHandler("stop", () => stopAll());
      navigator.mediaSession.setActionHandler("previoustrack", previousTrack);
      navigator.mediaSession.setActionHandler("nexttrack", nextTrack);
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
    state.youtubePlaylistId = "";
    state.youtubeUrl = "";
    state.audioOnly = true;
    state.isLoading = Boolean(data.loading);
    state.isPlaying = Boolean(data.playing);
    state.repeatOne = Boolean(data.repeatOne);
    state.itemCount = Number(data.itemCount || 0);
    state.itemIndex = Number(data.itemIndex ?? -1);
    state.hasNext = Boolean(data.hasNext);
    state.hasPrevious = Boolean(data.hasPrevious);

    const isLocal = state.source.startsWith("content://") || state.source.startsWith("file://");
    refs.sourceBadge.textContent = isLocal
      ? state.itemCount > 1 ? `Thư mục · ${state.itemCount} bài` : "Tệp trong máy"
      : "Media trực tiếp";
    refs.stage?.classList.add("audio-only");
    show(refs.empty);
    hide(refs.youtube);
    hide(refs.htmlPlayer);
    setEmptyCopy(
      state.type === "video" ? "▶" : "♫",
      state.isLoading ? "Đang tải bằng Android…" : "Đang phát ngoài nền điện thoại",
      state.itemCount > 1
        ? `Bài ${state.itemIndex + 1}/${state.itemCount} · tự chuyển bài tiếp theo trong thư mục.`
        : "Bạn có thể chuyển ứng dụng, khóa màn hình và điều khiển từ thông báo."
    );
    setMetadata(
      state.title,
      state.itemCount > 1
        ? `${state.subtitle} · bài ${state.itemIndex + 1}/${state.itemCount}`
        : state.subtitle,
      state.type === "video" ? "▶" : "♫",
      false
    );
    syncVolume(Number(data.volume ?? state.volume), false);
    setRepeatUi();
    setNavigationUi();
    setPlaying(state.isPlaying);

    if (!state.toolEnabled) setToolEnabled(true);
  }

  function prepareForPip() {
    if (state.type !== "youtube") return false;
    state.pipMode = true;
    show(refs.overlay);
    show(refs.youtube);
    hide(refs.empty);
    document.documentElement.classList.add("lqlq-youtube-pip");
    return true;
  }

  function onPipModeChanged(inPip) {
    state.pipMode = Boolean(inPip);
    document.documentElement.classList.toggle("lqlq-youtube-pip", state.pipMode);
    if (state.pipMode && state.type === "youtube") {
      show(refs.overlay);
      show(refs.youtube);
      hide(refs.empty);
    }
  }

  function handleYoutubeInfo(info) {
    if (!info || state.type !== "youtube") return;

    const videoData = info.videoData && typeof info.videoData === "object"
      ? info.videoData
      : null;
    if (videoData) {
      const currentId = String(videoData.video_id || videoData.videoId || "").trim();
      const currentTitle = String(videoData.title || "").trim();
      const author = String(videoData.author || "").trim();
      const changed = (
        (currentId && currentId !== state.youtubeId) ||
        (currentTitle && currentTitle !== state.title)
      );

      if (currentId) {
        state.youtubeId = currentId;
        const query = new URLSearchParams({ v: currentId });
        if (state.youtubePlaylistId) query.set("list", state.youtubePlaylistId);
        state.youtubeUrl = `https://www.youtube.com/watch?${query}`;
        state.source = state.youtubeUrl;
      }
      if (changed) {
        setMetadata(
          currentTitle || `YouTube · ${state.youtubeId}`,
          author
            ? `${author} · ${state.youtubePlaylistId ? "Playlist YouTube" : "YouTube"}`
            : state.youtubePlaylistId
              ? "Tự chuyển video tiếp theo trong playlist"
              : "Phát bằng trình nhúng chính thức · bấm Home để mở PiP",
          "▶",
          true
        );
        notifyNativeYoutubeState();
      }
    }

    const playlist = Array.isArray(info.playlist) ? info.playlist : null;
    if (playlist && playlist.length) {
      state.itemCount = playlist.length;
    }
    const rawIndex = info.playlistIndex ?? info.currentIndex;
    const playlistIndex = Number(rawIndex);
    if (Number.isFinite(playlistIndex) && playlistIndex >= 0) {
      state.itemIndex = playlistIndex;
    }
    if (state.itemCount > 1) {
      state.hasPrevious = state.itemIndex > 0;
      state.hasNext = state.itemIndex < state.itemCount - 1;
    }
    setNavigationUi();
  }

  function handleYoutubeState(playerState) {
    if (state.type !== "youtube") return;
    const code = Number(playerState);
    if (code === 1) {
      state.isLoading = false;
      setPlaying(true);
      return;
    }
    if (code === 2 || code === -1 || code === 5) {
      state.isLoading = false;
      setPlaying(false);
      return;
    }
    if (code === 3) {
      state.isLoading = true;
      setPlaying(false);
      return;
    }
    if (code !== 0) return;

    state.isLoading = false;
    setPlaying(false);
    if (state.repeatOne) {
      sendYoutubeCommand("seekTo", [0, true]);
      sendYoutubeCommand("playVideo");
      return;
    }
    if (!state.autoNext) return;

    // Playlist chính thức thường tự chuyển. Chờ một nhịp; chỉ gọi nextVideo
    // khi iframe vẫn chưa bắt đầu video mới để tránh bỏ qua một mục.
    youtubeEndedTimer = setTimeout(() => {
      if (state.type !== "youtube" || state.isPlaying || state.isLoading || state.repeatOne) return;
      sendYoutubeCommand("nextVideo");
      sendYoutubeCommand("playVideo");
    }, state.youtubePlaylistId ? 900 : 250);
  }

  window.addEventListener("message", event => {
    const origin = String(event.origin || "");
    if (!/youtube(?:-nocookie)?\.com$/i.test(origin.replace(/^https?:\/\//, ""))) return;
    let data = event.data;
    if (typeof data === "string") {
      try { data = JSON.parse(data); } catch { return; }
    }
    if (!data || state.type !== "youtube") return;

    if (data.event === "onStateChange") {
      handleYoutubeState(data.info);
      return;
    }
    if (data.event === "infoDelivery" && data.info) {
      handleYoutubeInfo(data.info);
      if (data.info.playerState != null) handleYoutubeState(data.info.playerState);
    }
  });

  refs.youtube?.addEventListener("load", () => {
    if (state.type !== "youtube" || !refs.youtube.src) return;
    registerYoutubeListener();
    forceYoutubeUnmute();
  });

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
    event.preventDefault();
    requestNativeFile();
  });
  refs.chooseFolder?.addEventListener("click", requestNativeFolder);

  refs.fileInput?.addEventListener("change", () => {
    openFileFallback(refs.fileInput.files?.[0]);
    refs.fileInput.value = "";
  });

  document.querySelectorAll("[data-media-example]").forEach(button => {
    button.addEventListener("click", () => {
      const type = button.dataset.mediaExample;
      if (type === "youtube") refs.urlInput.placeholder = "Ví dụ: https://www.youtube.com/watch?v=... hoặc playlist";
      else if (type === "audio") refs.urlInput.placeholder = "Ví dụ: https://example.com/music.mp3";
      else refs.urlInput.placeholder = "Ví dụ: https://example.com/video.mp4";
      refs.urlInput.focus();
    });
  });

  refs.minimizeMode?.addEventListener("click", closeCenter);
  refs.audioMode?.addEventListener("click", toggleAudioOnly);
  refs.pip?.addEventListener("click", openPictureInPicture);
  refs.previous?.addEventListener("click", previousTrack);
  refs.next?.addEventListener("click", nextTrack);
  refs.repeatOne?.addEventListener("click", toggleRepeatOne);
  refs.openYoutube?.addEventListener("click", openYoutubeExternally);
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
  refs.htmlPlayer?.addEventListener("ended", () => {
    if (!state.repeatOne) setPlaying(false);
  });
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
  setRepeatUi();
  setNavigationUi();
  installMediaSession();
  resetUi();

  window.ShieldMedia = {
    open: openCenter,
    minimize: closeCenter,
    stop: stopAll,
    pause: () => {
      if (state.backend === "native") return;
      if (state.type === "youtube") {
        sendYoutubeCommand("pauseVideo");
        setPlaying(false);
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
        sendYoutubeCommand(state.isPlaying ? "pauseVideo" : "playVideo");
        setPlaying(!state.isPlaying);
        return;
      }
      if (state.type === "audio" || state.type === "video") {
        if (refs.htmlPlayer.paused) refs.htmlPlayer.play().catch(() => {});
        else refs.htmlPlayer.pause();
      }
    },
    previous: previousTrack,
    next: nextTrack,
    toggleRepeatOne,
    loadYoutube: value => loadYoutube(typeof value === "string" ? parseYoutubeSource(value) : value),
    loadDirectSource,
    onNativeState,
    prepareForPip,
    onPipModeChanged,
    state
  };

  // Khi Activity/shell được tạo lại, nối vào MediaSession đang phát và khôi
  // phục giao diện mà không tải lại file hay URL.
  setTimeout(refreshNativeState, 0);
})();
