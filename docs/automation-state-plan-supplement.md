# BỔ SUNG KẾ HOẠCH — PASS 0, MIGRATION, BẢNG TRANSITION

> Ghép vào sau mục 15 của kế hoạch gốc. Ba phần này lấp các lỗ hổng rủi ro cao nhất
> trước khi bắt đầu refactor lớn.

---

## A. PASS 0 — Sửa hồi quy an toàn (làm trước, ship riêng)

**Nguyên tắc: thứ tự liệt kê trong mục 16 KHÔNG phải thứ tự thi công.**
PASS 0 chỉ gồm các sửa lỗi *độc lập*, không cần state machine mới, không cần Room migration.
Pause thật (mục 8) và Stop thật (mục 9) để lại cho PASS 4.

### A.1. Việc làm trong PASS 0

1. **Bug activeStep bị ghi đè** (`v35-...js:3462-3463`)
   Hiện: gán `session.activeStep = session.pendingLabel;` rồi ngay dòng sau đè
   `session.activeStep = "Dang dung tac vu";`. → Xóa dòng 3463.

2. **Thu hẹp WAITING_USER** (`v35-...js:5103`)
   Hiện: `if (status.includes("WAIT")) return "WAITING_USER";`
   → Thay bằng whitelist tường minh: chỉ map đúng các mã cần người dùng thao tác
   (`WAITING_USER_REVIEW`, `WAITING_USER_IMAGE`, `WAITING_PERMISSION`, ...).
   Các mã `WAITING_PROVIDER`, `WAITING_RETRY` map về nhóm **Đang chạy**, KHÔNG phải WAITING_USER.

3. **Không để CANCELLED → DRAFT**
   Rà mọi chỗ set về DRAFT sau khi hủy trong `cancelPendingTask()` và luồng
   `taskState === "CANCELLED"` (quanh `v35-...js:3748`, `4096`, `4010`).
   Trạng thái sau Dừng hẳn phải là `CANCELLED`/`Đã dừng`, giữ nguyên artifact.

4. **Tách nhãn Pause vs Stop ở tầng UI (chưa đổi hành vi nền)**
   Tạm thời `pause-session` và `stop-session` VẪN có thể gọi cùng hàm hủy nền
   (vì pause thật chưa có), NHƯNG:
   - Sau `stop-session` → state `CANCELLED`.
   - Sau `pause-session` → state `PAUSED` + nút chính "Chạy tiếp phần còn thiếu".
   Đây là bước đệm để mục 16 #1 không bị chặn bởi mục 8. Ghi rõ TODO trỏ tới PASS 4.

### A.2. KHÔNG làm trong PASS 0
- Không đổi bridge sang `jobId`.
- Không xóa hàng đợi JS (đợi PASS 3).
- Không đụng Room schema.

### A.3. Tiêu chí xong PASS 0
- Dừng hẳn không bao giờ về Bản nháp.
- Không còn trạng thái "Dang dung tac vu" nhấp nháy đè nhãn chạy.
- Job `WAITING_PROVIDER` không hiện nút "Chờ bạn xử lý".

---

## B. MIGRATION — Room + dữ liệu trạng thái cũ (rủi ro cao nhất)

Hiện: `AutomationDatabase version = 2`, dùng `autoMigrations` + `exportSchema = true`
(có schema JSON trong `schemas/`). Thêm cột điều khiển ⇒ **bump lên version 3**.

### B.1. Cột mới trên `AutomationJobEntity`

| Cột              | Kiểu    | Default        | Ghi chú                                  |
| ---------------- | ------- | -------------- | ---------------------------------------- |
| `stateReason`    | TEXT?   | NULL           | Lý do WAITING_USER / PAUSED / FAILED     |
| `progressPercent`| INTEGER | 0              | Tiến độ thật 0–100                       |
| `queuePosition`  | INTEGER | -1             | -1 = không trong hàng đợi                |
| `activeWorkId`   | TEXT?   | NULL           | WorkManager work id đang chạy            |
| `dataState`      | TEXT    | 'EMPTY'        | EMPTY/PARTIAL/READY/STALE (trục dữ liệu) |
| `pauseRequested` | INTEGER | 0              | Cờ pause hợp tác                         |
| `cancelRequested`| INTEGER | 0              | Cờ cancel hợp tác                        |

> `dirtyFlags` chi tiết (giọng cũ / video cũ / thiếu ảnh) nên để bảng/cột riêng
> hoặc JSON, KHÔNG nhồi chung `dataState` — hai thứ khác altitude.

### B.2. Chiến lược migration schema
- **Vì tất cả cột mới đều có default an toàn** → có thể dùng `@AutoMigration(from = 2, to = 3)`.
  Kiểm tra schema JSON được sinh lại; nếu Room báo cần spec (đổi/xóa cột) thì viết
  `AutoMigrationSpec`. Ưu tiên auto-migration để không mất dữ liệu.
- **Tuyệt đối KHÔNG dùng** `fallbackToDestructiveMigration()` — sẽ xóa sạch project của user.
- Cập nhật `AutomationDatabaseMigrationTest` thêm case `2 → 3` (đã có khung test migration sẵn).

### B.3. Migration dữ liệu trạng thái đang "trôi nổi" (one-shot, chạy 1 lần sau update)
Đây là phần dễ mất trạng thái nhất khi user cập nhật app lúc đang có job dở.
Khi khởi động lần đầu sau update, `AutomationRunCoordinator` chạy `reconcileOnBoot()`:

1. Đọc mọi job trong Room.
2. Với mỗi job `RUNNING`:
   - Nếu `activeWorkId` NULL hoặc WorkManager không còn work đó → không có Worker thật:
     * Job có checkpoint dở → chuyển `PAUSED` (reason `APP_RESTARTED`).
     * Job không checkpoint → chuyển `QUEUED` để chạy lại từ bước dở.
3. Đọc `AutomationAsyncTaskStore` (SharedPreferences) 1 lần cuối để vá trạng thái
   còn thiếu vào Room, rồi **ngừng coi nó là nguồn** (mục 6 kế hoạch gốc).
4. Đọc `RuntimeJobStore` (JSON) tương tự nếu còn giá trị mới hơn Room.
5. Sau bước này: Room là nguồn duy nhất. Ghi cờ `migrationV3Done` để không chạy lại.

### B.4. Tiêu chí xong
- Cập nhật app khi đang có 1 job chạy dở → mở lại thấy job đó ở PAUSED/QUEUED, KHÔNG mất.
- Không job nào kẹt `RUNNING` mà không có Worker.

---

## C. BẢNG TRANSITION TƯỜNG MINH + `computeAllowedActions`

### C.1. Ma trận chuyển trạng thái hợp lệ (execution state)

Ký hiệu: `→` = được phép. Mọi chuyển KHÔNG có trong bảng đều bị guard chặn và log.

| Từ \ Đến        | DRAFT | QUEUED | RUNNING | PAUSING | PAUSED | WAITING_USER | FAILED | CANCELLING | CANCELLED | COMPLETED |
| --------------- | :---: | :----: | :-----: | :-----: | :----: | :----------: | :----: | :--------: | :-------: | :-------: |
| DRAFT           |   ·   |   →    |    →    |         |        |              |        |            |     →     |           |
| QUEUED          |       |   ·    |    →    |         |   →¹   |              |        |     →      |     →     |           |
| RUNNING         |       |        |    ·    |    →    |        |      →       |   →    |     →      |           |     →     |
| PAUSING         |       |        |         |    ·    |   →    |              |        |     →      |           |           |
| PAUSED          |       |   →²   |         |         |   ·    |              |        |            |     →     |           |
| WAITING_USER    |       |   →³   |         |         |        |      ·       |        |     →      |     →     |           |
| FAILED          |       |   →⁴   |         |         |        |              |   ·    |            |     →     |           |
| CANCELLING      |       |        |         |         |        |              |        |     ·      |     →     |           |
| CANCELLED       |       |   →⁵   |         |         |        |              |        |            |     ·     |           |
| COMPLETED       |       |   →⁵   |         |         |        |              |        |            |     →     |     ·     |

Chú thích:
1. QUEUED→PAUSED: gỡ khỏi hàng đợi do user Tạm dừng trước khi tới lượt (job chưa chạy).
2. PAUSED→QUEUED: user bấm **Tiếp tục**.
3. WAITING_USER→QUEUED: user đã xử lý xong, tự động vào lại hàng đợi.
4. FAILED→QUEUED: **Thử lại bước lỗi** hoặc **Chạy lại từ đầu**.
5. CANCELLED/COMPLETED→QUEUED: **Chạy lại từ đầu** hoặc **Chạy tiếp phần còn thiếu** (tạo lần chạy mới).

**Bất biến quan trọng (viết thành test):**
- KHÔNG có ô nào từ `CANCELLED → DRAFT`. (bug hiện tại)
- Chỉ `PAUSING → PAUSED` mới sinh ra PAUSED khi đang chạy — không nhảy thẳng RUNNING→PAUSED.
- Tại một thời điểm tối đa **một** job ở `RUNNING`.

### C.2. `computeAllowedActions` — một hàm thuần, dùng chung UI + Notification

Chữ ký:
```
fun computeAllowedActions(
    state: ExecutionState,
    dataState: DataState,
    reason: StateReason?
): Set<Action>
```

Ánh xạ (nguồn sự thật duy nhất — cả snapshot gửi JS và notification đều gọi hàm này):

| ExecutionState | allowedActions                                                  |
| -------------- | --------------------------------------------------------------- |
| DRAFT          | START, DELETE                                                   |
| QUEUED         | OPEN, REMOVE_FROM_QUEUE                                          |
| RUNNING        | OPEN, PAUSE, CANCEL                                              |
| PAUSING        | (khóa, chỉ OPEN)                                                 |
| PAUSED         | RESUME, EDIT, CANCEL, RESTART_FROM_BEGINNING, DELETE            |
| WAITING_USER   | (nút chính theo `reason`, xem C.3), CANCEL                       |
| FAILED         | RETRY_FAILED_STEP, VIEW_LOG, RESTART_FROM_BEGINNING, CANCEL, DELETE |
| CANCELLING     | (khóa, chỉ OPEN)                                                 |
| CANCELLED      | CONTINUE_MISSING_STEPS, RESTART_FROM_BEGINNING, DELETE          |
| COMPLETED      | REVIEW/EXPORT, DUPLICATE, REGENERATE_VIDEO, RESTART_FROM_BEGINNING, DELETE |

**Chồng lớp dataState** (độc lập execution state, chỉ ảnh hưởng nút "cập nhật"):
- `dataState == STALE` → thêm 1 nút cập nhật đúng phạm vi (mục 7 gốc):
  thiếu ảnh → `CONTINUE_MISSING_STEPS`("Hoàn thành ảnh còn thiếu");
  giọng cũ → `UPDATE_VOICE_AND_VIDEO`; chỉ video cũ → `UPDATE_VIDEO`.
- `dataState == READY` → KHÔNG hiện nút cập nhật.

### C.3. Nút chính theo `stateReason` khi WAITING_USER (mục 5 gốc)

| stateReason           | Action / nhãn nút        |
| --------------------- | ------------------------ |
| `NEED_REVIEW`         | REVIEW · "Kiểm tra video"|
| `NEED_IMAGE`          | "Chọn hoặc tạo ảnh"      |
| `NEED_CONFIG`         | "Thiết lập dịch vụ"      |
| `NEED_PERMISSION`     | "Cấp quyền"              |
| `NEED_PUBLISH_CONFIRM`| "Kiểm tra và đăng"       |
| `NEED_CONTENT_EDIT`   | "Mở phần cần sửa"        |
| `WEB_STEP_INTERRUPTED`| "Tiếp tục bước web"      |

### C.4. Vị trí đặt hàm
- Kotlin thuần trong lớp model (`automation/model/`), không phụ thuộc Android.
- Test đơn vị phủ toàn bộ ma trận C.1 + bảng C.2 + C.3.
- JavaScript KHÔNG được tự tính `allowedActions`; chỉ render theo mảng nhận từ snapshot.

---

## D. Bổ sung nhỏ vào các mục gốc

- **Mục 10 (hàng đợi):** đổi `ExistingWorkPolicy.APPEND_OR_REPLACE` (một QUEUE_NAME chung,
  tag `clientRequestId` — `AutomationBridge.kt:325-329`) sang **enqueue theo từng job**:
  `uniqueName = "job-$jobId"`, `ExistingWorkPolicy.KEEP` → chống double-worker
  (nghiệm thu "bấm 2 lần không tạo 2 Worker"). Thứ tự hàng đợi do coordinator quản lý
  bằng `queuePosition`, KHÔNG mượn hàng đợi ngầm của WorkManager.

- **Mục 11 (web step):** thêm heartbeat liveness. Controller web ghi `lastHeartbeatAt`
  vào Room mỗi vài giây khi còn sống. `reconcileOnBoot()` (B.3) coi job `RUNNING` mà
  heartbeat quá hạn / Activity đã hủy là chết → `PAUSED` reason `WEB_STEP_INTERRUPTED`.

- **Mục 13 (bridge):** giữ `clientRequestId` làm **idempotency key** chống double-tap,
  chỉ hạ vai trò (không còn là định danh dự án). Định danh dùng `jobId`/`projectId`.
