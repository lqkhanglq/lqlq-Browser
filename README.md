# lqlq Browser — Android APK (v0.25)

Project Android của lqlq Browser, build APK trực tiếp bằng GitHub Actions. Bản v0.25 giữ hệ thống thẻ native và làm lại Dấu trang/Trang đã lưu, cache favicon, lưu ngoại tuyến và các điểm nghẽn hiệu năng của shell Android.

## Cách build trên GitHub

1. Tạo repository mới trên GitHub (Public hoặc Private đều được).
2. Tải toàn bộ thư mục này lên repo (giữ nguyên cấu trúc, **bao gồm cả thư mục ẩn `.github/`**).
3. Vào tab **Actions** → chọn workflow **Build lqlq Browser APK** → bấm **Run workflow** (hoặc chỉ cần push code là tự chạy).
4. Chờ build xong (~5-10 phút) → mở lần chạy → kéo xuống mục **Artifacts** → tải `lqlq-browser-apk`.
5. Trong file zip tải về có 2 APK:
   - `lqlq-browser-v0.25.0-debug.apk`
   - `lqlq-browser-v0.25.0-release.apk` ← nên cài bản này.

**Chữ ký ổn định:** repo có sẵn `keystore/lqlq-release.keystore`, mọi lần build đều ký cùng một chữ ký, nên cài bản mới đè lên bản cũ được, không phải gỡ ra cài lại.

## 3 vấn đề của bản cũ đã được xử lý

### 1. Đọc TXT và phát nhạc có thanh thông báo, chạy nền

- `PlaybackService` là foreground service kiểu `mediaPlayback`, hiện thanh thông báo media giống T2S: tên truyện/bài nhạc, câu đang đọc, nút **Câu trước / Phát-Tạm dừng / Câu sau / Đóng**.
- Khi chuyển sang ứng dụng khác hoặc tắt màn hình, TTS và nhạc vẫn tiếp tục (service + wake lock + không tạm dừng WebView khi ra nền).
- Quyền thông báo (`POST_NOTIFICATIONS`) được hỏi ngay lần mở đầu tiên trên Android 13+.

### 2. Quyền bộ nhớ, âm thanh — sửa lỗi không có tiếng

Nguyên nhân thường gặp của lỗi "mở nhạc / đọc TXT không có tiếng" ở bản cũ và cách bản này xử lý:

- **WebView chặn tự phát media** → đã đặt `mediaPlaybackRequiresUserGesture = false`.
- **Không có trình chọn tệp** → `onShowFileChooser` đã được cài, nên nút "Mở tệp TXT" và chọn nhạc/video trong máy hoạt động.
- **TTS phát sai kênh / âm lượng 0** → TTS dùng `AudioAttributes USAGE_MEDIA`, truyền âm lượng từ thanh trượt của giao diện, nút âm lượng vật lý điều khiển đúng kênh media.
- **Engine Google TTS không có** → tự chuyển sang engine mặc định của máy thay vì im lặng.
- Quyền `READ_MEDIA_AUDIO/VIDEO/IMAGES` (Android 13+) và `READ_EXTERNAL_STORAGE` (Android 12 trở xuống) được khai báo và hỏi khi mở app.

### 3. Bỏ chế độ vuốt ẩn/hiện thanh công cụ

Chế độ "Tự động ẩn khi cuộn" trong v22 đã được **gỡ bỏ** vì gây giật khi cuộn trên một số trang web. Mặc định giờ là **Luôn hiển thị**; vẫn còn tùy chọn "Luôn thu gọn" nếu muốn nhiều không gian màn hình. Cài đặt "auto" cũ lưu trong máy sẽ tự chuyển về "Luôn hiển thị".

## Kiến trúc

- **WebView giao diện (shell):** tải `assets/www/index.html` qua `WebViewAssetLoader` (origin `https://appassets.androidapp.com` — secure context nên `crypto.randomUUID`, `mediaSession`... hoạt động đầy đủ).
- **Hệ thống thẻ native:** `BrowserTabStore` là nguồn dữ liệu duy nhất cho danh sách thẻ, thẻ đang chọn, URL và tiêu đề. JavaScript chỉ giữ một bản sao để cập nhật thanh địa chỉ và số đếm.
- **Bộ chuyển thẻ:** `NativeTabSwitcherView` là giao diện Android native dạng lưới hai cột, không còn phụ thuộc overlay HTML, MutationObserver hoặc z-index của shell WebView.
- **WebView trang web:** chỉ giữ tối đa 4 WebView gần đây trong bộ nhớ. Khi chọn lại một thẻ đã bị giải phóng, WebView được tạo lại và nạp URL đã lưu. Cách này tránh giữ hàng chục renderer cùng lúc.
- **Cầu nối JavaScript:**
  - `LqlqTtsBridge` — đúng hợp đồng trong `ANDROID_TTS_BRIDGE_SPEC.md` (ưu tiên `com.google.android.tts`, danh sách giọng từ `textToSpeech.voices`, gọi lại `LqlqReader.onNativeUtteranceDone/Error`).
  - `LqlqPageBridge` — đúng hợp đồng trong `ANDROID_PAGE_BRIDGE_SPEC.md` kiểu A: inject thuật toán trích chương vào WebView trang web, trả JSON chương cho nút "Lấy chương đang mở" của Đọc truyện TXT và Chapter Clipper.
  - `LqlqAndroid` — gọi API thẻ native, báo trạng thái đọc/nhạc cho thông báo, lưu tệp (TXT, ảnh đã chỉnh...) vào thư mục Download.
  - Website bên ngoài chỉ nhận `PageToolsBridge` tối thiểu với `saveTextFile()` để Chapter Clipper xuất TXT; API quản lý thẻ và các quyền đặc biệt chỉ tồn tại trong shell.
- **`android-glue.js`** (script cuối trong `index.html`): đồng bộ bản sao trạng thái thẻ từ native sang giao diện, nối điều hướng với WebView thật, cập nhật thanh địa chỉ + lịch sử, theo dõi trạng thái Reader/Media và xử lý lưu tệp. Mở bằng trình duyệt máy tính thì file này tự tắt.

## Icon

Dùng đúng bộ icon trong `android-icon-pack/res/` của gói v0.23 (adaptive icon + legacy PNG đầy đủ các mật độ), label `lqlq Browser`. Ảnh gốc 1254px vẫn nằm trong repo tại `art/` để tái tạo sau này.

## Ghi chú

- Nhạc từ **YouTube embed** không điều khiển được từ thanh thông báo (iframe của YouTube không cho đọc trạng thái); nhạc/video từ tệp trong máy và link trực tiếp thì đầy đủ.
- Nút "Tạm dừng" TTS: Android TextToSpeech không hỗ trợ pause thật, nên khi bấm "Tiếp tục" sẽ đọc lại **từ đầu câu hiện tại** (không mất nội dung).
- `minSdk 24` (Android 7.0) → `targetSdk 35`.
