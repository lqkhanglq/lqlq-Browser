(() => {
  "use strict";

  const shell = document.getElementById("imageEditorShell");
  const batchPane = document.querySelector(
    '[data-image-editor-pane="batch"]'
  );
  const workspace = batchPane?.querySelector(".batch-workspace");
  const tools = workspace?.querySelector(".image-editor-sidebar");
  const main = workspace?.querySelector(".image-editor-main");
  const grid = document.getElementById("batchImageGrid");
  const count = document.getElementById("batchImageCount");
  const summary = document.getElementById("batchSelectionSummary");
  const batchInput = document.getElementById("batchImageInput");
  const batchSelectAll = document.getElementById("batchSelectAll");
  const batchSelectNone = document.getElementById("batchSelectNone");
  const batchApplyScale = document.getElementById("batchApplyScale");
  const batchRotate = document.getElementById("batchRotateButton");
  const batchFlipHorizontal = document.getElementById(
    "batchFlipHorizontal"
  );
  const batchFlipVertical = document.getElementById(
    "batchFlipVertical"
  );
  const batchReset = document.getElementById("batchResetTransforms");
  const batchDownload = document.getElementById("batchDownloadZip");
  const batchRemove = document.getElementById("batchRemoveSelected");
  const batchClear = document.getElementById("batchClearAll");

  if (!shell || !batchPane || !workspace || !tools || !main || !grid) {
    console.warn("lqlq mobile batch tools: thiếu phần tử giao diện.");
    return;
  }

  tools.id = "batchEditorTools";
  tools.classList.add("batch-editor-tools");
  tools.setAttribute("aria-hidden", "true");

  const cards = Array.from(tools.children)
    .filter(element => element.classList.contains("image-tool-card"));

  const toolNames = ["selection", "scale", "transform", "export"];
  cards.forEach((card, index) => {
    card.dataset.mobileBatchTool =
      toolNames[index] || `tool-${index}`;
  });

  const sheetHeader = document.createElement("header");
  sheetHeader.className = "mobile-batch-tool-sheet-header";
  sheetHeader.innerHTML = `
    <div>
      <span class="section-kicker">XỬ LÝ NHIỀU ẢNH</span>
      <b id="mobileBatchToolTitle">Phạm vi áp dụng</b>
    </div>
    <button
      type="button"
      id="mobileBatchToolClose"
      aria-label="Đóng công cụ"
    >×</button>
  `;
  tools.prepend(sheetHeader);

  const backdrop = document.createElement("div");
  backdrop.className = "mobile-batch-tool-backdrop hidden";
  backdrop.id = "mobileBatchToolBackdrop";
  workspace.insertBefore(backdrop, tools);

  const listHeader = document.createElement("div");
  listHeader.className = "mobile-batch-list-header";
  listHeader.innerHTML = `
    <div>
      <b id="mobileBatchCount">0 ảnh</b>
      <small id="mobileBatchSummary">Chưa có ảnh</small>
    </div>
    <button type="button" id="mobileBatchAddButton">
      ＋ Thêm ảnh
    </button>
  `;

  const originalHeading = main.querySelector(
    ".image-editor-section-heading"
  );
  main.insertBefore(listHeader, originalHeading || grid);

  const toolbar = document.createElement("nav");
  toolbar.className = "mobile-batch-tools-bar";
  toolbar.id = "mobileBatchToolsBar";
  toolbar.setAttribute(
    "aria-label",
    "Công cụ xử lý nhiều ảnh trên điện thoại"
  );
  toolbar.innerHTML = `
    <button type="button" data-mobile-batch-action="open">
      <span>▦</span><small>Ảnh</small>
    </button>
    <button type="button" data-mobile-batch-action="selection">
      <span>◎</span><small>Chọn</small>
    </button>
    <button type="button" data-mobile-batch-action="scale">
      <span>↔</span><small>Kích thước</small>
    </button>
    <button type="button" data-mobile-batch-action="transform">
      <span>↻</span><small>Xoay/lật</small>
    </button>
    <button type="button" data-mobile-batch-action="export">
      <span>⇩</span><small>Xuất</small>
    </button>
  `;
  batchPane.appendChild(toolbar);

  const refs = {
    backdrop,
    toolbar,
    toolTitle: sheetHeader.querySelector("#mobileBatchToolTitle"),
    closeTool: sheetHeader.querySelector("#mobileBatchToolClose"),
    mobileCount: listHeader.querySelector("#mobileBatchCount"),
    mobileSummary: listHeader.querySelector("#mobileBatchSummary"),
    addButton: listHeader.querySelector("#mobileBatchAddButton")
  };

  const toolTitles = {
    selection: "Chọn ảnh cần áp dụng",
    scale: "Thay đổi kích thước",
    transform: "Xoay, lật và đặt lại",
    export: "Xuất hoặc xóa ảnh"
  };

  function isMobile() {
    return window.matchMedia("(max-width: 700px)").matches;
  }

  function switchToBatchTab() {
    const button = document.querySelector(
      '[data-image-editor-tab="batch"]'
    );

    if (button && !button.classList.contains("active")) {
      button.click();
    }
  }

  function closeToolSheet() {
    tools.classList.remove("mobile-open");
    refs.backdrop.classList.add("hidden");
    tools.setAttribute("aria-hidden", "true");

    refs.toolbar
      .querySelectorAll("[data-mobile-batch-action]")
      .forEach(button => button.classList.remove("active"));
  }

  function openToolSheet(tool) {
    if (!isMobile()) return;

    switchToBatchTab();

    const card = tools.querySelector(
      `[data-mobile-batch-tool="${tool}"]`
    );

    if (!card) return;

    cards.forEach(item => {
      item.classList.toggle(
        "mobile-batch-tool-active",
        item === card
      );
    });

    refs.toolTitle.textContent = toolTitles[tool] || "Công cụ";
    tools.classList.add("mobile-open");
    refs.backdrop.classList.remove("hidden");
    tools.setAttribute("aria-hidden", "false");

    refs.toolbar
      .querySelectorAll("[data-mobile-batch-action]")
      .forEach(button => {
        button.classList.toggle(
          "active",
          button.dataset.mobileBatchAction === tool
        );
      });
  }

  function openImagePicker() {
    switchToBatchTab();
    closeToolSheet();
    batchInput?.click();
  }

  function syncBatchState() {
    const hasImages = Boolean(
      grid.querySelector(".batch-image-card")
    );

    shell.classList.toggle("has-batch-images", hasImages);

    refs.mobileCount.textContent =
      count?.textContent?.trim() || "0 ảnh";

    refs.mobileSummary.textContent =
      summary?.textContent?.trim() || "Chưa có ảnh";

    refs.addButton.textContent = hasImages
      ? "＋ Thêm ảnh"
      : "＋ Chọn ảnh";
  }

  function syncActiveTab() {
    const batchActive = batchPane.classList.contains("active");

    shell.classList.toggle(
      "mobile-batch-active",
      isMobile() && batchActive
    );

    if (!batchActive) {
      closeToolSheet();
    }

    tools.setAttribute(
      "aria-hidden",
      isMobile()
        ? String(!tools.classList.contains("mobile-open"))
        : "false"
    );
  }

  refs.toolbar
    .querySelectorAll("[data-mobile-batch-action]")
    .forEach(button => {
      button.addEventListener("click", () => {
        const action = button.dataset.mobileBatchAction;

        if (action === "open") {
          openImagePicker();
          return;
        }

        openToolSheet(action);
      });
    });

  refs.closeTool.addEventListener("click", closeToolSheet);
  refs.backdrop.addEventListener("click", closeToolSheet);
  refs.addButton.addEventListener("click", openImagePicker);

  /*
   * Close the sheet after a command so the edited thumbnails are visible
   * immediately, instead of making the user close it manually each time.
   */
  [
    batchSelectAll,
    batchSelectNone,
    batchApplyScale,
    batchRotate,
    batchFlipHorizontal,
    batchFlipVertical,
    batchReset,
    batchDownload,
    batchRemove,
    batchClear
  ].filter(Boolean).forEach(button => {
    button.addEventListener("click", () => {
      if (isMobile()) {
        setTimeout(closeToolSheet, 120);
      }
    });
  });

  new MutationObserver(syncBatchState).observe(grid, {
    childList: true,
    subtree: true
  });

  [count, summary].forEach(element => {
    if (!element) return;

    new MutationObserver(syncBatchState).observe(element, {
      childList: true,
      characterData: true,
      subtree: true
    });
  });

  document
    .querySelectorAll("[data-image-editor-tab]")
    .forEach(button => {
      button.addEventListener("click", () => {
        setTimeout(syncActiveTab, 0);
      });
    });

  new MutationObserver(syncActiveTab).observe(batchPane, {
    attributes: true,
    attributeFilter: ["class"]
  });

  window.addEventListener("resize", () => {
    if (!isMobile()) {
      closeToolSheet();
    }

    syncActiveTab();
    syncBatchState();
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

  syncActiveTab();
  syncBatchState();

  window.LqlqMobileBatchTools = {
    open: openToolSheet,
    close: closeToolSheet,
    sync: syncBatchState
  };
})();