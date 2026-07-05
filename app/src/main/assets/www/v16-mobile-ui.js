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
    menuBackdrop: document.getElementById("mobileMenuBackdrop"),
    search: document.getElementById("mobileTabsSearch"),
    viewToggle: document.getElementById("mobileTabsViewToggle")
  };

  // Việc (v0.23.24 — Vấn đề 4): Nhóm thẻ kiểu Chrome — bản đơn giản nhất.
  // Mỗi profile có 1 danh sách nhóm (id/tên/màu), mỗi tab tham chiếu tới 1
  // nhóm qua tab.groupId. Không hỗ trợ kéo-thả như Chrome thật, chỉ cần
  // gán/bỏ nhóm qua nút "⋮" trên mỗi thẻ trong lưới "Các thẻ đang mở".
  const GROUP_COLORS = ["#4f8f6d", "#4a7fc4", "#c48a3f", "#b1548a", "#7a63c9", "#c15a4d"];

  function ensureProfileGroups(profile) {
    if (!Array.isArray(profile.tabGroups)) profile.tabGroups = [];
    return profile.tabGroups;
  }

  function findGroup(profile, groupId) {
    return ensureProfileGroups(profile).find(g => g.id === groupId) || null;
  }

  function nextGroupColor(profile) {
    const groups = ensureProfileGroups(profile);
    return GROUP_COLORS[groups.length % GROUP_COLORS.length];
  }

  let openGroupMenuEl = null;

  function closeGroupMenu() {
    openGroupMenuEl?.remove();
    openGroupMenuEl = null;
  }

  function assignTabToGroup(tab, groupId) {
    tab.groupId = groupId || null;
    saveState();
    renderMobileTabs();
  }

  function openGroupMenu(tab, anchorEl) {
    closeGroupMenu();
    const profile = currentProfile();
    const groups = ensureProfileGroups(profile);

    const menu = document.createElement("div");
    menu.className = "mobile-tab-group-menu";

    const rect = anchorEl.getBoundingClientRect();
    menu.style.top = `${Math.min(rect.bottom + 6, window.innerHeight - 220)}px`;
    menu.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - 268))}px`;

    if (tab.groupId) {
      const clearBtn = document.createElement("button");
      clearBtn.type = "button";
      clearBtn.className = "mobile-tab-group-menu-item";
      clearBtn.textContent = "✕ Bỏ khỏi nhóm";
      clearBtn.addEventListener("click", () => {
        assignTabToGroup(tab, null);
        closeGroupMenu();
      });
      menu.appendChild(clearBtn);
    }

    groups.forEach(group => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "mobile-tab-group-menu-item";

      const dot = document.createElement("span");
      dot.className = "mobile-tab-group-menu-dot";
      dot.style.background = group.color;

      const label = document.createElement("span");
      label.textContent = group.name;

      item.append(dot, label);
      item.addEventListener("click", () => {
        assignTabToGroup(tab, group.id);
        closeGroupMenu();
      });
      menu.appendChild(item);
    });

    const inputRow = document.createElement("div");
    inputRow.className = "mobile-tab-group-menu-input";

    const input = document.createElement("input");
    input.type = "text";
    input.placeholder = "Tên nhóm mới";
    input.maxLength = 24;

    const createBtn = document.createElement("button");
    createBtn.type = "button";
    createBtn.textContent = "Tạo";
    createBtn.addEventListener("click", () => {
      const name = input.value.trim();
      if (!name) return;
      const group = {
        id: uid(),
        name,
        color: nextGroupColor(profile)
      };
      groups.push(group);
      assignTabToGroup(tab, group.id);
      closeGroupMenu();
    });

    inputRow.append(input, createBtn);
    menu.appendChild(inputRow);

    document.body.appendChild(menu);
    openGroupMenuEl = menu;

    setTimeout(() => {
      document.addEventListener("click", function onOutsideClick(event) {
        if (!menu.contains(event.target)) {
          closeGroupMenu();
          document.removeEventListener("click", onOutsideClick, true);
        }
      }, true);
    }, 0);
  }

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

  let tabsFilter = "";

  function tabMatchesFilter(tab) {
    if (!tabsFilter) return true;
    const haystack = `${tab.title || ""} ${tab.url || ""}`.toLowerCase();
    return haystack.includes(tabsFilter);
  }

  function renderMobileTabs() {
    const profile = currentProfile();
    const groups = ensureProfileGroups(profile);
    closeGroupMenu();
    refs.grid.innerHTML = "";

    const visibleTabs = profile.tabs.filter(tabMatchesFilter);

    if (!visibleTabs.length) {
      refs.grid.innerHTML = `<div class="mobile-tab-card-empty" style="grid-column:1/-1;text-align:center;padding:24px 8px;color:#89958d;font-size:12px;">Không tìm thấy thẻ phù hợp.</div>`;
      return;
    }

    visibleTabs.forEach(tab => {
      const group = tab.groupId ? findGroup(profile, tab.groupId) : null;

      const card = document.createElement("article");
      card.className =
        "mobile-tab-card"
        + (tab.id === profile.activeTabId ? " active" : "");
      if (group) card.style.borderColor = group.color;

      const preview = document.createElement("div");
      preview.className = "mobile-tab-card-preview";

      const domainColorSeed = String(tab.url || tab.title || "").split("/")[2] || tab.title || "T";
      let hash = 0;
      for (let i = 0; i < domainColorSeed.length; i++) hash = (hash * 31 + domainColorSeed.charCodeAt(i)) >>> 0;
      const hue = hash % 360;
      preview.style.background = `linear-gradient(180deg, hsl(${hue} 45% 94%), hsl(${hue} 35% 88%))`;
      preview.innerHTML = `<span>${escapeHtml(faviconLetter(tab))}</span>`;

      // Việc "ảnh xem trước giống Chrome" (v0.23.31): dùng ảnh chụp THẬT của
      // WebView (native đã chụp sẵn trong bộ nhớ, không tải mạng) nếu có —
      // giống hệt cách Chrome cache snapshot mỗi tab. Nếu tab chưa từng
      // render xong (chưa có ảnh), giữ nguyên gradient+chữ cái làm dự phòng.
      try {
        const thumbnail = window.LqlqAndroid?.getTabThumbnail?.(String(tab.id));
        if (thumbnail) {
          preview.style.backgroundImage = `url("${thumbnail}")`;
          preview.style.backgroundSize = "cover";
          preview.style.backgroundPosition = "top center";
          preview.querySelector("span")?.remove();
        }
      } catch {}

      if (tab.id === profile.activeTabId) {
        const activeBadge = document.createElement("span");
        activeBadge.className = "mobile-tab-active-badge";
        activeBadge.textContent = "Đang mở";
        preview.appendChild(activeBadge);
      }

      if (group) {
        const chip = document.createElement("span");
        chip.className = "mobile-tab-group-chip";
        chip.style.background = group.color;
        chip.textContent = group.name;
        preview.appendChild(chip);
      }

      const menuButton = document.createElement("button");
      menuButton.className = "mobile-tab-card-menu";
      menuButton.type = "button";
      menuButton.setAttribute("aria-label", `Tùy chọn nhóm cho ${tab.title || "thẻ"}`);
      menuButton.textContent = "⋮";
      menuButton.addEventListener("click", event => {
        event.stopPropagation();
        openGroupMenu(tab, menuButton);
      });

      const closeButton = document.createElement("button");
      closeButton.className = "mobile-tab-card-close";
      closeButton.type = "button";
      closeButton.setAttribute("aria-label", `Đóng ${tab.title}`);
      closeButton.textContent = "×";

      const copy = document.createElement("div");
      copy.className = "mobile-tab-card-copy";

      // Việc "Các thẻ đang mở bị lag" (v0.23.30): trước đây mỗi thẻ tải 1
      // ảnh favicon.ico THẬT QUA MẠNG (faviconForUrl → origin + "/favicon.ico")
      // ngay khi mở lưới — có bao nhiêu thẻ là bấy nhiêu request mạng cùng
      // lúc, gây cảm giác nặng/lag thật sự khi có nhiều thẻ hoặc mạng chậm.
      // Đã bỏ hẳn, chỉ dùng icon chữ cái local đã có sẵn trong `preview`
      // (gradient màu theo domain + chữ cái đầu) — không cần mạng, tức thì.
      const titleRow = document.createElement("div");
      titleRow.className = "mobile-tab-card-copy-title";
      const titleEl = document.createElement("b");
      titleEl.textContent = tab.title || "Thẻ mới";
      titleRow.append(titleEl);

      const urlEl = document.createElement("small");
      urlEl.textContent = tab.url || "https://google.com";

      copy.append(titleRow, urlEl);

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

      card.append(preview, closeButton, menuButton, copy);
      // Lưu sẵn text tìm kiếm trên chính card (thay vì phải đọc lại profile
      // mỗi lần gõ phím) để lọc bằng cách ẩn/hiện (mục 4 — độ mượt): tránh
      // phải huỷ-dựng-lại toàn bộ DOM + gọi lại getTabThumbnail() (bridge
      // native, có mã hoá base64 ảnh) cho MỌI thẻ mỗi lần gõ 1 ký tự.
      card.dataset.searchText = `${tab.title || ""} ${tab.url || ""}`.toLowerCase();
      refs.grid.appendChild(card);
    });
  }

  // Lọc theo từ khoá bằng cách ẩn/hiện card đã dựng sẵn — KHÔNG dựng lại
  // DOM, KHÔNG gọi lại getTabThumbnail(). Chỉ renderMobileTabs() đầy đủ khi
  // mở lưới lần đầu hoặc dữ liệu tab thực sự đổi (đóng/mở/đổi nhóm...).
  function applyTabsFilterInPlace() {
    const cards = refs.grid.querySelectorAll(".mobile-tab-card");
    if (!cards.length) return;
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
        emptyEl.style.cssText = "grid-column:1/-1;text-align:center;padding:24px 8px;color:#89958d;font-size:12px;";
        emptyEl.textContent = "Không tìm thấy thẻ phù hợp.";
        refs.grid.appendChild(emptyEl);
      }
      emptyEl.style.display = "";
    } else if (emptyEl) {
      emptyEl.style.display = "none";
    }
  }

  let searchDebounceTimer = null;

  function openTabSwitcher() {
    closeMenus();
    tabsFilter = "";
    if (refs.search) refs.search.value = "";

    // Việc (v0.23.27 — Vấn đề: "Các thẻ đang mở" không hiện gì): bỏ ẩn
    // overlay TRƯỚC khi render nội dung lưới thẻ. Trước đây renderMobileTabs()
    // chạy trước — nếu nó ném lỗi (vd. dữ liệu tab/nhóm hỏng), toàn bộ hàm này
    // dừng lại NGAY LẬP TỨC và 2 dòng bỏ class "hidden" / thêm class mở overlay
    // phía dưới KHÔNG BAO GIỜ chạy được → màn hình không hiện gì cả, đúng như
    // triệu chứng người dùng báo. Nay overlay luôn được mở trước, và việc
    // render nội dung được bọc try/catch riêng để lỗi (nếu có) không còn làm
    // "biến mất" cả tính năng nữa.
    refs.overlay.classList.remove("hidden");
    document.body.classList.add("mobile-overlay-open");

    try {
      renderMobileTabs();
    } catch (error) {
      console.warn("lqlq mobile tabs:", error);
      refs.grid.innerHTML = `<div class="mobile-tab-card-empty" style="grid-column:1/-1;text-align:center;padding:24px 8px;color:#89958d;font-size:12px;">Không thể tải danh sách thẻ.</div>`;
    }
  }

  function closeTabSwitcher() {
    closeGroupMenu();
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

    try {
      updateMobileTabCount();
    } catch (error) {
      console.warn("lqlq mobile tab count:", error);
    }

    if (!refs.overlay.classList.contains("hidden")) {
      try {
        renderMobileTabs();
      } catch (error) {
        console.warn("lqlq mobile tabs render:", error);
      }
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

  // Việc (v0.23.24 — Vấn đề 1): ô "Tìm thẻ của bạn" lọc theo tiêu đề/URL các
  // tab đang mở, phía client-side. Nút đổi kiểu xem chuyển giữa lưới 2 cột
  // (mặc định) và danh sách 1 cột (giống nút "danh sách số/lưới" của Chrome).
  refs.search?.addEventListener("input", () => {
    // Debounce ~180ms — tránh lọc lại ngay lập tức trên MỖI ký tự gõ, đặc
    // biệt vì applyTabsFilterInPlace() giờ chỉ ẩn/hiện (rẻ) chứ không dựng
    // lại DOM nữa, nhưng vẫn debounce để gõ nhanh mượt hơn, giống Chrome.
    const value = String(refs.search.value || "").trim().toLowerCase();
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      tabsFilter = value;
      applyTabsFilterInPlace();
    }, 180);
  });

  let tabsListMode = false;
  refs.viewToggle?.addEventListener("click", () => {
    tabsListMode = !tabsListMode;
    refs.grid.classList.toggle("list-mode", tabsListMode);
    refs.viewToggle.textContent = tabsListMode ? "☰" : "▦";
    refs.viewToggle.title = tabsListMode ? "Xem dạng lưới" : "Xem dạng danh sách";
  });

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