const ACCESS_TEXT = "Iqlq";
const STORAGE_KEY = "shieldBrowserPrototypeStateV2";
const NATIVE_TAB_STORE = Boolean(window.LqlqAndroid && window.LqlqAndroid.getTabState);

function uid() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }
  return "id-" + Date.now() + "-" + Math.random().toString(16).slice(2);
}

// Việc 2 (v0.23.4): thẻ "trống" (chưa mở trang nào) dùng url rỗng thay vì
// "https://google.com" giả lập — để không bị hiểu nhầm là "trang web thật
// cần khôi phục" (xem android-glue.js/index.html: chỉ tự mở lại thẻ có
// url http(s) thật), và để hiện đúng trạng thái "địa chỉ trống" ở #pageView.
function blankTab(title = "Thẻ mới") {
  return { id: uid(), title, url: "" };
}

const store = {
  normal: {
    tabs: [blankTab("Thẻ mới")],
    activeTabId: null,
    history: [],
    bookmarks: [],
    downloads: []
  },
  private: {
    tabs: [blankTab("Thẻ mới")],
    activeTabId: null,
    history: [],
    bookmarks: [],
    downloads: []
  },
  mode: "normal",
  zoom: 100
};

const els = {
  app: document.getElementById("app"),
  tabStrip: document.getElementById("tabStrip"),
  addressInput: document.getElementById("addressInput"),
  goBtn: document.getElementById("goBtn"),
  backBtn: document.getElementById("backBtn"),
  forwardBtn: document.getElementById("forwardBtn"),
  reloadBtn: document.getElementById("reloadBtn"),
  menuBtn: document.getElementById("menuBtn"),
  chromeMenu: document.getElementById("chromeMenu"),
  toolsMenu: document.getElementById("toolsMenu"),
  pageView: document.getElementById("pageView"),
  pageTitle: document.getElementById("pageTitle"),
  pageUrl: document.getElementById("pageUrl"),
  fakeSearch: document.getElementById("fakeSearch"),
  fakeSearchBtn: document.getElementById("fakeSearchBtn"),
  searchModal: document.getElementById("searchModal"),
  searchInput: document.getElementById("searchInput"),
  searchError: document.getElementById("searchError"),
  cancelSearch: document.getElementById("cancelSearch"),
  submitSearch: document.getElementById("submitSearch"),
  drawer: document.getElementById("sideDrawer"),
  drawerTitle: document.getElementById("drawerTitle"),
  drawerContent: document.getElementById("drawerContent"),
  closeDrawer: document.getElementById("closeDrawer"),
  toast: document.getElementById("toast"),
  zoomLabel: document.getElementById("zoomLabel"),
  videoPanel: document.getElementById("videoPanel"),
  videoShell: document.getElementById("videoShell"),
  closeVideoPanel: document.getElementById("closeVideoPanel"),
  videoUrlInput: document.getElementById("videoUrlInput"),
  openVideoUrl: document.getElementById("openVideoUrl"),
  videoFileInput: document.getElementById("videoFileInput"),
  videoFrame: document.getElementById("videoFrame"),
  videoPlayer: document.getElementById("videoPlayer"),
  screenLockOverlay: document.getElementById("screenLockOverlay"),
  holdToUnlock: document.getElementById("holdToUnlock"),
  downloadVideoBtn: document.getElementById("downloadVideoBtn"),
  toggleOrientationBtn: document.getElementById("toggleOrientationBtn"),
  toggleMuteLockBtn: document.getElementById("toggleMuteLockBtn"),
  toggleScreenLockBtn: document.getElementById("toggleScreenLockBtn"),
  toggleFullscreenBtn: document.getElementById("toggleFullscreenBtn"),
  stopVideoBtn: document.getElementById("stopVideoBtn"),
  videoStatus: document.getElementById("videoStatus"),
  contextVideoToolbar: document.getElementById("contextVideoToolbar"),
  contextDownloadVideo: document.getElementById("contextDownloadVideo"),
  contextOrientation: document.getElementById("contextOrientation"),
  contextMuteLock: document.getElementById("contextMuteLock"),
  contextScreenLock: document.getElementById("contextScreenLock"),
  contextFullscreen: document.getElementById("contextFullscreen"),
  globalScreenLock: document.getElementById("globalScreenLock"),
  globalHoldUnlock: document.getElementById("globalHoldUnlock"),
  warpModal: document.getElementById("warpModal"),
  warpStatusText: document.getElementById("warpStatusText"),
  openWarpApp: document.getElementById("openWarpApp"),
  installWarpApp: document.getElementById("installWarpApp"),
  checkWarpConnection: document.getElementById("checkWarpConnection"),
  closeWarpModal: document.getElementById("closeWarpModal"),
  androidAdblockBadge: document.getElementById("androidAdblockBadge")
};

function loadState() {
  const saved = localStorage.getItem(STORAGE_KEY);

  if (saved) {
    try {
      const parsed = JSON.parse(saved);
      Object.assign(store.normal, parsed.normal || {});
      Object.assign(store.private, parsed.private || {});
      store.zoom = parsed.zoom || 100;

      // Trong APK, danh sách thẻ do Android quản lý duy nhất. Không khôi phục
      // mảng tabs cũ từ localStorage vì đó là nguyên nhân tạo "thẻ ma": JS có
      // thẻ nhưng native không có WebView tương ứng. Lịch sử/dấu trang/tải xuống
      // vẫn được giữ nguyên; android-glue sẽ đồng bộ bản sao tabs ngay sau đó.
      if (NATIVE_TAB_STORE) {
        store.normal.tabs = [blankTab("Thẻ mới")];
        store.normal.activeTabId = store.normal.tabs[0].id;
        store.private.tabs = [blankTab("Thẻ mới")];
        store.private.activeTabId = store.private.tabs[0].id;
      }
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  if (!store.normal.tabs || !store.normal.tabs.length) {
    store.normal.tabs = [blankTab("Thẻ mới")];
  }

  if (!store.private.tabs || !store.private.tabs.length) {
    store.private.tabs = [blankTab("Thẻ mới")];
  }

  store.normal.activeTabId ||= store.normal.tabs[0].id;
  store.private.activeTabId ||= store.private.tabs[0].id;

  // Luôn khởi động ở giao diện thường. Dữ liệu vùng riêng vẫn được giữ lại.
  store.mode = "normal";
  saveState();
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    normal: store.normal,
    private: store.private,
    zoom: store.zoom
  }));
}

function currentProfile() {
  return store[store.mode];
}

function activeTab() {
  const profile = currentProfile();
  return profile.tabs.find(tab => tab.id === profile.activeTabId) || profile.tabs[0];
}

function normalizeUrl(input) {
  let value = String(input || "").trim();

  if (!value) return "https://google.com";
  if (/^[a-z]+:\/\//i.test(value)) return value;
  if (value.includes(".") && !value.includes(" ")) return "https://" + value;

  return "https://www.google.com/search?q=" + encodeURIComponent(value);
}

function titleFromUrl(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "") || "Thẻ mới";
  } catch {
    return "Thẻ mới";
  }
}

function navigate(url, addHistory = true) {
  const tab = activeTab();
  const finalUrl = normalizeUrl(url);

  tab.url = finalUrl;
  tab.title = titleFromUrl(finalUrl);

  if (addHistory) {
    currentProfile().history.unshift({
      title: tab.title,
      url: finalUrl,
      favicon: faviconForUrl(finalUrl),
      time: new Date().toLocaleString("vi-VN")
    });
    currentProfile().history = currentProfile().history.slice(0, 200);
  }

  saveState();
  render();
  toast("Đã mở: " + tab.title + " · Bộ lọc quảng cáo đang bật");
}

function newTab(url = "") {
  const profile = currentProfile();
  const tab = url
    ? { id: uid(), title: titleFromUrl(url), url }
    : blankTab("Thẻ mới");

  profile.tabs.push(tab);
  profile.activeTabId = tab.id;
  saveState();
  render();
}

function closeTab(id) {
  const profile = currentProfile();

  if (profile.tabs.length <= 1) {
    // Đóng thẻ cuối cùng → về thẻ trống (thanh địa chỉ trống), KHÔNG mở
    // lại google.com giả lập (Việc 2, v0.23.4).
    profile.tabs[0] = blankTab("Thẻ mới");
    profile.activeTabId = profile.tabs[0].id;
  } else {
    const index = profile.tabs.findIndex(tab => tab.id === id);
    profile.tabs = profile.tabs.filter(tab => tab.id !== id);

    if (profile.activeTabId === id) {
      profile.activeTabId = profile.tabs[Math.max(0, index - 1)].id;
    }
  }

  saveState();
  render();
}

function switchTab(id) {
  currentProfile().activeTabId = id;
  saveState();
  render();
}

function renderTabs() {
  const profile = currentProfile();
  els.tabStrip.innerHTML = "";

  profile.tabs.forEach(tab => {
    const row = document.createElement("div");
    row.className = "tab" + (tab.id === profile.activeTabId ? " active" : "");
    row.innerHTML = `
      <span>🌐</span>
      <span>${escapeHtml(tab.title)}</span>
      <button class="close" title="Đóng thẻ">×</button>
    `;

    row.addEventListener("click", () => switchTab(tab.id));
    row.querySelector(".close").addEventListener("click", event => {
      event.stopPropagation();
      closeTab(tab.id);
    });

    els.tabStrip.appendChild(row);
  });

  const plus = document.createElement("button");
  plus.className = "icon-btn";
  plus.textContent = "+";
  plus.title = "Thẻ mới";
  plus.addEventListener("click", () => newTab());
  els.tabStrip.appendChild(plus);
}

function renderPage() {
  const tab = activeTab();

  els.addressInput.value = tab.url || "";
  els.pageTitle.textContent = tab.title;
  els.pageUrl.textContent = tab.url || "";
  els.fakeSearch.value = "";
  els.zoomLabel.textContent = store.zoom + "%";

  els.pageView.style.transform = `scale(${store.zoom / 100})`;
  els.pageView.style.transformOrigin = "top center";

  // Không hiển thị chữ hoặc nhãn mô tả vùng riêng.
  els.app.classList.toggle("private-context", store.mode === "private");

  // Việc 2 (v0.23.4): chưa có trang thật nào mở (thẻ trống) và không có
  // native page đang hiển thị phía dưới → focus sẵn ô địa chỉ để gõ ngay.
  const nativePageOpen = document.body.classList.contains("lqlq-native-page-open");
  if (!tab.url && !nativePageOpen && document.activeElement !== els.fakeSearch) {
    setTimeout(() => els.fakeSearch?.focus(), 30);
  }
}

function render() {
  renderTabs();
  renderPage();

  if (els.androidAdblockBadge) {
    const android = /Android/i.test(navigator.userAgent || "");
    els.androidAdblockBadge.textContent = android
      ? "Android: bộ chặn đang bật"
      : "Android: mã tương thích đã bật";
  }
}

function openSearchModal() {
  closeMenus();
  els.searchModal.classList.remove("hidden");
  els.searchInput.value = "";
  els.searchError.classList.add("hidden");
  setTimeout(() => els.searchInput.focus(), 50);
}

function closeSearchModal() {
  els.searchModal.classList.add("hidden");
  els.searchInput.value = "";
  els.searchError.classList.add("hidden");
}

function submitHiddenSearch() {
  const query = els.searchInput.value;

  if (query !== ACCESS_TEXT) {
    els.searchError.classList.remove("hidden");
    els.searchInput.value = "";
    els.searchInput.focus();
    return;
  }

  closeSearchModal();

  // Cùng một thao tác dùng để vào hoặc trở về, không hiện thông báo tiết lộ.
  store.mode = store.mode === "normal" ? "private" : "normal";
  saveState();
  render();
}

function openDrawer(type) {
  // Việc (v0.23.27 — Vấn đề: "Nhật ký"/"Trang đã lưu" không hiện gì): mở
  // panel (thêm class "open" → native đưa shellWebView lên trên trang web
  // thật) TRƯỚC, rồi mới render nội dung bên trong try/catch riêng. Trước
  // đây nếu renderList()/truy cập profile.* ném lỗi giữa hàm, cả hàm dừng
  // NGAY SAU khi thêm class "open" nhưng TRƯỚC khi kịp có nội dung — và vì
  // exception xảy ra đồng bộ ngay trong sự kiện click, không ảnh hưởng gì
  // tới việc class đã add, nhưng để phòng các lỗi tương tự trong tương lai
  // (vd. dữ liệu profile hỏng do các đợt thêm tabGroups/Trang đã lưu), toàn
  // bộ phần render nội dung được bọc try/catch để KHÔNG BAO GIỜ làm panel
  // "im lặng không hiện gì" nữa — tối thiểu cũng thấy khung panel trống.
  els.drawer.classList.add("open");

  try {
    const profile = currentProfile();

    if (type === "history") {
      els.drawerTitle.textContent = "Nhật ký";
      renderList(profile.history || [], "Chưa có lịch sử.", "history");
      return;
    }

    if (type === "bookmarks") {
      els.drawerTitle.textContent = "Dấu trang và danh sách";
      renderList(profile.bookmarks || [], "Chưa có dấu trang.");
      return;
    }

    if (type === "downloads") {
      els.drawerTitle.textContent = "Tệp đã tải xuống";
      renderList(profile.downloads || [], "Chưa có tệp tải xuống.");
      return;
    }

    if (type === "adblock") {
      els.drawerTitle.textContent = "Bộ lọc quảng cáo";
      // Việc (v0.23.28): tách 2 lớp chặn khác nhau — (1) chặn domain quảng
      // cáo/nhảy trang LUÔN BẬT, không tắt được (đây là phần chính, đủ dùng
      // để chặn quảng cáo tự chuyển hướng) — và (2) ẩn quảng cáo hiển thị
      // trong DOM bằng MutationObserver, có thể TỐN CPU trên trang nhiều
      // quảng cáo động, nên cho bật/tắt riêng, MẶC ĐỊNH TẮT cho mượt hơn.
      const domEnabled = window.lqlqGetAdblockDomEnabled?.() ?? false;
      els.drawerContent.innerHTML = `
        <div class="info-panel">
          <h3>Chặn quảng cáo/chuyển hướng — luôn bật</h3>
          <p>Chặn tự động nhảy sang domain lạ, tab/cửa sổ quảng cáo tự mở, và các miền quảng cáo phổ biến. Đây là phần chính, luôn hoạt động, không tắt được.</p>
        </div>
        <div class="info-panel">
          <h3>Ẩn quảng cáo hiển thị trong trang (DOM)</h3>
          <p>Quét và ẩn phần tử quảng cáo trên trang — có thể làm trang hơi chậm hơn ở các trang có nhiều quảng cáo tự nạp lại liên tục. Tắt đi nếu thấy duyệt web bị giật.</p>
          <label class="reader-toggle-row" style="cursor:pointer">
            <span class="reader-toggle-copy"><b>Ẩn quảng cáo trong trang</b><small>Mặc định tắt để mượt hơn</small></span>
            <input type="checkbox" id="adblockDomToggle" ${domEnabled ? "checked" : ""} onchange="window.lqlqSetAdblockDomEnabled(this.checked)">
          </label>
        </div>
      `;
      return;
    }

    els.drawerTitle.textContent = "Thông tin";
    els.drawerContent.innerHTML = `
      <div class="info-panel">
        <p>Chức năng này sẽ được nối với Android WebView ở bản APK.</p>
      </div>
    `;
  } catch (error) {
    console.warn("lqlq openDrawer:", error);
    els.drawerTitle.textContent = "Thông tin";
    els.drawerContent.innerHTML = `<p class="note">Không thể tải nội dung: ${escapeHtml(String(error?.message || error))}</p>`;
  }
}

function faviconForUrl(url) {
  try {
    const parsed = new URL(url);
    return parsed.origin + "/favicon.ico";
  } catch {
    return "";
  }
}

function faviconFallbackLetter(item) {
  const source = String(item?.title || item?.url || "W").trim();
  return source.charAt(0) || "W";
}

function createHistoryRow(item) {
  const row = document.createElement("div");
  row.className = "history-row";

  const iconWrap = document.createElement("span");
  const image = document.createElement("img");
  image.className = "history-favicon";
  image.alt = "";
  image.loading = "lazy";
  image.referrerPolicy = "no-referrer";
  image.src = item.favicon || faviconForUrl(item.url);

  const fallback = document.createElement("span");
  fallback.className = "history-favicon-fallback hidden";
  fallback.textContent = faviconFallbackLetter(item);

  image.addEventListener("error", () => {
    image.classList.add("hidden");
    fallback.classList.remove("hidden");
  });

  iconWrap.appendChild(image);
  iconWrap.appendChild(fallback);

  const title = document.createElement("div");
  title.className = "history-title";
  title.textContent = item.title || titleFromUrl(item.url) || "Trang";

  row.appendChild(iconWrap);
  row.appendChild(title);

  const removeBtn = document.createElement("button");
  removeBtn.type = "button";
  removeBtn.className = "history-remove-btn";
  removeBtn.title = "Xóa mục này khỏi nhật ký";
  removeBtn.setAttribute("aria-label", "Xóa mục này khỏi nhật ký");
  removeBtn.textContent = "×";
  removeBtn.addEventListener("click", event => {
    event.stopPropagation();
    const profile = currentProfile();
    const index = profile.history.indexOf(item);
    if (index >= 0) {
      profile.history.splice(index, 1);
      saveState();
    }
    renderList(profile.history, "Chưa có lịch sử.", "history");
  });
  row.appendChild(removeBtn);

  row.addEventListener("click", () => {
    navigate(item.url);
    els.drawer.classList.remove("open");
  });

  return row;
}

function renderList(items, emptyText, listType = "generic") {
  if (!items.length) {
    els.drawerContent.innerHTML = `<p class="note">${emptyText}</p>`;
    return;
  }

  els.drawerContent.innerHTML = "";

  if (listType === "history") {
    items.forEach(item => {
      els.drawerContent.appendChild(createHistoryRow(item));
    });
    return;
  }

  items.forEach(item => {
    const row = document.createElement("div");
    row.className = "list-row";

    const title = document.createElement("b");
    title.textContent = item.title || "Trang";
    row.appendChild(title);

    if (item.url) {
      const detail = document.createElement("small");
      detail.textContent = item.url;
      row.appendChild(detail);
    }

    row.addEventListener("click", () => {
      if (item.url) {
        navigate(item.url);
      }
      els.drawer.classList.remove("open");
    });

    els.drawerContent.appendChild(row);
  });
}


function safeFilename(value) {
  const cleaned = String(value || "trang-web")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^\w.-]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 100);

  return cleaned || "trang-web";
}

function collectLocalStyles() {
  const blocks = [];

  for (const sheet of Array.from(document.styleSheets)) {
    try {
      const rules = Array.from(sheet.cssRules || [])
        .map(rule => rule.cssText)
        .join("\n");
      if (rules) blocks.push(rules);
    } catch {
      // Stylesheet ngoài miền có thể không cho đọc vì CORS.
    }
  }

  return blocks.join("\n");
}

function absolutizeSnapshotUrls(root, baseUrl) {
  const attributes = ["src", "href", "poster", "action"];

  root.querySelectorAll("*").forEach(element => {
    attributes.forEach(attribute => {
      const value = element.getAttribute(attribute);
      if (!value) return;
      if (
        value.startsWith("data:")
        || value.startsWith("blob:")
        || value.startsWith("#")
        || value.startsWith("javascript:")
      ) {
        return;
      }

      try {
        element.setAttribute(
          attribute,
          new URL(value, baseUrl).href
        );
      } catch {}
    });

    element.removeAttribute("onclick");
    element.removeAttribute("onload");
    element.removeAttribute("onerror");
  });
}

function buildOfflineHtmlSnapshot() {
  const tab = activeTab();
  const sourceUrl = tab?.url || location.href;
  const title = tab?.title || document.title || "Trang web";

  // Trong prototype, #pageView là nội dung trang đang hiển thị.
  // Ở APK/WebView sau này, native sẽ dùng WebView.saveWebArchive().
  const contentClone = els.pageView.cloneNode(true);
  contentClone.removeAttribute("style");
  contentClone.querySelectorAll(
    "script, iframe, .toast, .chrome-menu, .side-drawer, .context-video-toolbar, .global-screen-lock"
  ).forEach(node => node.remove());

  absolutizeSnapshotUrls(contentClone, sourceUrl);

  const styles = collectLocalStyles();

  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <base href="${escapeHtml(sourceUrl)}">
  <style>
    ${styles}
    body {
      overflow: auto !important;
      min-height: 100vh;
      padding: 20px;
      background: #eef3f8;
    }
    #pageView {
      transform: none !important;
      margin: 0 auto;
    }
  </style>
</head>
<body>
  <!-- Bản lưu ngoại tuyến tạo từ lqlq Browser Prototype -->
  <!-- Nguồn: ${escapeHtml(sourceUrl)} -->
  ${contentClone.outerHTML}
</body>
</html>`;
}

function downloadCurrentPageHtml() {
  try {
    const tab = activeTab();
    const html = buildOfflineHtmlSnapshot();
    const blob = new Blob(
      ["\ufeff", html],
      { type: "text/html;charset=utf-8" }
    );
    const objectUrl = URL.createObjectURL(blob);
    const filename =
      safeFilename(tab?.title || titleFromUrl(tab?.url)) +
      "_offline.html";

    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    anchor.target = "_self";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    setTimeout(() => URL.revokeObjectURL(objectUrl), 3000);

    currentProfile().downloads.unshift({
      title: filename,
      url: filename,
      type: "html-offline"
    });
    currentProfile().downloads = currentProfile().downloads.slice(0, 100);
    saveState();

    toast("Đã tải bản HTML ngoại tuyến.");
  } catch (error) {
    toast("Không thể tạo bản HTML: " + error.message);
  }
}

function clearData() {
  if (!confirm("Xóa lịch sử, dấu trang và danh sách tải xuống hiện tại?")) return;

  currentProfile().history = [];
  currentProfile().bookmarks = [];
  currentProfile().downloads = [];
  saveState();
  render();
  toast("Đã xóa dữ liệu duyệt web.");
}

function openToolsMenu() {
  els.chromeMenu.classList.add("hidden");
  els.toolsMenu.classList.remove("hidden");
}

function closeMenus() {
  els.chromeMenu.classList.add("hidden");
  els.toolsMenu.classList.add("hidden");
}

function handleAction(action) {
  switch (action) {
    case "new-tab":
    case "new-window":
      closeMenus();
      newTab();
      break;

    case "history":
      closeMenus();
      openDrawer("history");
      break;

    case "bookmarks":
      closeMenus();
      openDrawer("bookmarks");
      break;

    case "downloads":
      closeMenus();
      openDrawer("downloads");
      break;

    case "story-reader":
      closeMenus();
      window.ShieldReader?.open();
      break;

    case "save-page-html":
      closeMenus();
      downloadCurrentPageHtml();
      break;

    case "adblock":
      closeMenus();
      openDrawer("adblock");
      break;

    case "clear-data":
      closeMenus();
      clearData();
      break;

    case "zoom-out":
      store.zoom = Math.max(70, store.zoom - 10);
      saveState();
      render();
      break;

    case "zoom-in":
      store.zoom = Math.min(150, store.zoom + 10);
      saveState();
      render();
      break;

    case "fullscreen":
      closeMenus();
      document.documentElement.requestFullscreen?.();
      break;

    case "tools":
      openToolsMenu();
      break;

    case "tools-back":
      els.toolsMenu.classList.add("hidden");
      els.chromeMenu.classList.remove("hidden");
      break;

    case "warp-tools":
      openWarpModal();
      break;

    case "advanced-search":
      openSearchModal();
      break;

    case "exit":
      closeMenus();
      toast("Prototype không thể đóng trình duyệt thật.");
      break;

    default:
      closeMenus();
      openDrawer(action);
      break;
  }
}


let currentVideoObjectUrl = "";
let currentVideoSource = "";
let muteLocked = false;
let controlsLocked = false;
let videoOrientation = "portrait";
let unlockTimer = null;

function setVideoStatus(message) {
  els.videoStatus.textContent = message;
}

function showVideoPanel() {
  closeMenus();
  els.videoPanel.classList.remove("hidden");
}

function hideVideoPanel() {
  if (controlsLocked) {
    setVideoStatus("Hãy mở khóa màn hình trước khi đóng công cụ video.");
    return;
  }
  els.videoPanel.classList.add("hidden");
}

function revokeVideoObjectUrl() {
  if (currentVideoObjectUrl) {
    URL.revokeObjectURL(currentVideoObjectUrl);
    currentVideoObjectUrl = "";
  }
}

function loadVideoSource(source, label) {
  if (!source) return;

  els.videoPlayer.pause();
  els.videoPlayer.removeAttribute("src");
  els.videoPlayer.load();

  currentVideoSource = source;
  els.videoPlayer.src = source;
  els.videoPlayer.load();

  setVideoStatus("Đã mở: " + label);
  toast("Đã nạp video.");
}

function openVideoFromUrl() {
  const value = String(els.videoUrlInput.value || "").trim();
  if (!value) {
    setVideoStatus("Hãy nhập liên kết trực tiếp tới tệp video.");
    return;
  }

  let finalUrl = value;
  if (!/^[a-z]+:\/\//i.test(finalUrl)) {
    finalUrl = "https://" + finalUrl;
  }

  revokeVideoObjectUrl();
  loadVideoSource(finalUrl, finalUrl);
}

function openVideoFromFile(file) {
  if (!file) return;
  revokeVideoObjectUrl();
  currentVideoObjectUrl = URL.createObjectURL(file);
  loadVideoSource(currentVideoObjectUrl, file.name);
}

async function downloadCurrentVideo() {
  if (!currentVideoSource) {
    setVideoStatus("Chưa có video để tải.");
    return;
  }

  if (currentVideoSource.startsWith("blob:")) {
    const anchor = document.createElement("a");
    anchor.href = currentVideoSource;
    anchor.download = "video-lqlq-" + Date.now() + ".mp4";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setVideoStatus("Đã gửi yêu cầu tải file video đang mở.");
    return;
  }

  try {
    const response = await fetch(currentVideoSource, { mode: "cors" });
    if (!response.ok) throw new Error("HTTP " + response.status);

    const blob = await response.blob();
    const tempUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = tempUrl;
    anchor.download = guessVideoFilename(currentVideoSource, blob.type);
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    setTimeout(() => URL.revokeObjectURL(tempUrl), 3000);
    setVideoStatus("Đã bắt đầu tải video.");
  } catch (error) {
    const anchor = document.createElement("a");
    anchor.href = currentVideoSource;
    anchor.target = "_self";
    anchor.download = guessVideoFilename(currentVideoSource, "");
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    setVideoStatus("Trang nguồn không cho tải trực tiếp bằng HTML. Bản APK sẽ xử lý tốt hơn với liên kết video trực tiếp; video DRM/luồng bảo vệ sẽ không được vượt khóa.");
  }
}

function guessVideoFilename(url, mimeType) {
  try {
    const parsed = new URL(url);
    const tail = parsed.pathname.split("/").filter(Boolean).pop();
    if (tail && /\.[a-z0-9]{2,5}$/i.test(tail)) return tail;
  } catch {}

  if (/webm/i.test(mimeType)) return "video-" + Date.now() + ".webm";
  return "video-" + Date.now() + ".mp4";
}

async function applyOrientation(nextOrientation) {
  videoOrientation = nextOrientation;
  const landscape = videoOrientation === "landscape";

  els.videoFrame.classList.toggle("landscape", landscape);
  els.videoFrame.classList.toggle("portrait", !landscape);
  els.toggleOrientationBtn.textContent = landscape
    ? "↕ Chuyển dọc"
    : "↔ Chuyển ngang";

  try {
    if (document.fullscreenElement && screen.orientation?.lock) {
      await screen.orientation.lock(landscape ? "landscape" : "portrait");
    }
  } catch {
    // Trình duyệt desktop hoặc file:// có thể không cho khóa hướng.
  }

  setVideoStatus(landscape ? "Đã chuyển khung xem ngang." : "Đã chuyển khung xem dọc.");
}

function toggleOrientation() {
  applyOrientation(videoOrientation === "portrait" ? "landscape" : "portrait");
}

function enforceMuteLock() {
  if (muteLocked && !els.videoPlayer.muted) {
    els.videoPlayer.muted = true;
  }
}

function toggleMuteLock() {
  muteLocked = !muteLocked;

  if (muteLocked) {
    els.videoPlayer.muted = true;
    els.toggleMuteLockBtn.classList.add("active");
    els.toggleMuteLockBtn.textContent = "🔇 Đang khóa tiếng";
    setVideoStatus("Âm thanh đã bị khóa. Nút âm lượng của video không thể bật tiếng.");
  } else {
    els.toggleMuteLockBtn.classList.remove("active");
    els.toggleMuteLockBtn.textContent = "🔇 Khóa tiếng";
    setVideoStatus("Đã bỏ khóa tiếng. Video vẫn đang tắt tiếng cho tới khi bạn tự bật lại.");
  }
}

function setScreenLock(locked) {
  controlsLocked = locked;
  els.screenLockOverlay.classList.toggle("hidden", !locked);
  els.toggleScreenLockBtn.classList.toggle("active", locked);
  els.toggleScreenLockBtn.textContent = locked ? "🔒 Đang khóa màn hình" : "🔒 Khóa màn hình";
  document.body.classList.toggle("video-screen-locked", locked);

  if (locked) {
    els.videoPlayer.controls = false;
    setVideoStatus("Đã khóa các thao tác trên màn hình video.");
  } else {
    els.videoPlayer.controls = true;
    setVideoStatus("Đã mở khóa màn hình video.");
  }
}

function beginUnlockHold(event) {
  event.preventDefault();
  clearTimeout(unlockTimer);
  els.holdToUnlock.textContent = "Đang giữ...";

  unlockTimer = setTimeout(() => {
    setScreenLock(false);
    els.holdToUnlock.textContent = "Giữ để mở khóa";
  }, 1500);
}

function cancelUnlockHold() {
  clearTimeout(unlockTimer);
  unlockTimer = null;
  els.holdToUnlock.textContent = "Giữ để mở khóa";
}

async function toggleVideoFullscreen() {
  try {
    if (!document.fullscreenElement) {
      await els.videoShell.requestFullscreen();
      if (screen.orientation?.lock) {
        await screen.orientation.lock(videoOrientation === "landscape" ? "landscape" : "portrait");
      }
    } else {
      await document.exitFullscreen();
    }
  } catch {
    setVideoStatus("Trình duyệt hiện tại không cho toàn màn hình hoặc khóa hướng.");
  }
}

function stopVideo() {
  els.videoPlayer.pause();
  els.videoPlayer.currentTime = 0;
  setScreenLock(false);
  setVideoStatus("Đã dừng video.");
}


let activeContextVideo = null;
let contextMuteLocked = false;
let contextOrientationMode = "portrait";
let globalUnlockTimer = null;

function isUsablePageVideo(video) {
  if (!(video instanceof HTMLVideoElement)) return false;
  if (video.id === "videoPlayer") return false;
  if (video.closest("#videoPanel")) return false;

  const source = video.currentSrc
    || video.src
    || video.querySelector("source")?.src
    || "";

  return Boolean(source || video.readyState > 0);
}

function bindPageVideo(video) {
  if (!isUsablePageVideo(video) || video.dataset.lqlqVideoBound === "true") {
    return;
  }

  video.dataset.lqlqVideoBound = "true";

  const activate = () => {
    activeContextVideo = video;
    showContextVideoToolbar();
    if (contextMuteLocked) {
      video.muted = true;
    }
  };

  video.addEventListener("play", activate, true);
  video.addEventListener("playing", activate, true);
  video.addEventListener("click", activate, true);

  video.addEventListener("volumechange", () => {
    if (contextMuteLocked && !video.muted) {
      video.muted = true;
    }
  });

  video.addEventListener("ended", () => {
    if (activeContextVideo === video) {
      activeContextVideo = null;
      hideContextVideoToolbar();
    }
  });
}

function scanForPlayableVideos() {
  document.querySelectorAll("video").forEach(bindPageVideo);

  const playing = Array.from(document.querySelectorAll("video"))
    .find(video =>
      isUsablePageVideo(video)
      && !video.paused
      && !video.ended
    );

  if (playing) {
    activeContextVideo = playing;
    showContextVideoToolbar();
  } else if (
    activeContextVideo
    && (!document.contains(activeContextVideo) || activeContextVideo.ended)
  ) {
    activeContextVideo = null;
    hideContextVideoToolbar();
  }
}

function showContextVideoToolbar() {
  if (!activeContextVideo) return;
  els.contextVideoToolbar.classList.remove("hidden");
}

function hideContextVideoToolbar() {
  els.contextVideoToolbar.classList.add("hidden");
}

function activeVideoSource() {
  if (!activeContextVideo) return "";

  return activeContextVideo.currentSrc
    || activeContextVideo.src
    || activeContextVideo.querySelector("source")?.src
    || "";
}

async function downloadActiveContextVideo() {
  const source = activeVideoSource();

  if (!source) {
    toast("Không tìm thấy liên kết video trực tiếp.");
    return;
  }

  if (
    source.startsWith("blob:")
    || source.startsWith("mediasource:")
    || source.startsWith("data:")
  ) {
    toast("Video này dùng luồng tạm hoặc được bảo vệ, không thể tải trực tiếp.");
    return;
  }

  try {
    const response = await fetch(source, { mode: "cors" });
    if (!response.ok) throw new Error("HTTP " + response.status);

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = guessVideoFilename(source, blob.type);
    anchor.target = "_self";
    document.body.appendChild(anchor);

    allowOneNavigation();
    anchor.click();
    anchor.remove();

    setTimeout(() => URL.revokeObjectURL(url), 3000);
    toast("Đã bắt đầu tải video.");
  } catch {
    toast("Trang không cho HTML tải trực tiếp. Bản APK sẽ xử lý liên kết video trực tiếp tốt hơn.");
  }
}

async function toggleContextOrientation() {
  if (!activeContextVideo) return;

  contextOrientationMode =
    contextOrientationMode === "portrait"
      ? "landscape"
      : "portrait";

  const landscape = contextOrientationMode === "landscape";

  try {
    if (!document.fullscreenElement) {
      await activeContextVideo.requestFullscreen();
    }

    if (screen.orientation?.lock) {
      await screen.orientation.lock(
        landscape ? "landscape" : "portrait"
      );
    }
  } catch {
    activeContextVideo.style.width = landscape ? "100vw" : "min(100%, 480px)";
    activeContextVideo.style.height = landscape ? "56.25vw" : "auto";
    activeContextVideo.style.maxHeight = landscape ? "100vh" : "";
  }

  els.contextOrientation.textContent =
    landscape ? "↕ Chuyển dọc" : "↔ Chuyển ngang";
}

function toggleContextMuteLock() {
  contextMuteLocked = !contextMuteLocked;

  if (activeContextVideo && contextMuteLocked) {
    activeContextVideo.muted = true;
  }

  els.contextMuteLock.classList.toggle("active", contextMuteLocked);
  els.contextMuteLock.textContent = contextMuteLocked
    ? "🔇 Đang khóa tiếng"
    : "🔇 Khóa tiếng";
}

function setGlobalScreenLock(locked) {
  els.globalScreenLock.classList.toggle("hidden", !locked);
  els.contextScreenLock.classList.toggle("active", locked);
  els.contextScreenLock.textContent = locked
    ? "🔒 Đang khóa màn hình"
    : "🔒 Khóa màn hình";
}

function startGlobalUnlockHold(event) {
  event.preventDefault();
  clearTimeout(globalUnlockTimer);
  els.globalHoldUnlock.textContent = "Đang giữ...";

  globalUnlockTimer = setTimeout(() => {
    setGlobalScreenLock(false);
    els.globalHoldUnlock.textContent = "Giữ để mở khóa";
  }, 1500);
}

function cancelGlobalUnlockHold() {
  clearTimeout(globalUnlockTimer);
  globalUnlockTimer = null;
  els.globalHoldUnlock.textContent = "Giữ để mở khóa";
}

async function fullscreenActiveContextVideo() {
  if (!activeContextVideo) return;

  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      await activeContextVideo.requestFullscreen();
    }
  } catch {
    toast("Trình duyệt hiện tại không cho toàn màn hình.");
  }
}

const AD_HOST_PARTS = [
  "doubleclick",
  "googlesyndication",
  "googleadservices",
  "adservice",
  "adnxs",
  "popads",
  "popcash",
  "taboola",
  "outbrain",
  "criteo",
  "scorecardresearch",
  "zedo",
  "exoclick",
  "trafficjunky",
  "propellerads",
  "adsterra",
  "clickadu",
  "onclick",
  "popunder"
];

const AD_TEXT_PARTS = [
  "quảng cáo",
  "advertisement",
  "sponsored",
  "casino",
  "bet",
  "bonus",
  "500k",
  "siêu mã",
  "promo"
];

let strictNavigationAllowed = false;
let strictNavigationTimer = null;

function allowOneNavigation() {
  strictNavigationAllowed = true;
  clearTimeout(strictNavigationTimer);
  strictNavigationTimer = setTimeout(() => {
    strictNavigationAllowed = false;
  }, 1200);
}

function toAbsoluteUrl(value) {
  try {
    return new URL(String(value || ""), location.href).href;
  } catch {
    return String(value || "");
  }
}

function isSuspiciousAdUrl(value) {
  const url = toAbsoluteUrl(value).toLowerCase();
  if (!url) return false;

  return AD_HOST_PARTS.some(part => url.includes(part))
    || /(?:^|[/?&_.=-])(ads?|advert|banner|popup|popunder|redirect|clickid|campaign|affiliate)(?:[/?&_.=-]|$)/i.test(url);
}

function looksLikeAdElement(element) {
  if (!(element instanceof Element)) return false;

  const signature = [
    element.id || "",
    element.className || "",
    element.getAttribute("aria-label") || "",
    element.getAttribute("title") || "",
    element.getAttribute("data-ad") || "",
    element.getAttribute("data-ad-slot") || "",
    element.getAttribute("data-ad-client") || ""
  ].join(" ").toLowerCase();

  const text = String(element.innerText || element.textContent || "")
    .trim()
    .toLowerCase()
    .slice(0, 250);

  const src = element.getAttribute("src")
    || element.getAttribute("href")
    || element.getAttribute("action")
    || "";

  if (isSuspiciousAdUrl(src)) return true;

  if (/(^|[-_ ])(ads?|advert|advertisement|banner|sponsor|popup|popunder)([-_ ]|$)/i.test(signature)) {
    return true;
  }

  return AD_TEXT_PARTS.some(part => text.includes(part))
    && text.length < 220;
}

function blockAdElement(element) {
  if (!(element instanceof Element)) return;
  if (element.closest("#chromeMenu, #toolsMenu, #videoPanel, #contextVideoToolbar, #globalScreenLock, #imageEditorShell, #imageEditorBackdrop, #chapterClipperPanel")) {
    return;
  }

  if (element.dataset.lqlqBlockCounted !== "true") {
    element.dataset.lqlqBlockCounted = "true";
    document.dispatchEvent(new CustomEvent("shield:block", {
      detail: { kind: "ad" }
    }));
  }
  element.setAttribute("data-lqlq-ad-blocked", "true");
  element.style.setProperty("display", "none", "important");
  element.style.setProperty("visibility", "hidden", "important");
  element.style.setProperty("pointer-events", "none", "important");
}

function sanitizeElement(element) {
  if (!(element instanceof Element)) return;

  if (looksLikeAdElement(element)) {
    blockAdElement(element);
    return;
  }

  if (element.matches("a[target='_blank'], a[target='blank']")) {
    element.setAttribute("target", "_self");
    element.removeAttribute("rel");
  }

  if (element.matches("form[target='_blank'], form[target='blank']")) {
    element.setAttribute("target", "_self");
  }

  const inlineHandler = element.getAttribute("onclick") || "";
  if (/window\.open|popunder|popup|open\(/i.test(inlineHandler)) {
    element.removeAttribute("onclick");
  }

  if (element.matches("iframe, script, img, video, source")) {
    const source = element.getAttribute("src") || "";
    if (isSuspiciousAdUrl(source)) {
      blockAdElement(element);
      element.removeAttribute("src");
    }
  }

  element.querySelectorAll?.(
    "a[target='_blank'],a[target='blank'],form[target='_blank'],form[target='blank'],iframe,script,img,[class*='ad' i],[id*='ad' i],[class*='popup' i],[id*='popup' i],[class*='banner' i],[id*='banner' i]"
  ).forEach(child => sanitizeElement(child));
}

function isOverlayAdvertisement(element) {
  const candidate = element?.closest?.("a,button,div,section,aside,iframe");
  if (!candidate) return false;
  if (candidate.closest("#chromeMenu, #toolsMenu, #videoPanel, #contextVideoToolbar, #globalScreenLock, #imageEditorShell, #imageEditorBackdrop, #chapterClipperPanel")) {
    return false;
  }

  if (looksLikeAdElement(candidate)) return true;

  try {
    const style = getComputedStyle(candidate);
    const zIndex = Number.parseInt(style.zIndex || "0", 10);
    const rect = candidate.getBoundingClientRect();

    return (style.position === "fixed" || style.position === "absolute")
      && zIndex >= 100
      && rect.width >= 80
      && rect.height >= 40
      && AD_TEXT_PARTS.some(part =>
        String(candidate.innerText || candidate.textContent || "")
          .toLowerCase()
          .includes(part)
      );
  } catch {
    return false;
  }
}

function installStrictAdAndPopupBlocker() {
  try {
    Object.defineProperty(window, "open", {
      configurable: false,
      writable: false,
      value: function() {
        document.dispatchEvent(new CustomEvent("shield:block", {
          detail: { kind: "popup" }
        }));
        toast("Đã chặn cửa sổ hoặc tab mới.");
        return null;
      }
    });
  } catch {
    window.open = function() {
      toast("Đã chặn cửa sổ hoặc tab mới.");
      return null;
    };
  }

  document.addEventListener("click", event => {
    const element = event.target instanceof Element ? event.target : null;
    if (!element) return;

    if (isOverlayAdvertisement(element)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      blockAdElement(element.closest("a,button,div,section,aside,iframe") || element);
      toast("Đã chặn quảng cáo.");
      return;
    }

    const anchor = element.closest("a");
    if (!anchor) return;

    const href = anchor.href || anchor.getAttribute("href") || "";

    if (isSuspiciousAdUrl(href) || looksLikeAdElement(anchor)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      document.dispatchEvent(new CustomEvent("shield:block", {
        detail: { kind: "redirect" }
      }));
      blockAdElement(anchor);
      toast("Đã chặn liên kết quảng cáo.");
      return;
    }

    const asksForNewTab =
      anchor.target === "_blank"
      || anchor.target === "blank"
      || event.ctrlKey
      || event.metaKey
      || event.shiftKey
      || event.altKey;

    if (asksForNewTab) {
      event.preventDefault();
      event.stopImmediatePropagation();

      if (href && !href.startsWith("javascript:") && !href.startsWith("#")) {
        allowOneNavigation();
        location.href = href;
      }

      toast("Đã giữ liên kết trong tab hiện tại.");
    }
  }, true);


  let lastTouchTarget = null;
  let lastTouchTime = 0;

  document.addEventListener("touchstart", event => {
    const touchTarget = event.target instanceof Element
      ? event.target
      : null;

    lastTouchTarget = touchTarget;
    lastTouchTime = Date.now();

    if (touchTarget && isOverlayAdvertisement(touchTarget)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      blockAdElement(
        touchTarget.closest("a,button,div,section,aside,iframe")
        || touchTarget
      );
      toast("Đã chặn quảng cáo trên Android.");
    }
  }, { capture: true, passive: false });

  document.addEventListener("touchend", event => {
    const element = event.target instanceof Element
      ? event.target
      : lastTouchTarget;

    if (!element) return;

    const anchor = element.closest?.("a");
    if (!anchor) return;

    const href = anchor.href || anchor.getAttribute("href") || "";

    if (
      isSuspiciousAdUrl(href)
      || looksLikeAdElement(anchor)
      || isOverlayAdvertisement(anchor)
    ) {
      event.preventDefault();
      event.stopImmediatePropagation();
      blockAdElement(anchor);
      toast("Đã chặn liên kết quảng cáo trên Android.");
      return;
    }

    if (
      anchor.target === "_blank"
      || anchor.target === "blank"
    ) {
      event.preventDefault();
      event.stopImmediatePropagation();

      if (href && !href.startsWith("javascript:")) {
        allowOneNavigation();
        location.href = href;
      }

      toast("Đã giữ trang trong tab hiện tại.");
    }
  }, { capture: true, passive: false });

  document.addEventListener("pointerdown", event => {
    if (event.pointerType !== "touch") return;

    const element = event.target instanceof Element
      ? event.target
      : null;

    if (element && isOverlayAdvertisement(element)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      blockAdElement(
        element.closest("a,button,div,section,aside,iframe")
        || element
      );
    }
  }, true);

  const nativeAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    const href = this.href || this.getAttribute("href") || "";

    if (isSuspiciousAdUrl(href) || looksLikeAdElement(this)) {
      blockAdElement(this);
      toast("Đã chặn liên kết quảng cáo tự động.");
      return;
    }

    this.target = "_self";
    return nativeAnchorClick.call(this);
  };

  document.addEventListener("auxclick", event => {
    const anchor = event.target instanceof Element
      ? event.target.closest("a")
      : null;

    if (!anchor) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    const href = anchor.href || "";
    if (!isSuspiciousAdUrl(href) && href) {
      allowOneNavigation();
      location.href = href;
    }
    toast("Đã chặn mở tab mới.");
  }, true);

  document.addEventListener("submit", event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;

    const action = form.action || "";
    if (isSuspiciousAdUrl(action) || looksLikeAdElement(form)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      document.dispatchEvent(new CustomEvent("shield:block", {
        detail: { kind: "redirect" }
      }));
      toast("Đã chặn biểu mẫu quảng cáo.");
      return;
    }

    form.target = "_self";
  }, true);

  const nativeSubmit = HTMLFormElement.prototype.submit;
  HTMLFormElement.prototype.submit = function() {
    if (isSuspiciousAdUrl(this.action) || looksLikeAdElement(this)) {
      toast("Đã chặn chuyển hướng quảng cáo.");
      return;
    }
    this.target = "_self";
    return nativeSubmit.call(this);
  };

  sanitizeElement(document.documentElement);

  const observer = new MutationObserver(records => {
    records.forEach(record => {
      record.addedNodes.forEach(node => {
        if (node instanceof Element) sanitizeElement(node);
      });

      if (record.target instanceof Element) {
        sanitizeElement(record.target);
      }
    });

    scanForPlayableVideos();
  });

  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: [
      "src",
      "href",
      "target",
      "action",
      "onclick",
      "class",
      "id",
      "style"
    ]
  });

  setInterval(() => {
    document.querySelectorAll(
      "iframe,script,img,a,form,[class*='ad' i],[id*='ad' i],[class*='popup' i],[id*='popup' i],[class*='banner' i],[id*='banner' i]"
    ).forEach(sanitizeElement);
  }, 1800);
}


const CLOUDFLARE_WARP_PACKAGE = "com.cloudflare.onedotonedotonedotone";
const CLOUDFLARE_WARP_OFFICIAL_URL = "https://one.one.one.one/";
const CLOUDFLARE_WARP_HELP_URL = "https://one.one.one.one/help/";
const CLOUDFLARE_WARP_PLAY_URL =
  "https://play.google.com/store/apps/details?id="
  + CLOUDFLARE_WARP_PACKAGE;

function isAndroidBrowser() {
  return /Android/i.test(navigator.userAgent || "");
}

function openWarpModal() {
  closeMenus();
  els.warpModal.classList.remove("hidden");

  els.warpStatusText.textContent = isAndroidBrowser()
    ? "Nhấn Mở để thử chuyển sang ứng dụng 1.1.1.1 / WARP trên Android."
    : "Trên máy tính, nút Mở sẽ chuyển tới trang chính thức của Cloudflare.";
}

function closeWarpModal() {
  els.warpModal.classList.add("hidden");
}

function openWarpOfficialApp() {
  closeWarpModal();

  if (isAndroidBrowser()) {
    const fallback = encodeURIComponent(CLOUDFLARE_WARP_OFFICIAL_URL);

    // Thử mở app Android chính thức; nếu không có thì trình duyệt dùng URL dự phòng.
    location.href =
      "intent://one.one.one.one/"
      + "#Intent;"
      + "scheme=https;"
      + "package=" + CLOUDFLARE_WARP_PACKAGE + ";"
      + "S.browser_fallback_url=" + fallback + ";"
      + "end";
    return;
  }

  // Luôn mở trong tab hiện tại, không tạo tab mới.
  allowOneNavigation();
  location.href = CLOUDFLARE_WARP_OFFICIAL_URL;
}

function installWarpOfficialApp() {
  closeWarpModal();

  if (isAndroidBrowser()) {
    // Dùng trang Play Store HTTPS để có fallback tốt hơn market://.
    allowOneNavigation();
    location.href = CLOUDFLARE_WARP_PLAY_URL;
    return;
  }

  allowOneNavigation();
  location.href = CLOUDFLARE_WARP_OFFICIAL_URL;
}

function checkWarpOfficialConnection() {
  closeWarpModal();

  // Trang help chính thức cho biết thiết bị có đang dùng 1.1.1.1/WARP hay không.
  allowOneNavigation();
  location.href = CLOUDFLARE_WARP_HELP_URL;
}

function toast(message) {
  els.toast.textContent = message;
  els.toast.classList.remove("hidden");

  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => {
    els.toast.classList.add("hidden");
  }, 2200);
}

function escapeHtml(value) {
  return String(value || "").replace(/[&<>"']/g, character => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;"
  }[character]));
}

els.goBtn.addEventListener("click", () => navigate(els.addressInput.value));
els.addressInput.addEventListener("keydown", event => {
  if (event.key === "Enter") navigate(els.addressInput.value);
});

els.fakeSearchBtn.addEventListener("click", () => navigate(els.fakeSearch.value));
els.fakeSearch.addEventListener("keydown", event => {
  if (event.key === "Enter") navigate(els.fakeSearch.value);
});

document.querySelectorAll(".quick-grid button").forEach(button => {
  button.addEventListener("click", () => navigate(button.dataset.url));
});

els.reloadBtn.addEventListener("click", () => {
  toast("Đã tải lại · Bộ lọc quảng cáo vẫn bật.");
});

els.backBtn.addEventListener("click", () => {
  toast("Back/Forward sẽ nối với WebView ở bản APK.");
});

els.forwardBtn.addEventListener("click", () => {
  toast("Back/Forward sẽ nối với WebView ở bản APK.");
});

els.menuBtn.addEventListener("click", event => {
  event.stopPropagation();
  els.toolsMenu.classList.add("hidden");
  els.chromeMenu.classList.toggle("hidden");
});

els.chromeMenu.addEventListener("click", event => {
  const button = event.target.closest("button[data-action]");
  if (button) handleAction(button.dataset.action);
});

els.toolsMenu.addEventListener("click", event => {
  const button = event.target.closest("button[data-action]");
  if (button) handleAction(button.dataset.action);
});

document.addEventListener("click", event => {
  const insideMain = els.chromeMenu.contains(event.target);
  const insideTools = els.toolsMenu.contains(event.target);

  if (!insideMain && !insideTools && event.target !== els.menuBtn) {
    closeMenus();
  }
});

els.cancelSearch.addEventListener("click", closeSearchModal);
els.submitSearch.addEventListener("click", submitHiddenSearch);
els.searchInput.addEventListener("keydown", event => {
  if (event.key === "Enter") submitHiddenSearch();
});

els.searchModal.addEventListener("click", event => {
  if (event.target === els.searchModal) closeSearchModal();
});

els.closeDrawer.addEventListener("click", () => {
  els.drawer.classList.remove("open");
});

document.addEventListener("keydown", event => {
  if (event.ctrlKey && event.key.toLowerCase() === "t") {
    event.preventDefault();
    newTab();
  }
});


els.closeVideoPanel.addEventListener("click", hideVideoPanel);
els.openVideoUrl.addEventListener("click", openVideoFromUrl);
els.videoUrlInput.addEventListener("keydown", event => {
  if (event.key === "Enter") openVideoFromUrl();
});

els.videoFileInput.addEventListener("change", event => {
  openVideoFromFile(event.target.files?.[0]);
});

els.downloadVideoBtn.addEventListener("click", downloadCurrentVideo);
els.toggleOrientationBtn.addEventListener("click", toggleOrientation);
els.toggleMuteLockBtn.addEventListener("click", toggleMuteLock);
els.toggleScreenLockBtn.addEventListener("click", () => setScreenLock(!controlsLocked));
els.toggleFullscreenBtn.addEventListener("click", toggleVideoFullscreen);
els.stopVideoBtn.addEventListener("click", stopVideo);

els.videoPlayer.addEventListener("volumechange", enforceMuteLock);
els.videoPlayer.addEventListener("loadedmetadata", () => {
  const naturalLandscape = els.videoPlayer.videoWidth >= els.videoPlayer.videoHeight;
  applyOrientation(naturalLandscape ? "landscape" : "portrait");
});

els.holdToUnlock.addEventListener("pointerdown", beginUnlockHold);
els.holdToUnlock.addEventListener("pointerup", cancelUnlockHold);
els.holdToUnlock.addEventListener("pointercancel", cancelUnlockHold);
els.holdToUnlock.addEventListener("pointerleave", cancelUnlockHold);

document.addEventListener("fullscreenchange", () => {
  if (!document.fullscreenElement && screen.orientation?.unlock) {
    try { screen.orientation.unlock(); } catch {}
  }
});

installStrictAdAndPopupBlocker();



els.contextDownloadVideo.addEventListener("click", downloadActiveContextVideo);
els.contextOrientation.addEventListener("click", toggleContextOrientation);
els.contextMuteLock.addEventListener("click", toggleContextMuteLock);
els.contextScreenLock.addEventListener("click", () => setGlobalScreenLock(true));
els.contextFullscreen.addEventListener("click", fullscreenActiveContextVideo);

els.globalHoldUnlock.addEventListener("pointerdown", startGlobalUnlockHold);
els.globalHoldUnlock.addEventListener("pointerup", cancelGlobalUnlockHold);
els.globalHoldUnlock.addEventListener("pointercancel", cancelGlobalUnlockHold);
els.globalHoldUnlock.addEventListener("pointerleave", cancelGlobalUnlockHold);

document.addEventListener("fullscreenchange", () => {
  if (!document.fullscreenElement && screen.orientation?.unlock) {
    try {
      screen.orientation.unlock();
    } catch {}
  }
});

scanForPlayableVideos();



els.openWarpApp.addEventListener("click", openWarpOfficialApp);
els.installWarpApp.addEventListener("click", installWarpOfficialApp);
els.checkWarpConnection.addEventListener("click", checkWarpOfficialConnection);
els.closeWarpModal.addEventListener("click", closeWarpModal);

els.warpModal.addEventListener("click", event => {
  if (event.target === els.warpModal) {
    closeWarpModal();
  }
});



document.querySelectorAll("[data-ui-action]").forEach(button => {
  button.addEventListener("click", () => {
    handleAction(button.dataset.uiAction);
  });
});

loadState();
render();
