(() => {
  "use strict";

  const bridge = window.LqlqAutomation;

  const SESSION_STORE_KEY = "lqlq-video-automation-sessions-v35";
  const FILTERS = ["ALL", "RUNNING", "QUEUED", "PAUSED", "COMPLETED", "FAILED", "WAITING_USER"];
  const SETTINGS_TABS = ["content", "image", "voice", "video", "process", "publish", "background"];
  const WORKSPACE_PAGES = [
    { id: "content", group: "SOẠN NỘI DUNG", label: "Nội dung" },
    { id: "images", group: "TÀI NGUYÊN", label: "Hình ảnh" },
    { id: "voice", group: "TÀI NGUYÊN", label: "Giọng đọc" },
    { id: "video", group: "THÀNH PHẨM", label: "Xem trước video" },
    { id: "metadata", group: "THÀNH PHẨM", label: "Metadata" },
    { id: "review", group: "THÀNH PHẨM", label: "Kiểm duyệt" },
    { id: "publish", group: "THÀNH PHẨM", label: "Đăng video" },
    { id: "progress", group: "HỆ THỐNG", label: "Cài đặt tiến trình tự động" },
    { id: "logs", group: "HỆ THỐNG", label: "Nhật ký" },
    { id: "info", group: "HỆ THỐNG", label: "Thông tin phiên" }
  ];
  const STEP_ORDER = [
    "TOPIC",
    "CONTENT",
    "SCENE_PROMPTS",
    "ASSET_PLAN",
    "IMAGES_VISUALS",
    "VOICE",
    "SUBTITLE",
    "VIDEO",
    "METADATA",
    "REVIEW",
    "PUBLISH"
  ];
  const STEP_LABELS = {
    TOPIC: "Chủ đề",
    CONTENT: "Soạn nội dung",
    SCENE_PROMPTS: "Cảnh",
    ASSET_PLAN: "Kế hoạch tài nguyên",
    IMAGES_VISUALS: "Hình ảnh",
    VOICE: "Giọng đọc",
    SUBTITLE: "Phụ đề",
    VIDEO: "Video",
    METADATA: "Metadata",
    REVIEW: "Kiểm duyệt",
    PUBLISH: "Đăng"
  };
  // Cài đặt tiến trình tự động: app tự chạy tới bước nào rồi DỪNG lại (các bước sau
  // mốc này người dùng tự bấm khi muốn). Áp dụng cho luồng cào ảnh web (ChatGPT) mà
  // JS tự nối bước; luồng provider ảnh API chạy hết server-side như cũ.
  const AUTO_UNTIL_OPTIONS = [
    { value: "content", label: "Chỉ soạn nội dung rồi dừng" },
    { value: "image", label: "Tự tới Hình ảnh rồi dừng" },
    { value: "voice", label: "Tự tới Giọng đọc rồi dừng" },
    { value: "video", label: "Tự tới Video rồi dừng (khuyến nghị)" },
    { value: "metadata", label: "Tự tới Metadata rồi dừng" },
    { value: "review", label: "Tự phê duyệt Kiểm duyệt rồi dừng" },
    { value: "publish", label: "Tự động hết — TỰ ĐĂNG YouTube luôn (cần đã kết nối)" }
  ];
  const AUTO_STEP_RANK = { content: 1, image: 2, voice: 3, video: 4, metadata: 5, review: 6, publish: 7 };
  function autoUntilRank() {
    return AUTO_STEP_RANK[state.settingsDraft.autoUntilStep] || AUTO_STEP_RANK.video;
  }
  const DEFAULT_SESSION_CONFIG = {
    platform: "shorts",
    durationSeconds: 60,
    language: "Tiếng Việt"
  };
  const PLATFORM_OPTIONS = [
    { value: "shorts", label: "Shorts" },
    { value: "tiktok", label: "TikTok" },
    { value: "reels", label: "Reels" }
  ];
  // Khung hinh, chat luong, nen anh, kieu chuyen canh gio la CAI DAT TOAN CUC
  // (nam trong state.settingsDraft, tab "Video" cua Cai dat tu dong) - ap dung
  // cho MOI phien thay vi phai chinh rieng tung phien nhu truoc.
  const ASPECT_OPTIONS = ["9:16", "16:9", "1:1", "3:4", "4:3", "21:9"];
  const VIDEO_QUALITY_OPTIONS = [
    { value: "1080p", label: "1080p (chuẩn, khuyến nghị)" },
    { value: "720p", label: "720p (nhẹ hơn, xuất nhanh hơn)" }
  ];
  const VIDEO_BACKGROUND_MODE_OPTIONS = [
    { value: "blurred_fill", label: "Nền mờ (khuyến nghị) — ảnh hiện trọn vẹn, viền lấp bằng chính ảnh làm mờ" },
    { value: "black_bars", label: "Viền đen — ảnh hiện trọn vẹn, viền để đen đơn giản" }
  ];
  const VIDEO_MOTION_MODE_OPTIONS = [
    { value: "auto_mix", label: "Tự động phối hợp nhiều kiểu (khuyến nghị)" },
    { value: "zoom_in", label: "Chỉ zoom vào" },
    { value: "zoom_out", label: "Chỉ zoom ra" },
    { value: "pan_left_to_right", label: "Chỉ lia trái → phải" },
    { value: "pan_right_to_left", label: "Chỉ lia phải → trái" },
    { value: "none", label: "Không chuyển động (ảnh tĩnh)" }
  ];
  // Nhac nen la CAI DAT TOAN CUC (khong gan voi tung phien) - luu that su o phia
  // native (AutomationBackgroundMusicStore), JS chi doc/ghi qua bridge.
  const DEFAULT_BACKGROUND_MUSIC_SETTINGS = { hasMusic: false, displayName: "", mimeType: "", loop: true, volume: 0.35 };
  const SAFE_OPENAI_MODEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
  const SAFE_CLOUDFLARE_MODEL = /^@[A-Za-z0-9._/-]{3,127}$/;
  const SAFE_VOICE_MODEL = /^[A-Za-z0-9][A-Za-z0-9._@/-]{0,127}$/;
  const CUSTOM_MODEL_VALUE = "__custom__";
  const DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
  const CHATGPT_ACCOUNT_OPTIONS = [
    { value: "free", label: "ChatGPT thường" },
    { value: "plus", label: "ChatGPT Plus" },
    { value: "pro", label: "ChatGPT Pro" }
  ];
  const CHATGPT_MODE_OPTIONS = [
    { value: "write", label: "Viết" },
    { value: "web_search", label: "Tìm kiếm web" },
    { value: "deep_research", label: "Nghiên cứu sâu" }
  ];
  const CHATGPT_REASONING_OPTIONS = [
    { value: "standard", label: "Chuẩn" },
    { value: "high", label: "Cao" },
    { value: "max", label: "Rất cao" }
  ];

  const state = {
    open: false,
    currentView: "home",
    filter: "ALL",
    activeSessionId: "",
    activePage: "content",
    navOpen: false,
    settingsOpen: false,
    settingsTab: "content",
    sessions: loadSessions(),
    jobs: {},
    geminiStatus: { state: "NOT_CONFIGURED", model: DEFAULT_GEMINI_MODEL },
    imageProviders: [],
    selectedImageProviderId: "",
    voiceProviders: [],
    selectedVoiceProviderId: "",
    voiceDefinitions: [],
    backgroundMusicSettings: DEFAULT_BACKGROUND_MUSIC_SETTINGS,
    youtubePublish: { hasCredentials: false, connected: false, privacyStatus: "private", redirectUri: "" },
    youtubeDraft: { clientId: "", clientSecret: "" },
    settingsDraft: createDefaultSettingsDraft(),
    contentSource: loadContentSource(),
    voiceAutoSaveAttempted: {},
    imageAutoSaveAttempted: {},
    errorMessage: "",
    transientMessage: "Tạo dự án video theo workflow hiện có."
  };
  let jobPollTimer = 0;
  const scrollMemory = Object.create(null);

  injectStyles();
  const root = ensureRoot();
  bindGlobalEvents(root);
  render();
  resumePendingTasks();

  function resumePendingTasks() {
    state.sessions.forEach(session => {
      if (!session?.pendingTaskId) return;
      schedulePendingTaskPoll(session.sessionId, session.pendingTaskId, {
        label: session.pendingLabel || "Đang xử lý nền...",
        doneMessage: "Đã hoàn tất tác vụ chạy nền."
      }, 0);
    });
  }

  const CONTENT_SOURCE_STORE_KEY = "lqlq-video-automation-content-source";

  function loadContentSource() {
    try {
      const raw = localStorage.getItem(CONTENT_SOURCE_STORE_KEY);
      // Mặc định "web" (Gemini web, miễn phí) — chỉ dùng "api" nếu người dùng
      // đã từng tự chọn API và lưu lại trước đó.
      return raw === "api" || raw === "web" || raw === "chatgpt_web" ? raw : "web";
    } catch {
      return "web";
    }
  }

  const VIDEO_SETTINGS_STORE_KEY = "lqlq-video-automation-video-settings";

  function loadVideoSettings() {
    try {
      const raw = localStorage.getItem(VIDEO_SETTINGS_STORE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return {
        videoAspectRatio: ASPECT_OPTIONS.includes(parsed.videoAspectRatio) ? parsed.videoAspectRatio : "9:16",
        videoQualityTier: parsed.videoQualityTier === "720p" ? "720p" : "1080p",
        videoBackgroundMode: parsed.videoBackgroundMode === "black_bars" ? "black_bars" : "blurred_fill",
        videoMotionMode: VIDEO_MOTION_MODE_OPTIONS.some(option => option.value === parsed.videoMotionMode)
          ? parsed.videoMotionMode
          : "auto_mix",
        autoUntilStep: AUTO_STEP_RANK[parsed.autoUntilStep] ? parsed.autoUntilStep : "video",
        videoSubtitleColor: /^#[0-9a-fA-F]{6}$/.test(parsed.videoSubtitleColor || "") ? parsed.videoSubtitleColor : "#FFFFFF",
        chatGptAccountTier: CHATGPT_ACCOUNT_OPTIONS.some(option => option.value === parsed.chatGptAccountTier)
          ? parsed.chatGptAccountTier
          : "free",
        chatGptMode: CHATGPT_MODE_OPTIONS.some(option => option.value === parsed.chatGptMode)
          ? parsed.chatGptMode
          : "write",
        chatGptReasoning: CHATGPT_REASONING_OPTIONS.some(option => option.value === parsed.chatGptReasoning)
          ? parsed.chatGptReasoning
          : "standard"
      };
    } catch {
      return {
        videoAspectRatio: "9:16",
        videoQualityTier: "1080p",
        videoBackgroundMode: "blurred_fill",
        videoMotionMode: "auto_mix",
        autoUntilStep: "video",
        videoSubtitleColor: "#FFFFFF",
        chatGptAccountTier: "free",
        chatGptMode: "write",
        chatGptReasoning: "standard"
      };
    }
  }

  function persistSettingsDraft() {
    try {
      localStorage.setItem(VIDEO_SETTINGS_STORE_KEY, JSON.stringify({
        videoAspectRatio: state.settingsDraft.videoAspectRatio,
        videoQualityTier: state.settingsDraft.videoQualityTier,
        videoBackgroundMode: state.settingsDraft.videoBackgroundMode,
        videoMotionMode: state.settingsDraft.videoMotionMode,
        autoUntilStep: state.settingsDraft.autoUntilStep,
        videoSubtitleColor: state.settingsDraft.videoSubtitleColor,
        chatGptAccountTier: state.settingsDraft.chatGptAccountTier,
        chatGptMode: state.settingsDraft.chatGptMode,
        chatGptReasoning: state.settingsDraft.chatGptReasoning
      }));
    } catch {
      // localStorage khong kha dung — bo qua, se dung lai gia tri mac dinh lan sau.
    }
  }

  function persistContentSource(value) {
    try {
      localStorage.setItem(CONTENT_SOURCE_STORE_KEY, value);
    } catch {
      // localStorage khong kha dung — bo qua, se dung lai gia tri mac dinh lan sau.
    }
  }

  function createDefaultSettingsDraft() {
    return {
      geminiApiKey: "",
      geminiModel: DEFAULT_GEMINI_MODEL,
      // Mặc định "📌 Tìm ảnh Pinterest" — không cần đăng nhập, tìm-sẵn ảnh nhanh hơn
      // ChatGPT (đã kiểm chứng), gom theo nhân vật nên ít lần tìm.
      imageProviderId: "pinterest-image-search-web",
      imageModelPreset: "",
      imageModelCustom: "",
      imageApiKey: "",
      imageAccountId: "",
      // Mặc định "Google TTS trên điện thoại" (android-system-tts) — miễn phí,
      // không cần credential, khỏi phải tự chọn lại mỗi lần.
      voiceProviderId: "android-system-tts",
      voiceApiKey: "",
      voiceModel: "",
      voiceRegion: "",
      voiceLocale: "vi-VN",
      voiceId: "",
      voiceOutputFormat: "wav",
      voiceRate: 110,
      voicePitch: 100,
      chatGptAccountTier: "free",
      chatGptMode: "write",
      chatGptReasoning: "standard",
      // Cai dat video toan cuc - ap dung cho MOI phien, khong con nam rieng tung
      // phien nhu truoc (dat chung voi cac cai dat khac o day cho nhat quan).
      // Nap lai gia tri da luu (localStorage) thay vi luon ve mac dinh.
      ...loadVideoSettings()
    };
  }

  function ensureRoot() {
    let existing = document.getElementById("videoAutomationWorkspaceRoot");
    if (existing) return existing;
    existing = document.createElement("div");
    existing.id = "videoAutomationWorkspaceRoot";
    existing.className = "video-workspace-root hidden";
    existing.setAttribute("aria-hidden", "true");
    document.body.appendChild(existing);
    return existing;
  }

  function injectStyles() {
    if (document.getElementById("videoAutomationWorkspaceStyles")) return;
    const style = document.createElement("style");
    style.id = "videoAutomationWorkspaceStyles";
    style.textContent = `
.video-workspace-root {
  position: fixed;
  inset: 0;
  z-index: 1525;
  background: #f6fbf7;
}
.video-workspace-root.hidden { display: none; }
.video-workspace-shell {
  position: absolute;
  inset: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at 10% 0%, rgba(70, 202, 132, .12), transparent 32%),
    linear-gradient(180deg, rgba(255,255,255,.96), rgba(246,251,247,.96));
  color: #203328;
  box-shadow: none;
  border-right: 0;
  overflow: hidden;
}
.video-workspace-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid rgba(65, 106, 79, .12);
  background: rgba(255,255,255,.72);
  backdrop-filter: blur(14px);
}
.video-workspace-head button,
.video-workspace-chip-button,
.video-workspace-pill,
.video-workspace-primary,
.video-workspace-secondary,
.video-workspace-danger,
.video-workspace-ghost {
  font: inherit;
}
.video-workspace-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, #d9f8e6, #eefcf4);
  color: #0b8449;
  box-shadow: inset 0 0 0 1px rgba(12, 142, 80, .12);
  font-size: 20px;
  flex: 0 0 auto;
}
.video-workspace-head-copy { min-width: 0; flex: 1; overflow: hidden; }
.video-workspace-head-copy h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  /* Tiêu đề chủ đề có thể rất dài -> kẹp tối đa 2 dòng, tránh tràn che hết
     không gian làm việc (bug: mỗi chữ 1 dòng, kéo dài hết màn hình). */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.video-workspace-head-copy p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.35;
  color: #52685c;
  /* Phụ đề = trạng thái ngắn gọn, chỉ 1 dòng, không nhồi cả runtimeMessage. */
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.video-workspace-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}
.video-workspace-icon-button {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 12px;
  background: rgba(39, 73, 54, .08);
  color: inherit;
  cursor: pointer;
}
.video-workspace-status {
  padding: 7px 11px;
  border-radius: 999px;
  background: #e5f8ed;
  color: #0b8449;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}
.video-workspace-status[data-state="RUNNING"] { background: #0e9f58; color: #fff; }
.video-workspace-status[data-state="QUEUED"],
.video-workspace-status[data-state="WAITING_USER"] { background: #fff0cb; color: #8a6400; }
.video-workspace-status[data-state="FAILED"],
.video-workspace-status[data-state="CANCELLED"] { background: #fee7e4; color: #a13a31; }
.video-workspace-status[data-state="PAUSED"] { background: #e6eefc; color: #355a86; }
.video-workspace-body {
  flex: 1;
  overflow: auto;
  padding: 14px;
  scrollbar-width: thin;
}
.video-session-topbar,
.video-settings-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.video-session-actions,
.video-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.video-workspace-primary,
.video-workspace-secondary,
.video-workspace-danger,
.video-workspace-ghost {
  min-height: 40px;
  border: 0;
  border-radius: 12px;
  padding: 0 14px;
  font-weight: 750;
  cursor: pointer;
}
.video-workspace-primary {
  color: #fff;
  background: linear-gradient(135deg, #10a55d, #087f45);
  box-shadow: 0 10px 22px rgba(14, 159, 88, .18);
}
.video-workspace-secondary {
  color: #29513b;
  background: #edf6f0;
  box-shadow: inset 0 0 0 1px rgba(52, 95, 69, .08);
}
.video-workspace-danger {
  color: #a43939;
  background: #fff0ee;
}
.video-workspace-ghost {
  color: #597061;
  background: transparent;
  box-shadow: inset 0 0 0 1px rgba(52, 95, 69, .14);
}
.video-session-filter {
  display: flex;
  gap: 8px;
  overflow: auto;
  padding-bottom: 2px;
  margin-bottom: 12px;
}
.video-workspace-pill {
  min-height: 36px;
  border: 0;
  border-radius: 999px;
  padding: 0 14px;
  background: rgba(46, 79, 61, .08);
  color: #556e5f;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.video-workspace-pill.active {
  color: #0a864a;
  background: rgba(14,159,88,.14);
  box-shadow: inset 0 0 0 1px rgba(14,159,88,.14);
}
.video-session-empty,
.video-page-card,
.video-session-card,
.video-settings-card,
.video-sheet-card,
.video-scene-card,
.video-artifact-card,
.video-step-card {
  border: 1px solid rgba(59, 103, 76, .14);
  border-radius: 18px;
  background: rgba(255,255,255,.82);
  box-shadow: 0 10px 24px rgba(29, 69, 43, .05);
}
.video-session-empty,
.video-settings-card,
.video-page-card,
.video-session-card,
.video-sheet-card {
  padding: 16px;
}
.video-session-list {
  display: grid;
  gap: 10px;
}
.video-session-card-head,
.video-page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.video-session-card-head b,
.video-page-head b {
  display: block;
  font-size: 15px;
}
.video-session-card-head small,
.video-page-head small,
.video-helper-copy,
.video-field-note,
.video-muted-copy {
  color: #708275;
  line-height: 1.45;
}
.video-session-progress {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: #edf3ef;
  margin: 12px 0 8px;
}
.video-session-progress span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #0fa25b, #43c17f);
}
.video-session-card-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #708275;
}
.video-page-grid,
.video-settings-grid,
.video-scene-grid,
.video-artifact-grid,
.video-review-grid,
.video-info-grid {
  display: grid;
  gap: 12px;
}
.video-field,
.video-select,
.video-textarea,
.video-input {
  width: 100%;
}
.video-field {
  display: grid;
  gap: 6px;
}
.video-field label,
.video-field span:first-child {
  font-size: 13px;
  font-weight: 700;
}
.video-input,
.video-select,
.video-textarea {
  min-height: 46px;
  border: 1px solid rgba(68, 108, 83, .16);
  border-radius: 14px;
  padding: 12px 14px;
  color: inherit;
  background: #fff;
  font: inherit;
  box-sizing: border-box;
}
.video-textarea {
  min-height: 180px;
  resize: vertical;
}
.video-choice-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.video-choice-row.aspect {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.video-workspace-chip-button {
  min-height: 42px;
  border: 0;
  border-radius: 13px;
  background: #eef6f1;
  color: #31533f;
  font-weight: 750;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(52, 95, 69, .08);
}
.video-workspace-chip-button.active {
  color: #fff;
  background: linear-gradient(135deg, #0fa35b, #087f45);
}
.video-page-card + .video-page-card { margin-top: 12px; }
.video-scene-list,
.video-artifact-list,
.video-log-list,
.video-step-list {
  display: grid;
  gap: 10px;
}
.video-scene-card,
.video-artifact-card,
.video-step-card {
  padding: 14px;
}
.video-scene-card summary {
  cursor: pointer;
  list-style: none;
}
.video-scene-card summary::-webkit-details-marker { display: none; }
.video-scene-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.video-mini-pill {
  padding: 5px 9px;
  border-radius: 999px;
  background: #edf5ef;
  color: #45624f;
  font-size: 11px;
  font-weight: 700;
}
.video-artifact-thumb {
  width: 100%;
  border-radius: 14px;
  object-fit: cover;
  background: #eef4ef;
  aspect-ratio: 9 / 16;
}
.video-artifact-audio {
  width: 100%;
  margin-top: 8px;
}
.video-workspace-sheet-backdrop {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: flex;
  align-items: flex-end;
  background: rgba(15, 30, 20, .34);
}
.video-workspace-sheet {
  width: 100%;
  max-height: 82%;
  display: flex;
  flex-direction: column;
  border-radius: 22px 22px 0 0;
  background: #fff;
  box-shadow: 0 -18px 42px rgba(15, 30, 20, .22);
  overflow: hidden;
}
.video-workspace-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(64, 103, 79, .12);
}
.video-workspace-sheet-head b { font-size: 15px; }
.video-workspace-sheet-scroll {
  flex: 1;
  overflow: auto;
  padding: 14px;
}
.video-workspace-nav-section + .video-workspace-nav-section,
.video-settings-panel + .video-settings-panel {
  margin-top: 16px;
}
.video-workspace-nav-title {
  margin-bottom: 8px;
  color: #6f8174;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}
.video-nav-item {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
  border: 0;
  border-radius: 14px;
  background: #f3f8f4;
  color: #294d39;
  font-weight: 700;
  cursor: pointer;
}
.video-nav-item + .video-nav-item { margin-top: 8px; }
.video-nav-item.active {
  background: linear-gradient(135deg, rgba(15,163,91,.16), rgba(15,163,91,.08));
  box-shadow: inset 0 0 0 1px rgba(15,163,91,.16);
}
.video-settings-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.video-settings-tab {
  min-height: 40px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #65786c;
  font-weight: 750;
  cursor: pointer;
}
.video-settings-tab.active {
  color: #0a864a;
  background: rgba(14,159,88,.12);
  box-shadow: inset 0 0 0 1px rgba(14,159,88,.12);
}
.video-settings-state {
  padding: 10px 12px;
  border-radius: 14px;
  background: #eff5f1;
  color: #324f3d;
  font-size: 13px;
  line-height: 1.45;
}
.video-settings-state.error {
  background: #fff0ee;
  color: #a13a31;
}
.video-log-item {
  padding: 12px 0;
  border-bottom: 1px solid rgba(64, 103, 79, .08);
}
.video-log-item:last-child { border-bottom: 0; }
.video-log-item b { display: block; margin-bottom: 4px; }
.video-review-check {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f5faf6;
}
.video-review-check.warn { background: #fff6db; }
.video-review-check.fail { background: #fff0ee; }
.video-review-icon {
  width: 22px;
  flex: 0 0 22px;
  font-weight: 900;
}
.video-workspace-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 14px;
  margin-top: 14px;
  background: linear-gradient(180deg, rgba(251,253,251,0), rgba(251,253,251,.95) 28%);
}
.video-workspace-hidden { display: none !important; }
@media (max-width: 720px) {
  .video-workspace-root {
    background: var(--surface, #fbfdfb);
    backdrop-filter: none;
  }
  .video-workspace-shell { width: 100vw; }
  .video-choice-row,
  .video-choice-row.aspect,
  .video-settings-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* ===== Timeline Editor — giao diện tối kiểu CapCut trên điện thoại ===== */
.tl-overlay {
  position: fixed; inset: 0; z-index: 60;
  background: #0d0e12; color: #f2f3f5;
  display: flex; flex-direction: column;
  font-size: 14px;
  /* Chặn pinch-zoom phóng to CẢ TRANG khi thao tác trong editor. Vùng track cuộn
     ngang tự cho phép pan-x riêng bên dưới. */
  touch-action: none;
}
.tl-head {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; background: #15171c; border-bottom: 1px solid #23262e;
}
.tl-head .video-workspace-icon-button { background: #23262e; color: #f2f3f5; }
.tl-head-copy { flex: 1; min-width: 0; }
.tl-head-copy b { display: block; font-size: 15px; }
.tl-head-copy small { color: #9aa0aa; font-size: 11px; }
.tl-head .video-mini-pill { background: #23262e; color: #cfd3da; }
.tl-preview-wrap {
  flex: 3; min-height: 0; display: grid; place-items: center;
  background: #000; padding: 10px;
}
.tl-bottom { flex: 2; min-height: 0; display: flex; flex-direction: column; background: #101218; }
.tl-x { width: 34px; height: 34px; border: 0; border-radius: 8px; background: #23262e; color: #fff; font-size: 16px; }
.tl-head-copy { flex: 1; text-align: center; }
.tl-head-copy b { color: #cfd3da; font-size: 13px; }
.tl-preview {
  position: relative; max-height: 100%; max-width: 100%;
  height: min(46vh, 100%); background: #05060a; border-radius: 8px;
  overflow: hidden; box-shadow: 0 0 0 1px #23262e;
}
.tl-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
.tl-preview-empty { width: 100%; height: 100%; display: grid; place-items: center; color: #6b7280; }
.tl-cap-title {
  position: absolute; top: 8%; left: 6%; right: 6%; text-align: center;
  font-weight: 800; font-size: 5vmin; line-height: 1.15; color: #fff;
  text-shadow: 0 2px 6px rgba(0,0,0,.85); pointer-events: none;
}
.tl-cap-sub {
  position: absolute; bottom: 8%; left: 6%; right: 6%; text-align: center;
  font-weight: 700; font-size: 3.6vmin; line-height: 1.2; color: #fff;
  text-shadow: 0 2px 6px rgba(0,0,0,.9); pointer-events: none;
}
.tl-transport {
  display: flex; align-items: center; gap: 8px; justify-content: space-between;
  padding: 8px 12px; background: #15171c;
}
.tl-transport span { font-variant-numeric: tabular-nums; color: #cfd3da; font-size: 12px; }
.tl-transport-right { display: flex; gap: 8px; }
.tl-playbtn {
  flex: 0 0 auto; width: 40px; height: 40px; border-radius: 50%; border: 0;
  background: #2a6df4; color: #fff; font-size: 17px;
}
.tl-zoombtn {
  flex: 0 0 auto; width: 32px; height: 32px; border-radius: 8px; border: 0;
  background: #23262e; color: #f2f3f5; font-size: 18px; line-height: 1;
}
.tl-export { flex: 0 0 auto; padding: 8px 12px; }
.tl-toolbar {
  display: flex; gap: 8px; overflow-x: auto; padding: 8px 12px;
  background: #15171c; border-top: 1px solid #23262e;
}
.tl-tool {
  flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: 3px;
  background: transparent; border: 0; color: #d7dbe2; font: inherit; font-size: 11px;
  padding: 4px 8px; border-radius: 8px;
}
.tl-tool span:first-child { font-size: 18px; }
.tl-tool:active { background: #23262e; }
/* Khu track kiểu CapCut: vạch playhead TRẮNG cố định GIỮA, timeline TRƯỢT dưới nó */
.tl-trackzone { position: relative; flex: 1; min-height: 0; border-top: 1px solid #23262e; }
.tl-centerline {
  position: absolute; left: 50%; top: 0; bottom: 0; width: 2px;
  background: #fff; z-index: 10; transform: translateX(-1px);
  pointer-events: none; box-shadow: 0 0 4px rgba(0,0,0,.6);
}
.tl-scrollx {
  height: 100%; overflow-x: auto; overflow-y: auto;
  /* Cuộn ngang bằng 1 ngón để tua; KHÔNG cho trình duyệt phóng to cả trang. */
  touch-action: pan-x pan-y;
  padding: 8px 0 12px;
}
.tl-inner { position: relative; }
.tl-ruler { position: relative; height: 20px; }
.tl-tick { position: absolute; top: 0; bottom: 0; border-left: 1px solid #2b2f39; }
.tl-tick span { position: absolute; top: 2px; left: 3px; font-size: 9px; color: #6b7280; }
.tl-lane {
  position: relative; height: 34px; margin-top: 6px; background: #1b1e26;
  border-radius: 6px; overflow: hidden;
}
.tl-clip {
  position: absolute; top: 3px; bottom: 3px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; color: #fff; white-space: nowrap; overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.12); padding: 0 4px;
}
.tl-clip.selected { box-shadow: 0 0 0 2px #ffd23b, inset 0 0 0 1px rgba(255,255,255,.2); }
.tl-clip-dur { position: absolute; bottom: 1px; right: 5px; font-size: 8px; opacity: .9; pointer-events: none; }
.tl-resize {
  position: absolute; top: 0; bottom: 0; right: 0; width: 16px;
  background: #ffd23b; border-radius: 0 5px 5px 0; cursor: ew-resize;
  touch-action: none; box-shadow: -1px 0 3px rgba(0,0,0,.4);
}
.tl-playhead { position: absolute; top: 0; bottom: 0; width: 2px; background: #ff3b5c; z-index: 5; pointer-events: none; }
`;
    document.head.appendChild(style);
  }

  function bindGlobalEvents(container) {
    container.addEventListener("click", handleClick);
    container.addEventListener("input", handleInput);
    container.addEventListener("change", handleChange);
    container.addEventListener("pointerdown", handleTimelinePointerDown);
  }

  function handleClick(event) {
    const actionEl = event.target.closest("[data-video-action]");
    if (
      event.target.closest(".video-workspace-sheet")
      && actionEl?.dataset.videoAction === "close-sheet"
      && actionEl.classList.contains("video-workspace-sheet-backdrop")
    ) {
      return;
    }
    if (!actionEl) {
      if (event.target === root) closeWorkspace();
      return;
    }
    const action = actionEl.dataset.videoAction;
    switch (action) {
      case "close":
      case "back-home":
        if (state.currentView === "workspace") {
          state.currentView = "home";
          state.activeSessionId = "";
          state.navOpen = false;
          state.settingsOpen = false;
          render();
        } else {
          closeWorkspace();
        }
        break;
      case "create-session":
        createSession();
        break;
      case "open-session":
        openSession(actionEl.dataset.sessionId);
        break;
      case "resume-session":
        openSession(actionEl.dataset.sessionId);
        continueAutoFromContent();
        break;
      case "delete-session":
        deleteSession(actionEl.dataset.sessionId);
        break;
      case "duplicate-session":
        duplicateSession(actionEl.dataset.sessionId);
        break;
      case "set-filter":
        state.filter = actionEl.dataset.filter || "ALL";
        render();
        break;
      case "set-video-aspect":
        state.settingsDraft.videoAspectRatio = actionEl.dataset.value || state.settingsDraft.videoAspectRatio;
        persistSettingsDraft();
        render();
        break;
      case "pick-background-music":
        pickBackgroundMusic();
        break;
      case "clear-background-music":
        clearBackgroundMusic();
        break;
      case "toggle-nav":
        state.navOpen = !state.navOpen;
        state.settingsOpen = false;
        render();
        break;
      case "open-settings":
        state.settingsOpen = true;
        state.navOpen = false;
        refreshSettingsData();
        render();
        break;
      case "close-sheet":
        state.navOpen = false;
        state.settingsOpen = false;
        render();
        break;
      case "goto-page":
        state.activePage = actionEl.dataset.page || "content";
        state.navOpen = false;
        if (state.activePage === "publish") refreshYouTubeStatus();
        render();
        break;
      case "save-youtube-config":
        saveYouTubeConfig();
        break;
      case "connect-youtube":
        connectYouTubeAccount();
        break;
      case "disconnect-youtube":
        disconnectYouTubeAccount();
        break;
      case "publish-youtube":
        publishToYouTubeForActiveSession();
        break;
      case "settings-tab":
        state.settingsTab = actionEl.dataset.tab || "content";
        if (state.settingsTab === "voice") {
          refreshVoiceDefinitions();
        }
        // Tab "Đăng": nạp lại trạng thái YouTube từ native (Client ID/secret đã lưu,
        // đã kết nối chưa) — nếu không refresh thì mở lại app sẽ thấy như "mất".
        if (state.settingsTab === "publish") {
          refreshYouTubeStatus();
        }
        render();
        break;
      case "run-session":
        runSession(actionEl.dataset.sessionId || "");
        break;
      case "generate-content":
        smartAutoRun();
        break;
      case "force-restart":
        runSession("", false);
        break;
      case "generate-content-only":
        runSession("", true);
        break;
      case "save-scene-text":
        saveSceneText(actionEl.dataset.sceneId);
        break;
      case "delete-scene":
        deleteSceneForActiveSession(actionEl.dataset.sceneId);
        break;
      case "add-scene":
        addSceneForActiveSession();
        break;
      case "open-timeline":
        openTimelineEditor();
        break;
      case "close-timeline":
        closeTimelineEditor();
        break;
      case "timeline-clip":
        timelineJumpToClip(actionEl.dataset.clipId);
        break;
      case "timeline-select-clip":
        timelineSelectClip(actionEl.dataset.clipId);
        break;
      case "timeline-zoom-in":
        timelineZoom(1.5);
        break;
      case "timeline-zoom-out":
        timelineZoom(1 / 1.5);
        break;
      case "timeline-playpause":
        timelinePlayPause();
        break;
      case "timeline-export":
        timelineExport();
        break;
      case "timeline-replace-image":
        timelineReplaceImage();
        break;
      case "timeline-delete-clip":
        timelineDeleteClip();
        break;
      case "timeline-split":
        timelineSplitAtPlayhead();
        break;
      case "timeline-move-left":
        timelineMoveClip("up");
        break;
      case "timeline-move-right":
        timelineMoveClip("down");
        break;
      case "timeline-edit-title":
        timelineEditTitle();
        break;
      case "timeline-music-volume":
        timelineMusicVolume();
        break;
      case "timeline-duration-hint":
        timelineDurationHint();
        break;
      case "timeline-need-select":
        setTransient("Chọn 1 cảnh ở track Hình trước khi dùng công cụ.");
        render();
        break;
      case "timeline-tool-soon":
        setTransient("Công cụ này (Tách/Thời lượng/Chuyển động…) sẽ hoạt động ở bước sau khi có lưu timeline + renderer timeline.");
        render();
        break;
      case "replace-scene-image":
        replaceSceneImageForActiveSession(actionEl.dataset.sceneId);
        break;
      case "rescrape-scene-image":
        rescrapeSceneImageForActiveSession(actionEl.dataset.sceneId);
        break;
      case "download-scene-image":
        downloadSceneImageForActiveSession(actionEl.dataset.sceneId);
        break;
      case "move-scene-up":
        moveSceneForActiveSession(actionEl.dataset.sceneId, "up");
        break;
      case "move-scene-down":
        moveSceneForActiveSession(actionEl.dataset.sceneId, "down");
        break;
      case "rerun-voice-video":
        rerunVoiceAndVideo();
        break;
      case "continue-auto":
        continueAutoFromContent();
        break;
      case "poc-gemini-web":
        runGeminiWebPocDebug();
        break;
      case "retry-image":
        retryImageForActiveSession();
        break;
      case "retry-voice":
        retryVoiceForActiveSession();
        break;
      case "render-video":
      case "retry-video":
        retryVideoForActiveSession();
        break;
      case "regenerate-metadata":
        regenerateMetadataForActiveSession();
        break;
      case "save-metadata":
        saveMetadataDraftForActiveSession();
        break;
      case "approve":
        approveReviewForActiveSession();
        break;
      case "reject":
        rejectReviewForActiveSession();
        break;
      case "share":
        sharePublishForActiveSession();
        break;
      case "publish":
        markPublishedForActiveSession();
        break;
      case "preview-video":
        previewVideoForActiveSession();
        break;
      case "export-video":
        exportVideoForActiveSession();
        break;
      case "import-image":
      case "replace-image":
        startImportImagesForActiveSession();
        break;
      case "remove-queue":
      case "stop-session":
        cancelPendingTask(actionEl.dataset.sessionId || state.activeSessionId);
        break;
      case "pause-session":
        // WorkManager không "pause" giữa chừng an toàn -> Tạm dừng = DỪNG tác vụ nền
        // hiện tại; sau đó hiện nút "Tự động chạy tiếp" để nối lại từ chỗ đang dở.
        cancelPendingTask(state.activeSessionId);
        break;
      case "queue-now":
        runSession(actionEl.dataset.sessionId || "");
        break;
      case "save-gemini":
        saveGeminiConfiguration();
        break;
      case "test-gemini":
        testGeminiConnection();
        break;
      case "save-image":
        saveImageConfiguration();
        break;
      case "test-image":
        testImageConnection();
        break;
      case "save-voice":
        saveVoiceConfiguration();
        break;
      case "test-voice":
        testVoiceConnection();
        break;
      case "sample-voice":
        sampleVoice();
        break;
      case "open-voice-settings":
        openVoiceSettings();
        break;
    }
  }

  function handleInput(event) {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (target.id === "timelinePlayhead") {
      setTimelinePlayhead(Number(target.value) || 0);
      return;
    }
    const session = getActiveSession();
    if (target.id === "videoSessionTopicInput" && session) {
      session.topic = target.value;
      session.title = buildSessionTitle(target.value);
      session.updatedAt = Date.now();
      persistSessions();
      updateTopicMeta();
      updateWorkspaceHeader();
      updateSessionHomeCards();
      return;
    }
    if (target.id === "videoDurationSeconds" && session) {
      session.config.durationSeconds = clampNumber(target.value, 5, 600, 60);
      session.updatedAt = Date.now();
      persistSessions();
      return;
    }
    if (target.id === "videoQualityTierSelect") {
      state.settingsDraft.videoQualityTier = target.value === "720p" ? "720p" : "1080p";
      persistSettingsDraft();
      return;
    }
    if (target.id === "videoBackgroundModeSelect") {
      state.settingsDraft.videoBackgroundMode = target.value === "black_bars" ? "black_bars" : "blurred_fill";
      persistSettingsDraft();
      return;
    }
    if (target.id === "videoMotionModeSelect") {
      state.settingsDraft.videoMotionMode = VIDEO_MOTION_MODE_OPTIONS.some(option => option.value === target.value)
        ? target.value
        : "auto_mix";
      persistSettingsDraft();
      return;
    }
    if (target.id === "backgroundMusicVolumeRange") {
      // Khi ĐANG kéo: chỉ cập nhật nhãn + state trong bộ nhớ, KHÔNG render lại
      // toàn bộ UI (render giữa lúc kéo là nguyên nhân bị giật/nhảy màn hình) và
      // KHÔNG gọi backend. Việc lưu backend để dồn vào lúc thả (handleChange).
      const pct = clampNumber(target.value, 0, 200, 35);
      state.backgroundMusicSettings = {
        ...(state.backgroundMusicSettings || DEFAULT_BACKGROUND_MUSIC_SETTINGS),
        volume: pct / 100
      };
      const label = target.previousElementSibling;
      if (label) label.textContent = `Âm lượng nhạc nền (so với giọng đọc): ${pct}%`;
      return;
    }
    if (target.id === "videoSubtitleColorInput") {
      const v = /^#[0-9a-fA-F]{6}$/.test(target.value) ? target.value.toUpperCase() : "#FFFFFF";
      state.settingsDraft.videoSubtitleColor = v;
      persistSettingsDraft();
      const label = target.parentElement && target.parentElement.querySelector("span");
      if (label) label.textContent = v;
      return;
    }
    if (target.id === "youtubeClientIdInput") {
      state.youtubeDraft.clientId = target.value;
      return;
    }
    if (target.id === "youtubeClientSecretInput") {
      state.youtubeDraft.clientSecret = target.value;
      return;
    }
    if (target.id === "videoImageApiKey") {
      state.settingsDraft.imageApiKey = target.value;
      return;
    }
    if (target.id === "videoImageAccountId") {
      state.settingsDraft.imageAccountId = target.value;
      return;
    }
    if (target.id === "videoImageModelCustom") {
      state.settingsDraft.imageModelCustom = target.value;
      return;
    }
    if (target.id === "videoGeminiApiKey") {
      state.settingsDraft.geminiApiKey = target.value;
      return;
    }
    if (target.id === "videoGeminiModel") {
      state.settingsDraft.geminiModel = target.value;
      return;
    }
    if (target.id === "videoVoiceApiKey") {
      state.settingsDraft.voiceApiKey = target.value;
      return;
    }
    if (target.id === "videoVoiceModel") {
      state.settingsDraft.voiceModel = target.value;
      return;
    }
    if (target.id === "videoVoiceRegion") {
      state.settingsDraft.voiceRegion = target.value;
      return;
    }
    if (target.id === "videoVoiceLocale") {
      state.settingsDraft.voiceLocale = target.value;
      return;
    }
    if (target.id === "videoVoiceRate") {
      state.settingsDraft.voiceRate = clampNumber(target.value, 50, 200, 100);
      updateVoiceSliderLabels();
      return;
    }
    if (target.id === "videoVoicePitch") {
      state.settingsDraft.voicePitch = clampNumber(target.value, 50, 200, 100);
      updateVoiceSliderLabels();
      return;
    }
  }

  function handleChange(event) {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const session = getActiveSession();
    if (target.id === "autoUntilStepSelect") {
      state.settingsDraft.autoUntilStep = AUTO_STEP_RANK[target.value] ? target.value : "video";
      persistSettingsDraft();
      setTransient("Đã lưu cài đặt tiến trình tự động.");
      return;
    }
    if (target.id === "youtubePrivacySelect") {
      if (bridge?.setYouTubePrivacy) {
        parseResponse(bridge.setYouTubePrivacy(JSON.stringify({ privacyStatus: target.value })));
        state.youtubePublish.privacyStatus = target.value;
      }
      return;
    }
    if (target.id === "backgroundMusicVolumeRange") {
      // Chỉ lưu backend khi THẢ slider (một lần), không render lại UI → không giật.
      persistBackgroundMusicVolume(clampNumber(target.value, 0, 200, 35) / 100);
      return;
    }
    if (target.id === "backgroundMusicLoopCheckbox") {
      saveBackgroundMusicOptions({ loop: target.checked });
      return;
    }
    if (target.id === "videoContentSourceSelect") {
      state.contentSource = target.value === "api" || target.value === "chatgpt_web" ? target.value : "web";
      persistContentSource(state.contentSource);
      render();
      return;
    }
    if (target.id === "videoChatGptAccountTierSelect") {
      state.settingsDraft.chatGptAccountTier = CHATGPT_ACCOUNT_OPTIONS.some(option => option.value === target.value) ? target.value : "free";
      if (state.settingsDraft.chatGptAccountTier === "plus" && state.settingsDraft.chatGptReasoning === "standard") {
        state.settingsDraft.chatGptReasoning = "high";
      } else if (state.settingsDraft.chatGptAccountTier === "pro" && state.settingsDraft.chatGptReasoning !== "max") {
        state.settingsDraft.chatGptReasoning = "max";
      } else if (state.settingsDraft.chatGptAccountTier === "free" && state.settingsDraft.chatGptReasoning === "max") {
        state.settingsDraft.chatGptReasoning = "standard";
      }
      persistSettingsDraft();
      render();
      return;
    }
    if (target.id === "videoChatGptModeSelect") {
      state.settingsDraft.chatGptMode = CHATGPT_MODE_OPTIONS.some(option => option.value === target.value) ? target.value : "write";
      persistSettingsDraft();
      return;
    }
    if (target.id === "videoChatGptReasoningSelect") {
      state.settingsDraft.chatGptReasoning = CHATGPT_REASONING_OPTIONS.some(option => option.value === target.value) ? target.value : "standard";
      persistSettingsDraft();
      return;
    }
    if (target.id === "videoPlatformSelect" && session) {
      session.config.platform = target.value;
      session.updatedAt = Date.now();
      persistSessions();
      return;
    }
    if (target.id === "videoLanguageSelect" && session) {
      session.config.language = target.value;
      session.updatedAt = Date.now();
      persistSessions();
      return;
    }
    if (target.id === "videoImageProviderSelect") {
      state.settingsDraft.imageProviderId = target.value;
      if (!state.settingsDraft.imageModelPreset) {
        state.settingsDraft.imageModelPreset = "";
      }
      syncImageDraftFromSelectedProvider();
      refreshImageStatus();
      render();
      return;
    }
    if (target.id === "videoImageModelPreset") {
      state.settingsDraft.imageModelPreset = target.value;
      render();
      return;
    }
    if (target.id === "videoVoiceProviderSelect") {
      state.settingsDraft.voiceProviderId = target.value;
      syncVoiceDraftFromSelectedProvider();
      refreshVoiceDefinitions();
      refreshVoiceStatus();
      render();
      return;
    }
    if (target.id === "videoVoiceSelect") {
      state.settingsDraft.voiceId = target.value;
      render();
      return;
    }
    if (target.id === "videoVoiceOutputFormat") {
      state.settingsDraft.voiceOutputFormat = target.value;
      render();
    }
  }

  function createSession() {
    const sessionId = `session-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
    const session = {
      sessionId,
      title: "Dự án video mới",
      topic: "",
      status: "DRAFT",
      progressPercent: 0,
      activeStep: "Chưa chạy",
      updatedAt: Date.now(),
      linkedJobId: "",
      summary: "Chuẩn bị nội dung, hình ảnh, giọng đọc và video.",
      reviewStatus: "PENDING",
      lastPage: "content",
      pendingTaskId: "",
      pendingLabel: "",
      pendingState: "",
      config: { ...DEFAULT_SESSION_CONFIG }
    };
    state.sessions.unshift(session);
    persistSessions();
    state.currentView = "workspace";
    state.activeSessionId = sessionId;
    state.activePage = "content";
    state.navOpen = false;
    state.settingsOpen = false;
    state.errorMessage = "";
    setTransient("Đã tạo phiên nháp mới.");
    render();
  }

  function openSession(sessionId) {
    const session = state.sessions.find(item => item.sessionId === sessionId);
    if (!session) return;
    rememberCurrentScroll();
    state.currentView = "workspace";
    state.activeSessionId = sessionId;
    state.activePage = session.lastPage || "content";
    state.navOpen = false;
    state.settingsOpen = false;
    state.errorMessage = "";
    if (session.linkedJobId && !session.pendingTaskId) {
      refreshJobForSession(session);
    }
    render();
  }

  function refreshAllLinkedJobs() {
    // Chỉ render() lại khi có dữ liệu THẬT SỰ thay đổi — trước đây cứ có
    // linkedJobId là coi như "changed" và render() (thay toàn bộ innerHTML)
    // mỗi 2.5s dù không có gì mới, gây giật/chớp màn hình liên tục.
    //
    // Bỏ qua các phiên đang có pendingTaskId: schedulePendingTaskPoll() đã là
    // nguồn cập nhật % duy nhất cho phiên đó (đọc thẳng từ task nền, có % thật
    // theo từng bước). Nếu để hàm này poll job DB song song, mapJobStatus() sẽ
    // tính lại status/% theo số step đã COMPLETED trong DB (thường thấp hơn vì
    // job chỉ ghi 1 lần khi cả pipeline xong) và ghi đè ngược lại, gây giật qua
    // lại giữa "Đang chờ 0%" và "% thật đang chạy".
    let changed = false;
    state.sessions.forEach(session => {
      if (session && session.linkedJobId && !session.pendingTaskId) {
        if (refreshJobForSession(session)) {
          changed = true;
        }
      }
    });
    if (changed && state.open) {
      render();
    }
  }

  function deleteSession(sessionId) {
    const session = state.sessions.find(item => item.sessionId === sessionId);
    if (!session) return;
    if (session.pendingTaskId) {
      setTransient("Phiên đang chạy nền, chưa thể xóa lúc này.");
      render();
      return;
    }
    if (!confirm("Xóa phiên này? Ảnh, giọng đọc, video đã tạo cho phiên sẽ bị xóa vĩnh viễn khỏi máy, không khôi phục lại được.")) {
      return;
    }
    // Trước đây chỉ xóa khỏi danh sách hiển thị (localStorage), dữ liệu thật (job,
    // artifact DB rows, file ảnh/giọng/video) vẫn còn nguyên trên máy vĩnh viễn.
    // Giờ gọi native dọn sạch cả job graph lẫn file thật trước khi bỏ khỏi UI.
    if (session.linkedJobId && bridge?.deleteAutomationJob) {
      const response = parseResponse(bridge.deleteAutomationJob(session.linkedJobId));
      if (!response.ok) {
        setError(response.message || "Không thể xóa dữ liệu phiên trên máy lúc này.");
        render();
        return;
      }
    }
    state.sessions = state.sessions.filter(item => item.sessionId !== sessionId);
    delete state.jobs[sessionId];
    if (state.activeSessionId === sessionId) {
      state.activeSessionId = "";
      state.currentView = "home";
    }
    persistSessions();
    setTransient("Đã xóa phiên và dọn sạch dữ liệu trên máy.");
    render();
  }

  function duplicateSession(sessionId) {
    const source = state.sessions.find(item => item.sessionId === sessionId);
    if (!source) return;
    const copy = {
      ...source,
      sessionId: `session-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      title: `${source.title} (bản sao)`,
      status: "DRAFT",
      progressPercent: 0,
      activeStep: "Chưa chạy",
      updatedAt: Date.now(),
      linkedJobId: "",
      summary: "Bản sao của phiên trước, chờ chạy lại.",
      reviewStatus: "PENDING",
      pendingTaskId: "",
      pendingLabel: "",
      pendingState: "",
      lastPage: source.lastPage || "content"
    };
    state.sessions.unshift(copy);
    persistSessions();
    setTransient("Đã nhân bản phiên.");
    render();
  }

  function render() {
    const restoreKey = getScrollMemoryKey();
    const previousScrollTop = readCurrentScrollTop();
    const settingsRestoreKey = getSettingsScrollMemoryKey();
    const previousSettingsScrollTop = readCurrentSettingsScrollTop();
    root.classList.toggle("hidden", !state.open);
    root.setAttribute("aria-hidden", state.open ? "false" : "true");
    if (!state.open) {
      stopJobPolling();
      root.innerHTML = "";
      return;
    }
    ensureJobPolling();
    root.innerHTML = `
      <div class="video-workspace-shell" role="dialog" aria-modal="true" aria-label="${escapeHtml(getShellTitle())}">
        ${renderHeader()}
        <div class="video-workspace-body">
          ${state.currentView === "home" ? renderHomeView() : renderWorkspaceView()}
        </div>
        ${state.navOpen ? renderNavSheet() : ""}
        ${state.settingsOpen ? renderSettingsSheet() : ""}
        ${state.timelineEditor && state.timelineEditor.open ? renderTimelineEditor() : ""}
      </div>
    `;
    updateTopicMeta();
    updateVoiceSliderLabels();
    restoreScrollPosition(restoreKey, previousScrollTop);
    if (state.settingsOpen) {
      restoreSettingsScrollPosition(settingsRestoreKey, previousSettingsScrollTop);
    }
    if (state.timelineEditor && state.timelineEditor.open) updateTimelineLayout();
  }

  function renderHeader() {
    if (state.currentView === "home") {
      return `
        <header class="video-workspace-head">
          <span class="video-workspace-icon" aria-hidden="true">▶</span>
          <div class="video-workspace-head-copy">
            <h2>lqlq</h2>
          </div>
          <div class="video-workspace-head-actions">
            <button class="video-workspace-secondary" type="button" data-video-action="create-session">＋ Tạo dự án</button>
            <button class="video-workspace-icon-button" type="button" data-video-action="open-settings" aria-label="Cài đặt tự động">⚙</button>
            <button class="video-workspace-icon-button" type="button" id="videoWorkspaceCloseBtn" data-video-action="close" aria-label="Đóng">✕</button>
          </div>
        </header>
      `;
    }
    const session = getActiveSession();
    return `
      <header class="video-workspace-head">
        <button class="video-workspace-icon-button" type="button" data-video-action="back-home" aria-label="Quay lại">‹</button>
        <div class="video-workspace-head-copy">
          <h2>${escapeHtml(session?.title || "Dự án video")}</h2>
          <p>${escapeHtml(buildWorkspaceSubtitle(session))}</p>
        </div>
        <span class="video-workspace-status" data-state="${escapeHtml(session?.status || "DRAFT")}">${escapeHtml(formatSessionStatus(session?.status || "DRAFT"))}</span>
        <div class="video-workspace-head-actions">
          <button class="video-workspace-icon-button" type="button" data-video-action="toggle-nav" aria-label="Mở menu bảng">☰</button>
          <button class="video-workspace-icon-button" type="button" id="videoWorkspaceCloseBtn" data-video-action="close" aria-label="Đóng">✕</button>
        </div>
      </header>
    `;
  }

  function renderHomeView() {
    const sessions = getFilteredSessions();
    return `
      <div class="video-session-filter">
        ${FILTERS.map(filter => `
          <button class="video-workspace-pill ${state.filter === filter ? "active" : ""}" type="button" data-video-action="set-filter" data-filter="${filter}">
            ${escapeHtml(formatFilterLabel(filter))}
          </button>
        `).join("")}
      </div>
      ${sessions.length ? `<div class="video-session-list" id="videoSessionList">${sessions.map(renderSessionCard).join("")}</div>` : renderEmptyState()}
    `;
  }

  function renderSessionCard(session) {
    return `
      <article class="video-session-card" id="videoSessionCard-${escapeHtml(session.sessionId)}">
        <div class="video-session-card-head">
          <div>
            <b>${escapeHtml(session.title)}</b>
            <small>${escapeHtml(session.activeStep)} · ${escapeHtml(session.summary || "Chưa có mô tả")}</small>
          </div>
          <span class="video-workspace-status" data-state="${escapeHtml(session.status)}">${escapeHtml(formatSessionStatus(session.status))}</span>
        </div>
        <div class="video-session-progress">
          <span style="width:${Math.max(0, Math.min(100, Number(session.progressPercent || 0)))}%"></span>
        </div>
        <div class="video-session-card-meta">
          <span>${escapeHtml(formatProgressCopy(session))}</span>
          <span>${escapeHtml(formatRelativeTime(session.updatedAt))}</span>
        </div>
        ${isSessionBusy(session) ? `<div class="video-settings-state" style="margin-top:8px;">⏳ ${escapeHtml(session.pendingLabel || fallbackBusyLabel(session))}</div>` : ""}
        <div class="video-workspace-footer">
          ${renderSessionActions(session)}
        </div>
      </article>
    `;
  }

  function renderSessionActions(session) {
    const buttons = [];
    if (!session.pendingTaskId && (session.status === "QUEUED" || String(session.pendingState || "").toUpperCase() === "QUEUED")) {
      buttons.push(actionButton("open-session", "Mở dự án", session.sessionId, "secondary"));
      buttons.push(actionButton("remove-queue", "Bỏ khỏi hàng đợi", session.sessionId, "ghost"));
    } else if (isSessionBusy(session)) {
      buttons.push(actionButton("open-session", "Mở", session.sessionId, "secondary"));
      buttons.push(actionButton("stop-session", "Dừng", session.sessionId, "ghost"));
    } else if (session.status === "DRAFT") {
      buttons.push(actionButton("open-session", "Chỉnh sửa", session.sessionId, "secondary"));
      buttons.push(actionButton("run-session", "Chạy", session.sessionId, "primary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "danger"));
    } else if (session.status === "QUEUED") {
      buttons.push(actionButton("open-session", "Mở dự án", session.sessionId, "secondary"));
      buttons.push(actionButton("remove-queue", "Bỏ khỏi hàng đợi", session.sessionId, "ghost"));
    } else if (session.status === "RUNNING") {
      buttons.push(actionButton("open-session", "Mở", session.sessionId, "secondary"));
      buttons.push(actionButton("stop-session", "Dừng", session.sessionId, "ghost"));
    } else if (session.status === "PAUSED") {
      buttons.push(actionButton("open-session", "Chỉnh sửa", session.sessionId, "secondary"));
      buttons.push(actionButton("run-session", "Tiếp tục", session.sessionId, "primary"));
    } else if (session.status === "WAITING_USER") {
      buttons.push(actionButton("open-session", "Mở kiểm tra", session.sessionId, "secondary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "ghost"));
    } else if (session.status === "COMPLETED") {
      buttons.push(actionButton("open-session", "Xem kết quả", session.sessionId, "secondary"));
      buttons.push(actionButton("duplicate-session", "Nhân bản", session.sessionId, "primary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "ghost"));
    } else {
      buttons.push(actionButton("open-session", "Xem lỗi", session.sessionId, "secondary"));
      // Neu da co noi dung (Gemini xong roi) -> "Thu lai" chi TIEP TUC tu buoc loi
      // (tim anh con thieu -> giong -> video), KHONG goi lai Gemini. Chi khi chua co
      // noi dung moi chay lai tu dau.
      const failedJob = state.jobs[session.sessionId];
      const hasContent = Array.isArray(failedJob?.scenePrompts) && failedJob.scenePrompts.length > 0;
      if (hasContent) {
        buttons.push(actionButton("resume-session", "Thử lại (tiếp tục, giữ nội dung & ảnh)", session.sessionId, "primary"));
      } else {
        buttons.push(actionButton("run-session", "Thử lại từ đầu", session.sessionId, "primary"));
      }
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "danger"));
    }
    return buttons.join("");
  }

  function actionButton(action, label, sessionId, tone, disabled = false) {
    const className = tone === "primary"
      ? "video-workspace-primary"
      : tone === "danger"
        ? "video-workspace-danger"
        : tone === "ghost"
          ? "video-workspace-ghost"
          : "video-workspace-secondary";
    return `<button class="${className}" type="button" data-video-action="${action}" data-session-id="${escapeHtml(sessionId)}"${disabled ? " disabled" : ""}>${escapeHtml(label)}</button>`;
  }

  function renderEmptyState() {
    return `
      <section class="video-session-empty">
        <b>Chưa có dự án video nào</b>
        <p class="video-muted-copy">Tạo một dự án để bắt đầu từ chủ đề, hình ảnh, giọng đọc đến video hoàn chỉnh.</p>
        <div class="video-workspace-footer">
          <button class="video-workspace-primary" type="button" data-video-action="create-session">＋ Tạo dự án</button>
        </div>
      </section>
    `;
  }

  function renderWorkspaceView() {
    const session = getActiveSession();
    // KHÔNG hiện banner tiến trình dài ở đây nữa (che hết không gian làm việc) — chỉ
    // hiện 1 dòng nhỏ gọn khi đang chạy. Trạng thái đầy đủ xem ở danh sách phiên bên
    // ngoài (mỗi phiên có nhãn "đang chạy/đang chờ").
    return `
      <div class="video-page-grid">
        ${session?.pendingTaskId ? `<div class="video-mini-pill" style="align-self:flex-start;">⏳ Đang chạy nền (${escapeHtml(Math.max(0, Math.min(100, Number(session.progressPercent || 0))))}%) — xem chi tiết ở danh sách phiên</div>` : ""}
        ${renderWorkspacePage(session)}
      </div>
    `;
  }

  function renderWorkspacePage(session) {
    if (session) {
      session.lastPage = state.activePage;
    }
    switch (state.activePage) {
      case "content": return renderContentPage(session);
      case "images": return renderImagesPage(session);
      case "voice": return renderVoicePage(session);
      case "video": return renderVideoPage(session);
      case "metadata": return renderMetadataPage(session);
      case "review": return renderReviewPage(session);
      case "publish": return renderPublishPage(session);
      case "progress": return renderProgressPage(session);
      case "logs": return renderLogsPage(session);
      case "info": return renderInfoPage(session);
      default: return renderContentPage(session);
    }
  }

  function renderContentPage(session) {
    const job = getJobForSession(session);
    const parsedCount = Array.isArray(job?.scenePrompts) ? job.scenePrompts.length : 0;
    const imageBySceneId = new Map();
    filterArtifacts(job, "IMAGE").forEach(a => { if (a.sceneId) imageBySceneId.set(a.sceneId, a); });
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Nội dung</b>
            <small>Nhập chủ đề rồi "Chạy Tự động" để làm hết, hoặc "Tạo nội dung" để chỉ tạo kịch bản trước (rồi tự sửa tay nếu muốn).</small>
          </div>
          <span class="video-mini-pill">${escapeHtml(formatSessionStatus(session.status))}</span>
        </div>
        ${renderPipelineStepper(job)}

        <div class="video-field" style="margin-top:12px;">
          <span>Chủ đề hoặc yêu cầu video</span>
          <textarea class="video-textarea" id="videoSessionTopicInput" placeholder="Ví dụ: 10 câu nói giúp bạn giao tiếp tốt hơn">${escapeHtml(session.topic || "")}</textarea>
          <small class="video-field-note" id="videoSessionTopicMeta">0 ký tự</small>
        </div>

        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-field">
            <span>Nền tảng</span>
            <select class="video-select" id="videoPlatformSelect">
              ${PLATFORM_OPTIONS.map(option => `<option value="${option.value}"${session.config.platform === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}
            </select>
          </div>
          <div class="video-field">
            <span>Thời lượng mong muốn (giây)</span>
            <input class="video-input" id="videoDurationSeconds" type="number" min="5" max="600" value="${escapeHtml(getSessionDurationSeconds(session))}">
          </div>
          <div class="video-field">
            <span>Ngôn ngữ</span>
            <select class="video-select" id="videoLanguageSelect">
              <option${session.config.language === "Tiếng Việt" ? " selected" : ""}>Tiếng Việt</option>
              <option${session.config.language === "English" ? " selected" : ""}>English</option>
            </select>
          </div>
        </div>

        <div class="video-settings-state" style="margin-top:12px;">
          Thời lượng gửi thẳng xuống pipeline: ${escapeHtml(getSessionDurationSeconds(session))} giây. Gemini tự quyết định số cảnh và viết đủ lời đọc ít nhất bằng thời lượng này; dài hơn được phép, ngắn hơn không đạt.
          Khung hình, chất lượng, nền ảnh và kiểu chuyển cảnh giờ nằm trong Cài đặt tự động &gt; Video.
        </div>

        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="open-settings">⚙ Cài đặt tự động</button>
          ${/* Tạm ẩn nút debug "🧪 POC web" — mỗi lần bấm phải soạn nội dung lại từ đầu
                nên gây phiền, tự động hoá không cần thao tác này. Giữ nguyên
                data-video-action="poc-gemini-web" trong switch-case để bật lại
                nhanh khi cần debug, chỉ ẩn nút khỏi UI. */ ""}
          ${isSessionBusy(session)
            ? `<button class="video-workspace-danger" type="button" data-video-action="pause-session">⏸ Tạm dừng</button>`
            : `
              <button class="video-workspace-secondary" type="button" data-video-action="generate-content-only">Tạo nội dung</button>
              ${session.status === "PAUSED"
                ? `<button class="video-workspace-primary" type="button" data-video-action="generate-content">▶ Tiếp tục</button>`
                : parsedCount
                  ? `<button class="video-workspace-primary" type="button" data-video-action="generate-content">▶ Tự động chạy tiếp</button>
                     <button class="video-workspace-ghost" type="button" data-video-action="force-restart">Chạy lại từ đầu (xoá & tạo lại)</button>`
                  : `<button class="video-workspace-primary" type="button" data-video-action="generate-content">Chạy Tự động</button>`}
            `}
        </div>
        ${isSessionBusy(session)
          ? `<div class="video-settings-state" style="margin-top:8px;">⏳ ${session.pendingTaskId ? 'Đang chạy — bấm "Tạm dừng" để dừng, sau đó có nút "Tự động chạy tiếp" để nối lại.' : 'Phiên vẫn đang ở trạng thái chạy/chờ. Nếu nút chưa bắt lại đúng tác vụ nền cũ, app sẽ thử tìm lại tác vụ theo job hiện có khi bạn bấm "Tạm dừng".'}</div>`
          : ""}
      </section>

      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Kịch bản AI</b>
            <small>${escapeHtml(job?.runtimeMessage || "Sau khi chạy, kịch bản và danh sách cảnh sẽ hiện ở đây.")}</small>
          </div>
          <span class="video-mini-pill">${escapeHtml(job?.providerId || "Chưa có provider")}</span>
        </div>
        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-settings-state">Số ký tự script: ${escapeHtml((job?.generatedText || "").length)}</div>
          <div class="video-settings-state">Số cảnh (Gemini quyết định): ${escapeHtml(parsedCount)}</div>
        </div>
        <div class="video-page-card" style="margin-top:12px;">
          <div class="video-muted-copy">${multilineHtml(job?.generatedText || "Chưa có nội dung được tạo.")}</div>
        </div>
      </section>

      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Danh sách cảnh</b>
            <small>Sửa lời đọc/tiêu đề từng cảnh nếu muốn, rồi "Chạy lại" để tạo lại giọng + video (giữ nội dung & ảnh, không tạo lại từ đầu).</small>
          </div>
          ${parsedCount ? renderUpdateChangesButton(job, session) : ""}
        </div>
        <div class="video-scene-list" style="margin-top:12px;">
          ${parsedCount ? job.scenePrompts.map(scene => renderScenePromptCard(scene, imageBySceneId.get(scene.sceneId))).join("") : `<div class="video-settings-state">Chưa có scene prompts cho phiên này.</div>`}
          ${parsedCount ? renderAddSceneForm() : ""}
        </div>
      </section>
    `;
  }

  function renderPipelineStepper(job) {
    const stepMap = new Map((job?.steps || []).map(s => [s.stepType, s.status]));
    const stages = [
      { key: "CONTENT", label: "Nội dung" },
      { key: "IMAGES_VISUALS", label: "Ảnh" },
      { key: "VOICE", label: "Giọng" },
      { key: "VIDEO", label: "Video" },
      { key: "PUBLISH", label: "Đăng" }
    ];
    const styleFor = (st) => {
      if (st === "COMPLETED") return "background:#0e9f58;color:#fff";
      if (st === "RUNNING") return "background:#0e9f58;color:#fff";
      if (st === "WAITING_USER" || st === "QUEUED") return "background:#fff0cb;color:#8a6400";
      if (st === "FAILED" || st === "CANCELLED") return "background:#fee7e4;color:#a13a31";
      return "background:#eef1f5;color:#7a8698";
    };
    const iconFor = (st) => st === "COMPLETED" ? "✓" : (st === "RUNNING" ? "●" : (st === "FAILED" || st === "CANCELLED" ? "✕" : "○"));
    return `
      <div style="display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin-top:10px;">
        ${stages.map((s, i) => {
          const st = stepMap.get(s.key) || "PENDING";
          const sep = i > 0 ? `<span style="opacity:.35;">›</span>` : "";
          return `${sep}<span class="video-mini-pill" style="${styleFor(st)}">${iconFor(st)} ${escapeHtml(s.label)}</span>`;
        }).join("")}
      </div>
    `;
  }

  // Nút "Cập nhật thay đổi" ĐỘNG: đọc trạng thái STALE để chỉ làm phần cần làm,
  // đổi tên nút theo tình huống — không bao giờ gọi lại Gemini, không dựng lại
  // ảnh đã có (trừ ảnh còn thiếu).
  function renderUpdateChangesButton(job, session) {
    const scenes = job?.scenePrompts || [];
    const missing = scenes.filter(s => (s.imageStatus || "MISSING") === "MISSING").length;
    const stale = scenes.filter(s => s.imageStatus === "STALE").length;
    const voiceStale = job?.voiceStatus === "STALE" || job?.voiceStatus === "MISSING";
    const videoStale = job?.videoStatus === "STALE" || job?.videoStatus === "MISSING";
    const dis = session.pendingTaskId ? " disabled" : "";
    let btn;
    if (missing > 0) {
      btn = `<button class="video-workspace-primary" type="button" data-video-action="continue-auto"${dis}>▶ Tạo ${missing} ảnh còn thiếu → giọng → video</button>`;
    } else if (voiceStale && videoStale) {
      btn = `<button class="video-workspace-primary" type="button" data-video-action="rerun-voice-video"${dis}>▶ Cập nhật giọng + video</button>`;
    } else if (videoStale) {
      btn = `<button class="video-workspace-primary" type="button" data-video-action="rerun-voice-video"${dis}>▶ Cập nhật video</button>`;
    } else {
      btn = `<button class="video-workspace-secondary" type="button" data-video-action="rerun-voice-video"${dis}>▶ Chạy lại (giữ nội dung & ảnh)</button>`;
    }
    const note = stale > 0
      ? `<div class="video-settings-state" style="margin-top:6px;">⚠ ${stale} cảnh có "Ảnh cũ" (từ khoá đã đổi) — bấm "Thay ảnh cảnh này" ở cảnh đó nếu muốn cào lại ảnh khớp từ khoá mới.</div>`
      : "";
    return btn + note;
  }

  function sceneImageBadge(status) {
    if (status === "STALE") return `<span class="video-mini-pill" style="background:#fff0cb;color:#8a6400">! Ảnh cũ (từ khoá đã đổi)</span>`;
    if (status === "MISSING") return `<span class="video-mini-pill" style="background:#eef1f0;color:#6a7a72">○ Chưa có ảnh</span>`;
    return `<span class="video-mini-pill" style="background:#d9f8e6;color:#0b8449">✓ Ảnh sẵn sàng</span>`;
  }

  function renderScenePromptCard(scene, imageArtifact) {
    const sid = escapeHtml(scene.sceneId || "");
    const status = scene.imageStatus || (imageArtifact ? "READY" : "MISSING");
    return `
      <details class="video-scene-card">
        <summary>
          <b>Cảnh ${escapeHtml(scene.ordinal)}</b>
          <div class="video-scene-meta">
            ${sceneImageBadge(status)}
            <span class="video-mini-pill">${escapeHtml((scene.onScreenText || "").slice(0, 20) || scene.aspectRatio || "9:16")}</span>
          </div>
        </summary>
        <div style="margin-top:10px;">
          ${imageArtifact?.previewDataUrl
            ? `<img src="${escapeHtml(imageArtifact.previewDataUrl)}" alt="Ảnh cảnh ${escapeHtml(scene.ordinal)}" style="width:100%;max-height:180px;object-fit:cover;border-radius:10px;">`
            : `<div class="video-settings-state">Cảnh này chưa có ảnh.</div>`}
          <div class="video-workspace-footer" style="margin-top:8px;">
            <button class="video-workspace-primary" type="button" data-video-action="rescrape-scene-image" data-scene-id="${sid}">🔍 Lấy lại ảnh theo từ khoá (web)</button>
            <button class="video-workspace-secondary" type="button" data-video-action="replace-scene-image" data-scene-id="${sid}">🖼 Chọn ảnh từ máy</button>
            ${imageArtifact ? `<button class="video-workspace-secondary" type="button" data-video-action="download-scene-image" data-scene-id="${sid}">⬇ Tải ảnh về máy</button>` : ""}
            <button class="video-workspace-ghost" type="button" data-video-action="move-scene-up" data-scene-id="${sid}">▲ Lên</button>
            <button class="video-workspace-ghost" type="button" data-video-action="move-scene-down" data-scene-id="${sid}">▼ Xuống</button>
          </div>
          <div class="video-field" style="margin-top:8px;">
            <span>Tiêu đề cảnh (hiện trên video)</span>
            <input class="video-input" id="sceneTitle-${sid}" value="${escapeHtml(scene.onScreenText || "")}" placeholder="Vd: Goku">
          </div>
          <div class="video-field" style="margin-top:8px;">
            <span>Lời đọc của cảnh (giọng đọc + phụ đề)</span>
            <textarea class="video-textarea" id="sceneVoice-${sid}">${escapeHtml(scene.voiceText || "")}</textarea>
          </div>
          <div class="video-field" style="margin-top:8px;">
            <span>Từ khoá tìm ảnh (Pinterest/ChatGPT dùng cái này)</span>
            <input class="video-input" id="sceneQuery-${sid}" value="${escapeHtml(scene.stockSearchQuery || "")}" placeholder="Vd: Goku Dragon Ball">
          </div>
          <div class="video-workspace-footer">
            <button class="video-workspace-primary" type="button" data-video-action="save-scene-text" data-scene-id="${sid}">Lưu cảnh này</button>
            <button class="video-workspace-danger" type="button" data-video-action="delete-scene" data-scene-id="${sid}">Xoá cảnh</button>
          </div>
        </div>
      </details>
    `;
  }

  function renderAddSceneForm() {
    return `
      <details class="video-scene-card" style="margin-top:8px;">
        <summary><b>＋ Thêm cảnh mới</b></summary>
        <div style="margin-top:10px;">
          <div class="video-field"><span>Tiêu đề cảnh (tuỳ chọn)</span><input class="video-input" id="newSceneTitle" placeholder="Vd: Goku"></div>
          <div class="video-field" style="margin-top:8px;"><span>Lời đọc của cảnh</span><textarea class="video-textarea" id="newSceneVoice" placeholder="Nội dung sẽ được đọc + hiện phụ đề"></textarea></div>
          <div class="video-field" style="margin-top:8px;"><span>Từ khoá tìm ảnh</span><input class="video-input" id="newSceneQuery" placeholder="Vd: Goku Dragon Ball"></div>
          <div class="video-workspace-footer">
            <button class="video-workspace-primary" type="button" data-video-action="add-scene">＋ Thêm cảnh</button>
          </div>
          <div class="video-settings-state" style="margin-top:6px;">Cần ít nhất Lời đọc hoặc Từ khoá. Thêm xong nhớ cào ảnh cho cảnh mới rồi "Chạy lại".</div>
        </div>
      </details>
    `;
  }

  function renderImagesPage(session) {
    const job = getJobForSession(session);
    const images = filterArtifacts(job, "IMAGE");
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Hình ảnh</b>
            <small>${escapeHtml(images.length)}/${escapeHtml((job?.scenePrompts || []).length)} cảnh đã có visual.</small>
          </div>
          <div class="video-inline-actions">
            <button class="video-workspace-secondary" type="button" data-video-action="import-image">＋ Thêm ảnh từ máy</button>
            <button class="video-workspace-primary" type="button" data-video-action="retry-image">Tạo/lấy lại ảnh</button>
          </div>
        </div>
      </section>
      <section class="video-page-card">
        <div class="video-artifact-grid">
          ${images.length ? images.map(renderImageCard).join("") : `<div class="video-settings-state">Chưa có ảnh nào cho phiên này.</div>`}
        </div>
      </section>
    `;
  }

  function renderImageCard(artifact) {
    const info = parseSourceUrlFields(artifact.sourceUrl);
    return `
      <article class="video-artifact-card">
        ${artifact.previewDataUrl ? `<img class="video-artifact-thumb" src="${escapeHtml(artifact.previewDataUrl)}" alt="Ảnh cảnh ${escapeHtml(artifact.ordinal || "")}">` : ""}
        <div style="margin-top:10px;">
          <b>Cảnh ${escapeHtml(artifact.ordinal || "-")}</b>
          <small class="video-muted-copy">${escapeHtml(info.query || info.provider || artifact.uri || "Chưa có query")}</small>
          ${info.photoId ? `<small class="video-muted-copy">Photo ID: ${escapeHtml(info.photoId)}</small>` : ""}
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="replace-image">Thay ảnh</button>
          <button class="video-workspace-ghost" type="button" data-video-action="retry-image">Tạo lại cảnh</button>
        </div>
      </article>
    `;
  }

  function renderVoicePage(session) {
    const job = getJobForSession(session);
    const voice = filterArtifacts(job, "VOICE")[0];
    const info = parseSourceUrlFields(voice?.sourceUrl);
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Giọng đọc</b>
            <small>Hiển thị artifact âm thanh thật của phiên và cho phép retry riêng bước VOICE.</small>
          </div>
          <div class="video-inline-actions">
            <button class="video-workspace-secondary" type="button" data-video-action="open-settings">Điều chỉnh giọng</button>
            <button class="video-workspace-primary" type="button" data-video-action="retry-voice">Tạo lại giọng đọc</button>
          </div>
        </div>
      </section>
      <section class="video-page-card">
        ${voice ? `
          <div class="video-info-grid">
            <div class="video-settings-state">Provider: ${escapeHtml(info.provider || "VOICE")}</div>
            <div class="video-settings-state">Voice: ${escapeHtml(info.voice || "-")}</div>
            <div class="video-settings-state">Locale: ${escapeHtml(info.locale || "-")}</div>
            <div class="video-settings-state">Duration: ${escapeHtml(formatDurationMs(info.durationMs))}</div>
            <div class="video-settings-state">Chunks: ${escapeHtml(info.chunks || "-")}</div>
          </div>
          ${voice.previewDataUrl ? `<audio class="video-artifact-audio" controls preload="metadata" src="${escapeHtml(voice.previewDataUrl)}"></audio>` : ""}
        ` : `<div class="video-settings-state">Chưa có voice artifact cho phiên này.</div>`}
      </section>
    `;
  }

  function renderVideoPage(session) {
    const job = getJobForSession(session);
    const videoArtifact = filterArtifacts(job, "VIDEO_MP4")[0] || filterArtifacts(job, "VIDEO")[0];
    const planArtifact = filterArtifacts(job, "VIDEO_RENDER_PLAN")[0];
    const voice = filterArtifacts(job, "VOICE")[0];
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Xem trước video</b>
            <small>Nếu chưa có MP4 thì hiển thị render plan summary để chuẩn bị cho bước video.</small>
          </div>
          <div class="video-inline-actions">
            <button class="video-workspace-primary" type="button" data-video-action="open-timeline">🎬 Chỉnh video (Timeline)</button>
            <button class="video-workspace-secondary" type="button" data-video-action="render-video">Render video</button>
            <button class="video-workspace-secondary" type="button" data-video-action="retry-video">Render lại</button>
            ${videoArtifact ? `<button class="video-workspace-primary" type="button" data-video-action="preview-video">▶ Xem trước</button>` : ""}
            ${videoArtifact ? `<button class="video-workspace-secondary" type="button" data-video-action="export-video">Export MP4</button>` : ""}
          </div>
        </div>
      </section>
      <section class="video-page-card">
        ${videoArtifact ? `
          <div class="video-settings-state">Đã có VIDEO_MP4. Bấm "▶ Xem trước" để xem ngay trong app.</div>
          ${videoArtifact.previewDataUrl ? `<video class="video-artifact-thumb" controls preload="metadata" src="${escapeHtml(videoArtifact.previewDataUrl)}"></video>` : ""}
        ` : planArtifact ? `
          <div class="video-settings-state">Đã có VIDEO_RENDER_PLAN. Sẵn sàng đi tiếp khi bước render thật được nối hoàn chỉnh.</div>
          <div class="video-info-grid" style="margin-top:12px;">
            <div class="video-settings-state">Số ảnh: ${escapeHtml(filterArtifacts(job, "IMAGE").length)}</div>
            <div class="video-settings-state">Giọng đọc: ${escapeHtml(voice ? "Đã có" : "Chưa có")}</div>
            <div class="video-settings-state">Render plan URI: ${escapeHtml(planArtifact.uri || "-")}</div>
          </div>
        ` : `<div class="video-settings-state">Chưa có video hoặc render plan cho phiên này.</div>`}
      </section>
    `;
  }

  // ================= TIMELINE EDITOR (PASS 2: read-only) =================
  // Mở khung dựng video toàn màn hình, dựng từ getTimelineProject (build từ dữ liệu
  // hiện có). PASS 2 chỉ XEM: preview + playhead + các track. Kéo/cắt ở PASS sau.
  function openTimelineEditor() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.getTimelineProject) {
      setError("Chưa có dữ liệu để dựng timeline (cần đã có cảnh/ảnh).");
      render();
      return;
    }
    const response = parseResponse(bridge.getTimelineProject(session.linkedJobId));
    if (!response.ok || !response.timeline) {
      setError(response.message || "Không dựng được timeline.");
      render();
      return;
    }
    state.timelineEditor = {
      open: true,
      project: response.timeline,
      playheadMs: 0,
      pxPerSec: 40,
      playing: false,
      selectedClipId: null
    };
    render();
  }

  function closeTimelineEditor() {
    if (state.timelineEditor?._raf) cancelAnimationFrame(state.timelineEditor._raf);
    const audio = root.querySelector("#timelineAudio");
    if (audio) { try { audio.pause(); } catch (e) {} }
    state.timelineEditor = null;
    render();
  }

  function timelineZoom(factor) {
    if (!state.timelineEditor) return;
    const cur = state.timelineEditor.pxPerSec || 40;
    state.timelineEditor.pxPerSec = Math.max(8, Math.min(240, Math.round(cur * factor)));
    render();
  }

  // Phát xem trước: audio giọng đọc làm ĐỒNG HỒ, timeline TỰ CUỘN sang trái để
  // vạch playhead (cố định giữa) quét qua các cảnh; ảnh + tiêu đề + phụ đề đổi theo.
  function timelinePlayPause() {
    if (!state.timelineEditor) return;
    if (state.timelineEditor.playing) { timelineStopPlayback(); return; }
    const total = timelineTotalMs();
    let startMs = state.timelineEditor.playheadMs || 0;
    if (startMs >= total - 20) { startMs = 0; state.timelineEditor.playheadMs = 0; }
    state.timelineEditor.playing = true;
    state.timelineEditor._suppressScroll = true;
    render(); // đổi nút sang ⏸ + updateTimelineLayout đặt scroll về playhead
    const scroll = root.querySelector("#timelineScroll");
    const audio = root.querySelector("#timelineAudio");
    const hasAudio = audio && audio.src;
    if (hasAudio) {
      try {
        audio.currentTime = startMs / 1000;
        const p = audio.play();
        if (p && p.catch) p.catch(err => setTransient("Trình duyệt chặn phát audio: " + (err && err.name ? err.name : "lỗi") + ". Video vẫn chạy."));
      } catch (e) {}
    } else {
      setTransient("Phiên này chưa có file giọng đọc để phát (chỉ chạy hình).");
    }
    const perfStart = performance.now();
    const tick = () => {
      if (!state.timelineEditor || !state.timelineEditor.playing) return;
      let ms;
      if (hasAudio && !audio.paused) ms = audio.currentTime * 1000;
      else ms = startMs + (performance.now() - perfStart);
      if (ms >= total) { timelineSeekScroll(total); timelineStopPlayback(); return; }
      timelineSeekScroll(ms);
      state.timelineEditor._raf = requestAnimationFrame(tick);
    };
    state.timelineEditor._raf = requestAnimationFrame(tick);
  }

  // Đặt playhead theo ms trong lúc phát: cuộn timeline + cập nhật preview (scroll bị
  // suppress nên không sinh vòng lặp sự kiện).
  function timelineSeekScroll(ms) {
    const scroll = root.querySelector("#timelineScroll");
    if (scroll) scroll.scrollLeft = timelineMsToPx(ms);
    setTimelinePlayhead(ms);
  }

  function timelineStopPlayback() {
    if (!state.timelineEditor) return;
    if (state.timelineEditor._raf) { cancelAnimationFrame(state.timelineEditor._raf); state.timelineEditor._raf = null; }
    const audio = root.querySelector("#timelineAudio");
    if (audio) { try { audio.pause(); } catch (e) {} }
    state.timelineEditor.playing = false;
    state.timelineEditor._suppressScroll = false;
    render();
  }

  function timelineSelectClip(clipId) {
    if (!state.timelineEditor) return;
    state.timelineEditor.selectedClipId = clipId;
    const clip = (state.timelineEditor.project.clips || []).find(c => c.clipId === clipId);
    if (clip) state.timelineEditor.playheadMs = clip.startMs;
    render();
  }

  function timelineSelectedSceneId() {
    const id = state.timelineEditor?.selectedClipId;
    const clip = (state.timelineEditor?.project?.clips || []).find(c => c.clipId === id);
    return clip?.sceneId || null;
  }

  function reloadTimelineProject() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.getTimelineProject || !state.timelineEditor) return;
    const r = parseResponse(bridge.getTimelineProject(session.linkedJobId));
    if (r.ok && r.timeline) {
      state.timelineEditor.project = r.timeline;
      state.timelineEditor.selectedClipId = null;
      state.timelineEditor.playheadMs = Math.min(state.timelineEditor.playheadMs || 0, r.timeline.totalDurationMs || 0);
    }
  }

  function timelineReplaceImage() {
    const sceneId = timelineSelectedSceneId();
    if (!sceneId) { setTransient("Chọn 1 cảnh ở track Hình trước."); render(); return; }
    replaceSceneImageForActiveSession(sceneId);
  }

  function timelineDeleteClip() {
    const sceneId = timelineSelectedSceneId();
    const session = getActiveSession();
    if (!sceneId || !session?.linkedJobId || !bridge?.deleteScene) return;
    const resp = parseResponse(bridge.deleteScene(JSON.stringify({ jobId: session.linkedJobId, sceneId })));
    if (!resp.ok) { setError(resp.message || "Không xoá được cảnh."); render(); return; }
    refreshJobForSession(session);
    reloadTimelineProject();
    setError("");
    setTransient("Đã xoá cảnh khỏi timeline.");
    render();
  }

  function timelineExport() {
    const session = getActiveSession();
    if (!session?.linkedJobId) return;
    timelineStopPlayback();
    closeTimelineEditor();
    setTransient("Đang dựng & xuất video theo bản đã chỉnh...");
    retryVideoForActiveSession();
  }

  // Tách clip đang chọn TẠI vạch playhead (giữa màn hình).
  function timelineSplitAtPlayhead() {
    const sceneId = timelineSelectedSceneId();
    const session = getActiveSession();
    if (!sceneId || !session?.linkedJobId || !bridge?.splitScene) return;
    const clip = (state.timelineEditor.project.clips || []).find(c => c.sceneId === sceneId);
    if (!clip) return;
    const ph = state.timelineEditor.playheadMs || 0;
    const offset = Math.round(ph - clip.startMs);
    if (offset < 400 || offset > clip.durationMs - 400) {
      setTransient("Đưa vạch trắng vào GIỮA cảnh đang chọn rồi bấm Tách (cách mép ≥ 0.4s).");
      render(); return;
    }
    const resp = parseResponse(bridge.splitScene(JSON.stringify({ jobId: session.linkedJobId, sceneId, offsetMs: offset })));
    if (!resp.ok) { setTransient(resp.message || "Không tách được."); render(); return; }
    refreshJobForSession(session);
    reloadTimelineProject();
    setTransient("Đã tách cảnh tại vạch playhead.");
    render();
  }

  function timelineMoveClip(direction) {
    const sceneId = timelineSelectedSceneId();
    const session = getActiveSession();
    if (!sceneId || !session?.linkedJobId || !bridge?.moveScene) return;
    const resp = parseResponse(bridge.moveScene(JSON.stringify({ jobId: session.linkedJobId, sceneId, direction })));
    if (!resp.ok) { setTransient(resp.message || "Không đổi được thứ tự."); render(); return; }
    refreshJobForSession(session);
    const keepSel = state.timelineEditor.selectedClipId;
    reloadTimelineProject();
    state.timelineEditor.selectedClipId = keepSel;
    setTransient("Đã đổi thứ tự cảnh.");
    render();
  }

  function timelineEditTitle() {
    const sceneId = timelineSelectedSceneId();
    const session = getActiveSession();
    if (!sceneId || !session?.linkedJobId || !bridge?.updateSceneText) return;
    const clip = (state.timelineEditor.project.clips || []).find(c => c.sceneId === sceneId);
    const cap = (state.timelineEditor.project.captions || []).find(c => c.kind === "title" && c.sceneId === sceneId);
    const current = cap?.text || "";
    const next = window.prompt("Tiêu đề hiện trên video cho cảnh này:", current);
    if (next == null) return;
    const resp = parseResponse(bridge.updateSceneText(JSON.stringify({
      jobId: session.linkedJobId, sceneId, onScreenText: next.trim()
    })));
    if (!resp.ok) { setError(resp.message || "Không sửa được tiêu đề."); render(); return; }
    refreshJobForSession(session);
    const keepSel = state.timelineEditor.selectedClipId;
    reloadTimelineProject();
    state.timelineEditor.selectedClipId = keepSel;
    setError("");
    setTransient("Đã sửa tiêu đề cảnh.");
    render();
  }

  function timelineMusicVolume() {
    const session = getActiveSession();
    if (!bridge?.setBackgroundMusicOptions) { setTransient("Chưa hỗ trợ chỉnh nhạc ở shell này."); render(); return; }
    const cur = Math.round(((state.timelineEditor?.project?.musicTrack?.gain) ?? 0.35) * 100);
    const input = window.prompt("Âm lượng nhạc nền (0–200%). 100 = gốc:", String(cur));
    if (input == null) return;
    const vol = Math.max(0, Math.min(200, Number(input) || 0)) / 100;
    const loop = state.timelineEditor?.project?.musicTrack?.loop !== false;
    const resp = parseResponse(bridge.setBackgroundMusicOptions(JSON.stringify({ loop, volume: vol })));
    if (!resp.ok) { setTransient(resp.message || "Không đặt được âm lượng nhạc."); render(); return; }
    reloadTimelineProject();
    setTransient("Đã đặt âm lượng nhạc nền. Sẽ áp dụng khi xuất video.");
    render();
  }

  function timelineDurationHint() {
    setTransient("Kéo tay cầm VÀNG ở mép phải của clip (track Hình) để đổi thời lượng cảnh.");
    render();
  }

  // Kéo mép phải clip để đổi thời lượng (pointer). px -> ms theo pxPerSec.
  function handleTimelinePointerDown(event) {
    const handle = event.target instanceof HTMLElement ? event.target.closest(".tl-resize") : null;
    if (!handle || !state.timelineEditor) return;
    event.preventDefault();
    event.stopPropagation();
    const clipId = handle.getAttribute("data-clip-id");
    const clip = (state.timelineEditor.project.clips || []).find(c => c.clipId === clipId);
    if (!clip) return;
    const clipEl = root.querySelector("#tlclip-" + clipId);
    const startX = event.clientX;
    const origMs = clip.durationMs;
    let newMs = origMs;
    const onMove = (e) => {
      const dx = e.clientX - startX;
      const deltaMs = (dx / timelinePxPerSec()) * 1000;
      newMs = Math.max(800, Math.round((origMs + deltaMs) / 100) * 100);
      if (clipEl) {
        clipEl.style.width = timelineMsToPx(newMs) + "px";
        const durEl = clipEl.querySelector(".tl-clip-dur");
        if (durEl) durEl.textContent = (newMs / 1000).toFixed(1) + "s";
      }
    };
    const onUp = () => {
      document.removeEventListener("pointermove", onMove);
      document.removeEventListener("pointerup", onUp);
      const session = getActiveSession();
      if (session?.linkedJobId && bridge?.setSceneDuration && newMs !== origMs) {
        const resp = parseResponse(bridge.setSceneDuration(JSON.stringify({
          jobId: session.linkedJobId, sceneId: clip.sceneId, durationMs: newMs
        })));
        if (resp.ok) {
          refreshJobForSession(session);
          const keepSel = state.timelineEditor.selectedClipId;
          reloadTimelineProject();
          state.timelineEditor.selectedClipId = keepSel;
          setTransient("Đã đặt thời lượng cảnh " + (newMs / 1000).toFixed(1) + "s.");
        }
      }
      render();
    };
    document.addEventListener("pointermove", onMove);
    document.addEventListener("pointerup", onUp);
  }

  function timelineActiveClip() {
    const tl = state.timelineEditor?.project;
    if (!tl) return null;
    const ph = state.timelineEditor.playheadMs || 0;
    const clips = tl.clips || [];
    return clips.find(c => ph >= c.startMs && ph < c.startMs + c.durationMs) || clips[clips.length - 1] || null;
  }

  function timelineImageDataUrl(sceneId) {
    const session = getActiveSession();
    const job = session ? state.jobs[session.sessionId] : null;
    const art = (job?.artifacts || []).find(a => a.artifactType === "IMAGE" && a.sceneId === sceneId);
    return art?.previewDataUrl || "";
  }

  function timelineJumpToClip(clipId) {
    const tl = state.timelineEditor?.project;
    if (!tl) return;
    const clip = (tl.clips || []).find(c => c.clipId === clipId);
    if (!clip) return;
    state.timelineEditor.playheadMs = clip.startMs;
    render();
  }

  // Kéo playhead: cập nhật DOM trực tiếp cho mượt (không render lại cả cây).
  // Cập nhật preview theo playhead (KHÔNG động vào scroll). Playhead là vạch trắng
  // cố định giữa; thời gian đổi bằng cách cuộn timeline.
  function setTimelinePlayhead(ms) {
    if (!state.timelineEditor) return;
    const tl = state.timelineEditor.project;
    const total = Math.max(1, tl.totalDurationMs || 1);
    const clamped = Math.max(0, Math.min(total, ms));
    state.timelineEditor.playheadMs = clamped;
    const clip = timelineActiveClip();
    const img = root.querySelector("#timelinePreviewImg");
    if (img && clip && img.tagName === "IMG") {
      const url = timelineImageDataUrl(clip.sceneId);
      if (url && img.getAttribute("src") !== url) img.setAttribute("src", url);
    }
    const timeLabel = root.querySelector("#timelineTimeLabel");
    if (timeLabel) timeLabel.textContent = `${formatMs(clamped)} / ${formatMs(total)}`;
    const titleCap = (tl.captions || []).find(c => c.kind === "title" && clamped >= c.startMs && clamped < c.endMs);
    const subCap = (tl.captions || []).find(c => c.kind === "subtitle" && clamped >= c.startMs && clamped < c.endMs);
    const titleEl = root.querySelector("#timelinePreviewTitle");
    const subEl = root.querySelector("#timelinePreviewSub");
    if (titleEl) titleEl.textContent = titleCap?.text || "";
    if (subEl) subEl.textContent = subCap?.text || "";
  }

  function formatMs(ms) {
    const total = Math.round(ms / 100) / 10;
    const m = Math.floor(total / 60);
    const s = (total - m * 60).toFixed(1);
    return `${m}:${s.padStart(4, "0")}`;
  }

  function renderTimelineTrackBar(items, total, colorFn, labelFn) {
    return (items || []).map(it => {
      const left = (it.startMs / total) * 100;
      const width = Math.max(1.5, ((it.durationMs != null ? it.durationMs : (it.endMs - it.startMs)) / total) * 100);
      return `<div class="tl-clip" data-video-action="${it.clipId ? "timeline-clip" : ""}" ${it.clipId ? `data-clip-id="${escapeHtml(it.clipId)}"` : ""}
        style="left:${left}%;width:${width}%;background:${colorFn(it)}">${escapeHtml(labelFn(it))}</div>`;
    }).join("");
  }

  function renderTimelineEditor() {
    const tl = state.timelineEditor.project;
    const total = Math.max(1, tl.totalDurationMs || 1);
    const ph = state.timelineEditor.playheadMs || 0;
    const clip = timelineActiveClip();
    const previewUrl = clip ? timelineImageDataUrl(clip.sceneId) : "";
    const titleCap = (tl.captions || []).find(c => c.kind === "title" && ph >= c.startMs && ph < c.endMs);
    const subCap = (tl.captions || []).find(c => c.kind === "subtitle" && ph >= c.startMs && ph < c.endMs);
    const clips = tl.clips || [];
    const titles = (tl.captions || []).filter(c => c.kind === "title");
    const subs = (tl.captions || []).filter(c => c.kind === "subtitle");
    const w = tl.canvas?.width || 1080;
    const h = tl.canvas?.height || 1920;
    return `
      <div class="tl-overlay">
        <header class="tl-head">
          <button class="tl-x" type="button" data-video-action="close-timeline" aria-label="Đóng">✕</button>
          <div class="tl-head-copy"><b>${escapeHtml(tl.canvas?.aspectRatio || "9:16")}</b></div>
          <button class="video-workspace-primary tl-export" type="button" data-video-action="timeline-export">⤓ Xuất</button>
        </header>

        <div class="tl-preview-wrap">
          <div class="tl-preview" style="aspect-ratio:${escapeHtml(w)}/${escapeHtml(h)};">
            ${previewUrl ? `<img id="timelinePreviewImg" src="${escapeHtml(previewUrl)}" alt="preview">` : `<div class="tl-preview-empty" id="timelinePreviewImg">Cảnh chưa có ảnh</div>`}
            <div class="tl-cap-title" id="timelinePreviewTitle">${escapeHtml(titleCap?.text || "")}</div>
            <div class="tl-cap-sub" id="timelinePreviewSub">${escapeHtml(subCap?.text || "")}</div>
          </div>
        </div>

        <div class="tl-bottom">
          <div class="tl-transport">
            <span id="timelineTimeLabel">${escapeHtml(formatMs(ph))} / ${escapeHtml(formatMs(total))}</span>
            <button class="tl-playbtn" type="button" data-video-action="timeline-playpause">${state.timelineEditor.playing ? "⏸" : "▶"}</button>
            <div class="tl-transport-right">
              <button class="tl-zoombtn" type="button" data-video-action="timeline-zoom-out" aria-label="Thu nhỏ">−</button>
              <button class="tl-zoombtn" type="button" data-video-action="timeline-zoom-in" aria-label="Phóng to">+</button>
            </div>
          </div>

          <div class="tl-trackzone">
            <div class="tl-centerline"></div>
            <div class="tl-scrollx" id="timelineScroll">
              <div class="tl-inner" id="timelineInner" style="width:${timelineWidthPx()}px">
                <div class="tl-ruler">${renderTimelineRuler()}</div>
                <div class="tl-lane tl-lane-img">${renderTLBarsPx(clips, () => "#2a6df4", c => "C" + c.ordinal, true)}</div>
                <div class="tl-lane">${renderTLBarsPx(titles, () => "#8a5cf6", c => c.text.slice(0, 14))}</div>
                <div class="tl-lane">${renderTLBarsPx(subs, () => "#12a150", c => c.text.slice(0, 18))}</div>
                <div class="tl-lane">${tl.voiceTrack ? `<div class="tl-clip tl-audio-clip" style="left:0;width:${timelineWidthPx()}px;background:#0b8449">🔊 giọng đọc</div>` : ""}</div>
                <div class="tl-lane">${tl.musicTrack ? `<div class="tl-clip tl-audio-clip" style="left:0;width:${timelineWidthPx()}px;background:#c47f17">🎵 nhạc${tl.musicTrack.loop ? " (lặp)" : ""}</div>` : ""}</div>
              </div>
            </div>
          </div>

          ${renderTimelineToolbar()}
        </div>

        <audio id="timelineAudio" preload="auto"></audio>
      </div>
    `;
  }

  // Sau mỗi render, canh timeline: chừa lề = nửa bề rộng khung để playhead (cố định
  // GIỮA) khớp thời gian; đặt scroll về đúng playhead; nạp audio giọng đọc.
  function updateTimelineLayout() {
    if (!state.timelineEditor?.open) return;
    const scroll = root.querySelector("#timelineScroll");
    const inner = root.querySelector("#timelineInner");
    if (!scroll || !inner) return;
    if (!scroll._tlBound) {
      scroll.addEventListener("scroll", onTimelineScroll, { passive: true });
      scroll._tlBound = true;
    }
    const half = Math.round(scroll.clientWidth / 2);
    inner.style.marginLeft = half + "px";
    inner.style.marginRight = half + "px";
    // Đặt vị trí cuộn khớp playhead hiện tại (không kích hoạt cập nhật vòng lặp).
    // Khi đang PHÁT thì giữ nguyên suppress (vòng phát tự quản), không tự bỏ.
    if (!state.timelineEditor.playing) {
      state.timelineEditor._suppressScroll = true;
      scroll.scrollLeft = timelineMsToPx(state.timelineEditor.playheadMs || 0);
      setTimeout(() => { if (state.timelineEditor && !state.timelineEditor.playing) state.timelineEditor._suppressScroll = false; }, 60);
    } else {
      scroll.scrollLeft = timelineMsToPx(state.timelineEditor.playheadMs || 0);
    }
    // Nạp audio giọng đọc 1 lần cho phiên này.
    const audio = root.querySelector("#timelineAudio");
    const session = getActiveSession();
    if (audio && session?.linkedJobId && bridge?.getVoiceDataUrl && state.timelineEditor._audioJobId !== session.linkedJobId) {
      state.timelineEditor._audioJobId = session.linkedJobId;
      const r = parseResponse(bridge.getVoiceDataUrl(session.linkedJobId));
      if (r.ok && r.dataUrl) {
        audio.src = r.dataUrl;
        audio.volume = 1;
        try { audio.load(); } catch (e) {}
        state.timelineEditor._hasAudio = true;
      } else {
        state.timelineEditor._hasAudio = false;
      }
    }
  }

  function onTimelineScroll() {
    if (!state.timelineEditor || state.timelineEditor._suppressScroll) return;
    const scroll = root.querySelector("#timelineScroll");
    if (!scroll) return;
    const ms = (scroll.scrollLeft / timelinePxPerSec()) * 1000;
    setTimelinePlayhead(ms, true);
  }

  function timelineTotalMs() { return Math.max(1, state.timelineEditor?.project?.totalDurationMs || 1); }
  function timelinePxPerSec() { return state.timelineEditor?.pxPerSec || 40; }
  function timelineMsToPx(ms) { return (ms / 1000) * timelinePxPerSec(); }
  function timelineWidthPx() { return Math.max(320, Math.round(timelineMsToPx(timelineTotalMs()))); }

  function renderTimelineRuler() {
    const totalSec = timelineTotalMs() / 1000;
    const pps = timelinePxPerSec();
    const step = pps >= 40 ? 1 : pps >= 20 ? 2 : pps >= 10 ? 5 : 10;
    let out = "";
    for (let s = 0; s <= totalSec + 0.001; s += step) {
      const left = s * pps;
      out += `<div class="tl-tick" style="left:${left}px"><span>${s}s</span></div>`;
    }
    return out;
  }

  function renderTLBarsPx(items, colorFn, labelFn, selectable) {
    const selId = state.timelineEditor?.selectedClipId;
    return (items || []).map(it => {
      const dur = it.durationMs != null ? it.durationMs : (it.endMs - it.startMs);
      const left = timelineMsToPx(it.startMs);
      const width = Math.max(6, timelineMsToPx(dur));
      const sel = selectable && it.clipId && it.clipId === selId;
      const durLabel = selectable ? `<span class="tl-clip-dur">${(dur / 1000).toFixed(1)}s</span>` : "";
      const handle = sel ? `<div class="tl-resize" data-clip-id="${escapeHtml(it.clipId)}"></div>` : "";
      return `<div class="tl-clip${sel ? " selected" : ""}" id="${it.clipId ? "tlclip-" + escapeHtml(it.clipId) : ""}" ${it.clipId ? `data-video-action="timeline-select-clip" data-clip-id="${escapeHtml(it.clipId)}"` : ""}
        style="left:${left}px;width:${width}px;background:${colorFn(it)}">${escapeHtml(labelFn(it))}${durLabel}${handle}</div>`;
    }).join("");
  }

  function renderTimelineToolbar() {
    const sel = state.timelineEditor?.selectedClipId;
    const act = (a) => sel ? ` data-video-action="${a}"` : ` data-video-action="timeline-need-select"`;
    return `
      <div class="tl-toolbar">
        <button class="tl-tool" type="button"${act("timeline-move-left")}><span>◀</span><span>Sang trái</span></button>
        <button class="tl-tool" type="button"${act("timeline-move-right")}><span>▶</span><span>Sang phải</span></button>
        <button class="tl-tool" type="button"${act("timeline-replace-image")}><span>🖼️</span><span>Thay ảnh</span></button>
        <button class="tl-tool" type="button"${act("timeline-edit-title")}><span>🅣</span><span>Sửa tiêu đề</span></button>
        <button class="tl-tool" type="button"${act("timeline-duration-hint")}><span>↔️</span><span>Thời lượng</span></button>
        <button class="tl-tool" type="button"${act("timeline-delete-clip")}><span>🗑️</span><span>Xoá cảnh</span></button>
        <button class="tl-tool" type="button"${act("timeline-split")}><span>✂️</span><span>Tách</span></button>
        <button class="tl-tool" type="button" data-video-action="timeline-music-volume"><span>🎵</span><span>Âm lượng nhạc</span></button>
        <button class="tl-tool" type="button" data-video-action="timeline-tool-soon"><span>🎞️</span><span>Chuyển động</span></button>
        <button class="tl-tool" type="button" data-video-action="timeline-tool-soon"><span>🔀</span><span>Chuyển cảnh</span></button>
      </div>
    `;
  }

  function renderMetadataPage(session) {
    const job = getJobForSession(session);
    const draft = getMetadataDraft(session, job);
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Metadata</b>
            <small>Tại đây có thể xem, sửa nháp và tạo lại title, mô tả, hashtag từ pipeline metadata hiện có.</small>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Tiêu đề</span>
          <input class="video-input" id="videoMetadataTitleInput" value="${escapeHtml(draft.title)}">
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Mô tả</span>
          <textarea class="video-textarea" id="videoMetadataDescriptionInput">${escapeHtml(draft.description)}</textarea>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Hashtag</span>
          <input class="video-input" id="videoMetadataHashtagsInput" value="${escapeHtml(draft.hashtags)}" placeholder="#shorts #video #lqlq">
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="regenerate-metadata">Tạo lại metadata</button>
          <button class="video-workspace-primary" type="button" data-video-action="save-metadata">Lưu thay đổi</button>
        </div>
      </section>
    `;
  }

  function renderReviewPage(session) {
    const job = getJobForSession(session);
    const images = filterArtifacts(job, "IMAGE");
    const voice = filterArtifacts(job, "VOICE")[0];
    const video = filterArtifacts(job, "VIDEO_MP4")[0] || filterArtifacts(job, "VIDEO")[0];
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Kiểm duyệt video</b>
            <small>Checklist gọn để xác nhận tài nguyên và thành phẩm đã đủ trước khi đăng.</small>
          </div>
        </div>
        <div class="video-review-grid" style="margin-top:12px;">
          ${renderReviewCheck(Boolean(job?.generatedText), "Đã có nội dung AI", "Chưa có generatedText cho phiên này.")}
          ${renderReviewCheck(images.length > 0, `${images.length} ảnh đã sẵn sàng`, "Chưa có ảnh nào được tạo cho phiên này.")}
          ${renderReviewCheck(Boolean(voice), "Đã có giọng đọc", "Chưa có voice artifact.")}
          ${renderReviewCheck(Boolean(video), "Đã có video hoàn chỉnh", "Chưa có VIDEO_MP4, mới chỉ ở shell review.", true)}
          ${renderReviewCheck(Boolean(session.title), "Có tiêu đề phiên", "Thiếu tiêu đề phiên.")}
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="goto-page" data-page="content">Quay lại chỉnh sửa</button>
          <button class="video-workspace-primary" type="button" data-video-action="approve">Phê duyệt</button>
          <button class="video-workspace-danger" type="button" data-video-action="reject">Từ chối</button>
        </div>
      </section>
    `;
  }

  function renderPublishPage(session) {
    const job = getJobForSession(session);
    const video = filterArtifacts(job, "VIDEO_MP4")[0] || filterArtifacts(job, "VIDEO")[0];
    const reviewStatus = job?.reviewState?.status || session.reviewStatus || "PENDING";
    const hasVideo = Boolean(video);
    return `
      ${renderYouTubePublishCard(session, hasVideo)}
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Đăng thủ công (TikTok / Facebook...)</b>
            <small>TikTok/Facebook không cho đăng tự động qua API với app cá nhân — mở khay chia sẻ để đăng tay.</small>
          </div>
        </div>
        <div class="video-info-grid" style="margin-top:12px;">
          <div class="video-settings-state">Video: ${escapeHtml(video?.uri || "Chưa có VIDEO_MP4")}</div>
          <div class="video-settings-state">Trạng thái kiểm duyệt: ${escapeHtml(reviewStatus)}</div>
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="share">Chia sẻ / Mở ứng dụng đăng</button>
          <button class="video-workspace-primary" type="button" data-video-action="publish">Đánh dấu đã đăng</button>
        </div>
      </section>
    `;
  }

  function renderYouTubePublishCard(session, hasVideo) {
    const yt = state.youtubePublish || {};
    const privacy = yt.privacyStatus || "private";
    let inner = "";
    if (!yt.hasCredentials) {
      inner = `
        <div class="video-settings-state" style="margin-top:8px;">
          Đăng tự động thật lên YouTube cần OAuth Client (tạo 1 lần trên Google Cloud). Sau đó app tự upload kèm tiêu đề/mô tả/hashtag, không cần thao tác mỗi video.
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Client ID</span>
          <input class="video-input" id="youtubeClientIdInput" autocomplete="off" value="${escapeHtml(state.youtubeDraft.clientId || "")}" placeholder="xxxxxx.apps.googleusercontent.com">
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Client Secret</span>
          <input class="video-input" id="youtubeClientSecretInput" type="password" autocomplete="off" value="" placeholder="Dán Client Secret">
        </div>
        <div class="video-settings-state" style="margin-top:8px;">
          Trong Google Cloud, thêm URI chuyển hướng CHÍNH XÁC: <b>${escapeHtml(yt.redirectUri || "http://localhost/lqlq-youtube-oauth")}</b>
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-primary" type="button" data-video-action="save-youtube-config">Lưu Client ID/Secret</button>
        </div>
      `;
    } else if (!yt.connected) {
      inner = `
        <div class="video-settings-state" style="margin-top:8px;">Đã lưu Client ID/Secret. Bấm kết nối để đăng nhập Google và cấp quyền (1 lần).</div>
        <div class="video-workspace-footer">
          <button class="video-workspace-primary" type="button" data-video-action="connect-youtube">Kết nối tài khoản YouTube</button>
          <button class="video-workspace-secondary" type="button" data-video-action="disconnect-youtube">Xoá Client ID/Secret</button>
        </div>
      `;
    } else {
      inner = `
        <div class="video-settings-state" style="margin-top:8px;">✅ Đã kết nối YouTube. App có thể tự đăng video kèm metadata.</div>
        <div class="video-field" style="margin-top:12px;">
          <span>Chế độ hiển thị</span>
          <select class="video-select" id="youtubePrivacySelect">
            <option value="private"${privacy === "private" ? " selected" : ""}>Riêng tư (private)</option>
            <option value="unlisted"${privacy === "unlisted" ? " selected" : ""}>Không công khai (unlisted)</option>
            <option value="public"${privacy === "public" ? " selected" : ""}>Công khai (public)</option>
          </select>
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-primary" type="button" data-video-action="publish-youtube"${hasVideo ? "" : " disabled"}>Đăng lên YouTube ngay</button>
          <button class="video-workspace-secondary" type="button" data-video-action="disconnect-youtube">Ngắt kết nối</button>
        </div>
        ${hasVideo ? "" : `<div class="video-settings-state" style="margin-top:8px;">Chưa có VIDEO_MP4 để đăng.</div>`}
      `;
    }
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Đăng tự động lên YouTube</b>
            <small>Kết nối 1 lần, sau đó app tự upload — kèm sẵn tiêu đề/mô tả/hashtag.</small>
          </div>
        </div>
        ${inner}
      </section>
    `;
  }

  function renderProgressPage(session) {
    const job = getJobForSession(session);
    const stepMap = new Map((job?.steps || []).map(step => [step.stepType, step]));
    const autoUntil = state.settingsDraft.autoUntilStep || "video";
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Cài đặt tiến trình tự động</b>
            <small>Chọn app tự động chạy tới bước nào rồi dừng lại. Các bước sau mốc này bạn tự bấm khi muốn.</small>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Tự động chạy đến bước:</span>
          <select class="video-select" id="autoUntilStepSelect">
            ${AUTO_UNTIL_OPTIONS.map(option => `
              <option value="${option.value}"${autoUntil === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </div>
        <div class="video-settings-state" style="margin-top:8px;">
          Chọn mốc "Tự động hết — TỰ ĐĂNG YouTube luôn" thì app sẽ tự đăng lên YouTube kèm metadata theo chế độ (công khai/riêng tư) đã đặt ở Cài đặt tự động &gt; Đăng — miễn là bạn đã kết nối tài khoản YouTube. Các nền tảng khác (TikTok/Facebook) vẫn cần bấm "Chia sẻ".
        </div>
      </section>
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Tiến trình phiên</b>
            <small>Trạng thái từng bước của phiên hiện tại.</small>
          </div>
        </div>
        <div class="video-step-list" style="margin-top:12px;">
          ${STEP_ORDER.map((stepKey, index) => renderStepCard(stepMap.get(stepKey), stepKey, index)).join("")}
        </div>
      </section>
    `;
  }

  function renderStepCard(step, stepKey, index) {
    const status = step?.status || "PENDING";
    return `
      <article class="video-step-card">
        <b>${String(index + 1).padStart(2, "0")} · ${escapeHtml(STEP_LABELS[stepKey] || stepKey)}</b>
        <small class="video-muted-copy">${escapeHtml(step?.waitingReason || stepKey)}</small>
        <div class="video-scene-meta" style="margin-top:8px;">
          <span class="video-mini-pill">${escapeHtml(status)}</span>
        </div>
      </article>
    `;
  }

  function renderLogsPage(session) {
    const job = getJobForSession(session);
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Nhật ký</b>
            <small>Timeline gọn của runtime message và các step hiện có, không để tràn ngang màn hình.</small>
          </div>
        </div>
        <div class="video-log-list" style="margin-top:12px;">
          <div class="video-log-item">
            <b>${escapeHtml(formatRelativeTime(session.updatedAt))}</b>
            <small class="video-muted-copy">${escapeHtml(job?.runtimeMessage || session.summary)}</small>
          </div>
          ${(job?.steps || []).map(step => `
            <div class="video-log-item">
              <b>${escapeHtml(STEP_LABELS[step.stepType] || step.stepType)} · ${escapeHtml(step.status)}</b>
              <small class="video-muted-copy">${escapeHtml(step.waitingReason || "Không có ghi chú runtime")}</small>
            </div>
          `).join("")}
        </div>
      </section>
    `;
  }

  function renderInfoPage(session) {
    const job = getJobForSession(session);
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Thông tin phiên</b>
            <small>Metadata nội bộ của session workspace và liên kết job hiện tại.</small>
          </div>
        </div>
        <div class="video-info-grid" style="margin-top:12px;">
          <div class="video-settings-state">Session ID: ${escapeHtml(session.sessionId)}</div>
          <div class="video-settings-state">Job ID: ${escapeHtml(session.linkedJobId || "Chưa có")}</div>
          <div class="video-settings-state">Trạng thái: ${escapeHtml(session.status)}</div>
          <div class="video-settings-state">Tiến độ: ${escapeHtml(session.progressPercent)}%</div>
          <div class="video-settings-state">Cập nhật: ${escapeHtml(formatAbsoluteTime(session.updatedAt))}</div>
          <div class="video-settings-state">Workflow: ${escapeHtml(job?.workflowId || "automation-center-local-canonical")}</div>
        </div>
      </section>
    `;
  }

  function renderNavSheet() {
    return `
      <div class="video-workspace-sheet-backdrop" data-video-action="close-sheet">
        <div class="video-workspace-sheet">
          <div class="video-workspace-sheet-head">
            <b>Không gian làm việc</b>
            <button class="video-workspace-icon-button" type="button" data-video-action="close-sheet" aria-label="Đóng">✕</button>
          </div>
          <div class="video-workspace-sheet-scroll">
            ${groupWorkspacePages().map(section => `
              <div class="video-workspace-nav-section">
                <div class="video-workspace-nav-title">${escapeHtml(section.group)}</div>
                ${section.items.map(item => `
                  <button class="video-nav-item ${state.activePage === item.id ? "active" : ""}" type="button" data-video-action="goto-page" data-page="${item.id}">
                    <span>${escapeHtml(item.label)}</span>
                    <span>›</span>
                  </button>
                `).join("")}
              </div>
            `).join("")}
          </div>
        </div>
      </div>
    `;
  }

  function renderSettingsSheet() {
    return `
      <div class="video-workspace-sheet-backdrop" data-video-action="close-sheet">
        <div class="video-workspace-sheet">
          <div class="video-workspace-sheet-head">
            <b>Cài đặt tự động</b>
            <div class="video-inline-actions">
              <button class="video-workspace-icon-button" type="button" data-video-action="close-sheet" aria-label="Đóng">✕</button>
            </div>
          </div>
          <div class="video-workspace-sheet-scroll">
            <div class="video-settings-tabs">
              ${SETTINGS_TABS.map(tab => `
                <button class="video-settings-tab ${state.settingsTab === tab ? "active" : ""}" type="button" data-video-action="settings-tab" data-tab="${tab}">
                  ${escapeHtml(formatSettingsTab(tab))}
                </button>
              `).join("")}
            </div>
            ${renderSettingsPanel()}
          </div>
        </div>
      </div>
    `;
  }

  function renderSettingsPanel() {
    switch (state.settingsTab) {
      case "content": return renderContentSettings();
      case "image": return renderImageSettings();
      case "voice": return renderVoiceSettings();
      case "video": return renderVideoSettings();
      case "process": return renderProcessSettings();
      case "publish": return renderPublishSettings();
      case "background": return renderPlaceholderSettings("Chạy nền", "Phiên giờ chạy nền theo hàng chờ native. Notification và poll trạng thái đã được nối để bạn có thể tiếp tục duyệt web.");
      default: return renderContentSettings();
    }
  }

  // Tab "Đăng" trong Cài đặt tự động: cấu hình Client ID/Secret + kết nối YouTube +
  // chọn chế độ công khai/riêng tư (dùng lại đúng card ở trang Đăng). Client ID và
  // chế độ hiển thị giờ nằm cả ở đây, tách khỏi từng phiên như các cài đặt khác.
  // Tab "Tiến trình" trong Cài đặt tự động: chọn app tự chạy đến bước nào. Dùng
  // chung state.settingsDraft.autoUntilStep + persistSettingsDraft như các cài đặt
  // khác nên LƯU được qua lần thoát app (trước đây nằm ở trang riêng dễ tưởng mất).
  function renderProcessSettings() {
    const autoUntil = state.settingsDraft.autoUntilStep || "video";
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>Tiến trình tự động</b>
            <small>Chọn app tự động chạy tới bước nào rồi dừng. Cài đặt này được lưu và áp dụng cho mọi phiên.</small>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Tự động chạy đến bước:</span>
          <select class="video-select" id="autoUntilStepSelect">
            ${AUTO_UNTIL_OPTIONS.map(option => `
              <option value="${option.value}"${autoUntil === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </div>
        <div class="video-settings-state" style="margin-top:8px;">
          Chọn mốc cuối "Tự động hết — TỰ ĐĂNG YouTube luôn" thì app tự đăng YouTube kèm metadata theo chế độ (công khai/riêng tư) ở tab Đăng — cần đã kết nối YouTube.
        </div>
      </section>
    `;
  }

  function renderPublishSettings() {
    const session = getActiveSession();
    const job = session ? getJobForSession(session) : null;
    const hasVideo = Boolean(job && (filterArtifacts(job, "VIDEO_MP4")[0] || filterArtifacts(job, "VIDEO")[0]));
    return `
      <div class="video-settings-state" style="margin-bottom:10px;">
        Khi đặt "Mốc tiến trình tự động" = <b>Đăng</b> (mức cuối) và đã kết nối YouTube,
        app sẽ tự đăng video kèm metadata theo đúng <b>Chế độ hiển thị</b> bạn chọn dưới đây.
      </div>
      ${renderYouTubePublishCard(session, hasVideo)}
    `;
  }

  function renderContentSettings() {
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>Nội dung</b>
            <small>Cấu hình Gemini nằm ngoài session. API key chỉ đi qua native bridge.</small>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Nguồn nội dung</span>
          <select class="video-select" id="videoContentSourceSelect">
            <option value="api"${state.contentSource === "api" ? " selected" : ""}>Gemini API (cần API key)</option>
            <option value="web"${state.contentSource === "web" ? " selected" : ""}>Gemini web</option>
            <option value="chatgpt_web"${state.contentSource === "chatgpt_web" ? " selected" : ""}>ChatGPT web</option>
          </select>
          <small class="video-field-note">${state.contentSource === "web"
            ? "Mở gemini.google.com trong một tab thường và đăng nhập trước khi dùng nguồn này."
            : state.contentSource === "chatgpt_web"
              ? "Mở chatgpt.com trong một tab thường và đăng nhập trước. App sẽ dùng lại đúng phiên Free/Plus/Pro mà người dùng đã đăng nhập."
              : "Cần lưu API key Gemini bên dưới."}</small>
        </div>
        ${state.contentSource === "chatgpt_web" ? `
        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-field">
            <span>Loại tài khoản</span>
            <select class="video-select" id="videoChatGptAccountTierSelect">
              ${CHATGPT_ACCOUNT_OPTIONS.map(option => `<option value="${option.value}"${state.settingsDraft.chatGptAccountTier === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}
            </select>
          </div>
          <div class="video-field">
            <span>Chế độ tạo nội dung</span>
            <select class="video-select" id="videoChatGptModeSelect">
              ${CHATGPT_MODE_OPTIONS.map(option => `<option value="${option.value}"${state.settingsDraft.chatGptMode === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}
            </select>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Mức xử lý</span>
          <select class="video-select" id="videoChatGptReasoningSelect">
            ${CHATGPT_REASONING_OPTIONS.map(option => `<option value="${option.value}"${state.settingsDraft.chatGptReasoning === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}
          </select>
          <small class="video-field-note">${state.settingsDraft.chatGptAccountTier === "plus"
            ? "Tài khoản Plus nên dùng ít nhất mức Cao để viết kịch bản dài tốt hơn."
            : state.settingsDraft.chatGptAccountTier === "pro"
              ? "Tài khoản Pro phù hợp mức Rất cao khi cần kịch bản dài và chặt hơn."
              : "Tài khoản thường nên dùng Chuẩn hoặc Cao tùy tốc độ mong muốn."}</small>
        </div>
        <div class="video-settings-state" style="margin-top:12px;">
          App sẽ gửi prompt theo preset phù hợp với loại tài khoản, chế độ và mức xử lý bạn chọn. Việc tài khoản là thường, Plus hay Pro phụ thuộc phiên ChatGPT mà bạn đã đăng nhập sẵn trong trình duyệt của app.
        </div>
        ` : ""}
        ${state.contentSource === "api" ? `
        <div class="video-settings-state ${state.errorMessage ? "error" : ""}" style="margin-top:12px;">
          ${escapeHtml(state.errorMessage || formatGeminiStatusText())}
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Gemini API key</span>
          <input class="video-input" id="videoGeminiApiKey" type="password" autocomplete="off" placeholder="Nhập API key Gemini" value="">
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Model</span>
          <input class="video-input" id="videoGeminiModel" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.geminiModel || DEFAULT_GEMINI_MODEL)}">
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="test-gemini">Kiểm tra kết nối</button>
          <button class="video-workspace-primary" type="button" data-video-action="save-gemini">Lưu cấu hình Gemini</button>
        </div>
        ` : ""}
      </section>
    `;
  }

  function renderImageSettings() {
    const provider = getSelectedImageProvider();
    const needsAccountId = provider?.authType === "ACCOUNT_ID_AND_API_TOKEN";
    const supportsCustom = Boolean(state.settingsDraft.imageModelPreset === CUSTOM_MODEL_VALUE);
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>Hình ảnh</b>
            <small>Provider tạo ảnh thật nằm ngoài session. Session chỉ tiêu thụ artifact ảnh và stock query.</small>
          </div>
        </div>
        <div class="video-settings-state ${state.errorMessage ? "error" : ""}" style="margin-top:12px;">
          ${escapeHtml(formatImageStatusText(provider))}
        </div>
        ${provider?.providerId === "chatgpt-image-search-web" ? `
          <div class="video-settings-state" style="margin-top:8px;">
            ⚠️ Cần đăng nhập tài khoản ChatGPT ngay trong trình duyệt của app này trước khi tạo nội dung — nếu chưa đăng nhập, bước tìm ảnh sẽ báo lỗi.
          </div>
        ` : ""}
        ${provider?.providerId === "pinterest-image-search-web" ? `
          <div class="video-settings-state" style="margin-top:8px;">
            📌 Tìm ảnh Pinterest — <b>không cần đăng nhập</b>, nhanh hơn ChatGPT. (Đăng nhập Pinterest bằng email/mật khẩu là tùy chọn, giúp kết quả tốt hơn.)
          </div>
        ` : ""}
        <div class="video-field" style="margin-top:12px;">
          <span>Image provider</span>
          <select class="video-select" id="videoImageProviderSelect">
            ${state.imageProviders.map(item => `
              <option value="${escapeHtml(item.providerId)}"${state.settingsDraft.imageProviderId === item.providerId ? " selected" : ""}>
                ${escapeHtml(item.displayName)}${item.health === "NOT_IMPLEMENTED" ? " (chưa port)" : ""}
              </option>
            `).join("")}
          </select>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Model</span>
          <select class="video-select" id="videoImageModelPreset">
            ${renderImageModelOptions(provider)}
          </select>
        </div>
        ${supportsCustom ? `
          <div class="video-field" style="margin-top:12px;">
            <span>Model tùy chỉnh</span>
            <input class="video-input" id="videoImageModelCustom" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.imageModelCustom || "")}">
          </div>
        ` : ""}
        ${needsAccountId ? `
          <div class="video-field" style="margin-top:12px;">
            <span>Account ID</span>
            <input class="video-input" id="videoImageAccountId" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.imageAccountId || "")}">
          </div>
        ` : ""}
        <div class="video-field" style="margin-top:12px;">
          <span>Credential</span>
          <input class="video-input" id="videoImageApiKey" type="password" autocomplete="off" value="" placeholder="Nhập credential image provider">
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="test-image">Kiểm tra image provider</button>
          <button class="video-workspace-primary" type="button" data-video-action="save-image">Lưu cấu hình image</button>
        </div>
      </section>
    `;
  }

  function renderVoiceSettings() {
    const provider = getSelectedVoiceProvider();
    const needsCredential = provider?.requiresCredentials === true;
    const needsRegion = provider?.authType === "API_KEY_AND_REGION";
    const outputFormats = provider?.supportedOutputFormats?.length ? provider.supportedOutputFormats : ["wav"];
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>Giọng đọc</b>
            <small>Ưu tiên provider tiếng Việt. Google TTS trên máy vẫn có thể mở qua cài đặt hệ thống.</small>
          </div>
        </div>
        <div class="video-settings-state ${state.errorMessage ? "error" : ""}" style="margin-top:12px;">
          ${escapeHtml(state.errorMessage || formatVoiceStatusText(provider))}
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Voice provider</span>
          <select class="video-select" id="videoVoiceProviderSelect">
            ${state.voiceProviders.map(item => `
              <option value="${escapeHtml(item.providerId)}"${state.settingsDraft.voiceProviderId === item.providerId ? " selected" : ""}>
                ${escapeHtml(item.displayName)}${item.health === "NOT_IMPLEMENTED" ? " (chưa port)" : ""}
              </option>
            `).join("")}
          </select>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Giọng đọc</span>
          <select class="video-select" id="videoVoiceSelect">
            ${state.voiceDefinitions.map(voice => `
              <option value="${escapeHtml(voice.voiceId)}"${state.settingsDraft.voiceId === voice.voiceId ? " selected" : ""}>
                ${escapeHtml(voice.displayName)} (${escapeHtml(voice.locale)})
              </option>
            `).join("") || `<option value="">Chưa có danh sách giọng</option>`}
          </select>
        </div>
        ${needsCredential ? `
          <div class="video-field" style="margin-top:12px;">
            <span>Credential / API key</span>
            <input class="video-input" id="videoVoiceApiKey" type="password" autocomplete="off" value="" placeholder="Nhập API key của ${escapeHtml(provider?.displayName || "provider")}">
          </div>
        ` : ""}
        ${needsCredential ? `
          <div class="video-field" style="margin-top:12px;">
            <span>Model / profile</span>
            <input class="video-input" id="videoVoiceModel" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.voiceModel || "")}">
          </div>
        ` : ""}
        ${needsRegion ? `
          <div class="video-field" style="margin-top:12px;">
            <span>Region</span>
            <input class="video-input" id="videoVoiceRegion" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.voiceRegion || "")}">
          </div>
        ` : ""}
        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-field">
            <span>Locale</span>
            <input class="video-input" id="videoVoiceLocale" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.voiceLocale || "vi-VN")}">
          </div>
          <div class="video-field">
            <span>Output</span>
            <select class="video-select" id="videoVoiceOutputFormat">
              ${outputFormats.map(format => `<option value="${escapeHtml(format)}"${state.settingsDraft.voiceOutputFormat === format ? " selected" : ""}>${escapeHtml(format.toUpperCase())}</option>`).join("")}
            </select>
          </div>
        </div>
        ${(provider?.supportsSpeechRate || provider?.supportsPitch) ? `
          <div class="video-settings-grid" style="margin-top:12px;">
            ${provider?.supportsSpeechRate ? `
              <div class="video-field">
                <span>Tốc độ đọc · ${escapeHtml(state.settingsDraft.voiceRate)}</span>
                <input class="video-input" id="videoVoiceRate" type="range" min="50" max="200" step="1" value="${escapeHtml(state.settingsDraft.voiceRate)}">
              </div>
            ` : ""}
            ${provider?.supportsPitch ? `
              <div class="video-field">
                <span>Tông đọc · ${escapeHtml(state.settingsDraft.voicePitch)}</span>
                <input class="video-input" id="videoVoicePitch" type="range" min="50" max="200" step="1" value="${escapeHtml(state.settingsDraft.voicePitch)}">
              </div>
            ` : ""}
          </div>
        ` : ""}
        <div class="video-workspace-footer">
          ${provider?.providerId === "android-system-tts" ? `<button class="video-workspace-secondary" type="button" data-video-action="open-voice-settings">Mở cài đặt Google TTS</button>` : ""}
          <button class="video-workspace-secondary" type="button" data-video-action="sample-voice">Nghe thử</button>
          <button class="video-workspace-secondary" type="button" data-video-action="test-voice">Nạp và kiểm tra giọng</button>
          <button class="video-workspace-primary" type="button" data-video-action="save-voice">Lưu cấu hình voice</button>
        </div>
      </section>
    `;
  }

  function renderVideoSettings() {
    const draft = state.settingsDraft;
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>Video</b>
            <small>Áp dụng cho mọi phiên — khung hình, chất lượng, cách ghép ảnh và kiểu chuyển cảnh.</small>
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Khung hình</span>
          <div class="video-choice-row aspect">
            ${ASPECT_OPTIONS.map(option => `
              <button class="video-workspace-chip-button ${draft.videoAspectRatio === option ? "active" : ""}" type="button" data-video-action="set-video-aspect" data-value="${option}">
                ${escapeHtml(option)}
              </button>
            `).join("")}
          </div>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Chất lượng video</span>
          <select class="video-select" id="videoQualityTierSelect">
            ${VIDEO_QUALITY_OPTIONS.map(option => `
              <option value="${option.value}"${draft.videoQualityTier === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Chế độ nền ảnh</span>
          <select class="video-select" id="videoBackgroundModeSelect">
            ${VIDEO_BACKGROUND_MODE_OPTIONS.map(option => `
              <option value="${option.value}"${draft.videoBackgroundMode === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Kiểu chuyển cảnh (Ken Burns)</span>
          <select class="video-select" id="videoMotionModeSelect">
            ${VIDEO_MOTION_MODE_OPTIONS.map(option => `
              <option value="${option.value}"${draft.videoMotionMode === option.value ? " selected" : ""}>${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </div>
        <div class="video-field" style="margin-top:12px;">
          <span>Màu chữ phụ đề & tiêu đề</span>
          <div class="video-choice-row" style="align-items:center; gap:10px;">
            <input type="color" id="videoSubtitleColorInput" value="${escapeHtml(draft.videoSubtitleColor || "#FFFFFF")}" style="width:52px; height:36px; padding:0; border:none; background:none;">
            <span>${escapeHtml(draft.videoSubtitleColor || "#FFFFFF")}</span>
          </div>
        </div>
        <div class="video-settings-state" style="margin-top:12px;">
          Ảnh luôn hiển thị trọn vẹn (không cắt mất chủ thể); "Tự động phối hợp" luân phiên nhiều kiểu chuyển động giữa các cảnh cho đỡ đơn điệu, hoặc chọn cố định 1 kiểu nếu muốn thống nhất toàn video.
        </div>
        ${renderBackgroundMusicSettings()}
      </section>
    `;
  }

  function renderBackgroundMusicSettings() {
    const music = state.backgroundMusicSettings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    return `
      <div class="video-field" style="margin-top:20px; border-top:1px solid rgba(255,255,255,0.08); padding-top:16px;">
        <span>Nhạc nền (tuỳ chọn)</span>
        ${music.hasMusic ? `
          <div class="video-settings-state" style="margin-top:6px;">
            Đang dùng: <b>${escapeHtml(music.displayName || "Nhạc nền")}</b>
          </div>
        ` : `
          <div class="video-settings-state" style="margin-top:6px;">
            Chưa chọn nhạc nền — không có nhạc nền thì video vẫn render bình thường.
          </div>
        `}
        <div class="video-choice-row" style="margin-top:8px; gap:8px;">
          <button class="video-workspace-chip-button" type="button" data-video-action="pick-background-music">
            ${music.hasMusic ? "Đổi nhạc khác" : "Chọn nhạc từ máy"}
          </button>
          ${music.hasMusic ? `
            <button class="video-workspace-chip-button" type="button" data-video-action="clear-background-music">
              Xoá nhạc nền
            </button>
          ` : ""}
        </div>
        ${music.hasMusic ? `
          <div class="video-field" style="margin-top:12px;">
            <label style="display:flex; align-items:center; gap:8px;">
              <input type="checkbox" id="backgroundMusicLoopCheckbox" ${music.loop ? "checked" : ""} />
              <span>Lặp lại nhạc nền cho hết độ dài video</span>
            </label>
          </div>
          <div class="video-field" style="margin-top:12px;">
            <span>Âm lượng nhạc nền (so với giọng đọc): ${Math.round((music.volume ?? 0.35) * 100)}%</span>
            <input type="range" id="backgroundMusicVolumeRange" min="0" max="200" step="1" value="${Math.round((music.volume ?? 0.35) * 100)}" />
          </div>
        ` : ""}
      </div>
    `;
  }

  function renderPlaceholderSettings(title, description) {
    return `
      <section class="video-settings-card">
        <div class="video-page-head">
          <div>
            <b>${escapeHtml(title)}</b>
            <small>${escapeHtml(description)}</small>
          </div>
        </div>
        <div class="video-settings-state" style="margin-top:12px;">
          Phần này đã được quy hoạch đúng vị trí trong UI mới, nhưng pass hiện tại chỉ làm shell và điều hướng.
        </div>
      </section>
    `;
  }

  // Nút debug "🧪 POC web": chạy đúng prompt thật (giống hệt lúc bấm "Tạo nội
  // dung" với nguồn Gemini web) nhưng HIỆN overlay để xem trực tiếp — không
  // chạy tiếp phần pipeline (chia cảnh/ảnh/giọng/video), chỉ để kiểm tra xem
  // Gemini có trả đúng nội dung theo chủ đề hay không.
  function runGeminiWebPocDebug() {
    if (!bridge?.runGeminiWebPocDebug) {
      setError("Bridge POC web chưa sẵn sàng trong shell Android này.");
      render();
      return;
    }
    const session = getActiveSession();
    const topic = String(session?.topic || "").trim();
    if (!topic) {
      setError("Hãy nhập chủ đề nội dung trước khi chạy POC web.");
      render();
      return;
    }
    const desiredDurationSeconds = getSessionDurationSeconds(session);
    const maximumOutputLength = inferOutputLength(topic, desiredDurationSeconds);
    const dispatch = parseResponse(
      bridge.runGeminiWebPocDebug(JSON.stringify({
        topic,
        language: session.config.language === "English" ? "en" : "vi",
        contentType: "video_script",
        maximumOutputLength,
        desiredDurationSeconds
      }))
    );
    if (!dispatch.ok) {
      setError(dispatch.message || "Không chạy được POC web.");
      render();
      return;
    }
    setTransient("Đã mở POC web — theo dõi overlay để xem Gemini xử lý.");
    render();
    pollGeminiWebPocDebug(dispatch.clientRequestId, 0);
  }

  function pollGeminiWebPocDebug(clientRequestId, attempt) {
    const maxAttempts = 600;
    const intervalMs = 1500;
    setTimeout(() => {
      if (!bridge?.getAsyncTaskStatus) return;
      const response = parseResponse(bridge.getAsyncTaskStatus(clientRequestId));
      const taskState = response.ok ? String(response.state || "UNKNOWN") : "UNKNOWN";
      if (taskState === "DONE") {
        setTransient(`POC web xong. Nội dung nhận được (rút gọn): ${String(response.rawText || "").slice(0, 300)}`);
        render();
        return;
      }
      if (taskState === "ERROR") {
        setError(response.message || "POC web báo lỗi.");
        render();
        return;
      }
      if (attempt >= maxAttempts) {
        setError("POC web quá thời gian chờ.");
        render();
        return;
      }
      pollGeminiWebPocDebug(clientRequestId, attempt + 1);
    }, intervalMs);
  }

  // Nút chạy THÔNG MINH: LUÔN kiểm tra hiện đang có gì trước khi chạy.
  //  - Chưa có nội dung -> tạo nội dung (gọi Gemini) rồi chạy tiếp.
  //  - Đã có nội dung -> KHÔNG gọi lại Gemini, chỉ chạy tiếp từ bước còn thiếu
  //    (ảnh thiếu -> giọng -> video -> ... -> đăng). Tránh làm lại từ đầu.
  function smartAutoRun() {
    const session = getActiveSession();
    if (!session) return;
    if (isSessionBusy(session)) {
      setTransient("Phiên đang chạy — bấm 'Tạm dừng' nếu muốn dừng.");
      render();
      return;
    }
    // Lấy job mới nhất để biết đang có nội dung/ảnh chưa (sau khi thoát app mở lại,
    // state.jobs có thể chưa nạp -> phải refresh trước khi quyết định).
    if (session.linkedJobId) refreshJobForSession(session);
    const job = state.jobs[session.sessionId];
    const hasContent = Array.isArray(job?.scenePrompts) && job.scenePrompts.length > 0;
    if (!hasContent) {
      runSession("", false);
      return;
    }
    // Đã có nội dung -> tiếp tục từ bước còn dở (dùng lại đúng logic auto-continue).
    continueAutoFromContent();
  }

  function runSession(sessionId = "", contentOnly = false) {
    const session = sessionId
      ? state.sessions.find(item => item.sessionId === sessionId)
      : getActiveSession();
    if (!session) return;
    // contentOnly = chỉ tạo nội dung rồi dừng (không tự cào ảnh/giọng/video). Auto-
    // continue sẽ dừng khi thấy cờ này.
    session.contentOnlyRun = contentOnly;
    persistSessions();
    state.activeSessionId = session.sessionId;
    state.currentView = "workspace";
    state.activePage = "content";
    state.navOpen = false;
    state.settingsOpen = false;
    if (hasOtherBusySession(session.sessionId)) {
      enqueueSessionForLater(session, contentOnly);
      return;
    }
    if (!bridge?.runAutomationContentAsync) {
      setError("Automation Bridge chạy nền chưa sẵn sàng trong shell Android này.");
      render();
      return;
    }
    if (isSessionBusy(session)) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ, chưa thể chạy thêm.");
      render();
      return;
    }
    const topic = String(session.topic || "").trim();
    if (!topic) {
      setError("Hãy nhập chủ đề nội dung trước khi chạy phiên.");
      render();
      return;
    }
    const desiredDurationSeconds = getSessionDurationSeconds(session);
    const maximumOutputLength = inferOutputLength(topic, desiredDurationSeconds);
    setError("");

    const params = {
      topic,
      language: session.config.language === "English" ? "en" : "vi",
      desiredDurationSeconds,
      maximumOutputLength,
      aspectRatio: state.settingsDraft.videoAspectRatio || "9:16",
      videoQualityTier: state.settingsDraft.videoQualityTier || "1080p",
      videoBackgroundMode: state.settingsDraft.videoBackgroundMode || "blurred_fill",
      videoMotionMode: state.settingsDraft.videoMotionMode || "auto_mix",
      videoSubtitleColor: state.settingsDraft.videoSubtitleColor || "#FFFFFF"
    };

    if (state.contentSource === "web") {
      startGeminiWebContentFetch(session, params);
      return;
    }
    if (state.contentSource === "chatgpt_web") {
      startChatGptWebContentFetch(session, params);
      return;
    }

    const dispatch = parseResponse(
      bridge.runAutomationContentAsync(JSON.stringify({
        topic: params.topic,
        language: params.language,
        contentType: "video_script",
        maximumOutputLength: params.maximumOutputLength,
        desiredDurationSeconds: params.desiredDurationSeconds,
        aspectRatio: params.aspectRatio,
        videoQualityTier: params.videoQualityTier,
        videoBackgroundMode: params.videoBackgroundMode,
        videoMotionMode: params.videoMotionMode,
        videoSubtitleColor: params.videoSubtitleColor
      }))
    );
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể đưa phiên vào hàng chờ lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: `Đang tạo nội dung mục tiêu ${desiredDurationSeconds} giây...`,
      doneMessage: "Đã hoàn tất pipeline tạo nội dung cho phiên."
    });
  }

  // Nguồn nội dung "Gemini web": lấy nội dung qua WebView đã đăng nhập sẵn
  // (không cần API key), sau đó giao tiếp phần còn lại của pipeline (chia
  // cảnh/ảnh/giọng/video) xuống Worker nền — giống hệt đường API, chỉ khác
  // bước đầu lấy nội dung.
  function startGeminiWebContentFetch(session, params) {
    if (!bridge?.fetchGeminiWebContent) {
      setError("Bridge Gemini web chưa sẵn sàng trong shell Android này.");
      render();
      return;
    }
    const dispatch = parseResponse(
      bridge.fetchGeminiWebContent(JSON.stringify({
        topic: params.topic,
        language: params.language,
        contentType: "video_script",
        maximumOutputLength: params.maximumOutputLength,
        desiredDurationSeconds: params.desiredDurationSeconds
      }))
    );
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể mở Gemini web lúc này.");
      render();
      return;
    }
    session.pendingTaskId = dispatch.clientRequestId;
    session.pendingLabel = "Đang lấy nội dung từ Gemini web...";
    session.pendingState = "RUNNING";
    session.status = "RUNNING";
    session.activeStep = session.pendingLabel;
    session.activeStep = "Dang dung tac vu";
    session.updatedAt = Date.now();
    persistSessions();
    setTransient("Đang mở Gemini web để lấy nội dung — có thể mất vài chục giây.");
    render();
    scheduleWebContentPoll(session.sessionId, dispatch.clientRequestId, params, 0);
  }

  function startChatGptWebContentFetch(session, params) {
    if (!bridge?.fetchChatGptWebContent) {
      setError("Bridge ChatGPT web chưa sẵn sàng trong shell Android này.");
      render();
      return;
    }
    const dispatch = parseResponse(
      bridge.fetchChatGptWebContent(JSON.stringify({
        topic: params.topic,
        language: params.language,
        contentType: "video_script",
        maximumOutputLength: params.maximumOutputLength,
        desiredDurationSeconds: params.desiredDurationSeconds,
        chatGptAccountTier: state.settingsDraft.chatGptAccountTier || "free",
        chatGptMode: state.settingsDraft.chatGptMode || "write",
        chatGptReasoning: state.settingsDraft.chatGptReasoning || "standard"
      }))
    );
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể mở ChatGPT web lúc này.");
      render();
      return;
    }
    session.pendingTaskId = dispatch.clientRequestId;
    session.pendingLabel = "Đang lấy nội dung từ ChatGPT web...";
    session.pendingState = "RUNNING";
    session.status = "RUNNING";
    session.activeStep = session.pendingLabel;
    session.updatedAt = Date.now();
    persistSessions();
    setTransient("Đang mở ChatGPT web để lấy nội dung — có thể mất vài chục giây đến vài phút.");
    render();
    scheduleChatGptWebContentPoll(session.sessionId, dispatch.clientRequestId, params, 0);
  }

  function scheduleWebContentPoll(sessionId, clientRequestId, params, attempt) {
    const maxAttempts = 600;
    const intervalMs = 1500;
    setTimeout(() => {
      const session = state.sessions.find(item => item.sessionId === sessionId);
      if (!session || session.pendingTaskId !== clientRequestId || !bridge?.getAsyncTaskStatus) return;
      const response = parseResponse(bridge.getAsyncTaskStatus(clientRequestId));
      const taskState = response.ok ? String(response.state || "UNKNOWN") : "UNKNOWN";

      if (taskState === "DONE" && response.rawText) {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        // Fetch xong -> nhay THANG len 36% (khong ve 0, khong ket o 34). Pipeline se
        // di tiep tu day; buoc dau pipeline < 36 se bi max() giu o 36 cho toi khi
        // vuot qua -> thanh % luon tien, khong lui, khong ket.
        session.progressPercent = 36;
        persistSessions();
        const dispatch = parseResponse(
          bridge.runAutomationContentAsync(JSON.stringify({
            topic: params.topic,
            language: params.language,
            contentType: "video_script",
            maximumOutputLength: params.maximumOutputLength,
            desiredDurationSeconds: params.desiredDurationSeconds,
            aspectRatio: params.aspectRatio,
            videoQualityTier: params.videoQualityTier,
            videoBackgroundMode: params.videoBackgroundMode,
            videoMotionMode: params.videoMotionMode,
            videoSubtitleColor: params.videoSubtitleColor,
            preFetchedRawText: response.rawText
          }))
        );
        if (!dispatch.ok) {
          setError(dispatch.message || "Không thể tiếp tục pipeline sau khi lấy nội dung web.");
          if (state.activeSessionId === sessionId) render();
          return;
        }
        startPendingTask(session, dispatch.clientRequestId, {
          label: `[raw=${(response.rawText || "").length}] Đang tạo nội dung mục tiêu ${params.desiredDurationSeconds} giây...`,
          doneMessage: "Đã hoàn tất pipeline tạo nội dung cho phiên (nguồn: Gemini web)."
        });
        if (state.activeSessionId === sessionId) render();
        return;
      }
      if (taskState === "ERROR") {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        session.status = "FAILED";
        session.activeStep = "Lỗi";
        session.updatedAt = Date.now();
        persistSessions();
        setError(response.message || "Không lấy được nội dung từ Gemini web.");
        if (state.activeSessionId === sessionId) render();
        tryStartNextQueuedSession();
        return;
      }
      if (taskState === "CANCELLED") {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        session.status = "PAUSED";
        session.activeStep = "Đã tạm dừng";
        session.updatedAt = Date.now();
        persistSessions();
        setTransient(response.message || "Đã dừng lấy nội dung từ Gemini web.");
        if (state.activeSessionId === sessionId) render();
        tryStartNextQueuedSession();
        return;
      }
      // Con RUNNING: cap nhat % + nhan tung buoc (2% mo web ... 36% xong raw text).
      const fetchPct = Number(response.progressPercent);
      if (Number.isFinite(fetchPct) && fetchPct > 0) {
        session.progressPercent = fetchPct;
        const note = String(response.message || "").trim();
        if (note) { session.pendingLabel = note; session.activeStep = note; }
        session.updatedAt = Date.now();
        persistSessions();
        if (state.activeSessionId === sessionId) render();
      }
      if (attempt >= maxAttempts) {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        persistSessions();
        setError("Quá thời gian chờ Gemini web trả lời.");
        if (state.activeSessionId === sessionId) render();
        return;
      }
      scheduleWebContentPoll(sessionId, clientRequestId, params, attempt + 1);
    }, intervalMs);
  }

  function startImportImagesForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.startAutomationImageImport) {
      setError("Hãy chạy phiên trước để có job rồi mới thêm ảnh từ máy.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.startAutomationImageImport(JSON.stringify({
        jobId: session.linkedJobId
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể mở trình chọn ảnh lúc này.");
      render();
      return;
    }
    setTransient("Đã mở trình chọn ảnh. Sau khi chọn xong, ảnh sẽ tự gắn vào phiên hiện tại.");
    ensureJobPolling();
    render();
  }

  function retryImageForActiveSession() {
    const session = getActiveSession();
    const provider = getSelectedImageProvider();
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync || !provider) {
      setError("Chưa có job hợp lệ để retry IMAGE.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "retryImage",
      jobId: session.linkedJobId,
      providerId: provider.providerId
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể retry IMAGE lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang tạo/lấy lại ảnh...",
      doneMessage: "Đã hoàn tất bước IMAGE."
    });
  }

  function retryVoiceForActiveSession() {
    const session = getActiveSession();
    const provider = getSelectedVoiceProvider();
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync || !provider) {
      setError("Chưa có job hợp lệ để retry VOICE.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "retryVoice",
      jobId: session.linkedJobId,
      providerId: provider.providerId
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể retry VOICE lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang tạo giọng đọc...",
      doneMessage: "Đã hoàn tất bước VOICE."
    });
  }

  function retryVideoForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync) {
      setError("Chưa có job hợp lệ để render VIDEO.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "retryVideo",
      jobId: session.linkedJobId,
      videoQualityTier: state.settingsDraft.videoQualityTier || "1080p",
      videoBackgroundMode: state.settingsDraft.videoBackgroundMode || "blurred_fill",
      videoMotionMode: state.settingsDraft.videoMotionMode || "auto_mix",
      videoSubtitleColor: state.settingsDraft.videoSubtitleColor || "#FFFFFF"
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể render VIDEO lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang render video...",
      doneMessage: "Đã hoàn tất bước VIDEO."
    });
  }

  function startPendingTask(session, clientRequestId, options) {
    if (!clientRequestId) {
      setError("Không nhận được mã tác vụ chạy nền.");
      render();
      return;
    }
    session.pendingTaskId = clientRequestId;
    session.pendingLabel = "Đang chờ trong hàng đợi...";
    session.pendingState = "QUEUED";
    session.status = "QUEUED";
    session.activeStep = "Đang chờ hàng đợi";
    session.updatedAt = Date.now();
    persistSessions();
    setTransient(`${options.label || "Đang xử lý nền..."} Bạn có thể tiếp tục duyệt web hoặc tạo phiên khác.`);
    render();
    schedulePendingTaskPoll(session.sessionId, clientRequestId, options, 0);
  }

  function schedulePendingTaskPoll(sessionId, clientRequestId, options, attempt) {
    const maxAttempts = 2400;
    const intervalMs = 1500;
    setTimeout(() => {
      const session = state.sessions.find(item => item.sessionId === sessionId);
      if (!session || session.pendingTaskId !== clientRequestId || !bridge?.getAsyncTaskStatus) return;
      const response = parseResponse(bridge.getAsyncTaskStatus(clientRequestId));
      const taskState = response.ok ? String(response.state || "UNKNOWN") : "UNKNOWN";
      if (taskState === "DONE") {
        finishPendingTask(session, response, options);
        return;
      }
      if (taskState === "ERROR" || taskState === "CANCELLED") {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        session.status = taskState === "CANCELLED" ? "DRAFT" : "FAILED";
        session.activeStep = taskState === "CANCELLED" ? "Đã dừng" : "Lỗi";
        session.updatedAt = Date.now();
        persistSessions();
        if (taskState === "ERROR") setError(response.message || "Không thể xử lý phiên tự động lúc này.");
        else setTransient("Đã dừng và loại phiên khỏi hàng chờ.");
        if (state.open) render();
        return;
      }
      if (taskState === "QUEUED" || taskState === "RUNNING") {
        const beforeFingerprint = sessionProgressFingerprint(session);
        if (taskState === "QUEUED") {
          session.pendingState = "QUEUED";
          session.pendingLabel = response.message || "Đang chờ trong hàng đợi...";
          session.status = "QUEUED";
          session.activeStep = "Đang chờ hàng đợi";
        } else {
          const progressPercent = Number(response.progressPercent || 0);
          session.pendingState = "RUNNING";
          session.pendingLabel = response.message || options.label || "Đang xử lý nền...";
          session.status = "RUNNING";
          session.activeStep = session.pendingLabel;
          if (Number.isFinite(progressPercent) && progressPercent > 0) {
            session.progressPercent = Math.max(Number(session.progressPercent || 0), Math.min(99, progressPercent));
          }
          if (response.jobId) session.linkedJobId = response.jobId;
        }
        session.updatedAt = Date.now();
        persistSessions();
        // Chỉ render() (thay toàn bộ innerHTML) khi có gì đó thật sự đổi trên màn
        // hình — trước đây render() bị gọi ở MỌI tick 1.5s dù nhãn/% y hệt lần
        // trước, gây giật/chớp liên tục trong lúc tác vụ nền đang chạy.
        if (state.open && sessionProgressFingerprint(session) !== beforeFingerprint) {
          render();
        }
      }
      if (attempt >= maxAttempts) {
        setError("Không nhận được trạng thái mới từ tác vụ nền. Tác vụ vẫn có thể đang chạy; hãy kiểm tra thông báo Android.");
        if (state.open) render();
        return;
      }
      schedulePendingTaskPoll(sessionId, clientRequestId, options, attempt + 1);
    }, intervalMs);
  }

  function finishPendingTask(session, taskResponse, options) {
    session.pendingTaskId = "";
    session.pendingLabel = "";
    session.pendingState = "";
    const jobId = taskResponse.jobId;
    let job = null;
    if (jobId && bridge?.getAutomationJob) {
      const jobResponse = parseResponse(bridge.getAutomationJob(jobId));
      if (jobResponse.ok && jobResponse.job) {
        job = jobResponse.job;
        state.jobs[session.sessionId] = job;
        syncSessionFromJob(session, job);
      }
    }
    session.updatedAt = Date.now();
    persistSessions();
    setTransient(options.doneMessage || "Đã hoàn tất tác vụ chạy nền.");
    if (state.open) render();
    // Tự động nối tiếp: cào ảnh web -> giọng đọc -> video, không cần bấm tay —
    // "tự động hoá" nghĩa là chỉ cần app đang mở, không cần thao tác thêm.
    autoContinueSessionPipeline(session, job);
    tryStartNextQueuedSession();
  }

  // Chỉ TỰ ĐỘNG nối bước khi phiên đang ở luồng cào ảnh web (Google Images/
  // Pinterest, đặt qua session.webImageAutoContinue khi bắt đầu cào) — không đụng
  // tới các phiên dùng provider ảnh khác (API/Openverse/...), nơi người dùng vẫn
  // chủ động bấm "Tạo lại giọng đọc"/"Render lại" như trước giờ.
  function autoContinueSessionPipeline(session, job) {
    if (!job || session.pendingTaskId) return;
    // "Tạo nội dung" (chỉ nội dung): dừng ngay sau khi có nội dung, không tự chạy tiếp.
    if (session.contentOnlyRun) {
      session.contentOnlyRun = false;
      persistSessions();
      return;
    }
    const rank = autoUntilRank();
    const steps = Array.isArray(job.steps) ? job.steps : [];
    const imagesStep = steps.find(step => step.stepType === "IMAGES_VISUALS");
    const voiceStep = steps.find(step => step.stepType === "VOICE");
    const videoStep = steps.find(step => step.stepType === "VIDEO");
    const reviewStep = steps.find(step => step.stepType === "REVIEW");

    if (imagesStep && imagesStep.waitingReason === "WAITING_WEB_IMAGE_SCRAPE" && imagesStep.status === "WAITING_USER") {
      // Mốc "chỉ soạn nội dung" (rank < image) → không tự cào ảnh, dừng tại đây.
      if (rank < AUTO_STEP_RANK.image) return;
      startWebImageScrapeForSession(session);
      return;
    }
    if (!session.webImageAutoContinue) return;
    if (imagesStep?.status === "COMPLETED" && voiceStep?.status !== "COMPLETED") {
      if (rank < AUTO_STEP_RANK.voice) { stopAutoContinue(session); return; }
      dispatchRetryVoiceForSession(session);
      return;
    }
    if (voiceStep?.status === "COMPLETED" && videoStep?.status !== "COMPLETED") {
      if (rank < AUTO_STEP_RANK.video) { stopAutoContinue(session); return; }
      dispatchRetryVideoForSession(session);
      return;
    }
    if (videoStep?.status === "COMPLETED") {
      // Sau video, retryVideo (backend) đã tự sinh metadata + review (chờ phê duyệt).
      // Nếu mốc >= review thì tự phê duyệt luôn.
      const reviewApproved = (job?.reviewState?.status || "").toUpperCase() === "APPROVED";
      if (rank >= AUTO_STEP_RANK.review && reviewStep && !reviewApproved) {
        autoApproveReviewForSession(session);
      }
      // Mốc CUỐI = "publish": nếu đã kết nối YouTube thì TỰ ĐỘNG ĐĂNG luôn kèm
      // metadata (mặc định riêng tư/private cho an toàn). Chỉ đăng 1 lần / phiên.
      if (rank >= AUTO_STEP_RANK.publish && !session.autoPublished) {
        refreshYouTubeStatus();
      }
      if (rank >= AUTO_STEP_RANK.publish && !session.autoPublished && state.youtubePublish?.connected) {
        session.autoPublished = true;
        persistSessions();
        autoPublishYouTubeForSession(session);
        return;
      }
      stopAutoContinue(session);
      return;
    }
    // Cào ảnh hay bị ngắt/lỗi giữa chừng — TỰ ĐỘNG THỬ LẠI có giới hạn (tối đa 3
    // lần), và vì luồng cào là "missing-only" nên mỗi lần chỉ lấy ảnh còn thiếu,
    // KHÔNG đụng ảnh các cảnh đã có. Chỉ dừng hẳn khi vẫn lỗi sau 3 lần.
    if (imagesStep?.status === "FAILED") {
      const tries = Number(session.imageScrapeRetries || 0);
      if (rank >= AUTO_STEP_RANK.image && tries < 3) {
        session.imageScrapeRetries = tries + 1;
        persistSessions();
        setTransient(`Cào ảnh bị lỗi — tự động thử lại lần ${tries + 1}/3 (chỉ lấy ảnh còn thiếu)…`);
        startWebImageScrapeForSession(session);
        return;
      }
      session.imageScrapeRetries = 0;
      persistSessions();
      stopAutoContinue(session);
      return;
    }
    if (imagesStep?.status === "COMPLETED") session.imageScrapeRetries = 0;
    if (voiceStep?.status === "FAILED" || videoStep?.status === "FAILED") {
      stopAutoContinue(session);
    }
  }

  function stopAutoContinue(session) {
    session.webImageAutoContinue = false;
    persistSessions();
  }

  function autoApproveReviewForSession(session) {
    if (!session?.linkedJobId || !bridge?.approveAutomationReview) return;
    const response = parseResponse(bridge.approveAutomationReview(JSON.stringify({ jobId: session.linkedJobId })));
    if (response.ok && response.job) {
      state.jobs[session.sessionId] = response.job;
      syncSessionFromJob(session, response.job);
      persistSessions();
      setTransient("Đã tự động phê duyệt kiểm duyệt. Sang trang Đăng để chia sẻ khi muốn.");
    }
  }

  function startWebImageScrapeForSession(session) {
    if (!bridge?.scrapeWebImages) {
      setError("Bridge cào ảnh web chưa sẵn sàng trong shell Android này.");
      if (state.open) render();
      return;
    }
    if (!session?.linkedJobId) {
      setError("Phiên này chưa có job hợp lệ để cào ảnh.");
      if (state.open) render();
      return;
    }
    // Dam bao state.imageProviders da duoc nap (vd truong hop workspace vua mo
    // xong chua kip lay danh sach provider) truoc khi doc provider dang chon —
    // tranh im lang dung lai chi vi getSelectedImageProvider() tra ve null.
    refreshImageStatus();
    const provider = getSelectedImageProvider();
    if (!provider) {
      setError("Không xác định được provider ảnh đang chọn — hãy mở Cài đặt > Hình ảnh rồi thử lại.");
      if (state.open) render();
      return;
    }
    const dispatch = parseResponse(bridge.scrapeWebImages(JSON.stringify({
      jobId: session.linkedJobId,
      providerId: provider.providerId
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể cào ảnh từ web lúc này.");
      if (state.open) render();
      return;
    }
    session.webImageAutoContinue = true;
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang cào ảnh từ web...",
      doneMessage: "Đã cào xong ảnh từ web, tiếp tục tạo giọng đọc..."
    });
  }

  function dispatchRetryVoiceForSession(session) {
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync) return;
    refreshVoiceStatus();
    const provider = getSelectedVoiceProvider();
    if (!provider) {
      setError("Không xác định được provider giọng đọc đang chọn — hãy mở Cài đặt > Giọng đọc rồi thử lại.");
      if (state.open) render();
      return;
    }
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "retryVoice",
      jobId: session.linkedJobId,
      providerId: provider.providerId
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể tự động tạo giọng đọc lúc này.");
      if (state.open) render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang tạo giọng đọc...",
      doneMessage: "Đã hoàn tất giọng đọc, tiếp tục render video..."
    });
  }

  function dispatchRetryVideoForSession(session) {
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync) return;
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "retryVideo",
      jobId: session.linkedJobId,
      videoQualityTier: state.settingsDraft.videoQualityTier || "1080p",
      videoBackgroundMode: state.settingsDraft.videoBackgroundMode || "blurred_fill",
      videoMotionMode: state.settingsDraft.videoMotionMode || "auto_mix",
      videoSubtitleColor: state.settingsDraft.videoSubtitleColor || "#FFFFFF"
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể tự động render video lúc này.");
      if (state.open) render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang render video...",
      doneMessage: "Đã hoàn tất video (nguồn ảnh: cào từ web)."
    });
  }

  function cancelPendingTask(sessionId) {
    const session = state.sessions.find(item => item.sessionId === sessionId);
    if (!session) {
      setTransient("Không tìm thấy dự án để dừng.");
      render();
      return;
    }
    if (!session.pendingTaskId) {
      rehydratePendingTaskForSession(session);
    }
    if (!session?.pendingTaskId) {
      if (session.status === "QUEUED" || String(session.pendingState || "").toUpperCase() === "QUEUED") {
        session.pendingLabel = "";
        session.pendingState = "";
        session.status = "DRAFT";
        session.activeStep = "Da bo khoi hang doi";
        session.updatedAt = Date.now();
        persistSessions();
        setTransient("Da bo du an khoi hang doi.");
        render();
        tryStartNextQueuedSession();
        return;
      }
      setTransient(isSessionBusy(session)
        ? "Dự án này vẫn đang ở trạng thái chạy/chờ nhưng UI chưa tìm lại được mã tác vụ nền để gửi lệnh dừng."
        : "Dự án này không có tác vụ nền để dừng.");
      render();
      return;
    }
    if (!bridge?.cancelAutomationTask) {
      setError("Bridge dừng tác vụ nền chưa sẵn sàng.");
      render();
      return;
    }
    const response = parseResponse(bridge.cancelAutomationTask(session.pendingTaskId));
    if (!response.ok) {
      setError(response.message || "Không thể dừng tác vụ lúc này.");
      render();
      return;
    }
    session.pendingState = "CANCELLING";
    session.status = "PAUSED";
    session.activeStep = "Đã tạm dừng";
    setTransient("Đã gửi yêu cầu dừng. Đang chờ tác vụ web xác nhận huỷ...");
    persistSessions();
    // Chỉ poll nhận CANCELLED thật từ native rồi mới nhả hẳn task.
    render();
    tryStartNextQueuedSession();
  }

  function scheduleChatGptWebContentPoll(sessionId, clientRequestId, params, attempt) {
    const maxAttempts = 600;
    const intervalMs = 1500;
    setTimeout(() => {
      const session = state.sessions.find(item => item.sessionId === sessionId);
      if (!session || session.pendingTaskId !== clientRequestId || !bridge?.getAsyncTaskStatus) return;
      const response = parseResponse(bridge.getAsyncTaskStatus(clientRequestId));
      const taskState = response.ok ? String(response.state || "UNKNOWN") : "UNKNOWN";

      if (taskState === "DONE" && response.rawText) {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        // Fetch xong -> nhay THANG len 36% (khong ve 0, khong ket o 34). Pipeline se
        // di tiep tu day; buoc dau pipeline < 36 se bi max() giu o 36 cho toi khi
        // vuot qua -> thanh % luon tien, khong lui, khong ket.
        session.progressPercent = 36;
        persistSessions();
        const dispatch = parseResponse(
          bridge.runAutomationContentAsync(JSON.stringify({
            topic: params.topic,
            language: params.language,
            contentType: "video_script",
            maximumOutputLength: params.maximumOutputLength,
            desiredDurationSeconds: params.desiredDurationSeconds,
            aspectRatio: params.aspectRatio,
            videoQualityTier: params.videoQualityTier,
            videoBackgroundMode: params.videoBackgroundMode,
            videoMotionMode: params.videoMotionMode,
            videoSubtitleColor: params.videoSubtitleColor,
            preFetchedRawText: response.rawText
          }))
        );
        if (!dispatch.ok) {
          setError(dispatch.message || "Không thể tiếp tục pipeline sau khi lấy nội dung ChatGPT web.");
          if (state.activeSessionId === sessionId) render();
          return;
        }
        startPendingTask(session, dispatch.clientRequestId, {
          label: "Đang dựng nội dung + ảnh + giọng + video...",
          doneMessage: "Đã hoàn tất pipeline."
        });
        return;
      }
      if (taskState === "CANCELLED") {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        session.status = "DRAFT";
        session.activeStep = "Da dung";
        session.updatedAt = Date.now();
        persistSessions();
        setTransient(response.message || "Da dung lay noi dung tu ChatGPT web.");
        if (state.activeSessionId === sessionId) render();
        return;
      }
      if (attempt >= maxAttempts) {
        session.pendingTaskId = "";
        session.pendingLabel = "";
        session.pendingState = "";
        persistSessions();
        setError("Quá thời gian chờ ChatGPT web trả lời.");
        if (state.activeSessionId === sessionId) render();
        return;
      }
      scheduleChatGptWebContentPoll(sessionId, clientRequestId, params, attempt + 1);
    }, intervalMs);
  }

  function rehydratePendingTaskForSession(session) {
    if (!session?.linkedJobId || !bridge?.findActiveAutomationTaskByJobId) return false;
    const response = parseResponse(bridge.findActiveAutomationTaskByJobId(session.linkedJobId));
    const taskId = String(response?.clientRequestId || "").trim();
    if (!response?.ok || !taskId) return false;
    session.pendingTaskId = taskId;
    const status = parseResponse(bridge.getAsyncTaskStatus(taskId));
    const stateValue = String(status?.state || "").toUpperCase();
    if (stateValue === "RUNNING" || stateValue === "QUEUED") {
      session.pendingState = stateValue;
      session.pendingLabel = status.message || fallbackBusyLabel(session);
      session.status = stateValue;
      session.activeStep = session.pendingLabel || session.activeStep;
      if (status.jobId) session.linkedJobId = status.jobId;
      if (Number.isFinite(Number(status.progressPercent || 0)) && Number(status.progressPercent || 0) > 0) {
        session.progressPercent = Math.max(Number(session.progressPercent || 0), Math.min(99, Number(status.progressPercent || 0)));
      }
      persistSessions();
      return true;
    }
    return false;
  }

  function regenerateMetadataForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.retryAutomationMetadata) {
      setError("Chưa có job hợp lệ để tạo lại metadata.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.retryAutomationMetadata(JSON.stringify({
        jobId: session.linkedJobId
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể tạo lại metadata lúc này.");
      render();
      return;
    }
    const job = response.job;
    state.jobs[session.sessionId] = job;
    syncSessionFromJob(session, job);
    session.metadataDraft = buildMetadataDraft(job, session);
    persistSessions();
    setTransient(job?.runtimeMessage || "Đã tạo lại metadata cho phiên hiện tại.");
    render();
  }
  function saveMetadataDraftForActiveSession() {
    const session = getActiveSession();
    if (!session) return;
    const titleInput = root.querySelector("#videoMetadataTitleInput");
    const descriptionInput = root.querySelector("#videoMetadataDescriptionInput");
    const hashtagsInput = root.querySelector("#videoMetadataHashtagsInput");
    session.metadataDraft = {
      title: String(titleInput?.value || "").trim(),
      description: String(descriptionInput?.value || "").trim(),
      hashtags: String(hashtagsInput?.value || "").trim()
    };
    session.updatedAt = Date.now();
    persistSessions();
    setError("");
    setTransient("Đã lưu nháp metadata trong phiên làm việc.");
    render();
  }
  function approveReviewForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.approveAutomationReview) {
      setError("Chưa có review hợp lệ để phê duyệt.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.approveAutomationReview(JSON.stringify({
        jobId: session.linkedJobId
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể phê duyệt review lúc này.");
      render();
      return;
    }
    const job = response.job;
    state.jobs[session.sessionId] = job;
    syncSessionFromJob(session, job);
    persistSessions();
    setTransient(job?.runtimeMessage || "Đã phê duyệt review.");
    render();
  }
  function rejectReviewForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.rejectAutomationReview) {
      setError("Chưa có review hợp lệ để từ chối.");
      render();
      return;
    }
    const reason = window.prompt("Nhập lý do từ chối review:", "Cần sửa title/description");
    if (reason == null) return;
    const response = parseResponse(
      bridge.rejectAutomationReview(JSON.stringify({
        jobId: session.linkedJobId,
        reason
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể từ chối review lúc này.");
      render();
      return;
    }
    const job = response.job;
    state.jobs[session.sessionId] = job;
    syncSessionFromJob(session, job);
    persistSessions();
    setTransient(job?.runtimeMessage || "Review đã bị từ chối.");
    render();
  }
  function sharePublishForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.shareAutomationPublish) {
      setError("Chưa có publish plan hợp lệ để chia sẻ.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.shareAutomationPublish(JSON.stringify({
        jobId: session.linkedJobId
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể mở share sheet lúc này.");
      render();
      return;
    }
    const job = response.job;
    state.jobs[session.sessionId] = job;
    syncSessionFromJob(session, job);
    persistSessions();
    setTransient(job?.runtimeMessage || "Đã mở share sheet Android.");
    render();
  }
  function markPublishedForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.markAutomationPublished) {
      setError("Chưa có publish plan hợp lệ để đánh dấu đã đăng.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.markAutomationPublished(JSON.stringify({
        jobId: session.linkedJobId
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể đánh dấu đã đăng lúc này.");
      render();
      return;
    }
    const job = response.job;
    state.jobs[session.sessionId] = job;
    syncSessionFromJob(session, job);
    persistSessions();
    setTransient(job?.runtimeMessage || "Đã đánh dấu video đã đăng.");
    render();
  }

  function refreshYouTubeStatus() {
    if (!bridge?.getYouTubePublishStatus) return;
    const response = parseResponse(bridge.getYouTubePublishStatus());
    if (response.ok) {
      state.youtubePublish = {
        hasCredentials: !!response.hasCredentials,
        connected: !!response.connected,
        privacyStatus: response.privacyStatus || "private",
        redirectUri: response.redirectUri || ""
      };
    }
  }

  function saveYouTubeConfig() {
    if (!bridge?.saveYouTubePublishConfig) return;
    const clientId = String(state.youtubeDraft.clientId || "").trim();
    const secretInput = root.querySelector("#youtubeClientSecretInput");
    const clientSecret = String(secretInput?.value || state.youtubeDraft.clientSecret || "").trim();
    if (!clientId || !clientSecret) {
      setError("Cần nhập cả Client ID và Client Secret.");
      render();
      return;
    }
    const response = parseResponse(bridge.saveYouTubePublishConfig(JSON.stringify({ clientId, clientSecret })));
    if (!response.ok) {
      setError(response.message || "Không thể lưu cấu hình YouTube.");
      render();
      return;
    }
    state.youtubeDraft = { clientId: "", clientSecret: "" };
    setError("");
    setTransient("Đã lưu Client ID/Secret. Giờ bấm Kết nối tài khoản YouTube.");
    refreshYouTubeStatus();
    render();
  }

  function connectYouTubeAccount() {
    if (!bridge?.connectYouTubeAccount) return;
    const response = parseResponse(bridge.connectYouTubeAccount());
    if (!response.ok) {
      setError(response.message || "Không thể mở màn hình đăng nhập YouTube.");
      render();
      return;
    }
    setTransient("Đang mở màn hình đồng ý của Google... cấp quyền xong quay lại đây.");
    render();
    // OAuth chạy bất đồng bộ (WebView native) - poll lại trạng thái vài giây.
    let attempts = 0;
    const poll = () => {
      attempts += 1;
      refreshYouTubeStatus();
      render();
      if (!state.youtubePublish.connected && attempts < 20) setTimeout(poll, 1500);
      else if (state.youtubePublish.connected) setTransient("✅ Đã kết nối YouTube.");
    };
    setTimeout(poll, 2000);
  }

  function disconnectYouTubeAccount() {
    if (!bridge?.disconnectYouTubeAccount) return;
    parseResponse(bridge.disconnectYouTubeAccount());
    refreshYouTubeStatus();
    setTransient("Đã ngắt kết nối YouTube.");
    render();
  }

  function publishToYouTubeForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.publishToYouTube) {
      setError("Chưa có job hợp lệ để đăng YouTube.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const dispatch = parseResponse(bridge.publishToYouTube(JSON.stringify({ jobId: session.linkedJobId })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể đăng lên YouTube lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang đăng video lên YouTube...",
      doneMessage: "Đã đăng lên YouTube xong."
    });
  }

  // Tự động đăng YouTube cho 1 phiên (dùng trong luồng auto khi mốc = "publish").
  // Đảm bảo để chế độ riêng tư (private) trước khi đăng cho an toàn.
  function autoPublishYouTubeForSession(session) {
    if (!session?.linkedJobId || !bridge?.publishToYouTube) { stopAutoContinue(session); return; }
    if (session.pendingTaskId) return;
    // Đăng theo ĐÚNG chế độ hiển thị người dùng đã chọn ở Cài đặt tự động > Đăng
    // (private/unlisted/public) — không ép cứng private nữa.
    const privacy = state.youtubePublish?.privacyStatus || "private";
    const dispatch = parseResponse(bridge.publishToYouTube(JSON.stringify({ jobId: session.linkedJobId })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Tự động đăng YouTube thất bại — bạn có thể bấm 'Đăng lên YouTube ngay' để thử lại.");
      stopAutoContinue(session);
      if (state.open) render();
      return;
    }
    const privacyLabel = privacy === "public" ? "công khai" : (privacy === "unlisted" ? "không công khai" : "riêng tư");
    setTransient(`Đã tự động gửi video lên YouTube (${privacyLabel}).`);
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang tự động đăng lên YouTube...",
      doneMessage: "Đã tự động đăng lên YouTube (riêng tư)."
    });
  }

  function continueAutoFromContent() {
    const session = getActiveSession();
    if (!session?.linkedJobId) {
      setError("Chưa có phiên hợp lệ để tiếp tục.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên đang chạy — chờ xong đã.");
      render();
      return;
    }
    const job = state.jobs[session.sessionId];
    if (!Array.isArray(job?.scenePrompts) || job.scenePrompts.length === 0) {
      setError("Chưa có nội dung. Bấm 'Tạo nội dung' trước.");
      render();
      return;
    }
    // Tiếp tục từ bước ảnh (dùng nội dung đã có), KHÔNG tạo lại nội dung. autoContinue
    // sẽ tự cào ảnh -> giọng -> video.
    session.contentOnlyRun = false;
    persistSessions();
    setTransient("Đang tiếp tục: tìm ảnh → giọng → video...");
    autoContinueSessionPipeline(session, job);
    render();
  }

  function rerunVoiceAndVideo() {
    const session = getActiveSession();
    if (!session?.linkedJobId) {
      setError("Chưa có phiên hợp lệ để chạy lại.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const job = state.jobs[session.sessionId];
    const hasScenes = Array.isArray(job?.scenePrompts) && job.scenePrompts.length > 0;
    const hasImages = filterArtifacts(job, "IMAGE").length > 0;
    if (!hasScenes || !hasImages) {
      setError("Chưa có nội dung & ảnh để chạy lại. Hãy 'Chạy Tự động' hoặc tạo ảnh trước.");
      render();
      return;
    }
    // Dùng lại chuỗi tự động voice -> video (giữ nội dung & ảnh đã có, KHÔNG gọi
    // Gemini/Pinterest). autoContinue sẽ nối video sau khi voice xong.
    session.webImageAutoContinue = true;
    persistSessions();
    setTransient("Đang tạo lại giọng đọc + video từ nội dung & ảnh hiện có...");
    dispatchRetryVoiceForSession(session);
  }

  function deleteSceneForActiveSession(sceneId) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.deleteScene || !sceneId) return;
    if (session.pendingTaskId) { setTransient("Phiên đang chạy — chờ xong đã."); render(); return; }
    const response = parseResponse(bridge.deleteScene(JSON.stringify({ jobId: session.linkedJobId, sceneId })));
    if (!response.ok) {
      setError(response.message || "Không xoá được cảnh.");
      render();
      return;
    }
    refreshJobForSession(session);
    setError("");
    setTransient("Đã xoá cảnh. Bấm 'Chạy lại' để dựng lại video.");
    render();
  }

  function addSceneForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.addScene) return;
    if (session.pendingTaskId) { setTransient("Phiên đang chạy — chờ xong đã."); render(); return; }
    const voiceText = String(root.querySelector("#newSceneVoice")?.value || "").trim();
    const onScreenText = String(root.querySelector("#newSceneTitle")?.value || "").trim();
    const stockSearchQuery = String(root.querySelector("#newSceneQuery")?.value || "").trim();
    if (!voiceText && !stockSearchQuery) {
      setError("Cần nhập ít nhất Lời đọc hoặc Từ khoá tìm ảnh cho cảnh mới.");
      render();
      return;
    }
    const response = parseResponse(bridge.addScene(JSON.stringify({
      jobId: session.linkedJobId, voiceText, onScreenText, stockSearchQuery
    })));
    if (!response.ok) {
      setError(response.message || "Không thêm được cảnh.");
      render();
      return;
    }
    refreshJobForSession(session);
    setError("");
    setTransient("Đã thêm cảnh. Nhớ cào ảnh cho cảnh mới rồi 'Chạy lại'.");
    render();
  }

  function saveSceneText(sceneId) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.updateSceneText || !sceneId) return;
    const titleEl = root.querySelector("#sceneTitle-" + sceneId);
    const voiceEl = root.querySelector("#sceneVoice-" + sceneId);
    const queryEl = root.querySelector("#sceneQuery-" + sceneId);
    const onScreenText = String(titleEl?.value || "").trim();
    const voiceText = String(voiceEl?.value || "").trim();
    const stockSearchQuery = String(queryEl?.value || "").trim();
    const response = parseResponse(bridge.updateSceneText(JSON.stringify({
      jobId: session.linkedJobId,
      sceneId,
      voiceText,
      onScreenText,
      stockSearchQuery
    })));
    if (!response.ok) {
      setError(response.message || "Không lưu được cảnh này.");
      render();
      return;
    }
    // Cập nhật local để UI phản ánh ngay.
    const job = state.jobs[session.sessionId];
    const scene = job?.scenePrompts?.find(s => s.sceneId === sceneId);
    if (scene) {
      if (voiceText) scene.voiceText = voiceText;
      scene.onScreenText = onScreenText;
      if (stockSearchQuery) scene.stockSearchQuery = stockSearchQuery;
    }
    setError("");
    setTransient("Đã lưu cảnh. Bấm 'Chạy lại (giữ nội dung & ảnh)' để tạo lại giọng + video.");
  }

  // Thay ảnh cho DUNG 1 cảnh (mở trình chọn ảnh từ máy) — giữ nguyên ảnh cảnh
  // khác. Native trả về qua Toast + cập nhật runtime; JS chỉ khởi động picker.
  function replaceSceneImageForActiveSession(sceneId) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.replaceSceneImage || !sceneId) return;
    if (session.pendingTaskId) { setTransient("Phiên đang chạy — chờ xong đã."); render(); return; }
    const response = parseResponse(bridge.replaceSceneImage(JSON.stringify({ jobId: session.linkedJobId, sceneId })));
    if (!response.ok) {
      setError(response.message || "Không mở được trình chọn ảnh.");
      render();
      return;
    }
    setError("");
    setTransient("Chọn 1 ảnh từ máy để thay cho cảnh này…");
    render();
  }

  // Lưu từ khoá đang sửa của cảnh, XOÁ ảnh cảnh đó (đánh dấu thiếu), rồi cào web
  // lại — luồng cào là "missing-only" nên chỉ tìm đúng cảnh này theo từ khoá mới,
  // KHÔNG đụng ảnh cảnh khác.
  function rescrapeSceneImageForActiveSession(sceneId) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.clearSceneImage || !sceneId) return;
    if (session.pendingTaskId) { setTransient("Phiên đang chạy — chờ xong đã."); render(); return; }
    // 1) Lưu từ khoá (và lời đọc/tiêu đề) đang nhập của cảnh này.
    if (bridge.updateSceneText) {
      const titleEl = root.querySelector("#sceneTitle-" + sceneId);
      const voiceEl = root.querySelector("#sceneVoice-" + sceneId);
      const queryEl = root.querySelector("#sceneQuery-" + sceneId);
      parseResponse(bridge.updateSceneText(JSON.stringify({
        jobId: session.linkedJobId,
        sceneId,
        voiceText: String(voiceEl?.value || "").trim(),
        onScreenText: String(titleEl?.value || "").trim(),
        stockSearchQuery: String(queryEl?.value || "").trim()
      })));
    }
    // 2) Xoá ảnh cảnh này -> thiếu.
    const cleared = parseResponse(bridge.clearSceneImage(JSON.stringify({ jobId: session.linkedJobId, sceneId })));
    if (!cleared.ok) {
      setError(cleared.message || "Không xoá được ảnh cảnh này.");
      render();
      return;
    }
    refreshJobForSession(session);
    // 3) Cào web lại — chỉ cảnh thiếu (chính cảnh này) được tìm theo từ khoá mới.
    setError("");
    setTransient("Đang tìm lại ảnh cho cảnh này theo từ khoá mới…");
    startWebImageScrapeForSession(session);
    render();
  }

  function downloadSceneImageForActiveSession(sceneId) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.downloadSceneImage || !sceneId) return;
    const response = parseResponse(bridge.downloadSceneImage(JSON.stringify({ jobId: session.linkedJobId, sceneId })));
    if (!response.ok) {
      setError(response.message || "Không tải được ảnh về máy.");
      render();
      return;
    }
    setError("");
    setTransient("Đang lưu ảnh vào thư viện máy…");
  }

  function moveSceneForActiveSession(sceneId, direction) {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.moveScene || !sceneId) return;
    if (session.pendingTaskId) { setTransient("Phiên đang chạy — chờ xong đã."); render(); return; }
    const response = parseResponse(bridge.moveScene(JSON.stringify({ jobId: session.linkedJobId, sceneId, direction })));
    if (!response.ok) {
      setError(response.message || "Không đổi được thứ tự cảnh.");
      render();
      return;
    }
    refreshJobForSession(session);
    setError("");
    setTransient("Đã đổi thứ tự cảnh. Bấm 'Chạy lại' để dựng lại video theo thứ tự mới.");
    render();
  }

  function previewVideoForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.previewVideo) {
      setError("Chưa có VIDEO_MP4 để xem trước.");
      render();
      return;
    }
    const response = parseResponse(bridge.previewVideo(JSON.stringify({ jobId: session.linkedJobId })));
    if (!response.ok) {
      setError(response.message || "Không thể xem trước video lúc này.");
      render();
    }
  }

  function exportVideoForActiveSession() {
    const session = getActiveSession();
    if (!session?.linkedJobId || !bridge?.runAutomationStageAsync) {
      setError("Chưa có VIDEO_MP4 hợp lệ để xuất.");
      render();
      return;
    }
    if (session.pendingTaskId) {
      setTransient("Phiên này đang chạy nền hoặc đang chờ.");
      render();
      return;
    }
    const dispatch = parseResponse(bridge.runAutomationStageAsync(JSON.stringify({
      action: "exportVideo",
      jobId: session.linkedJobId
    })));
    if (!dispatch.ok) {
      setError(dispatch.message || "Không thể xuất VIDEO_MP4 lúc này.");
      render();
      return;
    }
    startPendingTask(session, dispatch.clientRequestId, {
      label: "Đang xuất MP4 vào Downloads...",
      doneMessage: "Đã xuất MP4. Kiểm tra thư mục Downloads/LQLQAutomation."
    });
  }

  function refreshSettingsData() {
    refreshGeminiStatus();
    refreshImageStatus();
    refreshVoiceStatus();
    refreshBackgroundMusicSettings();
  }

  function refreshBackgroundMusicSettings() {
    if (!bridge?.getBackgroundMusicSettings) return;
    const beforeSignature = backgroundMusicSettingsSignature(state.backgroundMusicSettings);
    const response = parseResponse(bridge.getBackgroundMusicSettings());
    if (!response.ok) return false;
    state.backgroundMusicSettings = response.settings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    return beforeSignature !== backgroundMusicSettingsSignature(state.backgroundMusicSettings);
  }

  function pickBackgroundMusic() {
    if (!bridge?.startBackgroundMusicImport) return;
    rememberCurrentSettingsScroll();
    const initialSignature = backgroundMusicSettingsSignature(state.backgroundMusicSettings);
    const response = parseResponse(bridge.startBackgroundMusicImport());
    if (!response.ok) {
      setError(response.message || "Không thể mở trình chọn nhạc nền lúc này.");
      render();
      return;
    }
    // Native picker chạy bất đồng bộ (Activity Result callback) - poll lại trạng
    // thái nhạc nền sau vài giây để cập nhật UI, giống cách app xử lý các picker
    // khác không có callback đồng bộ trực tiếp về JS.
    setTransient("Đang mở trình chọn nhạc từ máy...");
    render();
    let attempts = 0;
    const poll = () => {
      attempts += 1;
      const changed = refreshBackgroundMusicSettings();
      const currentSignature = backgroundMusicSettingsSignature(state.backgroundMusicSettings);
      if (changed || currentSignature !== initialSignature) {
        rememberCurrentSettingsScroll();
        if (state.backgroundMusicSettings?.hasMusic) {
          setTransient("Đã cập nhật nhạc nền.");
        }
        render();
        return;
      }
      if (attempts < 10) {
        setTimeout(poll, 1000);
      }
    };
    setTimeout(poll, 1000);
  }

  function clearBackgroundMusic() {
    if (!bridge?.clearBackgroundMusic) return;
    const response = parseResponse(bridge.clearBackgroundMusic());
    if (!response.ok) {
      setError(response.message || "Không thể xoá nhạc nền lúc này.");
      render();
      return;
    }
    state.backgroundMusicSettings = response.settings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    setTransient("Đã xoá nhạc nền.");
    render();
  }

  function saveBackgroundMusicOptions(overrides) {
    if (!bridge?.setBackgroundMusicOptions) return;
    const current = state.backgroundMusicSettings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    const loop = overrides.loop !== undefined ? overrides.loop : current.loop;
    const volume = overrides.volume !== undefined ? overrides.volume : current.volume;
    const response = parseResponse(bridge.setBackgroundMusicOptions(JSON.stringify({ loop, volume })));
    if (!response.ok) {
      setError(response.message || "Không thể lưu cài đặt nhạc nền.");
      render();
      return;
    }
    state.backgroundMusicSettings = response.settings || { ...current, loop, volume };
    render();
  }

  // Lưu riêng âm lượng nhạc nền KHÔNG render lại UI - dùng khi thả slider để tránh
  // giật/nhảy màn hình (state + nhãn đã cập nhật live ở handleInput rồi).
  function persistBackgroundMusicVolume(volume) {
    if (!bridge?.setBackgroundMusicOptions) return;
    const current = state.backgroundMusicSettings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    const response = parseResponse(bridge.setBackgroundMusicOptions(JSON.stringify({ loop: current.loop, volume })));
    if (response.ok && response.settings) {
      state.backgroundMusicSettings = response.settings;
    }
  }

  function refreshGeminiStatus() {
    if (!bridge?.getGeminiConfigurationStatus) return;
    const response = parseResponse(bridge.getGeminiConfigurationStatus());
    if (!response.ok) return;
    state.geminiStatus = response.status || state.geminiStatus;
    state.settingsDraft.geminiModel = state.geminiStatus.model || state.settingsDraft.geminiModel || DEFAULT_GEMINI_MODEL;
  }

  function refreshImageStatus() {
    if (!bridge?.getImageProviderConfigurationStatus) return;
    // Gửi kèm provider đang được người dùng chọn (settingsDraft) trong request —
    // native sẽ tự cấu hình/tự chọn ĐÚNG provider này nếu nó thuộc loại không cần
    // credential, thay vì tự đoán "provider đã chọn trước đó" rồi ghi đè ngược
    // lại lựa chọn mới của người dùng (đây là nguyên nhân trước đây đổi dropdown
    // xong lại tự nhảy về provider cũ).
    const requestedProviderId = state.settingsDraft.imageProviderId || state.selectedImageProviderId || "";
    const response = parseResponse(
      bridge.getImageProviderConfigurationStatus(JSON.stringify({ providerId: requestedProviderId }))
    );
    if (!response.ok) return;
    state.imageProviders = Array.isArray(response.providers) ? response.providers : [];
    const nativeSelected = state.imageProviders.find(item => item.selected)?.providerId || "";
    const selected = requestedProviderId
      || nativeSelected
      || state.imageProviders[0]?.providerId
      || "";
    state.selectedImageProviderId = selected;
    state.settingsDraft.imageProviderId = selected;
    syncImageDraftFromSelectedProvider();

    // Tự động lưu cấu hình cho provider KHÔNG cần credential (Openverse/
    // Wikimedia/Tự động nhiều nguồn) nếu chưa từng lưu — pipeline cần có bản
    // ghi "đã chọn" ở native để chạy được, khỏi phải bấm tay mỗi lần. Theo dõi
    // theo TỪNG providerId (không phải 1 cờ chung) để đổi qua lại giữa nhiều
    // provider miễn phí vẫn tự lưu đúng, không bị khoá sau lần thử đầu tiên.
    const provider = getSelectedImageProvider();
    if (
      provider && provider.authType === "NONE" &&
      provider.configurationStatus?.state === "NOT_CONFIGURED" &&
      !state.imageAutoSaveAttempted[provider.providerId]
    ) {
      state.imageAutoSaveAttempted[provider.providerId] = true;
      saveImageConfiguration();
    }
  }

  function refreshVoiceStatus() {
    if (!bridge?.getVoiceProviderConfigurationStatus) return;
    const requestedProviderId = state.settingsDraft.voiceProviderId || state.selectedVoiceProviderId || "";
    const response = parseResponse(bridge.getVoiceProviderConfigurationStatus(JSON.stringify({ providerId: requestedProviderId })));
    if (!response.ok) return;
    state.voiceProviders = Array.isArray(response.providers) ? response.providers : [];
    // Ưu tiên đúng ý người dùng vừa chọn (settingsDraft/selectedVoiceProviderId)
    // hơn nativeSelected (provider native đã lưu TỪ TRƯỚC) — nativeSelected chỉ
    // dùng làm fallback khi người dùng chưa từng chọn gì, giống hệt logic ảnh
    // (refreshImageStatus). Trước đây thứ tự ngược lại khiến đổi sang FPT/Vbee
    // xong bị kéo ngược về provider cũ (vd Google TTS).
    const nativeSelected = state.voiceProviders.find(item => item.selected)?.providerId || "";
    const selected = requestedProviderId
      || nativeSelected
      || state.voiceProviders[0]?.providerId
      || "";
    state.selectedVoiceProviderId = selected;
    state.settingsDraft.voiceProviderId = selected;
    syncVoiceDraftFromSelectedProvider();
    refreshVoiceDefinitions();

    // Tự động lưu cấu hình cho provider KHÔNG cần credential (vd "Google TTS
    // trên điện thoại") nếu chưa từng lưu — theo dõi theo TỪNG providerId
    // (không phải 1 cờ chung) để đổi qua lại nhiều provider miễn phí vẫn tự
    // lưu đúng, không bị khoá sau lần thử đầu tiên.
    const provider = getSelectedVoiceProvider();
    if (
      provider && provider.authType === "NONE" &&
      response.status?.state === "NOT_CONFIGURED" &&
      !state.voiceAutoSaveAttempted[provider.providerId]
    ) {
      state.voiceAutoSaveAttempted[provider.providerId] = true;
      saveVoiceConfiguration();
    }
  }

  function refreshVoiceDefinitions() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.listVoiceDefinitions || !provider || provider.health === "NOT_IMPLEMENTED") {
      state.voiceDefinitions = [];
      return;
    }
    const response = parseResponse(bridge.listVoiceDefinitions(JSON.stringify({ providerId: provider.providerId })));
    state.voiceDefinitions = response.ok && Array.isArray(response.voices) ? response.voices : [];
    // Nếu giọng đang chọn KHÔNG thuộc provider hiện tại (vd giữ lại giọng của
    // provider cũ khi đổi qua lại) thì reset về giọng đầu của provider mới — tránh
    // lỗi "voice is invalid" khi provider chỉ nhận giọng riêng của nó (vd FPT).
    const validVoiceIds = state.voiceDefinitions.map(voice => voice.voiceId);
    if (state.voiceDefinitions[0] && !validVoiceIds.includes(state.settingsDraft.voiceId)) {
      // Với Google TTS trên máy: mặc định GIỌNG THỨ 8 (index 7) nếu có, không thì
      // giọng cuối, cuối cùng mới giọng đầu — theo yêu cầu người dùng.
      let idx = 0;
      if (provider.providerId === "android-system-tts") {
        idx = state.voiceDefinitions.length >= 8 ? 7 : (state.voiceDefinitions.length - 1);
      }
      state.settingsDraft.voiceId = state.voiceDefinitions[idx].voiceId;
    }
  }

  function saveGeminiConfiguration() {
    if (!bridge?.saveGeminiConfiguration) return;
    const response = parseResponse(
      bridge.saveGeminiConfiguration(JSON.stringify({
        apiKey: state.settingsDraft.geminiApiKey || "",
        model: String(state.settingsDraft.geminiModel || DEFAULT_GEMINI_MODEL).trim() || DEFAULT_GEMINI_MODEL
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể lưu cấu hình Gemini.");
    } else {
      setError("");
      state.geminiStatus = response.status || state.geminiStatus;
      state.settingsDraft.geminiApiKey = "";
      setTransient("Đã lưu cấu hình Gemini.");
    }
    render();
  }

  function testGeminiConnection() {
    if (!bridge?.testGeminiConnection) return;
    const response = parseResponse(bridge.testGeminiConnection());
    if (!response.ok) {
      setError(response.message || "Kiểm tra Gemini thất bại.");
    } else {
      setError("");
      state.geminiStatus = response.status || state.geminiStatus;
      setTransient(response.status?.message || "Đã kết nối Gemini thành công.");
    }
    render();
  }

  function saveImageConfiguration() {
    const provider = getSelectedImageProvider();
    if (!bridge?.saveImageProviderConfiguration || !provider) return;
    const model = getSelectedImageModel();
    if (!isSafeImageModel(provider, model)) {
      setError("Model image provider không hợp lệ.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.saveImageProviderConfiguration(JSON.stringify({
        providerId: provider.providerId,
        apiKey: state.settingsDraft.imageApiKey || "",
        model,
        accountId: provider.authType === "ACCOUNT_ID_AND_API_TOKEN" ? (state.settingsDraft.imageAccountId || "") : ""
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể lưu cấu hình image provider.");
    } else {
      setError("");
      state.settingsDraft.imageApiKey = "";
      setTransient(response.status?.message || "Đã lưu cấu hình image provider.");
      refreshImageStatus();
    }
    render();
  }

  function testImageConnection() {
    const provider = getSelectedImageProvider();
    if (!bridge?.testImageProviderConnection || !provider) return;
    const response = parseResponse(
      bridge.testImageProviderConnection(JSON.stringify({ providerId: provider.providerId }))
    );
    if (!response.ok) {
      setError(response.message || "Kiểm tra image provider thất bại.");
    } else {
      setError("");
      setTransient(response.status?.message || "Đã kiểm tra image provider.");
      refreshImageStatus();
    }
    render();
  }

  function saveVoiceConfiguration() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.saveVoiceProviderConfiguration || !provider) return;
    if (state.settingsDraft.voiceModel && !SAFE_VOICE_MODEL.test(state.settingsDraft.voiceModel)) {
      setError("Model voice provider không hợp lệ.");
      render();
      return;
    }
    const response = parseResponse(
      bridge.saveVoiceProviderConfiguration(JSON.stringify({
        providerId: provider.providerId,
        locale: state.settingsDraft.voiceLocale || provider.defaultLocale || "vi-VN",
        voiceId: state.settingsDraft.voiceId || "",
        model: state.settingsDraft.voiceModel || "",
        apiKey: state.settingsDraft.voiceApiKey || "",
        region: state.settingsDraft.voiceRegion || "",
        engineName: provider.displayName || "",
        speechRate: sliderToScalar(state.settingsDraft.voiceRate),
        pitch: sliderToScalar(state.settingsDraft.voicePitch),
        outputFormat: state.settingsDraft.voiceOutputFormat || "wav"
      }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể lưu cấu hình voice provider.");
    } else {
      setError("");
      state.settingsDraft.voiceApiKey = "";
      setTransient(response.status?.message || "Đã lưu cấu hình voice provider.");
      refreshVoiceStatus();
    }
    render();
  }

  function testVoiceConnection() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.testVoiceProviderConnection || !provider) return;
    const response = parseResponse(
      bridge.testVoiceProviderConnection(JSON.stringify({ providerId: provider.providerId }))
    );
    if (!response.ok) {
      setError(response.message || "Kiểm tra voice provider thất bại.");
    } else {
      setError("");
      setTransient(response.status?.message || "Đã nạp và kiểm tra voice provider.");
      refreshVoiceStatus();
    }
    render();
  }

  function sampleVoice() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.synthesizeVoiceSample || !provider) return;
    const response = parseResponse(
      bridge.synthesizeVoiceSample(JSON.stringify({ providerId: provider.providerId }))
    );
    if (!response.ok) {
      setError(response.message || "Không thể nghe thử giọng đọc.");
      render();
      return;
    }
    setError("");
    setTransient("Đã tạo audio sample để nghe thử.");
    const previewUrl = response.artifact?.previewDataUrl;
    if (previewUrl) {
      try {
        const audio = new Audio(previewUrl);
        audio.play().catch(() => {});
      } catch {}
    }
    render();
  }

  function openVoiceSettings() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.openVoiceProviderSettings || !provider) return;
    parseResponse(bridge.openVoiceProviderSettings(JSON.stringify({ providerId: provider.providerId })));
    setTransient("Đã yêu cầu mở cài đặt giọng đọc trên thiết bị.");
    render();
  }

  function closeWorkspace() {
    state.open = false;
    state.currentView = "home";
    state.activeSessionId = "";
    state.navOpen = false;
    state.settingsOpen = false;
    render();
  }

  function refreshJobForSession(session) {
    if (!bridge?.getAutomationJob || !session?.linkedJobId) return false;
    const response = parseResponse(bridge.getAutomationJob(session.linkedJobId));
    if (!response.ok || !response.job) return false;
    const beforeFingerprint = sessionProgressFingerprint(session);
    state.jobs[session.sessionId] = response.job;
    syncSessionFromJob(session, response.job);
    const afterFingerprint = sessionProgressFingerprint(session);
    if (beforeFingerprint !== afterFingerprint) {
      persistSessions();
      return true;
    }
    return false;
  }

  function sessionProgressFingerprint(session) {
    return [
      session.status,
      session.progressPercent,
      session.activeStep,
      session.summary,
      session.reviewStatus
    ].join("|");
  }

  function syncSessionFromJob(session, job) {
    if (!session || !job) return;
    session.linkedJobId = job.jobId || session.linkedJobId;
    session.updatedAt = Number(job.createdAtEpochMs || Date.now());
    session.progressPercent = calculateProgress(job);
    session.activeStep = deriveActiveStep(job);
    session.summary = job.runtimeMessage || deriveSummary(job);
    session.status = mapJobStatus(job);
    session.reviewStatus = job?.reviewState?.status || session.reviewStatus || "PENDING";
    if (!session.metadataDraft || (!session.metadataDraft.title && !session.metadataDraft.description && !session.metadataDraft.hashtags)) {
      session.metadataDraft = buildMetadataDraft(job, session);
    }
    if (session.topic !== job.topic && (!session.topic || session.status !== "DRAFT")) {
      session.topic = job.topic || session.topic;
      session.title = buildSessionTitle(session.topic);
    }
  }
  function buildMetadataDraft(job, session) {
    const metadataPlan = job?.metadataPlan || {};
    const hashtags = Array.isArray(metadataPlan.hashtags)
      ? metadataPlan.hashtags.join(" ")
      : "";
    return {
      title: String(metadataPlan.title || session?.title || "").trim(),
      description: String(metadataPlan.description || job?.generatedText || "").trim(),
      hashtags: String(hashtags || "").trim()
    };
  }
  function getMetadataDraft(session, job) {
    return session?.metadataDraft || buildMetadataDraft(job, session);
  }

  function calculateProgress(job) {
    const steps = Array.isArray(job?.steps) ? job.steps : [];
    if (!steps.length) return 0;
    const completed = steps.filter(step => step.status === "COMPLETED").length;
    return Math.round((completed / steps.length) * 100);
  }

  function deriveActiveStep(job) {
    const running = (job?.steps || []).find(step => step.status === "RUNNING");
    if (running) {
      return `Bước ${STEP_LABELS[running.stepType] || running.stepType}`;
    }
    const waiting = (job?.steps || []).find(step => step.status === "FAILED" || step.status === "PENDING");
    if (waiting) {
      // Khi FAILED, waitingReason chỉ là 1 MÃ ngắn (vd "WEB_IMAGE_SCRAPE_FAILED"),
      // không nói rõ vì sao — ưu tiên hiện job.runtimeMessage (câu chi tiết thật,
      // vd nội dung Gemini trả về) nếu có, chỉ dùng mã làm phương án dự phòng.
      if (waiting.status === "FAILED" && job?.runtimeMessage) {
        return job.runtimeMessage;
      }
      return waiting.waitingReason || STEP_LABELS[waiting.stepType] || waiting.stepType;
    }
    return job?.runtimeMessage || "Chưa chạy";
  }

  function deriveSummary(job) {
    if (job?.artifacts?.length) {
      return `${job.artifacts.length} artifact · ${(job.scenePrompts || []).length} cảnh`;
    }
    if (job?.generatedText) {
      return "Đã có script AI, chờ các bước tiếp theo.";
    }
    return "Chưa có kết quả nào.";
  }

  function mapJobStatus(job) {
    const status = String(job?.status || "").toUpperCase();
    if (status.includes("FAIL")) return "FAILED";
    if (status.includes("QUEUE")) return "QUEUED";
    if (status.includes("RUN")) return "RUNNING";
    if (status.includes("WAIT")) return "WAITING_USER";
    if (status.includes("COMPLETE") || status.includes("DONE") || status.includes("SUCCESS")) return "COMPLETED";
    const waitingUser = (job?.steps || []).some(step => String(step.waitingReason || "").toUpperCase().includes("USER"));
    if (waitingUser) return "WAITING_USER";
    const running = (job?.steps || []).some(step => step.status === "RUNNING");
    if (running) return "RUNNING";
    const pending = (job?.steps || []).some(step => step.status === "PENDING");
    if (pending && job?.jobId) return "QUEUED";
    return job?.jobId ? "RUNNING" : "DRAFT";
  }

  function getActiveSession() {
    return state.sessions.find(item => item.sessionId === state.activeSessionId) || null;
  }

  function getJobForSession(session) {
    return session ? state.jobs[session.sessionId] || null : null;
  }

  function getFilteredSessions() {
    if (state.filter === "ALL") return [...state.sessions];
    return state.sessions.filter(item => item.status === state.filter);
  }

  function persistSessions() {
    localStorage.setItem(SESSION_STORE_KEY, JSON.stringify(state.sessions));
  }

  function loadSessions() {
    try {
      const raw = localStorage.getItem(SESSION_STORE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.map(item => ({
        ...item,
        pendingTaskId: item?.pendingTaskId || "",
        pendingLabel: item?.pendingLabel || "",
        pendingState: item?.pendingState || "",
        config: {
          ...DEFAULT_SESSION_CONFIG,
          ...(item?.config || {}),
          durationSeconds: Number(item?.config?.durationSeconds || parseLegacyDurationSeconds(item?.config?.duration) || DEFAULT_SESSION_CONFIG.durationSeconds)
        }
      })) : [];
    } catch {
      return [];
    }
  }

  function getShellTitle() {
    return state.currentView === "home" ? "lqlq" : "lqlq Video Studio";
  }

  function buildSessionTitle(topic) {
    const normalized = String(topic || "").trim();
    if (!normalized) return "Dự án video mới";
    return normalized.length > 60 ? `${normalized.slice(0, 57)}...` : normalized;
  }

  function buildWorkspaceSubtitle(session) {
    if (!session) return "Phiên nháp";
    // activeStep có thể là cả runtimeMessage rất dài -> chỉ lấy phần đầu, phần
    // chi tiết/tiến trình đã hiển thị ở danh sách phiên (ngoài không gian làm việc).
    const step = (session.activeStep || "Chưa chạy").split(/[.\n]/)[0].trim();
    const shortStep = step.length > 48 ? step.slice(0, 47).trimEnd() + "…" : step;
    return `${formatSessionStatus(session.status)} · ${shortStep}`;
  }

  function formatProgressCopy(session) {
    return `${Math.round(Number(session.progressPercent || 0))}% · ${session.activeStep || "Chưa chạy"}`;
  }

  function formatSessionStatus(status) {
    return ({
      DRAFT: "Bản nháp",
      QUEUED: "Đang chờ",
      RUNNING: "Đang chạy",
      PAUSED: "Tạm dừng",
      WAITING_USER: "Chờ người dùng",
      COMPLETED: "Hoàn tất",
      FAILED: "Lỗi",
      CANCELLED: "Đã hủy"
    })[status] || status;
  }

  function formatFilterLabel(filter) {
    return ({
      ALL: "Tất cả",
      RUNNING: "Đang chạy",
      QUEUED: "Đang chờ",
      PAUSED: "Tạm dừng",
      COMPLETED: "Hoàn tất",
      FAILED: "Lỗi",
      WAITING_USER: "Chờ người dùng"
    })[filter] || filter;
  }

  function isSessionBusy(session) {
    if (!session) return false;
    const status = String(session.status || "").toUpperCase();
    const pendingState = String(session.pendingState || "").toUpperCase();
    if (status === "PAUSED" || pendingState === "CANCELLING") return false;
    if (String(session.pendingTaskId || "").trim()) return true;
    if (pendingState === "QUEUED" || pendingState === "RUNNING") return true;
    return status === "QUEUED" || status === "RUNNING";
  }

  function fallbackBusyLabel(session) {
    const status = String(session?.status || "").toUpperCase();
    if (status === "QUEUED") return "Đang chờ trong hàng đợi...";
    return session?.activeStep || "Đang xử lý nền...";
  }

  function hasOtherBusySession(sessionId) {
    return state.sessions.some(item => item.sessionId !== sessionId && isSessionBusy(item));
  }

  function enqueueSessionForLater(session, contentOnly = false) {
    session.contentOnlyRun = contentOnly;
    session.pendingTaskId = "";
    session.pendingLabel = "Dang cho du an khac hoan tat...";
    session.pendingState = "QUEUED";
    session.status = "QUEUED";
    session.activeStep = "Dang cho luot chay";
    session.updatedAt = Date.now();
    persistSessions();
    setTransient("Da dua du an vao danh sach cho. Du an nay se tu dong bat dau khi du an dang chay hoan tat hoac dung.");
    render();
    tryStartNextQueuedSession();
  }

  function tryStartNextQueuedSession() {
    if (state.sessions.some(item => String(item.pendingTaskId || "").trim())) {
      return false;
    }
    const nextSession = state.sessions.find(item =>
      !String(item.pendingTaskId || "").trim() &&
      String(item.pendingState || "").toUpperCase() === "QUEUED" &&
      String(item.activeStep || "").toLowerCase().includes("cho")
    );
    if (!nextSession) return false;
    nextSession.pendingLabel = "";
    nextSession.pendingState = "";
    nextSession.status = "DRAFT";
    nextSession.activeStep = "Chuan bi chay";
    nextSession.updatedAt = Date.now();
    persistSessions();
    runSession(nextSession.sessionId, !!nextSession.contentOnlyRun);
    return true;
  }

  function updateTopicMeta() {
    const field = document.getElementById("videoSessionTopicInput");
    const meta = document.getElementById("videoSessionTopicMeta");
    if (!field || !meta) return;
    const length = String(field.value || "").trim().length;
    meta.textContent = `${length}/50000 ký tự · nhập mô tả càng rõ thì script và scene càng sát ý hơn.`;
  }

  function updateVoiceSliderLabels() {
    const rate = root.querySelector("#videoVoiceRate");
    const pitch = root.querySelector("#videoVoicePitch");
    if (rate?.previousElementSibling) {
      rate.previousElementSibling.textContent = `Tốc độ đọc · ${state.settingsDraft.voiceRate}`;
    }
    if (pitch?.previousElementSibling) {
      pitch.previousElementSibling.textContent = `Tông đọc · ${state.settingsDraft.voicePitch}`;
    }
  }

  function updateWorkspaceHeader() {
    const session = getActiveSession();
    const title = root.querySelector(".video-workspace-head-copy h2");
    if (title && session) title.textContent = session.title;
  }

  function updateSessionHomeCards() {
    if (state.currentView === "home") render();
  }

  function getWorkspaceBody() {
    return root.querySelector(".video-workspace-body");
  }

  function getSettingsSheetBody() {
    return root.querySelector(".video-workspace-sheet .video-workspace-sheet-scroll");
  }

  function getScrollMemoryKey() {
    if (state.currentView === "home") {
      return "home";
    }
    return `workspace:${state.activeSessionId || "none"}:${state.activePage || "content"}`;
  }

  function readCurrentScrollTop() {
    const body = getWorkspaceBody();
    return body ? body.scrollTop : 0;
  }

  function getSettingsScrollMemoryKey() {
    if (!state.open || !state.settingsOpen) return "";
    return `settings:${state.currentView || "workspace"}:${state.settingsTab || "content"}`;
  }

  function readCurrentSettingsScrollTop() {
    const body = getSettingsSheetBody();
    return body ? body.scrollTop : 0;
  }

  function rememberCurrentScroll() {
    const key = getScrollMemoryKey();
    const body = getWorkspaceBody();
    if (!key || !body) return;
    scrollMemory[key] = body.scrollTop || 0;
  }

  function rememberCurrentSettingsScroll() {
    const key = getSettingsScrollMemoryKey();
    const body = getSettingsSheetBody();
    if (!key || !body) return;
    scrollMemory[key] = body.scrollTop || 0;
  }

  function restoreScrollPosition(key, fallback = 0) {
    const body = getWorkspaceBody();
    if (!body) return;
    const remembered = scrollMemory[key];
    const target = Number.isFinite(remembered) ? remembered : fallback;
    body.scrollTop = Math.max(0, Number(target || 0));
  }

  function restoreSettingsScrollPosition(key, fallback = 0) {
    const body = getSettingsSheetBody();
    if (!body) return;
    const remembered = scrollMemory[key];
    const target = Number.isFinite(remembered) ? remembered : fallback;
    body.scrollTop = Math.max(0, Number(target || 0));
  }

  function backgroundMusicSettingsSignature(settings) {
    const current = settings || DEFAULT_BACKGROUND_MUSIC_SETTINGS;
    return [
      current.hasMusic ? "1" : "0",
      String(current.displayName || "").trim(),
      String(current.mimeType || "").trim(),
      current.loop ? "1" : "0",
      Number(current.volume ?? 0.35).toFixed(3)
    ].join("|");
  }

  function setTransient(message) {
    state.transientMessage = message;
  }

  function setError(message) {
    state.errorMessage = message || "";
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function multilineHtml(value) {
    return escapeHtml(value).replace(/\r?\n/g, "<br>");
  }

  function parseResponse(raw) {
    try {
      return JSON.parse(String(raw || ""));
    } catch {
      return { ok: false, message: "Phản hồi Automation không hợp lệ." };
    }
  }

  function renderReviewCheck(ok, successText, failureText, warn = false) {
    const klass = ok ? "" : warn ? "warn" : "fail";
    const icon = ok ? "✓" : warn ? "⚠" : "✕";
    const text = ok ? successText : failureText;
    return `
      <div class="video-review-check ${klass}">
        <span class="video-review-icon">${icon}</span>
        <div>${escapeHtml(text)}</div>
      </div>
    `;
  }

  function groupWorkspacePages() {
    const groups = [];
    WORKSPACE_PAGES.forEach(item => {
      let group = groups.find(entry => entry.group === item.group);
      if (!group) {
        group = { group: item.group, items: [] };
        groups.push(group);
      }
      group.items.push(item);
    });
    return groups;
  }

  function filterArtifacts(job, type) {
    return Array.isArray(job?.artifacts)
      ? job.artifacts.filter(item => String(item.artifactType || "").toUpperCase() === type)
      : [];
  }

  function parseSourceUrlFields(sourceUrl) {
    const fields = {};
    String(sourceUrl || "")
      .split(";")
      .forEach(part => {
        const [key, ...rest] = part.split("=");
        if (!key) return;
        fields[key.trim()] = rest.join("=").trim();
      });
    return fields;
  }

  function formatRelativeTime(epochMs) {
    if (!epochMs) return "Chưa cập nhật";
    const diffMinutes = Math.max(0, Math.round((Date.now() - Number(epochMs)) / 60000));
    if (diffMinutes < 1) return "Vừa xong";
    if (diffMinutes < 60) return `Cập nhật ${diffMinutes} phút trước`;
    const diffHours = Math.round(diffMinutes / 60);
    if (diffHours < 24) return `Cập nhật ${diffHours} giờ trước`;
    const diffDays = Math.round(diffHours / 24);
    return `Cập nhật ${diffDays} ngày trước`;
  }

  function formatAbsoluteTime(epochMs) {
    try {
      return new Date(Number(epochMs)).toLocaleString("vi-VN");
    } catch {
      return "-";
    }
  }

  function formatDurationMs(durationMs) {
    const ms = Number(durationMs || 0);
    if (!ms) return "-";
    const totalSeconds = Math.round(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }

  function inferOutputLength(topic, desiredDurationSeconds) {
    // MỘT logic duy nhất cho MỌI thời lượng (60s hay 30000s như nhau) - không chia
    // mốc. Thời lượng chỉ là con số trong prompt để Gemini tự quyết viết dài bao
    // nhiêu; app KHÔNG ép độ dài đầu ra khác nhau theo thời lượng nữa.
    return 12000;
  }

  function getSessionDurationSeconds(session) {
    const value = Number(session?.config?.durationSeconds || 0);
    if (Number.isFinite(value) && value > 0) return clampNumber(value, 5, 7200, 60);
    const legacy = parseLegacyDurationSeconds(session?.config?.duration);
    return clampNumber(legacy, 5, 7200, 60);
  }

  function parseLegacyDurationSeconds(value) {
    const normalized = String(value || "");
    const match = normalized.match(/(\d{1,4})/);
    return match ? Number(match[1]) : 60;
  }

  function ensureJobPolling() {
    if (!state.open || jobPollTimer) return;
    jobPollTimer = window.setInterval(() => {
      if (!state.open) return;
      refreshAllLinkedJobs();
    }, 2500);
  }

  function stopJobPolling() {
    if (!jobPollTimer) return;
    window.clearInterval(jobPollTimer);
    jobPollTimer = 0;
  }

  function clampNumber(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.min(max, Math.max(min, Math.round(number)));
  }

  function getSelectedImageProvider() {
    const id = state.settingsDraft.imageProviderId || state.selectedImageProviderId;
    return state.imageProviders.find(item => item.providerId === id) || null;
  }

  function getSelectedVoiceProvider() {
    const id = state.settingsDraft.voiceProviderId || state.selectedVoiceProviderId;
    return state.voiceProviders.find(item => item.providerId === id) || null;
  }

  function syncImageDraftFromSelectedProvider() {
    const provider = getSelectedImageProvider();
    if (!provider) return;
    state.selectedImageProviderId = provider.providerId;
    const status = provider.configurationStatus || {};
    const supportedModels = Array.isArray(provider.supportedModels) ? provider.supportedModels : [];
    const effectiveModel = String(status.model || provider.defaultModel || supportedModels[0] || "").trim();
    const useCustom = effectiveModel && !supportedModels.includes(effectiveModel);
    state.settingsDraft.imageModelPreset = useCustom ? CUSTOM_MODEL_VALUE : (effectiveModel || supportedModels[0] || CUSTOM_MODEL_VALUE);
    state.settingsDraft.imageModelCustom = useCustom ? effectiveModel : "";
    state.settingsDraft.imageAccountId = status.accountId || state.settingsDraft.imageAccountId || "";
  }

  function syncVoiceDraftFromSelectedProvider() {
    const provider = getSelectedVoiceProvider();
    if (!provider) return;
    state.selectedVoiceProviderId = provider.providerId;
    const status = provider.configurationStatus || {};
    state.settingsDraft.voiceLocale = status.locale || provider.defaultLocale || state.settingsDraft.voiceLocale || "vi-VN";
    state.settingsDraft.voiceId = status.voiceId || state.settingsDraft.voiceId || "";
    state.settingsDraft.voiceModel = status.model || state.settingsDraft.voiceModel || "";
    state.settingsDraft.voiceOutputFormat = status.outputFormat || state.settingsDraft.voiceOutputFormat || provider.supportedOutputFormats?.[0] || "wav";
  }

  function renderImageModelOptions(provider) {
    const supportedModels = Array.isArray(provider?.supportedModels) ? provider.supportedModels : [];
    const current = state.settingsDraft.imageModelPreset || CUSTOM_MODEL_VALUE;
    return supportedModels.map(model =>
      `<option value="${escapeHtml(model)}"${current === model ? " selected" : ""}>${escapeHtml(model)}</option>`
    ).join("") + `<option value="${CUSTOM_MODEL_VALUE}"${current === CUSTOM_MODEL_VALUE ? " selected" : ""}>Nhập model tùy chỉnh</option>`;
  }

  function getSelectedImageModel() {
    if (state.settingsDraft.imageModelPreset === CUSTOM_MODEL_VALUE) {
      return String(state.settingsDraft.imageModelCustom || "").trim();
    }
    return String(state.settingsDraft.imageModelPreset || "").trim();
  }

  function isSafeImageModel(provider, model) {
    const normalized = String(model || "").trim();
    if (!normalized) return false;
    if (
      normalized.includes("://") ||
      normalized.includes("\\") ||
      normalized.includes("{") ||
      normalized.includes("}") ||
      normalized.includes('"') ||
      /\s/.test(normalized)
    ) {
      return false;
    }
    if (provider?.providerId === "cloudflare-workers-ai") {
      return SAFE_CLOUDFLARE_MODEL.test(normalized);
    }
    if (provider?.providerId === "openai-images") {
      return SAFE_OPENAI_MODEL.test(normalized) && !normalized.includes("/");
    }
    return normalized.length <= 128;
  }

  function sliderToScalar(value) {
    return Math.min(Math.max(Number(value || 100) / 100, 0.5), 2.0);
  }

  function formatGeminiStatusText() {
    if (state.geminiStatus.state === "CONNECTED") {
      return `Đã kết nối Gemini · model: ${state.geminiStatus.model || DEFAULT_GEMINI_MODEL}`;
    }
    if (state.geminiStatus.state === "INVALID") {
      return `Gemini chưa hợp lệ · model: ${state.geminiStatus.model || DEFAULT_GEMINI_MODEL}`;
    }
    return "Chưa cấu hình Gemini.";
  }

  function formatImageStatusText(provider) {
    if (!provider) return "Chưa có dữ liệu image provider.";
    const status = provider.configurationStatus || {};
    const model = status.model || provider.defaultModel || "-";
    if (provider.health === "NOT_IMPLEMENTED") {
      return `${provider.displayName} đã có trong registry nhưng chưa được port native.`;
    }
    return `${provider.displayName} · ${status.state || "NOT_CONFIGURED"} · model: ${model}`;
  }

  function formatVoiceStatusText(provider) {
    if (!provider) return "Chưa có dữ liệu voice provider.";
    const status = provider.configurationStatus || {};
    if (provider.health === "NOT_IMPLEMENTED") {
      return `${provider.displayName} đã có trong registry nhưng connector thật vẫn chưa hoàn tất.`;
    }
    return `${provider.displayName} · ${status.state || "NOT_CONFIGURED"} · locale: ${status.locale || provider.defaultLocale || "vi-VN"}`;
  }

  function formatSettingsTab(tab) {
    return ({
      content: "Nội dung",
      image: "Hình ảnh",
      voice: "Giọng đọc",
      video: "Video",
      process: "Tiến trình",
      publish: "Đăng",
      background: "Chạy nền"
    })[tab] || tab;
  }

  window.LqlqAutomationCenter = {
    openFromTools() {
      state.open = true;
      state.currentView = "home";
      state.navOpen = false;
      state.settingsOpen = false;
      state.errorMessage = "";
      refreshSettingsData();
      refreshAllLinkedJobs();
      render();
    }
  };
})();
