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
    summaryCrystals: $("adventureSummaryCrystals"),
    overlay: $("adventureProfileOverlay"),
    close: $("adventureProfileClose"),
    back: $("adventureProfileBack"),
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
    portraitButton: $("adventurePortraitButton"),
    portraitImage: $("adventurePortraitImage"),
    portraitEmpty: $("adventurePortraitEmpty"),
    portraitUpload: $("adventurePortraitUpload"),
    cardSlots: $("adventureCardSlots"),
    cardModal: $("adventureCardModal"),
    cardModalClose: $("adventureCardModalClose"),
    cardModalImage: $("adventureCardModalImage"),
    cardModalName: $("adventureCardModalName"),
    cardModalMeta: $("adventureCardModalMeta"),
    cardModalEquip: $("adventureCardModalEquip"),
    cardModalUnequip: $("adventureCardModalUnequip"),
    cardModalDelete: $("adventureCardModalDelete"),
    charHp: $("adventureCharHp"),
    charAtk: $("adventureCharAtk"),
    charMana: $("adventureCharMana"),
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
    themeSelect: $("adventureThemeSelect"),
    lootToggle: $("adventureLootToggle"),
    beastsToggle: $("adventureBeastsToggle"),
    dynamicLootToggle: $("adventureDynamicLootToggle"),
    effectsToggle: $("adventureEffectsToggle"),
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
    storage: "device",
    portraitSet: false,
    portraitVersion: 0,
    characterHp: 100,
    characterAtk: 10,
    characterMana: 10,
    slotCapacity: 20,
    slotUsed: 0,
    slotPriceCrystals: 10,
    identityChangeCredits: 0,
    portraitChangeCredits: 0,
    equippedCardIds: [],
    collectionTheme: ""
  };

  let activeCardId = "";

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
    if (!elements.summaryCrystals) return;
    elements.summaryCrystals.textContent = String(state.exists ? (state.crystals || 0) : 0);
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
    if (elements.themeSelect) elements.themeSelect.value = state.collectionTheme || "";
    if (elements.lootToggle) elements.lootToggle.checked = state.lootEnabled !== false;
    if (elements.beastsToggle) elements.beastsToggle.checked = state.spiritBeastsEnabled !== false;
    if (elements.dynamicLootToggle) elements.dynamicLootToggle.checked = state.dynamicLootEnabled !== false;
    renderPortrait();
    renderCardSlots();
    if (elements.charHp) elements.charHp.textContent = String(state.characterHp || 100);
    if (elements.charAtk) elements.charAtk.textContent = String(state.characterAtk || 10);
    if (elements.charMana) elements.charMana.textContent = String(state.characterMana || 10);
  }

  const CARD_SLOT_COUNT = 10;

  function cardById(id) {
    return (state.dynamicCollection || []).find(entry => entry.id === id) || null;
  }

  function cardSlotGridHtml(items, capacity) {
    const slots = [];
    for (let i = 0; i < capacity; i++) {
      const entry = items[i];
      if (!entry) {
        slots.push(`<div class="adventure-gear-slot empty"></div>`);
        continue;
      }
      const image = entry.displayImageUrl || entry.imageUrl || "";
      slots.push(`<div class="adventure-gear-slot filled ${rarityClass(entry.rarity)}" data-card-id="${escapeHtml(entry.id)}" title="${escapeHtml(entry.name || "Thẻ Kỳ Vật")}">
        ${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(entry.name || "")}" loading="lazy" />` : "<span>?</span>"}
      </div>`);
    }
    return slots.join("");
  }

  function renderCardSlots() {
    if (!elements.cardSlots) return;
    const equipped = (state.equippedCardIds || []).map(cardById).filter(Boolean);
    elements.cardSlots.innerHTML = cardSlotGridHtml(equipped, CARD_SLOT_COUNT);
  }

  function openCardModal(cardId) {
    const entry = cardById(cardId);
    if (!entry || !elements.cardModal) return;
    activeCardId = cardId;
    const image = entry.displayImageUrl || entry.imageUrl || "";
    if (elements.cardModalImage) {
      if (image) { elements.cardModalImage.src = image; elements.cardModalImage.classList.remove("hidden"); }
      else elements.cardModalImage.classList.add("hidden");
    }
    if (elements.cardModalName) elements.cardModalName.textContent = entry.name || "Thẻ Kỳ Vật";
    if (elements.cardModalMeta) {
      elements.cardModalMeta.textContent = `${entry.rarity || "Thường"} · +${entry.statValue || 1} ${entry.statType || "ATK"}`;
    }
    const isEquipped = (state.equippedCardIds || []).includes(cardId);
    elements.cardModalEquip?.classList.toggle("hidden", isEquipped);
    elements.cardModalUnequip?.classList.toggle("hidden", !isEquipped);
    elements.cardModal.classList.remove("hidden");
  }

  function closeCardModal() {
    activeCardId = "";
    elements.cardModal?.classList.add("hidden");
  }

  function equipActiveCard() {
    if (!activeCardId) return;
    const result = callMutation("equipCard", activeCardId);
    if (!result?.ok) { window.toast?.(result?.error || "Không gắn được thẻ."); return; }
    applyNativeState(result.state);
    window.toast?.("Đã gắn Thẻ Kỳ Vật vào nhân vật.");
    closeCardModal();
  }

  function unequipActiveCard() {
    if (!activeCardId) return;
    const result = callMutation("unequipCard", activeCardId);
    if (!result?.ok) { window.toast?.(result?.error || "Không tháo được thẻ."); return; }
    applyNativeState(result.state);
    window.toast?.("Đã tháo Thẻ Kỳ Vật.");
    closeCardModal();
  }

  function deleteActiveCard() {
    if (!activeCardId) return;
    if (!window.confirm("Xóa hẳn Thẻ Kỳ Vật này? Không thể khôi phục lại.")) return;
    const result = callMutation("deleteCard", activeCardId);
    if (!result?.ok) { window.toast?.(result?.error || "Không xóa được thẻ."); return; }
    applyNativeState(result.state);
    window.toast?.("Đã xóa Thẻ Kỳ Vật.");
    closeCardModal();
    if (activeSubView === "inventory") renderInventory();
    if (activeSubView === "collection") renderCollection();
  }

  function renderPortrait() {
    if (!elements.portraitImage || !elements.portraitEmpty) return;
    if (state.portraitSet) {
      elements.portraitImage.src = `https://appassets.androidapp.com/character-portrait/portrait.jpg?v=${state.portraitVersion || 0}`;
      elements.portraitImage.classList.remove("hidden");
      elements.portraitEmpty.classList.add("hidden");
    } else {
      elements.portraitImage.classList.add("hidden");
      elements.portraitImage.removeAttribute("src");
      elements.portraitEmpty.classList.remove("hidden");
    }
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
    resetProfileScroll();
  }

  function openProfile() {
    editing = false;
    $("chromeMenu")?.classList.add("hidden");
    $("toolsMenu")?.classList.add("hidden");
    elements.overlay.classList.remove("hidden");
    elements.overlay.setAttribute("aria-hidden", "false");
    showView(state.exists ? "dashboard" : "create");
    resetProfileScroll();
  }

  /**
   * Vòng lặp mở Hồ sơ Phiêu lưu nhiều lần (đặc biệt sau khi cuộn xuống xem
   * túi hành trang/cửa hàng rồi thoát) có thể giữ nguyên scrollTop cũ của
   * lần trước — làm phần đầu trang (nút thoát, tiêu đề) bị cuộn khuất mất,
   * trông như "không có nút thoát". Luôn đưa về đầu mỗi khi mở lại hoặc
   * đổi màn hình.
   */
  function resetProfileScroll() {
    const card = elements.overlay.querySelector(".adventure-profile-card");
    const reset = () => {
      // Trên WebView Android, cả overlay và card đều từng có thể trở thành
      // vùng cuộn. Chỉ đặt scrollTop cho card khiến vị trí cuộn cũ của
      // overlay vẫn giữ lại, làm phần header bị trượt lên dưới thanh địa chỉ.
      elements.overlay.scrollTop = 0;
      elements.overlay.scrollLeft = 0;
      if (card) {
        card.scrollTop = 0;
        card.scrollLeft = 0;
      }
    };

    reset();
    // Chạy lại sau khi class hidden/view vừa đổi và WebView đã tính layout.
    requestAnimationFrame(reset);
  }

  /**
   * Thoát Hồ sơ Phiêu lưu (nút X hoặc back) quay về Menu chức năng
   * (chromeMenu) thay vì đóng thẳng ra trang web — vì hồ sơ được mở
   * từ trong menu đó, "thoát 1 lần" nên lùi lại đúng 1 cấp menu.
   */
  function closeProfile() {
    editing = false;
    activeSubView = "";
    elements.overlay.classList.add("hidden");
    elements.overlay.setAttribute("aria-hidden", "true");
    setFormError("");

    // Sự kiện click của nút Back/X tiếp tục nổi bọt lên document. app.js có
    // bộ đóng menu khi chạm ngoài, nên nếu mở chromeMenu ngay tại đây thì nó
    // sẽ bị đóng lại trong chính cú chạm đó. Mở ở frame kế tiếp để thật sự
    // quay về đúng Menu chức năng — nơi người dùng đã bấm avatar để vào hồ sơ.
    requestAnimationFrame(() => {
      $("toolsMenu")?.classList.add("hidden");
      $("chromeMenu")?.classList.remove("hidden");
    });
  }

  /**
   * Nút Back Android (v0.32.1): trước đây bấm Back trong lúc đang ở 1 màn
   * con (Đồ Giám/Túi Hành Trang/Cửa Hàng/Thẻ Khoe) sẽ đóng LUÔN cả bảng Hồ
   * sơ Phiêu lưu, giống hệt bấm nút X — không giống cảm giác "quay lại"
   * thông thường. android-glue.js gọi hàm này TRƯỚC khi tự đóng overlay:
   * nếu đang ở màn con thì quay về dashboard và trả về true (đã xử lý,
   * không đóng overlay); nếu đã ở dashboard rồi thì trả về false để
   * android-glue.js đóng overlay như bình thường.
   */
  function handleBackPress() {
    if (elements.overlay.classList.contains("hidden")) return false;
    if (activeSubView) {
      showView("dashboard");
      return true;
    }
    return false;
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
    window.toast?.("Đã lưu Hồ sơ Phiêu lưu.");
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
      window.toast?.(`Đã thêm ${entry.name || "Thẻ Kỳ Vật"} vào Vạn Giới Đồ Giám!`);
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

  function resizePortraitFile(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error("Không đọc được ảnh."));
      reader.onload = () => {
        const image = new Image();
        image.onerror = () => reject(new Error("Ảnh không hợp lệ."));
        image.onload = () => {
          const maxWidth = 1024;
          const maxHeight = 2048;
          const scale = Math.min(1, maxWidth / image.width, maxHeight / image.height);
          const width = Math.max(1, Math.round(image.width * scale));
          const height = Math.max(1, Math.round(image.height * scale));
          const canvas = document.createElement("canvas");
          canvas.width = width;
          canvas.height = height;
          canvas.getContext("2d").drawImage(image, 0, 0, width, height);
          resolve(canvas.toDataURL("image/jpeg", 0.9));
        };
        image.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  async function pickCharacterPortrait(file) {
    if (!file) return;
    try {
      const dataUri = await resizePortraitFile(file);
      const result = callMutation("setCharacterPortrait", dataUri);
      if (!result?.ok) {
        window.toast?.(result?.error || "Không đặt được ngoại hình nhân vật.");
        return;
      }
      applyNativeState(result.state);
      window.toast?.("Đã đặt ngoại hình nhân vật.");
    } catch (error) {
      window.toast?.(error?.message || "Không xử lý được ảnh ngoại hình.");
    } finally {
      if (elements.portraitUpload) elements.portraitUpload.value = "";
    }
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
    resetProfileScroll();
  }

  function renderSubView(view) {
    if (!elements.subContent || !activeSubView) return;
    const titles = {
      collection: "Vạn Giới Đồ Giám",
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
        <b>${escapeHtml(entry.name || "Thẻ Kỳ Vật Vô Danh")}</b>
        <span>${escapeHtml(entry.category || "Thẻ Kỳ Vật")} · ${"★".repeat(Math.max(1, Math.min(5, Number(entry.stars) || 1)))}</span>
        <small>${escapeHtml(entry.description || "Một phát hiện bí ẩn từ Vạn Giới.")}</small>
        <div class="dynamic-loot-stat-badge">+${entry.statValue || 1} ${entry.statType || "ATK"}</div>
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
        <b>${state.dynamicCollectionCount || 0} Thẻ Kỳ Vật</b>
        <small>Nội dung gần như không cạn: ảnh và mô tả đến từ Dynamic Loot Engine; khi AI chưa cấu hình, ứng dụng dùng Wikipedia/Wikimedia làm nguồn miễn phí.</small>
      </div>
      <div class="dynamic-loot-grid">${dynamicCards || `<div class="adventure-empty-collection"><b>Chưa có Thẻ Kỳ Vật</b><small>Tiếp tục đi đến các trang web mới. Kỳ Vật đầu tiên có tỷ lệ xuất hiện cao để bạn thử hệ thống.</small></div>`}</div>
      <div class="adventure-sub-summary">
        <b>${state.collectionCount || 0}/${state.catalogCount || catalog.length} Linh Thú nguyên bản</b>
        <small>Bộ Linh Thú offline vẫn được giữ làm nội dung dự phòng khi mất mạng.</small>
      </div>
      <div class="spirit-beast-grid">${beastCards || "<p>Chưa tải được danh mục.</p>"}</div>`;
  }

  function purchaseInventorySlot() {
    const result = callMutation("purchaseInventorySlot");
    if (!result?.ok) {
      window.toast?.(result?.error || "Không mở được ô hành trang.");
      return;
    }
    applyNativeState(result.state);
    window.toast?.("Đã mở thêm 1 ô hành trang.");
    renderInventory();
  }

  function unequippedCards() {
    const equipped = new Set(state.equippedCardIds || []);
    return (state.dynamicCollection || []).filter(entry => !equipped.has(entry.id));
  }

  function renderInventory() {
    const used = state.slotUsed || 0;
    const capacity = state.slotCapacity || 20;
    const usableCards = [];
    if (state.identityChangeCredits > 0) {
      usableCards.push(`
        <article class="adventure-shop-card">
          <span class="shop-icon">🪪</span>
          <div><b>Thẻ Đổi Danh Tính</b><small>Sở hữu ×${state.identityChangeCredits} — đổi lại biệt danh/avatar.</small></div>
          <button type="button" id="adventureUseIdentityBtn">Sử dụng</button>
        </article>`);
    }
    if (state.portraitChangeCredits > 0) {
      usableCards.push(`
        <article class="adventure-shop-card">
          <span class="shop-icon">🖼️</span>
          <div><b>Thẻ Đổi Ngoại Hình</b><small>Sở hữu ×${state.portraitChangeCredits} — đặt lại ảnh ngoại hình lớn.</small></div>
          <button type="button" id="adventureUsePortraitBtn">Sử dụng</button>
        </article>`);
    }
    elements.subContent.innerHTML = `
      <div class="adventure-sub-summary">
        <b>Túi hành trang · ${used}/${capacity} ô Thẻ Kỳ Vật</b>
        <small>Đầy ô? Mua thêm bằng Linh Thạch để tiếp tục sưu tập. Thẻ đã gắn vào nhân vật không chiếm ô.</small>
      </div>
      <div class="adventure-shop-list">
        <article class="adventure-shop-card">
          <span class="shop-icon">🎒</span>
          <div><b>Mở thêm 1 ô hành trang</b><small>Tăng vĩnh viễn giới hạn lưu Thẻ Kỳ Vật.</small></div>
          <button type="button" id="adventureBuySlotBtn">◆ ${state.slotPriceCrystals || 10}</button>
        </article>
        ${usableCards.join("")}
      </div>
      <div class="adventure-gear-row wide" id="adventureInventoryCardSlots">${cardSlotGridHtml(unequippedCards(), capacity)}</div>
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
    $("adventureBuySlotBtn")?.addEventListener("click", purchaseInventorySlot);
    $("adventureUseIdentityBtn")?.addEventListener("click", useIdentityCard);
    $("adventureUsePortraitBtn")?.addEventListener("click", usePortraitCard);
  }

  function useIdentityCard() {
    editing = true;
    showView("create");
  }

  function usePortraitCard() {
    showView("dashboard");
    elements.portraitUpload?.click();
  }

  const SHOP_ICONS = { gold: "🟡", silver: "⚪", basic: "🟢", identity: "🪪", portrait: "🖼️" };

  function renderShop() {
    const shopCards = (state.shop || []).map(item => `
      <article class="adventure-shop-card">
        <span class="shop-icon">${SHOP_ICONS[item.orbType] || "🟢"}</span>
        <div><b>${escapeHtml(item.name)}</b><small>${escapeHtml(item.description)}</small></div>
        <button type="button" data-shop-item="${escapeHtml(item.id)}">◆ ${item.cost}</button>
      </article>`).join("");
    elements.subContent.innerHTML = `
      <div class="adventure-sub-summary shop-balance"><b>◆ ${state.crystals || 0} Linh Thạch</b></div>
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
          <div><b>${state.dynamicCollectionCount || 0}</b><small>Thẻ Kỳ Vật</small></div>
          <div><b>${state.totalDiscoveries || 0}</b><small>Vùng đất</small></div>
        </div>
        <div class="share-rare dynamic"><small>Thẻ Kỳ Vật nổi bật</small><div>${dynamicHighlights || icons}</div></div>
        <div class="share-rare"><small>Linh Thú nổi bật</small><div>${icons}</div></div>
        <footer>lqlq Browser · Vạn Giới Đồ Giám</footer>
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
  elements.back?.addEventListener("click", () => {
    if (activeSubView) showView("dashboard");
    else closeProfile();
  });
  elements.createLater.addEventListener("click", () => {
    if (editing) { editing = false; showView("dashboard"); }
    else closeProfile();
  });
  elements.createSubmit.addEventListener("click", submitProfile);
  elements.nickname.addEventListener("keydown", event => { if (event.key === "Enter") submitProfile(); });
  // Việc bỏ nút "Chỉnh sửa" miễn phí (v0.32.1): sau khi tạo hồ sơ, biệt danh
  // và avatar bị khoá — chỉ đổi được bằng thẻ đổi tên/đổi avatar mua bằng
  // Linh Thạch (chưa triển khai). Nhánh `editing` trong showView()/submitProfile()
  // vẫn giữ nguyên để tái dùng cho tính năng thẻ đổi đó sau này.
  elements.deleteProfile.addEventListener("click", deleteProfile);
  elements.themeSelect?.addEventListener("change", () => {
    const value = elements.themeSelect.value;
    const result = callMutation("setCollectionTheme", value);
    if (result?.ok) {
      applyNativeState(result.state);
      window.toast?.(value ? "Đã đặt mục tiêu sưu tập." : "Đã bỏ mục tiêu sưu tập, quay về ngẫu nhiên.");
    } else {
      window.toast?.(result?.error || "Không lưu được mục tiêu sưu tập.");
      elements.themeSelect.value = state.collectionTheme || "";
    }
  });
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
  document.addEventListener("click", event => {
    const slot = event.target.closest(".adventure-gear-slot.filled[data-card-id]");
    if (slot) openCardModal(slot.dataset.cardId);
  });
  elements.cardModalClose?.addEventListener("click", closeCardModal);
  elements.cardModal?.addEventListener("click", event => { if (event.target === elements.cardModal) closeCardModal(); });
  elements.cardModalEquip?.addEventListener("click", equipActiveCard);
  elements.cardModalUnequip?.addEventListener("click", unequipActiveCard);
  elements.cardModalDelete?.addEventListener("click", deleteActiveCard);
  elements.portraitButton?.addEventListener("click", () => {
    if (state.portraitSet) {
      window.toast?.("Đổi ngoại hình cần tốn Linh Thạch — tính năng đổi trả phí sẽ có sau.");
      return;
    }
    elements.portraitUpload?.click();
  });
  elements.portraitUpload?.addEventListener("change", event => {
    pickCharacterPortrait(event.target.files?.[0]);
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
    refreshFromNative,
    handleBackPress
  };

  refreshFromNative();

  // Hiện số phiên bản app trong header — giúp người dùng tự xác nhận đã
  // cài đúng bản build mới nhất, không lẫn cache/APK cũ.
  try {
    const versionEl = $("adventureAppVersion");
    if (versionEl && window.LqlqAndroid?.getAppVersion) {
      versionEl.textContent = window.LqlqAndroid.getAppVersion();
    }
  } catch {}
})();
