(() => {
  // Việc (v0.23.22): GỘP LẠI còn 1 cơ chế nổi duy nhất cho nhạc/video nền —
  // trước đây có 2 thứ trùng chức năng: (1) khung "trình phát thu nhỏ" nổi
  // kéo được của chính JS này (kèm 1 <video>/<iframe YouTube> MIRROR riêng
  // để hiển thị, phải khoá tiếng vĩnh viễn để tránh chồng tiếng với bản
  // chính), và (2) nút bọt native mới thêm ở MainActivity.kt. Giờ CHỈ còn
  // nút bọt native làm điểm nổi trên mọi trang; panel đầy đủ (#mediaCenterOverlay)
  // chỉ có ĐÚNG 1 <video> (refs.htmlPlayer) và ĐÚNG 1 iframe YouTube
  // (refs.youtube) — không còn bản mirror nào để phải khoá tiếng giả tạo nữa.
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
    minimizeMode: document.getElementById("mediaMiniModeBtn"),
    audioMode: document.getElementById("mediaAudioModeBtn"),
    pip: document.getElementById("mediaPipBtn"),
    stop: document.getElementById("mediaStopBtn"),
    volume: document.getElementById("mediaVolumeRange"),
    volumeLabel: document.getElementById("mediaVolumeLabel")
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
    volume: 1,
    // Việc (v0.23.21): "Nhạc và video nền" hoạt động như 1 công cụ tiện ích
    // kiểu Chapter Clipper — bật/tắt bằng nút trong menu ☰, KHÁC với việc
    // đang phát hay không. Khi bật: nút bọt nổi (native) hiện trên MỌI
    // trang. Đóng bảng thêm nhạc (nút X/Thu nhỏ) chỉ ẩn giao diện, KHÔNG
    // tắt công cụ, nhạc vẫn chạy. Chỉ khi bấm lại nút menu để TẮT công cụ
    // thì mới thật sự dừng phát + ẩn nút bọt.
    toolEnabled: false
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
    refs.htmlPlayer.pause();
    refs.htmlPlayer.removeAttribute("src");
    refs.htmlPlayer.load();

    hide(refs.youtube);
    hide(refs.htmlPlayer);

    if (state.objectUrl) {
      URL.revokeObjectURL(state.objectUrl);
      state.objectUrl = "";
    }
  }

  function syncVolume(value) {
    state.volume = Math.max(0, Math.min(1, Number(value)));
    refs.htmlPlayer.volume = state.volume;
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

    refs.youtube.src = youtubeEmbedUrl(id, true);

    show(refs.youtube);
    hide(refs.empty);
    refs.stage.classList.remove("audio-only");

    refs.sourceBadge.textContent = "YouTube";
    setMetadata(`YouTube · ${id}`, "Phát bằng trình nhúng chính thức", "▶");
    setPlaying(true);
  }

  function loadDirectSource(source, title, mime = "") {
    clearFrames();

    const kind = directMediaKind(source, mime);
    state.type = kind;
    state.source = source;
    state.youtubeId = "";
    state.audioOnly = kind === "audio";

    refs.htmlPlayer.src = source;
    refs.htmlPlayer.muted = false;
    syncVolume(state.volume);

    show(refs.htmlPlayer);
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

  // Đóng bảng — CHỈ ẩn giao diện, nhạc/video vẫn tiếp tục phát nền. Nút bọt
  // native (nếu công cụ đang bật) vẫn hiện trên mọi trang để mở lại bảng.
  function closeCenter() {
    hide(refs.overlay);
  }

  function toggleAudioOnly() {
    if (state.type === "none") {
      notify("Chưa có nội dung đang phát.");
      return;
    }

    if (state.type === "youtube") {
      notify("YouTube phải giữ khung video chính thức.");
      return;
    }

    state.audioOnly = !state.audioOnly;
    refs.stage.classList.toggle("audio-only", state.audioOnly);

    refs.nowSubtitle.textContent = state.audioOnly
      ? "Chế độ chỉ âm thanh"
      : "Video tiếp tục khi chuyển thẻ";

    notify(state.audioOnly ? "Đã chuyển sang chỉ âm thanh." : "Đã hiện lại video.");
  }

  async function openPictureInPicture() {
    if (state.type === "none") {
      notify("Chưa có video đang phát.");
      return;
    }

    if (state.type === "youtube") {
      notify("PiP hệ thống không điều khiển được khung YouTube.");
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

  function installMediaSession() {
    if (!("mediaSession" in navigator)) return;

    try {
      navigator.mediaSession.setActionHandler("play", async () => {
        if (state.type === "audio" || state.type === "video") {
          await refs.htmlPlayer.play();
        }
      });

      navigator.mediaSession.setActionHandler("pause", () => {
        if (state.type === "audio" || state.type === "video") {
          refs.htmlPlayer.pause();
        }
      });

      navigator.mediaSession.setActionHandler("stop", stopAll);
    } catch {}
  }

  // Việc (v0.23.21): báo cho native biết công cụ đang bật/tắt để hiện/ẩn
  // nút bọt nổi trên MỌI trang (độc lập với việc có đang phát hay không —
  // khác setPlaying()/sendMediaState() vốn chỉ phản ánh trạng thái phát).
  function setToolEnabled(enabled) {
    state.toolEnabled = Boolean(enabled);
    try {
      if (typeof window.LqlqAndroid?.setMediaToolEnabled === "function") {
        window.LqlqAndroid.setMediaToolEnabled(state.toolEnabled);
      }
    } catch {}

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

  const originalHandleAction = handleAction;
  handleAction = function handleMediaAction(action) {
    if (action === "background-media") {
      // Toggle giống hệt Chapter Clipper: bấm lần 1 BẬT công cụ (hiện bọt
      // nổi + mở bảng thêm nhạc), bấm lần 2 TẮT hẳn (dừng phát, đóng bảng,
      // ẩn bọt nổi).
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

  refs.toolbarBtn.addEventListener("click", openCenter);
  refs.closePanel.addEventListener("click", closeCenter);
  refs.minimize.addEventListener("click", closeCenter);
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

  // Nút "Trình phát thu nhỏ" cũ giờ chỉ còn ý nghĩa "ẩn bảng, giữ nút bọt
  // nổi trên mọi trang" — hành vi y hệt đóng bảng (không còn khung mini
  // riêng để mở rộng ra nữa, nút bọt native đã thay thế hoàn toàn).
  refs.minimizeMode?.addEventListener("click", closeCenter);
  refs.audioMode.addEventListener("click", toggleAudioOnly);
  refs.pip.addEventListener("click", openPictureInPicture);
  refs.stop.addEventListener("click", stopAll);

  refs.volume.addEventListener("input", () => {
    syncVolume(Number(refs.volume.value) / 100);
  });

  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) {
      closeCenter();
    }
  });

  refs.htmlPlayer.addEventListener("play", () => setPlaying(true));
  refs.htmlPlayer.addEventListener("pause", () => setPlaying(false));
  refs.htmlPlayer.addEventListener("volumechange", () => {
    syncVolume(refs.htmlPlayer.volume);
  });
  refs.htmlPlayer.addEventListener("ended", () => setPlaying(false));

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
    minimize: closeCenter,
    stop: stopAll,
    // Tạm dừng phát mà không xóa nguồn — dùng khi mở một trang web thật
    // (video của trang đó có thể tự phát) để tránh chồng tiếng với media
    // đang phát trong panel này.
    pause: () => {
      try {
        refs.htmlPlayer.pause();
        setPlaying(false);
      } catch {}
    },
    // Bấm phát/tạm dừng thống nhất cho CẢ YouTube lẫn media trực tiếp — dùng
    // cho nút trên thanh thông báo nền. YouTube chỉ là iframe (không có thẻ
    // <audio>/<video>) nên cần gửi lệnh qua IFrame Player API postMessage.
    toggle: () => {
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
    state
  };
})();
