(() => {
  "use strict";

  const bridge = window.LqlqAutomation;
  const $ = id => document.getElementById(id);

  const refs = {
    overlay: $("automationCenterOverlay"),
    back: $("automationCenterBack"),
    close: $("automationCenterClose"),
    topic: $("automationTopicInput"),
    topicMeta: $("automationTopicMeta"),
    contentService: $("automationContentService"),
    voiceService: $("automationVoiceService"),
    videoService: $("automationVideoService"),
    publishService: $("automationPublishService"),
    start: $("automationStartBtn"),
    reset: $("automationResetBtn"),
    error: $("automationErrorBox"),
    runtimeCopy: $("automationRuntimeCopy"),
    stepList: $("automationStepList"),
    resultCard: $("automationResultCard"),
    resultSummary: $("automationResultSummary"),
    recentJobs: $("automationRecentJobs")
  };

  if (!refs.overlay || !refs.start) return;

  const STEP_ORDER = ["CONTENT", "VOICE", "VIDEO", "PUBLISH_DRAFT"];
  const STEP_LABELS = {
    CONTENT: "Soạn nội dung",
    VOICE: "Dựng giọng đọc",
    VIDEO: "Ghép video",
    PUBLISH_DRAFT: "Chuẩn bị bản nháp đăng"
  };

  let simulationToken = 0;
  let currentJob = null;

  function parseResponse(raw) {
    try {
      return JSON.parse(String(raw || ""));
    } catch {
      return { ok: false, message: "Phản hồi Automation Center không hợp lệ." };
    }
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function formatDate(epochMs) {
    if (!epochMs) return "Chưa có thời gian";
    try {
      return new Date(epochMs).toLocaleString("vi-VN");
    } catch {
      return String(epochMs);
    }
  }

  function setError(message = "") {
    refs.error.textContent = message;
    refs.error.classList.toggle("hidden", !message);
  }

  function setTopicMeta() {
    const length = refs.topic.value.trim().length;
    refs.topicMeta.textContent = `${length}/50000 ký tự. Mô tả càng chi tiết càng tốt. Nội dung sẽ được xem là text thuần.`;
  }

  function setControlsDisabled(disabled) {
    refs.start.disabled = disabled;
    refs.reset.disabled = disabled;
    refs.topic.disabled = disabled;
    refs.contentService.disabled = disabled;
    refs.voiceService.disabled = disabled;
    refs.videoService.disabled = disabled;
    refs.publishService.disabled = disabled;
  }

  function createStepModel(job, stateMap = {}) {
    const stepsByType = new Map((job?.steps || []).map(step => [step.stepType, step]));
    return STEP_ORDER.map(stepType => {
      const step = stepsByType.get(stepType) || {};
      return {
        stepType,
        title: STEP_LABELS[stepType] || stepType,
        detail: step.stepId || "Chưa có step ID",
        state: stateMap[stepType] || "waiting"
      };
    });
  }

  function renderSteps(job, stateMap = {}) {
    const items = createStepModel(job, stateMap);
    refs.stepList.innerHTML = items.map((item, index) => `
      <article class="automation-step-card ${item.state}">
        <span class="automation-step-index">0${index + 1}</span>
        <div class="automation-step-copy">
          <b>${escapeHtml(item.title)}</b>
          <small>${escapeHtml(item.detail)}</small>
        </div>
        <span class="automation-step-state">${escapeHtml(item.state)}</span>
      </article>
    `).join("");
  }

  function renderResult(job, message) {
    if (!job) {
      refs.resultCard.classList.add("hidden");
      refs.resultSummary.innerHTML = "";
      return;
    }

    refs.resultCard.classList.remove("hidden");
    refs.resultSummary.innerHTML = `
      <div class="automation-result-grid">
        <div><span>Job ID</span><b>${escapeHtml(job.jobId)}</b></div>
        <div><span>Trạng thái DB</span><b>${escapeHtml(job.status)}</b></div>
        <div><span>Thời gian tạo</span><b>${escapeHtml(formatDate(job.createdAtEpochMs))}</b></div>
        <div><span>Chế độ duyệt</span><b>${escapeHtml(job.publishMode || "review-before-post")}</b></div>
      </div>
      <p>${escapeHtml(message)}</p>
      <small>Job đã lưu vào database cục bộ. Chưa gọi AI thật, chưa tạo video thật, chưa upload thật.</small>
    `;
  }

  function renderRecentJobs(jobs) {
    if (!Array.isArray(jobs) || !jobs.length) {
      refs.recentJobs.innerHTML = `
        <div class="automation-empty-state">
          <b>Chưa có job gần đây</b>
          <small>Khi bạn tạo job mock, danh sách này sẽ hiện lại từ database cục bộ.</small>
        </div>
      `;
      return;
    }

    refs.recentJobs.innerHTML = jobs.map(job => `
      <button type="button" class="automation-recent-job" data-job-id="${escapeHtml(job.jobId)}">
        <div>
          <b>${escapeHtml(job.topic || "Job không có chủ đề")}</b>
          <small>${escapeHtml(job.jobId)}</small>
        </div>
        <span>${escapeHtml(job.status)}</span>
      </button>
    `).join("");
  }

  function loadRecentJobs() {
    if (!bridge?.listRecentAutomationJobs) {
      renderRecentJobs([]);
      return;
    }
    const response = parseResponse(bridge.listRecentAutomationJobs("automation-ui-mvp"));
    if (!response.ok) {
      renderRecentJobs([]);
      return;
    }
    renderRecentJobs(response.jobs || []);
  }

  function openFromTools() {
    refs.overlay.classList.remove("hidden");
    refs.overlay.setAttribute("aria-hidden", "false");
    setError("");
    setTopicMeta();
    renderSteps(currentJob);
    loadRecentJobs();
  }

  function closeOverlay(returnToTools) {
    refs.overlay.classList.add("hidden");
    refs.overlay.setAttribute("aria-hidden", "true");
    if (returnToTools) {
      openToolsMenu();
    }
  }

  async function runSimulation(job) {
    simulationToken += 1;
    const token = simulationToken;
    const stateMap = {};
    renderSteps(job, stateMap);

    for (const stepType of STEP_ORDER) {
      if (token !== simulationToken) return;
      stateMap[stepType] = "processing";
      refs.runtimeCopy.textContent = `Đang mô phỏng bước ${STEP_LABELS[stepType]}...`;
      renderSteps(job, stateMap);
      await new Promise(resolve => setTimeout(resolve, 1000));
      if (token !== simulationToken) return;
      stateMap[stepType] = "done";
      renderSteps(job, stateMap);
    }

    refs.runtimeCopy.textContent = "Mô phỏng hoàn tất. Job đã lưu vào database cục bộ; chưa tạo video thật hoặc tải lên thật.";
    renderResult(
      job,
      "Mô phỏng hoàn tất. Bạn có thể dùng Job ID này để tiếp tục các pass automation sau."
    );
    loadRecentJobs();
  }

  async function startAutomation() {
    setError("");
    refs.runtimeCopy.textContent = "Đang tạo job mock trong database cục bộ...";
    renderResult(null);
    currentJob = null;

    const payload = {
      topic: refs.topic.value,
      contentServiceId: refs.contentService.value,
      voiceServiceId: refs.voiceService.value,
      videoServiceId: refs.videoService.value,
      publishServiceId: refs.publishService.value,
      publishMode: document.querySelector("input[name='automationPublishMode']:checked")?.value || "review-before-post"
    };

    if (!bridge?.startMockAutomation) {
      setError("Automation Bridge chưa sẵn sàng trong shell Android này.");
      refs.runtimeCopy.textContent = "Không thể tạo job vì bridge chưa sẵn sàng.";
      return;
    }

    setControlsDisabled(true);
    try {
      const response = parseResponse(
        bridge.startMockAutomation(JSON.stringify(payload))
      );
      if (!response.ok) {
        setError(response.message || "Không thể tạo pipeline mock.");
        refs.runtimeCopy.textContent = "Tạo job thất bại. Hãy kiểm tra lại dữ liệu đầu vào.";
        return;
      }

      currentJob = response.job || null;
      refs.runtimeCopy.textContent = response.message || "Đã tạo job mock. Đang bắt đầu mô phỏng pipeline...";
      renderSteps(currentJob);
      await runSimulation(currentJob);
    } finally {
      setControlsDisabled(false);
    }
  }

  function resetWorkflow() {
    simulationToken += 1;
    currentJob = null;
    setError("");
    refs.topic.value = "";
    refs.contentService.value = "mock-content";
    refs.voiceService.value = "mock-voice";
    refs.videoService.value = "mock-video";
    refs.publishService.value = "mock-publish-draft";
    const publishMode = document.querySelector("input[name='automationPublishMode'][value='review-before-post']");
    if (publishMode) publishMode.checked = true;
    refs.runtimeCopy.textContent = "Sẵn sàng tạo job mock mới. Mỗi bước sẽ mô phỏng khoảng 1 giây.";
    renderSteps(null);
    renderResult(null);
    setTopicMeta();
    loadRecentJobs();
  }

  function openRecentJob(jobId) {
    if (!bridge?.getAutomationJob || !jobId) return;
    const response = parseResponse(bridge.getAutomationJob(jobId));
    if (!response.ok || !response.job) {
      setError(response.message || "Không đọc được job gần đây.");
      return;
    }
    currentJob = response.job;
    refs.runtimeCopy.textContent = "Đã nạp job từ database cục bộ. Đây vẫn là pipeline mock, chưa thực thi thật.";
    renderSteps(currentJob);
    renderResult(
      currentJob,
      "Job này được nạp lại từ database cục bộ để bạn kiểm tra cấu trúc pipeline."
    );
    setError("");
  }

  refs.start.addEventListener("click", startAutomation);
  refs.reset.addEventListener("click", resetWorkflow);
  refs.topic.addEventListener("input", setTopicMeta);
  refs.back.addEventListener("click", () => closeOverlay(true));
  refs.close.addEventListener("click", () => closeOverlay(false));
  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) closeOverlay(false);
  });
  refs.recentJobs.addEventListener("click", event => {
    const button = event.target.closest("[data-job-id]");
    if (!button) return;
    openRecentJob(button.dataset.jobId);
  });

  setTopicMeta();
  renderSteps(null);
  loadRecentJobs();

  window.LqlqAutomationCenter = {
    openFromTools
  };
})();
