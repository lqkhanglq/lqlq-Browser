(() => {
  "use strict";

  const DEFAULT_SAVED_PAGES = [
    {
      id: "default-google",
      title: "Google",
      url: "https://www.google.com",
      kind: "google",
      fallback: "G"
    },
    {
      id: "default-chatgpt",
      title: "ChatGPT",
      url: "https://chatgpt.com",
      kind: "chatgpt",
      fallback: "◎"
    },
    {
      id: "default-claude",
      title: "Claude",
      url: "https://claude.ai",
      kind: "claude",
      fallback: "✳"
    },
    {
      id: "default-gemini",
      title: "Gemini",
      url: "https://gemini.google.com",
      kind: "gemini",
      fallback: "✦"
    },
    {
      id: "default-grok",
      title: "Grok",
      url: "https://grok.com",
      kind: "grok",
      fallback: "𝕏"
    },
    {
      id: "default-perplexity",
      title: "Perplexity",
      url: "https://www.perplexity.ai",
      kind: "perplexity",
      fallback: "P"
    }
  ];

  const refs = {
    grid: document.getElementById("savedShortcutGrid"),
    saveHome: document.getElementById("homeSaveCurrentPage"),
    openHome: document.getElementById("homeOpenSavedPages"),
    saveToolbar: document.getElementById("desktopSavePageBtn")
  };

  function canonicalUrl(value) {
    try {
      const url = new URL(String(value || "").trim());
      url.hash = "";
      if (url.pathname === "/") {
        url.pathname = "";
      }
      return url.href.replace(/\/$/, "").toLowerCase();
    } catch {
      return String(value || "").trim().replace(/\/$/, "").toLowerCase();
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
    const known = DEFAULT_SAVED_PAGES.find(page => page.kind === kind);
    if (known) return known.fallback;

    return String(item.title || pageHost(item.url) || "W")
      .trim()
      .charAt(0)
      .toUpperCase() || "W";
  }

  function faviconUrl(item) {
    if (item.favicon) return item.favicon;

    try {
      const url = new URL(item.url);
      return `${url.origin}/favicon.ico`;
    } catch {
      return "";
    }
  }

  function normalizeBookmark(item) {
    const url = String(item?.url || "").trim();
    const title = String(
      item?.title
      || titleFromUrl(url)
      || pageHost(url)
      || "Trang"
    ).trim();

    return {
      ...item,
      id: item?.id || uid(),
      title,
      url,
      kind: item?.kind || knownKind(url),
      favicon: item?.favicon || faviconForUrl(url),
      savedAt: item?.savedAt || new Date().toISOString()
    };
  }

  function ensureDefaultsForProfile(profile) {
    profile.bookmarks = Array.isArray(profile.bookmarks)
      ? profile.bookmarks.map(normalizeBookmark)
      : [];

    if (profile.savedPagesInitializedV19) {
      return false;
    }

    const existing = new Set(
      profile.bookmarks.map(item => canonicalUrl(item.url))
    );

    for (const defaultPage of DEFAULT_SAVED_PAGES) {
      if (existing.has(canonicalUrl(defaultPage.url))) continue;

      profile.bookmarks.push({
        ...defaultPage,
        favicon: faviconForUrl(defaultPage.url),
        savedAt: new Date().toISOString()
      });
    }

    profile.savedPagesInitializedV19 = true;
    return true;
  }

  function ensureSavedPages() {
    const changedNormal = ensureDefaultsForProfile(store.normal);
    const changedPrivate = ensureDefaultsForProfile(store.private);

    if (changedNormal || changedPrivate) {
      saveState();
    }
  }

  function currentBookmarks() {
    ensureDefaultsForProfile(currentProfile());
    return currentProfile().bookmarks;
  }

  function findBookmarkIndex(url) {
    const target = canonicalUrl(url);
    return currentBookmarks().findIndex(
      item => canonicalUrl(item.url) === target
    );
  }

  function isCurrentPageSaved() {
    const tab = activeTab();
    return Boolean(tab?.url && findBookmarkIndex(tab.url) >= 0);
  }

  function saveCurrentPage() {
    const tab = activeTab();

    if (!tab?.url) {
      toast("Không có trang nào để lưu.");
      return;
    }

    const url = String(tab.url).trim();
    const title = String(
      tab.title
      || titleFromUrl(url)
      || pageHost(url)
      || "Trang đã lưu"
    ).trim();

    const bookmarks = currentBookmarks();
    const existingIndex = findBookmarkIndex(url);

    const entry = normalizeBookmark({
      id: existingIndex >= 0 ? bookmarks[existingIndex].id : uid(),
      title,
      url,
      kind: knownKind(url),
      favicon: faviconForUrl(url),
      savedAt: new Date().toISOString()
    });

    if (existingIndex >= 0) {
      bookmarks.splice(existingIndex, 1);
    }

    bookmarks.unshift(entry);
    currentProfile().bookmarks = bookmarks.slice(0, 80);
    saveState();
    renderSavedShortcuts();
    updateSaveButton();
    toast(`Đã lưu ${title}.`);
  }

  function removeSavedPage(id, event = null) {
    event?.stopPropagation();

    const profile = currentProfile();
    const item = profile.bookmarks.find(entry => entry.id === id);

    profile.bookmarks = profile.bookmarks.filter(entry => entry.id !== id);
    saveState();
    renderSavedShortcuts();
    openSavedPagesDrawer(false);
    updateSaveButton();

    if (item) {
      toast(`Đã xóa ${item.title}.`);
    }
  }

  function createShortcutIcon(item) {
    const icon = document.createElement("span");
    const kind = item.kind || knownKind(item.url);

    icon.className = `saved-shortcut-icon${kind ? ` ${kind}` : ""}`;

    const image = document.createElement("img");
    image.alt = "";
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.src = faviconUrl(item);

    const fallback = document.createElement("span");
    fallback.textContent = fallbackSymbol(item);

    image.addEventListener("load", () => {
      fallback.classList.add("hidden");
    });

    image.addEventListener("error", () => {
      image.classList.add("hidden");
      fallback.classList.remove("hidden");
    });

    icon.append(image, fallback);
    return icon;
  }

  function renderSavedShortcuts() {
    if (!refs.grid) return;

    const bookmarks = currentBookmarks().slice(0, 6);
    refs.grid.innerHTML = "";

    if (!bookmarks.length) {
      refs.grid.innerHTML = `
        <div class="saved-shortcut-empty">
          <b>Chưa có trang đã lưu</b>
          <small>Mở một website rồi chọn “Lưu trang hiện tại”.</small>
        </div>
      `;
      return;
    }

    bookmarks.forEach(item => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "saved-shortcut-item";
      button.title = item.url;

      const icon = createShortcutIcon(item);
      const title = document.createElement("b");
      title.textContent = item.title;

      button.append(icon, title);
      button.addEventListener("click", () => navigate(item.url));
      refs.grid.appendChild(button);
    });
  }

  function createSavedRow(item) {
    const row = document.createElement("article");
    row.className = "saved-page-row";

    const icon = document.createElement("span");
    icon.className = "saved-page-row-icon";

    const image = document.createElement("img");
    image.alt = "";
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.src = faviconUrl(item);

    const fallback = document.createElement("span");
    fallback.textContent = fallbackSymbol(item);

    image.addEventListener("load", () => fallback.classList.add("hidden"));
    image.addEventListener("error", () => {
      image.classList.add("hidden");
      fallback.classList.remove("hidden");
    });

    icon.append(image, fallback);

    const copy = document.createElement("div");
    copy.className = "saved-page-row-copy";

    const title = document.createElement("b");
    title.textContent = item.title;

    const detail = document.createElement("small");
    detail.textContent = pageHost(item.url) || item.url;

    copy.append(title, detail);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "saved-page-row-remove";
    remove.title = "Xóa trang đã lưu";
    remove.setAttribute("aria-label", `Xóa ${item.title}`);
    remove.textContent = "×";
    remove.addEventListener("click", event => {
      removeSavedPage(item.id, event);
    });

    row.append(icon, copy, remove);
    row.addEventListener("click", () => {
      navigate(item.url);
      els.drawer.classList.remove("open");
    });

    return row;
  }

  function openSavedPagesDrawer(announce = true) {
    closeMenus();
    els.drawer.classList.add("open");
    els.drawerTitle.textContent = "Trang đã lưu";
    els.drawerContent.innerHTML = "";

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
    close.addEventListener("click", () => {
      els.drawer.classList.remove("open");
    });

    toolbar.append(save, close);
    els.drawerContent.appendChild(toolbar);

    const bookmarks = currentBookmarks();

    const count = document.createElement("div");
    count.className = "saved-pages-count";
    count.innerHTML = `
      <span>${bookmarks.length} trang</span>
      <span>Nhấn vào một trang để mở</span>
    `;
    els.drawerContent.appendChild(count);

    if (!bookmarks.length) {
      const empty = document.createElement("p");
      empty.className = "note";
      empty.textContent = "Chưa có trang đã lưu.";
      els.drawerContent.appendChild(empty);
    } else {
      bookmarks.forEach(item => {
        els.drawerContent.appendChild(createSavedRow(item));
      });
    }

    if (announce) {
      toast("Đã mở danh sách trang đã lưu.");
    }
  }

  function updateSaveButton() {
    if (!refs.saveToolbar) return;

    const saved = isCurrentPageSaved();
    refs.saveToolbar.classList.toggle("saved", saved);
    refs.saveToolbar.title = saved
      ? "Trang này đã được lưu"
      : "Lưu trang hiện tại";
    refs.saveToolbar.setAttribute(
      "aria-label",
      refs.saveToolbar.title
    );
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

  const previousRender = render;
  render = function renderWithSavedPages() {
    previousRender();
    renderSavedShortcuts();
    updateSaveButton();
  };

  refs.saveHome?.addEventListener("click", saveCurrentPage);
  refs.openHome?.addEventListener("click", () => openSavedPagesDrawer());
  refs.saveToolbar?.addEventListener("click", saveCurrentPage);

  ensureSavedPages();
  renderSavedShortcuts();
  updateSaveButton();

  window.LqlqSavedPages = {
    open: openSavedPagesDrawer,
    saveCurrent: saveCurrentPage,
    remove: removeSavedPage,
    render: renderSavedShortcuts,
    defaults: DEFAULT_SAVED_PAGES
  };
})();