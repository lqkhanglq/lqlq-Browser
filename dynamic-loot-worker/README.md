# LQLQ Dynamic Loot Worker

Cloudflare Worker tùy chọn cho **lqlq Browser v0.32.0**.

## Chức năng

- `GET /api/random-loot`
  - mặc định tự chọn giữa Workers AI và Wikipedia/Wikimedia;
  - nếu AI lỗi hoặc hết hạn mức, tự rơi về nguồn tri thức mở;
  - trả một gói JSON gồm tên, thể loại, mô tả, độ hiếm, số sao và ảnh.
- `GET /api/health`
  - kiểm tra Worker đã hoạt động hay chưa.

## Triển khai

```bash
cd dynamic-loot-worker
npm install
npx wrangler login
npm run deploy
```

Sau khi deploy, Cloudflare sẽ trả URL dạng:

```text
https://lqlq-dynamic-loot.<tên-tài-khoản>.workers.dev
```

Trong GitHub repository của app, tạo **Actions variable**:

```text
LQLQ_DYNAMIC_LOOT_ENDPOINT=https://lqlq-dynamic-loot.<tên-tài-khoản>.workers.dev
```

Workflow build của project đã tự truyền biến này vào Android. Không đặt API token Cloudflare trong APK.

## Chế độ

- `mode=auto`: dùng AI theo `AI_RATE`, còn lại dùng Wikipedia/Wikimedia.
- `mode=ai`: cố dùng Workers AI, lỗi sẽ rơi về Wikimedia.
- `mode=knowledge`: chỉ dùng Wikipedia/Wikimedia.

Ví dụ:

```text
/api/random-loot?mode=auto&rarity=Hiếm&locale=vi&seed=test-123
```

## Điều chỉnh

Trong `wrangler.jsonc`:

```jsonc
"AI_ENABLED": "true",
"AI_RATE": "0.12"
```

`AI_RATE=0.12` nghĩa là khoảng 12% yêu cầu Worker sẽ thử tạo nội dung AI. Ứng dụng Android còn có tỷ lệ rơi riêng trước khi gọi Worker, nên AI không bị gọi ở mọi trang.

## Không deploy Worker thì sao?

Ứng dụng vẫn chạy. `DynamicLootRepository` tự gọi Wikipedia/Wikimedia trực tiếp để lấy một hình ảnh và mô tả ngẫu nhiên. Khi có Worker URL, AI mới được kích hoạt.
