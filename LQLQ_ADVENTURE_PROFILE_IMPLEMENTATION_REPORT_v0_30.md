# LQLQ Browser v0.30.0 — Adventure Profile Follow-up Report

## Mục tiêu lượt này
Theo phản hồi sau khi build/test v0.29.0, lượt v0.30.0 tập trung vào:

1. Bỏ khung “Chặn quảng cáo / Popup và chuyển hướng đã được kiểm soát” ở menu.
2. Làm rõ lời mời tạo tài khoản phiêu lưu khi chưa có hồ sơ.
3. Chuyển cơ chế nhận Linh Thạch:
   - **không còn cộng từ chặn redirect**,
   - **thay bằng Linh Thạch xuất hiện khi vào một trang web mới**.
4. Thêm nền móng giao diện theo hướng “menu game”:
   - cấp độ,
   - danh hiệu nhà mạo hiểm,
   - số vùng đất đã khám phá,
   - chỗ quy hoạch hành trang / cửa hàng.
5. Thêm khả năng dùng **ảnh trên máy làm avatar**.
6. Thêm tùy chọn **bật/tắt việc hiện Linh Thạch trên màn hình web**.

---

## Các thay đổi chính đã làm

### 1) Menu đầu trang đã đổi sang tóm tắt Phiêu lưu
- Xóa phần khung cũ về chặn quảng cáo.
- Thay bằng khối tóm tắt Phiêu lưu gồm:
  - Level,
  - danh hiệu,
  - số Linh Thạch,
  - mô tả ngắn trạng thái hiện tại.

### 2) Trạng thái chưa có hồ sơ rõ ràng hơn
Khi chưa tạo hồ sơ, phần đầu menu giờ hiển thị:
- **“Chưa có Hồ sơ Phiêu lưu”**
- **“Nhấn vào đây để tạo nhà mạo hiểm của bạn”**

=> người dùng nhìn vào sẽ hiểu ngay đây là một tính năng riêng để bấm vào tạo hồ sơ.

### 3) Cơ chế Linh Thạch mới
- Native store không còn thưởng Linh Thạch cho việc chặn quảng cáo/chuyển hướng.
- Khi một **trang web mới** tải xong:
  - ứng dụng xem đó là một **“vùng đất mới”**,
  - một **Linh Thạch overlay native** sẽ xuất hiện trên màn hình,
  - người dùng **nhấn vào để nhặt**,
  - Linh Thạch sẽ **bay lên góc trên bên phải** bằng animation native,
  - sau đó hồ sơ được cộng tiến trình / tài nguyên.
- Hiện tại theo yêu cầu, bản này đang để **tỷ lệ xuất hiện 100%** cho mỗi trang web mới.

### 4) Dashboard Hồ sơ Phiêu lưu theo hướng game hóa hơn
Hộp hồ sơ đã đổi bố cục theo kiểu “menu game” hơn:
- avatar nhân vật ở giữa,
- hai cột slot trang bị placeholder hai bên,
- hiển thị:
  - Level,
  - danh hiệu,
  - Linh Thạch,
  - số vùng đất đã khám phá,
  - số lần Shield bảo vệ.

### 5) Quy hoạch sẵn cho các phần mở rộng sau
Đã chừa chỗ trực quan trong giao diện cho:
- **Túi hành trang**,
- **Cửa hàng Linh Thạch**.

Chúng hiện mới là block mô tả/placeholder, chưa có logic đầy đủ.

### 6) Hướng dẫn rõ hơn
Đã thêm phần giải thích trực tiếp trong UI:
- cách kiếm Linh Thạch,
- việc “mỗi trang web mới = một vùng đất mới”,
- công dụng tương lai của Linh Thạch.

### 7) Avatar từ ảnh trên máy
Đã thêm luồng chọn ảnh local:
- người dùng có thể bấm **“Chọn ảnh từ máy”**,
- JS resize ảnh trước khi lưu,
- bridge native lưu `data:image/...` vào store,
- avatar custom được dùng lại ở menu + dashboard.

### 8) Bật / tắt Linh Thạch trên màn hình web
Đã thêm toggle riêng:
- **Hiện Linh Thạch trên màn hình web**

Nếu tắt:
- overlay Linh Thạch sẽ không xuất hiện,
- người dùng chỉ muốn dùng trình duyệt bình thường sẽ ít bị làm phiền hơn.

---

## File chính đã sửa

### Android / Kotlin
- `app/build.gradle.kts`
- `app/src/main/java/com/lqlq/browser/AdventureProfileStore.kt`
- `app/src/main/java/com/lqlq/browser/AdventureProfileBridge.kt`
- `app/src/main/java/com/lqlq/browser/MainActivity.kt`

### Web shell
- `app/src/main/assets/www/index.html`
- `app/src/main/assets/www/styles.css`
- `app/src/main/assets/www/v29-adventure-profile.js`
- `app/src/main/assets/www/CHANGELOG_v0_30.txt`

### Asset mới
- `app/src/main/res/drawable/adventure_crystal_loot.png`
- `app/src/main/assets/www/assets/adventure/crystal_loot.png`

---

## Hành vi hiện tại sau chỉnh sửa

### Khi chưa có hồ sơ
- Trình duyệt vẫn hoạt động bình thường.
- Menu cho thấy rõ đây là nơi tạo hồ sơ phiêu lưu.
- Không ép đăng nhập/đăng ký.

### Khi đã có hồ sơ
- Mỗi khi vào một trang web mới, Linh Thạch sẽ xuất hiện trên màn hình.
- Nhấn vào Linh Thạch để nhặt.
- Hồ sơ sẽ tăng:
  - Linh Thạch,
  - số vùng đất đã khám phá,
  - có thể tăng cấp/danh hiệu theo mốc.

### Chặn quảng cáo / redirect
- Shield vẫn hoạt động bảo vệ như trước.
- Vẫn có thể thống kê số lần bảo vệ.
- Nhưng **không còn là nguồn cộng Linh Thạch chính**.

---

## Giới hạn / lưu ý của bản này

1. Chưa chạy build Android đầy đủ trong môi trường hiện tại.
   - Môi trường ở đây không có full Android SDK/Gradle online để chạy build hoàn chỉnh.
   - Cần build lại trên GitHub Actions / máy local để xác nhận compile và test thực tế.

2. Slot trang bị, túi hành trang, cửa hàng mới là placeholder giao diện.
   - Chưa có logic vật phẩm/trang bị hoàn chỉnh.

3. Cơ chế “vùng đất mới” hiện đang dựa trên **URL trang web mới tải xong**.
   - Đây là nền móng hợp lý cho bước đầu.
   - Sau này có thể tinh chỉnh sâu hơn (domain mới, trang mới, khu vực mới, drop table...).

4. Avatar custom đang lưu nội bộ bằng data URL.
   - Phù hợp cho avatar nhỏ.
   - Nếu sau này muốn tối ưu mạnh hơn, có thể chuyển sang lưu file nội bộ riêng.

---

## Đề xuất bước tiếp theo

Ưu tiên nên test các tình huống sau trên APK build mới:

1. Tạo hồ sơ mới.
2. Dùng ảnh local làm avatar.
3. Vào vài website khác nhau xem Linh Thạch có hiện ổn không.
4. Nhấn nhặt Linh Thạch xem animation và cập nhật số liệu có đúng không.
5. Tắt/bật “Hiện Linh Thạch trên màn hình web”.
6. Kiểm tra menu khi chưa có hồ sơ và khi đã có hồ sơ.
7. Kiểm tra theme sáng/tối.

Nếu pass ổn, lượt sau nên làm tiếp:
- hành trang,
- vật phẩm hiếm,
- drop table,
- trang bị,
- cửa hàng Linh Thạch,
- nhiệm vụ đăng nhập / khám phá,
- minigame offline.
