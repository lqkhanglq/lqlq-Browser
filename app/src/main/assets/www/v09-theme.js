(() => {
  const APPEARANCE_KEY = "shieldAppearanceV09";

  const defaults = {
    mode: "light",
    accent: "emerald"
  };

  const modes = [
    {
      id: "light",
      name: "Sáng",
      description: "Rõ ràng, nhẹ và dễ đọc ban ngày.",
      icon: "☀"
    },
    {
      id: "dark",
      name: "Tối",
      description: "Nền tối dịu mắt khi dùng ban đêm.",
      icon: "◐"
    },
    {
      id: "system",
      name: "Theo hệ thống",
      description: "Tự đổi theo giao diện của thiết bị.",
      icon: "◒"
    },
    {
      id: "comfort",
      name: "Dịu mắt",
      description: "Tông ấm, giảm độ chói khi đọc lâu.",
      icon: "◉",
      isNew: true
    }
  ];

  const accents = [
    { id: "emerald", name: "Ngọc lục bảo", color: "#16a05a" },
    { id: "ocean", name: "Đại dương", color: "#2585c7" },
    { id: "amber", name: "Hổ phách", color: "#d98720" },
    { id: "violet", name: "Tím thanh lịch", color: "#7a5ac8" },
    { id: "rose", name: "Hồng trầm", color: "#c45476" },
    { id: "graphite", name: "Than chì", color: "#56636b" }
  ];

  const systemDarkQuery = window.matchMedia
    ? window.matchMedia("(prefers-color-scheme: dark)")
    : null;

  function readAppearance() {
    try {
      return {
        ...defaults,
        ...JSON.parse(localStorage.getItem(APPEARANCE_KEY) || "{}")
      };
    } catch {
      return { ...defaults };
    }
  }

  let appearance = readAppearance();

  function saveAppearance() {
    localStorage.setItem(APPEARANCE_KEY, JSON.stringify(appearance));
  }

  function resolveMode() {
    if (appearance.mode !== "system") {
      return appearance.mode;
    }

    return systemDarkQuery?.matches ? "dark" : "light";
  }

  function applyAppearance() {
    const root = document.documentElement;
    const resolved = resolveMode();

    root.dataset.theme = resolved;
    root.dataset.themeChoice = appearance.mode;
    root.dataset.accent = appearance.accent;

    let meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement("meta");
      meta.name = "theme-color";
      document.head.appendChild(meta);
    }

    const colorMap = {
      light: "#f4f8f5",
      dark: "#101a14",
      comfort: "#f0eadf"
    };

    meta.content = colorMap[resolved] || colorMap.light;

    document.dispatchEvent(new CustomEvent("shield:appearance-change", {
      detail: {
        ...appearance,
        resolvedMode: resolved
      }
    }));
  }

  function selectedClass(condition) {
    return condition ? " selected" : "";
  }

  function settingsMarkup() {
    const modeCards = modes.map(mode => `
      <button
        type="button"
        class="appearance-mode-card${selectedClass(appearance.mode === mode.id)}"
        data-appearance-mode="${mode.id}"
      >
        <span class="appearance-mode-icon">${mode.icon}</span>
        <span class="appearance-mode-copy">
          <b>
            ${mode.name}
            ${mode.isNew ? '<em class="appearance-new-badge">Mới</em>' : ""}
          </b>
          <small>${mode.description}</small>
        </span>
        <span class="appearance-radio"></span>
      </button>
    `).join("");

    const accentButtons = accents.map(accent => `
      <button
        type="button"
        class="accent-choice${selectedClass(appearance.accent === accent.id)}"
        data-accent-choice="${accent.id}"
        title="${accent.name}"
      >
        <span
          class="accent-swatch"
          style="--swatch-color:${accent.color}"
        ></span>
        <span>${accent.name}</span>
        <i>✓</i>
      </button>
    `).join("");

    return `
      <div class="settings-groups appearance-settings">
        <section class="settings-card appearance-card">
          <div class="settings-section-heading">
            <div>
              <span class="section-kicker">GIAO DIỆN</span>
              <h3>Chế độ hiển thị</h3>
            </div>
            <span class="appearance-current-label" id="appearanceCurrentLabel"></span>
          </div>

          <div class="appearance-mode-grid">
            ${modeCards}
          </div>
        </section>

        <section class="settings-card appearance-card">
          <div class="settings-section-heading">
            <div>
              <span class="section-kicker">MÀU CHỦ ĐẠO</span>
              <h3>Chọn màu giao diện</h3>
            </div>
          </div>

          <div class="accent-choice-grid">
            ${accentButtons}
          </div>

          <div class="appearance-preview">
            <div class="appearance-preview-toolbar">
              <span></span><span></span><span></span>
            </div>
            <div class="appearance-preview-body">
              <span class="appearance-preview-mark">◈</span>
              <div>
                <b>lqlq Browser</b>
                <small>Giao diện xem trước theo lựa chọn hiện tại</small>
              </div>
              <button type="button">Đang bật</button>
            </div>
          </div>
        </section>

        <section class="settings-card">
          <span class="section-kicker">QUYỀN RIÊNG TƯ</span>
          <h3>Dữ liệu duyệt web</h3>

          <div class="settings-row">
            <div>
              <b>Bộ lọc quảng cáo</b>
              <small>Luôn hoạt động trên mọi giao diện</small>
            </div>
            <span class="settings-on">Bật</span>
          </div>

          <div class="settings-row">
            <div>
              <b>Thanh địa chỉ</b>
              <small>Vị trí mặc định trên điện thoại</small>
            </div>
            <span>Phía trên</span>
          </div>

          <button
            class="drawer-danger-button"
            id="settingsClearDataButton"
          >
            Xóa dữ liệu hiện tại
          </button>
        </section>
      </div>
    `;
  }

  function currentModeName() {
    const chosen = modes.find(mode => mode.id === appearance.mode);
    const resolved = resolveMode();

    if (appearance.mode === "system") {
      return `${chosen?.name || "Theo hệ thống"} · ${
        resolved === "dark" ? "đang tối" : "đang sáng"
      }`;
    }

    return chosen?.name || "Sáng";
  }

  function refreshSettingsSelection() {
    document.querySelectorAll("[data-appearance-mode]").forEach(button => {
      button.classList.toggle(
        "selected",
        button.dataset.appearanceMode === appearance.mode
      );
    });

    document.querySelectorAll("[data-accent-choice]").forEach(button => {
      button.classList.toggle(
        "selected",
        button.dataset.accentChoice === appearance.accent
      );
    });

    const label = document.getElementById("appearanceCurrentLabel");
    if (label) {
      label.textContent = currentModeName();
    }
  }

  function bindSettingsControls() {
    document.querySelectorAll("[data-appearance-mode]").forEach(button => {
      button.addEventListener("click", () => {
        appearance.mode = button.dataset.appearanceMode;
        saveAppearance();
        applyAppearance();
        refreshSettingsSelection();
      });
    });

    document.querySelectorAll("[data-accent-choice]").forEach(button => {
      button.addEventListener("click", () => {
        appearance.accent = button.dataset.accentChoice;
        saveAppearance();
        applyAppearance();
        refreshSettingsSelection();
      });
    });

    document.getElementById("settingsClearDataButton")
      ?.addEventListener("click", clearData);

    refreshSettingsSelection();
  }

  const originalOpenDrawer = openDrawer;

  openDrawer = function openDrawerV09(type) {
    if (type !== "settings") {
      return originalOpenDrawer(type);
    }

    els.drawer.classList.add("open");
    els.drawerTitle.textContent = "Cài đặt";
    els.drawerContent.innerHTML = settingsMarkup();
    bindSettingsControls();
  };

  if (systemDarkQuery) {
    const onSystemThemeChanged = () => {
      if (appearance.mode === "system") {
        applyAppearance();
        refreshSettingsSelection();
      }
    };

    if (typeof systemDarkQuery.addEventListener === "function") {
      systemDarkQuery.addEventListener("change", onSystemThemeChanged);
    } else if (typeof systemDarkQuery.addListener === "function") {
      systemDarkQuery.addListener(onSystemThemeChanged);
    }
  }

  applyAppearance();
})();