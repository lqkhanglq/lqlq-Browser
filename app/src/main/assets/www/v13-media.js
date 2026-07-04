(() => {
  const refs = {
    toolbarBtn: document.getElementById("mediaToolbarBtn"),
    liveIndicator: document.getElementById("mediaLiveIndicator"),
    overlay: document.getElementById("mediaCenterOverlay"),
    closePanel: document.getElementById("mediaClosePanelBtn"),
    minimize: document.getElementById("mediaMinimizeBtn"),
    urlInput: document.getElementById("mediaUrlInput"),
    openUrl: document.getElementById("mediaOpenUrlBtn"),
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
    miniMode: document.getElementById("mediaMiniModeBtn"),
    audioMode: document.getElementById("mediaAudioModeBtn"),
    pip: document.getElementById("mediaPipBtn"),
    stop: document.getElementById("mediaStopBtn"),
    volume: document.getElementById("mediaVolumeRange"),
    volumeLabel: document.getElementById("mediaVolumeLabel"),
    mini: document.getElementById("globalMediaMini"),
    miniDrag: document.getElementById("globalMediaDrag"),
    miniSourceIcon: document.getElementById("globalMediaSourceIcon"),
    miniTitle: document.getElementById("globalMediaMiniTitle"),
    miniSubtitle: document.getElementById("globalMediaMiniSubtitle"),
    miniRestore: document.getElementById("globalMediaRestoreBtn"),
    miniHide: document.getElementById("globalMediaHideBtn"),
    miniStop: document.getElementById("globalMediaStopBtn"),
    miniPreview: document.getElementById("globalMediaPreview"),
    miniYoutube: document.getElementById("globalMiniYoutubeFrame"),
    miniVideo: document.getElementById("globalMiniVideo"),
    collapsed: document.getElementById("globalMediaCollapsed"),
    collapsedRestore: document.getElementById("globalMediaCollapsedRestore")
  };

  const state = {
    type: "none",
    title: "",
    subtitle: "",
    source: "",
    youtubeId: "",
    objectUrl: "",
    audioOnly: false,
    isPlaying: false,
    volume: 1
  };

  function show(element) {
    element?.classList.remove("hidden");
  }

  function hide(element) {
    element?.classList.add("hidden");
  }

  function notify(message) {
    if (typeof toast === "function") {
      toast(message);
    }
  }

  function youtubeIdFromUrl(value) {
    const raw = String(value || "").trim();

    if (/^[\w-]{11}$/.test(raw)) {
      return raw;
    }

    try {
      const url = new URL(raw);
      const host = url.hostname.replace(/^www\./, "");

      if (host === "youtu.be") {
        return url.pathname.split("/").filter(Boolean)[0] || "";
      }

      if (
        host === "youtube.com"
        || host === "m.youtube.com"
        || host === "music.youtube.com"
      ) {
        if (url.pathname === "/watch") {
          return url.searchParams.get("v") || "";
        }

        const parts = url.pathname.split("/").filter(Boolean);
        const marker = parts.findIndex(part =>
          ["embed", "shorts", "live"].includes(part)
        );

        if (marker >= 0) {
          return parts[marker + 1] || "";
        }
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
    return /\.(mp3|m4a|aac|ogg|wav|flac|mp4|webm|mov)(?:[?#].*)?$/i
      .test(String(value || ""));
  }

  function directMediaKind(value, mime = "") {
    if (/^audio\//i.test(mime)) return "audio";
    if (/^video\//i.test(mime)) return "video";
    if (/\.(mp3|m4a|aac|ogg|wav|flac)(?:[?#].*)?$/i.test(value)) return "audio";
    return "video";
  }

  function setMetadata(title, subtitle, icon = "♫") {
    state.title = title || "Nội dung đang phát";
    state.subtitle = subtitle || "Trình phát nền";
    refs.nowTitle.textContent = state.title;
    refs.nowSubtitle.textContent = state.subtitle;
    refs.nowIcon.textContent = icon;
    refs.miniTitle.textContent = state.title;
    refs.miniSubtitle.textContent = state.subtitle;
    refs.miniSourceIcon.textContent = icon;

    if ("mediaSession" in navigator) {
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
    refs.playingPill.classList.toggle("hidden", !state.isPlaying);
    refs.liveIndicator.classList.toggle("hidden", state.type === "none");
    refs.playingPill.textContent = state.isPlaying ? "Đang phát" : "Đã tạm dừng";
  }

  function clearFrames() {
    refs.youtube.src = "";
    refs.miniYoutube.src = "";
    refs.htmlPlayer.pause();
    refs.miniVideo.pause();

    refs.htmlPlayer.removeAttribute("src");
    refs.miniVideo.removeAttribute("src");
    refs.htmlPlayer.load();
    refs.miniVideo.load();

    hide(refs.youtube);
    hide(refs.miniYoutube);
    hide(refs.htmlPlayer);
    hide(refs.miniVideo);

    if (state.objectUrl) {
      URL.revokeObjectURL(state.objectUrl);
      state.objectUrl = "";
    }
  }

  function syncVolume(value) {
    state.volume = Math.max(0, Math.min(1, Number(value)));
    refs.htmlPlayer.volume = state.volume;
    refs.miniVideo.volume = state.volume;
    refs.volume.value = String(Math.round(state.volume * 100));
    refs.volumeLabel.textContent = `${Math.round(state.volume * 100)}%`;
  }

  function loadYoutube(id) {
    if (!id) {
      notify("Không nhận diện được liên kết YouTube.");
      return;
    }

    clearFrames();
    state.type = "youtube";
    state.youtubeId = id;
    state.source = id;
    state.audioOnly = false;

    const src = youtubeEmbedUrl(id, true);
    refs.youtube.src = src;
    refs.miniYoutube.src = src;

    show(refs.youtube);
    show(refs.miniYoutube);
    hide(refs.empty);
    refs.stage.classList.remove("audio-only");

    refs.sourceBadge.textContent = "YouTube";
    setMetadata(`YouTube · ${id}`, "Phát bằng trình nhúng chính thức", "▶");
    setPlaying(true);
    showMiniPlayer(false);
  }

  function loadDirectSource(source, title, mime = "") {
    clearFrames();

    const kind = directMediaKind(source, mime);
    state.type = kind;
    state.source = source;
    state.youtubeId = "";
    state.audioOnly = kind === "audio";

    refs.htmlPlayer.src = source;
    refs.miniVideo.src = source;
    refs.htmlPlayer.muted = false;
    // Trình phát mini chỉ là hình ảnh phản chiếu — phải luôn tắt tiếng,
    // nếu không âm thanh sẽ phát 2 lần chồng lên nhau.
    refs.miniVideo.muted = true;
    syncVolume(state.volume);

    show(refs.htmlPlayer);
    show(refs.miniVideo);
    hide(refs.empty);

    refs.stage.classList.toggle("audio-only", state.audioOnly);
    refs.sourceBadge.textContent = kind === "audio" ? "Âm thanh" : "Video";

    setMetadata(
      title || (kind === "audio" ? "Tệp âm thanh" : "Video trực tiếp"),
      kind === "audio" ? "Đang phát ở chế độ âm thanh" : "Video tiếp tục khi chuyển thẻ",
      kind === "audio" ? "♫" : "▶"
    );

    // Không chờ đồng bộ cho metadata: nếu trình duyệt chưa đọc xong metadata
    // (thường gặp với tệp content:// lớn từ hộp chọn tệp native), đợi sự kiện
    // loadedmetadata rồi mới play() — tránh đơ khi mở tệp local nặng.
    const startPlayback = () => {
      const playPromise = refs.htmlPlayer.play();
      playPromise?.catch(() => {
        notify("Trình duyệt yêu cầu bạn nhấn Play để bắt đầu.");
      });
    };
    if (refs.htmlPlayer.readyState >= 1) {
      startPlayback();
    } else {
      refs.htmlPlayer.addEventListener("loadedmetadata", startPlayback, { once: true });
    }

    setPlaying(true);
    showMiniPlayer(false);
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
      notify("HTML chỉ nhận YouTube hoặc liên kết trực tiếp .mp3/.mp4/.webm.");
      return;
    }

    const name = decodeURIComponent(value.split("/").pop()?.split(/[?#]/)[0] || "Media");
    loadDirectSource(value, name);
  }

  function openFile(file) {
    if (!file) return;

    let isMedia = /^audio\/|^video\//i.test(file.type);
    if (!isMedia) {
      // Một số hộp chọn tệp native trả mime rỗng/sai cho content:// — đoán
      // theo đuôi tệp thay vì từ chối luôn.
      const ext = String(file.name || "").split(".").pop()?.toLowerCase() || "";
      const audioExt = ["mp3", "m4a", "aac", "ogg", "oga", "wav", "flac"];
      const videoExt = ["mp4", "webm", "mov", "mkv", "3gp", "m4v"];
      isMedia = audioExt.includes(ext) || videoExt.includes(ext);
    }

    if (!isMedia) {
      notify("File đã chọn không phải âm thanh hoặc video.");
      return;
    }

    // Nhường main thread một nhịp trước khi tạo object URL / gán src, để
    // hộp chọn tệp native (content://) kịp đóng UI trước khi WebView bắt
    // đầu nạp tệp — tránh cảm giác đơ khi chọn tệp lớn.
    setTimeout(() => {
      const objectUrl = URL.createObjectURL(file);
      state.objectUrl = objectUrl;
      loadDirectSource(objectUrl, file.name, file.type);
    }, 0);
  }

  function openCenter() {
    closeMenus?.();
    show(refs.overlay);
  }

  function closeCenter() {
    hide(refs.overlay);

    if (state.type !== "none") {
      showMiniPlayer(false);
    }
  }

  function showMiniPlayer(collapsed = false) {
    if (state.type === "none") {
      notify("Chưa có nhạc hoặc video đang phát.");
      return;
    }

    show(refs.mini);
    refs.mini.classList.toggle("collapsed", collapsed);
    refs.collapsed.classList.toggle("hidden", !collapsed);
    hide(refs.overlay);
  }

  function restoreCenter() {
    refs.mini.classList.remove("collapsed");
    hide(refs.mini);
    show(refs.overlay);
  }

  function toggleAudioOnly() {
    if (state.type === "none") {
      notify("Chưa có nội dung đang phát.");
      return;
    }

    if (state.type === "youtube") {
      notify("YouTube phải giữ khung video chính thức; hãy dùng trình phát thu nhỏ.");
      showMiniPlayer(false);
      return;
    }

    state.audioOnly = !state.audioOnly;
    refs.stage.classList.toggle("audio-only", state.audioOnly);
    refs.miniPreview.classList.toggle("hidden", state.audioOnly);

    refs.nowSubtitle.textContent = state.audioOnly
      ? "Chế độ chỉ âm thanh"
      : "Video tiếp tục khi chuyển thẻ";

    refs.miniSubtitle.textContent = refs.nowSubtitle.textContent;
    notify(state.audioOnly ? "Đã chuyển sang chỉ âm thanh." : "Đã hiện lại video.");
  }

  async function openPictureInPicture() {
    if (state.type === "none") {
      notify("Chưa có video đang phát.");
      return;
    }

    if (state.type === "youtube") {
      notify("YouTube dùng trình phát thu nhỏ của Shield; PiP hệ thống không điều khiển được iframe.");
      showMiniPlayer(false);
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
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else {
        await video.requestPictureInPicture();
      }
    } catch {
      notify("Không thể mở Picture-in-Picture cho video này.");
    }
  }

  function stopAll() {
    clearFrames();
    state.type = "none";
    state.title = "";
    state.subtitle = "";
    state.source = "";
    state.youtubeId = "";
    state.audioOnly = false;
    state.isPlaying = false;

    show(refs.empty);
    hide(refs.playingPill);
    hide(refs.liveIndicator);
    hide(refs.mini);
    hide(refs.overlay);

    refs.sourceBadge.textContent = "Chưa có nguồn";
    refs.nowTitle.textContent = "Chưa phát nội dung";
    refs.nowSubtitle.textContent = "Âm thanh sẽ tiếp tục khi chuyển thẻ trong prototype.";
    refs.nowIcon.textContent = "♫";

    if ("mediaSession" in navigator) {
      try {
        navigator.mediaSession.metadata = null;
      } catch {}
    }

    notify("Đã dừng trình phát nền.");
  }

  function syncDirectPlayers(sourcePlayer) {
    if (state.type !== "audio" && state.type !== "video") return;

    const target = sourcePlayer === refs.htmlPlayer
      ? refs.miniVideo
      : refs.htmlPlayer;

    if (!Number.isFinite(sourcePlayer.currentTime)) return;

    if (Math.abs((target.currentTime || 0) - sourcePlayer.currentTime) > 1.2) {
      try {
        target.currentTime = sourcePlayer.currentTime;
      } catch {}
    }

    target.volume = sourcePlayer.volume;
  }

  function installMediaSession() {
    if (!("mediaSession" in navigator)) return;

    try {
      navigator.mediaSession.setActionHandler("play", async () => {
        if (state.type === "audio" || state.type === "video") {
          await refs.htmlPlayer.play();
        } else {
          showMiniPlayer(false);
        }
      });

      navigator.mediaSession.setActionHandler("pause", () => {
        if (state.type === "audio" || state.type === "video") {
          refs.htmlPlayer.pause();
          refs.miniVideo.pause();
        }
      });

      navigator.mediaSession.setActionHandler("stop", stopAll);
    } catch {}
  }

  let dragState = null;

  function startDrag(event) {
    if (event.target.closest("button")) return;

    const rect = refs.mini.getBoundingClientRect();
    dragState = {
      pointerId: event.pointerId,
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top
    };

    refs.mini.setPointerCapture?.(event.pointerId);
  }

  function moveDrag(event) {
    if (!dragState || event.pointerId !== dragState.pointerId) return;

    const maxLeft = window.innerWidth - refs.mini.offsetWidth - 6;
    const maxTop = window.innerHeight - refs.mini.offsetHeight - 6;

    const left = Math.max(6, Math.min(maxLeft, event.clientX - dragState.offsetX));
    const top = Math.max(6, Math.min(maxTop, event.clientY - dragState.offsetY));

    refs.mini.style.left = `${left}px`;
    refs.mini.style.top = `${top}px`;
    refs.mini.style.right = "auto";
    refs.mini.style.bottom = "auto";
  }

  function endDrag(event) {
    if (!dragState || event.pointerId !== dragState.pointerId) return;
    dragState = null;
  }

  const originalHandleAction = handleAction;
  handleAction = function handleMediaAction(action) {
    if (action === "background-media") {
      openCenter();
      return;
    }

    originalHandleAction(action);
  };

  const originalSwitchTab = switchTab;
  switchTab = function switchTabWithMedia(id) {
    originalSwitchTab(id);

    if (state.type !== "none" && !refs.overlay.classList.contains("hidden")) {
      showMiniPlayer(false);
    }
  };

  refs.toolbarBtn.addEventListener("click", openCenter);
  refs.closePanel.addEventListener("click", closeCenter);
  refs.minimize.addEventListener("click", () => showMiniPlayer(false));
  refs.openUrl.addEventListener("click", openUrl);
  refs.urlInput.addEventListener("keydown", event => {
    if (event.key === "Enter") openUrl();
  });

  refs.fileInput.addEventListener("change", () => {
    openFile(refs.fileInput.files?.[0]);
    refs.fileInput.value = "";
  });

  document.querySelectorAll("[data-media-example]").forEach(button => {
    button.addEventListener("click", () => {
      const type = button.dataset.mediaExample;

      if (type === "youtube") {
        refs.urlInput.placeholder = "Ví dụ: https://www.youtube.com/watch?v=...";
        refs.urlInput.focus();
      } else if (type === "audio") {
        refs.urlInput.placeholder = "Ví dụ: https://example.com/music.mp3";
        refs.urlInput.focus();
      } else {
        refs.urlInput.placeholder = "Ví dụ: https://example.com/video.mp4";
        refs.urlInput.focus();
      }
    });
  });

  refs.miniMode.addEventListener("click", () => showMiniPlayer(false));
  refs.audioMode.addEventListener("click", toggleAudioOnly);
  refs.pip.addEventListener("click", openPictureInPicture);
  refs.stop.addEventListener("click", stopAll);

  refs.volume.addEventListener("input", () => {
    syncVolume(Number(refs.volume.value) / 100);
  });

  refs.miniRestore.addEventListener("click", restoreCenter);
  refs.miniHide.addEventListener("click", () => {
    refs.mini.classList.add("collapsed");
    show(refs.collapsed);
  });
  refs.miniStop.addEventListener("click", stopAll);
  refs.collapsedRestore.addEventListener("click", () => {
    refs.mini.classList.remove("collapsed");
    hide(refs.collapsed);
  });

  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) {
      closeCenter();
    }
  });

  refs.htmlPlayer.addEventListener("play", () => {
    refs.miniVideo.play().catch(() => {});
    setPlaying(true);
  });

  refs.htmlPlayer.addEventListener("pause", () => {
    refs.miniVideo.pause();
    setPlaying(false);
  });

  refs.htmlPlayer.addEventListener("timeupdate", () => {
    syncDirectPlayers(refs.htmlPlayer);
  });

  refs.htmlPlayer.addEventListener("volumechange", () => {
    syncVolume(refs.htmlPlayer.volume);
  });

  refs.htmlPlayer.addEventListener("ended", () => {
    setPlaying(false);
  });

  refs.miniVideo.addEventListener("play", () => {
    refs.htmlPlayer.play().catch(() => {});
    setPlaying(true);
  });

  refs.miniVideo.addEventListener("pause", () => {
    refs.htmlPlayer.pause();
    setPlaying(false);
  });

  refs.miniVideo.addEventListener("timeupdate", () => {
    syncDirectPlayers(refs.miniVideo);
  });

  // Chốt chặn cuối chống phát tiếng đôi: trình phát mini KHÔNG BAO GIỜ
  // được phát tiếng, dù bất kỳ sự kiện/mã nào lỡ đổi muted thành false.
  refs.miniVideo.addEventListener("volumechange", () => {
    if (!refs.miniVideo.muted) refs.miniVideo.muted = true;
  });
  refs.miniVideo.addEventListener("play", () => {
    if (!refs.miniVideo.muted) refs.miniVideo.muted = true;
  });

  refs.miniDrag.addEventListener("pointerdown", startDrag);
  refs.miniDrag.addEventListener("pointermove", moveDrag);
  refs.miniDrag.addEventListener("pointerup", endDrag);
  refs.miniDrag.addEventListener("pointercancel", endDrag);

  document.addEventListener("keydown", event => {
    if (
      event.key === "Escape"
      && !refs.overlay.classList.contains("hidden")
    ) {
      closeCenter();
    }

    if (
      event.ctrlKey
      && event.shiftKey
      && event.key.toLowerCase() === "m"
    ) {
      event.preventDefault();
      openCenter();
    }
  });

  syncVolume(1);
  installMediaSession();

  window.ShieldMedia = {
    open: openCenter,
    minimize: () => showMiniPlayer(false),
    stop: stopAll,
    // Tạm dừng phát mà không xóa nguồn — dùng khi mở một trang web thật
    // (video của trang đó có thể tự phát) để tránh chồng tiếng với media
    // đang phát trong panel này.
    pause: () => {
      try {
        refs.htmlPlayer.pause();
        refs.miniVideo.pause();
        setPlaying(false);
      } catch {}
    },
    loadYoutube,
    loadDirectSource,
    state
  };
})();