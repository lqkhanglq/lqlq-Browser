(() => {
  "use strict";

  const bridge = window.LqlqAutomation;
  const SESSION_STORE_KEY = "lqlq-video-automation-sessions-v35";

  function safeJsonParse(raw, fallback) {
    try {
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  }

  function loadSessions() {
    const parsed = safeJsonParse(localStorage.getItem(SESSION_STORE_KEY), []);
    if (!Array.isArray(parsed)) return [];
    return parsed.map(session => ({
      ...session,
      sessionId: String(session?.sessionId || ""),
      linkedJobId: String(session?.linkedJobId || ""),
      title: String(session?.title || "Phiên video mới"),
      topic: String(session?.topic || ""),
      status: String(session?.status || "DRAFT"),
      summary: String(session?.summary || ""),
      progressPercent: Number(session?.progressPercent || 0),
      updatedAt: Number(session?.updatedAt || 0),
      activeStep: String(session?.activeStep || ""),
      reviewStatus: String(session?.reviewStatus || "PENDING")
    }));
  }

  function parseResponse(raw) {
    const parsed = safeJsonParse(raw, null);
    if (parsed && typeof parsed === "object") return parsed;
    return { ok: false, message: "Phản hồi không hợp lệ." };
  }

  function getAutomationJob(jobId) {
    if (!jobId || !bridge?.getAutomationJob) return null;
    const response = parseResponse(bridge.getAutomationJob(jobId));
    return response?.ok && response.job ? response.job : null;
  }

  function buildImageLookup(job) {
    const bySceneId = new Map();
    const byOrdinal = new Map();
    const artifacts = Array.isArray(job?.artifacts) ? job.artifacts : [];
    artifacts.forEach((artifact) => {
      if (artifact?.artifactType !== "IMAGE") return;
      const sceneId = String(artifact.sceneId || "").trim();
      const ordinal = Number(artifact.ordinal || 0);
      if (sceneId && !bySceneId.has(sceneId)) bySceneId.set(sceneId, artifact);
      if (ordinal > 0 && !byOrdinal.has(ordinal)) byOrdinal.set(ordinal, artifact);
    });
    return { bySceneId, byOrdinal };
  }

  function getImageArtifact(scene, lookup) {
    const sceneId = String(scene?.sceneId || "").trim();
    const ordinal = Number(scene?.ordinal || 0);
    if (sceneId && lookup.bySceneId.has(sceneId)) return lookup.bySceneId.get(sceneId);
    if (ordinal > 0 && lookup.byOrdinal.has(ordinal)) return lookup.byOrdinal.get(ordinal);
    return null;
  }

  function normalizeScene(scene, lookup) {
    const imageArtifact = getImageArtifact(scene, lookup);
    return {
      sceneId: String(scene?.sceneId || ""),
      ordinal: Number(scene?.ordinal || 0),
      onScreenText: String(scene?.onScreenText || ""),
      voiceText: String(scene?.voiceText || ""),
      stockSearchQuery: String(scene?.stockSearchQuery || ""),
      visualPrompt: String(scene?.visualPrompt || ""),
      plannedDurationMs: Number(scene?.plannedDurationMs || 0),
      imageArtifact,
      imageDisplayStatus: imageArtifact ? "READY" : "MISSING"
    };
  }

  function deriveStageStatus(project) {
    const scenes = project.scenes;
    const sceneCount = scenes.length;
    const readyImages = scenes.filter(scene => scene.imageDisplayStatus === "READY").length;
    const hasScenes = sceneCount > 0;
    const hasVoice = Array.isArray(project.job?.artifacts) && project.job.artifacts.some(artifact => artifact?.artifactType === "VOICE");
    const hasVideo = Array.isArray(project.job?.artifacts) && project.job.artifacts.some(artifact => artifact?.artifactType === "VIDEO_MP4");
    return [
      { key: "content", label: "Nội dung", status: hasScenes ? "READY" : "MISSING" },
      { key: "image", label: "Hình ảnh", status: hasScenes && readyImages === sceneCount ? "READY" : (hasScenes ? "PARTIAL" : "MISSING") },
      { key: "voice", label: "Giọng", status: hasVoice ? "READY" : "MISSING" },
      { key: "video", label: "Video", status: hasVideo ? "READY" : "MISSING" }
    ];
  }

  function buildProjectView(session) {
    const job = session?.linkedJobId ? getAutomationJob(session.linkedJobId) : null;
    const lookup = buildImageLookup(job);
    const scenes = (Array.isArray(job?.scenePrompts) ? job.scenePrompts : [])
      .slice()
      .sort((a, b) => Number(a?.ordinal || 0) - Number(b?.ordinal || 0))
      .map(scene => normalizeScene(scene, lookup));
    const progressPercent = job ? calculateProgress(job) : Number(session?.progressPercent || 0);
    const project = {
      sessionId: session?.sessionId || "",
      linkedJobId: session?.linkedJobId || "",
      title: String(session?.title || "Phiên video mới"),
      status: String(session?.status || "DRAFT"),
      topic: String(session?.topic || job?.topic || ""),
      sceneCount: scenes.length,
      scenes,
      hasVoice: Array.isArray(job?.artifacts) && job.artifacts.some(artifact => artifact?.artifactType === "VOICE"),
      hasVideo: Array.isArray(job?.artifacts) && job.artifacts.some(artifact => artifact?.artifactType === "VIDEO_MP4"),
      progressPercent,
      job
    };
    project.stages = deriveStageStatus(project);
    return project;
  }

  function calculateProgress(job) {
    const steps = Array.isArray(job?.steps) ? job.steps : [];
    if (!steps.length) return 0;
    const completed = steps.filter(step => step?.status === "COMPLETED").length;
    return Math.round((completed / steps.length) * 100);
  }

  window.LqlqVideoWorkspaceV36Store = {
    SESSION_STORE_KEY,
    loadSessions,
    buildProjectView
  };
})();
