(() => {
  "use strict";

  const refs = {
    home: document.getElementById("mobileHomeBtn"),
    newTab: document.getElementById("mobileNewTabBtn"),
    tabs: document.getElementById("mobileTabsBtn"),
    count: document.getElementById("mobileTabCount"),
    overlay: document.getElementById("mobileTabsOverlay"),
    grid: document.getElementById("mobileTabsGrid"),
    add: document.getElementById("mobileTabsAdd"),
    close: document.getElementById("mobileTabsClose"),
    closeOthers: document.getElementById("mobileTabsCloseOthers"),
    closeAll: document.getElementById("mobileTabsCloseAll"),
    menuBackdrop: document.getElementById("mobileMenuBackdrop")
  };

  function isMobileLayout() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function updateMobileTabCount() {
    const profile = currentProfile();
    const count = Math.max(1, profile.tabs.length);
    refs.count.textContent = count > 99 ? ":D" : String(count);
    refs.tabs.setAttribute(
      "aria-label",
      `${count} thẻ đang mở`
    );
  }

  function faviconLetter(tab) {
    const title = String(tab.title || "T").trim();
    return title.charAt(0).toUpperCase() || "T";
  }

  function renderMobileTabs() {
    const profile = currentProfile();
    refs.grid.innerHTML = "";

    profile.tabs.forEach(tab => {
      const card = document.createElement("article");
      card.className =
        "mobile-tab-card"
        + (tab.id === profile.activeTabId ? " active" : "");

      const preview = document.createElement("div");
      preview.className = "mobile-tab-card-preview";
      preview.innerHTML = `<span>${escapeHtml(faviconLetter(tab))}</span>`;

      if (tab.id === profile.activeTabId) {
        const activeBadge = document.createElement("span");
        activeBadge.className = "mobile-tab-active-badge";
        activeBadge.textContent = "Đang mở";
        preview.appendChild(activeBadge);
      }

      const closeButton = document.createElement("button");
      closeButton.className = "mobile-tab-card-close";
      closeButton.type = "button";
      closeButton.setAttribute("aria-label", `Đóng ${tab.title}`);
      closeButton.textContent = "×";

      const copy = document.createElement("div");
      copy.className = "mobile-tab-card-copy";
      copy.innerHTML = `
        <b>${escapeHtml(tab.title || "Thẻ mới")}</b>
        <small>${escapeHtml(tab.url || "https://google.com")}</small>
      `;

      closeButton.addEventListener("click", event => {
        event.stopPropagation();
        closeTab(tab.id);
        renderMobileTabs();
        updateMobileTabCount();
      });

      card.addEventListener("click", () => {
        switchTab(tab.id);
        closeTabSwitcher();
      });

      card.append(preview, closeButton, copy);
      refs.grid.appendChild(card);
    });
  }

  function openTabSwitcher() {
    closeMenus();
    renderMobileTabs();
    refs.overlay.classList.remove("hidden");
    document.body.classList.add("mobile-overlay-open");
  }

  function closeTabSwitcher() {
    refs.overlay.classList.add("hidden");
    document.body.classList.remove("mobile-overlay-open");
  }

  function newMobileTab() {
    closeMenus();
    newTab();
    closeTabSwitcher();
    requestAnimationFrame(() => {
      if (isMobileLayout()) {
        els.addressInput.focus();
        els.addressInput.select();
      }
    });
  }

  function closeOtherTabs() {
    const profile = currentProfile();
    const current = activeTab();

    profile.tabs = [current];
    profile.activeTabId = current.id;
    saveState();
    render();
    renderMobileTabs();
  }

  function closeAllTabs() {
    const profile = currentProfile();
    profile.tabs = [{
      id: uid(),
      title: "google.com",
      url: "https://google.com"
    }];
    profile.activeTabId = profile.tabs[0].id;
    saveState();
    render();
    renderMobileTabs();
  }

  function syncMenuBackdrop() {
    if (!isMobileLayout()) {
      refs.menuBackdrop.classList.add("hidden");
      return;
    }

    const menuOpen =
      !els.chromeMenu.classList.contains("hidden")
      || !els.toolsMenu.classList.contains("hidden");

    refs.menuBackdrop.classList.toggle("hidden", !menuOpen);
    document.body.classList.toggle("mobile-menu-open", menuOpen);
  }

  const previousRender = render;
  render = function renderWithMobileChrome() {
    previousRender();
    updateMobileTabCount();

    if (!refs.overlay.classList.contains("hidden")) {
      renderMobileTabs();
    }
  };

  refs.home.addEventListener("click", () => {
    closeMenus();
    navigate("https://google.com");
  });

  refs.newTab.addEventListener("click", newMobileTab);
  refs.tabs.addEventListener("click", openTabSwitcher);
  refs.add.addEventListener("click", newMobileTab);
  refs.close.addEventListener("click", closeTabSwitcher);
  refs.closeOthers.addEventListener("click", closeOtherTabs);
  refs.closeAll.addEventListener("click", closeAllTabs);

  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) {
      closeTabSwitcher();
    }
  });

  refs.menuBackdrop.addEventListener("click", () => {
    closeMenus();
    syncMenuBackdrop();
  });

  const menuObserver = new MutationObserver(syncMenuBackdrop);
  menuObserver.observe(els.chromeMenu, {
    attributes: true,
    attributeFilter: ["class"]
  });
  menuObserver.observe(els.toolsMenu, {
    attributes: true,
    attributeFilter: ["class"]
  });

  els.addressInput.addEventListener("focus", () => {
    if (isMobileLayout()) {
      setTimeout(() => els.addressInput.select(), 0);
    }
  });

  window.addEventListener("resize", () => {
    syncMenuBackdrop();

    if (!isMobileLayout()) {
      closeTabSwitcher();
    }
  });

  document.addEventListener("keydown", event => {
    if (
      event.key === "Escape"
      && !refs.overlay.classList.contains("hidden")
    ) {
      closeTabSwitcher();
    }
  });

  updateMobileTabCount();
  syncMenuBackdrop();

  window.ShieldMobileChrome = {
    openTabs: openTabSwitcher,
    closeTabs: closeTabSwitcher,
    update: updateMobileTabCount
  };
})();