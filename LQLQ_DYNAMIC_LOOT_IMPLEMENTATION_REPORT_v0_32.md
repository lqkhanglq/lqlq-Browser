# LQLQ Browser v0.32.0 — Dynamic Loot Engine

## Trạng thái

Đã triển khai source cho **LQLQ Dynamic Loot Engine** trên nền project v0.31.0.

Phiên bản mới:

```text
versionCode: 69
versionName: 0.32.0
```

Mục tiêu của lượt này là bỏ giới hạn “chỉ có một danh mục vài chục Linh Thú đóng sẵn” và bổ sung một hệ thẻ sưu tập động có thể mở rộng gần như không cạn.

---

## Mô hình đã triển khai

Khi người dùng có Hồ sơ Phiêu lưu và đi đến một trang web mới:

1. lqlq Browser tự quay tỷ lệ xuất hiện Kỳ Vật.
2. Ứng dụng tự quyết định độ hiếm trước.
3. Ứng dụng thử gọi **LQLQ Dynamic Loot Worker** nếu endpoint đã được cấu hình.
4. Worker có thể:
   - dùng Workers AI để tự nghĩ ra một vật/người/sinh vật/cổ vật hoàn toàn nguyên bản rồi tạo ảnh;
   - hoặc lấy ngẫu nhiên một mục có ảnh và mô tả từ Wikipedia/Wikimedia.
5. Nếu Worker chưa deploy, lỗi hoặc hết hạn mức, Android tự gọi Wikipedia/Wikimedia trực tiếp.
6. Kỳ Vật xuất hiện dưới dạng thẻ nổi trên màn hình web.
7. Người dùng nhấn thẻ để xem thông tin và chọn **Thu thập thẻ**.
8. Ảnh được nén thành WebP, lưu trong bộ nhớ riêng của ứng dụng và ghi vào **Vạn Giới Đồ Giám**.

Hệ 18 Linh Thú offline của v0.31 vẫn được giữ làm nội dung dự phòng và là một nhánh sưu tập riêng.

---

## Kỳ Vật động gồm những dữ liệu nào?

Mỗi thẻ có:

- ID ổn định hoặc ID duy nhất.
- Tên.
- Thể loại.
- Mô tả ngắn.
- Độ hiếm.
- Số sao.
- Ảnh.
- Nguồn nội dung.
- Trang nguồn nếu có.
- Nơi người dùng tìm thấy.
- Thời điểm thu thập.
- Số lần sở hữu cùng thẻ.

Các nhóm có thể xuất hiện gồm:

- Nhân vật.
- Động vật.
- Thực vật.
- Đồ vật.
- Đạo cụ.
- Vũ khí.
- Cổ vật.
- Công trình.
- Địa danh.
- Tác phẩm.
- Thiên thể.
- Kỳ Vật không phân loại.
- Nội dung AI nguyên bản như Linh Thú, Hồn Thú, Ma Thú, Yêu Thú và Thần Thú.

---

## Tỷ lệ thử nghiệm hiện tại

### Tỷ lệ Kỳ Vật xuất hiện trên web mới

```text
Chưa có Kỳ Vật nào: 86%
Sau khi đã sưu tập: 24%
```

Tỷ lệ đầu tiên cao để người dùng dễ nhìn thấy và hiểu hệ thống.

### Độ hiếm thẻ

```text
Thường:        72,0%
Hiếm:          20,0%
Sử Thi:         6,0%
Huyền Thoại:    1,7%
Thần Thoại:     0,3%
```

Độ hiếm do lqlq Browser quyết định, không giao hoàn toàn cho API bên ngoài.

### Tỷ lệ AI ở Worker

Mặc định trong `wrangler.jsonc`:

```text
AI_RATE = 0.12
```

Nghĩa là khoảng 12% yêu cầu tới Worker sẽ thử tạo nội dung AI; phần còn lại lấy từ Wikipedia/Wikimedia. Vì Android còn quay tỷ lệ rơi trước khi gọi Worker nên AI không bị gọi ở mọi trang web.

---

## Chế độ không có Worker

Ứng dụng **không bị phụ thuộc bắt buộc vào Cloudflare**.

Khi biến endpoint để trống:

```text
LQLQ_DYNAMIC_LOOT_ENDPOINT=
```

APK vẫn có thể:

- lấy bài Wikipedia ngẫu nhiên;
- lọc bài có thumbnail và mô tả;
- tự phân loại sơ bộ;
- gắn độ hiếm và số sao;
- hiển thị thẻ;
- lưu ảnh WebP;
- ghi vào bộ sưu tập.

Nhờ vậy bạn có thể build APK v0.32 trước rồi triển khai AI sau.

---

## Cloudflare Worker đã thêm

Thư mục:

```text
dynamic-loot-worker/
```

Các endpoint:

```text
GET /api/health
GET /api/random-loot
```

Tham số hỗ trợ:

```text
mode=auto|ai|knowledge
rarity=Thường|Hiếm|Sử Thi|Huyền Thoại|Thần Thoại
locale=vi|en
seed=<chuỗi bất kỳ>
```

Worker sử dụng:

- Workers AI binding `AI`.
- Text model mặc định: `@cf/meta/llama-3.1-8b-instruct-fast`.
- Image model mặc định: `@cf/black-forest-labs/flux-1-schnell`.
- JSON Schema để buộc AI trả tên, thể loại, mô tả và prompt ảnh theo cấu trúc.
- Fallback Wikipedia/Wikimedia khi AI lỗi.

---

## Cách deploy Worker

### Cách 1 — trên máy có Node.js

```bash
cd dynamic-loot-worker
npm install
npx wrangler login
npm run deploy
```

### Cách 2 — GitHub Actions

Đã thêm workflow:

```text
.github/workflows/deploy-dynamic-loot-worker.yml
```

Cần thêm hai GitHub Secrets:

```text
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
```

Sau đó chạy thủ công workflow **Deploy LQLQ Dynamic Loot Worker**.

### Nối Worker với APK

Sau khi deploy, tạo GitHub Actions repository variable:

```text
LQLQ_DYNAMIC_LOOT_ENDPOINT
```

Giá trị ví dụ:

```text
https://lqlq-dynamic-loot.<tài-khoản>.workers.dev
```

Workflow build APK đã tự truyền biến này vào Gradle.

---

## Lưu ảnh và dung lượng

- APK không đóng sẵn hàng trăm/hàng nghìn hình.
- Ảnh chỉ được lưu khi người dùng thực sự thu thập thẻ.
- Ảnh được nén WebP ở chất lượng 82%.
- File nằm trong bộ nhớ riêng của ứng dụng.
- Shell WebView đọc ảnh qua `WebViewAssetLoader` tại đường dẫn nội bộ:

```text
https://appassets.androidapp.com/dynamic-loot/<file>.webp
```

Đối với ảnh AI trả dưới dạng Base64, chuỗi Base64 không được giữ trong SharedPreferences sau khi thu thập; ứng dụng chỉ giữ file WebP cục bộ để tránh dữ liệu hồ sơ phình lớn.

---

## Giao diện đã cập nhật

### Dashboard

- Đổi mục **Đồ Giám Vạn Giới** thành **Vạn Giới Đồ Giám**.
- Hiển thị riêng:
  - số Kỳ Vật động;
  - số Linh Thú offline đã thu phục.
- Thêm công tắc:

```text
Cho phép Kỳ Vật động xuất hiện
```

### Vạn Giới Đồ Giám

Có hai khu vực:

1. **Kỳ Vật động**
   - ảnh thật hoặc ảnh AI;
   - tên;
   - thể loại;
   - độ hiếm;
   - số sao;
   - mô tả;
   - nguồn;
   - số lượng.

2. **Linh Thú nguyên bản**
   - giữ nguyên hệ 18 sinh vật offline của v0.31.

### Thẻ khoe thành tích

Đã bổ sung hình Kỳ Vật nổi bật vào thẻ thành tích để người dùng chụp màn hình khoe bộ sưu tập.

---

## File mới chính

### Android

```text
app/src/main/java/com/lqlq/browser/DynamicLootItem.kt
app/src/main/java/com/lqlq/browser/DynamicLootStore.kt
app/src/main/java/com/lqlq/browser/DynamicLootRepository.kt
app/src/main/java/com/lqlq/browser/DynamicLootImageCache.kt
```

### Web UI

```text
app/src/main/assets/www/v32-adventure-profile.js
app/src/main/assets/www/CHANGELOG_v0_32.txt
```

### Backend tùy chọn

```text
dynamic-loot-worker/package.json
dynamic-loot-worker/package-lock.json
dynamic-loot-worker/wrangler.jsonc
dynamic-loot-worker/src/index.js
dynamic-loot-worker/README.md
```

### Workflow

```text
.github/workflows/deploy-dynamic-loot-worker.yml
```

---

## File sửa chính

```text
app/build.gradle.kts
app/src/main/java/com/lqlq/browser/MainActivity.kt
app/src/main/java/com/lqlq/browser/AdventureProfileBridge.kt
app/src/main/assets/www/index.html
app/src/main/assets/www/styles.css
.github/workflows/build.yml
README.md
.gitignore
```

---

## Kiểm tra đã thực hiện

Đã kiểm tra:

- JavaScript UI bằng `node --check`.
- JavaScript Worker bằng `node --check`.
- HTML có đủ ID mới và đang nạp `v32-adventure-profile.js`.
- Kotlin được parser của `kotlinc` đọc qua; môi trường thiếu Android SDK nên chỉ xác nhận không có lỗi cú pháp kiểu `expecting/unexpected tokens`, chưa thể xác nhận toàn bộ Android symbol/linking.
- `package-lock.json` cho Worker đã được tạo.

---

## Chưa thể thực hiện trong môi trường này

### 1. Chưa build APK hoàn chỉnh

Môi trường hiện tại không có Android SDK/Gradle đầy đủ. Cần GitHub Actions xác nhận compile thực tế.

### 2. Chưa deploy Cloudflare Worker

Không có quyền truy cập tài khoản Cloudflare, `CLOUDFLARE_API_TOKEN` hoặc `CLOUDFLARE_ACCOUNT_ID` của bạn.

### 3. Chưa gọi thử API Internet từ container

Container chạy code không có quyền truy cập Internet trực tiếp, nên test Worker knowledge mode trả `fetch failed`. Đây là giới hạn môi trường, không phải kết quả test API thực tế.

---

## Checklist khi build/test sau này

1. Build APK khi chưa đặt Worker endpoint.
2. Tạo Hồ sơ Phiêu lưu.
3. Mở các website khác nhau.
4. Kiểm tra Kỳ Vật đầu tiên có xuất hiện.
5. Nhấn thẻ, xem ảnh, tên, loại, mô tả và nguồn.
6. Thu thập thẻ.
7. Mở Vạn Giới Đồ Giám và kiểm tra ảnh local.
8. Đóng/mở lại app, xác nhận thẻ vẫn còn.
9. Tắt “Cho phép Kỳ Vật động xuất hiện”.
10. Deploy Worker và đặt `LQLQ_DYNAMIC_LOOT_ENDPOINT`.
11. Build lại, kiểm tra thẻ nguồn AI và thẻ nguồn Wikimedia.
12. Kiểm tra theme sáng/tối và màn hình nhỏ.

---

## Hướng phát triển tiếp theo

Sau khi v0.32 build ổn định, các bước đáng làm tiếp:

- nút xuất thẻ thành ảnh PNG để chia sẻ;
- lọc Đồ Giám theo thể loại/độ hiếm/nguồn;
- sao lưu và khôi phục bộ sưu tập;
- giới hạn AI theo thiết bị/ngày ở Worker;
- cache hoặc R2 cho ảnh AI để giảm dữ liệu Base64;
- sự kiện Kỳ Vật theo chủ đề tuần/tháng;
- cửa hàng khung thẻ và hiệu ứng độ hiếm;
- số seri cho từng phiên bản thẻ;
- thành tựu “bộ sưu tập hiếm”.
