# Báo cáo làm lại Nhạc và video nền — lqlq Browser v0.26.0

## Vấn đề gốc

Bản v0.25 mở file local bằng `<input type="file">` trong shell WebView, sau đó tạo `blob:` URL và phát bằng `<video>`. Trên Android, tệp thực tế thường được cấp dưới dạng `content://`; quyền và vòng đời của nguồn này không phù hợp để một HTMLMediaElement trong WebView làm player nền ổn định.

`PlaybackService` cũ chỉ giữ một notification và chuyển lệnh về JavaScript. Âm thanh thật vẫn nằm trong renderer WebView của Activity. Vì vậy foreground service có chạy nhưng khi Activity/renderer bị đưa ra nền hoặc bị hệ thống tạm dừng, nguồn phát vẫn ngừng.

## Kiến trúc mới

### 1. Player thật nằm trong service

Đã thêm `NativeMediaPlaybackService.kt`:

- Kế thừa `MediaSessionService`.
- Tạo một `ExoPlayer` và `MediaSession` trong `onCreate()`.
- Dùng audio attributes loại media, xử lý audio focus và sự kiện rút tai nghe.
- Dùng wake mode cho nguồn local và nguồn mạng.
- Player chỉ được giải phóng khi service bị hủy, không phụ thuộc vòng đời của `MainActivity`.

### 2. Activity chỉ là controller

`MainActivity.kt` giờ:

- Kết nối service qua `SessionToken` + `MediaController` trong `onStart()`.
- Giải phóng controller trong `onStop()` nhưng không dừng player.
- Gửi `MediaItem`, play/pause/stop/seek và volume tới service.
- Nhận `Player.Listener` để đồng bộ trạng thái về giao diện JavaScript.
- Khi mở lại app, controller nối lại session đang chạy và shell hiện đúng tên, trạng thái và âm lượng hiện tại.

### 3. Chọn file đúng kiểu Android

Nút **Chọn file** giờ gọi `ACTION_OPEN_DOCUMENT` với `audio/*` và `video/*`:

- Lấy tên hiển thị từ `OpenableColumns.DISPLAY_NAME`.
- Lấy MIME từ `ContentResolver`, có fallback theo phần mở rộng.
- Giữ quyền đọc URI khi document provider hỗ trợ quyền bền vững.
- Không sao chép toàn bộ file vào RAM hoặc cache.
- Không yêu cầu quyền đọc toàn bộ thư viện media.

Các định dạng nhận diện: MP3, M4A/M4B, AAC, OGG/OGA, WAV, FLAC, MP4/M4V, WEBM, MKV, MOV và 3GP.

### 4. Phân tách native và WebView

`v13-media.js` được viết lại theo hai backend:

- `native`: file local và URL media trực tiếp.
- `web`: YouTube iframe chính thức và fallback khi chạy giao diện ngoài Android.

Các thay đổi quan trọng:

- Android không còn dùng `URL.createObjectURL(file)` cho file local.
- Mở website khác không tạm dừng player native.
- Nút play/pause/stop và volume trong panel gửi thẳng tới MediaController.
- Khi app được tạo lại, UI đọc state đã cache và nhận state thật từ session.
- Hủy trình chọn file không làm mất bài/YouTube đang phát.
- Backend native không đăng ký thêm Web MediaSession, tránh tạo session trùng.

### 5. Notification không bị trùng

`ShellBridge.kt` nhận trường `backend` từ `android-glue.js`:

- Với backend `native`, `MediaSessionService` tự quản lý notification.
- `PlaybackService` cũ được yêu cầu bỏ phiên `media`, nên không xuất hiện hai thanh thông báo cho cùng một bài.
- `PlaybackService` vẫn được giữ cho Đọc truyện TXT và fallback YouTube/WebView.

## Hành vi sau khi sửa

1. Mở **Nhạc và video nền**.
2. Chọn MP3/MP4 từ trình chọn file Android hoặc dán URL media trực tiếp.
3. Nội dung được phát bởi ExoPlayer trong service.
4. Bấm Home, chuyển ứng dụng hoặc tắt màn hình: âm thanh tiếp tục.
5. Điều khiển phát/tạm dừng từ notification, màn hình khóa hoặc thiết bị Bluetooth.
6. Mở lại lqlq Browser: panel khôi phục đúng nội dung đang phát.
7. Bấm **Dừng** trong panel: player dừng, playlist bị xóa và notification native được gỡ.

## Giới hạn có chủ ý

- File video/URL video native được ưu tiên cho **âm thanh nền**. Panel WebView không có surface video native, nên MP4 tiếp tục phát phần âm thanh khi app ở nền.
- YouTube vẫn dùng iframe chính thức. Project không trích xuất URL stream YouTube; do đó YouTube không có bảo đảm chạy nền giống file local/direct URL.
- Force stop ứng dụng trong Android Settings luôn dừng service; không ứng dụng nào được tiếp tục chạy sau force stop.

## File thay đổi so với v0.25

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/lqlq/browser/NativeMediaPlaybackService.kt` (mới)
- `app/src/main/java/com/lqlq/browser/MainActivity.kt`
- `app/src/main/java/com/lqlq/browser/ShellBridge.kt`
- `app/src/main/assets/www/v13-media.js`
- `app/src/main/assets/www/android-glue.js`
- `app/src/main/assets/www/index.html`
- `app/src/main/assets/www/CHANGELOG_v0_26.txt` (mới)
- `README.md`

## Kiểm tra đã chạy

- `node --check` cho toàn bộ JavaScript trong `assets/www`: đạt.
- Parse toàn bộ XML và AndroidManifest: đạt.
- Kiểm tra 256 HTML ID, không có ID trùng: đạt.
- Kiểm tra tất cả script được `index.html` tham chiếu đều tồn tại: đạt.
- Mock runtime cho `v13-media.js`: khởi tạo, nhận native state, toggle và stop đều đạt.
- Integration assertions cho dependency, manifest, service, bridge, picker và backend state: đạt.
- So sánh với v0.25 xác nhận chỉ các file liên quan đến media, version và tài liệu bị thay đổi.

## Chưa thể kiểm tra trong môi trường này

Không có Android SDK/thiết bị Android trong runtime hiện tại, nên chưa chạy được `assembleDebug`, cài APK hoặc thử notification thực tế trên điện thoại. Workflow GitHub Actions trong project vẫn dùng Gradle 8.7 + JDK 17 để build debug/release.
