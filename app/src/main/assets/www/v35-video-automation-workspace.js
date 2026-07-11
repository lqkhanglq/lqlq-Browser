(() => {
  "use strict";

  const bridge = window.LqlqAutomation;

  const SESSION_STORE_KEY = "lqlq-video-automation-sessions-v35";
  const FILTERS = ["ALL", "RUNNING", "QUEUED", "PAUSED", "COMPLETED", "FAILED", "WAITING_USER"];
  const SETTINGS_TABS = ["content", "image", "voice", "video", "publish", "background"];
  const WORKSPACE_PAGES = [
    { id: "content", group: "SOẠN NỘI DUNG", label: "Nội dung và cấu hình" },
    { id: "images", group: "TÀI NGUYÊN", label: "Hình ảnh" },
    { id: "voice", group: "TÀI NGUYÊN", label: "Giọng đọc" },
    { id: "video", group: "THÀNH PHẨM", label: "Xem trước video" },
    { id: "metadata", group: "THÀNH PHẨM", label: "Metadata" },
    { id: "review", group: "THÀNH PHẨM", label: "Kiểm duyệt" },
    { id: "publish", group: "THÀNH PHẨM", label: "Đăng video" },
    { id: "progress", group: "HỆ THỐNG", label: "Tiến trình" },
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
  const DEFAULT_SESSION_CONFIG = {
    platform: "shorts",
    aspectRatio: "9:16",
    durationSeconds: 60,
    requestedSceneCount: 12,
    language: "Tiếng Việt"
  };
  const PLATFORM_OPTIONS = [
    { value: "shorts", label: "Shorts" },
    { value: "tiktok", label: "TikTok" },
    { value: "reels", label: "Reels" }
  ];
  const ASPECT_OPTIONS = ["9:16", "16:9", "1:1", "3:4", "4:3", "21:9"];
  const SAFE_OPENAI_MODEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
  const SAFE_CLOUDFLARE_MODEL = /^@[A-Za-z0-9._/-]{3,127}$/;
  const SAFE_VOICE_MODEL = /^[A-Za-z0-9][A-Za-z0-9._@/-]{0,127}$/;
  const CUSTOM_MODEL_VALUE = "__custom__";
  const DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

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
    settingsDraft: createDefaultSettingsDraft(),
    errorMessage: "",
    transientMessage: "Tạo phiên video theo workflow canonical hiện có."
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

  function createDefaultSettingsDraft() {
    return {
      geminiApiKey: "",
      geminiModel: DEFAULT_GEMINI_MODEL,
      imageProviderId: "",
      imageModelPreset: "",
      imageModelCustom: "",
      imageApiKey: "",
      imageAccountId: "",
      voiceProviderId: "",
      voiceApiKey: "",
      voiceModel: "",
      voiceRegion: "",
      voiceLocale: "vi-VN",
      voiceId: "",
      voiceOutputFormat: "wav",
      voiceRate: 100,
      voicePitch: 100
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
.video-workspace-head-copy { min-width: 0; flex: 1; }
.video-workspace-head-copy h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
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
`;
    document.head.appendChild(style);
  }

  function bindGlobalEvents(container) {
    container.addEventListener("click", handleClick);
    container.addEventListener("input", handleInput);
    container.addEventListener("change", handleChange);
  }

  function handleClick(event) {
    const aspectEl = event.target.closest("[data-video-aspect]");
    if (aspectEl) {
      const session = getActiveSession();
      if (session) {
        session.config.aspectRatio = aspectEl.dataset.videoAspect || session.config.aspectRatio;
        session.updatedAt = Date.now();
        persistSessions();
        render();
      }
      return;
    }
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
        render();
        break;
      case "settings-tab":
        state.settingsTab = actionEl.dataset.tab || "content";
        if (state.settingsTab === "voice") {
          refreshVoiceDefinitions();
        }
        render();
        break;
      case "run-session":
        runSession(actionEl.dataset.sessionId || "");
        break;
      case "generate-content":
        runSession();
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
      case "resume-session":
        setTransient("Tạm dừng/tiếp tục chưa được Android WorkManager hỗ trợ an toàn cho pipeline đang gọi provider. Bạn có thể dừng phiên rồi chạy lại.");
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
    if (target.id === "videoRequestedSceneCount" && session) {
      session.config.requestedSceneCount = clampNumber(target.value, 1, 24, 12);
      persistSessions();
      return;
    }
    if (target.id === "videoDurationSeconds" && session) {
      session.config.durationSeconds = clampNumber(target.value, 5, 7200, 60);
      session.updatedAt = Date.now();
      persistSessions();
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
    if (target.matches("[data-video-aspect]") && session) {
      session.config.aspectRatio = target.dataset.videoAspect;
      session.updatedAt = Date.now();
      persistSessions();
      render();
      return;
    }
    if (target.id === "videoImageProviderSelect") {
      state.settingsDraft.imageProviderId = target.value;
      if (!state.settingsDraft.imageModelPreset) {
        state.settingsDraft.imageModelPreset = "";
      }
      syncImageDraftFromSelectedProvider();
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
      title: "Phiên video mới",
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
    state.sessions = state.sessions.filter(item => item.sessionId !== sessionId);
    delete state.jobs[sessionId];
    if (state.activeSessionId === sessionId) {
      state.activeSessionId = "";
      state.currentView = "home";
    }
    persistSessions();
    setTransient("Đã xóa phiên.");
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
      </div>
    `;
    updateTopicMeta();
    updateVoiceSliderLabels();
    restoreScrollPosition(restoreKey, previousScrollTop);
  }

  function renderHeader() {
    if (state.currentView === "home") {
      return `
        <header class="video-workspace-head">
          <span class="video-workspace-icon" aria-hidden="true">▶</span>
          <div class="video-workspace-head-copy">
            <h2>Phiên tạo video</h2>
          </div>
          <div class="video-workspace-head-actions">
            <button class="video-workspace-secondary" type="button" data-video-action="create-session">＋ Tạo phiên mới</button>
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
          <h2>${escapeHtml(session?.title || "Phiên video")}</h2>
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
      <div class="video-session-topbar">
        <div class="video-helper-copy">
          <b>Danh sách phiên</b><br>
          Tạo, theo dõi và mở lại từng dự án video theo workflow hiện có.
        </div>
        <div class="video-inline-actions">
          <button class="video-workspace-secondary" type="button" data-video-action="create-session">＋ Tạo phiên mới</button>
          <button class="video-workspace-secondary" type="button" data-video-action="open-settings">⚙ Cài đặt tự động</button>
        </div>
      </div>
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
        ${session.pendingTaskId ? `<div class="video-settings-state" style="margin-top:8px;">⏳ ${escapeHtml(session.pendingLabel || "Đang chạy nền...")}</div>` : ""}
        <div class="video-workspace-footer">
          ${renderSessionActions(session)}
        </div>
      </article>
    `;
  }

  function renderSessionActions(session) {
    const buttons = [];
    if (session.status === "DRAFT") {
      buttons.push(actionButton("open-session", "Chỉnh sửa", session.sessionId, "secondary"));
      buttons.push(actionButton("run-session", "Chạy", session.sessionId, "primary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "danger"));
    } else if (session.status === "QUEUED") {
      buttons.push(actionButton("open-session", "Mở phiên", session.sessionId, "secondary"));
      buttons.push(actionButton("remove-queue", "Bỏ khỏi hàng đợi", session.sessionId, "ghost"));
    } else if (session.status === "RUNNING") {
      buttons.push(actionButton("open-session", "Mở", session.sessionId, "secondary"));
      buttons.push(actionButton("stop-session", "Dừng", session.sessionId, "ghost"));
    } else if (session.status === "PAUSED") {
      buttons.push(actionButton("open-session", "Chỉnh sửa", session.sessionId, "secondary"));
      buttons.push(actionButton("run-session", "Chạy lại", session.sessionId, "primary"));
    } else if (session.status === "WAITING_USER") {
      buttons.push(actionButton("open-session", "Mở kiểm tra", session.sessionId, "secondary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "ghost"));
    } else if (session.status === "COMPLETED") {
      buttons.push(actionButton("open-session", "Xem kết quả", session.sessionId, "secondary"));
      buttons.push(actionButton("duplicate-session", "Nhân bản", session.sessionId, "primary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "ghost"));
    } else {
      buttons.push(actionButton("open-session", "Xem lỗi", session.sessionId, "secondary"));
      buttons.push(actionButton("run-session", "Thử lại", session.sessionId, "primary"));
      buttons.push(actionButton("delete-session", "Xóa", session.sessionId, "danger"));
    }
    return buttons.join("");
  }

  function actionButton(action, label, sessionId, tone) {
    const className = tone === "primary"
      ? "video-workspace-primary"
      : tone === "danger"
        ? "video-workspace-danger"
        : tone === "ghost"
          ? "video-workspace-ghost"
          : "video-workspace-secondary";
    return `<button class="${className}" type="button" data-video-action="${action}" data-session-id="${escapeHtml(sessionId)}">${escapeHtml(label)}</button>`;
  }

  function renderEmptyState() {
    return `
      <section class="video-session-empty">
        <b>Chưa có phiên tạo video nào</b>
        <p class="video-muted-copy">Tạo một phiên mới để bắt đầu từ chủ đề, hình ảnh, giọng đọc đến video hoàn chỉnh.</p>
        <div class="video-workspace-footer">
          <button class="video-workspace-primary" type="button" data-video-action="create-session">＋ Tạo phiên mới</button>
        </div>
      </section>
    `;
  }

  function renderWorkspaceView() {
    const session = getActiveSession();
    return `
      <div class="video-page-grid">
        ${session?.pendingTaskId ? `
          <section class="video-page-card">
            <div class="video-settings-state">⏳ ${escapeHtml(session.pendingLabel || "Đang chạy nền...")} (${escapeHtml(Math.max(0, Math.min(100, Number(session.progressPercent || 0))))}%) Bạn có thể rời màn hình này, mở web hoặc tạo phiên mới.</div>
            <div class="video-session-progress" style="margin-top:8px;">
              <span style="width:${Math.max(0, Math.min(100, Number(session.progressPercent || 0)))}%"></span>
            </div>
          </section>
        ` : ""}
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
    const expectedCount = Number(session.config.requestedSceneCount || 12);
    const parsedCount = Array.isArray(job?.scenePrompts) ? job.scenePrompts.length : 0;
    const warning = parsedCount && parsedCount < Math.max(10, expectedCount - 1)
      ? `<div class="video-settings-state error">Cảnh báo: dự kiến ${expectedCount} cảnh nhưng hiện chỉ có ${parsedCount} cảnh được parse.</div>`
      : "";
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Nội dung và cấu hình</b>
            <small>Đây là trang mặc định của phiên. Chỉ lưu chủ đề và preset phiên, không chứa API key/provider.</small>
          </div>
          <span class="video-mini-pill">${escapeHtml(formatSessionStatus(session.status))}</span>
        </div>

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
            <input class="video-input" id="videoDurationSeconds" type="number" min="5" max="7200" value="${escapeHtml(getSessionDurationSeconds(session))}">
          </div>
          <div class="video-field">
            <span>Ngôn ngữ</span>
            <select class="video-select" id="videoLanguageSelect">
              <option${session.config.language === "Tiếng Việt" ? " selected" : ""}>Tiếng Việt</option>
              <option${session.config.language === "English" ? " selected" : ""}>English</option>
            </select>
          </div>
        </div>

        <div class="video-field" style="margin-top:12px;">
          <span>Khung hình</span>
          <div class="video-choice-row aspect">
            ${ASPECT_OPTIONS.map(option => `
              <button class="video-workspace-chip-button ${session.config.aspectRatio === option ? "active" : ""}" type="button" data-video-aspect="${option}">
                ${escapeHtml(option)}
              </button>
            `).join("")}
          </div>
        </div>

        <div class="video-field" style="margin-top:12px;">
          <span>Số cảnh mong muốn</span>
          <input class="video-input" id="videoRequestedSceneCount" type="number" min="1" max="24" value="${escapeHtml(session.config.requestedSceneCount)}">
        </div>

        <div class="video-settings-state" style="margin-top:12px;">
          Thời lượng gửi thẳng xuống pipeline: ${escapeHtml(getSessionDurationSeconds(session))} giây. Gemini được yêu cầu viết đủ lời đọc ít nhất bằng thời lượng này; dài hơn được phép, ngắn hơn không đạt.
        </div>

        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="open-settings">⚙ Cài đặt tự động</button>
          <button class="video-workspace-primary" type="button" data-video-action="generate-content">Tạo nội dung</button>
        </div>
      </section>

      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Kịch bản AI</b>
            <small>${escapeHtml(job?.runtimeMessage || "Sau khi chạy, kịch bản và danh sách cảnh sẽ hiện ở đây.")}</small>
          </div>
          <span class="video-mini-pill">${escapeHtml(job?.providerId || "Chưa có provider")}</span>
        </div>
        ${warning}
        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-settings-state">Số ký tự script: ${escapeHtml((job?.generatedText || "").length)}</div>
          <div class="video-settings-state">Số cảnh parse được: ${escapeHtml(parsedCount)}</div>
          <div class="video-settings-state">Số cảnh kỳ vọng: ${escapeHtml(expectedCount)}</div>
        </div>
        <div class="video-page-card" style="margin-top:12px;">
          <div class="video-muted-copy">${multilineHtml(job?.generatedText || "Chưa có nội dung được tạo.")}</div>
        </div>
      </section>

      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Danh sách cảnh</b>
            <small>Mỗi cảnh được mở rộng riêng để xem tóm tắt, visual prompt và negative prompt.</small>
          </div>
        </div>
        <div class="video-scene-list" style="margin-top:12px;">
          ${parsedCount ? job.scenePrompts.map(renderScenePromptCard).join("") : `<div class="video-settings-state">Chưa có scene prompts cho phiên này.</div>`}
        </div>
      </section>
    `;
  }

  function renderScenePromptCard(scene) {
    return `
      <details class="video-scene-card">
        <summary>
          <b>Cảnh ${escapeHtml(scene.ordinal)}</b>
          <div class="video-scene-meta">
            <span class="video-mini-pill">${escapeHtml(scene.aspectRatio || "9:16")}</span>
          </div>
        </summary>
        <div style="margin-top:10px;">
          <div class="video-field"><span>Tóm tắt</span><div class="video-settings-state">${escapeHtml(scene.summary || "-")}</div></div>
          <div class="video-field" style="margin-top:8px;"><span>Visual prompt</span><div class="video-settings-state">${multilineHtml(scene.visualPrompt || "-")}</div></div>
          <div class="video-field" style="margin-top:8px;"><span>Negative prompt</span><div class="video-settings-state">${multilineHtml(scene.negativePrompt || "-")}</div></div>
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
            <small>${escapeHtml(images.length)}/${escapeHtml((job?.scenePrompts || []).length || session.config.requestedSceneCount)} cảnh đã có visual.</small>
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
            <button class="video-workspace-secondary" type="button" data-video-action="render-video">Render video</button>
            <button class="video-workspace-secondary" type="button" data-video-action="retry-video">Render lại</button>
            ${videoArtifact ? `<button class="video-workspace-primary" type="button" data-video-action="export-video">Export MP4</button>` : ""}
          </div>
        </div>
      </section>
      <section class="video-page-card">
        ${videoArtifact ? `
          <div class="video-settings-state">Đã có VIDEO_MP4. URI: ${escapeHtml(videoArtifact.uri || "-")}</div>
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
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Đăng video</b>
            <small>Pass này đang ở manual-assisted. Chỉ mở share sheet và đánh dấu trạng thái khi bạn xác nhận.</small>
          </div>
        </div>
        <div class="video-info-grid" style="margin-top:12px;">
          <div class="video-settings-state">Video: ${escapeHtml(video?.uri || "Chưa có VIDEO_MP4")}</div>
          <div class="video-settings-state">Trạng thái kiểm duyệt: ${escapeHtml(reviewStatus)}</div>
          <div class="video-settings-state">Nền tảng gợi ý: YouTube Shorts / TikTok / Facebook Reels</div>
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="share">Chia sẻ / Mở ứng dụng đăng</button>
          <button class="video-workspace-primary" type="button" data-video-action="publish">Đánh dấu đã đăng</button>
        </div>
      </section>
    `;
  }

  function renderProgressPage(session) {
    const job = getJobForSession(session);
    const stepMap = new Map((job?.steps || []).map(step => [step.stepType, step]));
    return `
      <section class="video-page-card">
        <div class="video-page-head">
          <div>
            <b>Tiến trình</b>
            <small>Workflow canonical của phiên, hiển thị gọn theo stepper.</small>
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
      case "video": return renderPlaceholderSettings("Video", "Renderer và preset video mặc định sẽ được gom ở đây, nhưng pass này chưa thay pipeline.");
      case "publish": return renderPlaceholderSettings("Đăng", "Tài khoản YouTube, TikTok, Facebook sẽ nằm ở đây, tách khỏi từng phiên.");
      case "background": return renderPlaceholderSettings("Chạy nền", "Phiên giờ chạy nền theo hàng chờ native. Notification và poll trạng thái đã được nối để bạn có thể tiếp tục duyệt web.");
      default: return renderContentSettings();
    }
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
          ${escapeHtml(formatVoiceStatusText(provider))}
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
            <span>Credential</span>
            <input class="video-input" id="videoVoiceApiKey" type="password" autocomplete="off" value="" placeholder="Nhập credential voice provider">
          </div>
        ` : ""}
        <div class="video-field" style="margin-top:12px;">
          <span>Model / profile</span>
          <input class="video-input" id="videoVoiceModel" type="text" autocomplete="off" value="${escapeHtml(state.settingsDraft.voiceModel || "")}">
        </div>
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
        <div class="video-settings-grid" style="margin-top:12px;">
          <div class="video-field">
            <span>Tốc độ đọc · ${escapeHtml(state.settingsDraft.voiceRate)}</span>
            <input class="video-input" id="videoVoiceRate" type="range" min="50" max="200" step="1" value="${escapeHtml(state.settingsDraft.voiceRate)}">
          </div>
          <div class="video-field">
            <span>Tông đọc · ${escapeHtml(state.settingsDraft.voicePitch)}</span>
            <input class="video-input" id="videoVoicePitch" type="range" min="50" max="200" step="1" value="${escapeHtml(state.settingsDraft.voicePitch)}">
          </div>
        </div>
        <div class="video-workspace-footer">
          <button class="video-workspace-secondary" type="button" data-video-action="open-voice-settings">Mở cài đặt Google TTS</button>
          <button class="video-workspace-secondary" type="button" data-video-action="sample-voice">Nghe thử</button>
          <button class="video-workspace-secondary" type="button" data-video-action="test-voice">Nạp và kiểm tra giọng</button>
          <button class="video-workspace-primary" type="button" data-video-action="save-voice">Lưu cấu hình voice</button>
        </div>
      </section>
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

  function runSession(sessionId = "") {
    const session = sessionId
      ? state.sessions.find(item => item.sessionId === sessionId)
      : getActiveSession();
    if (!session) return;
    state.activeSessionId = session.sessionId;
    state.currentView = "workspace";
    state.activePage = "content";
    state.navOpen = false;
    state.settingsOpen = false;
    if (!bridge?.runAutomationContentAsync) {
      setError("Automation Bridge chạy nền chưa sẵn sàng trong shell Android này.");
      render();
      return;
    }
    if (session.pendingTaskId) {
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
    const requestedSceneCount = Number(session.config.requestedSceneCount || 12);
    const maximumOutputLength = inferOutputLength(topic, requestedSceneCount, desiredDurationSeconds);
    setError("");
    const dispatch = parseResponse(
      bridge.runAutomationContentAsync(JSON.stringify({
        topic,
        language: session.config.language === "English" ? "en" : "vi",
        contentType: "video_script",
        maximumOutputLength,
        desiredDurationSeconds,
        requestedSceneCount
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
      jobId: session.linkedJobId
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
    if (jobId && bridge?.getAutomationJob) {
      const jobResponse = parseResponse(bridge.getAutomationJob(jobId));
      if (jobResponse.ok && jobResponse.job) {
        state.jobs[session.sessionId] = jobResponse.job;
        syncSessionFromJob(session, jobResponse.job);
      }
    }
    session.updatedAt = Date.now();
    persistSessions();
    setTransient(options.doneMessage || "Đã hoàn tất tác vụ chạy nền.");
    if (state.open) render();
  }

  function cancelPendingTask(sessionId) {
    const session = state.sessions.find(item => item.sessionId === sessionId);
    if (!session?.pendingTaskId) {
      setTransient("Phiên này không có tác vụ nền để dừng.");
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
    session.pendingTaskId = "";
    session.pendingLabel = "";
    session.pendingState = "";
    session.status = "DRAFT";
    session.activeStep = "Đã dừng";
    session.updatedAt = Date.now();
    persistSessions();
    setTransient("Đã yêu cầu dừng tác vụ và loại khỏi hàng chờ.");
    render();
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
    const response = parseResponse(bridge.getImageProviderConfigurationStatus());
    if (!response.ok) return;
    state.imageProviders = Array.isArray(response.providers) ? response.providers : [];
    const selected = state.imageProviders.find(item => item.selected)?.providerId || state.selectedImageProviderId || state.imageProviders[0]?.providerId || "";
    state.selectedImageProviderId = selected;
    if (!state.settingsDraft.imageProviderId) {
      state.settingsDraft.imageProviderId = selected;
    }
    syncImageDraftFromSelectedProvider();
  }

  function refreshVoiceStatus() {
    if (!bridge?.getVoiceProviderConfigurationStatus) return;
    const providerId = state.settingsDraft.voiceProviderId || state.selectedVoiceProviderId || "";
    const response = parseResponse(bridge.getVoiceProviderConfigurationStatus(JSON.stringify({ providerId })));
    if (!response.ok) return;
    state.voiceProviders = Array.isArray(response.providers) ? response.providers : [];
    const selected = state.voiceProviders.find(item => item.selected)?.providerId || state.selectedVoiceProviderId || state.voiceProviders[0]?.providerId || "";
    state.selectedVoiceProviderId = selected;
    if (!state.settingsDraft.voiceProviderId) {
      state.settingsDraft.voiceProviderId = selected;
    }
    syncVoiceDraftFromSelectedProvider();
    refreshVoiceDefinitions();
  }

  function refreshVoiceDefinitions() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.listVoiceDefinitions || !provider || provider.health === "NOT_IMPLEMENTED") {
      state.voiceDefinitions = [];
      return;
    }
    const response = parseResponse(bridge.listVoiceDefinitions(JSON.stringify({ providerId: provider.providerId })));
    state.voiceDefinitions = response.ok && Array.isArray(response.voices) ? response.voices : [];
    if (!state.settingsDraft.voiceId && state.voiceDefinitions[0]) {
      state.settingsDraft.voiceId = state.voiceDefinitions[0].voiceId;
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
    return state.currentView === "home" ? "Phiên tạo video" : "Không gian làm việc tạo video tự động";
  }

  function buildSessionTitle(topic) {
    const normalized = String(topic || "").trim();
    if (!normalized) return "Phiên video mới";
    return normalized.length > 60 ? `${normalized.slice(0, 57)}...` : normalized;
  }

  function buildWorkspaceSubtitle(session) {
    if (!session) return "Phiên nháp";
    return `${formatSessionStatus(session.status)} · ${session.activeStep || "Chưa chạy"}`;
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

  function formatProgressCopy(session) {
    return `${Math.round(Number(session.progressPercent || 0))}% · ${session.activeStep || "Chưa chạy"}`;
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

  function rememberCurrentScroll() {
    const key = getScrollMemoryKey();
    const body = getWorkspaceBody();
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

  function inferOutputLength(topic, sceneCount, desiredDurationSeconds) {
    const count = Number(sceneCount || 0);
    const normalized = String(topic || "");
    const duration = Number(desiredDurationSeconds || 0);
    if (duration >= 1200) return 45000;
    if (duration >= 600) return 30000;
    if (duration >= 300) return 22000;
    if (duration >= 120) return 16000;
    if (duration >= 60) return 12000;
    if (count >= 10 || /\b10\b|\b12\b|10 câu|12 scene|12 cảnh/i.test(normalized)) return 12000;
    if (count >= 5) return 10000;
    return 6000;
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

