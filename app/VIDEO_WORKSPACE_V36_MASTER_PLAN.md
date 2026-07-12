# VIDEO WORKSPACE V36 — MASTER PLAN

## 1. Mục tiêu

Làm lại **Không gian làm việc tạo video** của LQLQ Browser theo mô hình **Storyboard theo từng cảnh**, cho phép người dùng:

- Chỉ nhập chủ đề rồi để hệ thống tự hoàn thành toàn bộ.
- Dừng ở bất kỳ bước nào để kiểm tra, sửa tay, thêm/xóa/đổi thứ tự cảnh.
- Tự viết toàn bộ nội dung, tự thêm ảnh, rồi để app chỉ làm phần còn thiếu.
- Sửa một phần của video đã hoàn thành mà không gọi lại Gemini hoặc làm lại các tài nguyên không liên quan.
- Bấm một nút để app phân tích dữ liệu hiện tại, xác định phần còn thiếu hoặc đã lỗi thời, rồi tiếp tục đúng từ đó.

Nguyên tắc trung tâm:

> Tự động và chỉnh tay không phải hai workflow riêng. Chúng cùng thao tác trên một dự án video. Dữ liệu hiện tại của từng cảnh luôn là nguồn sự thật; tự động chỉ hoàn thành phần thiếu hoặc cập nhật phần đã lỗi thời.

---

## 2. Phạm vi khóa cứng

### 2.1. Không được viết lại chức năng đã có

Các phần sau giữ nguyên thuật toán và provider hiện tại:

- Gemini API/Gemini Web tạo nội dung.
- Parser chia cảnh.
- Pexels, Openverse, Pinterest, Google Images, AI image connectors.
- Android TTS, Edge, Azure, FPT, Vbee, Viettel và registry giọng đọc.
- `WavAudioAssembler` và logic ghép audio hiện có.
- `AndroidNativeSlideshowVideoRenderer` và các renderer hiện có.
- Metadata, review, publish YouTube.
- WorkManager, hàng chờ chạy nền, notification.
- Room database và workflow canonical hiện có.
- Cơ chế xuất MP4 và xem trước video.

### 2.2. Được phép thay đổi hoặc bổ sung

- Giao diện **Không gian làm việc**.
- Mô hình trạng thái chỉnh sửa của cảnh.
- API thêm/sửa/xóa/nhân bản/đổi vị trí cảnh.
- API thay ảnh hoặc tạo lại tài nguyên cho một cảnh.
- Lớp xác định tài nguyên nào `MISSING`, `READY`, `STALE`, `RUNNING`, `FAILED`.
- Bộ lập kế hoạch chạy tiếp dựa trên dữ liệu hiện có.
- Lưu voice artifact theo từng cảnh, đồng thời vẫn tạo voice tổng cho renderer.
- Lớp bridge/service mới phục vụ workspace.

### 2.3. Quy tắc an toàn

- Không xóa `v35-video-automation-workspace.js` trong giai đoạn phát triển.
- Không đổi tên hoặc xóa API bridge cũ trước khi V36 đạt kiểm thử hồi quy.
- Không thay đổi schema Room trong PASS đầu.
- Không để `AutomationFacade.kt` tiếp tục chứa toàn bộ logic workspace mới.
- Mỗi PASS phải build được hoặc nêu rõ vì sao chưa thể build với gói `src` hiện có.

---

## 3. Hiện trạng thực tế của `src11.zip`

### 3.1. Frontend

File chính:

- `src/main/assets/www/v35-video-automation-workspace.js`: khoảng 4.155 dòng.
- Được nạp trong `index.html` sau `v33-automation-center.js` và ghi đè `window.LqlqAutomationCenter`.
- Session UI đang lưu ở localStorage với key `lqlq-video-automation-sessions-v35`.

Các điểm hiện có cần giữ tương thích:

- `window.LqlqAutomationCenter.openFromTools()`.
- Danh sách phiên và liên kết `session.linkedJobId`.
- Poll job và task nền.
- Cài đặt content/image/voice/video/publish/background.
- Các luồng `continueAutoFromContent()`, `rerunVoiceAndVideo()`, export, preview, publish.

Các vấn đề hiện tại:

1. `generatedText` và `scenePrompts` cùng được hiển thị như hai nguồn nội dung.
2. `saveSceneText()` chỉ cập nhật ba trường rồi yêu cầu chạy lại voice + video toàn bộ.
3. `retryImageStep()` làm lại toàn bộ bước ảnh và xóa tất cả ảnh/video cũ.
4. `retryVoiceStep()` làm lại toàn bộ giọng.
5. Menu “Không gian làm việc” chia nội dung, ảnh, giọng và video thành nhiều trang rời, trong khi thao tác thật cần xoay quanh từng cảnh.
6. Nút “Chạy Tự động” chưa thể hiện rõ nó sẽ gọi lại nội dung hay chỉ làm phần thiếu.

### 3.2. Backend

Các file lớn:

- `AutomationFacade.kt`: khoảng 4.070 dòng.
- `AutomationBridge.kt`: khoảng 1.226 dòng.

Dữ liệu runtime hiện được lưu bằng:

- `RuntimeAutomationJob`, là data class private trong `AutomationFacade.kt`.
- `RuntimeJobStore`, lưu JSON tại `filesDir/automation-runtime-jobs`.

Cảnh hiện dùng:

- `ScenePrompt` trong `automation/image/ScenePromptGeneration.kt`.
- Các trường: `sceneId`, `ordinal`, `summary`, `visualPrompt`, `negativePrompt`, `aspectRatio`, `voiceText`, `onScreenText`, `plannedDurationMs`, `stockSearchQuery`, `visualDirection`.

Artifact hiện có `sceneId` và `ordinal`, đây là nền tảng tốt để chuyển sang thao tác theo cảnh.

Giọng hiện tại:

- `executeVoiceStage()` đã gọi TTS theo từng cảnh đối với WAV.
- Nhưng chỉ lưu artifact tổng loại `VOICE`.
- Vì không lưu voice từng cảnh, sửa một cảnh vẫn phải gọi TTS lại tất cả cảnh.

---

## 4. Kiến trúc mục tiêu

```text
Video Project
├── Project Settings
├── Scenes (nguồn dữ liệu chính)
│   ├── Scene 1
│   │   ├── Content
│   │   ├── Image state + artifact
│   │   ├── Voice state + artifact
│   │   └── Revision/hash
│   ├── Scene 2
│   └── ...
├── Combined outputs
│   ├── Combined voice
│   ├── Subtitle/timeline
│   ├── Render plan
│   └── Final video
├── Metadata / Review / Publish
└── Execution plan + logs
```

### 4.1. Nguồn sự thật

Sau khi AI tạo xong:

- `scenePrompts` hoặc model scene kế nhiệm là nguồn dữ liệu nội dung chính.
- `generatedText` chỉ là bản raw do AI trả về, dùng cho lịch sử/debug hoặc migration.
- Voice, phụ đề, render plan và metadata phải được dựng từ danh sách cảnh hiện tại.

### 4.2. Không tách “tự động” và “thủ công”

- Tự động: planner tìm phần thiếu/lỗi thời và thực hiện.
- Thủ công: người dùng sửa cùng dữ liệu cảnh đó.
- Sau khi sửa, planner tiếp tục từ dữ liệu mới.

---

## 5. Mô hình dữ liệu V36

### 5.1. Trạng thái tài nguyên

```kotlin
enum class WorkspaceResourceStatus {
    MISSING,
    READY,
    STALE,
    RUNNING,
    FAILED
}
```

Ý nghĩa:

- `MISSING`: chưa có tài nguyên.
- `READY`: có tài nguyên và phù hợp với input hiện tại.
- `STALE`: có tài nguyên nhưng input đã thay đổi.
- `RUNNING`: đang tạo.
- `FAILED`: lần tạo gần nhất thất bại.

### 5.2. Scene model mở rộng

Không bắt buộc sửa trực tiếp `ScenePrompt` ngay ở PASS đầu. Có thể tạo wrapper:

```kotlin
data class VideoWorkspaceScene(
    val sceneId: String,
    val ordinal: Int,
    val summary: String,
    val voiceText: String,
    val onScreenText: String,
    val stockSearchQuery: String,
    val visualPrompt: String,
    val negativePrompt: String?,
    val visualDirection: String,
    val aspectRatio: String,
    val plannedDurationMs: Long,

    val contentRevision: Long,
    val imageRevision: Long,
    val voiceRevision: Long,

    val imageStatus: WorkspaceResourceStatus,
    val voiceStatus: WorkspaceResourceStatus,
    val videoStatus: WorkspaceResourceStatus,

    val imageInputHash: String?,
    val voiceInputHash: String?,
    val lastError: String?
)
```

### 5.3. Hash phụ thuộc

Hash dùng để biết artifact còn hợp lệ hay không.

```text
imageInputHash = hash(
  stockSearchQuery,
  visualPrompt,
  negativePrompt,
  aspectRatio,
  imageProviderId,
  imageModel
)

voiceInputHash = hash(
  voiceText,
  voiceProviderId,
  voiceId,
  locale,
  speechRate,
  pitch,
  outputFormat
)

videoInputHash = hash(
  ordered sceneIds,
  each scene image artifact id/hash,
  each scene voice artifact id/hash,
  onScreenText,
  duration,
  aspect ratio,
  quality,
  background mode,
  motion mode,
  subtitle color,
  background music settings
)
```

Trong giai đoạn đầu có thể dùng revision thay hash. Bản ổn định nên dùng hash để tránh đánh dấu stale sai.

### 5.4. Artifact mới

Giữ artifact cũ và bổ sung:

```text
VOICE_SCENE       sceneId bắt buộc
VOICE_COMBINED    sceneId = null
```

Tương thích ngược:

- Artifact cũ `VOICE` được xem như `VOICE_COMBINED` legacy.
- Renderer hiện tại vẫn nhận voice tổng.
- Chỉ planner/workspace dùng `VOICE_SCENE` để tối ưu cập nhật cục bộ.

### 5.5. Workspace state

```kotlin
data class VideoWorkspaceState(
    val jobId: String,
    val topic: String,
    val scenes: List<VideoWorkspaceScene>,
    val projectSettings: VideoWorkspaceProjectSettings,
    val aggregateStatus: VideoWorkspaceAggregateStatus,
    val recommendedAction: VideoWorkspaceAction,
    val pendingChanges: List<WorkspaceChangeSummary>,
    val executionPlan: WorkspaceExecutionPlan?
)
```

---

## 6. Quy tắc invalidation

### 6.1. Ma trận bắt buộc

| Thay đổi | Giữ nguyên | Đánh dấu cần cập nhật |
|---|---|---|
| Sửa `voiceText` | Ảnh cảnh | Voice cảnh, voice tổng, phụ đề, video; metadata tùy policy |
| Sửa `onScreenText` | Ảnh, voice cảnh | Phụ đề/lớp chữ, video |
| Sửa `stockSearchQuery` | Nội dung, voice | Ảnh cảnh, video |
| Sửa `visualPrompt` | Nội dung, voice | Ảnh cảnh, video |
| Thay ảnh thủ công | Nội dung, voice | Video |
| Đổi voice/provider/rate/pitch | Nội dung, ảnh | Voice các cảnh liên quan, voice tổng, video |
| Thêm cảnh | Tất cả artifact của cảnh cũ | Tài nguyên cảnh mới, voice tổng, video, metadata/review |
| Xóa cảnh | Artifact cảnh khác | Voice tổng, video, metadata/review |
| Đổi thứ tự cảnh | Ảnh/voice từng cảnh | Voice tổng/timeline, video, metadata/review |
| Đổi aspect ratio | Nội dung/voice | Ảnh AI nếu provider phụ thuộc tỷ lệ; render plan, video |
| Đổi motion/background/subtitle color | Nội dung, ảnh, voice | Render plan, video |
| Đổi nhạc nền | Nội dung, ảnh, voice | Video |

### 6.2. Không xóa artifact ngay khi stale

Khi input thay đổi:

- Artifact cũ vẫn giữ để preview hoặc hoàn tác.
- Trạng thái chuyển `STALE`.
- Chỉ khi tạo artifact mới thành công mới thay active artifact.
- Nếu tạo mới thất bại, artifact cũ vẫn còn và UI báo “Bản hiện tại đã cũ”.

### 6.3. Thông báo thay đổi

Ví dụ:

> Đã sửa lời đọc Cảnh 4. Ảnh được giữ nguyên. Giọng Cảnh 4, voice tổng và video cần cập nhật.

---

## 7. Bộ lập kế hoạch chạy tiếp

### 7.1. Run mode

```kotlin
enum class WorkspaceRunMode {
    COMPLETE_MISSING,
    UPDATE_STALE,
    COMPLETE_MISSING_AND_STALE,
    REGENERATE_ALL
}
```

`REGENERATE_ALL` nằm trong menu nâng cao, có xác nhận, không đặt làm nút chính.

### 7.2. Target

```kotlin
enum class WorkspaceTarget {
    CONTENT,
    IMAGES,
    VOICE,
    VIDEO,
    METADATA,
    REVIEW_READY,
    PUBLISH_READY
}
```

### 7.3. Task model

```kotlin
sealed interface WorkspaceTask {
    data class GenerateContentAndScenes(...)
    data class GenerateSceneImage(val sceneId: String, ...)
    data class GenerateSceneVoice(val sceneId: String, ...)
    data class AssembleCombinedVoice(...)
    data class BuildSubtitleTimeline(...)
    data class RenderVideo(...)
    data class GenerateMetadata(...)
    data class BuildReviewState(...)
}
```

### 7.4. Planner algorithm

Pseudo logic:

```text
1. Đọc workspace state hiện tại.
2. Nếu chưa có cảnh:
   - Nếu người dùng yêu cầu CONTENT trở lên và có topic -> thêm GenerateContentAndScenes.
   - Nếu không có topic -> yêu cầu người dùng nhập nội dung/cảnh.
3. Với từng cảnh:
   - Nếu ảnh MISSING hoặc STALE và target >= IMAGES -> GenerateSceneImage.
   - Nếu voice MISSING hoặc STALE và target >= VOICE -> GenerateSceneVoice.
4. Nếu có bất kỳ voice cảnh thay đổi hoặc voice tổng thiếu/stale -> AssembleCombinedVoice.
5. Nếu video thiếu/stale và target >= VIDEO:
   - Chỉ thêm RenderVideo khi tất cả cảnh có ảnh dùng được và voice tổng dùng được.
6. Nếu metadata thiếu/stale và target >= METADATA -> GenerateMetadata.
7. Không thêm task gọi Gemini khi cảnh đã tồn tại, trừ khi người dùng bấm rõ “Tạo lại nội dung và cảnh”.
```

### 7.5. Nút chính theo trạng thái

Ví dụ:

- Chưa có cảnh: `Tạo nội dung và cảnh`.
- Có cảnh nhưng thiếu 4 ảnh: `Tạo 4 ảnh còn thiếu`.
- Sửa voiceText cảnh 3: `Cập nhật giọng Cảnh 3 và video`.
- Có đủ dữ liệu, chưa có video: `Hoàn thành video`.
- Video stale: `Render lại video`.
- Không còn việc: `Video đã cập nhật`.

---

## 8. Thiết kế giao diện V36

### 8.1. Điều hướng chính

Khi mở một phiên, vào thẳng Storyboard. Không mở sheet “Không gian làm việc” làm bước trung gian.

```text
┌──────────────────────────────────────┐
│ ‹  Tên phiên                    ⋮  × │
│ Bản nháp · 2 thay đổi chưa cập nhật │
├──────────────────────────────────────┤
│ Nội dung ✓  Ảnh !  Giọng ✓  Video ! │
├──────────────────────────────────────┤
│ Chủ đề · 9:16 · 120 giây · 12 cảnh  │
│                                [Sửa] │
├──────────────────────────────────────┤
│ CẢNH 1                    8,4 giây ✓ │
│ [Ảnh]  Tiêu đề cảnh                  │
│        Lời đọc rút gọn...            │
│        Từ khóa: red rose garden      │
│                     [Sửa] [⋮]        │
├──────────────────────────────────────┤
│ CẢNH 2                    Thiếu ảnh  │
│ [ + ]  Nội dung cảnh...              │
│                         [Tạo ảnh]     │
├──────────────────────────────────────┤
│               ＋ Thêm cảnh           │
├──────────────────────────────────────┤
│ [Cập nhật 2 thay đổi] [Hoàn thành]  │
└──────────────────────────────────────┘
```

### 8.2. Thanh công đoạn

Cuộn ngang:

- Nội dung
- Hình ảnh
- Giọng đọc
- Video
- Metadata
- Kiểm duyệt
- Đăng

Ký hiệu:

- `✓`: READY.
- `○`: MISSING.
- `!`: STALE hoặc cần xử lý.
- `…`: RUNNING.
- `×`: FAILED.

Chạm công đoạn sẽ cuộn/lọc đến phần liên quan, không chuyển sang một hệ thống trang rời.

### 8.3. Card cảnh

Card cảnh phải có:

- `ordinal` và trạng thái.
- Thumbnail visual hiện tại.
- `onScreenText` hoặc summary.
- `voiceText` rút gọn.
- `stockSearchQuery`.
- Thời lượng.
- Badge thiếu/stale/lỗi.
- Nút sửa.
- Menu: tạo lại ảnh, tạo lại giọng, nhân bản, chèn trước/sau, xóa.

### 8.4. Scene editor toàn màn hình

Tab **Nội dung**:

- Tiêu đề/chữ hiển thị.
- Lời đọc.
- Mô tả cảnh.
- Thời lượng dự kiến.

Tab **Hình ảnh**:

- Preview ảnh hiện tại.
- Từ khóa tìm kiếm.
- Prompt hình ảnh.
- Negative prompt.
- Hướng hình ảnh.
- Tìm ảnh khác.
- Tạo ảnh lại.
- Chọn ảnh từ máy.
- Giữ ảnh hiện tại.

Tab **Giọng đọc**:

- Nghe voice cảnh.
- Provider/voice/rate/pitch kế thừa cài đặt chung hoặc override.
- Tạo lại giọng cảnh.
- Chọn file âm thanh thủ công.

Nút cuối:

- `Hủy`.
- `Lưu`.
- `Lưu và cập nhật`.

### 8.5. Thêm cảnh

Cho phép:

- Thêm cuối.
- Chèn trước/sau cảnh đang chọn.
- Nhân bản cảnh.

Validation tối thiểu:

- `voiceText` hoặc nội dung mô tả không được rỗng hoàn toàn.
- Muốn tự tìm/tạo ảnh thì cần `stockSearchQuery` hoặc `visualPrompt`.
- Nếu đã chọn ảnh thủ công thì từ khóa có thể rỗng.
- `sceneId` không trùng.
- Sau thêm/xóa/move phải chuẩn hóa `ordinal` liên tục.

### 8.6. Menu phụ

Menu ba chấm của phiên:

- Cài đặt dự án.
- Nhật ký.
- Thông tin phiên.
- Tạo lại toàn bộ.
- Xóa phiên.

Metadata, review và publish vẫn có section riêng, nhưng không chiếm điều hướng chính của quá trình dựng cảnh.

---

## 9. API bridge V36

Các API mới đề xuất:

```text
getVideoWorkspaceState
updateVideoScene
addVideoScene
deleteVideoScene
duplicateVideoScene
moveVideoScene
replaceSceneImage
regenerateSceneImage
regenerateSceneVoice
createWorkspaceExecutionPlan
executeWorkspacePlan
```

### 9.1. Contract mẫu

#### `getVideoWorkspaceState`

Request:

```json
{"jobId":"job-123"}
```

Response:

```json
{
  "ok": true,
  "workspace": {
    "jobId": "job-123",
    "scenes": [],
    "recommendedAction": {},
    "pendingChanges": []
  }
}
```

#### `updateVideoScene`

Request:

```json
{
  "jobId": "job-123",
  "sceneId": "scene-03",
  "patch": {
    "voiceText": "...",
    "onScreenText": "...",
    "stockSearchQuery": "...",
    "visualPrompt": "..."
  },
  "expectedRevision": 7
}
```

Response phải trả:

- Scene mới.
- Revision mới.
- Danh sách tài nguyên bị stale.
- Recommended action mới.

#### `moveVideoScene`

```json
{
  "jobId": "job-123",
  "sceneId": "scene-07",
  "targetIndex": 2
}
```

#### `createWorkspaceExecutionPlan`

```json
{
  "jobId": "job-123",
  "mode": "COMPLETE_MISSING_AND_STALE",
  "target": "VIDEO"
}
```

Response chứa task preview để UI hiển thị trước khi chạy.

### 9.2. Bridge chỉ làm adapter

`AutomationBridge.kt` chỉ:

- Parse JSON.
- Validate trường bắt buộc.
- Gọi `VideoWorkspaceService`.
- Serialize response.

Không đưa dependency logic hoặc planner vào bridge.

---

## 10. Cấu trúc file mục tiêu

### 10.1. Frontend

```text
src/main/assets/www/
├── v35-video-automation-workspace.js      # giữ làm rollback
├── v36-video-workspace.js                 # shell + routing + lifecycle
├── v36-video-workspace-store.js           # state, normalization, selectors
├── v36-video-storyboard.js                # list/card cảnh
├── v36-video-scene-editor.js              # editor toàn màn hình
├── v36-video-workspace-actions.js          # bridge commands + task polling
└── v36-video-workspace.css                 # style riêng, không nhồi thêm styles.css
```

Nếu muốn giảm số file trong PASS đầu, có thể bắt đầu với ba file:

- `v36-video-workspace.js`
- `v36-video-workspace-store.js`
- `v36-video-workspace.css`

Sau khi ổn mới tách storyboard/editor.

### 10.2. Kotlin

```text
src/main/java/com/lqlq/browser/automation/workspace/
├── VideoWorkspaceModels.kt
├── VideoWorkspaceService.kt
├── SceneDependencyInvalidator.kt
├── VideoWorkspaceExecutionPlanner.kt
├── WorkspaceArtifactResolver.kt
└── WorkspaceMigration.kt
```

Giai đoạn voice theo cảnh:

```text
src/main/java/com/lqlq/browser/automation/voice/
├── SceneVoiceArtifactService.kt
└── CombinedVoiceAssembler.kt
```

---

## 11. Chiến lược migration

### 11.1. Feature flag frontend

Không sửa thẳng V35 thành V36.

Trong `index.html` nạp V36 sau V35:

```html
<script src="v35-video-automation-workspace.js"></script>
<script src="v36-video-workspace-store.js"></script>
<script src="v36-video-workspace.js"></script>
```

V36 cần giữ reference V35 trước khi ghi đè global:

```javascript
const legacyAutomationCenter = window.LqlqAutomationCenter;
```

Feature flag có thể lưu ở localStorage:

```text
lqlq-video-workspace-version = v36 | v35
```

Mặc định trong thời gian dev: V35. Sau khi test: V36.

### 11.2. Session localStorage

Không đổi key ngay.

- Đọc key V35 để giữ danh sách phiên hiện tại.
- V36 có thể tạo key `lqlq-video-automation-sessions-v36` nhưng phải migrate một lần và lưu marker.
- Không nhân đôi job native.
- `linkedJobId` phải giữ nguyên.

### 11.3. Runtime JSON

Thêm trường mới theo kiểu optional/default.

Ví dụ:

```json
{
  "workspaceSchemaVersion": 1,
  "sceneStates": {},
  "activeArtifactBindings": {}
}
```

Khi đọc file cũ:

- `workspaceSchemaVersion` thiếu -> xem là legacy.
- Dựng status từ dữ liệu hiện có.
- Scene có ảnh cùng `sceneId` -> image READY.
- Scene chưa có ảnh -> MISSING.
- Nếu có VOICE legacy nhưng không có `VOICE_SCENE` -> combined voice READY, voice scene trạng thái LEGACY/UNKNOWN; không ép tạo lại cho đến khi một cảnh thay đổi.
- Nếu có `VIDEO_MP4` -> video READY cho tới khi input bị sửa.

### 11.4. Rollback

V35 phải vẫn mở được phiên sau khi V36 chạy.

Trong giai đoạn đầu:

- Không xóa trường cũ.
- Không đổi artifact type `VOICE` ngay; thêm alias/migration.
- Không làm V35 phụ thuộc các file V36.

---

## 12. Kế hoạch PASS chi tiết

## PASS 00 — Baseline, tài liệu và feature flag

Mục tiêu:

- Đóng băng V35.
- Tạo shell V36 song song.
- Không thay đổi dữ liệu hoặc pipeline.

File được phép sửa:

- `index.html`.
- Thêm file V36 mới.
- Có thể thêm changelog.

File không được sửa:

- `AutomationFacade.kt`.
- `AutomationBridge.kt`.
- Connector, renderer, voice, database.

Nghiệm thu:

- `window.LqlqAutomationCenter.openFromTools()` vẫn hoạt động.
- Có thể chuyển V35/V36 bằng flag.
- V36 hiển thị danh sách phiên cũ.
- Không mất localStorage.
- Không có lỗi console làm hỏng app chính.

---

## PASS 01 — Store và snapshot chỉ đọc

Mục tiêu:

- V36 đọc job hiện tại và chuẩn hóa thành view model Storyboard.
- Chưa cho sửa.

Thực hiện:

- Tạo store độc lập.
- Map `scenePrompts` + artifacts theo `sceneId`.
- Tính trạng thái hiển thị tạm thời từ snapshot hiện có.
- Hiển thị card cảnh read-only.

Nghiệm thu:

- Cảnh đúng thứ tự.
- Thumbnail đúng `sceneId`, không chỉ theo index.
- Hiển thị cảnh thiếu ảnh.
- Hiển thị số cảnh, duration và trạng thái video.
- V35 và V36 cho cùng jobId.

---

## PASS 02 — Scene CRUD native an toàn

Mục tiêu:

- Thêm/sửa/xóa/nhân bản/move cảnh.
- Persist qua kill app.

Thêm package `automation/workspace` và service.

API tối thiểu:

- `getVideoWorkspaceState`.
- `updateVideoScene`.
- `addVideoScene`.
- `deleteVideoScene`.
- `duplicateVideoScene`.
- `moveVideoScene`.

Nghiệm thu:

- Reload UI vẫn còn dữ liệu.
- Kill app/mở lại vẫn còn.
- `sceneId` ổn định sau move.
- `ordinal` chuẩn hóa.
- Artifact ảnh theo `sceneId` không bị đổi nhầm khi move.
- Xóa cảnh không xóa artifact cảnh khác.

---

## PASS 03 — Scene editor và validation

Mục tiêu:

- Editor đầy đủ cho nội dung và hình ảnh metadata.
- Chưa cần chạy lại riêng từng cảnh.

Nghiệm thu:

- Lưu patch thay vì ghi đè toàn scene.
- Có `expectedRevision` để tránh ghi đè khi task nền vừa cập nhật.
- UI báo field lỗi rõ ràng.
- Có confirm khi đóng editor còn thay đổi chưa lưu.

---

## PASS 04 — Dependency invalidation

Mục tiêu:

- Mọi chỉnh sửa trả về đúng danh sách stale.
- Không xóa artifact cũ ngay.

Nghiệm thu bằng ma trận ở Mục 6.

Bắt buộc có unit test cho:

- Sửa `voiceText`.
- Sửa query ảnh.
- Thay ảnh.
- Thêm/xóa/move cảnh.
- Đổi video settings.

---

## PASS 05 — Planner chỉ lập kế hoạch

Mục tiêu:

- Planner tạo task list nhưng chưa thực thi executor mới.
- UI hiển thị “app sẽ làm gì”.

Nghiệm thu:

- Có cảnh rồi thì planner không tự gọi Gemini.
- Chỉ thiếu ảnh cảnh 3 thì plan không chứa cảnh khác.
- Chỉ video stale thì plan chỉ chứa render-related tasks.
- `REGENERATE_ALL` phải khác rõ với mode mặc định.

---

## PASS 06 — Tạo lại ảnh theo sceneId

Mục tiêu:

- Tận dụng connector ảnh hiện có nhưng chỉ xử lý cảnh được chọn.

Không dùng index làm danh tính chính.

Nghiệm thu:

- Tạo lại ảnh Cảnh 7 không thay ảnh Cảnh 1–6 và 8–N.
- Thất bại vẫn giữ ảnh cũ.
- Import ảnh thủ công gắn trực tiếp `sceneId`.
- Video chuyển stale; voice giữ ready.

---

## PASS 07 — Voice artifact từng cảnh

Mục tiêu:

- Lưu kết quả TTS từng cảnh.
- Ghép voice tổng từ cache scene voice.

Refactor `executeVoiceStage()` theo hướng:

1. Với từng cảnh, tính `voiceInputHash`.
2. Dùng lại `VOICE_SCENE` READY có hash phù hợp.
3. Chỉ gọi connector cho scene missing/stale.
4. Ghép tất cả scene WAV theo ordinal.
5. Lưu/update `VOICE_COMBINED`.
6. Cập nhật duration từng cảnh.

Provider không hỗ trợ WAV:

- Giữ fallback tổng hiện tại ở giai đoạn đầu.
- UI ghi rõ provider đó chưa hỗ trợ cập nhật voice cục bộ.
- Không làm hỏng provider đang hoạt động.

Nghiệm thu:

- Sửa Cảnh 5 chỉ gọi TTS cho Cảnh 5 với Android TTS WAV.
- Voice tổng có đúng thứ tự sau move cảnh.
- Xóa cảnh loại voice scene đó khỏi bản ghép mới.

---

## PASS 08 — Planner executor và nút hành động thông minh

Mục tiêu:

- Thực thi plan bằng WorkManager/hàng chờ hiện có.
- Nút chính đổi nhãn theo plan.

Nghiệm thu:

- App chạy nền bình thường.
- Pause/cancel không làm mất dữ liệu.
- Mỗi task cập nhật status theo scene.
- Mở phiên khác không làm treo UI.

---

## PASS 09 — Render, metadata, review theo stale state

Mục tiêu:

- Render dùng dữ liệu cảnh hiện tại.
- Metadata/review được invalidation đúng.

Nghiệm thu:

- Sửa ảnh chỉ render lại video, không gọi Gemini/TTS.
- Sửa lời đọc chỉ gọi TTS scene liên quan + ghép + render.
- Metadata không bị tạo lại vô cớ nếu policy nói không phụ thuộc thay đổi nhỏ.

---

## PASS 10 — Hoàn thiện UX, migration và bật mặc định

Mục tiêu:

- V36 mặc định sau khi test.
- V35 vẫn là fallback trong ít nhất một phiên bản.

Nghiệm thu đầy đủ theo checklist riêng.

---

## 13. Quy tắc giao việc cho Codex

Mỗi prompt chỉ thực hiện một PASS hoặc một phần nhỏ của PASS.

Codex phải:

1. Đọc các file liên quan trước khi sửa.
2. Nêu kế hoạch ngắn trong log làm việc.
3. Không sửa file ngoài danh sách cho phép nếu chưa giải thích.
4. Không xóa API cũ.
5. Không đổi provider/renderer.
6. Chạy build/test có thể chạy trong project đầy đủ.
7. Báo danh sách file thêm/sửa, API thêm, test chạy, lỗi còn lại.
8. Tạo commit hoặc checkpoint sau mỗi PASS nếu repo có Git.

Không giao một prompt “làm hết V36”.

---

## 14. Definition of Done toàn dự án

V36 chỉ được xem là hoàn thành khi cả ba workflow sau dùng chung một mô hình:

### Workflow A — Tự động toàn bộ

```text
Nhập chủ đề
→ Tạo nội dung và cảnh
→ Ảnh
→ Voice
→ Video
→ Metadata
→ Review
```

### Workflow B — AI rồi sửa tay

```text
AI tạo cảnh
→ Người dùng sửa cảnh 4, thay ảnh cảnh 7
→ Planner chỉ cập nhật voice cảnh 4, voice tổng và video
→ Không gọi lại Gemini
→ Không thay ảnh cảnh khác
```

### Workflow C — Tự làm nội dung

```text
Người dùng tự thêm cảnh, tự viết nội dung, tự thêm một số ảnh
→ Planner tạo ảnh phần thiếu
→ Tạo voice
→ Render video
```

Yêu cầu cuối:

- Không có hai nguồn nội dung cạnh tranh.
- Không tạo lại toàn bước khi chỉ một cảnh thay đổi.
- Không mất dữ liệu sau kill app.
- Không làm hỏng chức năng V35/pipeline cũ.
- Người dùng luôn biết app sắp làm gì trước khi chạy.
