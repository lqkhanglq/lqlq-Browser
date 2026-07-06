# lqlq Browser HTML Prototype v0.22

## Thanh công cụ điện thoại tự động ẩn

Thanh trình duyệt trên điện thoại giờ hỗ trợ ba chế độ:

```text
Tự động ẩn khi cuộn
Luôn hiển thị
Luôn thu gọn
```

Mở tại:

```text
Menu → Cá nhân hóa → Thanh công cụ điện thoại
```

## Chế độ mặc định: Tự động ẩn khi cuộn

- Vuốt lên khoảng 60 px: thanh trên cùng trượt lên và ẩn.
- Vuốt xuống khoảng 18 px: thanh xuất hiện lại.
- Khi trở về đầu trang: thanh luôn hiện.
- Chuyển thẻ, mở menu, nhập địa chỉ hoặc mở bảng công cụ: thanh luôn hiện.
- Hiệu ứng trượt khoảng 210 ms.

## Vạch kéo

Khi thanh bị ẩn, giữa mép trên màn hình xuất hiện một vạch nhỏ:

```text
            ━
```

Có thể:

- Chạm vào vạch để hiện thanh.
- Kéo vạch xuống để hiện thanh.
- Ở chế độ Luôn thu gọn, thanh tự ẩn lại sau vài giây.

## Không gian hiển thị

Thanh được đổi thành lớp cố định. Khi ẩn:

- Nội dung website mở rộng lên phía trên.
- Không để lại khoảng trống lớn.
- Vẫn hỗ trợ vùng an toàn của Android.
- Menu luôn mở phía trên website và không bị che.

## Cầu nối APK/WebView

Khi website thật nằm trong một WebView riêng, Android có thể chuyển trạng thái cuộn
sang HTML bằng:

```javascript
window.LqlqMobileToolbar.onPageScroll(scrollY)
window.LqlqMobileToolbar.onPageScrollDelta(deltaY)
window.LqlqMobileToolbar.onPageGesture("up")
window.LqlqMobileToolbar.onPageGesture("down")
```


## v0.23 — Icon lqlq Browser

Đã thêm icon chính thức do người dùng cung cấp.

Các vị trí quan trọng:

```text
assets/icons/lqlq-browser-icon-original-1254.png
assets/icons/lqlq-browser-icon-transparent-1254.png
manifest.webmanifest
favicon.ico
android-icon-pack/
CLAUDE_BUILD_HANDOFF.md
```

Icon đã được nối vào HTML dưới dạng favicon, Apple touch icon và PWA icon.
Bộ kích thước launcher Android cũng đã được tạo sẵn để Claude chuyển prototype
thành project build APK.

## v0.27 — YouTube PiP và playlist media

- YouTube đang phát tự chuyển sang Picture-in-Picture khi bấm Home (Android 8+).
- File MP3/MP4 có thể tạo playlist theo thư mục và tự chuyển bài.
- Có Bài trước, Bài tiếp theo và Lặp lại một bài.
- Có nút chọn cả thư mục bằng trình chọn tài liệu Android.
- Playlist YouTube dùng hàng đợi chính thức của trình nhúng YouTube.

## v0.29 — Hồ sơ Phiêu lưu tùy chọn

- Nhấn logo/tên ở đầu menu để tạo hồ sơ lưu trên thiết bị.
- Khi chưa tạo hồ sơ, lqlq Browser và Shield hoạt động như trước, không cộng Linh Thạch.
- Sau khi tạo hồ sơ, một lần chặn điều hướng main-frame hợp lệ nhận +1 Linh Thạch.
- Giới hạn 30 Linh Thạch/ngày và chống cộng trùng cùng tab/domain trong 2,2 giây.
- Có 8 avatar, chỉnh biệt danh, thống kê bảo vệ, bật/tắt hiệu ứng và xóa riêng hồ sơ.
- Trang bị, nhiệm vụ, Google Login và Play Billing chưa nằm trong bản này.
