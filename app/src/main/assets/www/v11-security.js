(() => {
  const SECURITY_KEY = "shieldSecurityV11";

  const permissionDefinitions = [
    {
      id: "camera",
      icon: "◉",
      name: "Camera",
      description: "Cho phép website dùng camera thiết bị"
    },
    {
      id: "microphone",
      icon: "◌",
      name: "Micro",
      description: "Cho phép website thu âm"
    },
    {
      id: "location",
      icon: "⌖",
      name: "Vị trí",
      description: "Chia sẻ vị trí gần đúng hoặc chính xác"
    },
    {
      id: "notifications",
      icon: "◇",
      name: "Thông báo",
      description: "Gửi thông báo ngoài trang web"
    },
    {
      id: "autoplay",
      icon: "▶",
      name: "Tự động phát",
      description: "Video hoặc âm thanh tự chạy"
    },
    {
      id: "clipboard",
      icon: "▤",
      name: "Clipboard",
      description: "Đọc nội dung đã sao chép"
    },
    {
      id: "externalApps",
      icon: "↗",
      name: "Mở ứng dụng ngoài",
      description: "Mở app, cửa hàng hoặc scheme khác"
    },
    {
      id: "downloads",
      icon: "⇩",
      name: "Nhiều lượt tải",
      description: "Website tạo nhiều tệp liên tục"
    }
  ];

  const levelNames = {
    standard: "Tiêu chuẩn",
    strong: "Mạnh",
    maximum: "Tối đa"
  };

  const defaultState = {
    level: "strong",
    safeBrowsing: true,
    httpsOnly: true,
    blockTrackers: true,
    blockThirdPartyCookies: true,
    blockExternalApps: true,
    blockExecutableDownloads: true,
    checkMimeMismatch: true,
    warnCrossDomainDownload: true,
    blockMultipleDownloads: true,
    offlineIsolationEnabled: true,
    trustedSites: [],
    sitePermissions: {},
    lastScanAt: 0
  };

  function readSecurityState() {
    try {
      return {
        ...defaultState,
        ...JSON.parse(localStorage.getItem(SECURITY_KEY) || "{}")
      };
    } catch {
      return { ...defaultState };
    }
  }

  let securityState = readSecurityState();

  const refs = {
    button: document.getElementById("securityCenterBtn"),
    overlay: document.getElementById("securityCenterOverlay"),
    close: document.getElementById("closeSecurityCenter"),
    siteIcon: document.getElementById("securitySiteIcon"),
    siteTitle: document.getElementById("securitySiteTitle"),
    siteSubtitle: document.getElementById("securitySiteSubtitle"),
    levelBadge: document.getElementById("securityLevelBadge"),
    mainStatus: document.getElementById("securityMainStatus"),
    mainTitle: document.getElementById("securityMainTitle"),
    mainDescription: document.getElementById("securityMainDescription"),
    httpsStatusIcon: document.getElementById("httpsStatusIcon"),
    httpsStatusText: document.getElementById("httpsStatusText"),
    trackerStatusText: document.getElementById("trackerStatusText"),
    externalAppStatusText: document.getElementById("externalAppStatusText"),
    permissionList: document.getElementById("permissionList"),
    resetPermissions: document.getElementById("resetSitePermissions"),
    openWarp: document.getElementById("securityOpenWarp"),
    checkWarp: document.getElementById("securityCheckWarp"),
    scan: document.getElementById("scanCurrentSite"),
    trust: document.getElementById("toggleTrustedSite"),
    clearSite: document.getElementById("clearCurrentSiteData"),
    blockExecutable: document.getElementById("blockExecutableDownloads"),
    mimeMismatch: document.getElementById("checkMimeMismatch"),
    crossDomain: document.getElementById("warnCrossDomainDownload"),
    multipleDownloads: document.getElementById("blockMultipleDownloads"),
    offlineIsolation: document.getElementById("offlineIsolationEnabled")
  };

  function saveSecurityState() {
    localStorage.setItem(SECURITY_KEY, JSON.stringify(securityState));
  }

  function currentUrl() {
    return activeTab()?.url || "https://google.com";
  }

  function currentHost() {
    try {
      return new URL(currentUrl()).hostname.replace(/^www\./, "") || "Trang hiện tại";
    } catch {
      return "Trang hiện tại";
    }
  }

  function currentOrigin() {
    try {
      return new URL(currentUrl()).origin;
    } catch {
      return currentUrl();
    }
  }

  function isHttps() {
    try {
      return new URL(currentUrl()).protocol === "https:";
    } catch {
      return false;
    }
  }

  function isTrustedSite() {
    return securityState.trustedSites.includes(currentHost());
  }

  function permissionDefaults(level = securityState.level) {
    if (level === "standard") {
      return {
        camera: "ask",
        microphone: "ask",
        location: "ask",
        notifications: "block",
        autoplay: "ask",
        clipboard: "ask",
        externalApps: "ask",
        downloads: "ask"
      };
    }

    if (level === "maximum") {
      return {
        camera: "block",
        microphone: "block",
        location: "block",
        notifications: "block",
        autoplay: "block",
        clipboard: "block",
        externalApps: "block",
        downloads: "block"
      };
    }

    return {
      camera: "block",
      microphone: "block",
      location: "block",
      notifications: "block",
      autoplay: "ask",
      clipboard: "ask",
      externalApps: "block",
      downloads: "ask"
    };
  }

  function sitePermissions() {
    const host = currentHost();

    if (!securityState.sitePermissions[host]) {
      securityState.sitePermissions[host] = permissionDefaults();
      saveSecurityState();
    }

    return securityState.sitePermissions[host];
  }

  function applyLevelPreset(level) {
    securityState.level = level;

    if (level === "standard") {
      securityState.httpsOnly = false;
      securityState.blockTrackers = true;
      securityState.blockThirdPartyCookies = false;
      securityState.blockExternalApps = true;
      securityState.blockExecutableDownloads = true;
      securityState.checkMimeMismatch = true;
      securityState.warnCrossDomainDownload = true;
      securityState.blockMultipleDownloads = true;
    } else if (level === "maximum") {
      securityState.httpsOnly = true;
      securityState.blockTrackers = true;
      securityState.blockThirdPartyCookies = true;
      securityState.blockExternalApps = true;
      securityState.blockExecutableDownloads = true;
      securityState.checkMimeMismatch = true;
      securityState.warnCrossDomainDownload = true;
      securityState.blockMultipleDownloads = true;
    } else {
      securityState.httpsOnly = true;
      securityState.blockTrackers = true;
      securityState.blockThirdPartyCookies = true;
      securityState.blockExternalApps = true;
      securityState.blockExecutableDownloads = true;
      securityState.checkMimeMismatch = true;
      securityState.warnCrossDomainDownload = true;
      securityState.blockMultipleDownloads = true;
    }

    securityState.sitePermissions[currentHost()] = permissionDefaults(level);
    saveSecurityState();
    renderSecurityCenter();
    toast(`Đã chọn mức bảo vệ ${levelNames[level]}.`);
  }

  function suspiciousUrlScore(url) {
    const value = String(url || "").toLowerCase();
    let score = 0;

    if (!value.startsWith("https://")) score += 2;
    if (/(login|verify|secure|account|wallet|bank).*(free|bonus|gift)/i.test(value)) score += 2;
    if (/(bit\.ly|tinyurl|t\.co|cutt\.ly|shorturl)/i.test(value)) score += 1;
    if (/@/.test(value)) score += 2;
    if (/https?:\/\/\d{1,3}(?:\.\d{1,3}){3}/i.test(value)) score += 1;
    if (/\.(zip|mov|click|top|xyz)(?:\/|$)/i.test(value)) score += 1;

    return score;
  }

  function scanCurrentPage() {
    const score = suspiciousUrlScore(currentUrl());
    const blocked = Number(activeTab()?.blockedCount || 0);
    const suspiciousElements = document.querySelectorAll(
      "[data-lqlq-ad-blocked='true'], iframe[src*='ad' i], a[href*='redirect' i]"
    ).length;

    securityState.lastScanAt = Date.now();
    saveSecurityState();

    if (score >= 3) {
      refs.mainStatus.className = "security-main-status danger";
      refs.mainStatus.querySelector(".security-main-icon").textContent = "!";
      refs.mainTitle.textContent = "Địa chỉ có một số dấu hiệu đáng ngờ";
      refs.mainDescription.textContent =
        "Prototype đã phát hiện mẫu URL đáng chú ý. APK sẽ dùng Safe Browsing để kiểm tra chính xác hơn.";
    } else if (!isHttps()) {
      refs.mainStatus.className = "security-main-status warning";
      refs.mainStatus.querySelector(".security-main-icon").textContent = "!";
      refs.mainTitle.textContent = "Kết nối chưa được mã hóa";
      refs.mainDescription.textContent =
        "Trang đang dùng HTTP. Mức Mạnh và Tối đa sẽ thử nâng cấp lên HTTPS.";
    } else {
      refs.mainStatus.className = "security-main-status";
      refs.mainStatus.querySelector(".security-main-icon").textContent = "✓";
      refs.mainTitle.textContent = "Chưa phát hiện dấu hiệu bất thường";
      refs.mainDescription.textContent =
        `Kiểm tra cục bộ hoàn tất · ${blocked + suspiciousElements} nội dung đã được xử lý.`;
    }

    toast("Đã quét lại trang hiện tại.");
  }

  function renderPermissions() {
    const permissions = sitePermissions();
    refs.permissionList.innerHTML = "";

    permissionDefinitions.forEach(definition => {
      const row = document.createElement("article");
      row.className = "permission-row";

      const icon = document.createElement("span");
      icon.className = "permission-icon";
      icon.textContent = definition.icon;

      const copy = document.createElement("div");
      copy.className = "permission-copy";
      copy.innerHTML = `
        <b>${definition.name}</b>
        <small>${definition.description}</small>
      `;

      const select = document.createElement("select");
      select.dataset.permissionId = definition.id;
      select.innerHTML = `
        <option value="block">Chặn</option>
        <option value="ask">Hỏi trước</option>
        <option value="allow">Cho phép</option>
      `;
      select.value = permissions[definition.id] || "block";

      select.addEventListener("change", () => {
        permissions[definition.id] = select.value;
        saveSecurityState();
        toast(`Đã cập nhật quyền ${definition.name}.`);
      });

      row.append(icon, copy, select);
      refs.permissionList.appendChild(row);
    });
  }

  function renderSecurityCenter() {
    const host = currentHost();
    const https = isHttps();
    const trusted = isTrustedSite();

    refs.siteIcon.textContent = host.charAt(0).toUpperCase();
    refs.siteTitle.textContent = host;
    refs.siteSubtitle.textContent = trusted
      ? "Website đã được đánh dấu tin cậy"
      : `${https ? "HTTPS" : "HTTP"} · Chưa cấp quyền nhạy cảm`;

    refs.levelBadge.textContent = levelNames[securityState.level];

    document.querySelectorAll("[data-security-level]").forEach(button => {
      button.classList.toggle(
        "selected",
        button.dataset.securityLevel === securityState.level
      );
    });

    refs.httpsStatusIcon.textContent = https ? "✓" : "!";
    refs.httpsStatusIcon.className =
      "security-check-icon " + (https ? "good" : "bad");
    refs.httpsStatusText.textContent = https
      ? "Đang dùng kết nối mã hóa"
      : securityState.httpsOnly
        ? "HTTP sẽ được nâng cấp hoặc chặn"
        : "Trang đang dùng kết nối không mã hóa";

    refs.trackerStatusText.textContent = securityState.blockThirdPartyCookies
      ? "Tracker và cookie bên thứ ba đang bị chặn"
      : "Đang chặn tracker phổ biến";

    refs.externalAppStatusText.textContent = securityState.blockExternalApps
      ? "Scheme và ứng dụng ngoài đang bị chặn"
      : "Sẽ hỏi trước khi mở ứng dụng ngoài";

    refs.trust.textContent = trusted
      ? "Bỏ tin cậy website này"
      : "Tin cậy website này";

    refs.blockExecutable.checked = securityState.blockExecutableDownloads;
    refs.mimeMismatch.checked = securityState.checkMimeMismatch;
    refs.crossDomain.checked = securityState.warnCrossDomainDownload;
    refs.multipleDownloads.checked = securityState.blockMultipleDownloads;
    refs.offlineIsolation.checked = securityState.offlineIsolationEnabled;

    renderPermissions();
    scanCurrentPage();
  }

  function openSecurityCenter() {
    closeMenus();
    refs.overlay.classList.remove("hidden");

    document.querySelectorAll("[data-security-tab]").forEach(button => {
      button.classList.toggle(
        "active",
        button.dataset.securityTab === "overview"
      );
    });

    document.querySelectorAll("[data-security-panel]").forEach(panel => {
      panel.classList.toggle(
        "active",
        panel.dataset.securityPanel === "overview"
      );
    });

    renderSecurityCenter();
  }

  function closeSecurityCenter() {
    refs.overlay.classList.add("hidden");
  }

  function clearSiteData() {
    const host = currentHost();
    const profile = currentProfile();

    if (!confirm(`Xóa lịch sử, dấu trang và quyền của ${host}?`)) {
      return;
    }

    const belongsToHost = item => {
      try {
        return new URL(item.url || item.sourceUrl || "").hostname
          .replace(/^www\./, "") === host;
      } catch {
        return false;
      }
    };

    profile.history = profile.history.filter(item => !belongsToHost(item));
    profile.bookmarks = profile.bookmarks.filter(item => !belongsToHost(item));
    profile.offlinePages = profile.offlinePages.filter(item => !belongsToHost(item));
    delete securityState.sitePermissions[host];
    securityState.trustedSites =
      securityState.trustedSites.filter(item => item !== host);

    saveState();
    saveSecurityState();
    renderSecurityCenter();
    toast("Đã xóa dữ liệu website hiện tại.");
  }

  function toggleTrustedSite() {
    const host = currentHost();

    if (isTrustedSite()) {
      securityState.trustedSites =
        securityState.trustedSites.filter(item => item !== host);
      toast("Đã bỏ website khỏi danh sách tin cậy.");
    } else {
      securityState.trustedSites.push(host);
      toast("Đã thêm website vào danh sách tin cậy.");
    }

    saveSecurityState();
    renderSecurityCenter();
  }

  function resetPermissions() {
    securityState.sitePermissions[currentHost()] =
      permissionDefaults(securityState.level);
    saveSecurityState();
    renderPermissions();
    toast("Đã đặt lại quyền website.");
  }

  function dangerousExtension(url) {
    const clean = String(url || "")
      .split(/[?#]/)[0]
      .toLowerCase();

    const blocked = [
      ".apk", ".xapk", ".apks", ".exe", ".msi", ".bat", ".cmd",
      ".scr", ".jar", ".dex", ".sh", ".bin", ".com", ".ps1"
    ];

    return blocked.find(extension => clean.endsWith(extension)) || "";
  }

  function hasDoubleExtension(url) {
    const clean = String(url || "")
      .split(/[?#]/)[0]
      .toLowerCase();

    return /\.(txt|pdf|jpg|jpeg|png|mp3|mp4|html?)\.(apk|exe|scr|bat|cmd|jar)$/i
      .test(clean);
  }

  function installDownloadShield() {
    document.addEventListener("click", event => {
      const anchor = event.target instanceof Element
        ? event.target.closest("a[href]")
        : null;

      if (!anchor) return;

      const href = anchor.href || anchor.getAttribute("href") || "";
      const extension = dangerousExtension(href);

      if (
        securityState.blockExecutableDownloads
        && (extension || hasDoubleExtension(href))
      ) {
        event.preventDefault();
        event.stopImmediatePropagation();
        toast(
          extension
            ? `Shield đã chặn tệp nguy hiểm ${extension}.`
            : "Shield đã chặn tệp có tên phần mở rộng giả."
        );
        return;
      }

      if (
        securityState.warnCrossDomainDownload
        && anchor.hasAttribute("download")
      ) {
        try {
          const downloadHost = new URL(href, location.href).hostname;
          const pageHost = new URL(currentUrl()).hostname;

          if (
            downloadHost
            && pageHost
            && downloadHost !== pageHost
            && !confirm("Tệp đến từ tên miền khác. Tiếp tục tải?")
          ) {
            event.preventDefault();
            event.stopImmediatePropagation();
          }
        } catch {}
      }
    }, true);
  }

  const originalNormalizeUrl = normalizeUrl;

  normalizeUrl = function normalizeSecureUrl(input) {
    const raw = String(input || "").trim();

    if (/^(intent|file|content|market|javascript|data):/i.test(raw)) {
      toast("Shield đã chặn loại địa chỉ không an toàn.");
      return activeTab()?.url || "https://google.com";
    }

    let normalized = originalNormalizeUrl(raw);

    if (
      securityState.httpsOnly
      && normalized.startsWith("http://")
      && !isTrustedSite()
    ) {
      normalized = "https://" + normalized.slice("http://".length);
      toast("Shield đã nâng cấp kết nối lên HTTPS.");
    }

    return normalized;
  };

  const originalBuildOfflineHtmlSnapshot = buildOfflineHtmlSnapshot;

  buildOfflineHtmlSnapshot = function buildSecureOfflineSnapshot() {
    let html = originalBuildOfflineHtmlSnapshot();

    if (!securityState.offlineIsolationEnabled) {
      return html;
    }

    const csp = `
  <meta http-equiv="Content-Security-Policy"
        content="default-src 'none'; img-src data: blob:; media-src data: blob:;
                 style-src 'unsafe-inline'; font-src data:; frame-src 'none';
                 connect-src 'none'; script-src 'none'; object-src 'none';
                 form-action 'none'; base-uri 'none';">`;

    const banner = `
  <div style="
    position:sticky;top:0;z-index:9999;
    display:flex;align-items:center;gap:10px;
    margin:-20px -20px 18px;padding:12px 18px;
    background:#e6f7ed;color:#126d39;
    border-bottom:1px solid #cde8d7;
    font:700 13px Arial,sans-serif">
    <span>🛡</span>
    <span>Ngoại tuyến an toàn — mạng và script đã bị khóa</span>
  </div>`;

    html = html.replace("<head>", "<head>" + csp);
    html = html.replace("<body>", "<body>" + banner);
    return html;
  };

  const originalHandleAction = handleAction;

  handleAction = function handleSecurityAction(action) {
    if (action === "security-center") {
      openSecurityCenter();
      return;
    }

    originalHandleAction(action);
  };

  refs.button.addEventListener("click", openSecurityCenter);
  refs.close.addEventListener("click", closeSecurityCenter);

  refs.overlay.addEventListener("click", event => {
    if (event.target === refs.overlay) {
      closeSecurityCenter();
    }
  });

  document.querySelectorAll("[data-security-tab]").forEach(button => {
    button.addEventListener("click", () => {
      const tab = button.dataset.securityTab;

      document.querySelectorAll("[data-security-tab]").forEach(item => {
        item.classList.toggle("active", item === button);
      });

      document.querySelectorAll("[data-security-panel]").forEach(panel => {
        panel.classList.toggle(
          "active",
          panel.dataset.securityPanel === tab
        );
      });
    });
  });

  document.querySelectorAll("[data-security-level]").forEach(button => {
    button.addEventListener("click", () => {
      applyLevelPreset(button.dataset.securityLevel);
    });
  });

  refs.resetPermissions.addEventListener("click", resetPermissions);
  refs.scan.addEventListener("click", scanCurrentPage);
  refs.trust.addEventListener("click", toggleTrustedSite);
  refs.clearSite.addEventListener("click", clearSiteData);

  refs.openWarp.addEventListener("click", () => {
    closeSecurityCenter();
    if (typeof openWarpModal === "function") {
      openWarpModal();
    }
  });

  refs.checkWarp.addEventListener("click", () => {
    closeSecurityCenter();
    if (typeof checkWarpOfficialConnection === "function") {
      checkWarpOfficialConnection();
    }
  });

  refs.blockExecutable.addEventListener("change", () => {
    securityState.blockExecutableDownloads = refs.blockExecutable.checked;
    saveSecurityState();
  });

  refs.mimeMismatch.addEventListener("change", () => {
    securityState.checkMimeMismatch = refs.mimeMismatch.checked;
    saveSecurityState();
  });

  refs.crossDomain.addEventListener("change", () => {
    securityState.warnCrossDomainDownload = refs.crossDomain.checked;
    saveSecurityState();
  });

  refs.multipleDownloads.addEventListener("change", () => {
    securityState.blockMultipleDownloads = refs.multipleDownloads.checked;
    saveSecurityState();
  });

  refs.offlineIsolation.addEventListener("change", () => {
    securityState.offlineIsolationEnabled = refs.offlineIsolation.checked;
    saveSecurityState();
    toast(
      refs.offlineIsolation.checked
        ? "Đã bật cách ly nội dung ngoại tuyến."
        : "Đã tắt cách ly nội dung ngoại tuyến."
    );
  });

  document.querySelectorAll(
    "[data-security-panel='offline'] [data-action]"
  ).forEach(button => {
    button.addEventListener("click", () => {
      closeSecurityCenter();
      handleAction(button.dataset.action);
    });
  });

  installDownloadShield();

  securityState.sitePermissions ||= {};
  securityState.trustedSites ||= [];
  saveSecurityState();
})();