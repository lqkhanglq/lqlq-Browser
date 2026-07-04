(() => {
  "use strict";

  const STORAGE_KEY = "shieldChapterClipperV15";

  const defaults = {
    chapters: [],
    batchSize: 10,
    batchNo: 1,
    autoExport: true,
    popupBlocker: true,
    compact: false,
    autoRun: false,
    autoRemaining: 0,
    autoTotal: 0,
    autoCount: 100,
    autoLastUrl: ""
  };

  const refs = {
    button: document.getElementById("chapterClipperToolbarBtn"),
    panel: document.getElementById("chapterClipperPanel"),
    body: document.getElementById("chapterClipperBody"),
    close: document.getElementById("chapterClipperClose"),
    minimize: document.getElementById("chapterClipperMinimize"),
    autoCard: document.getElementById("chapterClipperAutoCard"),
    runBadge: document.getElementById("chapterClipperRunBadge"),
    autoCount: document.getElementById("chapterClipperAutoCount"),
    autoStart: document.getElementById("chapterClipperAutoStart"),
    autoStop: document.getElementById("chapterClipperAutoStop"),
    save: document.getElementById("chapterClipperSave"),
    saveNext: document.getElementById("chapterClipperSaveNext"),
    test: document.getElementById("chapterClipperTest"),
    export: document.getElementById("chapterClipperExport"),
    undo: document.getElementById("chapterClipperUndo"),
    clear: document.getElementById("chapterClipperClear"),
    batchSize: document.getElementById("chapterClipperBatchSize"),
    autoExport: document.getElementById("chapterClipperAutoExport"),
    popupBlocker: document.getElementById("chapterClipperPopupBlocker"),
    batchBadge: document.getElementById("chapterClipperBatchBadge"),
    status: document.getElementById("chapterClipperStatus"),
    bridgeNote: document.getElementById("chapterClipperBridgeNote")
  };

  const DROP_PATTERNS = [
    /^Mê\s*Truyện\s*Chữ$/i,
    /^Danh\s+sách$/i,
    /^Thể\s+loại$/i,
    /^Tìm\s+kiếm\s+truyện/i,
    /^Chương\s+trước$/i,
    /^Chương\s+tiếp$/i,
    /^《?\s*Chương\s+trước/i,
    /^Chương\s+tiếp\s*》?$/i,
    /^Tải\s+Ebook$/i,
    /^Báo\s+lỗi$/i,
    /^Bình\s+luận$/i,
    /^Activate\s+Windows/i,
    /^Go\s+to\s+Settings/i,
    /^Menu$/i,
    /^Trang chủ$/i
  ];

  const END_PATTERNS = [
    /^《?\s*Chương\s+trước/i,
    /^Chương\s+tiếp\s*》?$/i,
    /^Báo lỗi$/i,
    /^Bình luận$/i,
    /^TRUYỆN HOT/i,
    /^Truyện hot/i,
    /^Danh sách truyện/i,
    /^Truyện đề cử/i,
    /^Có thể bạn thích/i
  ];

  let state = readState();
  let autoTimer = 0;

  function readState() {
    try {
      return {
        ...defaults,
        ...JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}")
      };
    } catch {
      return { ...defaults };
    }
  }

  function saveStateLocal() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  function notify(message) {
    if (typeof toast === "function") {
      toast(message);
      return;
    }
    console.log(message);
  }

  function normalizeLine(value) {
    return String(value || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t\f\v]+/g, " ")
      .trim();
  }

  function sanitizeFilename(name) {
    return String(name || "truyen")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[đĐ]/g, "d")
      .replace(/[^a-zA-Z0-9._ -]+/g, " ")
      .trim()
      .replace(/\s+/g, "_")
      .slice(0, 120) || "truyen";
  }

  function pageBridges() {
    return [
      window.LqlqPageBridge,
      window.ShieldPageBridge,
      window.LqlqAndroid,
      window.ShieldAndroid,
      window.Android,
      window.NativeBridge
    ].filter(Boolean);
  }

  function parseBridgePayload(raw, fallback = null) {
    if (raw == null || raw === "") return fallback;
    if (typeof raw === "object") return raw;

    const text = String(raw);
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }

  function activePageUrl() {
    for (const bridge of pageBridges()) {
      for (const methodName of [
        "getUrl",
        "getCurrentPageUrl",
        "getCurrentUrl",
        "currentUrl"
      ]) {
        if (typeof bridge?.[methodName] !== "function") continue;

        try {
          const value = bridge[methodName]();
          if (value) return String(value);
        } catch {}
      }
    }

    if (typeof activeTab === "function") {
      return activeTab()?.url || location.href;
    }

    return location.href;
  }

  function documentFromHtml(raw) {
    const parsed = parseBridgePayload(raw, "");
    const html = typeof parsed === "string"
      ? parsed
      : String(parsed?.html || parsed?.pageHtml || "");

    if (!html.trim()) return null;

    try {
      return new DOMParser().parseFromString(html, "text/html");
    } catch {
      return null;
    }
  }

  function resolvePageDocument() {
    for (const bridge of [
      window.LqlqPageBridge,
      window.ShieldPageBridge
    ].filter(Boolean)) {
      if (typeof bridge.getDocument === "function") {
        try {
          const result = bridge.getDocument();

          if (result?.body) {
            return result;
          }

          const parsedDocument = documentFromHtml(result);
          if (parsedDocument?.body) {
            return parsedDocument;
          }
        } catch {}
      }

      for (const methodName of [
        "getPageHtml",
        "getCurrentPageHtml",
        "getHtml",
        "currentPageHtml"
      ]) {
        if (typeof bridge[methodName] !== "function") continue;

        try {
          const parsedDocument = documentFromHtml(bridge[methodName]());
          if (parsedDocument?.body) {
            return parsedDocument;
          }
        } catch {}
      }
    }

    const frame = document.querySelector(
      "iframe[data-shield-page-frame], iframe[data-lqlq-page-frame], "
      + "iframe#pageFrame, iframe#webContentFrame"
    );

    try {
      if (frame?.contentDocument?.body) {
        return frame.contentDocument;
      }
    } catch {}

    const isBrowserShell =
      /lqlq Browser/i.test(document.title)
      && ["file:", "http:", "https:"].includes(location.protocol);

    if (!isBrowserShell && document.body) {
      return document;
    }

    return null;
  }

  function hasPageBridge() {
    if (resolvePageDocument()) return true;

    return pageBridges().some(bridge =>
      [
        "extractCurrentChapter",
        "getCurrentChapter",
        "extractReadableText",
        "getReadablePageText",
        "getCurrentPageText"
      ].some(methodName => typeof bridge?.[methodName] === "function")
    );
  }

  function normalizeChapterPayload(payload, fallbackUrl = activePageUrl()) {
    const data = parseBridgePayload(payload, null);
    if (!data || typeof data !== "object") return null;

    const body = String(
      data.body
      || data.text
      || data.content
      || data.chapterText
      || ""
    ).trim();

    if (!body) return null;

    const url = String(data.url || data.pageUrl || fallbackUrl || "");
    const chapterTitle = normalizeLine(
      data.chapterTitle || data.title || "Chương đang mở"
    );

    return {
      storyTitle: normalizeLine(
        data.storyTitle || data.bookTitle || data.novelTitle || "Truyện"
      ),
      chapterTitle,
      chapterNumber:
        Number.isFinite(Number(data.chapterNumber))
          ? Number(data.chapterNumber)
          : getChapterNumber(chapterTitle, url),
      body,
      text: body,
      url,
      savedAt: data.savedAt || new Date().toISOString(),
      charCount: Number(data.charCount || body.length)
    };
  }

  function tryNativeChapterPayload() {
    const methodNames = [
      "extractCurrentChapter",
      "getCurrentChapter",
      "extractReadableText",
      "getReadablePageText",
      "getCurrentPageText"
    ];

    for (const bridge of pageBridges()) {
      for (const methodName of methodNames) {
        if (typeof bridge?.[methodName] !== "function") continue;

        try {
          const payload = normalizeChapterPayload(
            bridge[methodName](),
            activePageUrl()
          );

          if (payload?.body) {
            return payload;
          }
        } catch (error) {
          console.warn(
            `Chapter extractor bridge ${methodName} failed`,
            error
          );
        }
      }
    }

    return null;
  }

  function openPanel() {
    if (typeof closeMenus === "function") {
      closeMenus();
    }

    refs.panel.classList.remove("hidden");
    refs.panel.setAttribute("aria-hidden", "false");
    refs.button?.classList.add("active");
    renderStatus();
  }

  function closePanel() {
    refs.panel.classList.add("hidden");
    refs.panel.setAttribute("aria-hidden", "true");
    refs.button?.classList.remove("active");
  }

  function togglePanel() {
    if (refs.panel.classList.contains("hidden")) {
      openPanel();
    } else {
      closePanel();
    }
  }

  function toggleCompact() {
    state.compact = !state.compact;
    refs.panel.classList.toggle("compact", state.compact);
    refs.minimize.textContent = state.compact ? "+" : "−";
    saveStateLocal();
  }

  function getStoryTitle(doc) {
    const breadcrumb = Array.from(
      doc.querySelectorAll(
        ".breadcrumb a, .breadcrumb span, [class*='breadcrumb'] a, [class*='breadcrumb'] span"
      )
    )
      .map(node => normalizeLine(node.innerText || node.textContent))
      .filter(Boolean)
      .filter(text =>
        !/^Mê\s*Truyện\s*Chữ$/i.test(text)
        && !/^Chương\s+\d+/i.test(text)
      );

    if (breadcrumb.length) {
      return breadcrumb[breadcrumb.length - 1];
    }

    for (const heading of Array.from(doc.querySelectorAll("h1"))) {
      const text = normalizeLine(heading.innerText || heading.textContent);
      if (text && !/^Chương\s+\d+/i.test(text)) {
        return text;
      }
    }

    return normalizeLine(doc.title?.split("|")[0] || "Truyện");
  }

  function getChapterTitle(doc, url) {
    const selectors = [
      "h2",
      "h1",
      ".chapter-title",
      ".chapter-name",
      "[class*='chapter'][class*='title']",
      "[class*='chapter'][class*='name']",
      ".title"
    ];

    for (const selector of selectors) {
      for (const node of Array.from(doc.querySelectorAll(selector))) {
        const text = normalizeLine(node.innerText || node.textContent);
        if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(text)) {
          return text;
        }
      }
    }

    const titleText = normalizeLine(doc.title?.split("|")[0] || "");
    const titleMatch =
      titleText.match(/(Chương|Chuong|Chapter)\s*\d+[^-|]*/i);

    if (titleMatch) {
      return normalizeLine(titleMatch[0]);
    }

    const urlMatch = String(url || "").match(/chuong[-_/]?(\d+)/i);
    return urlMatch
      ? `Chương ${Number.parseInt(urlMatch[1], 10)}`
      : "Chương chưa rõ số";
  }

  function getChapterNumber(title, url) {
    const titleMatch = String(title || "")
      .match(/(?:Chương|Chuong|Chapter)\s*0*(\d+)/i);

    if (titleMatch) {
      return Number.parseInt(titleMatch[1], 10);
    }

    const urlMatch = String(url || "").match(/chuong[-_/]?(\d+)/i);
    return urlMatch ? Number.parseInt(urlMatch[1], 10) : null;
  }

  function bodyLines(doc) {
    if (!doc?.body) return [];

    const ignoredTags = new Set([
      "SCRIPT", "STYLE", "NOSCRIPT", "SVG", "CANVAS",
      "IFRAME", "VIDEO", "AUDIO", "TEMPLATE"
    ]);

    const blockTags = new Set([
      "ADDRESS", "ARTICLE", "ASIDE", "BLOCKQUOTE", "BUTTON",
      "DD", "DETAILS", "DIALOG", "DIV", "DL", "DT",
      "FIELDSET", "FIGCAPTION", "FIGURE", "FOOTER", "FORM",
      "H1", "H2", "H3", "H4", "H5", "H6", "HEADER",
      "HR", "LI", "MAIN", "NAV", "OL", "P", "PRE",
      "SECTION", "SUMMARY", "TABLE", "TBODY", "TD", "TFOOT",
      "TH", "THEAD", "TR", "UL"
    ]);

    const chunks = [];

    function pushBreak() {
      if (chunks[chunks.length - 1] !== "\n") {
        chunks.push("\n");
      }
    }

    function walk(node) {
      if (!node) return;

      if (node.nodeType === Node.TEXT_NODE) {
        chunks.push(String(node.nodeValue || ""));
        return;
      }

      if (node.nodeType !== Node.ELEMENT_NODE) return;
      if (ignoredTags.has(node.tagName)) return;

      if (node.tagName === "BR") {
        pushBreak();
        return;
      }

      const isBlock = blockTags.has(node.tagName);
      if (isBlock) pushBreak();

      for (const child of node.childNodes) {
        walk(child);
      }

      if (isBlock) pushBreak();
    }

    walk(doc.body);

    return chunks
      .join("")
      .replace(/\r\n/g, "\n")
      .replace(/\r/g, "\n")
      .replace(/\u00a0/g, " ")
      .split("\n")
      .map(normalizeLine);
  }

  function cleanLines(lines, chapterTitle, storyTitle) {
    const clean = [];
    const chapterLower = normalizeLine(chapterTitle).toLowerCase();
    const storyLower = normalizeLine(storyTitle).toLowerCase();

    lines.forEach(rawLine => {
      const line = normalizeLine(rawLine);

      if (!line) {
        if (clean.length && clean[clean.length - 1] !== "") {
          clean.push("");
        }
        return;
      }

      if (DROP_PATTERNS.some(pattern => pattern.test(line))) return;
      if (chapterLower && line.toLowerCase() === chapterLower) return;
      if (storyLower && line.toLowerCase() === storyLower) return;
      clean.push(line);
    });

    return clean.join("\n").replace(/\n{3,}/g, "\n\n").trim();
  }

  function extractChapterFromDocument(
    doc,
    url = activePageUrl()
  ) {
    if (!doc?.body) {
      return {
        error: "Không nhận được nội dung DOM của trang đang mở."
      };
    }

    const storyTitle = getStoryTitle(doc);
    const chapterTitle = getChapterTitle(doc, url);
    const chapterNumber = getChapterNumber(chapterTitle, url);
    const lines = bodyLines(doc);

    let titleIndex = -1;
    const titleLower = normalizeLine(chapterTitle).toLowerCase();

    for (
      let index = 0;
      index < Math.min(lines.length, 220);
      index += 1
    ) {
      if (normalizeLine(lines[index]).toLowerCase() === titleLower) {
        titleIndex = index;
      }
    }

    let start = Math.max(0, titleIndex + 1);

    for (
      let index = start;
      index < Math.min(lines.length, start + 150);
      index += 1
    ) {
      const line = normalizeLine(lines[index]);

      if (
        line.length >= 12
        && /[a-zA-ZÀ-ỹ]/.test(line)
        && !DROP_PATTERNS.some(pattern => pattern.test(line))
        && line.toLowerCase() !== normalizeLine(storyTitle).toLowerCase()
      ) {
        start = index;
        break;
      }
    }

    const kept = [];
    let charCount = 0;

    for (let index = start; index < lines.length; index += 1) {
      const line = normalizeLine(lines[index]);

      if (
        charCount > 800
        && line
        && END_PATTERNS.some(pattern => pattern.test(line))
      ) {
        break;
      }

      kept.push(lines[index]);
      charCount += line ? line.length + 1 : 1;
    }

    const body = cleanLines(kept, chapterTitle, storyTitle);

    if (body.length < 300) {
      return {
        error:
          "Không lấy được nội dung đủ dài. Hãy chờ trang tải xong rồi thử lại."
      };
    }

    return {
      storyTitle,
      chapterTitle,
      chapterNumber,
      body,
      text: body,
      url,
      savedAt: new Date().toISOString(),
      charCount: body.length
    };
  }

  function extractCurrentChapter() {
    const nativePayload = tryNativeChapterPayload();
    if (nativePayload?.body) {
      return nativePayload;
    }

    const doc = resolvePageDocument();
    if (!doc) {
      return {
        error:
          "Chưa nhận được trang web thật. Bản APK cần nối active WebView qua "
          + "LqlqPageBridge hoặc trả JSON từ extractCurrentChapter()."
      };
    }

    return extractChapterFromDocument(doc, activePageUrl());
  }

  function sortedChapters() {
    return [...state.chapters].sort((left, right) => {
      const leftNumber = Number.isFinite(left.chapterNumber)
        ? left.chapterNumber
        : 999999;
      const rightNumber = Number.isFinite(right.chapterNumber)
        ? right.chapterNumber
        : 999999;

      if (leftNumber !== rightNumber) {
        return leftNumber - rightNumber;
      }

      return String(left.savedAt || "")
        .localeCompare(String(right.savedAt || ""));
    });
  }

  function formatTxt() {
    return sortedChapters()
      .map(chapter => {
        const title =
          chapter.chapterTitle
          || (chapter.chapterNumber
            ? `Chương ${chapter.chapterNumber}`
            : "Chương");

        return `${title}\n\n${chapter.body || ""}`.trim();
      })
      .filter(Boolean)
      .join("\n\n\n") + "\n";
  }

  function batchFilename() {
    const chapters = sortedChapters();
    const first = chapters[0] || {};
    const story = sanitizeFilename(
      first.storyTitle || "truyen"
    );
    const numbers = chapters
      .map(chapter => chapter.chapterNumber)
      .filter(Number.isFinite);

    const suffix = numbers.length
      ? `chuong_${Math.min(...numbers)}_${Math.max(...numbers)}`
      : `lo_${state.batchNo || 1}`;

    return `${story}_${suffix}.txt`;
  }

  function downloadText(filename, text) {
    const blob = new Blob(
      ["\ufeff", text],
      { type: "text/plain;charset=utf-8" }
    );
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");

    anchor.href = url;
    anchor.download = filename;
    anchor.target = "_self";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    setTimeout(() => URL.revokeObjectURL(url), 2500);
  }

  function readSettings() {
    state.batchSize = Math.max(
      1,
      Number.parseInt(refs.batchSize.value || "10", 10)
    );
    state.autoCount = Math.max(
      1,
      Number.parseInt(refs.autoCount.value || "100", 10)
    );
    state.autoExport = refs.autoExport.checked;
    state.popupBlocker = refs.popupBlocker.checked;
    saveStateLocal();
  }

  function commitChapter(chapter) {
    const duplicate = state.chapters.findIndex(item =>
      (
        chapter.chapterNumber != null
        && item.chapterNumber === chapter.chapterNumber
        && item.storyTitle === chapter.storyTitle
      )
      || item.url === chapter.url
    );

    if (duplicate >= 0) {
      state.chapters[duplicate] = chapter;
      notify(`Đã cập nhật ${chapter.chapterTitle}.`);
    } else {
      state.chapters.push(chapter);
      notify(`Đã lưu ${chapter.chapterTitle}.`);
    }

    saveStateLocal();
    renderStatus();

    if (state.autoExport && state.chapters.length >= state.batchSize) {
      exportTxt(true);
    }
  }

  function saveCurrent({ next = false } = {}) {
    readSettings();
    const chapter = extractCurrentChapter();

    if (chapter.error) {
      notify(chapter.error);
      refs.status.textContent = chapter.error;
      return false;
    }

    commitChapter(chapter);

    if (next) {
      setTimeout(goNextChapter, 280);
    }

    return true;
  }

  function findNextChapterUrl() {
    const doc = resolvePageDocument();
    if (!doc) return "";

    for (const element of Array.from(doc.querySelectorAll("a, button"))) {
      const text = normalizeLine(
        element.innerText
        || element.textContent
        || element.getAttribute("title")
        || ""
      );
      const href =
        element.href
        || element.getAttribute("data-href")
        || "";

      if (
        /Chương\s*tiếp|Chuong\s*tiep|Next/i.test(text)
        && href
        && !/^javascript:/i.test(href)
      ) {
        return href;
      }
    }

    return doc.querySelector("link[rel='next']")?.href || "";
  }

  function navigatePage(url) {
    if (!url) return false;

    if (window.ShieldPageBridge?.navigate) {
      try {
        window.ShieldPageBridge.navigate(url);
        return true;
      } catch {}
    }

    if (typeof navigate === "function") {
      navigate(url);
      return true;
    }

    return false;
  }

  function goNextChapter() {
    const url = findNextChapterUrl();

    if (!url) {
      notify("Không tìm thấy nút Chương tiếp trên trang hiện tại.");
      return false;
    }

    if (!navigatePage(url)) {
      notify("Chưa có cầu nối điều hướng trang thật.");
      return false;
    }

    return true;
  }

  function exportTxt(clearAfter = false) {
    readSettings();

    if (!state.chapters.length) {
      notify("Chưa có chương nào để xuất TXT.");
      return false;
    }

    const filename = batchFilename();
    downloadText(filename, formatTxt());

    if (clearAfter) {
      state.chapters = [];
      state.batchNo = (state.batchNo || 1) + 1;
      saveStateLocal();
      renderStatus();
    }

    notify(`Đã xuất ${filename}.`);
    return true;
  }

  function testExtract() {
    const chapter = extractCurrentChapter();

    if (chapter.error) {
      refs.status.textContent = chapter.error;
      notify(chapter.error);
      return;
    }

    const first = chapter.body.slice(0, 220);
    const last = chapter.body.slice(-220);

    refs.status.textContent =
      `${chapter.chapterTitle}\n`
      + `${chapter.charCount.toLocaleString("vi-VN")} ký tự\n\n`
      + `ĐOẠN ĐẦU:\n${first}\n\n`
      + `ĐOẠN CUỐI:\n${last}`;
  }

  function undoLast() {
    if (!state.chapters.length) {
      notify("Chưa có chương nào để bỏ.");
      return;
    }

    const removed = state.chapters.pop();
    saveStateLocal();
    renderStatus();
    notify(`Đã bỏ ${removed.chapterTitle || "chương cuối"}.`);
  }

  function clearBatch() {
    if (!state.chapters.length) return;
    if (!confirm("Xóa toàn bộ chương trong lô hiện tại?")) return;

    state.chapters = [];
    saveStateLocal();
    renderStatus();
    notify("Đã xóa lô hiện tại.");
  }

  function stopAutoRun(message = "Đã dừng tự động.") {
    clearTimeout(autoTimer);
    state.autoRun = false;
    state.autoRemaining = 0;
    state.autoLastUrl = "";
    saveStateLocal();
    renderStatus();
    notify(message);
  }

  function autoStep() {
    if (!state.autoRun || state.autoRemaining <= 0) {
      stopAutoRun("Đã hoàn tất lượt tự động.");
      return;
    }

    const chapter = extractCurrentChapter();

    if (chapter.error) {
      stopAutoRun(chapter.error);
      return;
    }

    if (state.autoLastUrl !== chapter.url) {
      commitChapter(chapter);
      state.autoRemaining = Math.max(0, state.autoRemaining - 1);
      state.autoLastUrl = chapter.url;
      saveStateLocal();
      renderStatus();
    }

    if (state.autoRemaining <= 0) {
      state.autoRun = false;
      saveStateLocal();

      if (state.autoExport && state.chapters.length) {
        exportTxt(true);
      }

      renderStatus();
      notify("Tự động đã hoàn tất.");
      return;
    }

    const nextUrl = findNextChapterUrl();

    if (!nextUrl) {
      stopAutoRun("Không tìm thấy Chương tiếp.");
      return;
    }

    const delay = 1000 + Math.floor(Math.random() * 1500);

    autoTimer = setTimeout(() => {
      if (!state.autoRun) return;

      if (!navigatePage(nextUrl)) {
        stopAutoRun("Chưa có cầu nối WebView để chuyển chương.");
        return;
      }

      autoTimer = setTimeout(autoStep, 1800);
    }, delay);
  }

  function startAutoRun() {
    readSettings();

    if (!hasPageBridge()) {
      notify(
        "Bản HTML chưa có trang web thật. Dùng tiện ích Chrome gốc hoặc chờ bản APK WebView."
      );
      refs.status.textContent =
        "Không thể chạy tự động trong trang mô phỏng.\n"
        + "Tiện ích gốc đã được đặt trong thư mục tools/.";
      return;
    }

    state.autoRun = true;
    state.autoTotal = state.autoCount;
    state.autoRemaining = state.autoCount;
    state.autoLastUrl = "";
    saveStateLocal();
    renderStatus();
    notify(`Bắt đầu tự động ${state.autoCount} chương.`);
    autoStep();
  }

  function renderStatus() {
    refs.batchSize.value = String(state.batchSize || 10);
    refs.autoCount.value = String(state.autoCount || 100);
    refs.autoExport.checked = state.autoExport !== false;
    refs.popupBlocker.checked = state.popupBlocker !== false;

    refs.panel.classList.toggle("compact", Boolean(state.compact));
    refs.minimize.textContent = state.compact ? "+" : "−";

    refs.autoCard.classList.toggle("running", Boolean(state.autoRun));
    refs.autoStart.disabled = Boolean(state.autoRun);
    refs.autoStop.disabled = !state.autoRun;
    refs.runBadge.textContent = state.autoRun
      ? `${state.autoTotal - state.autoRemaining}/${state.autoTotal}`
      : "Sẵn sàng";

    refs.batchBadge.textContent =
      `${state.chapters.length}/${state.batchSize || 10}`;

    const recent = sortedChapters()
      .slice(-7)
      .map((chapter, index) =>
        `${index + 1}. ${chapter.chapterTitle || "Chương"} `
        + `(${Number(chapter.charCount || chapter.body?.length || 0)
          .toLocaleString("vi-VN")} ký tự)`
      )
      .join("\n");

    const autoLine = state.autoRun
      ? `TỰ ĐỘNG: còn ${state.autoRemaining}/${state.autoTotal} chương\n`
      : "";

    refs.status.textContent =
      autoLine
      + `Lô hiện tại: ${state.chapters.length}/${state.batchSize || 10} chương\n`
      + (recent ? `Gần nhất:\n${recent}` : "Chưa lưu chương nào.");

    refs.bridgeNote.textContent = hasPageBridge()
      ? "Đã nhận diện được trang nội dung thật. Chapter Clipper có thể lấy nội dung."
      : "Bản HTML đang dùng giao diện mô phỏng. Khi nối WebView/APK, nút này sẽ lấy nội dung từ trang đang mở.";
  }

  const previousHandleAction = handleAction;
  handleAction = function handleChapterClipperAction(action) {
    if (action === "chapter-clipper") {
      openPanel();
      return;
    }

    previousHandleAction(action);
  };

  refs.button?.addEventListener("click", togglePanel);
  refs.close.addEventListener("click", closePanel);
  refs.minimize.addEventListener("click", toggleCompact);
  refs.autoStart.addEventListener("click", startAutoRun);
  refs.autoStop.addEventListener("click", () => stopAutoRun());
  refs.save.addEventListener("click", () => saveCurrent({ next: false }));
  refs.saveNext.addEventListener("click", () => saveCurrent({ next: true }));
  refs.test.addEventListener("click", testExtract);
  refs.export.addEventListener("click", () => exportTxt(false));
  refs.undo.addEventListener("click", undoLast);
  refs.clear.addEventListener("click", clearBatch);

  [
    refs.autoCount,
    refs.batchSize,
    refs.autoExport,
    refs.popupBlocker
  ].forEach(control => {
    control.addEventListener("change", () => {
      readSettings();
      renderStatus();
    });
  });

  document.addEventListener("keydown", event => {
    if (!(event.ctrlKey && event.shiftKey)) return;

    const key = event.key.toLowerCase();

    if (key === "s") {
      event.preventDefault();
      saveCurrent({ next: false });
    } else if (key === "n") {
      event.preventDefault();
      saveCurrent({ next: true });
    } else if (key === "e") {
      event.preventDefault();
      exportTxt(false);
    } else if (key === "a") {
      event.preventDefault();
      state.autoRun ? stopAutoRun() : startAutoRun();
    }
  }, true);

  renderStatus();

  window.LqlqChapterExtractor = {
    extractCurrentChapter,
    extractFromDocument: extractChapterFromDocument,
    resolvePageDocument,
    activePageUrl,
    hasPageBridge
  };

  window.ShieldChapterClipper = {
    open: openPanel,
    close: closePanel,
    extractCurrentChapter,
    extractFromDocument: extractChapterFromDocument,
    exportTxt,
    state
  };

  window.LqlqChapterClipper = window.ShieldChapterClipper;
})();