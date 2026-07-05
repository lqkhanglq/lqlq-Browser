(() => {
  "use strict";

  // Mobile chrome chỉ giữ trách nhiệm cho các nút trên thanh công cụ.
  // Danh sách thẻ thật đã chuyển hoàn toàn sang NativeTabSwitcherView.
  const refs = {
    home: document.getElementById("mobileHomeBtn"),
    newTab: document.getElementById("mobileNewTabBtn"),
    tabs: document.getElementById("mobileTabsBtn"),
    count: document.getElementById("mobileTabCount"),
    menuBackdrop: document.getElementById("mobileMenuBackdrop")
  };

  if (!refs.newTab || !refs.tabs || !refs.count) return;

  function isMobileLayout() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function updateMobileTabCount() {
    const profile = currentProfile();
    const count = Math.max(1, Array.isArray(profile.tabs) ? profile.tabs.length : 1);
    refs.count.textContent = count > 99 ? ":D" : String(count);
    refs.tabs.setAttribute("aria-label", `${count} thẻ đang mở`);
  }

  function openNativeTabSwitcher() {
    closeMenus();
    try {
      if (window.LqlqAndroid?.showTabSwitcher) {
        window.LqlqAndroid.showTabSwitcher();
        return;
      }
    } catch (error) {
      console.warn("lqlq native tabs:", error);
    }
    toast("Danh sách thẻ chỉ khả dụng trong ứng dụng Android.");
  }

  function newMobileTab() {
    closeMenus();
    newTab();
    requestAnimationFrame(() => {
      if (isMobileLayout()) {
        els.addressInput.focus();
        els.addressInput.select();
      }
    });
  }

  function syncMenuBackdrop() {
    if (!refs.menuBackdrop) return;
    if (!isMobileLayout()) {
      refs.menuBackdrop.classList.add("hidden");
      return;
    }
    const menuOpen =
      !els.chromeMenu.classList.contains("hidden") ||
      !els.toolsMenu.classList.contains("hidden");
    refs.menuBackdrop.classList.toggle("hidden", !menuOpen);
    document.body.classList.toggle("mobile-menu-open", menuOpen);
  }

  refs.home?.addEventListener("click", () => {
    closeMenus();
    navigate("https://google.com");
  });
  refs.newTab.addEventListener("click", newMobileTab);
  refs.tabs.addEventListener("click", openNativeTabSwitcher);

  refs.menuBackdrop?.addEventListener("click", () => {
    closeMenus();
    syncMenuBackdrop();
  });

  const menuObserver = new MutationObserver(syncMenuBackdrop);
  menuObserver.observe(els.chromeMenu, { attributes: true, attributeFilter: ["class"] });
  menuObserver.observe(els.toolsMenu, { attributes: true, attributeFilter: ["class"] });

  els.addressInput.addEventListener("focus", () => {
    if (isMobileLayout()) setTimeout(() => els.addressInput.select(), 0);
  });

  window.addEventListener("resize", syncMenuBackdrop);

  const previousRender = render;
  render = function renderWithMobileChrome() {
    previousRender();
    updateMobileTabCount();
  };

  updateMobileTabCount();
  syncMenuBackdrop();

  window.ShieldMobileChrome = {
    openTabs: openNativeTabSwitcher,
    closeTabs() {},
    update: updateMobileTabCount
  };
})();
