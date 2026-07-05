(() => {
  "use strict";

  // Việc (v0.23.36): VIẾT LẠI TỪ ĐẦU "Các thẻ đang mở" theo yêu cầu người
  // dùng — bản cũ tích luỹ qua nhiều lớp (nhóm thẻ kéo-thả, ảnh xem trước
  // native, đổi chế độ lưới/danh sách) suốt nhiều vòng vá mà vẫn còn báo lỗi
  // "trống hoàn toàn khi đang xem trang web thật". Nguyên nhân khả nghi nhất
  // (không loại trừ hoàn toàn nhưng rất đáng ngờ): bản cũ GHI ĐÈ hàm
  // render() TOÀN CỤC để tự render lại lưới tab mỗi khi render() được gọi
  // (mà render() bị gọi RẤT THƯỜNG XUYÊN khi đang xem trang thật — mỗi lần
  // onNativePage() nhận cập nhật từ onPageStarted/doUpdateVisitedHistory/
  // onPageFinished của MainActivity.kt) — dựng lại toàn bộ DOM lưới liên tục
  // trong lúc trang đang tải/điều hướng, dễ sinh trạng thái không nhất quán.
  // Bản viết lại này CHỈ render lưới khi người dùng THỰC SỰ mở nó
  // (openTabSwitcher), không còn hook vào render() toàn cục nữa. Bỏ hẳn
  // tính năng nhóm thẻ + ảnh xem trước + đổi chế độ xem — chỉ còn danh sách
  // thẻ đơn giản, đáng tin cậy, giống cấu trúc cơ bản của Chrome.

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
    menuBackdrop: document.getElementById("mobileMenuBackdrop"),
    search: document.getElementById("mobileTabsSearch"),
    viewToggle: document.getElementById("mobileTabsViewToggle")
  };

  // Nút đổi chế độ xem không còn dùng nữa (chỉ còn 1 kiểu lưới đơn giản).
  if (refs.viewToggle) refs.viewToggle.style.display = "none";

  function isMobileLayout() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function updateMobileTabCount() {
    const profile = currentProfile();
    const count = Math.max(1, profile.tabs.length);
    refs.count.textContent = count > 99 ? ":D" : String(count);
    refs.tabs.setAttribute("aria-label", `${count} thẻ đang mở`);
  }

  function faviconLetter(tab) {
    const title = String(tab.title || "T").trim();
    return title.charAt(0).toUpperCase() || "T";
  }

  function domainHue(tab) {
    const seed = String(tab.url || tab.title || "T").split("/")[2] || tab.title || "T";
    let hash = 0;
    for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
    return hash % 360;
  }

  function createTabCard(tab, activeTabId) {
    const card = document.createElement("article");
    card.className = "mobile-tab-card" + (tab.id === activeTabId ? " active" : "");

    const preview = document.createElement("div");
    preview.className = "mobile-tab-card-preview";
    preview.style.background = `linear-gradient(180deg, hsl(${domainHue(tab)} 45% 94%), hsl(${domainHue(tab)} 35% 88%))`;
    preview.innerHTML = `<span>${escapeHtml(faviconLetter(tab))}</span>`;

    if (tab.id === activeTabId) {
      const badge = document.createElement("span");
      badge.className = "mobile-tab-active-badge";
      badge.textContent = "Đang mở";
      preview.appendChild(badge);
    }

    const closeButton = document.createElement("button");
    closeButton.className = "mobile-tab-card-close";
    closeButton.type = "button";
    closeButton.setAttribute("aria-label", `Đóng ${tab.title || "thẻ"}`);
    closeButton.textContent = "×";
    closeButton.addEventListener("click", event => {
      event.stopPropagation();
      closeTab(tab.id);
      renderTabSwitcherGrid();
      updateMobileTabCount();
    });

    const copy = document.createElement("div");
    copy.className = "mobile-tab-card-copy";

    const titleEl = document.createElement("b");
    titleEl.textContent = tab.title || "Thẻ mới";

    const urlEl = document.createElement("small");
    urlEl.textContent = tab.url || "";

    copy.append(titleEl, urlEl);

    card.append(preview, closeButton, copy);
    card.dataset.searchText = `${tab.title || ""} ${tab.url || ""}`.toLowerCase();

    card.addEventListener("click", () => {
      switchTab(tab.id);
      closeTabSwitcher();
    });

    return card;
  }

  // Chỉ render khi thực sự cần (mở lưới, hoặc đóng/mở thẻ trong khi lưới
  // đang mở) — KHÔNG hook vào render() toàn cục nữa.
  function renderTabSwitcherGrid() {
    const profile = currentProfile();
    const activeTabId = profile.activeTabId;

    refs.grid.innerHTML = "";

    if (!profile.tabs.length) {
      refs.grid.innerHTML = `<div class="mobile-tab-card-empty">Chưa có thẻ nào.</div>`;
      return;
    }

    const fragment = document.createDocumentFragment();
    profile.tabs.forEach(tab => {
      fragment.appendChild(createTabCard(tab, activeTabId));
    });
    refs.grid.appendChild(fragment);
  }

  let tabsFilter = "";
  let searchDebounceTimer = null;

  function applyTabsFilter() {
    const cards = refs.grid.querySelectorAll(".mobile-tab-card");
    let visibleCount = 0;
    cards.forEach(card => {
      const match = !tabsFilter || (card.dataset.searchText || "").includes(tabsFilter);
      card.style.display = match ? "" : "none";
      if (match) visibleCount++;
    });

    let emptyEl = refs.grid.querySelector(".mobile-tab-card-empty");
    if (!visibleCount) {
      if (!emptyEl) {
        emptyEl = document.createElement("div");
        emptyEl.className = "mobile-tab-card-empty";
        emptyEl.textContent = "Không tìm thấy thẻ phù hợp.";
        refs.grid.appendChild(emptyEl);
      }
      emptyEl.style.display = "";
    } else if (emptyEl) {
      emptyEl.style.display = "none";
    }
  }

  function openTabSwitcher() {
    closeMenus();
    tabsFilter = "";
    if (refs.search) refs.search.value = "";

    // Mở overlay TRƯỚC khi render nội dung — nếu render lỗi vì lý do gì đó,
    // overlay vẫn hiện và thông báo lỗi thật sẽ hiện trong lưới thay vì cả
    // tính năng "biến mất" không dấu vết.
    refs.overlay.classList.remove("hidden");
    document.body.classList.add("mobile-overlay-open");

    // Việc "vẫn đơ khi đang xem web thật" (v0.23.37): KHÔNG chờ cơ chế dò
    // MutationObserver chung (delay ~80ms qua click listener + interval
    // 900ms) để native đưa shellWebView lên trước trang web thật — gọi
    // THẲNG native.setOverlayOpen(true) NGAY LẬP TỨC, đồng bộ với đúng hành
    // động mở khung này, loại bỏ mọi khả năng lệch thời điểm/timing.
    try {
      window.LqlqAndroid?.setOverlayOpen?.(true);
    } catch {}

    try {
      renderTabSwitcherGrid();
    } catch (error) {
      console.warn("lqlq mobile tabs:", error);
      refs.grid.innerHTML = `<div class="mobile-tab-card-empty">Không thể tải danh sách thẻ: ${escapeHtml(String(error?.message || error))}</div>`;
    }
  }

  function closeTabSwitcher() {
    refs.overlay.classList.add("hidden");
    document.body.classList.remove("mobile-overlay-open");
    try {
      window.LqlqAndroid?.setOverlayOpen?.(false);
    } catch {}
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
    renderTabSwitcherGrid();
  }

  function closeAllTabs() {
    const profile = currentProfile();
    profile.tabs = [{ id: uid(), title: "google.com", url: "https://google.com" }];
    profile.activeTabId = profile.tabs[0].id;
    saveState();
    render();
    renderTabSwitcherGrid();
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

  refs.search?.addEventListener("input", () => {
    const value = String(refs.search.value || "").trim().toLowerCase();
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      tabsFilter = value;
      applyTabsFilter();
    }, 180);
  });

  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) closeTabSwitcher();
  });

  refs.menuBackdrop.addEventListener("click", () => {
    closeMenus();
    syncMenuBackdrop();
  });

  const menuObserver = new MutationObserver(syncMenuBackdrop);
  menuObserver.observe(els.chromeMenu, { attributes: true, attributeFilter: ["class"] });
  menuObserver.observe(els.toolsMenu, { attributes: true, attributeFilter: ["class"] });

  els.addressInput.addEventListener("focus", () => {
    if (isMobileLayout()) {
      setTimeout(() => els.addressInput.select(), 0);
    }
  });

  window.addEventListener("resize", () => {
    syncMenuBackdrop();
    if (!isMobileLayout()) closeTabSwitcher();
  });

  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !refs.overlay.classList.contains("hidden")) {
      closeTabSwitcher();
    }
  });

  // Chỉ cần cập nhật SỐ LƯỢNG thẻ (số nhỏ trên icon) mỗi khi render() toàn
  // cục chạy — rẻ, không dựng lại DOM. Lưới chi tiết chỉ dựng khi thực sự mở.
  const previousRender = render;
  render = function renderWithMobileChrome() {
    previousRender();
    try {
      updateMobileTabCount();
    } catch (error) {
      console.warn("lqlq mobile tab count:", error);
    }
  };

  updateMobileTabCount();
  syncMenuBackdrop();

  window.ShieldMobileChrome = {
    openTabs: openTabSwitcher,
    closeTabs: closeTabSwitcher,
    update: updateMobileTabCount
  };
})();
