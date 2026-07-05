# lqlq Browser v0.25.0 — Audit và tối ưu Android

## Mục tiêu

- Làm cho **Lưu trang hiện tại / Trang đã lưu** hoạt động ổn định.
- Loại bỏ giật khi tạo thẻ mới và khi mở danh sách trang đã lưu.
- Giữ hệ thống thẻ native v0.24, đồng thời rà lại luồng cache, WebView, lịch sử, lưu ngoại tuyến, quyền Android và các mục menu.

## Nguyên nhân lỗi đã xác định

1. Nút lưu trước đây có thể đọc `activeTab()` bên JavaScript trong lúc bản sao tab chưa kịp đồng bộ với WebView native.
2. Sáu lối tắt mặc định từng bị trộn vào `profile.bookmarks`, làm “Trang đã lưu” không phản ánh đúng dữ liệu người dùng.
3. Trang thẻ mới và Nhật ký từng tạo URL `/favicon.ico`, dẫn đến nhiều request mạng cùng lúc.
4. Favicon dạng base64 bị lưu trực tiếp trong localStorage. Nhiều mục có thể làm state lớn, parse chậm hoặc đầy quota; khi quota đầy, thao tác lưu tiếp theo thất bại.
5. Callback tải trang dựng lại toàn bộ dải tab desktop và ghi localStorage quá nhiều lần.
6. Getter của TabStore vẫn serialize toàn bộ phiên thẻ vào SharedPreferences ngay cả khi không có thay đổi.
7. Shell nội bộ chạy bộ quét quảng cáo toàn DOM theo chu kỳ 1,8 giây dù shell không chứa nội dung web bên ngoài.
8. Lưu MHT cũ có nguy cơ đọc toàn bộ file vào RAM; mở MHT phải copy lại mỗi lần.
9. Guard chuyển domain chặn cả liên kết thật do người dùng bấm, không giống hành vi trình duyệt thông thường.
10. App xin quyền đọc toàn bộ ảnh/video/âm thanh dù các luồng chọn tệp đã dùng Storage Access Framework.

## Thay đổi chính

### Trang đã lưu và thẻ mới

- `getActivePageSnapshot()` lấy URL, tiêu đề, trạng thái hiển thị từ TabStore native.
- Chỉ cho lưu khi một trang web thật đang hiển thị; không vô tình lưu tab cũ khi đang ở trang chủ trống.
- Chuẩn hóa URL không còn chuyển toàn bộ path/query sang chữ thường, tránh gộp nhầm các URL phân biệt hoa/thường.
- Tách `DEFAULT_SHORTCUTS` khỏi bookmark thật.
- Migration schema v26:
  - xóa các mục mặc định cũ khỏi bookmark;
  - khử trùng lặp;
  - bỏ favicon base64 khỏi localStorage;
  - giữ URL, tiêu đề, URI ngoại tuyến và thời điểm lưu.
- Lưới trang chủ chỉ dựng lại khi dữ liệu thật sự đổi.
- Favicon hiển thị bằng chữ/logo CSS ngay lập tức; favicon native chỉ được lấy từ cache cục bộ khi phần tử sắp đi vào viewport qua `IntersectionObserver`.
- Không phát request `/favicon.ico` ở trang chủ, Trang đã lưu hay Nhật ký.

### Cache favicon native

- Thêm `FaviconStore.kt`.
- Nhận icon từ `WebChromeClient.onReceivedIcon()` sau khi trang đã tải.
- Thu nhỏ tối đa 64 px, giới hạn 40 KB/icon, 96 file trên đĩa và 48 mục RAM.
- File cache dọn theo LRU gần đúng và không tải mạng riêng.
- Callback UI chỉ đọc cache RAM; đọc file chỉ thực hiện qua bridge/luồng không phải UI.

### localStorage và Nhật ký

- Khi state vượt quota, tự thu gọn Nhật ký/Tải xuống và bỏ favicon cũ rồi thử lưu lại.
- Trong APK, `navigate()` không ghi Nhật ký ngay lúc bắt đầu tải. Chỉ URL cuối cùng từ `onPageFinished()` được ghi, tránh bản ghi trùng và URL redirect trung gian.
- Callback `loading=true` không còn ghi localStorage.
- Callback trang chỉ cập nhật `renderPage()` và toolbar, không dựng lại toàn bộ tab strip.

### Lưu/mở trang ngoại tuyến

- Chặn bấm lưu lặp trong khi một tác vụ MHT đang chạy.
- Chụp URL/tiêu đề nguồn trước khi `saveWebArchive()` chạy, nên đổi tab giữa chừng không gắn file vào sai bookmark.
- Stream file MHT trực tiếp sang Downloads/MediaStore, không `readBytes()` toàn bộ MHT vào RAM.
- Android 10+ dùng `IS_PENDING`, chỉ công bố file sau khi ghi thành công; file lỗi được xóa.
- Mở MHT qua cache `offline_open_v1`, tối đa 8 mục; lần mở tiếp theo không copy lại.
- Nếu file offline bị xóa, tự mở URL online tương ứng và báo rõ.

### WebView, thẻ và bộ nhớ

- `WebSettings.LOAD_DEFAULT` để WebView dùng HTTP cache tiêu chuẩn.
- Bật Safe Browsing trên Android 8+.
- `allowFileAccess=false` mặc định; chỉ bật cho WebView đang mở MHT local.
- Máy RAM thấp giữ tối đa 2 renderer trang; máy thường tối đa 4.
- Khi Android báo thiếu bộ nhớ, hủy mọi WebView nền và giữ tab metadata để tải lại.
- `BrowserTabStore.ensureModeHasTab()` chỉ ghi SharedPreferences khi thật sự sửa dữ liệu.
- Bộ chuyển thẻ native chỉ nhận `submitTabs()` khi đang mở; khi ẩn không còn bind RecyclerView trong từng callback tải trang.

### Điều hướng và giao diện Android

- Host quảng cáo đã biết vẫn bị chặn cứng.
- Liên kết main-frame có gesture được phép chuyển domain và đi theo redirect ngắn; chuyển hướng tự phát ngoài luồng gesture vẫn bị guard chặn.
- Thanh trạng thái và thanh điều hướng Android đổi sáng/tối theo chủ đề app.
- Bỏ quyền READ_MEDIA_AUDIO/VIDEO/IMAGES và READ_EXTERNAL_STORAGE không cần thiết.
- Nút In dùng `PrintManager` Android.
- Nút mở trang ngoại tuyến gọi đúng trình chọn file native.
- Các mục desktop chưa có backend Android (Extensions, Task Manager, DevTools, Tab groups) được ẩn trong APK thay vì để nút bấm không phản hồi.
- Shell APK không còn chạy bộ quét quảng cáo DOM theo chu kỳ; website thật vẫn dùng lớp chặn native/content-script riêng.

## Chức năng đã rà lại

- Tạo/chọn/đóng thẻ và khôi phục phiên thường.
- Bộ đệm WebView, renderer chết và memory trim.
- Thanh địa chỉ, chuyển hướng, reload, back và home.
- Lưu bookmark, cập nhật bookmark trùng URL, xóa và mở lại.
- Lưu MHT, mở MHT/HTML và fallback online.
- Nhật ký, tải xuống, file chooser và PrintManager.
- Đồng bộ giao diện sáng/tối giữa shell và native tab switcher/system bars.
- TTS, media nền, Chapter Clipper và image tools được giữ nguyên API hiện có; các thay đổi không thay bridge của chúng.

## Kiểm tra đã chạy

- `node --check` cho toàn bộ JavaScript trong `www/` và `www/tools/`.
- Parse toàn bộ XML resources và AndroidManifest.
- Kiểm tra `index.html`: 255 ID duy nhất, không trùng; mọi script/CSS/image local được tham chiếu đều tồn tại.
- Unit test Node cho migration bookmark, xóa shortcut mặc định, bỏ favicon base64 và cập nhật bookmark bằng snapshot native.
- Kiểm tra cân bằng lexical toàn bộ Kotlin và chạy Kotlin parser đến bước semantic resolution; không có lỗi cú pháp.
- Quét các mục `data-action`; mục có backend được nối, mục desktop không có backend được ẩn trên APK.

## Giới hạn kiểm thử

Môi trường sửa không có Android SDK/Gradle distribution và không có thiết bị Android, nên chưa chạy được `assembleDebug`, instrumentation test hoặc thao tác cảm ứng thật. GitHub Actions của project vẫn build cả debug/release bằng JDK 17 + Gradle 8.7. Sau khi build, nên kiểm tra nhanh trên điện thoại: lưu 3–5 trang, tạo liên tục 10 thẻ mới, mở/đóng Trang đã lưu, lưu một MHT, đóng app rồi mở lại file đó.

Ứng dụng dùng Android WebView nên có thể mô phỏng kiến trúc/UI trình duyệt và dùng cùng nền Chromium của WebView, nhưng không thể trở thành toàn bộ Chrome/Chromium chỉ bằng lớp app. Các mục desktop không có backend đã được ẩn để giao diện không hứa chức năng giả.
