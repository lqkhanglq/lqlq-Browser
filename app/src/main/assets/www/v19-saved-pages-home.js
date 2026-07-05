(() => {
  "use strict";

  /**
   * Trang thẻ mới và dấu trang được tách riêng như trình duyệt thật:
   * - DEFAULT_SHORTCUTS: lối tắt cục bộ trên trang thẻ mới, không phải bookmark.
   * - profile.bookmarks: chỉ chứa trang người dùng chủ động lưu.
   *
   * Không tải /favicon.ico trên trang chủ. Icon chỉ dùng dữ liệu favicon đã
   * được WebChromeClient lưu cục bộ hoặc ký tự thay thế, nên thẻ mới hiện ngay.
   */
  const DEFAULT_SHORTCUTS = [
    { id: "shortcut-google", title: "Google", url: "https://www.google.com", kind: "google", fallback: "G" },
    { id: "shortcut-chatgpt", title: "ChatGPT", url: "https://chatgpt.com", kind: "chatgpt", fallback: "◎" },
    { id: "shortcut-claude", title: "Claude", url: "https://claude.ai", kind: "claude", fallback: "✳" },
    { id: "shortcut-gemini", title: "Gemini", url: "https://gemini.google.com", kind: "gemini", fallback: "✦" },
    { id: "shortcut-grok", title: "Grok", url: "https://grok.com", kind: "grok", fallback: "𝕏" },
    { id: "shortcut-perplexity", title: "Perplexity", url: "https://www.perplexity.ai", kind: "perplexity", fallback: "P" }
  ];

  const refs = {
    grid: document.getElementById("savedShortcutGrid"),
    saveHome: document.getElementById("homeSaveCurrentPage"),
    openHome: document.getElementById("homeOpenSavedPages"),
    saveToolbar: document.getElementById("desktopSavePageBtn")
  };

  let lastGridSignature = "";
  let scheduledRender = 0;
  let faviconObserver = null;

  function scheduleFaviconHydration(icon, hydrate) {
    if (typeof IntersectionObserver === "function") {
      if (!faviconObserver) {
        faviconObserver = new IntersectionObserver(entries => {
          for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            faviconObserver.unobserve(entry.target);
            const run = entry.target.__lqlqHydrateFavicon;
            delete entry.target.__lqlqHydrateFavicon;
            if (typeof run === "function") run();
          }
        }, { rootMargin: "120px" });
      }
      icon.__lqlqHydrateFavicon = hydrate;
      faviconObserver.observe(icon);
      return;
    }

    const schedule = typeof requestIdleCallback === "function"
      ? callback => requestIdleCallback(callback, { timeout: 500 })
      : callback => setTimeout(callback, 100);
    schedule(hydrate);
  }

  function canonicalUrl(value) {
    try {
      const url = new URL(String(value || "").trim());
      url.hash = "";
      url.protocol = url.protocol.toLowerCase();
      url.hostname = url.hostname.toLowerCase();
      if (url.pathname === "/") url.pathname = "";
      return url.href.replace(/\/$/, "");
    } catch {
      return String(value || "").trim().replace(/\/$/, "");
    }
  }

  function pageHost(value) {
    try {
      return new URL(value).hostname.replace(/^www\./, "");
    } catch {
      return String(value || "");
    }
  }

  function knownKind(url) {
    const host = pageHost(url).toLowerCase();
    if (host.includes("google.com") && !host.includes("gemini")) return "google";
    if (host.includes("chatgpt.com") || host.includes("openai.com")) return "chatgpt";
    if (host.includes("claude.ai") || host.includes("claude.com")) return "claude";
    if (host.includes("gemini.google.com")) return "gemini";
    if (host.includes("grok.com") || host.includes("x.ai")) return "grok";
    if (host.includes("perplexity.ai")) return "perplexity";
    return "";
  }

  function fallbackSymbol(item) {
    const kind = item.kind || knownKind(item.url);
    const known = DEFAULT_SHORTCUTS.find(page => page.kind === kind);
    if (known) return known.fallback;
    return String(item.title || pageHost(item.url) || "W").trim().charAt(0).toUpperCase() || "W";
  }

  function localFaviconData(item) {
    const candidate = String(item?.faviconData || item?.favicon || "");
    return /^data:image\/(?:png|webp|jpeg|jpg|gif);base64,/i.test(candidate)
      ? candidate
      : "";
  }

  function normalizeBookmark(item) {
    const url = String(item?.url || "").trim();
    const title = String(
      item?.title || titleFromUrl(url) || pageHost(url) || "Trang"
    ).trim();

    return {
      id: item?.id || uid(),
      title,
      url,
      kind: item?.kind || knownKind(url),
      // Favicon nằm trong cache native theo URL, không nhét base64 vào
      // localStorage. Trạng thái nhẹ hơn nhiều và tránh đầy quota.
      faviconData: "",
      offlineUri: String(item?.offlineUri || ""),
      savedAt: item?.savedAt || new Date().toISOString()
    };
  }

  function migrateProfile(profile) {
    const old = Array.isArray(profile.bookmarks) ? profile.bookmarks : [];
    const seen = new Set();
    const migrated = [];

    for (const raw of old) {
      // Các mục default-* của v0.19 là lối tắt trang chủ bị trộn nhầm vào
      // bookmark. Bỏ chúng khỏi danh sách đã lưu; shortcut vẫn hiện cục bộ.
      if (String(raw?.id || "").startsWith("default-")) continue;
      const item = normalizeBookmark(raw);
      if (!item.url) continue;
      const key = canonicalUrl(item.url);
      if (!key || seen.has(key)) continue;
      seen.add(key);
      migrated.push(item);
    }

    profile.bookmarks = migrated.slice(0, 80);
    profile.savedPagesInitializedV19 = true;
    profile.savedPagesSchemaV25 = 1;
    profile.savedPagesSchemaV26 = 1;
  }

  function ensureSavedPages() {
    const needsNormal = store.normal.savedPagesSchemaV26 !== 1;
    const needsPrivate = store.private.savedPagesSchemaV26 !== 1;
    if (needsNormal) migrateProfile(store.normal);
    if (needsPrivate) migrateProfile(store.private);
    if (needsNormal || needsPrivate) saveState();
  }

  function currentBookmarks() {
    if (currentProfile().savedPagesSchemaV26 !== 1) {
      migrateProfile(currentProfile());
      saveState();
    }
    return currentProfile().bookmarks;
  }

  function readNativeSnapshot() {
    try {
      const raw = window.LqlqAndroid?.getActivePageSnapshot?.();
      const info = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (info && typeof info === "object") {
        return {
          url: String(info.url || "").trim(),
          title: String(info.title || "").trim(),
          faviconData: localFaviconData({ faviconData: info.faviconData }),
          loading: Boolean(info.loading),
          visible: Boolean(info.visible)
        };
      }
    } catch (error) {
      console.warn("Không đọc được snapshot trang native:", error);
    }

    const tab = activeTab();
    return {
      url: String(tab?.url || "").trim(),
      title: String(tab?.title || "").trim(),
      faviconData: "",
      loading: Boolean(tab?.loading),
      visible: Boolean(tab?.url)
    };
  }

  function findBookmarkIndex(url) {
    const target = canonicalUrl(url);
    return currentBookmarks().findIndex(item => canonicalUrl(item.url) === target);
  }

  function isCurrentPageSaved() {
    const snapshot = readNativeSnapshot();
    return Boolean(snapshot.visible && snapshot.url && findBookmarkIndex(snapshot.url) >= 0);
  }

  function saveCurrentPage() {
    const snapshot = readNativeSnapshot();
    const url = snapshot.url;

    if (!snapshot.visible || !url || !/^(https?|file|content):/i.test(url)) {
      toast("Hãy mở một trang web rồi mới lưu.");
      return;
    }

    const title = String(
      snapshot.title || titleFromUrl(url) || pageHost(url) || "Trang đã lưu"
    ).trim();
    const bookmarks = currentBookmarks();
    const existingIndex = findBookmarkIndex(url);
    const existing = existingIndex >= 0 ? bookmarks[existingIndex] : null;

    const entry = normalizeBookmark({
      id: existing?.id || uid(),
      title,
      url,
      kind: knownKind(url),
      faviconData: "",
      offlineUri: existing?.offlineUri || "",
      savedAt: existing?.savedAt || new Date().toISOString()
    });

    if (existingIndex >= 0) bookmarks.splice(existingIndex, 1);
    bookmarks.unshift(entry);
    currentProfile().bookmarks = bookmarks.slice(0, 80);
    saveState();
    invalidateSavedPages();
    toast(existing ? `Đã cập nhật ${title}.` : `Đã lưu ${title}.`);
  }

  function attachOfflineUri(url, offlineUri, title, faviconData = "") {
    if (!url || !offlineUri) return;
    const bookmarks = currentBookmarks();
    const index = findBookmarkIndex(url);

    if (index >= 0) {
      bookmarks[index] = normalizeBookmark({
        ...bookmarks[index],
        title: title || bookmarks[index].title,
        offlineUri,
        faviconData: ""
      });
    } else {
      bookmarks.unshift(normalizeBookmark({
        title: title || titleFromUrl(url) || pageHost(url) || "Trang đã lưu",
        url,
        kind: knownKind(url),
        offlineUri,
        faviconData: ""
      }));
      currentProfile().bookmarks = bookmarks.slice(0, 80);
    }

    saveState();
    invalidateSavedPages();
  }

  function removeSavedPage(id, event = null) {
    event?.stopPropagation();
    const profile = currentProfile();
    const item = currentBookmarks().find(entry => entry.id === id);
    profile.bookmarks = currentBookmarks().filter(entry => entry.id !== id);
    saveState();
    invalidateSavedPages();
    openSavedPagesDrawer(false);
    if (item) toast(`Đã xóa ${item.title}.`);
  }

  function hydrateIconFromNative(icon, fallback, url) {
    if (!url || typeof window.LqlqAndroid?.getCachedFaviconData !== "function") return;

    function attachImage(data) {
      if (!/^data:image\/(?:png|webp|jpeg|jpg|gif);base64,/i.test(String(data || ""))) return;
      if (icon.querySelector("img")) return;
      const image = document.createElement("img");
      image.alt = "";
      image.decoding = "async";
      image.src = data;
      image.addEventListener("load", () => fallback.classList.add("hidden"), { once: true });
      image.addEventListener("error", () => image.remove(), { once: true });
      icon.prepend(image);
    }

    const hydrate = () => {
      if (!icon.isConnected) return;
      try { attachImage(window.LqlqAndroid.getCachedFaviconData(url)); }
      catch (error) { console.debug("Favicon cache chưa sẵn sàng", error); }
    };
    scheduleFaviconHydration(icon, hydrate);
  }

  function createIcon(item, className) {
    const icon = document.createElement("span");
    const kind = item.kind || knownKind(item.url);
    icon.className = `${className}${kind ? ` ${kind}` : ""}`;

    const fallback = document.createElement("span");
    fallback.textContent = fallbackSymbol(item);
    icon.appendChild(fallback);

    // Vẽ chữ/logo CSS ngay trong frame đầu. Chỉ mục không có logo dựng sẵn
    // mới lấy favicon từ cache cục bộ khi sắp xuất hiện trên màn hình.
    if (!kind) hydrateIconFromNative(icon, fallback, item.url);
    return icon;
  }


  function homeItems() {
    const result = [];
    const seen = new Set();

    for (const item of currentBookmarks()) {
      const key = canonicalUrl(item.url);
      if (!key || seen.has(key)) continue;
      result.push(item);
      seen.add(key);
      if (result.length >= 6) return result;
    }

    for (const item of DEFAULT_SHORTCUTS) {
      const key = canonicalUrl(item.url);
      if (seen.has(key)) continue;
      result.push(item);
      seen.add(key);
      if (result.length >= 6) break;
    }
    return result;
  }

  function renderSavedShortcuts(force = false) {
    if (!refs.grid) return;
    const items = homeItems();
    const signature = JSON.stringify(items.map(item => [
      item.id, item.title, item.url
    ]));
    if (!force && signature === lastGridSignature && refs.grid.childElementCount) return;
    lastGridSignature = signature;

    const fragment = document.createDocumentFragment();
    for (const item of items) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "saved-shortcut-item";
      button.title = item.url;

      const title = document.createElement("b");
      title.textContent = item.title;
      button.append(createIcon(item, "saved-shortcut-icon"), title);
      button.addEventListener("click", () => navigate(item.url));
      fragment.appendChild(button);
    }

    refs.grid.replaceChildren(fragment);
  }

  function createSavedRow(item) {
    const row = document.createElement("article");
    row.className = "saved-page-row";

    const copy = document.createElement("div");
    copy.className = "saved-page-row-copy";
    const title = document.createElement("b");
    title.textContent = item.title;
    const detail = document.createElement("small");
    detail.textContent = pageHost(item.url) || item.url;
    copy.append(title, detail);

    if (item.offlineUri) {
      const offlineBadge = document.createElement("span");
      offlineBadge.className = "saved-page-row-offline-badge";
      offlineBadge.title = "Có bản lưu ngoại tuyến";
      offlineBadge.textContent = "⬇";
      copy.appendChild(offlineBadge);
    }

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "saved-page-row-remove";
    remove.title = "Xóa trang đã lưu";
    remove.setAttribute("aria-label", `Xóa ${item.title}`);
    remove.textContent = "×";
    remove.addEventListener("click", event => removeSavedPage(item.id, event));

    row.append(createIcon(item, "saved-page-row-icon"), copy, remove);
    row.addEventListener("click", () => {
      if (item.offlineUri && typeof window.LqlqAndroid?.openOfflineUri === "function") {
        try {
          window.LqlqAndroid.openOfflineUri(item.offlineUri, item.url);
        } catch {
          navigate(item.url);
        }
      } else {
        navigate(item.url);
      }
      els.drawer.classList.remove("open");
    });
    return row;
  }

  function openSavedPagesDrawer(announce = true) {
    closeMenus();
    els.drawer.classList.add("open");
    els.drawerTitle.textContent = "Trang đã lưu";
    els.drawerContent.replaceChildren();

    const toolbar = document.createElement("div");
    toolbar.className = "saved-pages-toolbar";
    const save = document.createElement("button");
    save.type = "button";
    save.className = "primary";
    save.textContent = "☆ Lưu trang hiện tại";
    save.addEventListener("click", saveCurrentPage);
    const close = document.createElement("button");
    close.type = "button";
    close.textContent = "Đóng";
    close.addEventListener("click", () => els.drawer.classList.remove("open"));
    toolbar.append(save, close);

    const bookmarks = currentBookmarks();
    const count = document.createElement("div");
    count.className = "saved-pages-count";
    count.innerHTML = `<span>${bookmarks.length} trang</span><span>Nhấn vào một trang để mở</span>`;

    const fragment = document.createDocumentFragment();
    fragment.append(toolbar, count);
    if (!bookmarks.length) {
      const empty = document.createElement("p");
      empty.className = "note";
      empty.textContent = "Chưa có trang đã lưu.";
      fragment.appendChild(empty);
    } else {
      bookmarks.forEach(item => fragment.appendChild(createSavedRow(item)));
    }
    els.drawerContent.appendChild(fragment);
    if (announce) toast("Đã mở danh sách trang đã lưu.");
  }

  function updateSaveButton() {
    if (!refs.saveToolbar) return;
    const saved = isCurrentPageSaved();
    refs.saveToolbar.classList.toggle("saved", saved);
    refs.saveToolbar.title = saved ? "Trang này đã được lưu" : "Lưu trang hiện tại";
    refs.saveToolbar.setAttribute("aria-label", refs.saveToolbar.title);
  }

  function scheduleHomeRender(force = false) {
    if (scheduledRender) cancelAnimationFrame(scheduledRender);
    scheduledRender = requestAnimationFrame(() => {
      scheduledRender = 0;
      renderSavedShortcuts(force);
      updateSaveButton();
    });
  }

  function invalidateSavedPages() {
    lastGridSignature = "";
    scheduleHomeRender(true);
    window.dispatchEvent(new CustomEvent("lqlq-bookmarks-changed"));
  }

  const previousHandleAction = handleAction;
  handleAction = function handleSavedPageAction(action) {
    if (action === "save-current-page") {
      closeMenus();
      saveCurrentPage();
      return;
    }
    if (action === "bookmarks") {
      openSavedPagesDrawer();
      return;
    }
    previousHandleAction(action);
  };

  refs.saveHome?.addEventListener("click", saveCurrentPage);
  refs.openHome?.addEventListener("click", () => openSavedPagesDrawer());
  refs.saveToolbar?.addEventListener("click", saveCurrentPage);

  [
    "lqlq-home-shown",
    "lqlq-tab-state-changed",
    "lqlq-active-page-changed",
    "lqlq-bookmarks-changed"
  ].forEach(type => window.addEventListener(type, () => scheduleHomeRender()));

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) scheduleHomeRender();
  });

  ensureSavedPages();
  scheduleHomeRender(true);

  window.LqlqLocalFavicon = {
    hydrate: hydrateIconFromNative
  };

  window.LqlqSavedPages = {
    open: openSavedPagesDrawer,
    saveCurrent: saveCurrentPage,
    remove: removeSavedPage,
    attachOfflineUri,
    render: () => renderSavedShortcuts(true),
    defaults: DEFAULT_SHORTCUTS
  };
})();
