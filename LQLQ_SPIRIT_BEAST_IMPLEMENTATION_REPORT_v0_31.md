# lqlq Browser v0.31.0 — Báo cáo triển khai Linh Thú Vạn Giới

## Mục tiêu

Lượt này biến ý tưởng “tìm sinh vật trong web” thành một hệ thống hoạt động độc lập phía trên trình duyệt:

- Mỗi trang web mới là một vùng đất.
- Linh Thạch vẫn có thể xuất hiện để nhặt.
- Một sinh vật Vạn Giới có xác suất xuất hiện ngẫu nhiên.
- Người dùng dùng Linh Cầu để thu phục.
- Sinh vật đã bắt được đưa vào Đồ Giám và có thể khoe bằng thẻ thành tích.

Toàn bộ hệ Phiêu lưu chỉ hoạt động khi người dùng đã tạo hồ sơ và bật tính năng. Người dùng bình thường không tạo hồ sơ vẫn sử dụng trình duyệt như trước.

---

## Các hệ thống đã thêm

### 1. Danh mục 18 sinh vật nguyên bản

Đã tạo `SpiritBeastCatalog.kt` với 18 sinh vật thuộc năm nhóm:

- Linh Thú
- Hồn Thú
- Ma Thú
- Yêu Thú
- Thần Thú

Độ hiếm:

- Thường
- Hiếm
- Sử Thi
- Huyền Thoại
- Thần Thoại

Một số sinh vật tiêu biểu:

- Mặc Hồ
- Thư Điểu
- Âm Miêu
- Họa Điệp
- Điện Hồ
- Khiên Linh
- Ảnh Lang
- Mộng Linh
- Dữ Liệu Long
- Hỏa Phượng
- Thiên Mục
- Hư Vô Kỳ Lân

Tên, mô tả và cấu trúc đều được làm riêng cho lqlq Browser, không sử dụng tài sản của Pokémon hoặc thương hiệu bên ngoài.

### 2. Sinh vật xuất hiện trên web

Sau khi một trang web mới tải xong:

- Hệ thống kiểm tra hồ sơ Phiêu lưu.
- Nếu người dùng bật Linh Thú, có xác suất xuất hiện một sinh vật.
- Ba lần gặp đầu có xác suất cao hơn để người dùng hiểu cơ chế.
- Sau đó tỷ lệ trở về mức khoảng 32%.
- Sinh vật xuất hiện bằng overlay native, không chèn JavaScript vào DOM của website.
- Mỗi lần chỉ có tối đa một thẻ sinh vật trên màn hình.
- Nếu không tương tác trong khoảng 24 giây, sinh vật sẽ rời khỏi vùng đất.

Cách này nhẹ hơn việc chạy animation liên tục trong website.

### 3. Chọn sinh vật theo loại vùng đất

Sinh vật không hoàn toàn ngẫu nhiên. Catalog có ưu tiên theo nội dung domain/title:

- Trang sách/truyện/tri thức ưu tiên Mặc Hồ, Thư Điểu, Mộng Linh.
- Trang âm nhạc/video ưu tiên Âm Miêu, Họa Điệp.
- Trang AI/công nghệ ưu tiên Điện Hồ, Tinh Não.
- Trang tin tức ưu tiên Phong Ưng.
- Trang mua sắm ưu tiên Kim Thử.
- Trang chưa phân loại có thể sinh Vô Danh Thú hoặc sinh vật Vạn Giới.

### 4. Hệ Linh Cầu và thu phục

Đã thêm ba loại Linh Cầu:

- Linh Cầu Thô
- Linh Cầu Bạc
- Linh Cầu Hoàng Kim

Khi tạo hồ sơ mới hoặc nâng cấp hồ sơ cũ lên v0.31:

- nhận 5 Linh Cầu Thô,
- nhận 1 Linh Cầu Bạc,
- nhận 0 Linh Cầu Hoàng Kim.

Mỗi loại Linh Cầu có hệ số thu phục khác nhau. Độ hiếm càng cao thì tỷ lệ càng thấp.

Đã thêm cơ chế “thất bại liên tiếp tăng nhẹ cơ hội”:

- mỗi lần thất bại tăng một ít tỷ lệ,
- giới hạn cộng thêm để không phá cân bằng,
- thành công thì đặt lại chuỗi hỗ trợ.

### 5. Đồ Giám Vạn Giới

Trong Hồ sơ Phiêu lưu đã có mục **Đồ Giám Vạn Giới**:

- hiển thị toàn bộ 18 sinh vật,
- sinh vật chưa bắt chỉ hiện ô khóa,
- sinh vật đã bắt hiện icon, tên, nhóm, độ hiếm,
- hiển thị số lần bắt trùng,
- hiển thị domain gặp đầu tiên.

### 6. Túi Hành Trang

Đã có mục **Túi Hành Trang** để xem:

- số Linh Cầu Thô,
- số Linh Cầu Bạc,
- số Linh Cầu Hoàng Kim,
- tổng số lần gặp sinh vật,
- tổng số lần thu phục thành công,
- số loài khác nhau trong bộ sưu tập.

Hành trang hiện mới tập trung vào Linh Cầu. Kiến trúc đã chừa đường cho vật phẩm, trứng, trang bị và chiến lợi phẩm trong các bản tiếp theo.

### 7. Cửa Hàng Linh Thạch

Đã biến placeholder cửa hàng thành chức năng mua thật:

- Túi 3 Linh Cầu Thô: 15 Linh Thạch.
- 1 Linh Cầu Bạc: 35 Linh Thạch.
- 1 Linh Cầu Hoàng Kim: 90 Linh Thạch.

Mọi giao dịch được xử lý ở native store, JavaScript chỉ hiển thị kết quả.

### 8. Thẻ Khoe Thành Tích

Đã thêm một thẻ được bố trí riêng để người dùng chụp màn hình, gồm:

- avatar,
- biệt danh,
- cấp độ và danh hiệu,
- số Linh Thạch,
- số sinh vật đã sưu tập,
- số vùng đất đã khám phá,
- các sinh vật hiếm nổi bật.

Bản này dùng cách chụp màn hình thủ công. Nút xuất ảnh trực tiếp có thể làm sau.

### 9. Công tắc bật/tắt

Đã thêm tùy chọn:

- Hiện Linh Thạch trên màn hình web.
- Cho phép Linh Thú xuất hiện.
- Hiệu ứng nhận Linh Thạch.

Nếu người dùng không thích game hóa, họ có thể tắt phần xuất hiện trên web mà vẫn giữ trình duyệt và hồ sơ.

---

## Dữ liệu lưu offline

`AdventureProfileStore.kt` hiện lưu thêm:

- ba loại Linh Cầu,
- tổng số lần gặp,
- tổng số lần bắt,
- chuỗi hỗ trợ sau thất bại,
- bộ sưu tập sinh vật,
- domain gặp đầu tiên,
- số lượng từng loài,
- công tắc Linh Thú.

Đã bổ sung cơ chế cấp quà khởi đầu cho hồ sơ v0.29/v0.30 cũ để người dùng hiện tại không bị thiếu Linh Cầu sau khi cập nhật.

---

## File chính đã thay đổi

### File mới

- `app/src/main/java/com/lqlq/browser/SpiritBeastCatalog.kt`
- `app/src/main/assets/www/v31-adventure-profile.js`
- `app/src/main/assets/www/CHANGELOG_v0_31.txt`
- `LQLQ_SPIRIT_BEAST_IMPLEMENTATION_REPORT_v0_31.md`

### File cập nhật

- `app/build.gradle.kts`
- `app/src/main/java/com/lqlq/browser/MainActivity.kt`
- `app/src/main/java/com/lqlq/browser/AdventureProfileStore.kt`
- `app/src/main/java/com/lqlq/browser/AdventureProfileBridge.kt`
- `app/src/main/assets/www/index.html`
- `app/src/main/assets/www/styles.css`

---

## Phiên bản

- `versionCode = 68`
- `versionName = 0.31.0`

---

## Kiểm tra đã thực hiện trong môi trường hiện tại

- JavaScript `v31-adventure-profile.js`: đã chạy `node --check`, không có lỗi cú pháp.
- HTML: đã parse và kiểm tra không có ID trùng.
- `SpiritBeastCatalog.kt`, `AdventureProfileStore.kt` và `AdventureProfileBridge.kt`: đã compile bằng Kotlin compiler với stub Android tối thiểu để kiểm tra cú pháp và kiểu dữ liệu cơ bản.
- Đã kiểm tra số lượng dấu ngoặc trong các file Kotlin chính.

## Chưa thể xác nhận

Môi trường hiện tại không có Android SDK/Gradle đầy đủ nên chưa chạy được build APK thật. Cần build qua GitHub Actions để xác nhận:

- MainActivity compile với Android SDK,
- overlay Linh Thú hiển thị đúng trên thiết bị,
- AlertDialog thu phục hoạt động đúng,
- giao diện Đồ Giám/Hành Trang/Cửa Hàng không bị tràn trên màn hình nhỏ,
- dữ liệu tồn tại sau khi đóng/mở app.

---

## Checklist test APK sau này

1. Cài đè từ v0.29 hoặc v0.30 và xác nhận hồ sơ cũ vẫn còn.
2. Kiểm tra nhận 5 Linh Cầu Thô + 1 Linh Cầu Bạc khi nâng cấp.
3. Mở nhiều website mới và đợi sinh vật xuất hiện.
4. Chọn từng loại Linh Cầu và kiểm tra số lượng bị trừ.
5. Kiểm tra thành công/thất bại thu phục.
6. Kiểm tra Đồ Giám cập nhật sinh vật đã bắt.
7. Mua Linh Cầu trong cửa hàng và kiểm tra Linh Thạch bị trừ đúng.
8. Mở Thẻ Khoe Thành Tích và chụp màn hình.
9. Tắt “Cho phép Linh Thú xuất hiện” và xác nhận không còn overlay sinh vật.
10. Kiểm tra trình duyệt, Shield, media, TTS, Chapter Clipper và thẻ native không bị hồi quy.
