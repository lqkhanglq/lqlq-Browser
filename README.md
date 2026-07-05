# lqlq Browser — Android APK (v0.27.0)

Project Android của lqlq Browser. Bản v0.27.0 giữ toàn bộ hệ thẻ native, Trang đã lưu và trình phát Media3 của v0.26, đồng thời bổ sung **YouTube Picture-in-Picture**, **lặp lại một bài** và **playlist tự động theo thư mục**.

## Cách build bằng GitHub Actions

1. Đưa toàn bộ thư mục project lên repository GitHub, bao gồm `.github/` và `keystore/`.
2. Mở **Actions** → **Build lqlq Browser APK** → **Run workflow**.
3. Tải artifact `lqlq-browser-apk` sau khi build xong.
4. Gói artifact chứa:
   - `lqlq-browser-v0.27.0-debug.apk`
   - `lqlq-browser-v0.27.0-release.apk`

Bản release tiếp tục dùng keystore ổn định của project, vì vậy có thể cài đè lên bản cũ có cùng chữ ký.

## YouTube Picture-in-Picture

- Dán link video hoặc playlist YouTube và phát như bình thường.
- Khi video đang phát, bấm Home hoặc vuốt về màn hình chính: ứng dụng chuyển sang cửa sổ video nổi 16:9.
- Có nút **Picture-in-Picture** để mở thủ công.
- Có nút **Mở bằng YouTube** để chuyển sang ứng dụng YouTube chính thức.
- PiP chỉ tự mở khi nội dung YouTube thật sự đang phát; video đã tạm dừng không tự bật PiP.
- YouTube vẫn chạy bằng iframe chính thức, không trích xuất luồng âm thanh/video.

## Playlist và tự chuyển bài

### File trong máy

- Khi chọn một file MP3/MP4, ứng dụng cố tìm toàn bộ file media cùng loại trong thư mục chứa file đó.
- Danh sách được sắp theo tên và đưa vào Media3; hết bài hiện tại sẽ tự chuyển sang bài kế tiếp.
- Trên Android 13+, ứng dụng chỉ xin `READ_MEDIA_AUDIO` hoặc `READ_MEDIA_VIDEO` đúng lúc cần tạo playlist thư mục.
- Trên Android 7–12, ứng dụng dùng quyền đọc media cũ khi cần.
- Nếu nhà cung cấp file không cho xác định thư mục hoặc người dùng từ chối quyền, file đã chọn vẫn phát bình thường như một bài đơn.
- Nút **Chọn thư mục** dùng Storage Access Framework, không cần quyền đọc toàn bộ thư viện và tạo playlist từ các file trực tiếp trong thư mục đã chọn.

### YouTube

- Link playlist hoặc link video có tham số `list=` dùng hàng đợi chính thức của YouTube và tự chuyển video tiếp theo.
- Nút **Bài trước/Bài tiếp theo** gửi lệnh trực tiếp tới YouTube IFrame Player.
- Với link video đơn không có playlist, bài tiếp theo phụ thuộc hàng đợi/đề xuất mà trình nhúng YouTube cung cấp; ứng dụng không tự thu thập hay trích xuất video liên quan.

## Lặp lại một bài

- Nút **Lặp lại một bài** hoạt động với file local, URL media trực tiếp, playlist local và YouTube.
- Trạng thái được lưu lại giữa các lần mở ứng dụng.
- Với Media3, chế độ lặp được đặt ở `Player.REPEAT_MODE_ONE`; tắt lặp sẽ quay lại tự chuyển bài trong playlist.

## Chạy ngoài nền

- File local và URL media trực tiếp vẫn chạy trong `NativeMediaPlaybackService` (`MediaSessionService` + ExoPlayer).
- Bấm Home, chuyển ứng dụng hoặc khóa màn hình vẫn phát; Android cung cấp notification, màn hình khóa và điều khiển tai nghe/Bluetooth.
- YouTube tiếp tục bằng PiP, không biến thành luồng âm thanh ẩn.

## Kiến trúc chính

- `NativeMediaPlaybackService.kt`: ExoPlayer, MediaSession, khôi phục repeat-one.
- `MainActivity.kt`: playlist MediaStore/SAF, điều khiển MediaController, PiP Activity và mở YouTube ngoài ứng dụng.
- `ShellBridge.kt`: cầu nối JavaScript cho chọn file/thư mục, next/previous/repeat và PiP.
- `v13-media.js`: điều phối backend native/YouTube, YouTube IFrame API, repeat-one và trạng thái playlist.
- `AndroidManifest.xml`: khai báo PiP và quyền media theo phiên bản Android.

## Yêu cầu

- `minSdk 24`, `targetSdk 35`, Java 17.
- AndroidX Media3 `1.10.1`.
- Android 8.0 trở lên để dùng Picture-in-Picture.
- Android 13+ cần cho phép thông báo để thấy thanh điều khiển media native.
