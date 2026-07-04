(() => {
  "use strict";

  const shell = document.getElementById("imageEditorShell");
  const singlePane = document.querySelector(
    '[data-image-editor-pane="single"]'
  );
  const workspace = singlePane?.querySelector(".single-workspace");
  const tools = workspace?.querySelector(".image-editor-sidebar");
  const main = workspace?.querySelector(".single-main");
  const stage = document.getElementById("singleCanvasStage");
  const canvasWrap = document.getElementById("singleCanvasWrap");
  const title = document.getElementById("singleImageTitle");
  const meta = document.getElementById("singleImageMeta");
  const singleInput = document.getElementById("singleImageInput");
  const cropActions = document.getElementById("singleCropActions");
  const startCrop = document.getElementById("singleStartCrop");
  const originalApplyCrop = document.getElementById("singleApplyCrop");
  const originalCancelCrop = document.getElementById("singleCancelCrop");
  const saveButton = document.getElementById("singleSaveButton");

  if (!shell || !singlePane || !workspace || !tools || !main || !stage) {
    console.warn("lqlq mobile image tools: thiếu phần tử giao diện.");
    return;
  }

  // Keep branding consistent.
  document
    .querySelectorAll(".image-editor-heading h2")
    .forEach(element => {
      if (/Shield Image Studio/i.test(element.textContent)) {
        element.textContent = "lqlq Image Studio";
      }
    });

  tools.id = "singleEditorTools";
  tools.classList.add("single-editor-tools");
  tools.setAttribute("aria-hidden", "true");

  const cards = Array.from(tools.children)
    .filter(element => element.classList.contains("image-tool-card"));

  const toolNames = ["history", "transform", "crop", "slice", "save"];
  cards.forEach((card, index) => {
    card.dataset.mobileImageTool = toolNames[index] || `tool-${index}`;
  });

  const sheetHeader = document.createElement("header");
  sheetHeader.className = "mobile-image-tool-sheet-header";
  sheetHeader.innerHTML = `
    <div>
      <span class="section-kicker">CÔNG CỤ CHỈNH ẢNH</span>
      <b id="mobileImageToolTitle">Lịch sử chỉnh sửa</b>
    </div>
    <button
      type="button"
      id="mobileImageToolClose"
      aria-label="Đóng công cụ"
    >×</button>
  `;
  tools.prepend(sheetHeader);

  const backdrop = document.createElement("div");
  backdrop.className = "mobile-image-tool-backdrop hidden";
  backdrop.id = "mobileImageToolBackdrop";
  workspace.insertBefore(backdrop, tools);

  const canvasHeader = document.createElement("div");
  canvasHeader.className = "mobile-image-canvas-header";
  canvasHeader.innerHTML = `
    <div>
      <b id="mobileImageCanvasTitle">Chưa mở ảnh</b>
      <small id="mobileImageCanvasMeta">Chọn ảnh để bắt đầu</small>
    </div>
    <button type="button" id="mobileImageReplaceButton">Đổi ảnh</button>
  `;
  main.insertBefore(canvasHeader, stage);

  const toolbar = document.createElement("nav");
  toolbar.className = "mobile-image-tools-bar";
  toolbar.id = "mobileImageToolsBar";
  toolbar.setAttribute("aria-label", "Công cụ chỉnh ảnh trên điện thoại");
  toolbar.innerHTML = `
    <button type="button" data-mobile-image-action="open">
      <span>◇</span><small>Ảnh</small>
    </button>
    <button type="button" data-mobile-image-action="history">
      <span>↶</span><small>Lịch sử</small>
    </button>
    <button type="button" data-mobile-image-action="transform">
      <span>↔</span><small>Biến đổi</small>
    </button>
    <button type="button" data-mobile-image-action="crop">
      <span>⌗</span><small>Cắt</small>
    </button>
    <button type="button" data-mobile-image-action="slice">
      <span>▦</span><small>Chia</small>
    </button>
    <button type="button" data-mobile-image-action="save">
      <span>⇩</span><small>Lưu</small>
    </button>
  `;
  singlePane.appendChild(toolbar);

  const cropBar = document.createElement("div");
  cropBar.className = "mobile-image-crop-bar hidden";
  cropBar.id = "mobileImageCropBar";
  cropBar.innerHTML = `
    <button type="button" id="mobileImageCancelCrop">Hủy</button>
    <div>
      <b>Đang chọn vùng cắt</b>
      <small>Kéo các điểm trực tiếp trên ảnh</small>
    </div>
    <button
      type="button"
      class="primary"
      id="mobileImageApplyCrop"
    >Áp dụng</button>
  `;
  singlePane.appendChild(cropBar);

  const refs = {
    backdrop,
    toolbar,
    cropBar,
    toolTitle: sheetHeader.querySelector("#mobileImageToolTitle"),
    closeTool: sheetHeader.querySelector("#mobileImageToolClose"),
    mobileTitle: canvasHeader.querySelector("#mobileImageCanvasTitle"),
    mobileMeta: canvasHeader.querySelector("#mobileImageCanvasMeta"),
    replaceImage: canvasHeader.querySelector("#mobileImageReplaceButton"),
    cropApply: cropBar.querySelector("#mobileImageApplyCrop"),
    cropCancel: cropBar.querySelector("#mobileImageCancelCrop")
  };

  const toolTitles = {
    history: "Lịch sử chỉnh sửa",
    transform: "Kích thước, xoay và lật",
    crop: "Cắt ảnh",
    slice: "Chia ảnh",
    save: "Lưu ảnh"
  };

  function isMobile() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function switchToSingleTab() {
    const button = document.querySelector(
      '[data-image-editor-tab="single"]'
    );

    if (button && !button.classList.contains("active")) {
      button.click();
    }
  }

  function closeToolSheet() {
    tools.classList.remove("mobile-open");
    refs.backdrop.classList.add("hidden");
    tools.setAttribute("aria-hidden", "true");

    toolbar
      .querySelectorAll("[data-mobile-image-action]")
      .forEach(button => button.classList.remove("active"));
  }

  function openToolSheet(tool) {
    if (!isMobile()) return;

    switchToSingleTab();

    const card = tools.querySelector(
      `[data-mobile-image-tool="${tool}"]`
    );

    if (!card) return;

    cards.forEach(item => {
      item.classList.toggle("mobile-tool-active", item === card);
    });

    refs.toolTitle.textContent = toolTitles[tool] || "Công cụ";
    tools.classList.add("mobile-open");
    refs.backdrop.classList.remove("hidden");
    tools.setAttribute("aria-hidden", "false");

    toolbar
      .querySelectorAll("[data-mobile-image-action]")
      .forEach(button => {
        button.classList.toggle(
          "active",
          button.dataset.mobileImageAction === tool
        );
      });
  }

  function syncImageState() {
    const hasImage =
      canvasWrap && !canvasWrap.classList.contains("hidden");

    shell.classList.toggle("has-single-image", Boolean(hasImage));

    refs.mobileTitle.textContent = hasImage
      ? title.textContent
      : "Chưa mở ảnh";

    refs.mobileMeta.textContent = hasImage
      ? meta.textContent
      : "Chọn ảnh để bắt đầu";
  }

  function syncCropState() {
    const active =
      cropActions && !cropActions.classList.contains("hidden");

    shell.classList.toggle("mobile-crop-active", Boolean(active));
    refs.cropBar.classList.toggle("hidden", !active);

    if (active) {
      closeToolSheet();
    }
  }

  function openImagePicker() {
    switchToSingleTab();
    closeToolSheet();
    singleInput?.click();
  }

  toolbar
    .querySelectorAll("[data-mobile-image-action]")
    .forEach(button => {
      button.addEventListener("click", () => {
        const action = button.dataset.mobileImageAction;

        if (action === "open") {
          openImagePicker();
          return;
        }

        openToolSheet(action);
      });
    });

  refs.closeTool.addEventListener("click", closeToolSheet);
  refs.backdrop.addEventListener("click", closeToolSheet);
  refs.replaceImage.addEventListener("click", openImagePicker);

  startCrop?.addEventListener("click", () => {
    setTimeout(syncCropState, 0);
  });

  refs.cropApply.addEventListener("click", () => {
    originalApplyCrop?.click();
    setTimeout(syncCropState, 0);
  });

  refs.cropCancel.addEventListener("click", () => {
    originalCancelCrop?.click();
    setTimeout(syncCropState, 0);
  });

  saveButton?.addEventListener("click", () => {
    if (isMobile()) {
      setTimeout(closeToolSheet, 100);
    }
  });

  if (canvasWrap) {
    new MutationObserver(syncImageState).observe(canvasWrap, {
      attributes: true,
      attributeFilter: ["class"]
    });
  }

  if (cropActions) {
    new MutationObserver(syncCropState).observe(cropActions, {
      attributes: true,
      attributeFilter: ["class"]
    });
  }

  [title, meta].forEach(element => {
    if (!element) return;

    new MutationObserver(syncImageState).observe(element, {
      childList: true,
      characterData: true,
      subtree: true
    });
  });

  // On phones, the editor opens directly in “Chỉnh một ảnh”.
  const previousHandleAction = handleAction;
  handleAction = function handleMobileImageEditorAction(action) {
    if (action === "image-editor" && isMobile()) {
      closeMenus();
      window.ShieldImageStudio?.open("single");
      return;
    }

    previousHandleAction(action);
  };

  function syncActiveEditorTab() {
    const singleActive = singlePane.classList.contains("active");
    shell.classList.toggle(
      "mobile-single-active",
      isMobile() && singleActive
    );

    if (!singleActive) {
      closeToolSheet();
    }

    tools.setAttribute(
      "aria-hidden",
      isMobile()
        ? String(!tools.classList.contains("mobile-open"))
        : "false"
    );
  }

  document
    .querySelectorAll("[data-image-editor-tab]")
    .forEach(button => {
      button.addEventListener("click", () => {
        setTimeout(syncActiveEditorTab, 0);
      });
    });

  new MutationObserver(syncActiveEditorTab).observe(singlePane, {
    attributes: true,
    attributeFilter: ["class"]
  });

  window.addEventListener("resize", () => {
    if (!isMobile()) {
      closeToolSheet();
      shell.classList.remove("mobile-crop-active");
      refs.cropBar.classList.add("hidden");
    }

    syncActiveEditorTab();
    syncImageState();
    syncCropState();
  });

  document.addEventListener("keydown", event => {
    if (
      event.key === "Escape"
      && tools.classList.contains("mobile-open")
    ) {
      event.stopImmediatePropagation();
      closeToolSheet();
    }
  }, true);

  syncActiveEditorTab();
  syncImageState();
  syncCropState();

  window.LqlqMobileImageTools = {
    open: openToolSheet,
    close: closeToolSheet,
    sync: syncImageState
  };
})();