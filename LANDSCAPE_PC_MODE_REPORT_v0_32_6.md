# lqlq Browser v0.32.6 — Landscape PC mode and Android insets

## Mục tiêu

- Thêm nút quay ngang/chế độ PC ngay trong Menu chức năng web.
- Khi nằm ngang, ẩn thanh trạng thái phía trên để tăng diện tích hiển thị.
- Vẫn giữ Back/Home/Đa nhiệm của Android nhưng trang web phải kết thúc trước vùng nút đó, không bị che.
- Không làm thay đổi bố cục điện thoại dọc vốn đang ổn định.

## Thay đổi

- `index.html`: thêm mục **Quay ngang · chế độ PC** trong phần Cá nhân hóa.
- `app.js`: đổi nhãn động giữa chế độ ngang và dọc; gọi bridge native để khóa hướng.
- `ShellBridge.kt`: thêm `setScreenOrientation(mode)`.
- `MainActivity.kt`:
  - dùng edge-to-edge có kiểm soát cho target SDK 35;
  - dọc: áp dụng đầy đủ status/navigation insets như bố cục cũ;
  - ngang: ẩn riêng status bar, chỉ dùng navigation-bar inset ở đúng cạnh có nút hệ thống;
  - không cộng cutout thành một dải trắng giả ở cạnh trái;
  - khôi phục đúng chính sách sau khi thoát video fullscreen.
- Phiên bản: `0.32.6 (73)`.
