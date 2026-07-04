# lqlq Browser — Android APK (v0.23)

Chuyển từ HTML prototype v0.23 sang project Android hoàn chỉnh, build APK trực tiếp trên GitHub Actions.

## Cách build trên GitHub

1. Tạo repository mới trên GitHub (Public hoặc Private đều được).
2. Tải toàn bộ thư mục này lên repo (giữ nguyên cấu trúc, **bao gồm cả thư mục ẩn `.github/`**).
3. Vào tab **Actions** → chọn workflow **Build lqlq Browser APK** → bấm **Run workflow** (hoặc chỉ cần push code là tự chạy).
4. Chờ build xong (~5-10 phút) → mở lần chạy → kéo xuống mục **Artifacts** → tải `lqlq-browser-apk`.
5. Trong file zip tải về có 2 APK:
   - `lqlq-browser-v0.23-debug.apk`
   - `lqlq-browser-v0.23-release.apk` ← nên cài bản này.

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
- **WebView trang web:** mỗi thẻ là một WebView native riêng, nằm ngay dưới thanh công cụ HTML (chiều cao thanh do `android-glue.js` báo qua `LqlqAndroid.setToolbarHeight`).
- **Cầu nối JavaScript:**
  - `LqlqTtsBridge` — đúng hợp đồng trong `ANDROID_TTS_BRIDGE_SPEC.md` (ưu tiên `com.google.android.tts`, danh sách giọng từ `textToSpeech.voices`, gọi lại `LqlqReader.onNativeUtteranceDone/Error`).
  - `LqlqPageBridge` — đúng hợp đồng trong `ANDROID_PAGE_BRIDGE_SPEC.md` kiểu A: inject thuật toán trích chương vào WebView trang web, trả JSON chương cho nút "Lấy chương đang mở" của Đọc truyện TXT và Chapter Clipper.
  - `LqlqAndroid` — điều khiển thẻ, báo trạng thái đọc/nhạc cho thông báo, lưu tệp (TXT, ảnh đã chỉnh...) vào thư mục Download.
  - Cả 3 bridge **chỉ gắn vào WebView giao diện**, không lộ cho website bên ngoài (đúng mục "An toàn" trong spec).
- **`android-glue.js`** (script cuối trong `index.html`): nối `navigate/newTab/closeTab/switchTab` của giao diện với WebView thật, đồng bộ thanh địa chỉ + lịch sử, theo dõi trạng thái Reader/Media để cập nhật thông báo, chuyển link tải `blob:`/`data:` thành lưu file thật. Mở bằng trình duyệt máy tính thì file này tự tắt, bản web vẫn chạy như cũ.

## Icon

Dùng đúng bộ icon trong `android-icon-pack/res/` của gói v0.23 (adaptive icon + legacy PNG đầy đủ các mật độ), label `lqlq Browser`. Ảnh gốc 1254px vẫn nằm trong repo tại `art/` để tái tạo sau này.

## Ghi chú

- Nhạc từ **YouTube embed** không điều khiển được từ thanh thông báo (iframe của YouTube không cho đọc trạng thái); nhạc/video từ tệp trong máy và link trực tiếp thì đầy đủ.
- Nút "Tạm dừng" TTS: Android TextToSpeech không hỗ trợ pause thật, nên khi bấm "Tiếp tục" sẽ đọc lại **từ đầu câu hiện tại** (không mất nội dung).
- `minSdk 24` (Android 7.0) → `targetSdk 35`.
