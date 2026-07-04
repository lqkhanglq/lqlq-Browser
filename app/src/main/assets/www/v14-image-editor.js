(() => {
  "use strict";

  const refs = {
    toolbarButton: document.getElementById("imageEditorToolbarBtn"),
    backdrop: document.getElementById("imageEditorBackdrop"),
    shell: document.getElementById("imageEditorShell"),
    close: document.getElementById("closeImageEditor"),
    status: document.getElementById("imageEditorStatus"),

    batchInput: document.getElementById("batchImageInput"),
    batchBrowse: document.getElementById("batchBrowseButton"),
    batchDropZone: document.getElementById("batchImageDropZone"),
    batchGrid: document.getElementById("batchImageGrid"),
    batchCount: document.getElementById("batchImageCount"),
    batchSummary: document.getElementById("batchSelectionSummary"),
    batchSelectAll: document.getElementById("batchSelectAll"),
    batchSelectNone: document.getElementById("batchSelectNone"),
    batchScale: document.getElementById("batchScaleInput"),
    batchApplyScale: document.getElementById("batchApplyScale"),
    batchRotate: document.getElementById("batchRotateInput"),
    batchRotateButton: document.getElementById("batchRotateButton"),
    batchFlipH: document.getElementById("batchFlipHorizontal"),
    batchFlipV: document.getElementById("batchFlipVertical"),
    batchReset: document.getElementById("batchResetTransforms"),
    batchFormat: document.getElementById("batchExportFormat"),
    batchZip: document.getElementById("batchDownloadZip"),
    batchRemove: document.getElementById("batchRemoveSelected"),
    batchClear: document.getElementById("batchClearAll"),

    singleInput: document.getElementById("singleImageInput"),
    singleBrowse: document.getElementById("singleBrowseButton"),
    singleDropZone: document.getElementById("singleImageDropZone"),
    singleDropHint: document.getElementById("singleDropHint"),
    singleTitle: document.getElementById("singleImageTitle"),
    singleMeta: document.getElementById("singleImageMeta"),
    singleFooter: document.getElementById("singleEditorFooterText"),
    singlePlaceholder: document.getElementById("singleCanvasPlaceholder"),
    singleWrap: document.getElementById("singleCanvasWrap"),
    singleCanvas: document.getElementById("singleEditorCanvas"),
    cropCanvas: document.getElementById("singleCropOverlayCanvas"),
    singleUndo: document.getElementById("singleUndoButton"),
    singleRedo: document.getElementById("singleRedoButton"),
    singleReset: document.getElementById("singleResetButton"),
    singleClear: document.getElementById("singleClearButton"),
    singleScale: document.getElementById("singleScaleInput"),
    singleResize: document.getElementById("singleResizeButton"),
    singleRotate: document.getElementById("singleRotateInput"),
    singleRotateButton: document.getElementById("singleRotateButton"),
    singleRotateLeft: document.getElementById("singleRotateLeft"),
    singleFlipH: document.getElementById("singleFlipHorizontal"),
    singleFlipV: document.getElementById("singleFlipVertical"),
    singleStartCrop: document.getElementById("singleStartCrop"),
    cropActions: document.getElementById("singleCropActions"),
    singleApplyCrop: document.getElementById("singleApplyCrop"),
    singleCancelCrop: document.getElementById("singleCancelCrop"),
    rows: document.getElementById("singleRowsInput"),
    cols: document.getElementById("singleColsInput"),
    slice: document.getElementById("singleSliceButton"),
    format: document.getElementById("singleExportFormat"),
    qualityField: document.getElementById("singleQualityField"),
    quality: document.getElementById("singleQualityInput"),
    save: document.getElementById("singleSaveButton"),
    sliceSection: document.getElementById("sliceResultsSection"),
    sliceTitle: document.getElementById("sliceResultTitle"),
    sliceGrid: document.getElementById("sliceResultsGrid"),
    sliceZip: document.getElementById("downloadSlicesZip")
  };

  const batchItems = [];
  const single = {
    canvas: null,
    fileName: "",
    originalDataUrl: "",
    history: [],
    historyIndex: -1,
    cropActive: false,
    cropRect: null,
    pointer: null,
    slices: []
  };

  let nextBatchId = 1;

  const imageContext = refs.singleCanvas.getContext("2d");
  const cropContext = refs.cropCanvas.getContext("2d");

  function setStatus(text, state = "idle") {
    refs.status.textContent = text;
    refs.status.dataset.state = state;
  }

  function notify(text) {
    if (typeof toast === "function") {
      toast(text);
    }
  }

  function openEditor(tab = null) {
    if (typeof closeMenus === "function") {
      closeMenus();
    }

    refs.backdrop.classList.remove("hidden");
    refs.shell.classList.remove("hidden");
    refs.shell.setAttribute("aria-hidden", "false");

    if (tab) {
      switchTab(tab);
    }
  }

  function closeEditor() {
    refs.backdrop.classList.add("hidden");
    refs.shell.classList.add("hidden");
    refs.shell.setAttribute("aria-hidden", "true");
    exitCropMode();
  }

  function switchTab(tab) {
    document.querySelectorAll("[data-image-editor-tab]").forEach(button => {
      button.classList.toggle(
        "active",
        button.dataset.imageEditorTab === tab
      );
    });

    document.querySelectorAll("[data-image-editor-pane]").forEach(pane => {
      pane.classList.toggle(
        "active",
        pane.dataset.imageEditorPane === tab
      );
    });
  }

  function isSupportedImage(file) {
    return file
      && /^image\/(png|jpeg|webp|gif)$/i.test(file.type)
      && file.size <= 50 * 1024 * 1024;
  }

  function basename(name) {
    const value = String(name || "image").replace(/\.[^.]+$/, "");
    return value || "image";
  }

  function extensionForMime(mime) {
    if (mime === "image/jpeg") return "jpg";
    if (mime === "image/webp") return "webp";
    return "png";
  }

  function humanBytes(bytes) {
    const value = Number(bytes || 0);
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }

  function canvasToBlob(canvas, mime = "image/png", quality = 0.92) {
    return new Promise((resolve, reject) => {
      canvas.toBlob(blob => {
        if (blob) {
          resolve(blob);
          return;
        }
        reject(new Error("Không thể tạo dữ liệu ảnh."));
      }, mime, quality);
    });
  }

  function downloadBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.target = "_self";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 3000);
  }

  function cloneCanvas(source) {
    const canvas = document.createElement("canvas");
    canvas.width = source.width;
    canvas.height = source.height;
    canvas.getContext("2d").drawImage(source, 0, 0);
    return canvas;
  }

  async function canvasFromFile(file) {
    if (!isSupportedImage(file)) {
      throw new Error("Chỉ nhận PNG, JPG, WebP hoặc GIF tĩnh dưới 50 MB.");
    }

    const objectUrl = URL.createObjectURL(file);

    try {
      const image = await new Promise((resolve, reject) => {
        const element = new Image();
        element.onload = () => resolve(element);
        element.onerror = () => reject(new Error("Không đọc được ảnh."));
        element.src = objectUrl;
      });

      const canvas = document.createElement("canvas");
      canvas.width = image.naturalWidth || image.width;
      canvas.height = image.naturalHeight || image.height;

      if (!canvas.width || !canvas.height) {
        throw new Error("Ảnh không có kích thước hợp lệ.");
      }

      const pixelCount = canvas.width * canvas.height;
      if (pixelCount > 120_000_000) {
        throw new Error("Ảnh quá lớn để xử lý an toàn trong trình duyệt.");
      }

      canvas.getContext("2d").drawImage(image, 0, 0);
      return canvas;
    } finally {
      URL.revokeObjectURL(objectUrl);
    }
  }

  function transformedDimensions(width, height, scale, angleDegrees) {
    const scaledWidth = Math.max(1, Math.round(width * scale));
    const scaledHeight = Math.max(1, Math.round(height * scale));
    const radians = angleDegrees * Math.PI / 180;
    const sine = Math.abs(Math.sin(radians));
    const cosine = Math.abs(Math.cos(radians));

    return {
      sourceWidth: scaledWidth,
      sourceHeight: scaledHeight,
      width: Math.max(1, Math.ceil(scaledWidth * cosine + scaledHeight * sine)),
      height: Math.max(1, Math.ceil(scaledWidth * sine + scaledHeight * cosine))
    };
  }

  function renderBatchItem(item, preview = false) {
    const dims = transformedDimensions(
      item.source.width,
      item.source.height,
      item.scale,
      item.rotation
    );

    let outputScale = 1;
    if (preview) {
      outputScale = Math.min(
        180 / dims.width,
        135 / dims.height,
        1
      );
    }

    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(dims.width * outputScale));
    canvas.height = Math.max(1, Math.round(dims.height * outputScale));

    const context = canvas.getContext("2d");
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.translate(canvas.width / 2, canvas.height / 2);
    context.rotate(item.rotation * Math.PI / 180);
    context.scale(item.flipH ? -1 : 1, item.flipV ? -1 : 1);
    context.drawImage(
      item.source,
      -dims.sourceWidth * outputScale / 2,
      -dims.sourceHeight * outputScale / 2,
      dims.sourceWidth * outputScale,
      dims.sourceHeight * outputScale
    );

    return canvas;
  }

  async function addBatchFiles(fileList) {
    const files = Array.from(fileList || []).filter(isSupportedImage);

    if (!files.length) {
      notify("Không có ảnh hợp lệ để thêm.");
      return;
    }

    setStatus(`Đang nạp ${files.length} ảnh…`, "working");

    for (const file of files) {
      try {
        const source = await canvasFromFile(file);
        batchItems.push({
          id: nextBatchId++,
          name: file.name,
          size: file.size,
          source,
          scale: 1,
          rotation: 0,
          flipH: false,
          flipV: false,
          selected: true
        });
      } catch (error) {
        notify(`${file.name}: ${error.message}`);
      }
    }

    renderBatchGrid();
    setStatus(`Đã nạp ${batchItems.length} ảnh`, "success");
  }

  function selectedBatchItems() {
    return batchItems.filter(item => item.selected);
  }

  function renderBatchGrid() {
    refs.batchGrid.innerHTML = "";
    refs.batchCount.textContent = `${batchItems.length} ảnh`;

    const selectedCount = selectedBatchItems().length;
    refs.batchSummary.textContent = batchItems.length
      ? `${selectedCount}/${batchItems.length} ảnh đã chọn`
      : "Chưa có ảnh";

    if (!batchItems.length) {
      refs.batchGrid.innerHTML = `
        <div class="image-editor-empty">
          <span>▦</span>
          <b>Chưa có ảnh hàng loạt</b>
          <small>Thả nhiều ảnh vào vùng phía trên để bắt đầu.</small>
        </div>
      `;
      return;
    }

    batchItems.forEach(item => {
      const card = document.createElement("article");
      card.className =
        "batch-image-card" + (item.selected ? " selected" : "");
      card.dataset.batchId = String(item.id);

      const preview = document.createElement("div");
      preview.className = "batch-image-preview";

      const previewCanvas = renderBatchItem(item, true);
      const select = document.createElement("span");
      select.className = "batch-select-indicator";
      select.textContent = "✓";

      const badge = document.createElement("span");
      badge.className = "batch-transform-badge";
      badge.textContent =
        `${Math.round(item.scale * 100)}% · ${item.rotation}°`;

      preview.append(previewCanvas, select, badge);

      const copy = document.createElement("div");
      copy.className = "batch-image-copy";
      copy.innerHTML = `
        <b>${escapeHtml(item.name)}</b>
        <small>
          ${item.source.width} × ${item.source.height} · ${humanBytes(item.size)}
        </small>
      `;

      card.append(preview, copy);
      card.addEventListener("click", () => {
        item.selected = !item.selected;
        renderBatchGrid();
      });

      refs.batchGrid.appendChild(card);
    });
  }

  function requireBatchSelection() {
    const items = selectedBatchItems();
    if (!items.length) {
      notify("Hãy chọn ít nhất một ảnh.");
      return null;
    }
    return items;
  }

  function applyBatchTransform(callback, message) {
    const items = requireBatchSelection();
    if (!items) return;

    items.forEach(callback);
    renderBatchGrid();
    setStatus(message, "success");
  }

  // -------------------------------------------------------------------
  // ZIP implementation (store-only ZIP, no CDN dependency).
  // -------------------------------------------------------------------
  const crcTable = (() => {
    const table = new Uint32Array(256);
    for (let value = 0; value < 256; value += 1) {
      let crc = value;
      for (let bit = 0; bit < 8; bit += 1) {
        crc = (crc & 1)
          ? (0xEDB88320 ^ (crc >>> 1))
          : (crc >>> 1);
      }
      table[value] = crc >>> 0;
    }
    return table;
  })();

  function crc32(data) {
    let crc = 0xFFFFFFFF;
    for (const byte of data) {
      crc = crcTable[(crc ^ byte) & 0xFF] ^ (crc >>> 8);
    }
    return (crc ^ 0xFFFFFFFF) >>> 0;
  }

  function little16(value) {
    return new Uint8Array([
      value & 0xFF,
      (value >>> 8) & 0xFF
    ]);
  }

  function little32(value) {
    return new Uint8Array([
      value & 0xFF,
      (value >>> 8) & 0xFF,
      (value >>> 16) & 0xFF,
      (value >>> 24) & 0xFF
    ]);
  }

  function concatArrays(parts) {
    const length = parts.reduce((sum, part) => sum + part.length, 0);
    const output = new Uint8Array(length);
    let offset = 0;

    parts.forEach(part => {
      output.set(part, offset);
      offset += part.length;
    });

    return output;
  }

  function dosTimeDate(date = new Date()) {
    const year = Math.max(1980, date.getFullYear());
    const time =
      ((date.getHours() & 0x1F) << 11)
      | ((date.getMinutes() & 0x3F) << 5)
      | (Math.floor(date.getSeconds() / 2) & 0x1F);
    const day =
      (((year - 1980) & 0x7F) << 9)
      | (((date.getMonth() + 1) & 0x0F) << 5)
      | (date.getDate() & 0x1F);

    return { time, day };
  }

  async function createZip(entries) {
    const encoder = new TextEncoder();
    const localParts = [];
    const centralParts = [];
    let offset = 0;
    const stamp = dosTimeDate();

    for (const entry of entries) {
      const nameBytes = encoder.encode(entry.name);
      const data = new Uint8Array(await entry.blob.arrayBuffer());
      const checksum = crc32(data);
      const flags = 0x0800;

      const localHeader = concatArrays([
        little32(0x04034B50),
        little16(20),
        little16(flags),
        little16(0),
        little16(stamp.time),
        little16(stamp.day),
        little32(checksum),
        little32(data.length),
        little32(data.length),
        little16(nameBytes.length),
        little16(0),
        nameBytes
      ]);

      localParts.push(localHeader, data);

      const centralHeader = concatArrays([
        little32(0x02014B50),
        little16(20),
        little16(20),
        little16(flags),
        little16(0),
        little16(stamp.time),
        little16(stamp.day),
        little32(checksum),
        little32(data.length),
        little32(data.length),
        little16(nameBytes.length),
        little16(0),
        little16(0),
        little16(0),
        little16(0),
        little32(0),
        little32(offset),
        nameBytes
      ]);

      centralParts.push(centralHeader);
      offset += localHeader.length + data.length;
    }

    const centralDirectory = concatArrays(centralParts);
    const endRecord = concatArrays([
      little32(0x06054B50),
      little16(0),
      little16(0),
      little16(entries.length),
      little16(entries.length),
      little32(centralDirectory.length),
      little32(offset),
      little16(0)
    ]);

    return new Blob(
      [...localParts, centralDirectory, endRecord],
      { type: "application/zip" }
    );
  }

  async function exportBatchZip() {
    if (!batchItems.length) {
      notify("Chưa có ảnh để xuất.");
      return;
    }

    setStatus("Đang tạo ZIP…", "working");

    try {
      const mime = refs.batchFormat.value;
      const extension = extensionForMime(mime);
      const entries = [];

      for (let index = 0; index < batchItems.length; index += 1) {
        const item = batchItems[index];
        const canvas = renderBatchItem(item, false);

        if (
          canvas.width > 16384
          || canvas.height > 16384
          || canvas.width * canvas.height > 120_000_000
        ) {
          throw new Error(`${item.name} có kích thước xuất quá lớn.`);
        }

        const blob = await canvasToBlob(canvas, mime, 0.92);
        entries.push({
          name: `${String(index + 1).padStart(2, "0")}_${basename(item.name)}.${extension}`,
          blob
        });
      }

      const zip = await createZip(entries);
      downloadBlob(zip, "Shield_Image_Studio_Batch.zip");
      setStatus(`Đã xuất ${entries.length} ảnh`, "success");
      notify("Đã tạo tệp ZIP hoàn toàn cục bộ.");
    } catch (error) {
      setStatus("Xuất ZIP thất bại", "error");
      notify(error.message);
    }
  }

  // -------------------------------------------------------------------
  // Single image editor.
  // -------------------------------------------------------------------
  function updateSingleDisplay() {
    if (!single.canvas) {
      refs.singlePlaceholder.classList.remove("hidden");
      refs.singleWrap.classList.add("hidden");
      refs.singleTitle.textContent = "Chưa mở ảnh";
      refs.singleMeta.textContent = "Chưa có dữ liệu";
      refs.singleFooter.textContent = "Sẵn sàng";
      refs.singleDropHint.textContent = "PNG, JPG, WebP hoặc GIF tĩnh";
      refs.singleUndo.disabled = true;
      refs.singleRedo.disabled = true;
      refs.singleReset.disabled = true;
      refs.singleClear.disabled = true;
      exitCropMode();
      return;
    }

    refs.singlePlaceholder.classList.add("hidden");
    refs.singleWrap.classList.remove("hidden");

    refs.singleCanvas.width = single.canvas.width;
    refs.singleCanvas.height = single.canvas.height;
    imageContext.clearRect(
      0,
      0,
      refs.singleCanvas.width,
      refs.singleCanvas.height
    );
    imageContext.drawImage(single.canvas, 0, 0);

    refs.cropCanvas.width = single.canvas.width;
    refs.cropCanvas.height = single.canvas.height;

    refs.singleTitle.textContent = single.fileName || "Ảnh chưa đặt tên";
    refs.singleMeta.textContent =
      `${single.canvas.width} × ${single.canvas.height} px`;
    refs.singleFooter.textContent =
      `${single.fileName || "image"} · ${single.canvas.width} × ${single.canvas.height}`;
    refs.singleDropHint.textContent =
      `${single.fileName} · ${single.canvas.width} × ${single.canvas.height}`;

    refs.singleUndo.disabled = single.historyIndex <= 0;
    refs.singleRedo.disabled =
      single.historyIndex >= single.history.length - 1;
    refs.singleReset.disabled = !single.originalDataUrl;
    refs.singleClear.disabled = false;

    if (single.cropActive) {
      drawCropOverlay();
    } else {
      cropContext.clearRect(
        0,
        0,
        refs.cropCanvas.width,
        refs.cropCanvas.height
      );
    }
  }

  function dataUrlFromCanvas(canvas) {
    return canvas.toDataURL("image/png");
  }

  async function canvasFromDataUrl(dataUrl) {
    const image = await new Promise((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error("Không khôi phục được ảnh."));
      element.src = dataUrl;
    });

    const canvas = document.createElement("canvas");
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    canvas.getContext("2d").drawImage(image, 0, 0);
    return canvas;
  }

  function pushHistory() {
    if (!single.canvas) return;

    single.history = single.history.slice(0, single.historyIndex + 1);
    single.history.push(dataUrlFromCanvas(single.canvas));

    if (single.history.length > 14) {
      single.history.shift();
    }

    single.historyIndex = single.history.length - 1;
    updateSingleDisplay();
  }

  async function commitSingle(canvas, label, resetHistory = false) {
    single.canvas = cloneCanvas(canvas);
    exitCropMode();

    if (resetHistory) {
      single.history = [];
      single.historyIndex = -1;
    }

    pushHistory();
    clearSlices();
    setStatus(label, "success");
  }

  async function loadSingleFile(file) {
    if (!isSupportedImage(file)) {
      notify("Chỉ nhận PNG, JPG, WebP hoặc GIF tĩnh dưới 50 MB.");
      return;
    }

    setStatus("Đang mở ảnh…", "working");

    try {
      const canvas = await canvasFromFile(file);
      single.fileName = file.name;
      single.originalDataUrl = dataUrlFromCanvas(canvas);
      await commitSingle(canvas, "Đã mở ảnh", true);
      switchTab("single");
    } catch (error) {
      setStatus("Không mở được ảnh", "error");
      notify(error.message);
    }
  }

  async function restoreHistory(index) {
    if (index < 0 || index >= single.history.length) return;

    setStatus("Đang khôi phục…", "working");

    try {
      single.canvas = await canvasFromDataUrl(single.history[index]);
      single.historyIndex = index;
      exitCropMode();
      clearSlices();
      updateSingleDisplay();
      setStatus("Đã khôi phục", "success");
    } catch (error) {
      setStatus("Khôi phục thất bại", "error");
      notify(error.message);
    }
  }

  function transformedSingleCanvas({
    scale = 1,
    angle = 0,
    flipH = false,
    flipV = false
  }) {
    if (!single.canvas) return null;

    const dims = transformedDimensions(
      single.canvas.width,
      single.canvas.height,
      scale,
      angle
    );

    if (
      dims.width > 16384
      || dims.height > 16384
      || dims.width * dims.height > 120_000_000
    ) {
      throw new Error("Kích thước ảnh sau biến đổi quá lớn.");
    }

    const output = document.createElement("canvas");
    output.width = dims.width;
    output.height = dims.height;

    const context = output.getContext("2d");
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.translate(output.width / 2, output.height / 2);
    context.rotate(angle * Math.PI / 180);
    context.scale(flipH ? -1 : 1, flipV ? -1 : 1);
    context.drawImage(
      single.canvas,
      -dims.sourceWidth / 2,
      -dims.sourceHeight / 2,
      dims.sourceWidth,
      dims.sourceHeight
    );

    return output;
  }

  async function resizeSingle() {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    const percent = Number(refs.singleScale.value);
    if (!Number.isFinite(percent) || percent < 1 || percent > 500) {
      notify("Tỉ lệ phải nằm trong khoảng 1–500%.");
      return;
    }

    try {
      const output = transformedSingleCanvas({ scale: percent / 100 });
      await commitSingle(output, `Đã đổi kích thước ${percent}%`);
      refs.singleScale.value = "100";
    } catch (error) {
      notify(error.message);
    }
  }

  async function rotateSingle(degrees) {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    const angle = Number(degrees);
    if (!Number.isFinite(angle)) {
      notify("Góc xoay không hợp lệ.");
      return;
    }

    try {
      const output = transformedSingleCanvas({ angle });
      await commitSingle(output, `Đã xoay ${angle}°`);
    } catch (error) {
      notify(error.message);
    }
  }

  async function flipSingle(horizontal) {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    try {
      const output = transformedSingleCanvas({
        flipH: horizontal,
        flipV: !horizontal
      });
      await commitSingle(
        output,
        horizontal ? "Đã lật ngang" : "Đã lật dọc"
      );
    } catch (error) {
      notify(error.message);
    }
  }

  function cropHandlePositions(rect) {
    const { x, y, width, height } = rect;
    return {
      nw: [x, y],
      n: [x + width / 2, y],
      ne: [x + width, y],
      e: [x + width, y + height / 2],
      se: [x + width, y + height],
      s: [x + width / 2, y + height],
      sw: [x, y + height],
      w: [x, y + height / 2]
    };
  }

  function startCropMode() {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    single.cropActive = true;
    single.cropRect = {
      x: Math.round(single.canvas.width * 0.1),
      y: Math.round(single.canvas.height * 0.1),
      width: Math.max(10, Math.round(single.canvas.width * 0.8)),
      height: Math.max(10, Math.round(single.canvas.height * 0.8))
    };

    refs.cropActions.classList.remove("hidden");
    refs.singleStartCrop.classList.add("hidden");
    refs.cropCanvas.style.pointerEvents = "auto";
    drawCropOverlay();
    setStatus("Đang chọn vùng cắt", "working");
  }

  function exitCropMode() {
    single.cropActive = false;
    single.cropRect = null;
    single.pointer = null;
    refs.cropActions.classList.add("hidden");
    refs.singleStartCrop.classList.remove("hidden");
    refs.cropCanvas.style.pointerEvents = "none";

    cropContext.clearRect(
      0,
      0,
      refs.cropCanvas.width,
      refs.cropCanvas.height
    );
  }

  function drawCropOverlay() {
    if (!single.cropActive || !single.cropRect) return;

    const width = refs.cropCanvas.width;
    const height = refs.cropCanvas.height;
    const rect = single.cropRect;

    cropContext.clearRect(0, 0, width, height);
    cropContext.save();

    cropContext.fillStyle = "rgba(8, 18, 11, .58)";
    cropContext.beginPath();
    cropContext.rect(0, 0, width, height);
    cropContext.rect(rect.x, rect.y, rect.width, rect.height);
    cropContext.fill("evenodd");

    cropContext.strokeStyle = "#ffffff";
    cropContext.lineWidth = Math.max(2, width / 800);
    cropContext.strokeRect(rect.x, rect.y, rect.width, rect.height);

    cropContext.strokeStyle = "rgba(255,255,255,.58)";
    cropContext.lineWidth = Math.max(1, width / 1400);

    for (let index = 1; index < 3; index += 1) {
      const vertical = rect.x + rect.width * index / 3;
      const horizontal = rect.y + rect.height * index / 3;

      cropContext.beginPath();
      cropContext.moveTo(vertical, rect.y);
      cropContext.lineTo(vertical, rect.y + rect.height);
      cropContext.stroke();

      cropContext.beginPath();
      cropContext.moveTo(rect.x, horizontal);
      cropContext.lineTo(rect.x + rect.width, horizontal);
      cropContext.stroke();
    }

    const handleSize = Math.max(
      7,
      Math.min(width, height) / 90
    );

    Object.values(cropHandlePositions(rect)).forEach(([x, y]) => {
      cropContext.fillStyle = "#ffffff";
      cropContext.fillRect(
        x - handleSize / 2,
        y - handleSize / 2,
        handleSize,
        handleSize
      );
      cropContext.strokeStyle = "rgba(20,40,27,.65)";
      cropContext.strokeRect(
        x - handleSize / 2,
        y - handleSize / 2,
        handleSize,
        handleSize
      );
    });

    cropContext.restore();

    refs.singleFooter.textContent =
      `Vùng cắt: ${Math.round(rect.width)} × ${Math.round(rect.height)} px`;
  }

  function pointerToCanvas(event) {
    const bounds = refs.cropCanvas.getBoundingClientRect();
    return {
      x: (event.clientX - bounds.left) * refs.cropCanvas.width / bounds.width,
      y: (event.clientY - bounds.top) * refs.cropCanvas.height / bounds.height
    };
  }

  function hitCropHandle(point) {
    const rect = single.cropRect;
    const bounds = refs.cropCanvas.getBoundingClientRect();
    const tolerance =
      13 * refs.cropCanvas.width / Math.max(bounds.width, 1);

    for (const [name, [x, y]] of Object.entries(cropHandlePositions(rect))) {
      if (
        Math.abs(point.x - x) <= tolerance
        && Math.abs(point.y - y) <= tolerance
      ) {
        return name;
      }
    }

    return "";
  }

  function pointInsideRect(point, rect) {
    return point.x >= rect.x
      && point.y >= rect.y
      && point.x <= rect.x + rect.width
      && point.y <= rect.y + rect.height;
  }

  function clampCropRect(rect) {
    const minSize = 8;
    let x = Math.max(0, Math.min(rect.x, refs.cropCanvas.width - minSize));
    let y = Math.max(0, Math.min(rect.y, refs.cropCanvas.height - minSize));
    let width = Math.max(minSize, rect.width);
    let height = Math.max(minSize, rect.height);

    if (x + width > refs.cropCanvas.width) {
      width = refs.cropCanvas.width - x;
    }

    if (y + height > refs.cropCanvas.height) {
      height = refs.cropCanvas.height - y;
    }

    return { x, y, width, height };
  }

  function resizeCropRect(original, handle, deltaX, deltaY) {
    let { x, y, width, height } = original;

    if (handle.includes("w")) {
      x += deltaX;
      width -= deltaX;
    }

    if (handle.includes("e")) {
      width += deltaX;
    }

    if (handle.includes("n")) {
      y += deltaY;
      height -= deltaY;
    }

    if (handle.includes("s")) {
      height += deltaY;
    }

    const minSize = 8;

    if (width < minSize) {
      if (handle.includes("w")) {
        x -= minSize - width;
      }
      width = minSize;
    }

    if (height < minSize) {
      if (handle.includes("n")) {
        y -= minSize - height;
      }
      height = minSize;
    }

    return clampCropRect({ x, y, width, height });
  }

  async function applyCrop() {
    if (!single.canvas || !single.cropRect) return;

    const rect = {
      x: Math.round(single.cropRect.x),
      y: Math.round(single.cropRect.y),
      width: Math.round(single.cropRect.width),
      height: Math.round(single.cropRect.height)
    };

    const output = document.createElement("canvas");
    output.width = rect.width;
    output.height = rect.height;
    output.getContext("2d").drawImage(
      single.canvas,
      rect.x,
      rect.y,
      rect.width,
      rect.height,
      0,
      0,
      rect.width,
      rect.height
    );

    await commitSingle(output, "Đã cắt ảnh");
  }

  function clearSlices() {
    single.slices = [];
    refs.sliceGrid.innerHTML = "";
    refs.sliceSection.classList.add("hidden");
    refs.sliceTitle.textContent = "0 phần";
  }

  function generateSlices() {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    const rows = Number.parseInt(refs.rows.value, 10);
    const cols = Number.parseInt(refs.cols.value, 10);

    if (
      !Number.isInteger(rows)
      || !Number.isInteger(cols)
      || rows < 1
      || cols < 1
      || rows > 20
      || cols > 20
    ) {
      notify("Số hàng và cột phải nằm trong khoảng 1–20.");
      return;
    }

    single.slices = [];
    refs.sliceGrid.innerHTML = "";

    for (let row = 0; row < rows; row += 1) {
      for (let col = 0; col < cols; col += 1) {
        const x1 = Math.floor(col * single.canvas.width / cols);
        const x2 = Math.floor((col + 1) * single.canvas.width / cols);
        const y1 = Math.floor(row * single.canvas.height / rows);
        const y2 = Math.floor((row + 1) * single.canvas.height / rows);

        const sliceCanvas = document.createElement("canvas");
        sliceCanvas.width = x2 - x1;
        sliceCanvas.height = y2 - y1;
        sliceCanvas.getContext("2d").drawImage(
          single.canvas,
          x1,
          y1,
          sliceCanvas.width,
          sliceCanvas.height,
          0,
          0,
          sliceCanvas.width,
          sliceCanvas.height
        );

        const slice = {
          row: row + 1,
          col: col + 1,
          canvas: sliceCanvas,
          name:
            `${basename(single.fileName)}_part_${row + 1}_${col + 1}.png`
        };

        single.slices.push(slice);

        const card = document.createElement("article");
        card.className = "slice-result-card";

        const preview = document.createElement("div");
        preview.className = "slice-result-preview";

        const previewCanvas = document.createElement("canvas");
        const ratio = Math.min(
          140 / sliceCanvas.width,
          105 / sliceCanvas.height,
          1
        );
        previewCanvas.width = Math.max(1, Math.round(sliceCanvas.width * ratio));
        previewCanvas.height = Math.max(1, Math.round(sliceCanvas.height * ratio));
        previewCanvas.getContext("2d").drawImage(
          sliceCanvas,
          0,
          0,
          previewCanvas.width,
          previewCanvas.height
        );
        preview.appendChild(previewCanvas);

        const footer = document.createElement("div");
        footer.className = "slice-result-footer";

        const title = document.createElement("b");
        title.textContent = `Phần ${row + 1}.${col + 1}`;

        const button = document.createElement("button");
        button.textContent = "Tải";
        button.addEventListener("click", async () => {
          const blob = await canvasToBlob(sliceCanvas, "image/png");
          downloadBlob(blob, slice.name);
        });

        footer.append(title, button);
        card.append(preview, footer);
        refs.sliceGrid.appendChild(card);
      }
    }

    refs.sliceTitle.textContent = `${single.slices.length} phần`;
    refs.sliceSection.classList.remove("hidden");
    setStatus(`Đã chia thành ${single.slices.length} phần`, "success");
  }

  async function downloadSlicesZip() {
    if (!single.slices.length) {
      notify("Chưa có phần ảnh nào.");
      return;
    }

    setStatus("Đang tạo ZIP các phần…", "working");

    try {
      const entries = [];
      for (const slice of single.slices) {
        entries.push({
          name: slice.name,
          blob: await canvasToBlob(slice.canvas, "image/png")
        });
      }

      const zip = await createZip(entries);
      downloadBlob(zip, `${basename(single.fileName)}_parts.zip`);
      setStatus("Đã tải ZIP các phần", "success");
    } catch (error) {
      setStatus("Không tạo được ZIP", "error");
      notify(error.message);
    }
  }

  async function saveSingle() {
    if (!single.canvas) {
      notify("Hãy mở ảnh trước.");
      return;
    }

    const mime = refs.format.value;
    const quality = Math.max(
      0.1,
      Math.min(1, Number(refs.quality.value || 92) / 100)
    );
    const extension = extensionForMime(mime);

    try {
      const blob = await canvasToBlob(single.canvas, mime, quality);
      downloadBlob(
        blob,
        `${basename(single.fileName)}_edited.${extension}`
      );
      setStatus("Đã tải ảnh", "success");
    } catch (error) {
      setStatus("Không thể lưu ảnh", "error");
      notify(error.message);
    }
  }

  function clearSingle() {
    single.canvas = null;
    single.fileName = "";
    single.originalDataUrl = "";
    single.history = [];
    single.historyIndex = -1;
    clearSlices();
    updateSingleDisplay();
    setStatus("Đã đóng ảnh", "idle");
  }

  // -------------------------------------------------------------------
  // Event binding.
  // -------------------------------------------------------------------
  const originalHandleAction = handleAction;
  handleAction = function handleImageEditorAction(action) {
    if (action === "image-editor") {
      openEditor();
      return;
    }

    originalHandleAction(action);
  };

  refs.toolbarButton.addEventListener("click", () => openEditor());
  refs.close.addEventListener("click", closeEditor);
  refs.backdrop.addEventListener("click", closeEditor);

  document.querySelectorAll("[data-image-editor-tab]").forEach(button => {
    button.addEventListener("click", () => {
      switchTab(button.dataset.imageEditorTab);
    });
  });

  function bindDropZone(zone, callback) {
    ["dragenter", "dragover"].forEach(type => {
      zone.addEventListener(type, event => {
        event.preventDefault();
        zone.classList.add("drag-active");
      });
    });

    ["dragleave", "drop"].forEach(type => {
      zone.addEventListener(type, event => {
        event.preventDefault();
        zone.classList.remove("drag-active");
      });
    });

    zone.addEventListener("drop", event => {
      callback(event.dataTransfer?.files || []);
    });
  }

  refs.batchBrowse.addEventListener("click", () => refs.batchInput.click());
  refs.batchInput.addEventListener("change", () => {
    addBatchFiles(refs.batchInput.files);
    refs.batchInput.value = "";
  });
  bindDropZone(refs.batchDropZone, addBatchFiles);

  refs.batchSelectAll.addEventListener("click", () => {
    batchItems.forEach(item => item.selected = true);
    renderBatchGrid();
  });

  refs.batchSelectNone.addEventListener("click", () => {
    batchItems.forEach(item => item.selected = false);
    renderBatchGrid();
  });

  refs.batchApplyScale.addEventListener("click", () => {
    const percent = Number(refs.batchScale.value);
    if (!Number.isFinite(percent) || percent < 1 || percent > 500) {
      notify("Tỉ lệ phải nằm trong khoảng 1–500%.");
      return;
    }

    applyBatchTransform(
      item => item.scale = percent / 100,
      `Đã đặt kích thước ${percent}%`
    );
  });

  refs.batchRotateButton.addEventListener("click", () => {
    const degrees = Number(refs.batchRotate.value);
    if (!Number.isFinite(degrees)) {
      notify("Góc xoay không hợp lệ.");
      return;
    }

    applyBatchTransform(
      item => item.rotation =
        Math.round((item.rotation + degrees) % 360),
      `Đã xoay thêm ${degrees}°`
    );
  });

  refs.batchFlipH.addEventListener("click", () => {
    applyBatchTransform(
      item => item.flipH = !item.flipH,
      "Đã lật ngang ảnh được chọn"
    );
  });

  refs.batchFlipV.addEventListener("click", () => {
    applyBatchTransform(
      item => item.flipV = !item.flipV,
      "Đã lật dọc ảnh được chọn"
    );
  });

  refs.batchReset.addEventListener("click", () => {
    applyBatchTransform(item => {
      item.scale = 1;
      item.rotation = 0;
      item.flipH = false;
      item.flipV = false;
    }, "Đã đặt lại biến đổi");
  });

  refs.batchZip.addEventListener("click", exportBatchZip);

  refs.batchRemove.addEventListener("click", () => {
    const selected = selectedBatchItems();
    if (!selected.length) {
      notify("Chưa chọn ảnh để xóa.");
      return;
    }

    const selectedIds = new Set(selected.map(item => item.id));
    for (let index = batchItems.length - 1; index >= 0; index -= 1) {
      if (selectedIds.has(batchItems[index].id)) {
        batchItems.splice(index, 1);
      }
    }

    renderBatchGrid();
    setStatus("Đã xóa ảnh được chọn", "success");
  });

  refs.batchClear.addEventListener("click", () => {
    if (!batchItems.length) return;
    if (!confirm("Xóa toàn bộ ảnh hàng loạt đang mở?")) return;
    batchItems.length = 0;
    renderBatchGrid();
    setStatus("Đã xóa toàn bộ", "idle");
  });

  refs.singleBrowse.addEventListener("click", () => refs.singleInput.click());
  refs.singleInput.addEventListener("change", () => {
    loadSingleFile(refs.singleInput.files?.[0]);
    refs.singleInput.value = "";
  });
  bindDropZone(refs.singleDropZone, files => loadSingleFile(files?.[0]));

  refs.singleUndo.addEventListener("click", () => {
    restoreHistory(single.historyIndex - 1);
  });

  refs.singleRedo.addEventListener("click", () => {
    restoreHistory(single.historyIndex + 1);
  });

  refs.singleReset.addEventListener("click", async () => {
    if (!single.originalDataUrl) return;
    const original = await canvasFromDataUrl(single.originalDataUrl);
    await commitSingle(original, "Đã khôi phục ảnh gốc");
  });

  refs.singleClear.addEventListener("click", clearSingle);
  refs.singleResize.addEventListener("click", resizeSingle);
  refs.singleRotateButton.addEventListener("click", () => {
    rotateSingle(Number(refs.singleRotate.value));
  });
  refs.singleRotateLeft.addEventListener("click", () => rotateSingle(-90));
  refs.singleFlipH.addEventListener("click", () => flipSingle(true));
  refs.singleFlipV.addEventListener("click", () => flipSingle(false));
  refs.singleStartCrop.addEventListener("click", startCropMode);
  refs.singleApplyCrop.addEventListener("click", applyCrop);
  refs.singleCancelCrop.addEventListener("click", () => {
    exitCropMode();
    updateSingleDisplay();
    setStatus("Đã hủy cắt ảnh", "idle");
  });

  refs.cropCanvas.addEventListener("pointerdown", event => {
    if (!single.cropActive || !single.cropRect) return;

    const point = pointerToCanvas(event);
    const handle = hitCropHandle(point);

    single.pointer = {
      id: event.pointerId,
      start: point,
      original: { ...single.cropRect },
      mode: handle
        ? "resize"
        : pointInsideRect(point, single.cropRect)
          ? "move"
          : "new",
      handle
    };

    if (single.pointer.mode === "new") {
      single.cropRect = {
        x: point.x,
        y: point.y,
        width: 8,
        height: 8
      };
      single.pointer.original = { ...single.cropRect };
    }

    refs.cropCanvas.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  });

  refs.cropCanvas.addEventListener("pointermove", event => {
    if (
      !single.pointer
      || single.pointer.id !== event.pointerId
      || !single.cropRect
    ) {
      return;
    }

    const point = pointerToCanvas(event);
    const deltaX = point.x - single.pointer.start.x;
    const deltaY = point.y - single.pointer.start.y;
    const original = single.pointer.original;

    if (single.pointer.mode === "move") {
      single.cropRect = clampCropRect({
        x: original.x + deltaX,
        y: original.y + deltaY,
        width: original.width,
        height: original.height
      });
    } else if (single.pointer.mode === "resize") {
      single.cropRect = resizeCropRect(
        original,
        single.pointer.handle,
        deltaX,
        deltaY
      );
    } else {
      const x = Math.min(original.x, point.x);
      const y = Math.min(original.y, point.y);
      single.cropRect = clampCropRect({
        x,
        y,
        width: Math.max(8, Math.abs(point.x - original.x)),
        height: Math.max(8, Math.abs(point.y - original.y))
      });
    }

    drawCropOverlay();
    event.preventDefault();
  });

  ["pointerup", "pointercancel"].forEach(type => {
    refs.cropCanvas.addEventListener(type, event => {
      if (single.pointer?.id === event.pointerId) {
        single.pointer = null;
      }
    });
  });

  refs.slice.addEventListener("click", generateSlices);
  refs.sliceZip.addEventListener("click", downloadSlicesZip);
  refs.save.addEventListener("click", saveSingle);

  refs.format.addEventListener("change", () => {
    refs.qualityField.classList.toggle(
      "hidden",
      refs.format.value === "image/png"
    );
  });

  window.addEventListener("resize", () => {
    if (single.cropActive) {
      drawCropOverlay();
    }
  });

  document.addEventListener("keydown", event => {
    const editorOpen = !refs.shell.classList.contains("hidden");
    if (!editorOpen) return;

    if (event.key === "Escape") {
      if (single.cropActive) {
        exitCropMode();
        updateSingleDisplay();
      } else {
        closeEditor();
      }
      return;
    }

    const modifier = event.ctrlKey || event.metaKey;
    if (!modifier) return;

    if (event.key.toLowerCase() === "z" && !event.shiftKey) {
      event.preventDefault();
      restoreHistory(single.historyIndex - 1);
    }

    if (
      event.key.toLowerCase() === "y"
      || (event.key.toLowerCase() === "z" && event.shiftKey)
    ) {
      event.preventDefault();
      restoreHistory(single.historyIndex + 1);
    }
  });

  renderBatchGrid();
  updateSingleDisplay();

  window.ShieldImageStudio = {
    open: openEditor,
    close: closeEditor,
    batchItems,
    single
  };
})();