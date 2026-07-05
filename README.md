# lqlq Browser — Android APK (v0.26.0)

Project Android của lqlq Browser. Bản v0.26.0 giữ toàn bộ thay đổi của v0.25 và làm lại **Nhạc và video nền** bằng trình phát Android native.

## Cách build bằng GitHub Actions

1. Đưa toàn bộ thư mục project lên repository GitHub, bao gồm `.github/` và `keystore/`.
2. Mở **Actions** → **Build lqlq Browser APK** → **Run workflow**.
3. Tải artifact `lqlq-browser-apk` sau khi build xong.
4. Gói artifact chứa:
   - `lqlq-browser-v0.26.0-debug.apk`
   - `lqlq-browser-v0.26.0-release.apk`

Bản release vẫn dùng keystore ổn định của project nên có thể cài đè lên bản cũ cùng chữ ký.

## Nhạc và video nền v0.26.0

### Nguồn phát native

- File trong máy: MP3, M4A, AAC, OGG, WAV, FLAC, MP4, WEBM, MKV, MOV và 3GP.
- URL media trực tiếp: các liên kết `.mp3`, `.mp4`, `.webm` và định dạng tương ứng.
- Tệp được chọn qua Android Storage Access Framework; ứng dụng không cần quyền đọc toàn bộ kho ảnh/nhạc/video.
- MP4 và các file video được dùng như nguồn âm thanh nền khi ứng dụng bị thu nhỏ hoặc màn hình bị khóa.

### Chạy ngoài nền

- Player và `MediaSession` sống trong `NativeMediaPlaybackService`, không nằm trong WebView hay `MainActivity`.
- Khi bấm Home, chuyển ứng dụng, tắt màn hình hoặc vuốt Activity khỏi màn hình gần đây trong lúc đang phát, service tiếp tục sở hữu player.
- Android tự hiện thông báo MediaStyle với tiêu đề, nút phát/tạm dừng, tua và dừng; nút tai nghe/Bluetooth và màn hình khóa điều khiển cùng MediaSession.
- Khi mở lại ứng dụng, giao diện nối lại MediaController và khôi phục đúng trạng thái đang phát mà không mở lại tệp.
- Nút **Dừng** xóa playlist, nhờ đó notification native được gỡ đúng cách.

### YouTube

YouTube tiếp tục dùng iframe nhúng chính thức. Bản này không trích xuất luồng âm thanh/video từ YouTube, nên không cam kết phát YouTube khi Activity bị hệ thống tạm dừng. Phát nền ổn định áp dụng cho file local và URL media trực tiếp do Media3 xử lý.

## Kiến trúc chính

- `NativeMediaPlaybackService.kt`: `MediaSessionService` + `ExoPlayer`.
- `MainActivity.kt`: kết nối bằng `MediaController`, mở `ACTION_OPEN_DOCUMENT`, chuyển lệnh từ UI tới service và gửi state về shell.
- `ShellBridge.kt`: API JavaScript riêng cho mở file, phát URL, play/pause/stop và volume; ngăn `PlaybackService` cũ tạo notification media trùng.
- `v13-media.js`: phân tách rõ backend `native` và `web`; Android không còn dùng `URL.createObjectURL()` cho `content://`.
- `PlaybackService.kt`: vẫn giữ nguyên cho Đọc truyện TXT và fallback media WebView/YouTube.

## Các phần từ v0.25 vẫn được giữ

- Hệ thống thẻ native với `BrowserTabStore` là nguồn dữ liệu duy nhất.
- Cache tối đa 2 WebView trên máy RAM thấp và 4 WebView trên máy thường.
- Dấu trang/Trang đã lưu đã tách khỏi shortcut mặc định.
- Favicon cache cục bộ, không tải hàng loạt khi mở thẻ mới.
- Lưu trang ngoại tuyến bằng MHT, ghi stream thẳng vào Downloads.
- Safe Browsing, cache WebView mặc định và quyền tệp theo Storage Access Framework.

## Yêu cầu

- `minSdk 24`, `targetSdk 35`, Java 17.
- AndroidX Media3 `1.10.1`.
- Quyền thông báo cần được cho phép trên Android 13+ để thấy thanh điều khiển trong notification.
