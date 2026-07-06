# Báo cáo triển khai lqlq Browser v0.29.0

## 1. Phạm vi

Đã triển khai **Pass A–E** của kế hoạch Hồ sơ Phiêu lưu trên project người dùng cung cấp.

File đầu vào:

```text
lqlq-browser-android-v0_23_1(1).zip
SHA-256: bad3cad38e97711630df1c5bcbea5d0d5da68caad150a2770e3ace10f6c20abf
```

Project bên trong file đầu vào thực tế đang ở:

```text
versionName = 0.28.1
versionCode = 65
```

Bản triển khai mới đã nâng thành:

```text
versionName = 0.29.0
versionCode = 66
```

Không reset Git, không xóa lịch sử Git và không ghi đè các thay đổi chưa commit đã có sẵn trong ZIP đầu vào.

---

## 2. Chức năng đã hoàn thành

### 2.1. Trạng thái Khách — trình duyệt giữ nguyên

Khi người dùng chưa tạo Hồ sơ Phiêu lưu:

- Menu vẫn hiện `lqlq Browser` và biểu tượng Shield.
- Shield, chặn quảng cáo và chặn chuyển hướng vẫn hoạt động như trước.
- Không cộng Linh Thạch.
- Không ghi tiến trình Phiêu lưu.
- Không có hiệu ứng ngọc.
- Không ép đăng ký hoặc đăng nhập.
- Các chức năng trình duyệt, đọc truyện, media và chỉnh ảnh không bị khóa.

### 2.2. Tạo Hồ sơ Phiêu lưu offline

Nhấn khu vực logo/tên ở đầu menu để mở giao diện Hồ sơ Phiêu lưu.

Người dùng có thể:

- Đặt biệt danh từ 2–20 ký tự.
- Chọn một trong 8 avatar mặc định.
- Tạo hồ sơ lưu cục bộ trên thiết bị.
- Chỉnh lại biệt danh và avatar.
- Xóa riêng Hồ sơ Phiêu lưu.

Giao diện nói rõ hồ sơ được lưu trên thiết bị và không bắt buộc đăng nhập.

### 2.3. Header cá nhân hóa

Sau khi tạo hồ sơ, khu vực đầu menu chuyển thành:

```text
[Avatar] Biệt danh
         ◆ N Linh Thạch · Đã bảo vệ M lần
```

Nhấn vào khu vực này sẽ mở bảng Hồ sơ Phiêu lưu. Khi đóng bảng, ứng dụng trở lại giao diện trình duyệt bình thường.

### 2.4. Linh Thạch từ Shield

Linh Thạch chỉ được cộng khi thỏa mãn toàn bộ điều kiện:

- Người dùng đã tạo Hồ sơ Phiêu lưu.
- Shield chặn một điều hướng `main-frame` hợp lệ.
- Sự kiện là domain quảng cáo đã biết hoặc chuyển hướng sang domain gốc khác.
- Sự kiện không bị nhận diện là callback trùng.
- Người dùng chưa đạt giới hạn thưởng trong ngày.

Mỗi lần đủ điều kiện:

```text
+1 Linh Thạch
```

Toast native được bổ sung nội dung `+1 Linh Thạch`, đồng thời giao diện shell nhận sự kiện để cập nhật hồ sơ và chạy hiệu ứng thu hoạch.

### 2.5. Chống cộng ngọc sai hoặc cộng hàng loạt

Đã áp dụng các giới hạn sau:

- Chỉ điều hướng `main-frame` được thưởng.
- Ảnh, script, iframe, tracker và request quảng cáo nền không được thưởng.
- Cùng `tab + host` bị chống trùng trong 2,2 giây.
- Giới hạn nhận thưởng: **30 Linh Thạch/ngày**.
- Sau khi đạt giới hạn, tổng lượt Shield bảo vệ vẫn tăng nhưng Linh Thạch không tăng.

### 2.6. Bảng Hồ sơ Phiêu lưu

Bảng hồ sơ hiện có:

- Avatar và biệt danh.
- Tổng Linh Thạch.
- Tổng lượt Shield bảo vệ hợp lệ.
- Tiến độ thu hoạch hằng ngày, ví dụ `8/30 Linh Thạch`.
- Công tắc bật/tắt hiệu ứng nhận Linh Thạch.
- Chỉnh sửa hồ sơ.
- Xóa hồ sơ.
- Thẻ thông báo rằng Trang bị và Nhiệm vụ sẽ được làm ở lượt sau.

### 2.7. Xóa hồ sơ an toàn

Xóa Hồ sơ Phiêu lưu chỉ xóa:

- Biệt danh.
- Avatar.
- Linh Thạch.
- Thống kê Shield của hồ sơ.
- Cài đặt hiệu ứng hồ sơ.

Không xóa:

- Lịch sử duyệt web.
- Dấu trang.
- Thẻ đang mở.
- Trang ngoại tuyến.
- Cài đặt trình duyệt.
- Dữ liệu đọc truyện và media hiện có.

---

## 3. Kiến trúc triển khai

### 3.1. Native Android là nguồn dữ liệu duy nhất

Dữ liệu hồ sơ được lưu trong:

```text
AdventureProfileStore.kt
```

Dùng `SharedPreferences` riêng:

```text
lqlq_adventure_profile_v1
```

Các trường chính:

```text
profile_created
nickname
avatar_id
crystals
total_shield_protects
rewarded_today
reward_day
effects_enabled
created_at
```

JavaScript không tự cộng ngọc và không lưu số dư vào `localStorage`.

### 3.2. Bridge riêng, chỉ gắn vào shell

Bridge mới:

```text
AdventureProfileBridge.kt
```

Tên JavaScript interface:

```text
window.LqlqAdventure
```

Bridge chỉ được gắn vào WebView giao diện nội bộ. Nó không được gắn vào WebView của website bên ngoài.

### 3.3. API giao diện

Script mới:

```text
v29-adventure-profile.js
```

Đối tượng giao diện công khai:

```javascript
window.LqlqAdventureUI
```

Các callback chính:

```javascript
applyNativeState(state)
onShieldProtection(result)
openProfile()
closeProfile()
refreshFromNative()
```

### 3.4. Nút Back Android

`adventureProfileOverlay` đã được thêm vào danh sách overlay của `android-glue.js`. Nút Back trên điện thoại sẽ đóng bảng Hồ sơ Phiêu lưu trước khi quay lại trang web.

---

## 4. File mới

```text
LQLQ_ADVENTURE_PROFILE_IMPLEMENTATION_PLAN.md
LQLQ_ADVENTURE_PROFILE_IMPLEMENTATION_REPORT_v0_29.md
app/src/main/java/com/lqlq/browser/AdventureProfileStore.kt
app/src/main/java/com/lqlq/browser/AdventureProfileBridge.kt
app/src/main/assets/www/v29-adventure-profile.js
app/src/main/assets/www/CHANGELOG_v0_29.txt
```

## 5. File được sửa cho tính năng này

```text
README.md
app/build.gradle.kts
app/src/main/java/com/lqlq/browser/MainActivity.kt
app/src/main/assets/www/index.html
app/src/main/assets/www/styles.css
app/src/main/assets/www/android-glue.js
app/src/main/assets/www/README.txt
```

Không sửa logic của các module đọc truyện, media, PiP, chỉnh ảnh hoặc quản lý thẻ ngoài phần cần nối overlay và Shield.

---

## 6. Kiểm tra đã thực hiện

### PASS — cú pháp JavaScript

Đã chạy `node --check` cho:

```text
v29-adventure-profile.js
android-glue.js
app.js
```

Kết quả: không có lỗi cú pháp.

### PASS — cấu trúc HTML

- Parse được `index.html`.
- Tổng cộng 296 ID.
- Không có ID bị trùng.

### PASS — biên dịch riêng hai lớp Kotlin mới

Đã biên dịch `AdventureProfileStore.kt` và `AdventureProfileBridge.kt` bằng Kotlin compiler với Android API stubs tối thiểu.

Kết quả: biên dịch thành công.

### PASS — kiểm thử logic kho hồ sơ

Đã chạy kiểm thử logic với SharedPreferences giả lập:

- Hồ sơ ban đầu không tồn tại.
- Không có hồ sơ thì Shield không thưởng.
- Tên 1 ký tự bị từ chối.
- Tên có nhiều khoảng trắng được chuẩn hóa.
- Tạo hồ sơ thành công.
- Ghi 31 lượt bảo vệ trong một ngày:
  - Tổng lượt bảo vệ = 31.
  - Linh Thạch = 30.
  - Thưởng hôm nay = 30/30.
- Chỉnh tên/avatar thành công.
- Tắt hiệu ứng thành công.
- Xóa hồ sơ trả về trạng thái Khách.

Kết quả: `AdventureProfileStore logic test OK`.

### Chưa chạy được — full Android Gradle build

Môi trường xử lý hiện tại không có Android SDK, không có Gradle cài sẵn và không có kết nối mạng để tải chúng. Vì vậy chưa thể chạy:

```text
gradle assembleDebug assembleRelease
```

Workflow GitHub Actions có sẵn trong project vẫn dùng Gradle 8.7 để build. Cần đẩy ZIP/project này lên GitHub và chạy workflow để xác nhận build APK thật.

---

## 7. Checklist test APK trên điện thoại

Sau khi GitHub Actions build thành công, nên kiểm tra theo thứ tự:

1. Cài đè lên bản cũ và xác nhận lịch sử/thẻ/dấu trang còn nguyên.
2. Mở menu khi chưa có hồ sơ:
   - Vẫn thấy `lqlq Browser`.
   - Không có Linh Thạch.
3. Nhấn logo/tên:
   - Bảng tạo Hồ sơ Phiêu lưu xuất hiện.
4. Nhập biệt danh và chọn avatar:
   - Header đổi sang avatar + tên + `0 Linh Thạch`.
5. Đóng và mở lại ứng dụng:
   - Hồ sơ vẫn còn.
6. Truy cập trang có chuyển hướng quảng cáo thật:
   - Trang rác bị chặn.
   - Toast có `+1 Linh Thạch`.
   - Số Linh Thạch và tổng lượt bảo vệ tăng đúng 1.
7. Gây cùng redirect lặp nhanh:
   - Không bị cộng hai lần trong 2,2 giây.
8. Mở Hồ sơ Phiêu lưu:
   - Thống kê đúng.
   - Thanh tiến độ ngày đúng.
9. Tắt hiệu ứng:
   - Shield vẫn thưởng nhưng không chạy hiệu ứng bay.
10. Xóa hồ sơ:
    - Header trở về `lqlq Browser`.
    - Lịch sử, dấu trang và thẻ vẫn còn.
11. Kiểm tra hồi quy:
    - Đọc truyện TXT/TTS.
    - Chapter Clipper.
    - Nhạc/video nền.
    - Playlist thư mục.
    - YouTube/PiP.
    - Chỉnh ảnh.
    - Chế độ riêng tư/ẩn danh.
    - Nút Back Android và menu.

---

## 8. Giới hạn có chủ ý của v0.29.0

Chưa triển khai trong bản này:

- Đăng nhập Google hoặc Facebook.
- Đồng bộ hồ sơ giữa nhiều thiết bị.
- Máy chủ quản lý tài khoản.
- Mua/bán Linh Thạch.
- Google Play Billing hoặc bản Pro.
- Cửa hàng trang bị.
- Nhiệm vụ hằng ngày.
- Nơi tiêu Linh Thạch.
- Avatar từ thư viện ảnh.
- Thưởng cho request quảng cáo con hoặc popup không tạo callback native.

Vì dữ liệu đang lưu offline, xóa dữ liệu ứng dụng hoặc gỡ cài đặt có thể làm mất hồ sơ. Không nên bán Linh Thạch trước khi có cơ chế khôi phục đáng tin cậy.

---

## 9. Bước tiếp theo đề xuất

Chỉ tiếp tục sau khi bản v0.29.0 build và test ổn trên máy thật:

1. Sửa lỗi phát hiện trong test APK.
2. Thêm **một** nơi tiêu Linh Thạch thử nghiệm, ưu tiên thao tác Chapter Clipper hàng loạt.
3. Thêm 2–3 trang bị đại diện cho cài đặt thật, không làm cửa hàng lớn ngay.
4. Đưa cho 10–20 người dùng thử để xem họ có thích Hồ sơ Phiêu lưu hay thấy phiền.
5. Sau khi có phản hồi mới quyết định làm nhiệm vụ, Pro và sao lưu Google.
