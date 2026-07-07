# lqlq Browser v0.32.6 — Xoay ngang và vùng an toàn Android

## Đã sửa

- Thêm mục **Quay ngang · chế độ PC** vào menu chức năng web.
- Khi đang ngang, mục này đổi thành **Quay dọc · chế độ điện thoại**.
- Gọi native Android để ép dọc/ngang, không phụ thuộc công tắc tự xoay của trình duyệt web.
- Giao diện web tiếp tục tự chuyển sang breakpoint PC khi chiều rộng màn hình đủ lớn.
- Ở chế độ ngang thường, ẩn thanh trạng thái phía trên.
- Giữ thanh điều hướng Back/Home/Đa nhiệm của Android.
- Áp dụng WindowInsets cho status bar, navigation bar, bàn phím và display cutout để nút/nội dung không bị che.
- Cho nền cửa sổ phủ vùng camera/cutout ở cạnh ngang, giảm dải đen thừa nhưng vẫn giữ nội dung tương tác trong vùng an toàn.
- Video toàn màn hình vẫn ẩn toàn bộ system bars như trước; khi thoát video, app tự trở về quy tắc dọc/ngang phù hợp.
- Điều chỉnh vị trí Linh Thạch, Linh Thú và Kỳ Vật theo kích thước vùng nội dung sau khi trừ insets.

## Phiên bản

- `versionCode 73`
- `versionName 0.32.6`
