(() => {
  "use strict";

  const bridge = window.LqlqAutomation;
  const $ = id => document.getElementById(id);

  const refs = {
    overlay: $("automationCenterOverlay"),
    back: $("automationCenterBack"),
    close: $("automationCenterClose"),
    geminiApiKey: $("automationGeminiApiKey"),
    geminiModel: $("automationGeminiModel"),
    saveGemini: $("automationSaveGeminiBtn"),
    testGemini: $("automationTestGeminiBtn"),
    geminiConnectionStatus: $("automationConnectionStatus"),
    geminiConfigurationStatus: $("automationConfigurationStatus"),
    imageProvider: $("automationImageProvider"),
    imageProviderMeta: $("automationImageProviderMeta"),
    imageApiKey: $("automationImageApiKey"),
    imageAccountIdField: $("automationImageAccountIdField"),
    imageAccountId: $("automationImageAccountId"),
    imageModelPreset: $("automationImageModelPreset"),
    imageModelCustom: $("automationImageModelCustom"),
    imageProviderCost: $("automationImageProviderCost"),
    imageCredentialHint: $("automationImageCredentialHint"),
    imageConnectionStatus: $("automationImageConnectionStatus"),
    imageConfigurationStatus: $("automationImageConfigurationStatus"),
    imageCapabilityBox: $("automationImageCapabilityBox"),
    imageCapabilityMeta: $("automationImageCapabilityMeta"),
    saveImage: $("automationSaveImageBtn"),
    testImage: $("automationTestImageBtn"),
    voiceProvider: $("automationVoiceProvider"),
    voiceProviderMeta: $("automationVoiceProviderMeta"),
    voiceCredentialLabel: $("automationVoiceCredentialLabel"),
    voiceApiKey: $("automationVoiceApiKey"),
    voiceCredentialHint: $("automationVoiceCredentialHint"),
    voiceModel: $("automationVoiceModel"),
    voiceModelHint: $("automationVoiceModelHint"),
    voiceRegionField: $("automationVoiceRegionField"),
    voiceRegion: $("automationVoiceRegion"),
    voiceSelect: $("automationVoiceSelect"),
    voiceVoiceMeta: $("automationVoiceVoiceMeta"),
    voiceLocale: $("automationVoiceLocale"),
    voiceOutputFormat: $("automationVoiceOutputFormat"),
    voiceRate: $("automationVoiceRate"),
    voiceRateValue: $("automationVoiceRateValue"),
    voicePitch: $("automationVoicePitch"),
    voicePitchValue: $("automationVoicePitchValue"),
    voiceConnectionStatus: $("automationVoiceConnectionStatus"),
    voiceConfigurationStatus: $("automationVoiceConfigurationStatus"),
    voiceCapabilityBox: $("automationVoiceCapabilityBox"),
    voiceCapabilityMeta: $("automationVoiceCapabilityMeta"),
    saveVoice: $("automationSaveVoiceBtn"),
    testVoice: $("automationTestVoiceBtn"),
    sampleVoice: $("automationSampleVoiceBtn"),
    openVoiceSettings: $("automationOpenVoiceSettingsBtn"),
    videoRendererMode: $("automationVideoRendererMode"),
    videoWorkerUrl: $("automationVideoWorkerUrl"),
    testVideoWorker: $("automationTestVideoWorkerBtn"),
    videoWorkerStatus: $("automationVideoWorkerStatus"),
    videoWorkerHint: $("automationVideoWorkerHint"),
    topic: $("automationTopicInput"),
    topicMeta: $("automationTopicMeta"),
    start: $("automationStartBtn"),
    reset: $("automationResetBtn"),
    retryImage: $("automationRetryImageBtn"),
    retryImageAction: $("automationRetryImageAction"),
    retryVoice: $("automationRetryVoiceBtn"),
    retryVoiceAction: $("automationRetryVoiceAction"),
    retryVideo: $("automationRetryVideoBtn"),
    retryVideoAction: $("automationRetryVideoAction"),
    exportVideo: $("automationExportVideoBtn"),
    exportVideoAction: $("automationExportVideoAction"),
    retryMetadata: $("automationRetryMetadataBtn"),
    retryMetadataAction: $("automationRetryMetadataAction"),
    approveReview: $("automationApproveReviewBtn"),
    approveReviewAction: $("automationApproveReviewAction"),
    rejectReview: $("automationRejectReviewBtn"),
    rejectReviewAction: $("automationRejectReviewAction"),
    sharePublish: $("automationSharePublishBtn"),
    sharePublishAction: $("automationSharePublishAction"),
    markPublished: $("automationMarkPublishedBtn"),
    markPublishedAction: $("automationMarkPublishedAction"),
    error: $("automationErrorBox"),
    notice: $("automationNotice"),
    runtimeCopy: $("automationRuntimeCopy"),
    stepList: $("automationStepList"),
    resultCard: $("automationResultCard"),
    resultSummary: $("automationResultSummary")
  };

  if (!refs.overlay || !refs.start) return;

  const STEP_ORDER = [
    { key: "TOPIC", label: "Chu de" },
    { key: "CONTENT", label: "Soan noi dung" },
    { key: "SCENE_PROMPTS", label: "Chia scene" },
    { key: "ASSET_PLAN", label: "Chon asset" },
    { key: "IMAGES_VISUALS", label: "Lay/tao visual" },
    { key: "VOICE", label: "Giong doc" },
    { key: "SUBTITLE", label: "Subtitle" },
    { key: "VIDEO", label: "Dung template" },
    { key: "METADATA", label: "Metadata" },
    { key: "REVIEW", label: "Kiem duyet" },
    { key: "PUBLISH", label: "Dang" }
  ];

  const DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
  const CUSTOM_MODEL_VALUE = "__custom__";
  const READER_TTS_SETTINGS_KEY = "shieldStoryReaderSettingsV1";
  const VIDEO_RENDERER_MODE_NATIVE = "android_native_render";
  const VIDEO_RENDERER_MODE_LOCAL = "local_plan_only";
  const VIDEO_RENDERER_MODE_EXTERNAL = "external_moviepy_worker";
  const IMAGE_PROVIDER_META = {
    "openai-images": {
      accountLabel: "",
      credentialLabel: "OpenAI API key",
      credentialHint: "OpenAI Images su dung API tra phi rieng. ChatGPT Plus khong bao gom credit API. Co the can billing va xac minh tai khoan/to chuc.",
      modelLabels: {
        "gpt-image-2": "GPT Image 2 - mac dinh",
        "gpt-image-1-mini": "GPT Image 1 Mini - tiet kiem",
        "gpt-image-1": "GPT Image 1 - cu/deprecated"
      }
    },
    "cloudflare-workers-ai": {
      accountLabel: "Cloudflare Account ID",
      credentialLabel: "Cloudflare API token",
      credentialHint: "Cloudflare Workers AI dung Account ID + API token. Credential duoc luu native theo provider rieng.",
      modelLabels: {}
    },
    "pexels": {
      accountLabel: "",
      credentialLabel: "Pexels API key",
      credentialHint: "Pexels la stock media mien phi/co gioi han, phu hop lam background cho shorts. Model mac dinh chi la search stock photo, khong tao anh AI.",
      modelLabels: {
        "stock-photo-search-v1": "Stock photo search v1"
      }
    }
  };
  const SAFE_OPENAI_MODEL = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;
  const SAFE_CLOUDFLARE_MODEL = /^@[A-Za-z0-9._/-]{3,127}$/;
  const SAFE_VOICE_MODEL = /^[A-Za-z0-9][A-Za-z0-9._@/-]{0,127}$/;
  const VOICE_PROVIDER_META = {
    "vbee-tts": {
      credentialLabel: "VBEE API key",
      credentialHint: "VBEE la provider giong Viet uu tien cho Automation. Credential duoc luu tach rieng theo provider trong native layer.",
      modelHint: "Co the de trong de dung voice mac dinh, hoac nhap voice code/profile VBEE tuy chinh neu tai khoan cua ban can."
    },
    "fpt-ai-tts": {
      credentialLabel: "FPT.AI API key",
      credentialHint: "FPT.AI la provider giong Viet theo API key. Khong dung chung credential voi provider khac.",
      modelHint: "Thuong khong can model rieng."
    },
    "viettel-ai-tts": {
      credentialLabel: "Viettel AI API key",
      credentialHint: "Viettel AI la provider giong Viet theo API key. Registry se tach connector va credential theo provider.",
      modelHint: "Thuong khong can model rieng."
    },
    "zalo-ai-tts": {
      credentialLabel: "Zalo AI API key",
      credentialHint: "Zalo AI la provider giong Viet theo API key. Registry se tach connector va credential theo provider.",
      modelHint: "Thuong khong can model rieng."
    },
    "elevenlabs": {
      credentialLabel: "ElevenLabs API key",
      credentialHint: "ElevenLabs la provider cloud. Chi cau hinh neu ban muon voice quoc te thay cho provider Viet.",
      modelHint: "Co the can model/profile tuy goi."
    },
    "google-cloud-tts": {
      credentialLabel: "Google Cloud credential",
      credentialHint: "Google Cloud TTS thuong can service-account native, khong phai luong API key don gian trong shell nay.",
      modelHint: "Google Cloud khong uu tien model text input o pass nay."
    },
    "android-system-tts": {
      credentialLabel: "Khong can credential",
      credentialHint: "Dung Google TTS tren dien thoai va uu tien lay cau hinh da luu tu Reader/TXT cua app.",
      modelHint: "Provider local khong can model."
    }
  };

  let lastGeminiStatus = { state: "NOT_CONFIGURED", model: "" };
  let imageProviders = [];
  let selectedImageProviderId = "";
  let voiceProviders = [];
  let voiceDefinitions = [];
  let selectedVoiceProviderId = "";
  let lastJob = null;
  let isApplyingReaderVoiceConfig = false;

  function getVideoRendererMode() {
    const value = String(refs.videoRendererMode?.value || "").trim();
    if (value === VIDEO_RENDERER_MODE_EXTERNAL) return VIDEO_RENDERER_MODE_EXTERNAL;
    if (value === VIDEO_RENDERER_MODE_LOCAL) return VIDEO_RENDERER_MODE_LOCAL;
    return VIDEO_RENDERER_MODE_NATIVE;
  }

  function getVideoWorkerUrl() {
    return String(refs.videoWorkerUrl?.value || "").trim();
  }

  function renderVideoWorkerStatus(message, hint = "") {
    if (refs.videoWorkerStatus) {
      refs.videoWorkerStatus.textContent = message || "Android native renderer se duoc uu tien.";
    }
    if (refs.videoWorkerHint) {
      refs.videoWorkerHint.textContent = hint || "Native mode se co gang xuat MP4 ngay tren thiet bi. Worker cu chi con la lua chon deprecated/advanced.";
    }
  }

  function parseResponse(raw) {
    try {
      return JSON.parse(String(raw || ""));
    } catch {
      return {
        ok: false,
        errorCode: "PROVIDER_FAILURE",
        message: "Phan hoi Automation khong hop le."
      };
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

  function multilineHtml(value) {
    return escapeHtml(value).replace(/\r?\n/g, "<br>");
  }

  function extractDebugMetric(sourceUrl, key) {
    const text = String(sourceUrl || "");
    const prefix = `${key}=`;
    const part = text.split(";").find(item => item.startsWith(prefix));
    return part ? part.slice(prefix.length) : "";
  }

  function parseTopicListicleCount(topic) {
    const normalized = String(topic || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
    const match = normalized.match(/(\d{1,2})\s*(cau noi|cau|cach|bai hoc|thoi quen|meo|ly do|buoc|dieu|tips?)/);
    return match ? Number(match[1]) : 0;
  }

  function parseDeclaredSceneCount(text) {
    const normalized = String(text || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
    const match = normalized.match(/(\d{1,2})\s*(scene|canh)/);
    return match ? Number(match[1]) : 0;
  }

  function setError(message = "") {
    refs.error.textContent = message;
    refs.error.classList.toggle("hidden", !message);
  }

  function setTopicMeta() {
    const length = refs.topic.value.trim().length;
    refs.topicMeta.textContent = `${length}/50000 ky tu. Mo ta cang chi tiet cang tot. Noi dung se duoc xem la text thuan.`;
    updateStartState();
  }

  function getSelectedProvider() {
    const providerId = refs.imageProvider?.value || selectedImageProviderId;
    return imageProviders.find(provider => provider.providerId === providerId) || null;
  }

  function getSelectedProviderStatus() {
    return getSelectedProvider()?.configurationStatus || {
      state: "NOT_CONFIGURED",
      providerId: selectedImageProviderId || "",
      model: ""
    };
  }

  function isProviderImplemented(provider) {
    return provider && provider.health !== "NOT_IMPLEMENTED";
  }

  function voiceProviderUsesLocalSettings(provider) {
    return provider?.authType === "NONE";
  }

  function voiceProviderNeedsRegion(provider) {
    return provider?.authType === "API_KEY_AND_REGION";
  }

  function voiceProviderNeedsCredential(provider) {
    return provider?.requiresCredentials === true;
  }

  function readReaderTtsSettings() {
    try {
      const raw = window.localStorage?.getItem(READER_TTS_SETTINGS_KEY) || "";
      return raw ? JSON.parse(raw) : {};
    } catch {
      return {};
    }
  }

  function normalizeReaderVoiceScalar(value, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number) || number <= 0) return fallback;
    if (number > 10) return Math.min(Math.max(number / 100, 0.5), 2.0);
    return Math.min(Math.max(number, 0.5), 2.0);
  }

  function scalarToSliderValue(value, fallback = 1.0) {
    const normalized = normalizeReaderVoiceScalar(value, fallback);
    return Math.round(normalized * 100);
  }

  function sliderValueToScalar(value, fallback = 1.0) {
    const number = Number(value);
    if (!Number.isFinite(number) || number <= 0) return fallback;
    return Math.min(Math.max(number / 100, 0.5), 2.0);
  }

  function updateVoiceSliderLabels() {
    if (refs.voiceRateValue) {
      refs.voiceRateValue.textContent = `${Math.round(Number(refs.voiceRate?.value || 100))}`;
    }
    if (refs.voicePitchValue) {
      refs.voicePitchValue.textContent = `${Math.round(Number(refs.voicePitch?.value || 100))}`;
    }
  }

  function getSelectedVoiceProvider() {
    const providerId = refs.voiceProvider?.value || selectedVoiceProviderId;
    return voiceProviders.find(provider => provider.providerId === providerId) || null;
  }

  function getSelectedVoiceProviderStatus() {
    return getSelectedVoiceProvider()?.configurationStatus || {
      state: "NOT_CONFIGURED",
      providerId: selectedVoiceProviderId || "",
      model: "",
      voiceId: "",
      locale: "vi-VN"
    };
  }

  function requiresAccountId(provider) {
    return provider?.authType === "ACCOUNT_ID_AND_API_TOKEN";
  }

  function getSelectedImageModel() {
    if (!refs.imageModelPreset) return "";
    const preset = refs.imageModelPreset.value || "";
    if (preset === CUSTOM_MODEL_VALUE) {
      return refs.imageModelCustom?.value.trim() || "";
    }
    return preset;
  }

  function isSafeModelForProvider(provider, model) {
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

  function renderImageProviderOptions() {
    if (!refs.imageProvider) return;

    refs.imageProvider.innerHTML = imageProviders.map(provider => {
      const selected = provider.selected || provider.providerId === selectedImageProviderId;
      const suffix = provider.health === "NOT_IMPLEMENTED" ? " (chua port)" : "";
      return `<option value="${escapeHtml(provider.providerId)}"${selected ? " selected" : ""}>${escapeHtml(provider.displayName + suffix)}</option>`;
    }).join("");

    if (!refs.imageProvider.value && imageProviders.length) {
      refs.imageProvider.value = imageProviders[0].providerId;
    }
    selectedImageProviderId = refs.imageProvider.value || selectedImageProviderId;
  }

  function syncImageModelControls(currentModel = "") {
    const provider = getSelectedProvider();
    if (!provider || !refs.imageModelPreset || !refs.imageModelCustom) return;

    const providerMeta = IMAGE_PROVIDER_META[provider.providerId] || { modelLabels: {} };
    const supportedModels = Array.isArray(provider.supportedModels) ? provider.supportedModels : [];
    const effectiveModel = String(currentModel || provider.configurationStatus?.model || provider.defaultModel || "").trim();

    refs.imageModelPreset.innerHTML = supportedModels.map(model => {
      const label = providerMeta.modelLabels?.[model] || model;
      return `<option value="${escapeHtml(model)}">${escapeHtml(label)}</option>`;
    }).join("") + `<option value="${CUSTOM_MODEL_VALUE}">Nhap model tuy chinh</option>`;

    const useCustom = effectiveModel && !supportedModels.includes(effectiveModel);
    refs.imageModelPreset.value = useCustom ? CUSTOM_MODEL_VALUE : (effectiveModel || provider.defaultModel || supportedModels[0] || CUSTOM_MODEL_VALUE);
    refs.imageModelCustom.classList.toggle("hidden", !useCustom);
    refs.imageModelCustom.value = useCustom ? effectiveModel : "";
  }

  function renderImageProviderDetails() {
    const provider = getSelectedProvider();
    if (!provider) return;

    const status = provider.configurationStatus || { state: "NOT_CONFIGURED" };
    const providerMeta = IMAGE_PROVIDER_META[provider.providerId] || {};
    selectedImageProviderId = provider.providerId;

    refs.imageProviderMeta.textContent = `${provider.displayName} · ${provider.costType} · ${provider.health}`;
    refs.imageProviderCost.textContent =
      `Auth: ${provider.authType}. Cost: ${provider.costType}. Output: ${(provider.supportedOutputFormats || []).join(", ") || "-"}. Max images/job: ${provider.maxImagesPerJob}.`;
    refs.imageCredentialHint.textContent = providerMeta.credentialHint ||
      "Credential chi di qua native bridge va duoc luu native theo tung provider rieng.";
    refs.imageAccountIdField.classList.toggle("hidden", !requiresAccountId(provider));
    refs.imageAccountId.value = status.accountId || "";
    refs.imageApiKey.placeholder = providerMeta.credentialLabel || "Nhap credential image provider";
    refs.imageCapabilityBox.textContent =
      `${provider.syncOrAsync.toUpperCase()} · polling ${provider.supportsPolling ? "co" : "khong"} · negative prompt ${provider.supportsNegativePrompt ? "co" : "khong"}`;
    refs.imageCapabilityMeta.textContent =
      `Health: ${provider.health}. Stability: ${provider.stabilityLevel}. Aspect ratio: ${provider.supportsAspectRatio ? "co" : "khong"}.`;

    syncImageModelControls(status.model || provider.defaultModel || "");
  }

  function renderVoiceProviderOptions() {
    if (!refs.voiceProvider) return;

    refs.voiceProvider.innerHTML = voiceProviders.map(provider => {
      const selected = provider.selected || provider.providerId === selectedVoiceProviderId;
      const suffix = provider.health === "NOT_IMPLEMENTED" ? " (chua port)" : "";
      return `<option value="${escapeHtml(provider.providerId)}"${selected ? " selected" : ""}>${escapeHtml(provider.displayName + suffix)}</option>`;
    }).join("");

    if (!refs.voiceProvider.value && voiceProviders.length) {
      refs.voiceProvider.value = voiceProviders[0].providerId;
    }
    selectedVoiceProviderId = refs.voiceProvider.value || selectedVoiceProviderId;
  }

  function renderVoiceDefinitions() {
    if (!refs.voiceSelect) return;
    const status = getSelectedVoiceProviderStatus();
    const currentVoiceId = status.voiceId || refs.voiceSelect.dataset.savedVoice || refs.voiceSelect.value || "";

    refs.voiceSelect.innerHTML = voiceDefinitions.map(voice => {
      const tags = [];
      if (voice.isDefault) tags.push("mac dinh");
      if (voice.networkRequired) tags.push("can mang");
      const tagLabel = tags.length ? ` · ${tags.join(" · ")}` : "";
      return `<option value="${escapeHtml(voice.voiceId)}">${escapeHtml(`${voice.displayName} (${voice.locale})${tagLabel}`)}</option>`;
    }).join("");

    if (!refs.voiceSelect.value && voiceDefinitions.length) {
      refs.voiceSelect.value = currentVoiceId && voiceDefinitions.some(voice => voice.voiceId === currentVoiceId)
        ? currentVoiceId
        : voiceDefinitions[0].voiceId;
    } else if (currentVoiceId && voiceDefinitions.some(voice => voice.voiceId === currentVoiceId)) {
      refs.voiceSelect.value = currentVoiceId;
    }

    const selectedVoice = voiceDefinitions.find(voice => voice.voiceId === refs.voiceSelect.value) || voiceDefinitions[0];
    refs.voiceVoiceMeta.textContent = selectedVoice
      ? `${selectedVoice.engineName} · ${selectedVoice.locale} · ${selectedVoice.networkRequired ? "can mang" : "offline duoc"}`
      : "Chua co voice catalog cho provider nay. Khi connector native san sang, danh sach giong se hien o day.";
  }

  function renderVoiceProviderDetails() {
    const provider = getSelectedVoiceProvider();
    if (!provider) return;

    const status = provider.configurationStatus || { state: "NOT_CONFIGURED", locale: "vi-VN" };
    const providerMeta = VOICE_PROVIDER_META[provider.providerId] || {};
    const readerSettings = readReaderTtsSettings();
    const readerVoiceId = typeof readerSettings.voice === "string" ? readerSettings.voice.trim() : "";
    const fallbackRate = scalarToSliderValue(readerSettings.rate, 1.0);
    const fallbackPitch = scalarToSliderValue(readerSettings.pitch, 1.0);
    selectedVoiceProviderId = provider.providerId;
    refs.voiceProviderMeta.textContent = `${provider.displayName} · ${provider.costType} · ${provider.health}`;
    refs.voiceCapabilityBox.textContent =
      `${provider.defaultLocale} · sample ${provider.supportsSamplePreview ? "co" : "khong"} · chunking ${provider.supportsChunking ? "co" : "khong"}`;
    refs.voiceCapabilityMeta.textContent =
      `Verified locale: ${(provider.verifiedLocales || []).join(", ") || "-"}. Output: ${(provider.supportedOutputFormats || []).join(", ") || "-"}.`;
    refs.voiceCredentialLabel.textContent = providerMeta.credentialLabel || "Voice credential";
    refs.voiceCredentialHint.textContent = providerMeta.credentialHint ||
      "Credential voice se duoc luu native theo provider dang chon.";
    refs.voiceModelHint.textContent = providerMeta.modelHint ||
      "De trong neu provider nay khong can model/profile rieng.";
    refs.voiceApiKey.parentElement?.classList.toggle("hidden", !voiceProviderNeedsCredential(provider));
    refs.voiceRegionField?.classList.toggle("hidden", !voiceProviderNeedsRegion(provider));
    refs.openVoiceSettings?.classList.toggle("hidden", !voiceProviderUsesLocalSettings(provider));
    refs.voiceApiKey.placeholder = providerMeta.credentialLabel || "Nhap credential voice provider";
    refs.voiceApiKey.value = "";
    refs.voiceRegion.value = "";
    refs.voiceModel.value = status.model || "";
    refs.voiceLocale.value = status.locale || provider.defaultLocale || "vi-VN";
    refs.voiceSelect.dataset.savedVoice = voiceProviderUsesLocalSettings(provider) ? readerVoiceId : "";
    refs.voiceOutputFormat.innerHTML = (provider.supportedOutputFormats || ["wav"]).map(format =>
      `<option value="${escapeHtml(format)}">${escapeHtml(format.toUpperCase())}</option>`
    ).join("");
    refs.voiceOutputFormat.value = provider.supportedOutputFormats?.[0] || "wav";
    refs.voiceRate.value = String(voiceProviderUsesLocalSettings(provider) ? fallbackRate : 100);
    refs.voicePitch.value = String(voiceProviderUsesLocalSettings(provider) ? fallbackPitch : 100);
    updateVoiceSliderLabels();
    renderVoiceDefinitions();
  }

  function buildVoiceConfigurationPayload(provider) {
    return {
      providerId: provider.providerId,
      locale: refs.voiceLocale.value.trim() || provider.defaultLocale || "vi-VN",
      voiceId: refs.voiceSelect.value || refs.voiceSelect.dataset.savedVoice || "",
      model: refs.voiceModel.value.trim(),
      apiKey: voiceProviderNeedsCredential(provider) ? refs.voiceApiKey.value : "",
      region: voiceProviderNeedsRegion(provider) ? refs.voiceRegion.value.trim() : "",
      engineName: voiceProviderUsesLocalSettings(provider) ? provider.displayName : "",
      speechRate: sliderValueToScalar(refs.voiceRate.value, 1.0),
      pitch: sliderValueToScalar(refs.voicePitch.value, 1.0),
      outputFormat: refs.voiceOutputFormat.value || "wav"
    };
  }

  function tryHydrateReaderVoiceConfiguration(status) {
    const provider = getSelectedVoiceProvider();
    if (!provider || !voiceProviderUsesLocalSettings(provider) || isApplyingReaderVoiceConfig) {
      return;
    }
    if (status?.state && status.state !== "NOT_CONFIGURED" && status.state !== "INVALID") {
      return;
    }
    if (!bridge?.saveVoiceProviderConfiguration) {
      return;
    }

    isApplyingReaderVoiceConfig = true;
    try {
      const response = parseResponse(
        bridge.saveVoiceProviderConfiguration(JSON.stringify(buildVoiceConfigurationPayload(provider)))
      );
      if (!response.ok) {
        return;
      }
      applyVoiceProvidersFromResponse(response);
      refreshVoiceDefinitions();
      renderVoiceStatus(
        response.status || getSelectedVoiceProviderStatus(),
        response.status?.message || "Da tai cau hinh Google TTS tu Reader/TXT."
      );
    } finally {
      isApplyingReaderVoiceConfig = false;
    }
  }

  function renderGeminiStatus(status, overrideMessage = "") {
    lastGeminiStatus = status || { state: "NOT_CONFIGURED", model: "" };
    const model = lastGeminiStatus.model || refs.geminiModel.value.trim() || DEFAULT_GEMINI_MODEL;

    if (lastGeminiStatus.model) {
      refs.geminiModel.value = lastGeminiStatus.model;
    }

    if (lastGeminiStatus.state === "CONNECTED") {
      refs.geminiConnectionStatus.textContent = `Da ket noi Gemini · model: ${model}`;
      refs.geminiConfigurationStatus.textContent = overrideMessage || "Gemini da san sang. Ban co the tao content that ngay trong APK.";
    } else if (lastGeminiStatus.state === "INVALID") {
      refs.geminiConnectionStatus.textContent = `Gemini chua hop le · model: ${model}`;
      refs.geminiConfigurationStatus.textContent = overrideMessage || lastGeminiStatus.message || "API key hoac model hien tai bi Gemini tu choi.";
    } else {
      refs.geminiConnectionStatus.textContent = "Chua cau hinh Gemini.";
      refs.geminiConfigurationStatus.textContent = "Can cau hinh Gemini truoc khi chay.";
    }

    updateNotice();
    updateStartState();
  }

  function renderImageStatus(status, overrideMessage = "") {
    const provider = getSelectedProvider();
    const model = status?.model || getSelectedImageModel() || provider?.defaultModel || "-";
    const label = provider?.displayName || "Image provider";

    if (!refs.imageConnectionStatus || !refs.imageConfigurationStatus) return;

    if (!provider) {
      refs.imageConnectionStatus.textContent = "Chua co du lieu image provider.";
      refs.imageConfigurationStatus.textContent = "Can nap registry image provider truoc khi cau hinh.";
      return;
    }

    if (provider.health === "NOT_IMPLEMENTED") {
      refs.imageConnectionStatus.textContent = `${label} chua duoc port native.`;
      refs.imageConfigurationStatus.textContent = "Provider nay da co trong registry nhung chua duoc phep chay that trong pass hien tai.";
      updateNotice();
      return;
    }

    if (status.state === "GENERATION_VERIFIED") {
      refs.imageConnectionStatus.textContent = `Generation verified · ${label} · model: ${model}`;
      refs.imageConfigurationStatus.textContent = overrideMessage || status.message || "Provider da tao va xac minh anh that.";
    } else if (status.state === "CREDENTIAL_VALIDATED") {
      refs.imageConnectionStatus.textContent = `Credential validated · ${label} · model: ${model}`;
      refs.imageConfigurationStatus.textContent = overrideMessage || status.message || "Credential hop le, nhung chua co image artifact that trong job hien tai.";
    } else if (status.state === "CONFIG_SAVED") {
      refs.imageConnectionStatus.textContent = `Config saved · ${label} · model: ${model}`;
      refs.imageConfigurationStatus.textContent = overrideMessage || status.message || "Da luu credential, nhung chua xac thuc va chua tao anh that.";
    } else if (status.state === "INVALID") {
      refs.imageConnectionStatus.textContent = `Credential invalid · ${label} · model: ${model}`;
      refs.imageConfigurationStatus.textContent = overrideMessage || status.message || "Credential hoac quyen model hien tai khong hop le.";
    } else {
      refs.imageConnectionStatus.textContent = `Chua cau hinh ${label}.`;
      refs.imageConfigurationStatus.textContent = overrideMessage || "Can cau hinh image provider de buoc IMAGE co the chay that.";
    }

    updateNotice();
  }

  function renderVoiceStatus(status, overrideMessage = "") {
    const provider = getSelectedVoiceProvider();
    if (!refs.voiceConnectionStatus || !refs.voiceConfigurationStatus) return;

    if (!provider) {
      refs.voiceConnectionStatus.textContent = "Chua co du lieu voice provider.";
      refs.voiceConfigurationStatus.textContent = "Can nap registry voice provider truoc khi cau hinh.";
      return;
    }

    const voiceId = status?.voiceId || refs.voiceSelect?.value || "-";
    const locale = status?.locale || refs.voiceLocale?.value || provider.defaultLocale || "vi-VN";

    if (provider.health === "NOT_IMPLEMENTED") {
      refs.voiceConnectionStatus.textContent = `${provider.displayName} chua duoc port native.`;
      refs.voiceConfigurationStatus.textContent = "Provider nay da co trong registry va duong config native, nhung connector thuc te van dang duoc port.";
      updateNotice();
      return;
    }

    if (status.state === "GENERATION_VERIFIED") {
      refs.voiceConnectionStatus.textContent = `Generation verified · ${provider.displayName} · ${locale}`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || status.message || "Provider da tao va xac minh voice artifact that.";
    } else if (status.state === "SAMPLE_VERIFIED") {
      refs.voiceConnectionStatus.textContent = `Sample verified · ${provider.displayName} · ${voiceId}`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || status.message || "Da nghe thu thanh cong bang giong tieng Viet tren thiet bi.";
    } else if (status.state === "VOICE_LIST_LOADED") {
      refs.voiceConnectionStatus.textContent = `Voice list loaded · ${provider.displayName} · ${locale}`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || status.message || "Da nap danh sach giong doc tieng Viet.";
    } else if (status.state === "CONFIG_SAVED") {
      refs.voiceConnectionStatus.textContent = `Config saved · ${provider.displayName} · ${locale}`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || status.message || "Da luu cau hinh voice provider, nhung chua test generation that.";
    } else if (status.state === "INVALID") {
      refs.voiceConnectionStatus.textContent = `Voice provider invalid · ${provider.displayName}`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || status.message || "Cau hinh voice provider hien tai khong hop le.";
    } else {
      refs.voiceConnectionStatus.textContent = `Chua cau hinh ${provider.displayName}.`;
      refs.voiceConfigurationStatus.textContent = overrideMessage || "Can cau hinh voice provider truoc khi tao voice artifact that.";
    }

    updateNotice();
  }

  function updateNotice() {
    const provider = getSelectedProvider();
    const imageStatus = getSelectedProviderStatus();
    const voiceProvider = getSelectedVoiceProvider();
    const voiceStatus = getSelectedVoiceProviderStatus();
    const videoMode = getVideoRendererMode();

    if (lastGeminiStatus.state !== "CONNECTED") {
      refs.notice.textContent = "Can cau hinh Gemini truoc khi chay CONTENT. IMAGE da duoc khoa, va VOICE se dung provider tieng Viet ban chon sau khi IMAGE san sang.";
      return;
    }

    if (!provider) {
      refs.notice.textContent = "CONTENT da san sang voi Gemini. IMAGE se doi toi khi ban chon image provider.";
      return;
    }

    if (provider.health === "NOT_IMPLEMENTED") {
      refs.notice.textContent = `${provider.displayName} moi chi o muc registry. Chon OpenAI Images hoac Cloudflare Workers AI neu muon chay buoc IMAGE that ngay bay gio.`;
      return;
    }

    if (imageStatus.state === "GENERATION_VERIFIED") {
      if (voiceStatus.state === "GENERATION_VERIFIED") {
        refs.notice.textContent = videoMode === VIDEO_RENDERER_MODE_NATIVE
          ? "CONTENT, IMAGE va VOICE deu da co artifact that. Android se uu tien render MP4 noi bo truoc khi chuyen sang metadata/review/publish."
          : videoMode === VIDEO_RENDERER_MODE_EXTERNAL
            ? "CONTENT, IMAGE va VOICE deu da co artifact that. Worker MoviePy cu van co the duoc dung neu ban muon thu lai huong deprecated."
            : "CONTENT, IMAGE va VOICE deu da co artifact that. Video dang o Local Plan Only nen se dung lai o VIDEO_RENDER_PLAN.";
        return;
      }
      if (voiceStatus.state === "SAMPLE_VERIFIED" || voiceStatus.state === "VOICE_LIST_LOADED" || voiceStatus.state === "CONFIG_SAVED") {
        refs.notice.textContent = "IMAGE da san sang. VOICE co the retry rieng voi provider dang chon ma khong chay lai CONTENT hay IMAGE.";
        return;
      }
      refs.notice.textContent = "CONTENT va IMAGE deu da co provider that. Hay cau hinh VOICE voi provider tieng Viet trong registry de tiep tuc workflow.";
      return;
    }

    if (imageStatus.state === "CREDENTIAL_VALIDATED") {
      refs.notice.textContent = "Credential image provider da hop le. Khi chay job, app se giu CONTENT va scene prompts, tao 3 anh that theo thu tu tung canh.";
      return;
    }

    if (voiceProvider && voiceStatus.state === "VOICE_LIST_LOADED") {
      refs.notice.textContent = "VOICE provider da nap duoc danh sach giong. Pipeline moi: SCRIPT -> SCENE -> ASSET_PLAN -> VISUAL -> VOICE/SUBTITLE -> VIDEO -> REVIEW -> PUBLISH.";
      return;
    }

    refs.notice.textContent = "CONTENT da san sang voi Gemini. ASSET_PLAN se uu tien stock/template de giam chi phi; VISUAL su dung provider ban chon, sau do VOICE co the retry rieng.";
  }

  function updateStartState() {
    const hasTopic = refs.topic.value.trim().length > 0;
    const isGeminiReady = lastGeminiStatus.state === "CONNECTED";
    refs.start.disabled = !(hasTopic && isGeminiReady);
  }

  function updateRetryState() {
    const canRetryImage = Boolean(lastJob?.jobId && lastJob?.scenePrompts?.length);
    const canRetryVoice = Boolean(
      lastJob?.jobId
      && lastJob?.generatedText
      && Array.isArray(lastJob?.artifacts)
      && lastJob.artifacts.some(artifact => artifact.artifactType === "IMAGE")
    );
    const canRetryVideo = Boolean(
      lastJob?.jobId
      && lastJob?.generatedText
      && Array.isArray(lastJob?.scenePrompts)
      && lastJob.scenePrompts.length
      && Array.isArray(lastJob?.assetPlans)
      && lastJob.assetPlans.length
      && Array.isArray(lastJob?.artifacts)
      && lastJob.artifacts.some(artifact => artifact.artifactType === "IMAGE")
      && lastJob.artifacts.some(artifact => artifact.artifactType === "VOICE")
    );
    const canExportVideo = Boolean(
      lastJob?.jobId
      && Array.isArray(lastJob?.artifacts)
      && lastJob.artifacts.some(artifact => artifact.artifactType === "VIDEO_MP4")
      && Array.isArray(lastJob?.steps)
      && lastJob.steps.some(step => step.stepType === "VIDEO" && step.waitingReason === "VIDEO_MP4_READY")
    );
    const canRetryMetadata = Boolean(
      lastJob?.jobId
      && Array.isArray(lastJob?.artifacts)
      && lastJob.artifacts.some(artifact => artifact.artifactType === "VIDEO_MP4")
    );
    const reviewStatus = String(lastJob?.reviewState?.status || "");
    const publishStatus = String(lastJob?.publishPlan?.status || "");
    const canApproveReview = Boolean(lastJob?.jobId && reviewStatus === "WAITING_USER");
    const canRejectReview = Boolean(lastJob?.jobId && reviewStatus === "WAITING_USER");
    const canSharePublish = Boolean(lastJob?.jobId && (publishStatus === "READY" || publishStatus === "MANUAL_ASSISTED"));
    const canMarkPublished = Boolean(lastJob?.jobId && (publishStatus === "READY" || publishStatus === "MANUAL_ASSISTED"));
    refs.retryImageAction?.classList.toggle("hidden", !canRetryImage);
    if (refs.retryImage) {
      refs.retryImage.disabled = !canRetryImage || !isProviderImplemented(getSelectedProvider());
    }
    refs.retryVoiceAction?.classList.toggle("hidden", !canRetryVoice);
    if (refs.retryVoice) {
      refs.retryVoice.disabled = !canRetryVoice || !isProviderImplemented(getSelectedVoiceProvider());
    }
    refs.retryVideoAction?.classList.toggle("hidden", !canRetryVideo);
    if (refs.retryVideo) {
      refs.retryVideo.disabled = !canRetryVideo;
    }
    refs.exportVideoAction?.classList.toggle("hidden", !canExportVideo);
    if (refs.exportVideo) {
      refs.exportVideo.disabled = !canExportVideo;
    }
    refs.retryMetadataAction?.classList.toggle("hidden", !canRetryMetadata);
    if (refs.retryMetadata) refs.retryMetadata.disabled = !canRetryMetadata;
    refs.approveReviewAction?.classList.toggle("hidden", !canApproveReview);
    if (refs.approveReview) refs.approveReview.disabled = !canApproveReview;
    refs.rejectReviewAction?.classList.toggle("hidden", !canRejectReview);
    if (refs.rejectReview) refs.rejectReview.disabled = !canRejectReview;
    refs.sharePublishAction?.classList.toggle("hidden", !canSharePublish);
    if (refs.sharePublish) refs.sharePublish.disabled = !canSharePublish;
    refs.markPublishedAction?.classList.toggle("hidden", !canMarkPublished);
    if (refs.markPublished) refs.markPublished.disabled = !canMarkPublished;
  }

  function renderSteps(job) {
    const map = new Map((job?.steps || []).map(step => [step.stepType, step]));
    refs.stepList.innerHTML = STEP_ORDER.map((entry, index) => {
      const step = map.get(entry.key);
      const status = step?.status || "PENDING";
      const waitingReason = step?.waitingReason || entry.key;
      const cssClass =
        status === "COMPLETED" ? "done" :
        status === "FAILED" ? "error" :
        status === "RUNNING" ? "processing" :
        "waiting";

      return `
        <article class="automation-step-card ${cssClass}">
          <span class="automation-step-index">${String(index + 1).padStart(2, "0")}</span>
          <div class="automation-step-copy">
            <b>${escapeHtml(entry.label)}</b>
            <small>${escapeHtml(waitingReason)}</small>
          </div>
          <span class="automation-step-state">${escapeHtml(status)}</span>
        </article>
      `;
    }).join("");
  }

  function renderScenePrompts(scenePrompts) {
    if (!Array.isArray(scenePrompts) || scenePrompts.length === 0) return "";

    return `
      <div class="automation-artifact-block">
        <b>Scene prompts (${escapeHtml(scenePrompts.length)})</b>
        ${scenePrompts.map(scene => `
          <div class="automation-artifact-row">
            <b>Canh ${escapeHtml(scene.ordinal || "-")} · ${escapeHtml(scene.plannedDurationMs || 0)} ms</b>
            <small>${escapeHtml(scene.summary || "")}</small>
            ${scene.onScreenText ? `<small>On-screen: ${escapeHtml(scene.onScreenText)}</small>` : ""}
            ${scene.voiceText ? `<small>Voice: ${escapeHtml(scene.voiceText)}</small>` : ""}
            ${scene.stockSearchQuery ? `<small>Stock query: ${escapeHtml(scene.stockSearchQuery)}</small>` : ""}
            ${scene.visualDirection ? `<small>Visual direction: ${escapeHtml(scene.visualDirection)}</small>` : ""}
            ${/tao video|scene so|1080x1920|bat buoc/i.test(String(scene.stockSearchQuery || "")) ? `<small>Stock query looks polluted by full prompt.</small>` : ""}
            <small>${multilineHtml(scene.visualPrompt || "")}</small>
          </div>
        `).join("")}
      </div>
    `;
  }

  function renderAssetPlans(assetPlans) {
    if (!Array.isArray(assetPlans) || assetPlans.length === 0) return "";

    return `
      <div class="automation-artifact-block">
        <b>Asset plan</b>
        ${assetPlans.map(plan => `
          <div class="automation-artifact-row">
            <b>Canh ${escapeHtml(plan.ordinal || "-")} · ${escapeHtml(plan.strategy || "-")}</b>
            <small>Query: ${escapeHtml(plan.assetQuery || "")}</small>
            ${/tao video|scene so|1080x1920|bat buoc/i.test(String(plan.assetQuery || "")) ? `<small>Stock query looks polluted by full prompt.</small>` : ""}
            <small>Template: ${escapeHtml(plan.templateId || "-")} · ${escapeHtml(plan.renderMode || "-")}</small>
            <small>${escapeHtml(plan.rationale || "")}</small>
          </div>
        `).join("")}
      </div>
    `;
  }

  function renderVideoRenderPlan(plan) {
    if (!plan || !Array.isArray(plan.scenes) || !plan.scenes.length) return "";

    return `
      <div class="automation-scene-card">
        <div class="automation-section-head">
          <span class="automation-kicker muted">VIDEO PLAN</span>
          <b>${escapeHtml(plan.rendererId || "local-json-video-plan")}</b>
        </div>
        <p>Target: ${escapeHtml(plan.renderTarget || "-")} · Scenes: ${escapeHtml(plan.sceneCount || 0)} · Tong thoi luong: ${escapeHtml(plan.totalDurationMs || 0)} ms</p>
        <div class="automation-result-stack">
          ${plan.scenes.map(scene => `
            <div class="automation-scene-row">
              <b>Scene ${escapeHtml(scene.ordinal)} · ${escapeHtml(scene.renderMode || "-")}</b>
              <small>${escapeHtml(scene.strategy || "-")} · ${escapeHtml(scene.durationMs || 0)} ms</small>
              <small>${escapeHtml(scene.subtitleText || "")}</small>
            </div>
          `).join("")}
        </div>
      </div>
    `;
  }

  function testVideoWorker() {
    const mode = getVideoRendererMode();
    const workerUrl = getVideoWorkerUrl();
    if (mode !== VIDEO_RENDERER_MODE_EXTERNAL) {
      renderVideoWorkerStatus(
        mode === VIDEO_RENDERER_MODE_NATIVE ? "Dang o Android Native Render." : "Dang o Local Plan Only.",
        "Chi can test worker neu ban chu dong dung nhanh deprecated External MoviePy Worker."
      );
      return;
    }
    if (!bridge?.testVideoRenderWorker) {
      setError("Video worker bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!workerUrl) {
      setError("Can nhap worker URL truoc khi test.");
      return;
    }

    setError("");
    renderVideoWorkerStatus("Dang kiem tra worker video...", "App se goi GET /health tren worker.");
    const response = parseResponse(
      bridge.testVideoRenderWorker(JSON.stringify({ videoWorkerUrl: workerUrl }))
    );
    if (!response.ok) {
      renderVideoWorkerStatus("Worker chua san sang.", response.message || "Khong the ket noi worker video.");
      setError(response.message || "Khong the ket noi worker video.");
      return;
    }

    const status = response.status || {};
    renderVideoWorkerStatus(
      `Da ket noi worker · ${status.providerId || "moviepy"} · ${status.model || "-"}`,
      status.message || "Worker co the nhan render plan va xuat MP4 that."
    );
  }

  function renderArtifacts(artifacts) {
    if (!Array.isArray(artifacts) || artifacts.length === 0) return "";

    return artifacts.map(artifact => {
      const preview = artifact.previewDataUrl && String(artifact.mimeType || "").startsWith("image/")
        ? `<img class="automation-result-image" src="${escapeHtml(artifact.previewDataUrl)}" alt="Automation artifact ${escapeHtml(artifact.ordinal || "")}">`
        : artifact.previewDataUrl && String(artifact.mimeType || "").startsWith("audio/")
          ? `<audio class="automation-result-audio" controls preload="metadata" src="${escapeHtml(artifact.previewDataUrl)}"></audio>`
          : "";

      return `
        <div class="automation-artifact-row">
          <b>${escapeHtml(artifact.artifactType || "ARTIFACT")}${artifact.ordinal ? ` · ${escapeHtml(artifact.ordinal)}` : ""}</b>
          ${artifact.sceneId ? `<small>Scene: ${escapeHtml(artifact.sceneId)}</small>` : ""}
          ${artifact.providerRequestId ? `<small>Provider request: ${escapeHtml(artifact.providerRequestId)}</small>` : ""}
          <small>URI: ${escapeHtml(artifact.uri || artifact.artifactId || "")}</small>
          ${artifact.sourceUrl ? `<small>Source URL: ${escapeHtml(artifact.sourceUrl)}</small>` : ""}
          ${preview}
        </div>
      `;
    }).join("");
  }

  function renderMetadataPlan(plan) {
    if (!plan) return "";
    const platforms = plan.platforms || {};
    return `
      <div class="automation-artifact-block">
        <b>Metadata plan</b>
        <div class="automation-artifact-row">
          <b>${escapeHtml(plan.title || "-")}</b>
          <small>Short title: ${escapeHtml(plan.shortTitle || "-")}</small>
          <small>${multilineHtml(plan.description || "")}</small>
          <small>Thumbnail text: ${escapeHtml(plan.thumbnailText || "-")}</small>
          <small>Category: ${escapeHtml(plan.category || "-")} · Language: ${escapeHtml(plan.language || "-")}</small>
          <small>Hashtags: ${escapeHtml((plan.hashtags || []).join(" "))}</small>
          <small>Source scenes: ${escapeHtml(plan.sourceSceneCount || 0)} · Voice: ${escapeHtml(plan.sourceVoiceDurationMs || 0)} ms · Video: ${escapeHtml(plan.sourceVideoDurationMs || 0)} ms</small>
          ${(plan.safetyNotes || []).map(note => `<small>${escapeHtml(note)}</small>`).join("")}
        </div>
        ${Object.entries(platforms).map(([key, value]) => `
          <div class="automation-artifact-row">
            <b>${escapeHtml(key)}</b>
            ${value.title ? `<small>Title: ${escapeHtml(value.title)}</small>` : ""}
            ${value.description ? `<small>${multilineHtml(value.description)}</small>` : ""}
            ${value.caption ? `<small>${multilineHtml(value.caption)}</small>` : ""}
            ${Array.isArray(value.hashtags) ? `<small>Hashtags: ${escapeHtml(value.hashtags.join(" "))}</small>` : ""}
            ${value.visibility ? `<small>Visibility: ${escapeHtml(value.visibility)}</small>` : ""}
          </div>
        `).join("")}
      </div>
    `;
  }

  function renderReviewState(state) {
    if (!state) return "";
    return `
      <div class="automation-artifact-block">
        <b>Review state</b>
        <div class="automation-artifact-row">
          <b>Status: ${escapeHtml(state.status || "-")}</b>
          ${(state.checks || []).map(check => `<small>${escapeHtml(check.passed ? "PASS" : "FAIL")} · ${escapeHtml(check.label)} · ${escapeHtml(check.detail || "")}</small>`).join("")}
          ${(state.warnings || []).map(warning => `<small>Warning: ${escapeHtml(warning)}</small>`).join("")}
          ${state.rejectedReason ? `<small>Rejected reason: ${escapeHtml(state.rejectedReason)}</small>` : ""}
        </div>
      </div>
    `;
  }

  function renderPublishPlan(plan) {
    if (!plan) return "";
    return `
      <div class="automation-artifact-block">
        <b>Publish plan</b>
        <div class="automation-artifact-row">
          <b>Status: ${escapeHtml(plan.status || "-")}</b>
          <small>Mode: ${escapeHtml(plan.publishMode || "-")}</small>
          <small>Targets: ${escapeHtml((plan.targets || []).join(", "))}</small>
          <small>Review: ${escapeHtml(plan.reviewStatus || "-")}</small>
          <small>Video artifact: ${escapeHtml(plan.videoArtifactUri || "-")}</small>
          <small>Metadata artifact: ${escapeHtml(plan.metadataArtifactUri || "-")}</small>
          ${(plan.notes || []).map(note => `<small>${escapeHtml(note)}</small>`).join("")}
        </div>
      </div>
    `;
  }

  function renderResult(job) {
    lastJob = job || null;
    updateRetryState();

    if (!job) {
      refs.resultCard.classList.add("hidden");
      refs.resultSummary.innerHTML = "";
      return;
    }

    const usage = job.usageMetadata || {};
    const effectiveMaximumOutputLength = Number(usage.maximumOutputLength || 0);
    const expectedSceneCount = Number(usage.expectedSceneCount || 0);
    const parsedSceneCount = Number(usage.parsedSceneCount || (job.scenePrompts?.length || 0));
    const artifactSummary = renderArtifacts(job.artifacts);
    const scenePromptSummary = renderScenePrompts(job.scenePrompts);
    const assetPlanSummary = renderAssetPlans(job.assetPlans);
    const videoRenderPlanSummary = renderVideoRenderPlan(job.videoRenderPlan);
    const metadataSummary = renderMetadataPlan(job.metadataPlan);
    const reviewSummary = renderReviewState(job.reviewState);
    const publishSummary = renderPublishPlan(job.publishPlan);
    const imageStep = (job.steps || []).find(step => step.stepType === "IMAGES_VISUALS");
    const voiceStep = (job.steps || []).find(step => step.stepType === "VOICE");
    const subtitleStep = (job.steps || []).find(step => step.stepType === "SUBTITLE");
    const videoStep = (job.steps || []).find(step => step.stepType === "VIDEO");
    const voiceArtifact = Array.isArray(job.artifacts)
      ? job.artifacts.find(artifact => artifact.artifactType === "VOICE")
      : null;
    const voiceInputSceneCount = Number(extractDebugMetric(voiceArtifact?.sourceUrl, "inputSceneCount") || 0);
    const voiceInputCharCount = Number(extractDebugMetric(voiceArtifact?.sourceUrl, "inputCharCount") || 0);
    const voiceDurationMs = Number(extractDebugMetric(voiceArtifact?.sourceUrl, "durationMs") || 0);
    const listicleCount = parseTopicListicleCount(job.topic);
    const declaredSceneCount = parseDeclaredSceneCount(job.generatedText || "");
    const warningMessages = [];
    if (listicleCount >= 10 && Array.isArray(job.scenePrompts) && job.scenePrompts.length < 10) {
      warningMessages.push(`Topic dang yeu cau ${listicleCount} muc nhung scene prompt moi co ${job.scenePrompts.length}.`);
    }
    if (declaredSceneCount >= 12 && Array.isArray(job.scenePrompts) && job.scenePrompts.length < 10) {
      warningMessages.push(`Generated script dang khai bao khoang ${declaredSceneCount} scene nhung app moi tao ${job.scenePrompts.length} scene prompt.`);
    }
    if (expectedSceneCount > 0 && parsedSceneCount > 0 && parsedSceneCount < expectedSceneCount) {
      warningMessages.push(`Scene parser moi tao ${parsedSceneCount}/${expectedSceneCount} scene. Gemini output co the van bi cut hoac thieu scene.`);
    }
    if (listicleCount >= 10 && voiceDurationMs > 0 && voiceDurationMs < 30000) {
      warningMessages.push(`Voice duration moi dat ${voiceDurationMs} ms, qua ngan so voi topic listicle ${listicleCount} muc.`);
    }
    const scriptCharCount = String(job.generatedText || "").trim().length;

    refs.resultCard.classList.remove("hidden");
    refs.resultSummary.innerHTML = `
      <div class="automation-result-grid">
        <div><span>Job ID</span><b>${escapeHtml(job.jobId || "-")}</b></div>
        <div><span>Content provider</span><b>${escapeHtml(job.providerId || "-")}</b></div>
        <div><span>Model</span><b>${escapeHtml(job.model || "-")}</b></div>
        <div><span>Max output</span><b>${escapeHtml(effectiveMaximumOutputLength || 0)}</b></div>
        <div><span>Expected scenes</span><b>${escapeHtml(expectedSceneCount || 0)}</b></div>
        <div><span>Parsed scenes</span><b>${escapeHtml(parsedSceneCount || 0)}</b></div>
        <div><span>Script chars</span><b>${escapeHtml(scriptCharCount)}</b></div>
        <div><span>Scene count</span><b>${escapeHtml(job.scenePrompts?.length || 0)}</b></div>
        <div><span>Voice chars</span><b>${escapeHtml(voiceInputCharCount || 0)}</b></div>
        <div><span>Voice scenes</span><b>${escapeHtml(voiceInputSceneCount || 0)}</b></div>
        <div><span>Voice duration</span><b>${escapeHtml(voiceDurationMs || 0)} ms</b></div>
        <div><span>IMAGE</span><b>${escapeHtml(imageStep?.waitingReason || "-")}</b></div>
        <div><span>VOICE</span><b>${escapeHtml(voiceStep?.waitingReason || "-")}</b></div>
        <div><span>SUBTITLE</span><b>${escapeHtml(subtitleStep?.waitingReason || "-")}</b></div>
        <div><span>VIDEO</span><b>${escapeHtml(videoStep?.waitingReason || "-")}</b></div>
      </div>
      <p>${escapeHtml(job.runtimeMessage || "Pipeline da tao xong.")}</p>
      ${warningMessages.length ? `<div class="automation-warning-box">${warningMessages.map(message => `<small>${escapeHtml(message)}</small>`).join("")}</div>` : ""}
      ${scenePromptSummary}
      ${assetPlanSummary}
      ${videoRenderPlanSummary}
      ${metadataSummary}
      ${reviewSummary}
      ${publishSummary}
      ${artifactSummary}
      ${Object.keys(usage).length ? `<small>Usage: ${escapeHtml(JSON.stringify(usage))}</small>` : ""}
      <div class="automation-generated-script">${multilineHtml(job.generatedText || "")}</div>
    `;
  }

  function applyImageProvidersFromResponse(response) {
    if (Array.isArray(response?.providers)) {
      imageProviders = response.providers;
      renderImageProviderOptions();
      renderImageProviderDetails();
    }
  }

  function applyVoiceProvidersFromResponse(response) {
    if (Array.isArray(response?.providers)) {
      voiceProviders = response.providers;
      renderVoiceProviderOptions();
      renderVoiceProviderDetails();
    }
  }

  function refreshGeminiStatus() {
    if (!bridge?.getGeminiConfigurationStatus) {
      setError("Automation Bridge chua san sang trong shell Android nay.");
      return;
    }

    const response = parseResponse(bridge.getGeminiConfigurationStatus());
    if (!response.ok) {
      setError(response.message || "Khong the doc trang thai Gemini.");
      return;
    }

    renderGeminiStatus(response.status || { state: "NOT_CONFIGURED" });
  }

  function refreshImageStatus() {
    if (!bridge?.getImageProviderConfigurationStatus) {
      return;
    }

    const response = parseResponse(bridge.getImageProviderConfigurationStatus());
    if (!response.ok) {
      setError(response.message || "Khong the doc trang thai image provider.");
      return;
    }

    applyImageProvidersFromResponse(response);
    renderImageStatus(response.status || getSelectedProviderStatus());
    updateRetryState();
  }

  function refreshVoiceStatus() {
    if (!bridge?.getVoiceProviderConfigurationStatus) {
      return;
    }

    const providerId = refs.voiceProvider?.value || selectedVoiceProviderId || "";
    const response = parseResponse(
      bridge.getVoiceProviderConfigurationStatus(JSON.stringify({ providerId }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the doc trang thai voice provider.");
      return;
    }

    applyVoiceProvidersFromResponse(response);
    refreshVoiceDefinitions();
    renderVoiceStatus(response.status || getSelectedVoiceProviderStatus());
    tryHydrateReaderVoiceConfiguration(response.status || getSelectedVoiceProviderStatus());
    updateRetryState();
  }

  function refreshVoiceDefinitions() {
    const provider = getSelectedVoiceProvider();
    voiceDefinitions = [];
    if (!provider || !bridge?.listVoiceDefinitions || !isProviderImplemented(provider)) {
      renderVoiceDefinitions();
      return;
    }

    const response = parseResponse(
      bridge.listVoiceDefinitions(JSON.stringify({ providerId: provider.providerId }))
    );
    if (!response.ok) {
      renderVoiceDefinitions();
      return;
    }

    voiceDefinitions = Array.isArray(response.voices) ? response.voices : [];
    renderVoiceDefinitions();
  }

  function saveGeminiConfiguration() {
    if (!bridge?.saveGeminiConfiguration) {
      setError("Automation Bridge chua san sang trong shell Android nay.");
      return;
    }

    setError("");
    const payload = {
      apiKey: refs.geminiApiKey.value,
      model: refs.geminiModel.value.trim() || DEFAULT_GEMINI_MODEL
    };
    const response = parseResponse(
      bridge.saveGeminiConfiguration(JSON.stringify(payload))
    );

    if (!response.ok) {
      setError(response.message || "Khong the luu cau hinh Gemini.");
      return;
    }

    refs.geminiApiKey.value = "";
    renderGeminiStatus(response.status || { state: "CONNECTED", model: payload.model });
  }

  function saveImageConfiguration() {
    const provider = getSelectedProvider();
    if (!bridge?.saveImageProviderConfiguration || !provider) {
      setError("Image provider bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    const model = getSelectedImageModel() || provider.defaultModel || "";
    if (!isSafeModelForProvider(provider, model)) {
      setError("Model image provider khong hop le.");
      return;
    }
    if (requiresAccountId(provider) && !refs.imageAccountId.value.trim()) {
      setError("Can nhap Account ID cho provider nay.");
      return;
    }

    const payload = {
      providerId: provider.providerId,
      apiKey: refs.imageApiKey.value,
      model,
      accountId: requiresAccountId(provider) ? refs.imageAccountId.value.trim() : ""
    };
    const response = parseResponse(
      bridge.saveImageProviderConfiguration(JSON.stringify(payload))
    );

    if (!response.ok) {
      setError(response.message || "Khong the luu cau hinh image provider.");
      return;
    }

    refs.imageApiKey.value = "";
    applyImageProvidersFromResponse(response);
    renderImageStatus(response.status || getSelectedProviderStatus());
  }

  function saveVoiceConfiguration() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.saveVoiceProviderConfiguration || !provider) {
      setError("Voice provider bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Voice provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    const payload = buildVoiceConfigurationPayload(provider);
    if (payload.model && !SAFE_VOICE_MODEL.test(payload.model)) {
      setError("Model voice provider khong hop le.");
      return;
    }
    const response = parseResponse(
      bridge.saveVoiceProviderConfiguration(JSON.stringify(payload))
    );

    if (!response.ok) {
      setError(response.message || "Khong the luu cau hinh voice provider.");
      return;
    }

    refs.voiceApiKey.value = "";
    applyVoiceProvidersFromResponse(response);
    renderVoiceStatus(response.status || getSelectedVoiceProviderStatus());
  }

  function testGeminiConnection() {
    if (!bridge?.testGeminiConnection) {
      setError("Automation Bridge chua san sang trong shell Android nay.");
      return;
    }

    setError("");
    refs.geminiConnectionStatus.textContent = "Dang kiem tra Gemini...";
    const response = parseResponse(bridge.testGeminiConnection());

    if (!response.ok) {
      refreshGeminiStatus();
      setError(response.message || "Kiem tra Gemini that bai.");
      return;
    }

    renderGeminiStatus(
      response.status || { state: "CONNECTED", model: refs.geminiModel.value.trim() || DEFAULT_GEMINI_MODEL },
      response.status?.message || "Da ket noi Gemini thanh cong."
    );
  }

  function testImageConnection() {
    const provider = getSelectedProvider();
    if (!bridge?.testImageProviderConnection || !provider) {
      setError("Image provider bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    refs.imageConnectionStatus.textContent = `Dang kiem tra ${provider.displayName}...`;
    const response = parseResponse(
      bridge.testImageProviderConnection(JSON.stringify({ providerId: provider.providerId }))
    );

    if (!response.ok) {
      refreshImageStatus();
      const imageMessages = {
        INVALID_API_KEY: "API key hoac token khong hop le.",
        BILLING_REQUIRED: "Can bat billing cho provider nay truoc khi test.",
        CREDIT_EXHAUSTED: "Provider da het credit cho tai khoan hien tai.",
        MODEL_ACCESS_REQUIRED: "Tai khoan hien tai chua duoc phep dung model nay.",
        ACCOUNT_VERIFICATION_REQUIRED: "Provider yeu cau xac minh tai khoan hoac to chuc.",
        RATE_LIMITED: "Provider dang gioi han tam thoi. Thu lai sau.",
        PROVIDER_UNAVAILABLE: "Provider tam thoi khong san sang.",
        COMMUNITY_QUEUE_DELAY: "Provider dang qua tai, can cho hang doi.",
        USER_ACTION_REQUIRED: "Provider nay chua duoc port hoac can thao tac tay them.",
        INVALID_RESPONSE: "Provider tra ve phan hoi khong hop le.",
        INVALID_IMAGE: "Provider tra ve artifact anh khong hop le."
      };
      if (imageMessages[response.errorCode]) {
        refs.imageConfigurationStatus.textContent = imageMessages[response.errorCode];
      }
      setError(response.message || "Kiem tra image provider that bai.");
      return;
    }

    refreshImageStatus();
    renderImageStatus(
      response.status || getSelectedProviderStatus(),
      response.status?.message || "Credential image provider hop le."
    );
  }

  function testVoiceConnection() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.testVoiceProviderConnection || !provider) {
      setError("Voice provider bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Voice provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    refs.voiceConnectionStatus.textContent = `Dang nap giong ${provider.displayName}...`;
    const response = parseResponse(
      bridge.testVoiceProviderConnection(JSON.stringify({ providerId: provider.providerId }))
    );

    if (!response.ok) {
      refreshVoiceStatus();
      setError(response.message || "Kiem tra voice provider that bai.");
      return;
    }

    refreshVoiceStatus();
    renderVoiceStatus(
      response.status || getSelectedVoiceProviderStatus(),
      response.status?.message || "Da nap danh sach giong tieng Viet."
    );
  }

  function sampleVoice() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.synthesizeVoiceSample || !provider) {
      setError("Voice provider bridge chua san sang trong shell Android nay.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Voice provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    refs.voiceConnectionStatus.textContent = `Dang nghe thu ${provider.displayName}...`;
    const response = parseResponse(
      bridge.synthesizeVoiceSample(JSON.stringify({ providerId: provider.providerId }))
    );

    if (!response.ok) {
      refreshVoiceStatus();
      setError(response.message || "Khong the nghe thu giong doc.");
      return;
    }

    const artifact = response.artifact;
    if (artifact?.previewDataUrl) {
      try {
        const audio = new Audio(artifact.previewDataUrl);
        audio.play().catch(() => {});
      } catch {}
    }
    refreshVoiceStatus();
    renderVoiceStatus(
      { ...getSelectedVoiceProviderStatus(), state: "SAMPLE_VERIFIED" },
      "Da nghe thu giong doc tieng Viet thanh cong."
    );
  }

  function openVoiceSettings() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.openVoiceProviderSettings || !provider) {
      setError("Voice provider bridge chua san sang trong shell Android nay.");
      return;
    }
    setError("");
    parseResponse(bridge.openVoiceProviderSettings(JSON.stringify({ providerId: provider.providerId })));
  }

  function startAutomation() {
    if (!bridge?.generateAutomationContent) {
      setError("Automation Bridge chua san sang trong shell Android nay.");
      return;
    }

    setError("");
    renderResult(null);
    refs.runtimeCopy.textContent = "Dang chay SCRIPT -> SCENE -> ASSET_PLAN -> VISUAL -> VOICE -> SUBTITLE -> VIDEO. Neu du dieu kien, app se tao VIDEO_RENDER_PLAN JSON.";

    const payload = {
      topic: refs.topic.value,
      language: "vi",
      contentType: "video_script",
      maximumOutputLength: 12000,
      videoRendererMode: getVideoRendererMode(),
      videoWorkerUrl: getVideoWorkerUrl()
    };
    const response = parseResponse(
      bridge.generateAutomationContent(JSON.stringify(payload))
    );

    if (!response.ok) {
      if (response.errorCode === "NOT_CONFIGURED") {
        refreshGeminiStatus();
        refreshImageStatus();
        refreshVoiceStatus();
      }
      refs.runtimeCopy.textContent = "Pipeline SCRIPT -> SCENE -> ASSET_PLAN -> VISUAL -> VOICE -> VIDEO chua hoan thanh.";
      setError(response.message || "Khong the tao pipeline that luc nay.");
      return;
    }

    const job = response.job;
    renderSteps(job);
    renderResult(job);
    refs.runtimeCopy.textContent = job?.runtimeMessage || "Content, image, voice va video render plan da duoc cap nhat.";
    refreshImageStatus();
    refreshVoiceStatus();
  }

  function retryImageStep() {
    const provider = getSelectedProvider();
    if (!bridge?.retryImageStep || !lastJob?.jobId || !provider) {
      setError("Chua co job hop le de thu lai IMAGE.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    refs.runtimeCopy.textContent = `Dang thu lai buoc IMAGE voi ${provider.displayName} ma khong chay lai CONTENT...`;
    const response = parseResponse(
      bridge.retryImageStep(JSON.stringify({
        jobId: lastJob.jobId,
        providerId: provider.providerId
      }))
    );

    if (!response.ok) {
      refreshImageStatus();
      setError(response.message || "Khong the retry buoc IMAGE luc nay.");
      return;
    }

    const job = response.job;
    renderSteps(job);
    renderResult(job);
    refs.runtimeCopy.textContent = job?.runtimeMessage || "Da thu lai IMAGE thanh cong.";
    refreshImageStatus();
    refreshVoiceStatus();
  }

  function retryVoiceStep() {
    const provider = getSelectedVoiceProvider();
    if (!bridge?.retryVoiceStep || !lastJob?.jobId || !provider) {
      setError("Chua co job hop le de thu lai VOICE.");
      return;
    }
    if (!isProviderImplemented(provider)) {
      setError("Voice provider nay chua duoc port native trong pass hien tai.");
      return;
    }

    setError("");
    refs.runtimeCopy.textContent = `Dang thu lai buoc VOICE voi ${provider.displayName} ma khong chay lai CONTENT hay IMAGE...`;
    const response = parseResponse(
      bridge.retryVoiceStep(JSON.stringify({
        jobId: lastJob.jobId,
        providerId: provider.providerId
      }))
    );

    if (!response.ok) {
      refreshVoiceStatus();
      setError(response.message || "Khong the retry buoc VOICE luc nay.");
      return;
    }

    const job = response.job;
    renderSteps(job);
    renderResult(job);
    refs.runtimeCopy.textContent = job?.runtimeMessage || "Da thu lai VOICE thanh cong.";
    refreshVoiceStatus();
  }

  function retryVideoStep() {
    if (!bridge?.retryVideoStep || !lastJob?.jobId) {
      setError("Chua co job hop le de thu lai VIDEO.");
      return;
    }

    setError("");
    refs.runtimeCopy.textContent = "Dang thu lai buoc VIDEO ma khong chay lai CONTENT, IMAGE hay VOICE...";
    const response = parseResponse(
      bridge.retryVideoStep(JSON.stringify({
        jobId: lastJob.jobId,
        videoRendererMode: getVideoRendererMode(),
        videoWorkerUrl: getVideoWorkerUrl()
      }))
    );

    if (!response.ok) {
      setError(response.message || "Khong the retry buoc VIDEO luc nay.");
      return;
    }

    const job = response.job;
    renderSteps(job);
    renderResult(job);
    refs.runtimeCopy.textContent = job?.runtimeMessage || "Da thu lai VIDEO thanh cong.";
  }

  function exportVideoMp4() {
    if (!bridge?.exportVideoMp4 || !lastJob?.jobId) {
      setError("Chua co VIDEO_MP4 hop le de export.");
      return;
    }
    setError("");
    refs.runtimeCopy.textContent = "Dang export VIDEO_MP4 ra Downloads/LQLQAutomation...";
    const response = parseResponse(
      bridge.exportVideoMp4(JSON.stringify({ jobId: lastJob.jobId }))
    );
    if (!response.ok) {
      refs.runtimeCopy.textContent = "Export MP4 that bai.";
      setError(response.message || "Khong the export VIDEO_MP4 luc nay.");
      return;
    }
    const exported = response.export || {};
    const location = exported.displayPath || exported.contentUri || "Downloads/LQLQAutomation";
    refs.runtimeCopy.textContent = `Da export MP4: ${location}`;
    setError("");
  }

  function retryMetadata() {
    if (!bridge?.retryAutomationMetadata || !lastJob?.jobId) {
      setError("Chua co job hop le de tao lai metadata.");
      return;
    }
    setError("");
    refs.runtimeCopy.textContent = "Dang tao lai metadata tu VIDEO_MP4 hien tai...";
    const response = parseResponse(
      bridge.retryAutomationMetadata(JSON.stringify({ jobId: lastJob.jobId }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the tao lai metadata luc nay.");
      return;
    }
    renderSteps(response.job);
    renderResult(response.job);
    refs.runtimeCopy.textContent = response.job?.runtimeMessage || "Da tao lai metadata.";
  }

  function approveReview() {
    if (!bridge?.approveAutomationReview || !lastJob?.jobId) {
      setError("Chua co review hop le de approve.");
      return;
    }
    setError("");
    const response = parseResponse(
      bridge.approveAutomationReview(JSON.stringify({ jobId: lastJob.jobId }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the approve review luc nay.");
      return;
    }
    renderSteps(response.job);
    renderResult(response.job);
    refs.runtimeCopy.textContent = response.job?.runtimeMessage || "Review da duoc approve.";
  }

  function rejectReview() {
    if (!bridge?.rejectAutomationReview || !lastJob?.jobId) {
      setError("Chua co review hop le de reject.");
      return;
    }
    const reason = window.prompt("Nhap ly do reject review:", "Can sua title/description");
    if (reason == null) return;
    setError("");
    const response = parseResponse(
      bridge.rejectAutomationReview(JSON.stringify({ jobId: lastJob.jobId, reason }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the reject review luc nay.");
      return;
    }
    renderSteps(response.job);
    renderResult(response.job);
    refs.runtimeCopy.textContent = response.job?.runtimeMessage || "Review da bi reject.";
  }

  function sharePublish() {
    if (!bridge?.shareAutomationPublish || !lastJob?.jobId) {
      setError("Chua co publish plan hop le de mo share sheet.");
      return;
    }
    setError("");
    refs.runtimeCopy.textContent = "Dang mo share sheet Android cho publish MVP...";
    const response = parseResponse(
      bridge.shareAutomationPublish(JSON.stringify({ jobId: lastJob.jobId }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the mo share sheet luc nay.");
      return;
    }
    renderSteps(response.job);
    renderResult(response.job);
    refs.runtimeCopy.textContent = response.job?.runtimeMessage || "Share sheet da duoc mo.";
  }

  function markPublished() {
    if (!bridge?.markAutomationPublished || !lastJob?.jobId) {
      setError("Chua co publish plan hop le de mark published.");
      return;
    }
    setError("");
    const response = parseResponse(
      bridge.markAutomationPublished(JSON.stringify({ jobId: lastJob.jobId }))
    );
    if (!response.ok) {
      setError(response.message || "Khong the danh dau published luc nay.");
      return;
    }
    renderSteps(response.job);
    renderResult(response.job);
    refs.runtimeCopy.textContent = response.job?.runtimeMessage || "Da danh dau published.";
  }

  function refreshView() {
    setError("");
    renderResult(null);
    renderSteps(null);
    refs.runtimeCopy.textContent = "Lam moi trang thai provider va pipeline local-native...";
    refreshGeminiStatus();
    refreshImageStatus();
    refreshVoiceStatus();
  }

  function openFromTools() {
    refs.overlay.classList.remove("hidden");
    refs.overlay.setAttribute("aria-hidden", "false");
    renderSteps(null);
    renderResult(null);
    refreshView();
  }

  function closeOverlay(returnToTools) {
    refs.overlay.classList.add("hidden");
    refs.overlay.setAttribute("aria-hidden", "true");
    if (returnToTools && typeof openToolsMenu === "function") {
      openToolsMenu();
    }
  }

  refs.saveGemini?.addEventListener("click", saveGeminiConfiguration);
  refs.testGemini?.addEventListener("click", testGeminiConnection);
  refs.saveImage?.addEventListener("click", saveImageConfiguration);
  refs.testImage?.addEventListener("click", testImageConnection);
  refs.saveVoice?.addEventListener("click", saveVoiceConfiguration);
  refs.testVoice?.addEventListener("click", testVoiceConnection);
  refs.sampleVoice?.addEventListener("click", sampleVoice);
  refs.openVoiceSettings?.addEventListener("click", openVoiceSettings);
  refs.testVideoWorker?.addEventListener("click", testVideoWorker);
  refs.retryImage?.addEventListener("click", retryImageStep);
  refs.retryVoice?.addEventListener("click", retryVoiceStep);
  refs.retryVideo?.addEventListener("click", retryVideoStep);
  refs.exportVideo?.addEventListener("click", exportVideoMp4);
  refs.retryMetadata?.addEventListener("click", retryMetadata);
  refs.approveReview?.addEventListener("click", approveReview);
  refs.rejectReview?.addEventListener("click", rejectReview);
  refs.sharePublish?.addEventListener("click", sharePublish);
  refs.markPublished?.addEventListener("click", markPublished);
  refs.imageProvider?.addEventListener("change", () => {
    selectedImageProviderId = refs.imageProvider.value || "";
    renderImageProviderDetails();
    renderImageStatus(getSelectedProviderStatus());
    updateRetryState();
  });
  refs.voiceProvider?.addEventListener("change", () => {
    selectedVoiceProviderId = refs.voiceProvider.value || "";
    renderVoiceProviderDetails();
    refreshVoiceStatus();
  });
  refs.videoRendererMode?.addEventListener("change", () => {
    updateNotice();
    if (getVideoRendererMode() === VIDEO_RENDERER_MODE_LOCAL) {
      renderVideoWorkerStatus("Dang o Local Plan Only.", "App se tao VIDEO_RENDER_PLAN de ban review hoac retry native render sau.");
    } else if (getVideoRendererMode() === VIDEO_RENDERER_MODE_NATIVE) {
      renderVideoWorkerStatus("Dang o Android Native Render.", "App se co gang xuat MP4 ngay tren thiet bi tu IMAGE + VOICE.");
    }
  });
  refs.videoWorkerUrl?.addEventListener("input", () => {
    if (!getVideoWorkerUrl()) {
      renderVideoWorkerStatus();
    }
  });
  [refs.voiceRate, refs.voicePitch].forEach(control => {
    control?.addEventListener("input", updateVoiceSliderLabels);
  });
  refs.imageModelPreset?.addEventListener("change", () => {
    const useCustom = refs.imageModelPreset.value === CUSTOM_MODEL_VALUE;
    refs.imageModelCustom.classList.toggle("hidden", !useCustom);
    if (!useCustom) {
      refs.imageModelCustom.value = "";
    }
  });
  refs.start?.addEventListener("click", startAutomation);
  refs.reset?.addEventListener("click", refreshView);
  refs.topic?.addEventListener("input", setTopicMeta);
  refs.back?.addEventListener("click", () => closeOverlay(true));
  refs.close?.addEventListener("click", () => closeOverlay(false));
  refs.overlay?.addEventListener("click", event => {
    if (event.target === refs.overlay) closeOverlay(false);
  });

  setTopicMeta();
  renderSteps(null);
  renderResult(null);
  updateNotice();
  updateRetryState();
  updateVoiceSliderLabels();
  renderVideoWorkerStatus();
  refreshVoiceStatus();

  window.LqlqAutomationCenter = {
    openFromTools
  };
})();
