(() => {
  "use strict";

  const store = window.LqlqVideoWorkspaceV36Store;
  const rootId = "lqlqVideoWorkspaceV36Root";
  const flagKey = "lqlq-video-workspace-version";

  if (!store) return;

  const previousCenter = window.LqlqAutomationCenter;
  let root = null;
  const state = {
    open: false,
    view: "home",
    sessions: [],
    selectedSessionId: "",
    project: null,
    errorMessage: ""
  };

  function ensureRoot() {
    if (root && root.isConnected) return root;
    root = document.getElementById(rootId);
    if (root) return root;
    root = document.createElement("div");
    root.id = rootId;
    root.className = "v36-workspace-shell";
    document.body.appendChild(root);
    root.addEventListener("click", handleClick);
    return root;
  }

  function getSelectedVersion() {
    try {
      return localStorage.getItem(flagKey) === "v36" ? "v36" : "v35";
    } catch {
      return "v35";
    }
  }

  function setSelectedVersion(version) {
    try {
      localStorage.setItem(flagKey, version === "v36" ? "v36" : "v35");
    } catch {}
  }

  function openHome() {
    hideLegacyWorkspaceRoot();
    state.open = true;
    state.view = "home";
    state.errorMessage = "";
    state.project = null;
    state.sessions = store.loadSessions()
      .slice()
      .sort((a, b) => Number(b.updatedAt || 0) - Number(a.updatedAt || 0));
    render();
  }

  function openStoryboard(sessionId) {
    const session = state.sessions.find(item => item.sessionId === sessionId);
    if (!session) return;
    state.selectedSessionId = sessionId;
    state.view = "storyboard";
    state.project = store.buildProjectView(session);
    render();
  }

  function closeWorkspace() {
    state.open = false;
    render();
  }

  function backHome() {
    state.view = "home";
    state.project = null;
    state.errorMessage = "";
    state.sessions = store.loadSessions()
      .slice()
      .sort((a, b) => Number(b.updatedAt || 0) - Number(a.updatedAt || 0));
    render();
  }

  function handleClick(event) {
    const target = event.target instanceof HTMLElement ? event.target.closest("[data-v36-action]") : null;
    if (!target) return;
    const action = target.getAttribute("data-v36-action");
    switch (action) {
      case "close":
        closeWorkspace();
        break;
      case "back-home":
        backHome();
        break;
      case "open-session":
        openStoryboard(target.getAttribute("data-session-id") || "");
        break;
      case "coming-soon":
        state.errorMessage = "Chức năng này sẽ được mở ở PASS sau.";
        render();
        break;
    }
  }

  function render() {
    const container = ensureRoot();
    if (!state.open) {
      container.innerHTML = "";
      container.classList.remove("open");
      return;
    }
    container.classList.add("open");
    container.innerHTML = `
      <div class="v36-backdrop">
        <section class="v36-panel" role="dialog" aria-modal="true" aria-label="lqlq Video Studio V36">
          ${state.view === "home" ? renderHome() : renderStoryboard()}
        </section>
      </div>
    `;
  }

  function renderHome() {
    const sessions = state.sessions;
    return `
      <header class="v36-header">
        <div>
          <div class="v36-dev-badge">V36 DEV</div>
          <h2>lqlq</h2>
          <p>Đọc lại dữ liệu V35 và mở bảng phân cảnh ở chế độ chỉ xem.</p>
        </div>
        <button class="v36-icon-button" type="button" data-v36-action="close" aria-label="Đóng">×</button>
      </header>
      ${state.errorMessage ? `<div class="v36-banner">${escapeHtml(state.errorMessage)}</div>` : ""}
      <div class="v36-home-meta">
        <div class="v36-stat">
          <span>Dự án</span>
          <b>${sessions.length}</b>
        </div>
        <div class="v36-stat">
          <span>Flag</span>
          <b>${escapeHtml(getSelectedVersion())}</b>
        </div>
        <div class="v36-stat">
          <span>Nguồn dữ liệu</span>
          <b>Session V35</b>
        </div>
      </div>
      <div class="v36-session-list">
        ${sessions.length ? sessions.map(renderSessionCard).join("") : `
          <div class="v36-empty-card">
            <b>Chưa có dự án nào</b>
            <p>V36 hiện chỉ đọc danh sách dự án từ localStorage của V35.</p>
          </div>
        `}
      </div>
    `;
  }

  function renderSessionCard(session) {
    return `
      <article class="v36-session-card">
        <div class="v36-session-head">
          <div>
            <b>${escapeHtml(session.title || "Dự án video mới")}</b>
            <p>${escapeHtml(session.summary || "Chưa chạy")}</p>
          </div>
          <span class="v36-status-pill" data-state="${escapeHtml(session.status || "DRAFT")}">${escapeHtml(formatSessionStatus(session.status))}</span>
        </div>
        <div class="v36-progress">
          <span style="width:${clampPercent(session.progressPercent)}%"></span>
        </div>
        <div class="v36-session-foot">
          <span>${clampPercent(session.progressPercent)}%</span>
          <button class="v36-button v36-button-primary" type="button" data-v36-action="open-session" data-session-id="${escapeHtml(session.sessionId)}">Mở bảng phân cảnh</button>
        </div>
      </article>
    `;
  }

  function renderStoryboard() {
    const project = state.project;
    if (!project) {
      return `
        <header class="v36-header">
          <button class="v36-icon-button" type="button" data-v36-action="back-home" aria-label="Quay lại">‹</button>
          <div><h2>Bảng phân cảnh</h2></div>
          <button class="v36-icon-button" type="button" data-v36-action="close" aria-label="Đóng">×</button>
        </header>
        <div class="v36-empty-card"><b>Không tải được phiên.</b></div>
      `;
    }
    return `
      <header class="v36-header">
        <button class="v36-icon-button" type="button" data-v36-action="back-home" aria-label="Quay lại">‹</button>
        <div class="v36-header-copy">
          <div class="v36-dev-badge">V36 DEV</div>
          <h2>${escapeHtml(project.title)}</h2>
          <p>${escapeHtml(formatSessionStatus(project.status))} · ${escapeHtml(project.sceneCount)} cảnh · ${clampPercent(project.progressPercent)}%</p>
        </div>
        <button class="v36-icon-button" type="button" data-v36-action="close" aria-label="Đóng">×</button>
      </header>
      ${state.errorMessage ? `<div class="v36-banner">${escapeHtml(state.errorMessage)}</div>` : ""}
      <div class="v36-stage-row">
        ${project.stages.map(renderStageChip).join("")}
      </div>
      <section class="v36-project-card">
        <div class="v36-project-grid">
          <div><span>Chủ đề</span><b>${escapeHtml(project.topic || "Chưa có")}</b></div>
          <div><span>Cảnh</span><b>${escapeHtml(project.sceneCount)}</b></div>
          <div><span>Giọng đọc</span><b>${project.hasVoice ? "Đã có" : "Chưa có"}</b></div>
          <div><span>Video</span><b>${project.hasVideo ? "Đã có" : "Chưa có"}</b></div>
        </div>
      </section>
      <section class="v36-scene-list">
        ${project.scenes.length ? project.scenes.map(renderSceneCard).join("") : `
          <div class="v36-empty-card">
            <b>Chưa có cảnh</b>
            <p>PASS 00-01 chỉ hiển thị read-only. Tạo và sửa cảnh sẽ sang PASS sau.</p>
          </div>
        `}
      </section>
      <footer class="v36-footer-bar">
        <button class="v36-button v36-button-secondary" type="button" data-v36-action="coming-soon">Tạo nội dung</button>
        <button class="v36-button v36-button-secondary" type="button" data-v36-action="coming-soon">Hoàn thành phần còn thiếu</button>
      </footer>
    `;
  }

  function renderStageChip(stage) {
    return `
      <div class="v36-stage-chip" data-state="${escapeHtml(stage.status)}">
        <span>${escapeHtml(stage.label)}</span>
        <b>${escapeHtml(formatStageStatus(stage.status))}</b>
      </div>
    `;
  }

  function renderSceneCard(scene) {
    const image = scene.imageArtifact?.previewDataUrl
      ? `<img src="${escapeAttribute(scene.imageArtifact.previewDataUrl)}" alt="${escapeAttribute(scene.onScreenText || `Cảnh ${scene.ordinal}`)}">`
      : `<div class="v36-scene-placeholder">${scene.imageDisplayStatus}</div>`;
    return `
      <article class="v36-scene-card">
        <div class="v36-scene-media">${image}</div>
        <div class="v36-scene-copy">
          <div class="v36-scene-top">
            <span class="v36-scene-index">Cảnh ${escapeHtml(scene.ordinal)}</span>
            <span class="v36-status-pill" data-state="${escapeHtml(scene.imageDisplayStatus)}">${escapeHtml(scene.imageDisplayStatus)}</span>
          </div>
          <b>${escapeHtml(scene.onScreenText || "Chưa có tiêu đề cảnh")}</b>
          <p>${escapeHtml(truncate(scene.voiceText || "Chưa có voiceText.", 180))}</p>
          <div class="v36-scene-meta">
            <span>Query: ${escapeHtml(scene.stockSearchQuery || "Chưa có")}</span>
            <span>${formatDuration(scene.plannedDurationMs)}</span>
          </div>
          <div class="v36-scene-actions">
            <button class="v36-button v36-button-disabled" type="button" disabled>Sửa cảnh ở PASS sau</button>
          </div>
        </div>
      </article>
    `;
  }

  function formatSessionStatus(status) {
    return ({
      DRAFT: "Bản nháp",
      RUNNING: "Đang chạy",
      QUEUED: "Đang chờ",
      PAUSED: "Tạm dừng",
      COMPLETED: "Hoàn tất",
      FAILED: "Lỗi",
      WAITING_USER: "Chờ người dùng"
    })[String(status || "").toUpperCase()] || String(status || "Bản nháp");
  }

  function formatStageStatus(status) {
    return ({
      READY: "Sẵn sàng",
      PARTIAL: "Thiếu một phần",
      MISSING: "Chưa có"
    })[String(status || "").toUpperCase()] || String(status || "");
  }

  function formatDuration(durationMs) {
    const totalSeconds = Math.max(0, Math.round(Number(durationMs || 0) / 1000));
    if (!totalSeconds) return "0s";
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }

  function clampPercent(value) {
    return Math.max(0, Math.min(100, Number(value || 0)));
  }

  function truncate(value, maxLength) {
    const text = String(value || "").trim();
    if (text.length <= maxLength) return text;
    return `${text.slice(0, maxLength - 1)}…`;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeAttribute(value) {
    return escapeHtml(value);
  }

  function hideLegacyWorkspaceRoot() {
    const legacyRoot = document.getElementById("videoAutomationWorkspaceRoot");
    if (!legacyRoot) return;
    legacyRoot.classList.add("hidden");
    legacyRoot.setAttribute("aria-hidden", "true");
  }

  window.LqlqVideoWorkspaceVersion = {
    get() {
      return getSelectedVersion();
    },
    set(version) {
      const resolved = version === "v36" ? "v36" : "v35";
      setSelectedVersion(resolved);
      return resolved;
    },
    useV35() {
      return this.set("v35");
    },
    useV36() {
      return this.set("v36");
    }
  };

  window.LqlqAutomationCenter = {
    openFromTools() {
      if (getSelectedVersion() === "v36") {
        openHome();
        return;
      }
      closeWorkspace();
      previousCenter?.openFromTools?.();
    }
  };
})();
