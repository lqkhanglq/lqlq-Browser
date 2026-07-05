# Native tabs rewrite — v0.24.0

## Phạm vi đã thay đổi

- Android (`BrowserTabStore`) là nguồn dữ liệu duy nhất cho danh sách thẻ, thẻ đang chọn, URL, tiêu đề và khôi phục phiên thường.
- Bộ chuyển thẻ HTML/JavaScript cũ đã bị xóa; thay bằng `NativeTabSwitcherView` dùng `RecyclerView` lưới hai cột.
- Khi chọn một thẻ đã được khôi phục nhưng chưa có WebView, Android tự tạo WebView và tải URL đã lưu.
- Chỉ giữ tối đa 4 WebView gần đây. Những thẻ khác chỉ giữ metadata và được tải lại khi chọn.
- Đã xóa chụp thumbnail WebView trên UI thread.
- Đóng thẻ, đóng các thẻ khác và đóng tất cả đều xử lý trong native store.
- Website bên ngoài chỉ được cấp `PageToolsBridge.saveTextFile()`, không còn truy cập API quản lý thẻ của shell.

## Dữ liệu phiên

- Phiên thường được lưu trong SharedPreferences `lqlq_native_tabs`.
- Phiên riêng tư chỉ tồn tại trong bộ nhớ và mất khi đóng ứng dụng.
- Danh sách thẻ cũ trong `shieldBrowserPrototypeStateV2` không được khôi phục trong APK để loại bỏ trạng thái “thẻ ma”. Lịch sử, dấu trang và tải xuống cũ vẫn được giữ.

## Kiểm tra đã chạy

- `node --check` cho toàn bộ JavaScript trong `assets/www`: đạt.
- Parse HTML bằng Python `html.parser`: đạt.
- Parse CSS bằng `tinycss2`, không có lỗi cú pháp: đạt.
- `git diff --check`: đạt.
- Biên dịch độc lập và chạy kiểm thử hành vi cho `BrowserTabStore` bằng Kotlin 1.9: đạt.
- Tìm kiếm xác nhận không còn tham chiếu đến `mobileTabsOverlay`, `mobileTabsGrid`, `getTabThumbnail`, `tabThumbnails`, `switchPage()` hoặc `closePage()` trong mã ứng dụng.

## Chưa xác nhận trong môi trường này

Môi trường chỉnh sửa không có Android SDK/Gradle wrapper nên chưa chạy được `assembleDebug` hoặc cài APK lên thiết bị. GitHub Actions của project vẫn là đường build chính; cần kiểm tra thực tế thao tác mở/chuyển/đóng nhiều thẻ trên điện thoại sau khi workflow build xong.
