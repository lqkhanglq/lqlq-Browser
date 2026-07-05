/*
 * chapter-clipper-content.js — lqlq Browser v0.23.4
 *
 * Bản chuyển thể từ content-script gốc "Chapter Clipper TXT Plus v1.3"
 * (C:\Users\Admin\Downloads\chapter_clipper_txt_plus_v1_3_auto\content.js).
 * KHÔNG còn phụ thuộc chrome.storage/chrome.tabs (API extension) — dùng
 * localStorage của chính trang đang mở (mỗi trang/site có lô riêng, giống
 * hành vi content-script gốc lưu theo tab). Không còn hard-code cứng vào
 * metruyenchuvn.com để dùng được trên nhiều trang đọc truyện khác nhau —
 * các mảng lọc (TOP_UI_PATTERNS/DROP_LINE_PATTERNS/END_PATTERNS/AD_SELECTORS)
 * vẫn giữ nguyên vì đều là các cụm chữ tiếng Việt phổ biến trên web đọc
 * truyện, dùng làm bộ lọc mặc định chứ không bắt buộc đúng 100% mọi site.
 *
 * Được nạp bằng native.injectChapterClipper() (MainActivity.kt) thẳng vào
 * WebView của trang web thật qua evaluateJavascript() — không còn dùng
 * kiến trúc PageBridge round-trip cũ (v15-chapter-clipper.js, đã tắt).
 */
(() => {
  if (window.__chapterClipperTxtPlusLoaded) {
    // Đã tiêm rồi (bấm nút "Bật Chapter Clipper" lần 2) → chỉ bật/tắt panel.
    const existing = document.getElementById("cc-panel");
    if (existing) {
      const hidden = existing.style.display === "none";
      existing.style.setProperty("display", hidden ? "" : "none", "important");
    }
    return;
  }
  window.__chapterClipperTxtPlusLoaded = true;

  const STORAGE_KEY = "lqlqChapterClipperTxtStateV2";

  const DEFAULT_STATE = {
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

  const TOP_UI_PATTERNS = [
    /^Mê\s*Truyện\s*Chữ$/i,
    /^Danh\s+sách$/i,
    /^Thể\s+loại$/i,
    /^Tìm\s+kiếm\s+truyện/i,
    /^Nữ\s+Đế/i,
    /^Chương\s+\d+/i,
    /^Chương\s+trước$/i,
    /^Chương\s+tiếp$/i,
    /^《?\s*Chương\s+trước/i,
    /^Chương\s+tiếp\s*》?$/i,
    /^Tải\s+Ebook$/i,
    /^Menu$/i,
    /^Trang chủ$/i,
    /^Toggle navigation$/i
  ];

  const DROP_LINE_PATTERNS = [
    /^Mê\s*Truyện\s*Chữ$/i,
    /^Danh\s+sách$/i,
    /^Thể\s+loại$/i,
    /^Tìm\s+kiếm\s+truyện/i,
    /^Chương\s+trước$/i,
    /^Chương\s+tiếp$/i,
    /^《?\s*Chương\s+trước\s*$/i,
    /^Chương\s+tiếp\s*》?$/i,
    /^《\s*Chương\s+trước$/i,
    /^Tải\s+Ebook$/i,
    /^Báo\s+lỗi$/i,
    /^Bình\s+luận$/i,
    /^Activate\s+Windows/i,
    /^Go\s+to\s+Settings/i,
    /^SIÊU/i,
    /^\d+\s+chương$/i,
    /^©/i,
    /^Menu$/i,
    /^Trang chủ$/i,
    /^Toggle navigation$/i
  ];

  const END_PATTERNS = [
    /^《?\s*Chương\s+trước/i,
    /^Chương\s+tiếp\s*》?$/i,
    /^Bạn có thể dùng phím/i,
    /^Báo lỗi$/i,
    /^Bình luận$/i,
    /^TRUYỆN HOT/i,
    /^Truyện hot/i,
    /^Danh sách truyện/i,
    /^Truyện đề cử/i,
    /^Có thể bạn thích/i,
    /^Mời bạn đọc/i,
    /^Các truyện/i,
    /^Đọc truyện/i
  ];

  const AD_SELECTORS = [
    "iframe[src*='ads']",
    "iframe[src*='doubleclick']",
    "iframe[src*='googlesyndication']",
    "iframe[src*='googleadservices']",
    "[id*='ads' i]",
    "[class*='ads' i]",
    "[id*='advert' i]",
    "[class*='advert' i]",
    "[id*='banner' i]",
    "[class*='banner' i]",
    "[class*='popup' i]",
    "[id*='popup' i]",
    "[class*='float' i][class*='ad' i]"
  ];

  // ------------------------------------------------------------------
  // Chặn popup cơ bản: tổng quát cho mọi site (không hard-code 1 site) —
  // chỉ cho phép window.open()/target=_blank tới CÙNG origin với trang
  // đang mở; mọi origin khác coi là quảng cáo/popup.
  // ------------------------------------------------------------------
  function injectPagePopupBlocker() {
    const sameOrigin = location.origin;
    const code = `
      (() => {
        if (window.__ccPagePopupBlocker) return;
        window.__ccPagePopupBlocker = true;
        const sameOrigin = ${JSON.stringify(sameOrigin)};
        const originalOpen = window.open;
        window.open = function(url, name, specs) {
          try {
            const allow = window.__ccAllowPopup === true;
            const s = String(url || "");
            let sameSite = !s;
            try { sameSite = !s || new URL(s, location.href).origin === sameOrigin; } catch (e) {}
            if (!allow && s && !sameSite) {
              console.debug("[Chapter Clipper] blocked popup:", s);
              return null;
            }
          } catch (e) {}
          return originalOpen.apply(window, arguments);
        };
        document.addEventListener("click", (e) => {
          const a = e.target && e.target.closest ? e.target.closest("a[target='_blank'], a[target='blank']") : null;
          if (!a) return;
          const href = a.href || "";
          let sameSite = true;
          try { sameSite = !href || new URL(href, location.href).origin === sameOrigin; } catch (err) {}
          if (href && !sameSite) {
            e.preventDefault();
            e.stopPropagation();
            console.debug("[Chapter Clipper] blocked target=_blank:", href);
          }
        }, true);
      })();
    `;
    const s = document.createElement("script");
    s.textContent = code;
    (document.documentElement || document.head || document.body).appendChild(s);
    s.remove();
  }

  // ------------------------------------------------------------------
  // Lưu trữ: localStorage của chính trang (đồng bộ, bọc thành Promise để
  // giữ nguyên hình dạng API getState()/setState() của bản gốc).
  // ------------------------------------------------------------------
  function getState() {
    return new Promise(resolve => {
      let data = {};
      try {
        data = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}") || {};
      } catch (e) {}
      resolve(Object.assign({}, DEFAULT_STATE, data));
    });
  }

  function setState(state) {
    return new Promise(resolve => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      } catch (e) {}
      resolve();
    });
  }

  function sanitizeFilename(name) {
    return String(name || "truyen")
      .normalize("NFD").replace(/[̀-ͯ]/g, "")
      .replace(/[đĐ]/g, "d")
      .replace(/[^a-zA-Z0-9._ -]+/g, " ")
      .trim()
      .replace(/\s+/g, "_")
      .slice(0, 120) || "truyen";
  }

  function normalizeLine(s) {
    return String(s || "")
      .replace(/ /g, " ")
      .replace(/[ \t\f\v]+/g, " ")
      .trim();
  }

  function bodyTextLines() {
    const raw = document.body ? (document.body.innerText || document.body.textContent || "") : "";
    return raw
      .replace(/\r\n/g, "\n")
      .replace(/\r/g, "\n")
      .replace(/ /g, " ")
      .split("\n")
      .map(normalizeLine);
  }

  function hideAdsFast() {
    try {
      AD_SELECTORS.forEach(sel => {
        document.querySelectorAll(sel).forEach(el => {
          if (el.closest && el.closest("#cc-panel")) return;
          const text = normalizeLine(el.innerText || el.textContent || "");
          if (/Chrome|Trình tiết kiệm bộ nhớ|hoạt động nhanh hơn/i.test(text)) return;
          el.style.setProperty("display", "none", "important");
          el.style.setProperty("visibility", "hidden", "important");
        });
      });

      document.querySelectorAll("a, div").forEach(el => {
        if (el.closest && el.closest("#cc-panel")) return;
        const style = getComputedStyle(el);
        if (style.position !== "fixed") return;
        const text = normalizeLine(el.innerText || el.textContent || "");
        const html = (el.outerHTML || "").slice(0, 500).toLowerCase();
        if (/ads?|advert|banner|popup|siêu|500k|casino|promo|click/.test(text.toLowerCase() + " " + html)) {
          el.style.setProperty("display", "none", "important");
        }
      });
    } catch (e) {}
  }

  function getStoryTitle() {
    const breadcrumb = Array.from(document.querySelectorAll(".breadcrumb a, .breadcrumb span, [class*='breadcrumb'] a, [class*='breadcrumb'] span"))
      .map(x => normalizeLine(x.innerText || x.textContent))
      .filter(Boolean);

    const good = breadcrumb.filter(x =>
      !/^Mê\s*Truyện\s*Chữ$/i.test(x) &&
      !/^Chương\s+\d+/i.test(x)
    );

    if (good.length >= 1) return good[good.length - 1];

    for (const h of Array.from(document.querySelectorAll("h1"))) {
      const t = normalizeLine(h.innerText || h.textContent);
      if (t && !/^Chương\s+\d+/i.test(t)) return t;
    }

    return normalizeLine((document.title || "").split("|")[0].split("- Chương")[0]);
  }

  function getChapterTitle() {
    const selectors = [
      "h2", "h1", ".chapter-title", ".chapter-name", "[class*='chapter'][class*='title']",
      "[class*='chapter'][class*='name']", ".title"
    ];

    for (const sel of selectors) {
      for (const n of Array.from(document.querySelectorAll(sel))) {
        const t = normalizeLine(n.innerText || n.textContent);
        if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(t)) return t;
      }
    }

    for (const line of bodyTextLines().slice(0, 50)) {
      if (/^(Chương|Chuong|Chapter)\s*\d+/i.test(line)) return line;
    }

    const fromTitle = normalizeLine((document.title || "").split("|")[0]);
    const m = fromTitle.match(/(Chương|Chuong|Chapter)\s*\d+[^-|]*/i);
    if (m) return normalizeLine(m[0]);

    const urlM = location.href.match(/chuong[-_/]?(\d+)/i);
    if (urlM) return `Chương ${parseInt(urlM[1], 10)}`;

    return "Chương chưa rõ số";
  }

  function getChapterNumber(title) {
    const m1 = String(title || "").match(/(?:Chương|Chuong|Chapter)\s*0*(\d+)/i);
    if (m1) return parseInt(m1[1], 10);
    const m2 = location.href.match(/chuong[-_/]?(\d+)/i);
    if (m2) return parseInt(m2[1], 10);
    return null;
  }

  function lineHasStoryTitle(line, storyTitle) {
    if (!storyTitle) return false;
    const a = normalizeLine(line).toLowerCase();
    const b = normalizeLine(storyTitle).toLowerCase();
    return a === b || a.includes(b);
  }

  function isLikelyContentLine(line) {
    if (!line) return false;
    if (DROP_LINE_PATTERNS.some(p => p.test(line))) return false;
    if (TOP_UI_PATTERNS.some(p => p.test(line))) return false;
    if (!/[a-zA-ZÀ-ỹ]/.test(line)) return false;
    if (line.length < 12) return false;
    return true;
  }

  function cleanFinalLines(lines, chapterTitle, storyTitle) {
    const out = [];
    for (let line of lines) {
      line = normalizeLine(line);
      if (!line) {
        if (out.length && out[out.length - 1] !== "") out.push("");
        continue;
      }
      if (DROP_LINE_PATTERNS.some(p => p.test(line))) continue;
      if (chapterTitle && line.toLowerCase() === chapterTitle.toLowerCase()) continue;
      if (lineHasStoryTitle(line, storyTitle)) continue;
      out.push(line);
    }
    return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
  }

  function extractByPageBoundaries(chapterTitle, storyTitle) {
    const lines = bodyTextLines();

    let titleIndexes = [];
    const chapNum = getChapterNumber(chapterTitle);
    const exactTitle = normalizeLine(chapterTitle).toLowerCase();

    for (let i = 0; i < Math.min(lines.length, 160); i++) {
      const l = normalizeLine(lines[i]);
      if (!l) continue;
      const low = l.toLowerCase();

      if (exactTitle && low === exactTitle) titleIndexes.push(i);
      else if (chapNum != null && new RegExp(`^chương\\s*0*${chapNum}\\b`, "i").test(l)) titleIndexes.push(i);
    }

    let scanFrom = titleIndexes.length ? titleIndexes[titleIndexes.length - 1] + 1 : 0;

    let start = -1;
    for (let i = scanFrom; i < Math.min(lines.length, scanFrom + 100); i++) {
      const l = normalizeLine(lines[i]);
      if (!l) continue;
      if (!isLikelyContentLine(l)) continue;
      if (lineHasStoryTitle(l, storyTitle)) continue;

      start = i;
      break;
    }

    if (start < 0) return "";

    const kept = [];
    let charCount = 0;
    for (let i = start; i < lines.length; i++) {
      const l = normalizeLine(lines[i]);

      if (charCount > 800 && l && END_PATTERNS.some(p => p.test(l))) break;

      kept.push(lines[i]);
      if (l) charCount += l.length + 1;

      if (charCount > 800 && /^TRUYỆN HOT|^Truyện hot/i.test(l)) break;
    }

    return cleanFinalLines(kept, chapterTitle, storyTitle);
  }

  function getVisibleText(el) {
    if (!el) return "";
    const clone = el.cloneNode(true);
    clone.querySelectorAll([
      "script", "style", "noscript", "svg", "canvas", "iframe",
      "nav", "header", "footer", "aside", "form", "button",
      ".ads", ".ad", ".advertisement", ".banner", ".comment", ".comments",
      ".breadcrumb", ".navbar", ".menu", ".search", ".recommend", ".hot",
      "[class*='ads' i]", "[id*='ads' i]", "[class*='comment' i]", "[id*='comment' i]",
      "[class*='banner' i]", "[id*='banner' i]"
    ].join(",")).forEach(n => n.remove());

    return clone.innerText || clone.textContent || "";
  }

  function normalizeText(raw, chapterTitle = "", storyTitle = "") {
    const lines = String(raw || "")
      .replace(/\r\n/g, "\n")
      .replace(/\r/g, "\n")
      .replace(/ /g, " ")
      .split("\n")
      .map(normalizeLine);

    let kept = [];
    let charCount = 0;
    for (const line of lines) {
      if (charCount > 800 && line && END_PATTERNS.some(p => p.test(line))) break;
      kept.push(line);
      if (line) charCount += line.length + 1;
    }
    return cleanFinalLines(kept, chapterTitle, storyTitle);
  }

  function scoreText(text) {
    const s = String(text || "");
    const lines = s.split("\n").map(normalizeLine).filter(Boolean);
    const longLines = lines.filter(l => l.length >= 30).length;
    const punc = (s.match(/[,.!?;:。！？…]/g) || []).length;
    const bad = (s.match(/Chương trước|Chương tiếp|Tải Ebook|Bình luận|TRUYỆN HOT|Danh sách|Thể loại/gi) || []).length;
    return s.length + longLines * 220 + punc * 5 - bad * 1200;
  }

  function extractByContainerFallback(chapterTitle, storyTitle) {
    const selectors = [
      "#chapter-content", ".chapter-content", ".chapter-c", "#chapter-c",
      ".chapter-body", ".chapter-text", ".content-chapter", ".reading-content",
      ".entry-content", "article", "main", ".container", ".chapter", "body"
    ];

    let best = "";
    let bestScore = -Infinity;

    for (const sel of selectors) {
      for (const el of Array.from(document.querySelectorAll(sel))) {
        const raw = getVisibleText(el);
        const cleaned = normalizeText(raw, chapterTitle, storyTitle);
        const sc = scoreText(cleaned);
        if (cleaned.length > 120 && sc > bestScore) {
          best = cleaned;
          bestScore = sc;
        }
      }
    }

    if (!best || best.length < 300) {
      const ps = Array.from(document.querySelectorAll("p"))
        .map(p => normalizeLine(p.innerText || p.textContent))
        .filter(t => t.length > 20 && !DROP_LINE_PATTERNS.some(p => p.test(t)));
      if (ps.length) best = normalizeText(ps.join("\n\n"), chapterTitle, storyTitle);
    }

    return best;
  }

  function extractBody(chapterTitle, storyTitle) {
    hideAdsFast();

    const byBoundary = extractByPageBoundaries(chapterTitle, storyTitle);
    const byContainer = extractByContainerFallback(chapterTitle, storyTitle);

    if (byBoundary.length >= byContainer.length * 0.85) return byBoundary;
    if (byContainer.length > byBoundary.length + 1000) return byContainer;
    return byBoundary || byContainer;
  }

  function extractCurrentChapter() {
    const storyTitle = getStoryTitle();
    const chapterTitle = getChapterTitle();
    const chapterNumber = getChapterNumber(chapterTitle);
    const body = extractBody(chapterTitle, storyTitle);

    return {
      storyTitle,
      chapterTitle,
      chapterNumber,
      body,
      url: location.href,
      savedAt: new Date().toISOString(),
      charCount: body.length
    };
  }

  function sortedChapters(chapters) {
    return [...chapters].sort((a, b) => {
      const an = Number.isFinite(a.chapterNumber) ? a.chapterNumber : 999999;
      const bn = Number.isFinite(b.chapterNumber) ? b.chapterNumber : 999999;
      if (an !== bn) return an - bn;
      return String(a.savedAt || "").localeCompare(String(b.savedAt || ""));
    });
  }

  function formatTxt(chapters) {
    return sortedChapters(chapters).map(ch => {
      const title = ch.chapterTitle || (ch.chapterNumber ? `Chương ${ch.chapterNumber}` : "Chương");
      return `${title}\n\n${ch.body || ""}`.trim();
    }).filter(Boolean).join("\n\n\n") + "\n";
  }

  function downloadTxt(filename, text) {
    try {
      if (window.LqlqAndroid && typeof window.LqlqAndroid.saveTextFile === "function") {
        const ok = window.LqlqAndroid.saveTextFile(String(filename), "﻿" + String(text));
        if (ok) return;
      }
    } catch (e) {}
    const blob = new Blob(["﻿", text], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.style.display = "none";
    document.documentElement.appendChild(a);
    a.click();
    setTimeout(() => {
      URL.revokeObjectURL(url);
      a.remove();
    }, 1000);
  }

  function makeBatchFilename(chapters, state) {
    const sorted = sortedChapters(chapters);
    const first = sorted[0] || {};
    const story = sanitizeFilename(first.storyTitle || document.title || "truyen");
    const nums = sorted.map(c => c.chapterNumber).filter(n => Number.isFinite(n));
    let suffix;
    if (nums.length) suffix = `chuong_${Math.min(...nums)}_${Math.max(...nums)}`;
    else suffix = `lo_${state.batchNo || 1}`;
    return `${story}_${suffix}.txt`;
  }

  async function exportChapters(clearAfter = false) {
    const state = await getState();
    if (!state.chapters.length) {
      showToast("Chưa có chương nào để xuất TXT.");
      return false;
    }

    const filename = makeBatchFilename(state.chapters, state);
    const text = formatTxt(state.chapters);
    downloadTxt(filename, text);

    if (clearAfter) {
      state.chapters = [];
      state.batchNo = (state.batchNo || 1) + 1;
      await setState(state);
      updateStatus();
    }

    return true;
  }

  function readPanelSettingsInto(state) {
    const batchInput = document.getElementById("cc-batch-size");
    const autoInput = document.getElementById("cc-auto-export");
    const popupInput = document.getElementById("cc-popup-blocker");
    const autoCountInput = document.getElementById("cc-auto-count");

    if (batchInput) state.batchSize = Math.max(1, parseInt(batchInput.value || "10", 10));
    if (autoInput) state.autoExport = !!autoInput.checked;
    if (popupInput) state.popupBlocker = !!popupInput.checked;
    if (autoCountInput) state.autoCount = Math.max(1, parseInt(autoCountInput.value || "100", 10));
  }

  async function commitChapter(ch) {
    const state = await getState();
    readPanelSettingsInto(state);

    const dupIndex = state.chapters.findIndex(x =>
      (ch.chapterNumber != null && x.chapterNumber === ch.chapterNumber && x.storyTitle === ch.storyTitle) ||
      x.url === ch.url
    );

    if (dupIndex >= 0) {
      state.chapters[dupIndex] = ch;
      showToast(`Đã cập nhật ${ch.chapterTitle}: ${ch.body.length.toLocaleString()} ký tự.`);
    } else {
      state.chapters.push(ch);
      showToast(`Đã lưu ${ch.chapterTitle}: ${ch.body.length.toLocaleString()} ký tự.`);
    }

    await setState(state);
    updateStatus();

    const latest = await getState();
    const count = latest.chapters.length;

    if (latest.autoExport && count >= latest.batchSize) {
      const filename = makeBatchFilename(latest.chapters, latest);
      const text = formatTxt(latest.chapters);
      downloadTxt(filename, text);
      latest.chapters = [];
      latest.batchNo = (latest.batchNo || 1) + 1;
      await setState(latest);
      updateStatus();
      showToast(`Đã xuất TXT: ${filename}. Bắt đầu lô mới.`);
    }

    return true;
  }

  async function saveCurrentChapter({ next = false } = {}) {
    const ch = extractCurrentChapter();

    if (!ch.body || ch.body.length < 300) {
      showToast("Không lấy được nội dung chương đầy đủ. Hãy chờ trang tải xong rồi bấm lại.");
      return false;
    }

    await commitChapter(ch);

    if (next) setTimeout(goNextChapter, 250);
    return true;
  }

  function waitForContent(maxMs = 25000) {
    return new Promise(resolve => {
      const t0 = Date.now();
      const tryOnce = () => {
        let ch = null;
        try { ch = extractCurrentChapter(); } catch (e) {}
        if (ch && ch.body && ch.body.length >= 300) return resolve(ch);
        if (Date.now() - t0 > maxMs) return resolve(null);
        setTimeout(tryOnce, 700);
      };
      tryOnce();
    });
  }

  async function startAutoRun() {
    const state = await getState();
    readPanelSettingsInto(state);

    const n = Math.max(1, parseInt(state.autoCount || 10, 10));
    state.autoRun = true;
    state.autoRemaining = n;
    state.autoTotal = n;
    state.autoLastUrl = "";
    await setState(state);
    updateStatus();
    showToast(`▶ Bắt đầu tự động ${n} chương. Đừng thao tác trên tab này.`);
    autoStep();
  }

  async function stopAutoRun({ silent = false, reason = "" } = {}) {
    const state = await getState();
    state.autoRun = false;
    state.autoRemaining = 0;
    state.autoLastUrl = "";
    await setState(state);
    updateStatus();
    if (!silent) showToast(reason ? `⏹ Đã dừng tự động: ${reason}` : "⏹ Đã dừng tự động.");
  }

  async function finishAutoRun(reason) {
    const state = await getState();
    const done = (state.autoTotal || 0) - (state.autoRemaining || 0);

    state.autoRun = false;
    state.autoRemaining = 0;
    state.autoLastUrl = "";

    if (state.autoExport && state.chapters.length) {
      const filename = makeBatchFilename(state.chapters, state);
      const text = formatTxt(state.chapters);
      downloadTxt(filename, text);
      state.chapters = [];
      state.batchNo = (state.batchNo || 1) + 1;
      showToast(`Đã xuất TXT phần còn lại: ${filename}`);
    }

    await setState(state);
    updateStatus();

    const msg = reason
      ? `⏹ Tự động dừng (${reason}). Đã lưu ${done}/${state.autoTotal || done} chương.`
      : `✅ Tự động hoàn tất: đã lưu ${done} chương.`;
    showToast(msg);
  }

  async function autoStep() {
    let state = await getState();
    if (!state.autoRun || (state.autoRemaining || 0) <= 0) return;

    updateStatus();

    const alreadySaved = state.autoLastUrl && state.autoLastUrl === location.href;

    let ch = null;
    if (!alreadySaved) {
      showToast(`⏳ Tự động: đang lấy nội dung... (còn ${state.autoRemaining}/${state.autoTotal})`);
      ch = await waitForContent();

      state = await getState();
      if (!state.autoRun) return;

      if (!ch) {
        await finishAutoRun("không lấy được nội dung chương, có thể trang chưa tải xong hoặc đổi giao diện");
        return;
      }

      await commitChapter(ch);

      state = await getState();
      if (!state.autoRun) return;

      state.autoRemaining = Math.max(0, (state.autoRemaining || 0) - 1);
      state.autoLastUrl = location.href;
      await setState(state);
      updateStatus();
    }

    if ((state.autoRemaining || 0) <= 0) {
      await finishAutoRun();
      return;
    }

    const href = findNextChapterLink();
    if (!href) {
      await finishAutoRun("không tìm thấy nút Chương tiếp, có thể đã hết truyện");
      return;
    }

    const delay = 1000 + Math.floor(Math.random() * 1500);
    showToast(`➡ Còn ${state.autoRemaining}/${state.autoTotal} chương. Chuyển chương sau ${(delay / 1000).toFixed(1)}s...`);

    setTimeout(async () => {
      const s = await getState();
      if (!s.autoRun) return;
      location.href = href;
    }, delay);
  }

  async function resumeAutoRunIfNeeded() {
    const state = await getState();
    if (state.autoRun && (state.autoRemaining || 0) > 0) {
      setTimeout(autoStep, 600);
    } else if (state.autoRun) {
      stopAutoRun({ silent: true });
    }
  }

  function findNextChapterLink() {
    const links = Array.from(document.querySelectorAll("a, button"));
    for (const el of links) {
      const text = normalizeLine(el.innerText || el.textContent || el.getAttribute("title") || "");
      const href = el.href || el.getAttribute("data-href") || "";
      if (/Chương\s*tiếp|Chuong\s*tiep|Next/i.test(text) && href && !/javascript:/i.test(href)) return href;
    }

    const relNext = document.querySelector("link[rel='next']");
    if (relNext && relNext.href) return relNext.href;
    return null;
  }

  function goNextChapter() {
    const href = findNextChapterLink();
    if (!href) {
      showToast("Không tìm thấy nút Chương tiếp trên trang này.");
      return;
    }
    location.href = href;
  }

  function showToast(msg) {
    let toastEl = document.getElementById("cc-toast");
    if (!toastEl) {
      toastEl = document.createElement("div");
      toastEl.id = "cc-toast";
      document.documentElement.appendChild(toastEl);
    }
    toastEl.textContent = msg;
    toastEl.classList.add("show");
    clearTimeout(window.__ccToastTimer);
    window.__ccToastTimer = setTimeout(() => toastEl.classList.remove("show"), 2600);
  }

  async function clearBatch() {
    const state = await getState();
    state.chapters = [];
    await setState(state);
    updateStatus();
    showToast("Đã xóa lô hiện tại.");
  }

  async function undoLast() {
    const state = await getState();
    if (!state.chapters.length) {
      showToast("Chưa có chương nào để hoàn tác.");
      return;
    }
    const removed = state.chapters.pop();
    await setState(state);
    updateStatus();
    showToast(`Đã bỏ chương cuối: ${removed.chapterTitle || "Chương"}`);
  }

  function testExtract() {
    const ch = extractCurrentChapter();
    showToast(`${ch.chapterTitle} · ${ch.body.length.toLocaleString()} ký tự`);
    const status = document.getElementById("cc-status");
    if (status) {
      status.textContent =
        `${ch.chapterTitle}\n${ch.body.length.toLocaleString()} ký tự\n\n` +
        `ĐOẠN ĐẦU:\n${ch.body.slice(0, 220)}\n\nĐOẠN CUỐI:\n${ch.body.slice(-220)}`;
    }
  }

  async function updateStatus() {
    const state = await getState();

    const status = document.getElementById("cc-status");
    const batch = document.getElementById("cc-batch-size");
    const auto = document.getElementById("cc-auto-export");
    const popup = document.getElementById("cc-popup-blocker");
    const body = document.getElementById("cc-body");
    const min = document.getElementById("cc-min");

    if (batch) batch.value = state.batchSize || 10;
    if (auto) auto.checked = state.autoExport !== false;
    if (popup) popup.checked = state.popupBlocker !== false;

    const autoCount = document.getElementById("cc-auto-count");
    const autoStart = document.getElementById("cc-auto-start");
    const autoStop = document.getElementById("cc-auto-stop");
    if (autoCount && document.activeElement !== autoCount) autoCount.value = state.autoCount || 100;
    if (autoStart) {
      autoStart.disabled = !!state.autoRun;
      autoStart.textContent = state.autoRun ? "⏳ Đang chạy..." : "▶ Bắt đầu tự động";
    }
    if (autoStop) autoStop.disabled = !state.autoRun;
    const autoBox = document.getElementById("cc-auto-box");
    if (autoBox) autoBox.classList.toggle("cc-running", !!state.autoRun);

    if (body && min) {
      body.style.display = state.compact ? "none" : "block";
      min.textContent = state.compact ? "+" : "−";
    }

    if (status) {
      const sorted = sortedChapters(state.chapters);
      const list = sorted
        .map((c, i) => `${i + 1}. ${c.chapterTitle || "Chương"} (${(c.charCount || (c.body || "").length || 0).toLocaleString()} ký tự)`)
        .slice(-7)
        .join("\n");
      const autoLine = state.autoRun
        ? `🤖 TỰ ĐỘNG: đã lưu ${(state.autoTotal || 0) - (state.autoRemaining || 0)}/${state.autoTotal || 0} chương\n`
        : "";
      status.textContent =
        autoLine +
        `Lô hiện tại: ${state.chapters.length}/${state.batchSize || 10} chương\n` +
        (list ? `Gần nhất:\n${list}` : "Chưa lưu chương nào.");
    }
  }

  async function toggleCompact() {
    const state = await getState();
    state.compact = !state.compact;
    await setState(state);
    updateStatus();
  }

  async function saveSettingsFromPanel() {
    const state = await getState();
    readPanelSettingsInto(state);
    await setState(state);
    updateStatus();
  }

  function makePanel() {
    if (!document.body) {
      setTimeout(makePanel, 50);
      return;
    }

    const panel = document.createElement("div");
    panel.id = "cc-panel";
    panel.innerHTML = `
      <div id="cc-head">
        <b>TXT Chapter+ v1.3</b>
        <button id="cc-min" title="Thu nhỏ/mở rộng">−</button>
      </div>
      <div id="cc-body">
        <div id="cc-auto-box">
          <label class="cc-row">
            <span>Tự động</span>
            <input id="cc-auto-count" type="number" min="1" max="5000" value="100">
            <span>chương</span>
          </label>
          <div class="cc-two">
            <button id="cc-auto-start">▶ Bắt đầu tự động</button>
            <button id="cc-auto-stop">⏹ Dừng</button>
          </div>
        </div>
        <button id="cc-save">📋 Lưu chương FULL</button>
        <button id="cc-save-next">⚡ Lưu FULL & chương tiếp</button>
        <button id="cc-test">🔎 Kiểm tra lấy nội dung</button>
        <button id="cc-export">💾 Xuất TXT ngay</button>
        <div class="cc-two">
          <button id="cc-undo">↩ Bỏ chương cuối</button>
          <button id="cc-clear">🗑 Xóa lô</button>
        </div>
        <label class="cc-row">
          <span>Tự xuất mỗi</span>
          <input id="cc-batch-size" type="number" min="1" max="200" value="10">
          <span>chương</span>
        </label>
        <label class="cc-check">
          <input id="cc-auto-export" type="checkbox" checked>
          <span>Đủ số thì tự tải TXT</span>
        </label>
        <label class="cc-check">
          <input id="cc-popup-blocker" type="checkbox" checked>
          <span>Chặn popup/tab quảng cáo</span>
        </label>
        <div class="cc-hotkeys">
          Ctrl+Shift+S: lưu · Ctrl+Shift+N: lưu & tiếp · Ctrl+Shift+A: tự động
        </div>
        <pre id="cc-status"></pre>
      </div>
    `;
    document.documentElement.appendChild(panel);

    panel.querySelector("#cc-min").addEventListener("click", toggleCompact);
    panel.querySelector("#cc-auto-start").addEventListener("click", startAutoRun);
    panel.querySelector("#cc-auto-stop").addEventListener("click", () => stopAutoRun());
    panel.querySelector("#cc-auto-count").addEventListener("change", saveSettingsFromPanel);
    panel.querySelector("#cc-save").addEventListener("click", () => saveCurrentChapter({ next: false }));
    panel.querySelector("#cc-save-next").addEventListener("click", () => saveCurrentChapter({ next: true }));
    panel.querySelector("#cc-test").addEventListener("click", testExtract);
    panel.querySelector("#cc-export").addEventListener("click", () => exportChapters(false));
    panel.querySelector("#cc-clear").addEventListener("click", clearBatch);
    panel.querySelector("#cc-undo").addEventListener("click", undoLast);
    panel.querySelector("#cc-batch-size").addEventListener("change", saveSettingsFromPanel);
    panel.querySelector("#cc-auto-export").addEventListener("change", saveSettingsFromPanel);
    panel.querySelector("#cc-popup-blocker").addEventListener("change", saveSettingsFromPanel);

    document.addEventListener("keydown", e => {
      if (!(e.ctrlKey && e.shiftKey)) return;
      const key = e.key.toLowerCase();
      if (key === "s") {
        e.preventDefault();
        saveCurrentChapter({ next: false });
      } else if (key === "n") {
        e.preventDefault();
        saveCurrentChapter({ next: true });
      } else if (key === "e") {
        e.preventDefault();
        exportChapters(false);
      } else if (key === "a") {
        e.preventDefault();
        getState().then(s => s.autoRun ? stopAutoRun() : startAutoRun());
      }
    }, true);

    updateStatus();
  }

  injectPagePopupBlocker();
  console.debug("[Chapter Clipper] Đã bật chặn quảng cáo/popup cho trang này.");

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      makePanel();
      hideAdsFast();
      resumeAutoRunIfNeeded();
    });
  } else {
    makePanel();
    hideAdsFast();
    resumeAutoRunIfNeeded();
  }

  let adRuns = 0;
  const adTimer = setInterval(() => {
    hideAdsFast();
    adRuns++;
    if (adRuns > 40) clearInterval(adTimer);
  }, 500);

  const observer = new MutationObserver(() => hideAdsFast());
  setTimeout(() => {
    if (document.body) observer.observe(document.body, { childList: true, subtree: true });
  }, 1000);

  // ------------------------------------------------------------------
  // Hook dùng chung với reader.js (trySharedChapterExtraction() trong
  // reader.js đã chủ động tìm window.LqlqChapterExtractor/LqlqChapterClipper/
  // ShieldChapterClipper). Chỉ tái sử dụng logic trích xuất+dọn dẹp text đã
  // có ở trên (extractCurrentChapter) — KHÔNG lưu file, KHÔNG đụng vào
  // batch/autoRun hiện tại (không side-effect).
  // ------------------------------------------------------------------
  window.LqlqChapterClipper = window.LqlqChapterClipper || {};
  window.LqlqChapterClipper.extractCurrentChapter = function () {
    const ch = extractCurrentChapter();
    return {
      title: ch.storyTitle,
      chapterTitle: ch.chapterTitle,
      text: ch.body,
      url: ch.url
    };
  };
})();
