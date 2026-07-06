# lqlq Browser — Kế hoạch Hồ sơ Phiêu lưu và Linh Thạch

## 0. Baseline được kiểm tra

- ZIP nguồn: `lqlq-browser-android-v0_23_1(1).zip`
- SHA-256: `bad3cad38e97711630df1c5bcbea5d0d5da68caad150a2770e3ace10f6c20abf`
- Project bên trong đang khai báo:
  - `versionName = 0.28.1`
  - `versionCode = 65`
- ZIP có thư mục `.git`, nhưng working tree đang có 28 file tracked bị sửa so với commit hiện tại.
- Quy tắc triển khai: làm trên bản sao của nội dung ZIP hiện tại; tuyệt đối không `git reset`, không checkout đè và không làm mất các thay đổi chưa commit.

## 1. Mục tiêu sản phẩm

Giữ lqlq Browser là trình duyệt bình thường đối với mọi người dùng. Lớp Phiêu lưu chỉ được kích hoạt khi người dùng chủ động tạo hồ sơ.

### Trạng thái Khách

- Giao diện và chức năng trình duyệt hoạt động như hiện tại.
- Menu hiển thị logo Shield, tên `lqlq Browser`, dòng `Bảo vệ đang hoạt động`.
- Shield vẫn chặn quảng cáo, popup và chuyển hướng.
- Không có Linh Thạch, nhiệm vụ, trang bị hoặc hiệu ứng cộng thưởng.
- Không ép đăng nhập và không hiện lời mời lặp lại.

### Trạng thái có Hồ sơ Phiêu lưu

- Menu header thay logo/tên mặc định bằng avatar và biệt danh.
- Dưới tên hiển thị số Linh Thạch và thống kê Shield ngắn.
- Chặn một chuyển hướng/popup đủ điều kiện sẽ cộng Linh Thạch và phát hiệu ứng nhẹ.
- Nhấn avatar/header mới mở khu vực Hồ sơ Phiêu lưu.
- Khi đóng khu vực hồ sơ, ứng dụng trở lại trình duyệt bình thường.

## 2. Quyết định phạm vi bản đầu

### Sẽ làm trong bản đầu

1. Hồ sơ offline, không cần máy chủ.
2. Biệt danh do người dùng nhập.
3. Avatar mặc định chọn từ một bộ có sẵn.
4. Linh Thạch bắt đầu từ 0.
5. Thưởng `+1` khi Shield chặn một sự kiện điều hướng chính đủ điều kiện.
6. Hiệu ứng Linh Thạch bay vào khu vực hồ sơ.
7. Trang Hồ sơ Phiêu lưu mở khi nhấn avatar/header.
8. Thống kê cơ bản:
   - Linh Thạch hiện có.
   - Tổng lần Shield bảo vệ.
   - Số lần nhận thưởng hôm nay.
9. Nút tắt hiệu ứng thưởng.
10. Nút xóa hồ sơ có xác nhận rõ ràng.
11. Một nơi tiêu Linh Thạch thử nghiệm: tác vụ Chapter Clipper tự động/hàng loạt, nhưng chỉ nối sau khi luồng hồ sơ và thưởng ổn định.

### Chưa làm trong bản đầu

- Đăng nhập Google/Facebook.
- Đồng bộ thời gian thực giữa nhiều thiết bị.
- Máy chủ tài khoản.
- Bán Linh Thạch.
- Google Play Billing/Pro.
- Nhiệm vụ hằng ngày đầy đủ.
- Cửa hàng lớn hoặc hàng chục trang bị.
- Bảng xếp hạng.
- Ảnh avatar tùy chọn từ thư viện người dùng.
- Thưởng cho mọi request quảng cáo con trong trang.

## 3. Kiến trúc dữ liệu

### 3.1. Nguồn dữ liệu chuẩn

Tạo lớp native riêng, không nhét thêm trạng thái vào `MainActivity.kt`:

- `AdventureProfileStore.kt`
- lưu bằng `SharedPreferences` riêng: `lqlq_adventure_profile_v1`

Lý do:

- Hồ sơ tồn tại độc lập với localStorage của shell.
- Dễ sao lưu bằng Android Auto Backup hiện đã bật qua `android:allowBackup="true"`.
- Dễ nối Google Play Billing/Pro sau này.
- Không làm phình thêm `MainActivity.kt` vốn đã gần 3.000 dòng.

### 3.2. Schema v1

```text
schemaVersion: Int = 1
profileCreated: Boolean
nickname: String
avatarId: String
crystals: Long
totalShieldProtects: Long
rewardedToday: Int
rewardDayKey: String
effectsEnabled: Boolean
createdAtEpochMs: Long
lastUpdatedEpochMs: Long
```

Quy tắc dữ liệu:

- Linh Thạch không được âm.
- Biệt danh 2–20 ký tự sau khi trim.
- Avatar chỉ nhận ID nằm trong danh sách cho phép.
- Mọi thay đổi số dư phải đi qua một hàm giao dịch duy nhất.
- Xóa hồ sơ phải xóa toàn bộ dữ liệu Phiêu lưu nhưng không xóa lịch sử duyệt, dấu trang hoặc cài đặt trình duyệt.

## 4. Luồng sự kiện Shield → phần thưởng

### 4.1. Chỉ thưởng sự kiện có ý nghĩa

Bản đầu chỉ thưởng cho:

- Chặn popup/tab mới thật.
- Chặn chuyển hướng main-frame sang domain lạ.
- Chặn main-frame tới domain quảng cáo đã biết.

Không thưởng cho:

- Ảnh, iframe, script hoặc request quảng cáo con.
- Cùng một host bị chặn lặp lại liên tục trong thời gian rất ngắn.
- Những chặn xảy ra khi chưa tạo hồ sơ.
- Sự kiện phát sinh từ chế độ ẩn danh nếu sau này cần giữ tính riêng tư tuyệt đối.

### 4.2. Chống cộng trùng

Hiện cùng một điều hướng có thể đi qua cả `shouldOverrideUrlLoading()` và `shouldInterceptRequest()`. Cần một bộ chống trùng native:

```text
key = tabId + eventKind + targetHost
cooldown = 1500–2500 ms
```

Chỉ sự kiện đầu tiên trong cooldown được tính thưởng và gửi sang shell.

### 4.3. Giới hạn thưởng

Bản thử nghiệm đặt hằng số cấu hình riêng, ví dụ:

```text
REWARD_PER_EVENT = 1
DAILY_REWARD_CAP = 30
```

- Khi hết giới hạn ngày: tổng lượt bảo vệ vẫn tăng nhưng Linh Thạch không tăng.
- Không khuyến khích người dùng cố tình farm trang rác.
- Các giá trị đặt trong một object/config riêng để đổi dễ dàng sau khi thử nghiệm.

### 4.4. Giao tiếp native → shell

Thêm callback vào `window.LqlqGlue`:

```text
onShieldProtectionEvent(payload)
```

Payload tối thiểu:

```json
{
  "kind": "redirect",
  "host": "example.com",
  "rewardGranted": true,
  "rewardAmount": 1,
  "crystals": 27,
  "totalShieldProtects": 103,
  "dailyRewardCapReached": false
}
```

Native là nguồn chuẩn quyết định cộng hay không. JavaScript chỉ render giao diện và hiệu ứng, không tự cộng số dư.

## 5. Giao diện

### 5.1. Menu header hiện tại

Vị trí hiện có trong `index.html`:

- `.menu-header`
- `.menu-brand`
- `.menu-brand-mark`
- tên `lqlq Browser`
- dòng `Bảo vệ đang hoạt động`

Sẽ thay thành các node có ID rõ ràng nhưng giữ layout cũ.

#### Khách

```text
[Shield] lqlq Browser
         Bảo vệ đang hoạt động
```

Nhấn header mở modal giới thiệu ngắn:

```text
Tạo Hồ sơ Phiêu lưu
Đặt biệt danh và chọn avatar để bắt đầu tích lũy Linh Thạch khi Shield bảo vệ bạn.
[Tạo hồ sơ] [Để sau]
```

#### Có hồ sơ

```text
[Avatar] <Biệt danh>
         ◆ <số Linh Thạch> · Shield đã bảo vệ <tổng lượt>
```

Nhấn header mở trang Hồ sơ Phiêu lưu.

### 5.2. Modal tạo hồ sơ

- Không xuất hiện tự động lúc mở app.
- Chỉ mở khi người dùng nhấn menu header ở trạng thái Khách.
- Chọn 6–8 avatar mặc định từ asset nội bộ.
- Nhập biệt danh.
- Nút tạo hồ sơ chỉ bật khi dữ liệu hợp lệ.
- Tạo xong không tự tặng quá nhiều; có thể dùng 0 hoặc quà khởi đầu nhỏ sau khi chốt cân bằng.

### 5.3. Trang Hồ sơ Phiêu lưu

Bản đầu gồm:

- Avatar và biệt danh.
- Linh Thạch.
- Tổng lượt Shield bảo vệ.
- Tiến độ thưởng hôm nay, ví dụ `12/30`.
- Công tắc hiệu ứng Linh Thạch.
- Khu `Trang bị` ở trạng thái “Sắp mở” hoặc một vật phẩm thử nghiệm duy nhất.
- Nút chỉnh biệt danh/avatar.
- Nút xóa hồ sơ.

Không đưa các mục này vào menu ba chấm chính ngoài menu header.

### 5.4. Hiệu ứng thưởng

- Hiện sau thông báo chặn.
- `+1 Linh Thạch` nổi gần giữa/phía trên nội dung.
- Một biểu tượng nhỏ bay về avatar/menu header.
- Thời lượng khoảng 600–900 ms.
- Không che thanh địa chỉ lâu.
- Không phát âm thanh mặc định.
- Tôn trọng `prefers-reduced-motion` và công tắc tắt hiệu ứng.

## 6. Trang bị — thiết kế chuẩn bị, chưa mở rộng ngay

Trang bị là cách biểu diễn cài đặt thật, không tạo lời hứa bảo mật giả.

Ví dụ tương lai:

| Trang bị | Cài đặt thật |
|---|---|
| Mũ Tĩnh Lặng | Tắt Toast khi Shield chặn |
| Khiên Phản Hướng | Bật domain/root-domain guard |
| Kính Đọc Giả | Mở nhanh chế độ đọc |
| Túi Thu Chương | Giảm/miễn chi phí tác vụ lấy chương tự động |

Bản đầu chỉ chuẩn bị schema mở rộng:

```text
equippedItemIds: Set<String>
unlockedItemIds: Set<String>
```

Nhưng chưa cần hiển thị cửa hàng đầy đủ.

## 7. Các file dự kiến thay đổi

### File mới

- `app/src/main/java/com/lqlq/browser/AdventureProfileStore.kt`
- `app/src/main/java/com/lqlq/browser/AdventureProfileBridge.kt` hoặc mở rộng `ShellBridge.kt` nếu giữ API nhỏ
- `app/src/main/assets/www/v29-adventure-profile.js`
- avatar preset dưới `app/src/main/assets/www/assets/avatars/`
- `ADVENTURE_PROFILE_IMPLEMENTATION_REPORT.md`

### File sửa chính

- `app/src/main/java/com/lqlq/browser/MainActivity.kt`
  - phát sự kiện Shield chuẩn hóa
  - chống trùng
  - gọi store giao dịch
  - callback sang shell
- `app/src/main/java/com/lqlq/browser/ShellBridge.kt`
  - đọc/tạo/sửa/xóa hồ sơ nếu không tách bridge riêng
- `app/src/main/assets/www/index.html`
  - ID động cho menu header
  - modal tạo hồ sơ
  - panel Hồ sơ Phiêu lưu
  - phần tử hiệu ứng thưởng
  - nạp module JS mới
- `app/src/main/assets/www/android-glue.js`
  - `onShieldProtectionEvent()`
  - đồng bộ snapshot hồ sơ native khi shell sẵn sàng
  - thêm overlay mới vào danh sách overlay
- `app/src/main/assets/www/styles.css`
  - avatar/header
  - modal/panel
  - hiệu ứng Linh Thạch
  - dark theme/mobile layout
- `app/build.gradle.kts`
  - tăng version ở pass cuối, không tăng trước khi build qua kiểm tra
- `README.md` và changelog/report

### File cần đọc nhưng hạn chế sửa

- `app/src/main/assets/www/v11-security.js`
- `app/src/main/assets/www/v15-chapter-clipper.js`
- `app/src/main/assets/www/v16-mobile-ui.js`
- `app/src/main/assets/www/v22-mobile-toolbar.js`

## 8. Thứ tự triển khai an toàn

### Pass A — Baseline và contract

- Sao chép project ra thư mục làm việc mới.
- Ghi checksum ZIP và trạng thái file.
- Không reset working tree.
- Xác nhận build baseline nếu môi trường cho phép.

### Pass B — Store native

- Tạo `AdventureProfileStore`.
- API snapshot JSON.
- API create/update/delete.
- API transaction cộng/trừ Linh Thạch.
- Kiểm tra dữ liệu đầu vào.

### Pass C — UI Hồ sơ offline

- Menu header Khách/Hồ sơ.
- Modal tạo hồ sơ.
- Chọn avatar preset.
- Trang hồ sơ.
- Tắt/xóa hồ sơ.
- Chưa nối thưởng Shield.

### Pass D — Shield event pipeline

- Chuẩn hóa các nơi chặn trong `MainActivity` qua một hàm duy nhất.
- Chống cộng trùng.
- Tăng thống kê.
- Chỉ thưởng khi có hồ sơ.
- Gửi payload về shell.

### Pass E — Hiệu ứng thưởng

- Toast/notification mới.
- Hiệu ứng `+1` bay về avatar.
- Tắt hiệu ứng.
- Không ảnh hưởng khi chưa có hồ sơ.

### Pass F — Chi tiêu thử nghiệm

- Chọn đúng một tác vụ nâng cao của Chapter Clipper.
- Trước khi chạy, native/store xác minh đủ Linh Thạch.
- Chỉ trừ khi tác vụ được chấp nhận bắt đầu.
- Có rollback nếu khởi chạy thất bại ngay.
- Người chưa tạo hồ sơ vẫn dùng chức năng hiện có theo chính sách được chốt; không được phá trải nghiệm cũ.

### Pass G — Hoàn thiện và build

- Kiểm tra dark/light theme.
- Kiểm tra xoay màn hình, Back, menu overlay.
- Kiểm tra clean install và update install.
- Build debug/release.
- Tăng version ở cuối.
- Đóng ZIP kết quả mới, không ghi đè ZIP gốc.

## 9. Tiêu chí chấp nhận

### Trạng thái Khách

- Cài mới mở app không xuất hiện modal ép tạo hồ sơ.
- Shield và mọi chức năng cũ hoạt động như trước.
- Chặn chuyển hướng chỉ hiện thông báo cũ, không có `+1`.
- Menu header vẫn là `lqlq Browser`.

### Tạo hồ sơ

- Nhấn menu header mới mở lời mời tạo hồ sơ.
- Không thể tạo với tên trống/quá dài.
- Chọn avatar và tạo xong header cập nhật ngay.
- Đóng/mở app dữ liệu vẫn còn.

### Nhận thưởng

- Một chuyển hướng main-frame bị chặn cộng đúng 1.
- Cùng một sự kiện không cộng hai lần qua hai callback WebView.
- Request quảng cáo con không làm số dư tăng hàng loạt.
- Hết giới hạn ngày không cộng thêm Linh Thạch nhưng vẫn tăng tổng bảo vệ.
- Chưa có hồ sơ thì không cộng.

### Hồ sơ

- Nhấn avatar mở đúng panel.
- Tắt hiệu ứng thì số dư vẫn tăng nhưng không chạy animation.
- Xóa hồ sơ không xóa lịch sử/dấu trang/trang đã lưu.
- Sau xóa, header trở về `lqlq Browser` và ngừng nhận Linh Thạch.

### Không hồi quy

- Nhiều thẻ native vẫn hoạt động.
- Đọc TXT/TTS không bị ảnh hưởng.
- Media nền và PiP không bị ảnh hưởng.
- Chapter Clipper vẫn hoạt động như trước khi chưa nối chi phí.
- Menu, drawer, dark theme và Back Android vẫn đúng.

## 10. Rủi ro cần kiểm soát

1. **Cộng trùng Shield:** xử lý native bằng cooldown/dedupe, không vá ở UI.
2. **MainActivity quá lớn:** tách store và logic profile ra class riêng.
3. **UI overlay che WebView:** khai báo panel/modal mới trong `android-glue.js` OVERLAYS.
4. **Mất dữ liệu:** dùng SharedPreferences, schema version và write atomic qua `edit()`.
5. **Farm Linh Thạch:** giới hạn ngày, cooldown và không thưởng request con.
6. **Người dùng hiểu nhầm tài khoản cloud:** dùng chữ `Hồ sơ Phiêu lưu` và ghi rõ `lưu trên thiết bị`.
7. **Phá người dùng cũ:** mọi gamification phải opt-in; trạng thái mặc định là Khách.

## 11. Điểm cần chốt trước Pass F

Chỉ còn một quyết định sản phẩm cần xác nhận sau khi Pass A–E ổn định:

- Tác vụ đầu tiên tiêu Linh Thạch là gì và giá bao nhiêu.

Khuyến nghị ban đầu:

- Không thu phí đọc truyện theo giờ.
- Không thu phí duyệt web hoặc Shield.
- Chỉ thu cho thao tác tự động/hàng loạt như lấy nhiều chương hoặc xử lý hàng loạt.
