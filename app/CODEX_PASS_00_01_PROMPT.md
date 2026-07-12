# PROMPT CHO CODEX — VIDEO WORKSPACE V36 / PASS 00 + PASS 01

Dán toàn bộ nội dung dưới đây vào Codex tại project:

`C:\Users\Admin\Downloads\lqlq-browser-android-v0_33_automation-pipeline`

---

Bạn đang làm trên Android project LQLQ Browser. Hãy đọc `VIDEO_WORKSPACE_V36_MASTER_PLAN.md` ở project root trước khi sửa code.

## Mục tiêu lần này

Chỉ thực hiện **PASS 00 + PASS 01**:

1. Giữ nguyên toàn bộ chức năng V35.
2. Tạo shell V36 song song có feature flag/rollback.
3. V36 đọc danh sách phiên và snapshot job hiện tại.
4. Hiển thị Storyboard read-only theo từng cảnh.
5. Tuyệt đối chưa thêm CRUD, planner, invalidation hoặc chạy lại riêng từng cảnh.

## Baseline cần kiểm tra trước

- `src/main/assets/www/v35-video-automation-workspace.js` đang export `window.LqlqAutomationCenter`.
- `src/main/assets/www/index.html` đang nạp V35 ở cuối trang.
- V35 dùng localStorage key `lqlq-video-automation-sessions-v35`.
- Job snapshot hiện có `scenePrompts`, `artifacts`, `videoRenderPlan`, `metadataPlan`, `reviewState`, `publishPlan`.
- Artifact ảnh đã có `sceneId` và `ordinal`.

## Phạm vi file được phép sửa

Được phép:

- `src/main/assets/www/index.html`.
- Thêm `src/main/assets/www/v36-video-workspace-store.js`.
- Thêm `src/main/assets/www/v36-video-workspace.js`.
- Thêm `src/main/assets/www/v36-video-workspace.css`.
- Có thể thêm changelog/tài liệu PASS.

Không được sửa trong lần này:

- `v35-video-automation-workspace.js`, trừ khi thật sự không còn cách khác; nếu phải sửa thì chỉ thêm hook không phá hành vi và phải giải thích.
- `AutomationFacade.kt`.
- `AutomationBridge.kt`.
- Mọi connector, renderer, voice, database, worker.
- `styles.css` lớn; style V36 phải nằm trong CSS riêng.

## Yêu cầu feature flag

1. V35 được nạp trước V36.
2. Trước khi ghi đè global, V36 phải giữ reference của V35.
3. Flag lưu bằng localStorage, ví dụ:

```text
lqlq-video-workspace-version = v35 | v36
```

4. Trong giai đoạn này mặc định là `v35` để rollback an toàn.
5. Có cách bật V36 bằng console hoặc localStorage mà không build lại.
6. Khi flag là V35, hành vi phải giống hiện tại.
7. Khi flag là V36, `window.LqlqAutomationCenter.openFromTools()` mở shell mới.

## Yêu cầu store V36

Tạo module/store có nhiệm vụ:

- Đọc session từ key V35 mà không thay đổi nội dung.
- Chuẩn hóa danh sách session.
- Lấy job snapshot qua bridge/API hiện có giống cách V35 đang làm.
- Map scene theo `sceneId`.
- Map image artifact theo `sceneId` trước; chỉ fallback theo `ordinal` cho dữ liệu legacy.
- Không dùng index làm danh tính chính.
- Không ghi native data.

Selector/view model tối thiểu:

```javascript
{
  sessionId,
  linkedJobId,
  title,
  status,
  topic,
  sceneCount,
  scenes: [
    {
      sceneId,
      ordinal,
      onScreenText,
      voiceText,
      stockSearchQuery,
      visualPrompt,
      plannedDurationMs,
      imageArtifact,
      imageDisplayStatus
    }
  ],
  hasVoice,
  hasVideo,
  progressPercent
}
```

`imageDisplayStatus` tạm thời chỉ cần:

- `READY` nếu tìm thấy IMAGE artifact cho scene.
- `MISSING` nếu chưa có.

Chưa được tự suy diễn `STALE` trong PASS này.

## Yêu cầu UI V36 read-only

Màn hình home:

- Danh sách phiên tương tự V35, nhưng có nhãn V36 dev để phân biệt.
- Mở phiên vào Storyboard, không mở sheet điều hướng cũ.

Storyboard:

- Header: quay lại, tên phiên, trạng thái.
- Thanh trạng thái đơn giản: Nội dung / Hình ảnh / Giọng / Video.
- Thông tin chung: chủ đề, số cảnh, tiến độ.
- Danh sách card cảnh theo `ordinal`.
- Card hiển thị thumbnail, số cảnh, tiêu đề, voiceText rút gọn, query ảnh, duration, trạng thái ảnh.
- Cảnh chưa ảnh phải có placeholder rõ ràng.
- Chưa có thao tác sửa/tạo lại; các nút có thể disabled hoặc ghi “Sẽ có ở PASS sau”.
- Có nút quay lại home và đóng workspace.
- Responsive cho màn hình Android hẹp.

Không sao chép nguyên node graph. Dùng Storyboard card.

## Tương thích bắt buộc

- Không đổi localStorage V35.
- Không xóa hoặc thay `linkedJobId`.
- Không gọi Gemini, ảnh, voice, video khi chỉ mở V36.
- Không tạo job mới ngoài hành động người dùng đã có.
- Không làm mất `window.LqlqAutomationCenter.openFromTools()`.
- Không làm lỗi các nút browser khác.

## Kiểm thử

Trong project đầy đủ, chạy build phù hợp, ít nhất:

```bash
./gradlew :app:assembleDebug
```

Nếu Windows:

```bat
gradlew.bat :app:assembleDebug
```

Kiểm tra thủ công:

1. Flag V35: mở công cụ tạo video, giao diện cũ hoạt động.
2. Flag V36: mở giao diện mới.
3. Phiên có 12 cảnh: hiển thị đủ 12, đúng thứ tự.
4. Ảnh được gắn đúng sceneId.
5. Cảnh thiếu ảnh hiển thị MISSING.
6. Đóng/mở lại không thay session data.
7. Console không có uncaught error.

## Báo cáo bắt buộc sau khi hoàn thành

Trả lời với:

- File đã thêm.
- File đã sửa.
- Cách bật V36 và quay lại V35.
- Cách V36 lấy session/job snapshot.
- Kết quả build.
- Hạn chế còn lại đúng phạm vi PASS 00 + 01.
- Không tự chuyển sang PASS 02.
