(() => {
  "use strict";

  const SETTINGS_KEY = "shieldStoryReaderSettingsV1";
  const TEXT_KEY = "shieldStoryReaderDraftV1";
  const MAX_SAVED_TEXT = 1_500_000;

  const $ = id => document.getElementById(id);
  const ui = {
    toolbarBtn: $("readerToolbarBtn"),
    panel: $("storyReaderPanel"),
    backdrop: $("storyReaderBackdrop"),
    close: $("closeStoryReader"),
    // Việc (v0.23.25): chỉ lấy tab/pane BÊN TRONG overlay cài đặt — pane
    // "content" giờ nằm ngoài overlay, luôn hiện mặc định, không được để
    // switchTab() (dùng cho tab Giọng đọc/Thay thế) vô tình ẩn nó đi.
    tabs: Array.from(document.querySelectorAll("#readerSettingsOverlay [data-reader-tab]")),
    panes: Array.from(document.querySelectorAll("#readerSettingsOverlay [data-reader-pane]")),
    sourceTitle: $("readerSourceTitle"),
    sourceMeta: $("readerSourceMeta"),
    text: $("readerText"),
    charCount: $("readerCharCount"),
    sentenceCount: $("readerSentenceCount"),
    webBtn: $("readerExtractWebBtn"),
    fileBtn: $("readerOpenTxtBtn"),
    fileInput: $("readerFileInput"),
    cleanBtn: $("readerCleanBtn"),
    cleanMirror: $("readerCleanBtnMirror"),
    copyBtn: $("readerCopyBtn"),
    saveBtn: $("readerSaveTxtBtn"),
    clearBtn: $("readerClearBtn"),
    // Việc (v0.23.25): gộp 3 nút Đọc/Tạm dừng/Dừng thành 1 nút duy nhất —
    // nhãn/hành vi đổi theo trạng thái (▶ Đọc → ⏸ Tạm dừng → ▶ Tiếp tục).
    // Nút "Dừng" riêng bị bỏ hẳn theo yêu cầu; muốn dừng hẳn thì tạm dừng
    // rồi đóng bảng (đọc vẫn tiếp tục nền như trước nếu không tạm dừng).
    playPauseBtn: $("readerPlayPauseBtn"),
    settingsBtn: $("readerSettingsBtn"),
    settingsCloseBtn: $("readerSettingsCloseBtn"),
    settingsOverlay: $("readerSettingsOverlay"),
    prevBtn: $("readerPrevBtn"),
    nextBtn: $("readerNextBtn"),
    voice: $("readerVoiceSelect"),
    rate: $("readerRate"),
    rateValue: $("readerRateValue"),
    pitch: $("readerPitch"),
    pitchValue: $("readerPitchValue"),
    volume: $("readerVolume"),
    volumeValue: $("readerVolumeValue"),
    pauseMs: $("readerPauseMs"),
    pauseMsValue: $("readerPauseMsValue"),
    repeat: $("readerRepeat"),
    readCursor: $("readerReadCursor"),
    caseSensitive: $("readerCaseSensitive"),
    replacementRules: $("readerReplacementRules"),
    skipWords: $("readerSkipWords"),
    previewRules: $("readerPreviewRules"),
    applyRules: $("readerApplyRules"),
    rulesStatus: $("readerRulesStatus"),
    progressFill: $("readerProgressFill"),
    progressText: $("readerProgressText"),
    status: $("readerStatus"),
    nowReading: $("readerNowReading"),
    nativeHint: $("readerNativeHint"),
    ttsEngineName: $("readerTtsEngineName"),
    ttsEngineMeta: $("readerTtsEngineMeta"),
    voiceSourceNote: $("readerVoiceSourceNote"),
    refreshVoicesBtn: $("readerRefreshVoicesBtn"),
    openTtsSettingsBtn: $("readerOpenTtsSettingsBtn")
  };

  if (!ui.panel || !ui.toolbarBtn) return;

  const state = {
    voices: [],
    segments: [],
    segmentIndex: 0,
    utterance: null,
    speaking: false,
    paused: false,
    timer: 0,
    sourceUrl: "",
    sourceTitle: "Văn bản chưa đặt tên",
    nativeMode: false,
    voiceMode: "web",
    nativeEngine: null
  };

  const TOP_UI_PATTERNS = [
    /^Mê\s*Truyện\s*Chữ$/i, /^Danh\s+sách$/i, /^Thể\s+loại$/i,
    /^Tìm\s+kiếm\s+truyện/i, /^Chương\s+trước$/i, /^Chương\s+tiếp$/i,
    /^《?\s*Chương\s+trước/i, /^Chương\s+tiếp\s*》?$/i, /^Tải\s+Ebook$/i,
    /^Menu$/i, /^Trang chủ$/i, /^Toggle navigation$/i
  ];

  const DROP_LINE_PATTERNS = [
    /^Mê\s*Truyện\s*Chữ$/i, /^Danh\s+sách$/i, /^Thể\s+loại$/i,
    /^Tìm\s+kiếm\s+truyện/i, /^Chương\s+trước$/i, /^Chương\s+tiếp$/i,
    /^《?\s*Chương\s+trước\s*$/i, /^Chương\s+tiếp\s*》?$/i,
    /^Tải\s+Ebook$/i, /^Báo\s+lỗi$/i, /^Bình\s+luận$/i,
    /^Đăng\s+nhập$/i, /^Đăng\s+ký$/i, /^Quảng\s+cáo$/i,
    /^Activate\s+Windows/i, /^Go\s+to\s+Settings/i, /^SIÊU/i,
    /^\d+\s+chương$/i, /^©/i, /^Menu$/i, /^Trang chủ$/i,
    /^Toggle navigation$/i, /^Truyện\s+hot$/i, /^TRUYỆN\s+HOT$/i,
    /^Có thể bạn thích$/i, /^Truyện đề cử$/i, /^Danh sách truyện$/i
  ];

  const END_PATTERNS = [
    /^《?\s*Chương\s+trước/i, /^Chương\s+tiếp\s*》?$/i,
    /^Bạn có thể dùng phím/i, /^Báo lỗi$/i, /^Bình luận$/i,
    /^TRUYỆN HOT/i, /^Truyện hot/i, /^Danh sách truyện/i,
    /^Truyện đề cử/i, /^Có thể bạn thích/i, /^Mời bạn đọc/i,
    /^Các truyện/i, /^Đọc truyện/i
  ];

  const CONTENT_SELECTORS = [
    "#chapter-content", ".chapter-content", ".chapter-c", "#chapter-c",
    ".chapter-body", ".chapter-text", ".content-chapter", ".reading-content",
    ".entry-content", "[itemprop='articleBody']", "article", "main", ".chapter", ".container"
  ];

  function normalizeLine(value) {
    return String(value || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t\f\v]+/g, " ")
      .trim();
  }

  function cleanText(raw, title = "") {
    const lines = String(raw || "")
      .replace(/\r\n?/g, "\n")
      .replace(/\u00a0/g, " ")
      .split("\n")
      .map(normalizeLine);

    const cleanTitle = normalizeLine(title).toLowerCase();
    const out = [];
    let contentChars = 0;
    let contentStarted = false;

    for (const line of lines) {
      if (!line) {
        if (contentStarted && out.length && out[out.length - 1] !== "") out.push("");
        continue;
      }
      if (DROP_LINE_PATTERNS.some(pattern => pattern.test(line))) continue;
      if (cleanTitle && line.toLowerCase() === cleanTitle) continue;

      if (!contentStarted) {
        const likely = /[a-zA-ZÀ-ỹ]/.test(line) && line.length >= 12 &&
          !TOP_UI_PATTERNS.some(pattern => pattern.test(line));
        if (!likely) continue;
        contentStarted = true;
      }

      if (contentChars > 800 && END_PATTERNS.some(pattern => pattern.test(line))) break;
      out.push(line);
      contentChars += line.length + 1;
    }

    return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
  }

  function visibleText(element) {
    if (!element) return "";
    const clone = element.cloneNode(true);
    clone.querySelectorAll([
      "script", "style", "noscript", "svg", "canvas", "iframe", "video", "audio",
      "nav", "header", "footer", "aside", "form", "button", "input", "textarea", "select",
      ".ads", ".ad", ".advertisement", ".banner", ".comment", ".comments", ".breadcrumb",
      ".navbar", ".menu", ".search", ".recommend", ".hot", ".popup", ".modal",
      "[class*='ads' i]", "[id*='ads' i]", "[class*='advert' i]", "[id*='advert' i]",
      "[class*='comment' i]", "[id*='comment' i]", "[class*='banner' i]", "[id*='banner' i]",
      "#storyReaderPanel", "#storyReaderBackdrop"
    ].join(",")).forEach(node => node.remove());
    return clone.innerText || clone.textContent || "";
  }

  function scoreText(text) {
    const value = String(text || "");
    const lines = value.split("\n").map(normalizeLine).filter(Boolean);
    const longLines = lines.filter(line => line.length >= 30).length;
    const punctuation = (value.match(/[,.!?;:。！？…]/g) || []).length;
    const noise = (value.match(/Chương trước|Chương tiếp|Tải Ebook|Bình luận|TRUYỆN HOT|Danh sách|Thể loại/gi) || []).length;
    return value.length + longLines * 220 + punctuation * 5 - noise * 1200;
  }

  function guessDocumentTitle(doc) {
    const chapterSelectors = [
      ".chapter-title", ".chapter-name", "[class*='chapter'][class*='title']",
      "[class*='chapter'][class*='name']", "h1", "h2"
    ];
    for (const selector of chapterSelectors) {
      for (const node of Array.from(doc.querySelectorAll(selector))) {
        const text = normalizeLine(node.innerText || node.textContent);
        if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(text)) return text;
      }
    }
    return normalizeLine(doc.title || "") || "Trang đang mở";
  }

  function extractFromDocument(doc = document) {
    const title = guessDocumentTitle(doc);
    let best = "";
    let bestScore = -Infinity;

    for (const selector of CONTENT_SELECTORS) {
      for (const element of Array.from(doc.querySelectorAll(selector))) {
        if (element.closest?.("#storyReaderPanel")) continue;
        const cleaned = cleanText(visibleText(element), title);
        const score = scoreText(cleaned);
        if (cleaned.length > 120 && score > bestScore) {
          best = cleaned;
          bestScore = score;
        }
      }
    }

    if (!best || best.length < 300) {
      const paragraphs = Array.from(doc.querySelectorAll("p"))
        .map(node => normalizeLine(node.innerText || node.textContent))
        .filter(line => line.length > 20 && !DROP_LINE_PATTERNS.some(pattern => pattern.test(line)));
      if (paragraphs.length) best = cleanText(paragraphs.join("\n\n"), title);
    }

    if (!best) best = cleanText(visibleText(doc.body), title);
    return { title, text: best, url: location.href };
  }

  function parseNativePayload(raw) {
    if (raw == null) return null;
    if (typeof raw === "object") return raw;
    const text = String(raw);
    try { return JSON.parse(text); } catch { return { text }; }
  }

  function tryNativeExtraction() {
    const bridges = [
      window.LqlqPageBridge,
      window.ShieldPageBridge,
      window.LqlqAndroid,
      window.ShieldAndroid,
      window.Android,
      window.NativeBridge
    ].filter(Boolean);
    const names = ["extractReadableText", "getReadablePageText", "getCurrentPageText", "extractCurrentChapter"];
    for (const bridge of bridges) {
      for (const name of names) {
        if (typeof bridge[name] !== "function") continue;
        try {
          const payload = parseNativePayload(bridge[name]());
          if (payload && (payload.text || payload.body || payload.html)) return payload;
        } catch (error) {
          console.warn("Shield Reader native extraction failed", error);
        }
      }
    }
    return null;
  }

  function setPayload(payload, fallbackTitle = "Trang đang mở") {
    const data = parseNativePayload(payload) || {};
    let text = data.text || data.body || "";
    // Việc (v0.23.24 — Vấn đề 5): nếu payload đã có sẵn text/body (tới từ
    // LqlqPageBridge.extractCurrentChapter() hoặc bộ máy Chapter Clipper
    // dùng chung), nội dung ĐÃ được thuật toán tốt (extractByPageBoundaries/
    // extractByContainerFallback) làm sạch rồi. Trước đây code này CHẠY LẠI
    // cleanText() (thuật toán yếu hơn, tuned cho HTML thô) đè lên kết quả đã
    // sạch — có thể cắt cụt/xáo nội dung đúng do ngưỡng "contentStarted"/800
    // ký tự/END_PATTERNS không khớp với input đã sạch. Chỉ dùng cleanText()
    // khi PHẢI tự trích xuất từ data.html thô (không có sẵn text/body).
    const alreadyClean = Boolean(text);
    if (!text && data.html) {
      const doc = new DOMParser().parseFromString(String(data.html), "text/html");
      const extracted = extractFromDocument(doc); // extractFromDocument() đã tự cleanText() bên trong
      text = extracted.text;
      data.title ||= extracted.title;
    } else if (!alreadyClean) {
      text = cleanText(text, data.title || fallbackTitle);
    }
    text = String(text || "").trim();
    if (!text) {
      notify("Không tìm thấy phần nội dung truyện trong trang này.", "warning");
      return false;
    }
    ui.text.value = text;
    state.sourceTitle = normalizeLine(data.chapterTitle || data.title || fallbackTitle) || fallbackTitle;
    state.sourceUrl = data.url || location.href;
    ui.sourceTitle.textContent = state.sourceTitle;
    ui.sourceMeta.textContent = `${text.length.toLocaleString("vi-VN")} ký tự · Đã loại menu, quảng cáo và nút chuyển chương`;
    updateTextStats();
    persistDraft();
    notify("Đã lấy và làm sạch nội dung trang.", "success");
    return true;
  }

  function trySharedChapterExtraction() {
    const extractor =
      window.LqlqChapterExtractor
      || window.LqlqChapterClipper
      || window.ShieldChapterClipper;

    if (typeof extractor?.extractCurrentChapter !== "function") {
      return null;
    }

    try {
      return parseNativePayload(extractor.extractCurrentChapter());
    } catch (error) {
      console.warn("Shared Chapter Clipper extraction failed", error);
      return {
        error: "Bộ máy Chapter Clipper gặp lỗi khi lấy nội dung."
      };
    }
  }

  function extractCurrentWebPage() {
    stopSpeech();

    const sharedPayload = trySharedChapterExtraction();

    if (
      sharedPayload
      && !sharedPayload.error
      && setPayload(
        sharedPayload,
        sharedPayload.chapterTitle || "Chương đang mở"
      )
    ) {
      state.nativeMode = true;
      ui.nativeHint.textContent =
        "Đã lấy chương bằng cùng bộ máy với Chapter Clipper.";
      return;
    }

    const nativePayload = tryNativeExtraction();
    if (nativePayload && setPayload(nativePayload)) {
      state.nativeMode = true;
      ui.nativeHint.textContent =
        "Đã lấy nội dung qua cầu nối Android WebView.";
      return;
    }

    const isPrototypePage =
      /lqlq Browser/i.test(document.title)
      && location.protocol === "file:";

    if (isPrototypePage) {
      const message =
        sharedPayload?.error
        || "Bản HTML chưa truy cập được DOM của tab web thật. "
        + "Khi làm APK, hãy nối active WebView qua LqlqPageBridge.";

      ui.nativeHint.textContent = message;
      notify(message, "warning");
      return;
    }

    const payload = extractFromDocument(document);

    if (setPayload(payload, "Trang đang mở")) {
      ui.nativeHint.textContent =
        "Đã lấy nội dung trực tiếp bằng bộ lọc dự phòng của Reader.";
      return;
    }

    const message =
      sharedPayload?.error
      || "Không tìm thấy nội dung chương trong trang đang mở.";

    ui.nativeHint.textContent = message;
    notify(message, "warning");
  }

  function readTxtFile(file) {
    if (!file) return;
    if (file.size > 30 * 1024 * 1024) {
      notify("Tệp quá lớn. Bản mẫu giới hạn 30 MB để tránh treo trình duyệt.", "warning");
      return;
    }
    const reader = new FileReader();
    reader.onerror = () => notify("Không thể đọc tệp TXT này.", "error");
    reader.onload = () => {
      stopSpeech();
      let text = String(reader.result || "").replace(/^\uFEFF/, "");
      ui.text.value = text;
      state.sourceTitle = file.name.replace(/\.txt$/i, "") || "Tệp TXT";
      state.sourceUrl = "";
      ui.sourceTitle.textContent = state.sourceTitle;
      ui.sourceMeta.textContent = `${file.name} · ${(file.size / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} KB`;
      updateTextStats();
      persistDraft();
      notify("Đã mở tệp TXT.", "success");
    };
    reader.readAsText(file, "utf-8");
  }

  function splitLongSegment(segment, maxLength = 420) {
    if (segment.text.length <= maxLength) return [segment];
    const results = [];
    let localStart = 0;
    const source = segment.text;
    while (localStart < source.length) {
      let end = Math.min(source.length, localStart + maxLength);
      if (end < source.length) {
        const windowText = source.slice(localStart, end);
        const preferred = Math.max(windowText.lastIndexOf(". "), windowText.lastIndexOf(", "), windowText.lastIndexOf("; "), windowText.lastIndexOf(" "));
        if (preferred > maxLength * 0.55) end = localStart + preferred + 1;
      }
      const chunkText = source.slice(localStart, end).trim();
      if (chunkText) {
        const trimOffset = source.slice(localStart, end).indexOf(chunkText);
        results.push({ text: chunkText, start: segment.start + localStart + Math.max(0, trimOffset), end: segment.start + end });
      }
      localStart = end;
    }
    return results;
  }

  function segmentText(text) {
    const source = String(text || "");
    const result = [];
    if (window.Intl?.Segmenter) {
      const segmenter = new Intl.Segmenter("vi", { granularity: "sentence" });
      for (const part of segmenter.segment(source)) {
        const cleaned = String(part.segment || "").trim();
        if (!cleaned) continue;
        const start = part.index + part.segment.indexOf(cleaned);
        result.push(...splitLongSegment({ text: cleaned, start, end: start + cleaned.length }));
      }
    } else {
      const regex = /[^.!?…\n]+(?:[.!?…]+|\n+|$)/g;
      let match;
      while ((match = regex.exec(source))) {
        const cleaned = match[0].trim();
        if (!cleaned) continue;
        const start = match.index + match[0].indexOf(cleaned);
        result.push(...splitLongSegment({ text: cleaned, start, end: start + cleaned.length }));
      }
    }
    return result;
  }

  function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function parseRules() {
    const replacements = [];
    String(ui.replacementRules.value || "").split(/\r?\n/).forEach(rawLine => {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) return;
      let divider = line.indexOf("=>");
      let size = 2;
      if (divider < 0) { divider = line.indexOf("="); size = 1; }
      if (divider < 0) { divider = line.indexOf("\t"); size = 1; }
      if (divider < 0) return;
      const from = line.slice(0, divider).trim();
      const to = line.slice(divider + size).trim();
      if (from) replacements.push({ from, to });
    });
    const skips = String(ui.skipWords.value || "")
      .split(/[\n,;]/)
      .map(item => item.trim())
      .filter(Boolean);
    return { replacements, skips };
  }

  function transformForSpeech(input) {
    let output = String(input || "");
    const { replacements, skips } = parseRules();
    const flags = ui.caseSensitive.checked ? "g" : "gi";
    for (const rule of replacements) {
      output = output.replace(new RegExp(escapeRegExp(rule.from), flags), rule.to);
    }
    for (const word of skips) {
      output = output.replace(new RegExp(escapeRegExp(word), flags), " ");
    }
    return output.replace(/\s{2,}/g, " ").trim();
  }

  function nativeTtsBridge() {
    return window.LqlqTtsBridge
      || window.LqlqAndroid
      || window.ShieldAndroid
      || window.Android
      || window.NativeBridge
      || null;
  }

  function parseBridgeJson(raw, fallback = null) {
    if (raw == null || raw === "") return fallback;
    if (typeof raw === "object") return raw;
    try {
      return JSON.parse(String(raw));
    } catch {
      return fallback;
    }
  }

  function normalizeNativeVoice(raw, index) {
    const value = raw && typeof raw === "object" ? raw : {};
    const id = String(
      value.id
      || value.voiceId
      || value.name
      || value.voiceURI
      || `android-voice-${index}`
    );
    const name = String(value.name || value.label || id);
    const lang = String(
      value.lang
      || value.language
      || value.locale
      || "vi-VN"
    ).replace("_", "-");

    return {
      source: "native",
      id,
      voiceURI: id,
      name,
      lang,
      default: Boolean(value.default || value.isDefault),
      networkRequired: Boolean(
        value.networkRequired || value.isNetworkConnectionRequired
      ),
      engine: String(value.engine || value.engineName || ""),
      packageName: String(value.packageName || value.enginePackage || ""),
      raw: value
    };
  }

  function getNativeEngineInfo() {
    const bridge = nativeTtsBridge();
    if (!bridge) return null;

    const methodNames = [
      "getTtsEngineInfo",
      "getTextToSpeechEngineInfo",
      "getSpeechEngineInfo"
    ];

    for (const methodName of methodNames) {
      if (typeof bridge[methodName] !== "function") continue;

      try {
        const parsed = parseBridgeJson(bridge[methodName](), null);
        if (parsed) return parsed;
      } catch (error) {
        console.warn("Không đọc được thông tin Android TTS", error);
      }
    }

    return null;
  }

  function getNativeVoices() {
    const bridge = nativeTtsBridge();
    if (!bridge) return [];

    const methodNames = [
      "getTtsVoices",
      "getTextToSpeechVoices",
      "listTtsVoices",
      "listVoices"
    ];

    for (const methodName of methodNames) {
      if (typeof bridge[methodName] !== "function") continue;

      try {
        const parsed = parseBridgeJson(bridge[methodName](), []);
        const list = Array.isArray(parsed)
          ? parsed
          : Array.isArray(parsed?.voices)
            ? parsed.voices
            : [];

        if (list.length) {
          return list.map(normalizeNativeVoice);
        }
      } catch (error) {
        console.warn("Không đọc được danh sách giọng Android TTS", error);
      }
    }

    return [];
  }

  function isVietnameseVoice(voice) {
    const lang = String(voice?.lang || "").toLowerCase();
    const name = String(voice?.name || "").toLowerCase();
    return lang.startsWith("vi")
      || name.includes("tiếng việt")
      || name.includes("vietnam");
  }

  function isGoogleVoice(voice) {
    const text = [
      voice?.name,
      voice?.engine,
      voice?.packageName,
      voice?.raw?.features
    ].join(" ").toLowerCase();

    return text.includes("google")
      || text.includes("com.google.android.tts")
      || text.includes("speech services");
  }

  function voiceSort(left, right) {
    const leftVietnamese = isVietnameseVoice(left) ? 0 : 1;
    const rightVietnamese = isVietnameseVoice(right) ? 0 : 1;
    if (leftVietnamese !== rightVietnamese) {
      return leftVietnamese - rightVietnamese;
    }

    const leftGoogle = isGoogleVoice(left) ? 0 : 1;
    const rightGoogle = isGoogleVoice(right) ? 0 : 1;
    if (leftGoogle !== rightGoogle) {
      return leftGoogle - rightGoogle;
    }

    const leftOffline = left?.networkRequired ? 1 : 0;
    const rightOffline = right?.networkRequired ? 1 : 0;
    if (leftOffline !== rightOffline) {
      return leftOffline - rightOffline;
    }

    return String(left?.name || "").localeCompare(
      String(right?.name || ""),
      "vi"
    );
  }

  function selectedVoiceEntry() {
    return state.voices.find(voice =>
      voice.id === ui.voice.value
      || voice.voiceURI === ui.voice.value
      || voice.name === ui.voice.value
    ) || null;
  }

  function selectedVoice() {
    const entry = selectedVoiceEntry();
    return entry?.source === "web" ? entry.browserVoice : null;
  }

  function mappedRate() {
    const value = Number(ui.rate.value) || 0;
    return Math.max(0.1, Math.min(10, value / 100));
  }

  function mappedPitch() {
    const value = Number(ui.pitch.value) || 0;
    return Math.max(0.01, Math.min(2, value / 100));
  }

  function canUseNativeTts() {
    const bridge = nativeTtsBridge();
    return Boolean(
      bridge
      && (
        typeof bridge.speakText === "function"
        || typeof bridge.speak === "function"
      )
    );
  }

  function speakNative(text) {
    const bridge = nativeTtsBridge();
    if (!bridge) return false;

    const speakMethod =
      typeof bridge.speakText === "function"
        ? bridge.speakText.bind(bridge)
        : typeof bridge.speak === "function"
          ? bridge.speak.bind(bridge)
          : null;

    if (!speakMethod) return false;

    try {
      const voice = selectedVoiceEntry();
      const settings = {
        voice: voice?.id || ui.voice.value,
        voiceName: voice?.name || "",
        language: voice?.lang || "vi-VN",
        preferGoogleEngine: true,
        enginePackage: "com.google.android.tts",
        rate: Number(ui.rate.value),
        pitch: Number(ui.pitch.value),
        volume: Number(ui.volume.value),
        utteranceId: `lqlq-reader-${state.segmentIndex}`
      };

      speakMethod(text, JSON.stringify(settings));
      return true;
    } catch (error) {
      console.warn("Android Google TTS failed", error);
      return false;
    }
  }

  function updateProgress() {
    const total = state.segments.length;
    const current = total ? Math.min(total, state.segmentIndex + 1) : 0;
    const percent = total ? Math.round((current / total) * 100) : 0;
    ui.progressFill.style.width = `${percent}%`;
    ui.progressText.textContent = total ? `Câu ${current}/${total} · ${percent}%` : "Chưa bắt đầu";
  }

  function highlightSegment(segment) {
    if (!segment) return;
    ui.text.focus({ preventScroll: true });
    try {
      ui.text.setSelectionRange(segment.start, segment.end);
      const before = ui.text.value.slice(0, segment.start);
      const line = before.split("\n").length;
      const lineHeight = parseFloat(getComputedStyle(ui.text).lineHeight) || 24;
      ui.text.scrollTop = Math.max(0, (line - 3) * lineHeight);
    } catch {}
    ui.nowReading.textContent = segment.text;
    updateProgress();
  }

  // Việc (v0.23.25): nhãn nút gộp phản ánh đúng 1 trong 3 trạng thái.
  function updatePlayPauseLabel() {
    if (!state.speaking) {
      ui.playPauseBtn.textContent = "▶ Đọc";
    } else if (state.paused) {
      ui.playPauseBtn.textContent = "▶ Tiếp tục";
    } else {
      ui.playPauseBtn.textContent = "⏸ Tạm dừng";
    }
  }

  function finishSpeech() {
    state.speaking = false;
    state.paused = false;
    state.utterance = null;
    clearTimeout(state.timer);
    ui.status.textContent = "Đã đọc xong";
    ui.status.dataset.state = "done";
    updatePlayPauseLabel();
    if (ui.repeat.checked && state.segments.length) {
      state.segmentIndex = 0;
      state.timer = setTimeout(speakCurrentSegment, Number(ui.pauseMs.value) || 0);
    }
  }

  function onSegmentEnd() {
    if (!state.speaking) return;
    state.segmentIndex += 1;
    if (state.segmentIndex >= state.segments.length) {
      finishSpeech();
      return;
    }
    const delay = Number(ui.pauseMs.value) || 0;
    clearTimeout(state.timer);
    state.timer = setTimeout(speakCurrentSegment, delay);
  }

  function speakCurrentSegment() {
    if (!state.speaking || state.paused) return;
    const segment = state.segments[state.segmentIndex];
    if (!segment) { finishSpeech(); return; }
    highlightSegment(segment);
    const speechText = transformForSpeech(segment.text);
    if (!speechText) { onSegmentEnd(); return; }

    ui.status.textContent = "Đang đọc";
    ui.status.dataset.state = "speaking";

    if (canUseNativeTts() && speakNative(speechText)) {
      // Native APK should call ShieldReader.onNativeUtteranceDone(id) after completion.
      // The timeout is only a safe prototype fallback.
      const estimated = Math.max(500, speechText.length * (52 / mappedRate()));
      clearTimeout(state.timer);
      state.timer = setTimeout(onSegmentEnd, Math.min(estimated * 2 + 8000, 120000));
      return;
    }

    if (!("speechSynthesis" in window)) {
      notify("Trình duyệt này không hỗ trợ Web Speech. Bản APK cần nối Android TextToSpeech.", "error");
      stopSpeech();
      return;
    }

    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(speechText);
    const voice = selectedVoice();
    if (voice) utterance.voice = voice;
    utterance.lang = voice?.lang || "vi-VN";
    utterance.rate = mappedRate();
    utterance.pitch = mappedPitch();
    utterance.volume = Math.max(0, Math.min(1, Number(ui.volume.value) / 100));
    utterance.onend = onSegmentEnd;
    utterance.onerror = event => {
      if (event.error === "canceled" || event.error === "interrupted") return;
      notify(`Giọng đọc gặp lỗi: ${event.error || "không xác định"}`, "error");
      stopSpeech();
    };
    state.utterance = utterance;
    window.speechSynthesis.speak(utterance);
  }

  function startSpeech() {
    const text = ui.text.value;
    if (!text.trim()) {
      notify("Chưa có văn bản để đọc.", "warning");
      return;
    }
    stopSpeech(false);
    state.segments = segmentText(text);
    if (!state.segments.length) {
      notify("Không tìm thấy câu hợp lệ để đọc.", "warning");
      return;
    }
    const cursor = ui.readCursor.checked ? ui.text.selectionStart : 0;
    state.segmentIndex = Math.max(0, state.segments.findIndex(segment => cursor >= segment.start && cursor < segment.end));
    if (state.segmentIndex < 0) state.segmentIndex = 0;
    state.speaking = true;
    state.paused = false;
    updatePlayPauseLabel();
    speakCurrentSegment();
  }

  function pauseOrResume() {
    if (!state.speaking) return;
    const bridge = nativeTtsBridge();
    if (!state.paused) {
      state.paused = true;
      clearTimeout(state.timer);
      if (bridge && typeof bridge.pauseTts === "function") {
        try { bridge.pauseTts(); } catch {}
      } else {
        window.speechSynthesis?.pause();
      }
      ui.status.textContent = "Đã tạm dừng";
      ui.status.dataset.state = "paused";
      updatePlayPauseLabel();
    } else {
      state.paused = false;
      if (bridge && typeof bridge.resumeTts === "function") {
        try { bridge.resumeTts(); } catch {}
      } else if (window.speechSynthesis?.paused) {
        window.speechSynthesis.resume();
      } else {
        speakCurrentSegment();
      }
      ui.status.textContent = "Đang đọc";
      ui.status.dataset.state = "speaking";
      updatePlayPauseLabel();
    }
  }

  // Nút gộp: chưa đọc → bắt đầu; đang đọc → tạm dừng; đang tạm dừng → tiếp tục.
  function handlePlayPauseClick() {
    if (!state.speaking) startSpeech();
    else pauseOrResume();
  }

  function stopSpeech(resetProgress = true) {
    state.speaking = false;
    state.paused = false;
    state.utterance = null;
    clearTimeout(state.timer);
    try { window.speechSynthesis?.cancel(); } catch {}
    const bridge = nativeTtsBridge();
    if (bridge && typeof bridge.stopTts === "function") {
      try { bridge.stopTts(); } catch {}
    }
    ui.status.textContent = "Sẵn sàng";
    ui.status.dataset.state = "idle";
    updatePlayPauseLabel();
    if (resetProgress) {
      state.segmentIndex = 0;
      updateProgress();
      ui.nowReading.textContent = "Chưa có câu đang đọc.";
    }
  }

  function jumpSegment(direction) {
    if (!state.segments.length) state.segments = segmentText(ui.text.value);
    if (!state.segments.length) return;
    const wasSpeaking = state.speaking;
    state.segmentIndex = Math.max(0, Math.min(state.segments.length - 1, state.segmentIndex + direction));
    highlightSegment(state.segments[state.segmentIndex]);
    if (wasSpeaking) {
      try { window.speechSynthesis?.cancel(); } catch {}
      clearTimeout(state.timer);
      state.speaking = true;
      state.paused = false;
      speakCurrentSegment();
    }
  }

  function updateTtsEngineUi(nativeVoices = []) {
    const engine = getNativeEngineInfo();
    state.nativeEngine = engine;

    const engineText = [
      engine?.name,
      engine?.label,
      engine?.packageName,
      engine?.package
    ].filter(Boolean).join(" ");

    const googleEngine = /google|com\.google\.android\.tts|speech services/i
      .test(engineText);

    if (nativeVoices.length || canUseNativeTts()) {
      state.voiceMode = "native";
      ui.ttsEngineName.textContent = googleEngine || !engineText
        ? "Nhận dạng và tổng hợp giọng nói của Google"
        : String(engine?.label || engine?.name || "Bộ tổng hợp giọng nói Android");

      const vietnameseCount = nativeVoices.filter(isVietnameseVoice).length;
      ui.ttsEngineMeta.textContent = vietnameseCount
        ? `${vietnameseCount} giọng tiếng Việt khả dụng trên điện thoại`
        : "Đang dùng bộ TTS mặc định của Android; hãy cài dữ liệu giọng tiếng Việt.";

      ui.voiceSourceNote.textContent =
        "Danh sách này được lấy trực tiếp từ Android TextToSpeech trong APK.";
      return;
    }

    state.voiceMode = "web";
    ui.ttsEngineName.textContent = "Giọng của trình duyệt hiện tại";
    ui.ttsEngineMeta.textContent =
      "Bản HTML không thể đọc trực tiếp ứng dụng TTS của Android.";
    ui.voiceSourceNote.textContent =
      "Khi đóng gói APK, lqlq Browser sẽ ưu tiên com.google.android.tts và giọng vi-VN đã cài.";
  }

  function populateVoices() {
    const previous = ui.voice.dataset.savedVoice || ui.voice.value;
    const nativeVoices = getNativeVoices().sort(voiceSort);

    if (nativeVoices.length) {
      state.voices = nativeVoices;
      ui.voice.innerHTML = "";

      for (const voice of state.voices) {
        const option = document.createElement("option");
        option.value = voice.id;

        const tags = [];
        if (isGoogleVoice(voice)) tags.push("Google");
        if (isVietnameseVoice(voice)) tags.push("Tiếng Việt");
        if (voice.default) tags.push("mặc định");
        if (voice.networkRequired) tags.push("cần mạng");
        else tags.push("offline");

        option.textContent =
          `${voice.name} (${voice.lang}) · ${tags.join(" · ")}`;
        ui.voice.append(option);
      }

      const preferred =
        state.voices.find(voice => voice.id === previous)
        || state.voices.find(voice =>
          isVietnameseVoice(voice)
          && isGoogleVoice(voice)
          && !voice.networkRequired
        )
        || state.voices.find(voice =>
          isVietnameseVoice(voice) && isGoogleVoice(voice)
        )
        || state.voices.find(voice => isVietnameseVoice(voice))
        || state.voices[0];

      ui.voice.value = preferred.id;
      updateTtsEngineUi(nativeVoices);
      persistSettings();
      return;
    }

    const browserVoices = window.speechSynthesis?.getVoices?.() || [];
    state.voices = browserVoices
      .map((voice, index) => ({
        source: "web",
        id: voice.voiceURI || voice.name || `browser-voice-${index}`,
        voiceURI: voice.voiceURI || voice.name,
        name: voice.name,
        lang: String(voice.lang || "").replace("_", "-"),
        default: Boolean(voice.default),
        networkRequired: false,
        engine: /google/i.test(voice.name) ? "Google Web Speech" : "Web Speech",
        packageName: "",
        browserVoice: voice,
        raw: voice
      }))
      .sort(voiceSort);

    ui.voice.innerHTML = "";

    if (!state.voices.length) {
      const option = document.createElement("option");
      option.value = "";
      option.textContent = canUseNativeTts()
        ? "Android / Google TTS — chờ danh sách giọng"
        : "Tiếng Việt (sẽ dùng Android Google TTS trong APK)";
      ui.voice.append(option);
      updateTtsEngineUi([]);
      return;
    }

    for (const voice of state.voices) {
      const option = document.createElement("option");
      option.value = voice.id;

      const sourceLabel = isGoogleVoice(voice) ? "Google" : "Trình duyệt";
      option.textContent =
        `${sourceLabel} · ${voice.name} (${voice.lang})`
        + `${voice.default ? " · mặc định" : ""}`;
      ui.voice.append(option);
    }

    const preferred =
      state.voices.find(voice => voice.id === previous)
      || state.voices.find(voice =>
        isVietnameseVoice(voice) && isGoogleVoice(voice)
      )
      || state.voices.find(voice => isVietnameseVoice(voice))
      || state.voices[0];

    ui.voice.value = preferred.id;
    updateTtsEngineUi([]);
  }

  function refreshVoiceList() {
    populateVoices();
    const vietnameseCount = state.voices.filter(isVietnameseVoice).length;

    notify(
      vietnameseCount
        ? `Đã tìm thấy ${vietnameseCount} giọng tiếng Việt.`
        : "Chưa tìm thấy giọng tiếng Việt. Hãy mở cài đặt TTS và tải dữ liệu giọng.",
      vietnameseCount ? "success" : "warning"
    );
  }

  function openAndroidTtsSettings() {
    const bridge = nativeTtsBridge();
    const methodNames = [
      "openTtsSettings",
      "openTextToSpeechSettings",
      "openSpeechSettings",
      "openTtsEngineSettings"
    ];

    for (const methodName of methodNames) {
      if (typeof bridge?.[methodName] !== "function") continue;

      try {
        bridge[methodName]();
        return;
      } catch (error) {
        console.warn("Không mở được cài đặt TTS Android", error);
      }
    }

    notify(
      "Bản HTML không thể mở cài đặt Android. Trong APK, nút này sẽ mở mục Nhận dạng và tổng hợp giọng nói.",
      "warning"
    );
  }

  function setRangeVisual(control) {
    const min = Number(control.min) || 0;
    const max = Number(control.max) || 100;
    const value = Number(control.value) || 0;
    const percent = max > min ? ((value - min) / (max - min)) * 100 : 0;
    control.style.setProperty("--reader-range", `${Math.max(0, Math.min(100, percent))}%`);
  }

  function updateRangeLabels() {
    ui.rateValue.textContent = `${Math.round(Number(ui.rate.value))}`;
    ui.pitchValue.textContent = `${Math.round(Number(ui.pitch.value))}`;
    ui.volumeValue.textContent = `${Math.round(Number(ui.volume.value))}%`;
    ui.pauseMsValue.textContent = `${Math.round(Number(ui.pauseMs.value))} ms`;
    [ui.rate, ui.pitch, ui.volume, ui.pauseMs].forEach(setRangeVisual);
  }

  function quickSentenceCount(text) {
    const value = String(text || "").trim();
    if (!value) return 0;
    let count = 0;
    let inSentence = false;
    for (let i = 0; i < value.length; i += 1) {
      const char = value[i];
      if (!/\s/.test(char)) inSentence = true;
      if (inSentence && /[.!?…\n]/.test(char)) {
        count += 1;
        inSentence = false;
      }
    }
    return count + (inSentence ? 1 : 0);
  }

  function updateTextStats() {
    const text = ui.text.value;
    ui.charCount.textContent = `${text.length.toLocaleString("vi-VN")} ký tự`;
    const count = quickSentenceCount(text);
    ui.sentenceCount.textContent = `${count.toLocaleString("vi-VN")} câu`;
  }

  function persistSettings() {
    const settings = {
      voice: ui.voice.value,
      rate: Math.round(Number(ui.rate.value)),
      pitch: Math.round(Number(ui.pitch.value)),
      volume: Math.round(Number(ui.volume.value)),
      pauseMs: Math.round(Number(ui.pauseMs.value)),
      repeat: ui.repeat.checked,
      readCursor: ui.readCursor.checked,
      caseSensitive: ui.caseSensitive.checked,
      replacementRules: ui.replacementRules.value,
      skipWords: ui.skipWords.value
    };
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  }

  function persistDraft() {
    const value = ui.text.value;
    if (value.length <= MAX_SAVED_TEXT) localStorage.setItem(TEXT_KEY, value);
    else localStorage.removeItem(TEXT_KEY);
  }

  function loadSaved() {
    try {
      const settings = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
      if (Number.isFinite(settings.rate)) ui.rate.value = String(Math.max(0, Math.min(300, settings.rate)));
      if (Number.isFinite(settings.pitch)) ui.pitch.value = String(Math.max(0, Math.min(300, settings.pitch)));
      if (Number.isFinite(settings.volume)) ui.volume.value = String(Math.max(0, Math.min(100, settings.volume)));
      if (Number.isFinite(settings.pauseMs)) ui.pauseMs.value = String(Math.max(0, Math.min(3000, settings.pauseMs)));
      ui.repeat.checked = Boolean(settings.repeat);
      ui.readCursor.checked = settings.readCursor !== false;
      ui.caseSensitive.checked = Boolean(settings.caseSensitive);
      ui.replacementRules.value = settings.replacementRules || "";
      ui.skipWords.value = settings.skipWords || "";
      ui.voice.dataset.savedVoice = settings.voice || "";
    } catch {}
    const draft = localStorage.getItem(TEXT_KEY);
    if (draft) {
      ui.text.value = draft;
      ui.sourceTitle.textContent = "Bản nháp gần nhất";
      ui.sourceMeta.textContent = "Đã khôi phục từ bộ nhớ cục bộ";
    }
    updateRangeLabels();
    updateTextStats();
  }

  function saveTxt() {
    const text = ui.text.value;
    if (!text.trim()) { notify("Không có nội dung để lưu.", "warning"); return; }
    const safe = (state.sourceTitle || "truyen")
      .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
      .replace(/[đĐ]/g, "d")
      .replace(/[^a-zA-Z0-9._ -]+/g, " ")
      .trim().replace(/\s+/g, "_").slice(0, 100) || "truyen";
    const blob = new Blob(["\uFEFF", text], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${safe}.txt`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify("Đã tạo tệp TXT.", "success");
  }

  async function copyText() {
    if (!ui.text.value) return;
    try {
      await navigator.clipboard.writeText(ui.text.value);
      notify("Đã sao chép văn bản.", "success");
    } catch {
      ui.text.select();
      document.execCommand("copy");
      notify("Đã sao chép văn bản.", "success");
    }
  }

  function previewTransform() {
    const { replacements, skips } = parseRules();
    const original = ui.text.value;
    const transformed = transformForSpeech(original);
    const changed = original === transformed ? 0 : Math.abs(original.length - transformed.length);
    ui.rulesStatus.textContent = `${replacements.length} quy tắc thay thế · ${skips.length} mục bỏ qua · Bản đọc dài ${transformed.length.toLocaleString("vi-VN")} ký tự${changed ? ` · lệch ${changed.toLocaleString("vi-VN")} ký tự` : ""}.`;
    notify("Đã kiểm tra quy tắc đọc.", "success");
  }

  function applyRulesToText() {
    if (!ui.text.value) return;
    const transformed = transformForSpeech(ui.text.value);
    if (transformed === ui.text.value) {
      notify("Không có thay đổi nào được áp dụng.", "warning");
      return;
    }
    ui.text.value = transformed;
    updateTextStats();
    persistDraft();
    notify("Đã áp dụng thay thế vào văn bản.", "success");
  }

  function openPanel() {
    ui.panel.classList.add("open");
    ui.backdrop.classList.remove("hidden");
    ui.panel.setAttribute("aria-hidden", "false");
    ui.toolbarBtn.classList.add("active");
    document.body.classList.add("reader-panel-open");
  }

  function closePanel() {
    ui.panel.classList.remove("open");
    ui.backdrop.classList.add("hidden");
    ui.panel.setAttribute("aria-hidden", "true");
    ui.toolbarBtn.classList.remove("active");
    document.body.classList.remove("reader-panel-open");
  }

  function notify(message, type = "info") {
    if (typeof window.toast === "function") {
      window.toast(message);
      return;
    }
    ui.status.textContent = message;
    ui.status.dataset.state = type;
  }

  function switchTab(name) {
    ui.tabs.forEach(tab => tab.classList.toggle("active", tab.dataset.readerTab === name));
    ui.panes.forEach(pane => pane.classList.toggle("active", pane.dataset.readerPane === name));
  }

  ui.toolbarBtn.addEventListener("click", event => {
    event.stopPropagation();
    ui.panel.classList.contains("open") ? closePanel() : openPanel();
  });
  ui.close.addEventListener("click", closePanel);
  ui.backdrop.addEventListener("click", closePanel);
  ui.tabs.forEach(tab => tab.addEventListener("click", () => switchTab(tab.dataset.readerTab)));
  ui.webBtn.addEventListener("click", extractCurrentWebPage);
  ui.fileBtn.addEventListener("click", () => ui.fileInput.click());
  ui.fileInput.addEventListener("change", event => readTxtFile(event.target.files?.[0]));
  ui.cleanBtn.addEventListener("click", () => {
    const cleaned = cleanText(ui.text.value, state.sourceTitle);
    if (!cleaned) { notify("Không còn nội dung sau khi làm sạch.", "warning"); return; }
    ui.text.value = cleaned;
    updateTextStats();
    persistDraft();
    notify("Đã loại dòng thừa, menu và nút chuyển chương.", "success");
  });
  ui.cleanMirror.addEventListener("click", () => ui.cleanBtn.click());
  ui.copyBtn.addEventListener("click", copyText);
  ui.saveBtn.addEventListener("click", saveTxt);
  ui.clearBtn.addEventListener("click", () => {
    stopSpeech();
    ui.text.value = "";
    state.sourceTitle = "Văn bản chưa đặt tên";
    ui.sourceTitle.textContent = state.sourceTitle;
    ui.sourceMeta.textContent = "Chưa có nội dung";
    updateTextStats();
    persistDraft();
  });
  ui.playPauseBtn.addEventListener("click", handlePlayPauseClick);
  ui.settingsBtn?.addEventListener("click", () => ui.settingsOverlay?.classList.remove("hidden"));
  ui.settingsCloseBtn?.addEventListener("click", () => ui.settingsOverlay?.classList.add("hidden"));
  ui.settingsOverlay?.addEventListener("click", event => {
    if (event.target === ui.settingsOverlay) ui.settingsOverlay.classList.add("hidden");
  });
  ui.prevBtn.addEventListener("click", () => jumpSegment(-1));
  ui.nextBtn.addEventListener("click", () => jumpSegment(1));
  ui.previewRules.addEventListener("click", previewTransform);
  ui.applyRules.addEventListener("click", applyRulesToText);

  let textStatsTimer = 0;
  ui.text.addEventListener("input", () => {
    clearTimeout(textStatsTimer);
    textStatsTimer = setTimeout(() => {
      updateTextStats();
      persistDraft();
    }, 180);
  });

  [ui.rate, ui.pitch, ui.volume, ui.pauseMs].forEach(control => control.addEventListener("input", () => {
    control.value = String(Math.round(Number(control.value)));
    updateRangeLabels();
    persistSettings();
  }));
  ui.refreshVoicesBtn?.addEventListener("click", refreshVoiceList);
  ui.openTtsSettingsBtn?.addEventListener("click", openAndroidTtsSettings);

  [ui.voice, ui.repeat, ui.readCursor, ui.caseSensitive, ui.replacementRules, ui.skipWords].forEach(control => {
    control.addEventListener("change", persistSettings);
    control.addEventListener("input", persistSettings);
  });

  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && ui.panel.classList.contains("open")) closePanel();
    if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "r") {
      event.preventDefault();
      openPanel();
    }
  });

  if ("speechSynthesis" in window) {
    window.speechSynthesis.onvoiceschanged = () => {
      const saved = ui.voice.dataset.savedVoice || ui.voice.value;
      populateVoices();
      if (
        saved
        && state.voices.some(voice =>
          voice.id === saved
          || voice.voiceURI === saved
          || voice.name === saved
        )
      ) {
        ui.voice.value = saved;
      }
    };
  }

  window.ShieldReader = {
    open: openPanel,
    close: closePanel,
    setPagePayload(payload) {
      openPanel();
      switchTab("content");
      return setPayload(payload);
    },
    cleanText,
    extractFromDocument,
    extractCurrentChapter() {
      return trySharedChapterExtraction();
    },
    refreshVoices: populateVoices,
    onNativeVoicesChanged() { populateVoices(); },
    onNativeUtteranceDone() {
      if (state.speaking && !state.paused) onSegmentEnd();
    },
    onNativeUtteranceError(message) {
      notify(message || "Android Google TTS gặp lỗi.", "error");
      stopSpeech();
    }
  };

  window.LqlqReader = window.ShieldReader;

  loadSaved();
  populateVoices();
  updateProgress();
})();
