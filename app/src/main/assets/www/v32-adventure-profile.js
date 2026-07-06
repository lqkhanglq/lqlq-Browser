/*
 * v32-adventure-profile.js — Hồ sơ Phiêu lưu + LQLQ Dynamic Loot Engine.
 *
 * Native Android (LqlqAdventure) là nguồn dữ liệu duy nhất. JavaScript chỉ
 * hiển thị giao diện, không tự ý cộng/trừ Linh Thạch, Linh Cầu hay bộ sưu tập.
 */
(() => {
  "use strict";

  const native = window.LqlqAdventure;
  const $ = id => document.getElementById(id);

  const AVATARS = [
    { id: "guardian", symbol: "🛡️", name: "Hộ Vệ" },
    { id: "swordsman", symbol: "⚔️", name: "Kiếm Khách" },
    { id: "scholar", symbol: "📜", name: "Học Giả" },
    { id: "ranger", symbol: "🏹", name: "Du Hiệp" },
    { id: "mage", symbol: "🔮", name: "Pháp Sư" },
    { id: "dragon", symbol: "🐉", name: "Long Vệ" },
    { id: "lotus", symbol: "🪷", name: "Liên Hoa" },
    { id: "comet", symbol: "☄️", name: "Tinh Vân" }
  ];

  const GUEST_SHIELD = `
    <svg viewBox="0 0 24 24" id="adventureGuestShieldIcon" aria-hidden="true">
      <path d="M12 2.8 19 5.6v5.5c0 4.6-2.9 8.6-7 10.1-4.1-1.5-7-5.5-7-10.1V5.6L12 2.8Z"></path>
      <path d="m8.6 12 2.1 2.1 4.8-5"></path>
    </svg>`;

  const elements = {
    entry: $("adventureProfileEntry"),
    avatarMark: $("adventureAvatarMark"),
    profileName: $("adventureProfileName"),
    profileStatus: $("adventureProfileStatus"),
    summaryLevel: $("adventureSummaryLevel"),
    summaryRank: $("adventureSummaryRank"),
    summaryHint: $("adventureSummaryHint"),
    summaryCrystals: $("adventureSummaryCrystals"),
    overlay: $("adventureProfileOverlay"),
    close: $("adventureProfileClose"),
    createView: $("adventureCreateView"),
    dashboardView: $("adventureDashboardView"),
    subView: $("adventureSubView"),
    subBack: $("adventureSubBack"),
    subTitle: $("adventureSubTitle"),
    subContent: $("adventureSubContent"),
    title: $("adventureProfileTitle"),
    createPreview: $("adventureCreatePreview"),
    nickname: $("adventureNicknameInput"),
    avatarGrid: $("adventureAvatarGrid"),
    uploadBtn: $("adventureAvatarUploadBtn"),
    uploadInput: $("adventureAvatarUpload"),
    uploadName: $("adventureAvatarUploadName"),
    formError: $("adventureFormError"),
    createLater: $("adventureCreateLater"),
    createSubmit: $("adventureCreateSubmit"),
    dashboardAvatar: $("adventureDashboardAvatar"),
    dashboardName: $("adventureDashboardName"),
    crystalCount: $("adventureCrystalCount"),
    discoverCount: $("adventureDiscoverCount"),
    protectCount: $("adventureProtectCount"),
    levelValue: $("adventureLevelValue"),
    rankTitle: $("adventureRankTitle"),
    collectionCount: $("adventureCollectionCount"),
    catalogCount: $("adventureCatalogCount"),
    dynamicCollectionCount: $("adventureDynamicCollectionCount"),
    dailyText: $("adventureDailyText"),
    dailyProgress: $("adventureDailyProgress"),
    lootToggle: $("adventureLootToggle"),
    beastsToggle: $("adventureBeastsToggle"),
    dynamicLootToggle: $("adventureDynamicLootToggle"),
    effectsToggle: $("adventureEffectsToggle"),
    editProfile: $("adventureEditProfile"),
    deleteProfile: $("adventureDeleteProfile"),
    effectHost: $("adventureCrystalEffectHost")
  };

  if (!elements.entry || !elements.overlay) return;

  let state = {
    exists: false,
    nickname: "",
    avatarId: "guardian",
    customAvatarData: "",
    crystals: 0,
    totalShieldProtects: 0,
    totalDiscoveries: 0,
    rewardedToday: 0,
    dailyLimit: 30,
    effectsEnabled: true,
    lootEnabled: true,
    spiritBeastsEnabled: true,
    level: 1,
    rankTitle: "Khách lữ hành",
    orbBasic: 0,
    orbSilver: 0,
    orbGold: 0,
    totalBeastEncounters: 0,
    totalBeastCaptures: 0,
    collectionCount: 0,
    catalogCount: 0,
    collection: [],
    catalog: [],
    shop: [],
    dynamicLootEnabled: true,
    dynamicCollectionCount: 0,
    dynamicTotalEncounters: 0,
    dynamicTotalCollected: 0,
    dynamicCollection: [],
    storage: "device"
  };

  let selectedAvatarId = "guardian";
  let selectedCustomAvatar = "";
  let editing = false;
  let activeSubView = "";

  function parseJson(raw, fallback = null) {
    try {
      if (raw && typeof raw === "object") return raw;
      return JSON.parse(String(raw || ""));
    } catch {
      return fallback;
    }
  }

  function avatarFor(id) {
    return AVATARS.find(item => item.id === id) || AVATARS[0];
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function avatarMarkup(avatarId, customAvatarData, extraClass = "") {
    if (customAvatarData) {
      return `<img class="adventure-avatar-image ${extraClass}" src="${escapeHtml(customAvatarData)}" alt="avatar" />`;
    }
    const avatar = avatarFor(avatarId);
    return `<span class="adventure-avatar-symbol ${extraClass}">${avatar.symbol}</span>`;
  }

  function setFormError(message = "") {
    elements.formError.textContent = message;
    elements.formError.classList.toggle("hidden", !message);
  }

  function buildAvatarGrid() {
    elements.avatarGrid.innerHTML = AVATARS.map(item => `
      <button type="button" class="adventure-avatar-option" data-avatar-id="${item.id}"
        role="option" aria-label="${item.name}" aria-selected="false">
        <span>${item.symbol}</span><small>${item.name}</small>
      </button>`).join("");

    elements.avatarGrid.addEventListener("click", event => {
      const button = event.target.closest("button[data-avatar-id]");
      if (!button) return;
      selectedCustomAvatar = "";
      if (elements.uploadInput) elements.uploadInput.value = "";
      if (elements.uploadName) elements.uploadName.textContent = "Chưa chọn ảnh tùy chỉnh";
      selectAvatar(button.dataset.avatarId);
    });
  }

  function selectAvatar(id) {
    const avatar = avatarFor(id);
    selectedAvatarId = avatar.id;
    renderPreviewAvatar();
    elements.avatarGrid.querySelectorAll("button[data-avatar-id]").forEach(button => {
      const selected = button.dataset.avatarId === selectedAvatarId;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-selected", selected ? "true" : "false");
    });
  }

  function renderPreviewAvatar() {
    elements.createPreview.innerHTML = avatarMarkup(selectedAvatarId, selectedCustomAvatar, "preview");
  }

  function renderHeader() {
    if (!state.exists) {
      elements.entry.classList.remove("has-adventure-profile");
      elements.avatarMark.innerHTML = GUEST_SHIELD;
      elements.profileName.textContent = "Chưa có Hồ sơ Phiêu lưu";
      elements.profileStatus.textContent = "Nhấn vào đây để tạo nhà mạo hiểm của bạn";
      elements.entry.setAttribute("aria-label", "Tạo Hồ sơ Phiêu lưu");
      return;
    }
    elements.entry.classList.add("has-adventure-profile");
    elements.avatarMark.innerHTML = avatarMarkup(state.avatarId, state.customAvatarData, "header");
    elements.profileName.textContent = state.nickname || "Nhà mạo hiểm";
    elements.profileStatus.textContent = `${state.rankTitle || "Khám Phá Giả"} · Lv.${state.level || 1}`;
    elements.entry.setAttribute("aria-label", `Mở Hồ sơ Phiêu lưu của ${state.nickname || "người dùng"}`);
  }

  function renderSummary() {
    if (!elements.summaryLevel) return;
    if (!state.exists) {
      elements.summaryLevel.textContent = "1";
      elements.summaryRank.textContent = "Khách lữ hành";
      elements.summaryHint.textContent = "Tạo Hồ sơ Phiêu lưu để mở hành trình khám phá, nhặt Linh Thạch và sưu tập Linh Thú.";
      elements.summaryCrystals.textContent = "0";
      return;
    }
    elements.summaryLevel.textContent = String(state.level || 1);
    elements.summaryRank.textContent = state.rankTitle || "Khám Phá Giả";
    elements.summaryHint.textContent = `${state.dynamicCollectionCount || 0} Kỳ Vật · ${state.collectionCount || 0}/${state.catalogCount || 0} Linh Thú · ${state.totalDiscoveries || 0} vùng đất.`;
    elements.summaryCrystals.textContent = String(state.crystals || 0);
  }

  function renderDashboard() {
    elements.dashboardAvatar.innerHTML = avatarMarkup(state.avatarId, state.customAvatarData, "dashboard");
    elements.dashboardName.textContent = state.nickname || "Nhà mạo hiểm";
    elements.crystalCount.textContent = String(state.crystals || 0);
    elements.discoverCount.textContent = String(state.totalDiscoveries || 0);
    elements.protectCount.textContent = String(state.totalShieldProtects || 0);
    elements.levelValue.textContent = String(state.level || 1);
    elements.rankTitle.textContent = state.rankTitle || "Khám Phá Giả";
    if (elements.collectionCount) elements.collectionCount.textContent = String(state.collectionCount || 0);
    if (elements.catalogCount) elements.catalogCount.textContent = String(state.catalogCount || state.catalog?.length || 0);
    if (elements.dynamicCollectionCount) elements.dynamicCollectionCount.textContent = String(state.dynamicCollectionCount || 0);

    const current = Math.max(0, Number(state.rewardedToday) || 0);
    const limit = Math.max(1, Number(state.dailyLimit) || 30);
    elements.dailyText.textContent = `${current}/${limit} Linh Thạch`;
    elements.dailyProgress.style.width = `${Math.min(100, (current / limit) * 100)}%`;
    elements.effectsToggle.checked = state.effectsEnabled !== false;
    if (elements.lootToggle) elements.lootToggle.checked = state.lootEnabled !== false;
    if (elements.beastsToggle) elements.beastsToggle.checked = state.spiritBeastsEnabled !== false;
    if (elements.dynamicLootToggle) elements.dynamicLootToggle.checked = state.dynamicLootEnabled !== false;
  }

  function applyNativeState(nextState) {
    const parsed = parseJson(nextState, nextState);
    if (!parsed || typeof parsed !== "object") return;
    state = { ...state, ...parsed };
    state.collection = Array.isArray(state.collection) ? state.collection : [];
    state.catalog = Array.isArray(state.catalog) ? state.catalog : [];
    state.shop = Array.isArray(state.shop) ? state.shop : [];
    state.dynamicCollection = Array.isArray(state.dynamicCollection) ? state.dynamicCollection : [];
    selectedAvatarId = avatarFor(state.avatarId).id;
    if (!editing) selectedCustomAvatar = state.customAvatarData || "";
    renderHeader();
    renderSummary();
    renderDashboard();
    if (activeSubView) renderSubView(activeSubView);

    if (!elements.overlay.classList.contains("hidden") && !activeSubView) {
      showView(state.exists && !editing ? "dashboard" : "create");
    }
  }

  function showView(view) {
    activeSubView = "";
    elements.subView?.classList.add("hidden");
    const showDashboard = view === "dashboard" && state.exists;
    elements.createView.classList.toggle("hidden", showDashboard);
    elements.dashboardView.classList.toggle("hidden", !showDashboard);
    elements.title.textContent = showDashboard
      ? "Hồ sơ Phiêu lưu"
      : (editing ? "Chỉnh sửa nhà mạo hiểm" : "Tạo Hồ sơ Phiêu lưu");

    if (!showDashboard) {
      elements.nickname.value = editing ? (state.nickname || "") : "";
      selectedAvatarId = editing ? state.avatarId : "guardian";
      selectedCustomAvatar = editing ? (state.customAvatarData || "") : "";
      renderPreviewAvatar();
      selectAvatar(selectedAvatarId);
      elements.createSubmit.textContent = editing ? "Lưu thay đổi" : "Tạo hồ sơ";
      elements.createLater.textContent = editing ? "Hủy" : "Để sau";
      if (elements.uploadName) {
        elements.uploadName.textContent = selectedCustomAvatar ? "Đã chọn ảnh tùy chỉnh" : "Chưa chọn ảnh tùy chỉnh";
      }
      setFormError("");
      setTimeout(() => elements.nickname.focus(), 100);
    } else {
      renderDashboard();
    }
  }

  function openProfile() {
    editing = false;
    $("chromeMenu")?.classList.add("hidden");
    $("toolsMenu")?.classList.add("hidden");
    elements.overlay.classList.remove("hidden");
    elements.overlay.setAttribute("aria-hidden", "false");
    showView(state.exists ? "dashboard" : "create");
  }

  function closeProfile() {
    editing = false;
    activeSubView = "";
    elements.overlay.classList.add("hidden");
    elements.overlay.setAttribute("aria-hidden", "true");
    setFormError("");
  }

  function callMutation(method, ...args) {
    if (!native || typeof native[method] !== "function") {
      return { ok: false, error: "Hồ sơ Phiêu lưu chỉ hoạt động trong ứng dụng Android." };
    }
    try {
      return parseJson(native[method](...args), { ok: false, error: "Không đọc được phản hồi từ Android." });
    } catch (error) {
      return { ok: false, error: error?.message || "Không cập nhật được hồ sơ." };
    }
  }

  function submitProfile() {
    const nickname = elements.nickname.value.trim();
    if ([...nickname].length < 2 || [...nickname].length > 20) {
      setFormError("Biệt danh cần từ 2 đến 20 ký tự.");
      return;
    }

    let result;
    const advancedMethod = editing ? "updateProfileAdvanced" : "createProfileAdvanced";
    if (native && typeof native[advancedMethod] === "function") {
      result = callMutation(advancedMethod, nickname, selectedAvatarId, selectedCustomAvatar || "");
    } else {
      result = callMutation(editing ? "updateProfile" : "createProfile", nickname, selectedAvatarId);
    }

    if (!result?.ok) {
      setFormError(result?.error || "Không lưu được hồ sơ.");
      return;
    }
    applyNativeState(result.state);
    editing = false;
    showView("dashboard");
    window.toast?.("Đã lưu Hồ sơ Phiêu lưu trên thiết bị.");
  }

  function deleteProfile() {
    const confirmed = window.confirm("Xóa Hồ sơ Phiêu lưu? Linh Thạch, Linh Cầu, Đồ Giám và cấp độ sẽ mất; dữ liệu trình duyệt vẫn được giữ.");
    if (!confirmed) return;
    const result = callMutation("deleteProfile");
    if (!result?.ok) {
      window.toast?.(result?.error || "Không xóa được hồ sơ.");
      return;
    }
    applyNativeState(result.state);
    closeProfile();
    window.toast?.("Đã xóa Hồ sơ Phiêu lưu. Dữ liệu trình duyệt không bị ảnh hưởng.");
  }

  function showCrystalEffect(amount = 1) {
    if (!state.exists || state.effectsEnabled === false || !elements.effectHost) return;
    const effect = document.createElement("div");
    effect.className = "adventure-crystal-flight";
    effect.innerHTML = `<span>◆</span><b>+${Math.max(1, Number(amount) || 1)}</b>`;
    elements.effectHost.appendChild(effect);
    elements.entry.classList.remove("adventure-profile-pulse");
    void elements.entry.offsetWidth;
    elements.entry.classList.add("adventure-profile-pulse");
    const remove = () => effect.remove();
    effect.addEventListener("animationend", remove, { once: true });
    setTimeout(remove, 1800);
  }

  function onShieldProtection(payload) {
    const result = parseJson(payload, payload);
    if (result?.state) applyNativeState(result.state);
  }

  function onRealmCrystalCollected(payload) {
    const result = parseJson(payload, payload);
    if (!result || typeof result !== "object") return;
    if (result.state) applyNativeState(result.state);
    if (result.rewarded) showCrystalEffect(result.amount || 1);
  }

  function onSpiritBeastCapture(payload) {
    const result = parseJson(payload, payload);
    if (!result || typeof result !== "object") return;
    if (result.state) applyNativeState(result.state);
    if (result.success && result.beast) {
      window.toast?.(`Đã thu phục ${result.beast.name} — ${result.beast.rarity}!`);
    }
  }

  function onDynamicLootCollected(payload) {
    const result = parseJson(payload, payload);
    if (!result || typeof result !== "object") return;
    if (result.state) applyNativeState(result.state);
    const entry = result.entry;
    if (result.ok && entry) {
      window.toast?.(`Đã thêm ${entry.name || "Kỳ Vật"} vào Vạn Giới Đồ Giám!`);
    }
  }

  function refreshFromNative() {
    if (!native || typeof native.getProfileState !== "function") {
      applyNativeState(state);
      return;
    }
    try { applyNativeState(native.getProfileState()); }
    catch { applyNativeState(state); }
  }

  function resizeImageFile(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error("Không đọc được ảnh."));
      reader.onload = () => {
        const image = new Image();
        image.onerror = () => reject(new Error("Ảnh không hợp lệ."));
        image.onload = () => {
          const maxSize = 192;
          const scale = Math.min(1, maxSize / Math.max(image.width, image.height));
          const width = Math.max(48, Math.round(image.width * scale));
          const height = Math.max(48, Math.round(image.height * scale));
          const canvas = document.createElement("canvas");
          canvas.width = width;
          canvas.height = height;
          canvas.getContext("2d").drawImage(image, 0, 0, width, height);
          resolve(canvas.toDataURL("image/png", 0.92));
        };
        image.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  function collectionById() {
    return new Map((state.collection || []).map(entry => [entry.beastId, entry]));
  }

  function rarityClass(rarity) {
    return `rarity-${String(rarity || "thuong").toLowerCase()
      .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
      .replaceAll(" ", "-")}`;
  }

  function openSubView(view) {
    if (!state.exists) return;
    activeSubView = view;
    editing = false;
    elements.createView.classList.add("hidden");
    elements.dashboardView.classList.add("hidden");
    elements.subView.classList.remove("hidden");
    renderSubView(view);
  }

  function renderSubView(view) {
    if (!elements.subContent || !activeSubView) return;
    const titles = {
      collection: "Đồ Giám Vạn Giới",
      inventory: "Túi Hành Trang",
      shop: "Cửa Hàng Linh Thạch",
      share: "Thẻ Khoe Thành Tích"
    };
    elements.subTitle.textContent = titles[view] || "Vạn Giới";
    if (view === "collection") renderCollection();
    else if (view === "inventory") renderInventory();
    else if (view === "shop") renderShop();
    else renderShareCard();
  }

  function renderCollection() {
    const dynamicCards = (state.dynamicCollection || []).map(entry => {
      const image = entry.displayImageUrl || entry.imageUrl || "";
      const source = entry.generated ? "AI nguyên bản" : (entry.sourceType === "wikimedia" ? "Wikimedia" : "Nguồn mở");
      return `<article class="dynamic-loot-card ${rarityClass(entry.rarity)}">
        <div class="dynamic-loot-image-wrap">
          ${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(entry.name)}" loading="lazy" />` : `<span>?</span>`}
          <em>${escapeHtml(entry.rarity || "Thường")}</em>
        </div>
        <b>${escapeHtml(entry.name || "Kỳ Vật Vô Danh")}</b>
        <span>${escapeHtml(entry.category || "Kỳ Vật")} · ${"★".repeat(Math.max(1, Math.min(5, Number(entry.stars) || 1)))}</span>
        <small>${escapeHtml(entry.description || "Một phát hiện bí ẩn từ Vạn Giới.")}</small>
        <footer><span>${source}</span><span>×${entry.count || 1}</span></footer>
      </article>`;
    }).join("");

    const owned = collectionById();
    const catalog = state.catalog || [];
    const beastCards = catalog.map(beast => {
      const entry = owned.get(beast.id);
      const captured = Boolean(entry);
      return `<article class="spirit-beast-card ${captured ? "captured" : "locked"} ${rarityClass(beast.rarity)}">
        <div class="spirit-beast-icon">${captured ? escapeHtml(beast.icon) : "?"}</div>
        <b>${captured ? escapeHtml(beast.name) : "Chưa khám phá"}</b>
        <span>${captured ? `${escapeHtml(beast.family)} · ${escapeHtml(beast.rarity)}` : escapeHtml(beast.rarity)}</span>
        <small>${captured ? `Bắt được ×${entry.count} · ${escapeHtml(entry.firstDomain || "Vạn Giới")}` : "Tiếp tục khám phá các trang web mới"}</small>
      </article>`;
    }).join("");

    elements.subContent.innerHTML = `
      <div class="adventure-sub-summary dynamic-summary">
        <b>${state.dynamicCollectionCount || 0} Kỳ Vật động</b>
        <small>Nội dung gần như không cạn: ảnh và mô tả đến từ Dynamic Loot Engine; khi AI chưa cấu hình, ứng dụng dùng Wikipedia/Wikimedia làm nguồn miễn phí.</small>
      </div>
      <div class="dynamic-loot-grid">${dynamicCards || `<div class="adventure-empty-collection"><b>Chưa có Kỳ Vật động</b><small>Tiếp tục đi đến các trang web mới. Kỳ Vật đầu tiên có tỷ lệ xuất hiện cao để bạn thử hệ thống.</small></div>`}</div>
      <div class="adventure-sub-summary">
        <b>${state.collectionCount || 0}/${state.catalogCount || catalog.length} Linh Thú nguyên bản</b>
        <small>Bộ Linh Thú offline vẫn được giữ làm nội dung dự phòng khi mất mạng.</small>
      </div>
      <div class="spirit-beast-grid">${beastCards || "<p>Chưa tải được danh mục.</p>"}</div>`;
  }

  function renderInventory() {
    elements.subContent.innerHTML = `
      <div class="adventure-inventory-grid">
        <article class="orb-card basic"><span>◉</span><div><b>Linh Cầu Thô</b><strong>×${state.orbBasic || 0}</strong><small>Dùng cho Linh Thú thường và những lần thử cơ bản.</small></div></article>
        <article class="orb-card silver"><span>◉</span><div><b>Linh Cầu Bạc</b><strong>×${state.orbSilver || 0}</strong><small>Tăng tỷ lệ thu phục sinh vật hiếm.</small></div></article>
        <article class="orb-card gold"><span>◉</span><div><b>Linh Cầu Hoàng Kim</b><strong>×${state.orbGold || 0}</strong><small>Phù hợp với Linh Thú Sử Thi trở lên.</small></div></article>
      </div>
      <div class="adventure-guide-card">
        <b>Thành tích thu phục</b>
        <small>Đã gặp ${state.totalBeastEncounters || 0} lần · Thu phục thành công ${state.totalBeastCaptures || 0} lần · Sở hữu ${state.collectionCount || 0} loài khác nhau.</small>
      </div>
      <div class="adventure-guide-card">
        <b>Hành trang đang mở rộng</b>
        <small>Các bản sau sẽ thêm mảnh bản đồ, trứng Linh Thú, khung avatar, vật phẩm hiếm và trang bị nhà mạo hiểm.</small>
      </div>`;
  }

  function renderShop() {
    const shopCards = (state.shop || []).map(item => `
      <article class="adventure-shop-card">
        <span class="shop-icon">${item.orbType === "gold" ? "🟡" : item.orbType === "silver" ? "⚪" : "🟢"}</span>
        <div><b>${escapeHtml(item.name)}</b><small>${escapeHtml(item.description)}</small></div>
        <button type="button" data-shop-item="${escapeHtml(item.id)}">◆ ${item.cost}</button>
      </article>`).join("");
    elements.subContent.innerHTML = `
      <div class="adventure-sub-summary shop-balance"><b>◆ ${state.crystals || 0} Linh Thạch</b><small>Mọi giao dịch chỉ lưu offline trong Hồ sơ Phiêu lưu.</small></div>
      <div class="adventure-shop-list">${shopCards}</div>`;
    elements.subContent.querySelectorAll("button[data-shop-item]").forEach(button => {
      button.addEventListener("click", () => purchaseShopItem(button.dataset.shopItem));
    });
  }

  function purchaseShopItem(itemId) {
    const result = callMutation("purchaseShopItem", itemId);
    if (!result?.ok) {
      window.toast?.(result?.error || "Không mua được vật phẩm.");
      return;
    }
    applyNativeState(result.state);
    window.toast?.("Đã thêm vật phẩm vào Túi Hành Trang.");
    renderShop();
  }

  function renderShareCard() {
    const owned = collectionById();
    const rare = [...(state.catalog || [])]
      .filter(beast => owned.has(beast.id))
      .sort((a, b) => rarityScore(b.rarity) - rarityScore(a.rarity))
      .slice(0, 6);
    const icons = rare.map(beast => `<span title="${escapeHtml(beast.name)}">${escapeHtml(beast.icon)}</span>`).join("") || "<span>?</span>";
    const dynamicHighlights = [...(state.dynamicCollection || [])]
      .sort((a, b) => rarityScore(b.rarity) - rarityScore(a.rarity))
      .slice(0, 4)
      .map(item => {
        const image = item.displayImageUrl || item.imageUrl || "";
        return image ? `<img src="${escapeHtml(image)}" title="${escapeHtml(item.name)}" alt="${escapeHtml(item.name)}" />` : "";
      }).join("");
    elements.subContent.innerHTML = `
      <div class="adventure-share-card" id="adventureShareCard">
        <div class="share-card-top">
          <div class="share-avatar">${avatarMarkup(state.avatarId, state.customAvatarData, "share")}</div>
          <div><span>NHÀ MẠO HIỂM VẠN GIỚI</span><h4>${escapeHtml(state.nickname || "Nhà mạo hiểm")}</h4><p>Lv.${state.level || 1} · ${escapeHtml(state.rankTitle || "Khám Phá Giả")}</p></div>
        </div>
        <div class="share-stats">
          <div><b>${state.crystals || 0}</b><small>Linh Thạch</small></div>
          <div><b>${state.dynamicCollectionCount || 0}</b><small>Kỳ Vật</small></div>
          <div><b>${state.totalDiscoveries || 0}</b><small>Vùng đất</small></div>
        </div>
        <div class="share-rare dynamic"><small>Kỳ Vật nổi bật</small><div>${dynamicHighlights || icons}</div></div>
        <div class="share-rare"><small>Linh Thú nổi bật</small><div>${icons}</div></div>
        <footer>lqlq Browser · Đồ Giám Vạn Giới</footer>
      </div>
      <div class="adventure-guide-card"><b>Sẵn sàng để khoe</b><small>Thẻ này được bố trí riêng để bạn chụp màn hình và chia sẻ bộ sưu tập với bạn bè. Các bản sau có thể thêm nút xuất ảnh trực tiếp.</small></div>`;
  }

  function rarityScore(rarity) {
    return ({ "Thần Thoại": 5, "Huyền Thoại": 4, "Sử Thi": 3, "Hiếm": 2, "Thường": 1 })[rarity] || 0;
  }

  buildAvatarGrid();
  selectAvatar("guardian");

  elements.entry.addEventListener("click", openProfile);
  elements.entry.addEventListener("keydown", event => {
    if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openProfile(); }
  });
  elements.close.addEventListener("click", closeProfile);
  elements.createLater.addEventListener("click", () => {
    if (editing) { editing = false; showView("dashboard"); }
    else closeProfile();
  });
  elements.createSubmit.addEventListener("click", submitProfile);
  elements.nickname.addEventListener("keydown", event => { if (event.key === "Enter") submitProfile(); });
  elements.editProfile.addEventListener("click", () => { editing = true; showView("create"); });
  elements.deleteProfile.addEventListener("click", deleteProfile);
  elements.effectsToggle.addEventListener("change", () => {
    const result = callMutation("setEffectsEnabled", elements.effectsToggle.checked);
    if (result?.ok) applyNativeState(result.state);
    else elements.effectsToggle.checked = state.effectsEnabled !== false;
  });
  elements.lootToggle?.addEventListener("change", () => {
    const result = callMutation("setLootEnabled", elements.lootToggle.checked);
    if (result?.ok) applyNativeState(result.state);
    else elements.lootToggle.checked = state.lootEnabled !== false;
  });
  elements.beastsToggle?.addEventListener("change", () => {
    const result = callMutation("setSpiritBeastsEnabled", elements.beastsToggle.checked);
    if (result?.ok) applyNativeState(result.state);
    else elements.beastsToggle.checked = state.spiritBeastsEnabled !== false;
  });
  elements.dynamicLootToggle?.addEventListener("change", () => {
    const result = callMutation("setDynamicLootEnabled", elements.dynamicLootToggle.checked);
    if (result?.ok) applyNativeState(result.state);
    else elements.dynamicLootToggle.checked = state.dynamicLootEnabled !== false;
  });
  document.querySelectorAll("[data-adventure-view]").forEach(button => {
    button.addEventListener("click", () => openSubView(button.dataset.adventureView));
  });
  elements.subBack?.addEventListener("click", () => showView("dashboard"));
  elements.uploadBtn?.addEventListener("click", () => elements.uploadInput?.click());
  elements.uploadInput?.addEventListener("change", async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      selectedCustomAvatar = await resizeImageFile(file);
      if (elements.uploadName) elements.uploadName.textContent = file.name || "Ảnh tùy chỉnh";
      renderPreviewAvatar();
      setFormError("");
    } catch (error) {
      setFormError(error?.message || "Không xử lý được ảnh đại diện.");
    }
  });
  elements.overlay.addEventListener("click", event => { if (event.target === elements.overlay) closeProfile(); });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !elements.overlay.classList.contains("hidden")) {
      if (activeSubView) showView("dashboard"); else closeProfile();
    }
  });

  window.LqlqAdventureUI = {
    applyNativeState,
    onShieldProtection,
    onRealmCrystalCollected,
    onSpiritBeastCapture,
    onDynamicLootCollected,
    openProfile,
    closeProfile,
    refreshFromNative
  };

  refreshFromNative();
})();
