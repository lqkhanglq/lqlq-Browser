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


## v0.29.0 — Hồ sơ Phiêu lưu tùy chọn

- Người chưa tạo hồ sơ vẫn dùng lqlq Browser như trình duyệt bình thường.
- Nhấn khu vực logo/tên trong menu để tạo Hồ sơ Phiêu lưu lưu cục bộ.
- Hồ sơ hỗ trợ biệt danh, 8 avatar mặc định, Linh Thạch, thống kê Shield và bật/tắt hiệu ứng.
- Shield chỉ cộng `+1 Linh Thạch` cho lần chặn điều hướng main-frame hợp lệ sau khi đã có hồ sơ.
- Chống cộng trùng trong 2,2 giây và giới hạn 30 Linh Thạch/ngày; request ảnh/script/iframe không được thưởng.
- Xóa hồ sơ không xóa lịch sử, dấu trang, thẻ hay dữ liệu trình duyệt.
- Chưa có Google/Facebook Login, máy chủ, bán Linh Thạch, nhiệm vụ hoặc cửa hàng trang bị.

## v0.32.0 — LQLQ Dynamic Loot Engine

Project có thêm hệ **Kỳ Vật Vạn Giới động**:

- khi người dùng có Hồ sơ Phiêu lưu và đi tới web mới, app tự quay tỷ lệ rơi;
- thẻ Kỳ Vật có thể lấy từ Cloudflare Worker + Workers AI;
- nếu Worker chưa được cấu hình hoặc lỗi, app tự dùng Wikipedia/Wikimedia;
- ảnh đã thu thập được nén WebP và lưu cục bộ, không đóng sẵn hàng nghìn ảnh vào APK;
- bộ sưu tập hiển thị trong **Vạn Giới Đồ Giám**.

### Cấu hình endpoint khi build

```bash
gradle assembleRelease \
  -PLQLQ_DYNAMIC_LOOT_ENDPOINT="https://lqlq-dynamic-loot.<account>.workers.dev"
```

GitHub Actions đọc repository variable `LQLQ_DYNAMIC_LOOT_ENDPOINT`. Nếu để trống, APK vẫn dùng nguồn Wikipedia/Wikimedia trực tiếp.

Mã Worker nằm trong thư mục `dynamic-loot-worker/`.

## v0.32.6 — Xoay ngang và chế độ PC an toàn

- Menu chức năng có nút **Quay ngang · chế độ PC** và nút quay dọc tương ứng.
- Chế độ ngang ẩn status bar, giữ Back/Home/Đa nhiệm và chừa đúng vùng an toàn Android.
- Nền app phủ vùng cutout/camera để giảm dải đen thừa; nội dung web không nằm dưới các nút hệ thống.
