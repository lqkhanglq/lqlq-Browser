(() => {
  "use strict";

  const STORAGE_KEY = "lqlqMobileToolbarModeV22";
  const MODES = {
    // Chế độ "auto" (vuốt lên ẩn, vuốt xuống hiện) đã được gỡ bỏ trong bản APK
    // vì gây giật khi cuộn trên một số trang web.
    always: {
      label: "Luôn hiển thị",
      description:
        "Thanh trình duyệt luôn nằm trên màn hình. Đây là chế độ mặc định.",
      icon: "▔"
    },
    compact: {
      label: "Luôn thu gọn",
      description:
        "Chỉ hiện vạch kéo; chạm vạch để mở thanh tạm thời.",
      icon: "━"
    }
  };

  const refs = {
    body: document.body,
    shell: document.querySelector(".top-shell"),
    stage: document.querySelector(".browser-stage"),
    reveal: document.getElementById("mobileToolbarReveal"),
    modeText: document.getElementById("mobileToolbarModeText"),
    address: document.getElementById("addressInput"),
    menuButton: document.getElementById("menuBtn"),
    home: document.getElementById("mobileHomeBtn"),
    newTab: document.getElementById("mobileNewTabBtn"),
    tabs: document.getElementById("mobileTabsBtn"),
    drawer: document.getElementById("sideDrawer"),
    chromeMenu: document.getElementById("chromeMenu"),
    toolsMenu: document.getElementById("toolsMenu"),
    mobileTabs: document.getElementById("mobileTabsOverlay")
  };

  if (!refs.shell || !refs.stage || !refs.reveal) {
    console.warn("lqlq mobile toolbar: thiếu phần tử giao diện.");
    return;
  }

  let mode = readMode();
  let hidden = false;
  let lastScrollTop = refs.stage.scrollTop;
  let lastExternalScrollTop = 0;
  let downwardDistance = 0;
  let upwardDistance = 0;
  let ignoreScrollUntil = 0;
  let compactTimer = 0;
  let revealPointerY = null;

  function isMobile() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function readMode() {
    const saved = localStorage.getItem(STORAGE_KEY);
    return Object.hasOwn(MODES, saved) ? saved : "always";
  }

  function saveMode() {
    localStorage.setItem(STORAGE_KEY, mode);
  }

  function modeLabel() {
    return MODES[mode]?.label || MODES.always.label;
  }

  function setUiOpenClass() {
    const open = isUiBlockingAutoHide();
    refs.body.classList.toggle("mobile-toolbar-ui-open", open);
  }

  function isVisible(element) {
    return Boolean(
      element
      && !element.classList.contains("hidden")
    );
  }

  function isUiBlockingAutoHide() {
    if (!isMobile()) return false;

    if (document.activeElement === refs.address) return true;
    if (refs.drawer?.classList.contains("open")) return true;
    if (isVisible(refs.chromeMenu)) return true;
    if (isVisible(refs.toolsMenu)) return true;
    if (isVisible(refs.mobileTabs)) return true;

    const activePanels = [
      "#readerPanel:not(.hidden)",
      "#videoPanel:not(.hidden)",
      "#imageEditorShell:not(.hidden)",
      "#chapterClipperPanel:not(.hidden)",
      "#securityCenter:not(.hidden)",
      "#globalScreenLock:not(.hidden)"
    ];

    return activePanels.some(selector =>
      document.querySelector(selector)
    );
  }

  function clearCompactTimer() {
    clearTimeout(compactTimer);
    compactTimer = 0;
  }

  function scheduleCompactHide(delay = 4200) {
    clearCompactTimer();

    if (
      mode !== "compact"
      || hidden
      || !isMobile()
      || isUiBlockingAutoHide()
    ) {
      return;
    }

    compactTimer = setTimeout(() => {
      if (!isUiBlockingAutoHide()) {
        setHidden(true, "compact-timeout");
      }
    }, delay);
  }

  function setHidden(next, reason = "") {
    if (!isMobile()) {
      next = false;
    }

    if (mode === "always" && next) {
      next = false;
    }

    if (next && isUiBlockingAutoHide()) {
      next = false;
    }

    if (hidden === next) {
      if (!next && mode === "compact") {
        scheduleCompactHide();
      }
      return;
    }

    hidden = next;
    ignoreScrollUntil = performance.now() + 270;

    refs.body.classList.toggle("mobile-toolbar-hidden", hidden);
    refs.reveal.setAttribute("aria-hidden", String(!hidden));

    if (hidden) {
      clearCompactTimer();
      refs.address?.blur();
    } else if (mode === "compact") {
      scheduleCompactHide(
        reason === "menu" || reason === "drawer" ? 6500 : 4200
      );
    }

    window.dispatchEvent(
      new CustomEvent("lqlq-mobile-toolbar-change", {
        detail: { hidden, mode, reason }
      })
    );
  }

  function showToolbar(reason = "manual") {
    setHidden(false, reason);
  }

  function hideToolbar(reason = "manual") {
    setHidden(true, reason);
  }

  function resetScrollAccumulator(scrollTop) {
    lastScrollTop = scrollTop;
    downwardDistance = 0;
    upwardDistance = 0;
  }

  function processScrollPosition(scrollTop, source = "stage") {
    if (!isMobile()) return;

    const current = Math.max(0, Number(scrollTop) || 0);
    const previous =
      source === "external"
        ? lastExternalScrollTop
        : lastScrollTop;

    if (source === "external") {
      lastExternalScrollTop = current;
    } else {
      lastScrollTop = current;
    }

    if (mode !== "auto") return;

    if (isUiBlockingAutoHide()) {
      showToolbar("ui-open");
      downwardDistance = 0;
      upwardDistance = 0;
      return;
    }

    if (performance.now() < ignoreScrollUntil) {
      downwardDistance = 0;
      upwardDistance = 0;
      return;
    }

    if (current <= 8) {
      showToolbar("top");
      downwardDistance = 0;
      upwardDistance = 0;
      return;
    }

    const delta = current - previous;
    if (Math.abs(delta) < 0.75) return;

    if (delta > 0) {
      downwardDistance += delta;
      upwardDistance = 0;

      if (downwardDistance >= 60) {
        hideToolbar("scroll-down");
        downwardDistance = 0;
      }
    } else {
      upwardDistance += Math.abs(delta);
      downwardDistance = 0;

      if (upwardDistance >= 18) {
        showToolbar("scroll-up");
        upwardDistance = 0;
      }
    }
  }

  function setMode(nextMode, announce = true) {
    if (!Object.hasOwn(MODES, nextMode)) {
      nextMode = "always";
    }

    mode = nextMode;
    saveMode();

    refs.body.dataset.mobileToolbarMode = mode;
    if (refs.modeText) {
      refs.modeText.textContent = modeLabel();
    }

    if (!isMobile()) {
      setHidden(false, "desktop");
    } else if (mode === "always") {
      setHidden(false, "mode-always");
    } else if (mode === "compact") {
      if (isUiBlockingAutoHide()) {
        setHidden(false, "mode-compact-ui");
      } else {
        setHidden(true, "mode-compact");
      }
    } else {
      setHidden(false, "mode-auto");
      resetScrollAccumulator(refs.stage.scrollTop);
    }

    if (announce && typeof toast === "function") {
      toast(`Thanh công cụ: ${modeLabel()}.`);
    }

    renderSettingsSelection();
  }

  function renderSettingsSelection() {
    document
      .querySelectorAll("[data-mobile-toolbar-mode]")
      .forEach(button => {
        const selected =
          button.dataset.mobileToolbarMode === mode;

        button.classList.toggle("selected", selected);
        button.setAttribute("aria-pressed", String(selected));
      });
  }

  function createModeOption(key) {
    const info = MODES[key];
    const button = document.createElement("button");

    button.type = "button";
    button.className = "mobile-toolbar-mode-option";
    button.dataset.mobileToolbarMode = key;
    button.innerHTML = `
      <span class="mobile-toolbar-mode-icon">${info.icon}</span>
      <span class="mobile-toolbar-mode-copy">
        <b>${info.label}</b>
        <small>${info.description}</small>
      </span>
      <span class="mobile-toolbar-mode-check">✓</span>
    `;

    button.addEventListener("click", () => {
      setMode(key);
    });

    return button;
  }

  function openToolbarSettings() {
    if (typeof closeMenus === "function") {
      closeMenus();
    }

    showToolbar("drawer");

    refs.drawer?.classList.add("open");
    if (els?.drawerTitle) {
      els.drawerTitle.textContent = "Thanh công cụ điện thoại";
    }
    if (!els?.drawerContent) return;

    els.drawerContent.innerHTML = "";

    const list = document.createElement("div");
    list.className = "mobile-toolbar-mode-list";

    ["always", "compact"].forEach(key => {
      list.appendChild(createModeOption(key));
    });

    const note = document.createElement("div");
    note.className = "mobile-toolbar-setting-note";
    note.textContent =
      "Chế độ tự động ẩn khi cuộn đã được gỡ bỏ trong bản APK "
      + "vì gây giật khi cuộn trên một số trang web.";

    const actions = document.createElement("div");
    actions.className = "mobile-toolbar-setting-actions";

    const preview = document.createElement("button");
    preview.type = "button";
    preview.textContent = "Thử thu gọn";
    preview.addEventListener("click", () => {
      refs.drawer?.classList.remove("open");
      setTimeout(() => hideToolbar("preview"), 160);
    });

    const done = document.createElement("button");
    done.type = "button";
    done.className = "primary";
    done.textContent = "Xong";
    done.addEventListener("click", () => {
      refs.drawer?.classList.remove("open");

      setTimeout(() => {
        if (mode === "compact") {
          hideToolbar("compact-drawer-close");
        } else {
          showToolbar("drawer-close");
        }
      }, 180);
    });

    actions.append(preview, done);
    els.drawerContent.append(list, note, actions);

    renderSettingsSelection();
    setUiOpenClass();
  }

  function showBeforeUiAction() {
    showToolbar("ui-action");
  }

  refs.stage.addEventListener(
    "scroll",
    () => processScrollPosition(refs.stage.scrollTop, "stage"),
    { passive: true }
  );

  refs.reveal.addEventListener("click", () => {
    showToolbar("pull-handle");
  });

  refs.reveal.addEventListener(
    "pointerdown",
    event => {
      revealPointerY = event.clientY;
      refs.reveal.setPointerCapture?.(event.pointerId);
    },
    { passive: true }
  );

  refs.reveal.addEventListener(
    "pointerup",
    event => {
      if (
        revealPointerY != null
        && event.clientY - revealPointerY > 12
      ) {
        showToolbar("pull-down");
      }
      revealPointerY = null;
    },
    { passive: true }
  );

  [
    refs.menuButton,
    refs.home,
    refs.newTab,
    refs.tabs
  ].filter(Boolean).forEach(button => {
    button.addEventListener("pointerdown", showBeforeUiAction, {
      passive: true
    });
  });

  refs.address?.addEventListener("focus", () => {
    showToolbar("address-focus");
  });

  refs.address?.addEventListener("input", () => {
    showToolbar("address-input");
  });

  const observedElements = [
    refs.chromeMenu,
    refs.toolsMenu,
    refs.drawer,
    refs.mobileTabs
  ].filter(Boolean);

  const uiObserver = new MutationObserver(() => {
    setUiOpenClass();

    if (isUiBlockingAutoHide()) {
      showToolbar("ui-open");
    } else if (mode === "compact") {
      scheduleCompactHide(700);
    }
  });

  observedElements.forEach(element => {
    uiObserver.observe(element, {
      attributes: true,
      attributeFilter: ["class"]
    });
  });

  const previousHandleAction = handleAction;
  handleAction = function handleMobileToolbarAction(action) {
    if (action === "mobile-toolbar-display") {
      openToolbarSettings();
      return;
    }

    previousHandleAction(action);
  };

  window.addEventListener("resize", () => {
    setUiOpenClass();

    if (!isMobile()) {
      setHidden(false, "desktop-resize");
      clearCompactTimer();
      return;
    }

    setMode(mode, false);
  });

  document.addEventListener(
    "visibilitychange",
    () => {
      if (document.visibilityState === "visible") {
        showToolbar("page-visible");

        if (mode === "compact") {
          scheduleCompactHide();
        }
      }
    }
  );

  document.addEventListener(
    "keydown",
    event => {
      if (event.key === "Escape" && hidden) {
        showToolbar("escape");
      }
    },
    true
  );

  /*
   * APK/WebView bridge:
   * Native code can forward the active webpage scroll position here.
   */
  window.LqlqMobileToolbar = {
    get mode() {
      return mode;
    },
    get hidden() {
      return hidden;
    },
    show: showToolbar,
    hide: hideToolbar,
    setMode,
    openSettings: openToolbarSettings,
    onPageScroll(scrollY) {
      processScrollPosition(scrollY, "external");
    },
    onPageScrollDelta(deltaY) {
      const next = Math.max(
        0,
        lastExternalScrollTop + (Number(deltaY) || 0)
      );
      processScrollPosition(next, "external");
    },
    onPageGesture(direction) {
      if (mode !== "auto") return;

      if (direction === "up") {
        hideToolbar("native-gesture-up");
      } else if (direction === "down") {
        showToolbar("native-gesture-down");
      }
    }
  };

  refs.body.dataset.mobileToolbarMode = mode;
  if (refs.modeText) {
    refs.modeText.textContent = modeLabel();
  }

  setUiOpenClass();
  setMode(mode, false);
})();