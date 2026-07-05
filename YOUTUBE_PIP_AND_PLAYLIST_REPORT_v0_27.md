# Báo cáo YouTube PiP và playlist — lqlq Browser v0.27.0

## Mục tiêu

1. Khi phát link YouTube rồi rời ứng dụng, video tiếp tục trong cửa sổ Picture-in-Picture.
2. Thêm lặp lại đúng một bài.
3. File local tự chuyển sang bài kế tiếp trong cùng thư mục.
4. YouTube tự chuyển trong hàng đợi/playlist YouTube.
5. Không phá phần phát nền Media3 đã hoạt động ở v0.26.

## Thay đổi Android

### Picture-in-Picture

`AndroidManifest.xml` khai báo `supportsPictureInPicture=true` và Activity có thể resize. `MainActivity` theo dõi trạng thái YouTube do JavaScript gửi về:

- Chỉ bật auto-enter khi YouTube đang phát.
- Android 12+ dùng `setAutoEnterEnabled(true)`.
- Android 8–11 dùng `onUserLeaveHint()` và gọi `enterPictureInPictureMode()`.
- Trước khi vào PiP, shell chuyển sang layout chỉ còn iframe video 16:9 và cung cấp `sourceRectHint` cho hiệu ứng chuyển mượt hơn.
- Khi thoát PiP, layout bình thường được khôi phục. Nút **Mở bằng YouTube** không tạo thêm một PiP trùng.

### Playlist local

Khi người dùng chọn một file:

1. Giữ quyền đọc URI của file đã chọn.
2. Xác định MIME audio/video.
3. Xin quyền media đúng loại khi cần đọc các file khác trong thư mục.
4. Android 10+ tra `RELATIVE_PATH` bằng MediaStore.
5. Android 7–9 dùng đường dẫn media legacy có kiểm soát.
6. Chỉ lấy các file cùng loại với file đã chọn để tránh yêu cầu thừa quyền audio/video.
7. Bảo đảm file vừa chọn luôn được đưa vào playlist ngay cả khi URI của DocumentProvider khác URI MediaStore.
8. Sắp danh sách theo tên rồi gọi `MediaController.setMediaItems(items, startIndex, 0)`.

Nếu không xác định được thư mục, player dùng playlist một phần tử nên file vẫn phát được. Nút **Chọn thư mục** là đường ổn định hơn cho DocumentProvider đặc biệt: nó dùng `ACTION_OPEN_DOCUMENT_TREE` và duyệt các file trực tiếp trong thư mục được cấp quyền.

### Repeat-one và điều khiển hàng đợi

- `Player.REPEAT_MODE_ONE` cho Media3.
- Repeat-one được lưu trong `SharedPreferences` và khôi phục khi service tạo lại.
- `seekToNextMediaItem()` và `seekToPreviousMediaItem()` cho playlist native.
- Notification/lock-screen/headset tiếp tục dùng cùng `MediaSession`.

## Thay đổi YouTube

- Nhận diện `watch`, `youtu.be`, `shorts`, `live`, `embed`, link playlist và link có `list=`.
- Embed playlist bằng `listType=playlist` + `list`.
- Đăng ký listener của YouTube IFrame API bằng `postMessage`.
- Khi video kết thúc:
  - Repeat-one bật: tua về 0 và phát lại.
  - Repeat-one tắt: giữ auto-next của playlist; nếu iframe chưa chuyển thì gọi `nextVideo()`.
- Nút Bài trước/Bài tiếp theo gọi `previousVideo()`/`nextVideo()`.
- Khi playlist chuyển mục, tiêu đề, kênh, vị trí hàng đợi và URL hiện tại được đồng bộ lại để nút **Mở bằng YouTube** mở đúng video đang phát.
- Không lấy URL stream và không đưa nội dung YouTube vào ExoPlayer.

## Tệp đã sửa

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/lqlq/browser/MainActivity.kt`
- `app/src/main/java/com/lqlq/browser/NativeMediaPlaybackService.kt`
- `app/src/main/java/com/lqlq/browser/ShellBridge.kt`
- `app/src/main/assets/www/index.html`
- `app/src/main/assets/www/styles.css`
- `app/src/main/assets/www/v13-media.js`
- `README.md`
- `app/src/main/assets/www/README.txt`
- `app/src/main/assets/www/CHANGELOG_v0_27.txt`

## Kiểm tra đã chạy

- `node --check` cho toàn bộ JavaScript: đạt.
- Parse toàn bộ XML/AndroidManifest: đạt.
- Kiểm tra 261 HTML ID: không trùng.
- Kiểm tra 29 ID được `v13-media.js` dùng: không thiếu.
- Kiểm tra tài nguyên local trong `index.html`: không thiếu.
- Chạy mô phỏng JavaScript cho YouTube playlist, cập nhật metadata khi đổi video, repeat-one, PiP class, native playlist state và lệnh next: đạt.
- Chạy Kotlin compiler ở chế độ kiểm tra cú pháp; không có lỗi parser. Các lỗi unresolved Android là do môi trường không có Android SDK/classpath.
- Kiểm tra toàn vẹn ZIP sau đóng gói: ghi ở file SHA-256 đi kèm.

## Giới hạn cần biết

- YouTube PiP giữ video chính thức hiển thị trong cửa sổ nổi; đây không phải phát âm thanh ẩn khi tắt màn hình.
- Auto-next được bảo đảm cho playlist YouTube hoặc link có ngữ cảnh playlist. Với video đơn, khả năng chuyển sang đề xuất tiếp theo phụ thuộc trình nhúng YouTube.
- Một số trình quản lý file không cung cấp đủ thông tin để suy ra thư mục từ một URI đơn. Khi đó dùng **Chọn thư mục** để tạo playlist đầy đủ.
- Môi trường sửa project không có Android SDK/thiết bị, nên chưa chạy `assembleDebug` hoặc test PiP thật trên điện thoại. GitHub Actions trong project vẫn là bước build APK chính thức.
