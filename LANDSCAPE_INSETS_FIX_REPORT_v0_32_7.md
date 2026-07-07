# LANDSCAPE INSETS FIX — v0.32.7

## Nguyên nhân

Bản v0.32.6 ép edge-to-edge cho cả dọc và ngang rồi cộng status/cutout/navigation inset vào padding của root. Trên thiết bị thử nghiệm, chế độ dọc bị cộng status inset lần hai nên xuất hiện dải trắng ngang và toàn bộ toolbar bị đẩy xuống. Ở ngang, `cutout.left` tạo dải trắng lớn bên trái.

## Cách sửa

- Dọc: `setDecorFitsSystemWindows(true)`, root padding bằng 0, trở lại hành vi ổn định của v0.32.5.
- Ngang: `setDecorFitsSystemWindows(false)`, ẩn status bar, chỉ dùng `navigationBars()` để chừa cạnh có Back/Home/Đa nhiệm.
- Không dùng `displayCutout()` làm padding; giữ `SHORT_EDGES` để nền/WebView phủ cạnh camera.
- Cộng khe 6dp ở cạnh navigation bar để nút menu web không sát hoặc bị che.

## Phiên bản

- `versionCode 74`
- `versionName 0.32.7`
