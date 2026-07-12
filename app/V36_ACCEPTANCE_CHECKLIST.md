# VIDEO WORKSPACE V36 — ACCEPTANCE CHECKLIST

## A. Không phá chức năng cũ

- [ ] V35 vẫn mở được bằng feature flag.
- [ ] Danh sách phiên cũ không mất.
- [ ] `linkedJobId` giữ nguyên.
- [ ] Gemini connectors không bị sửa ngoài scope.
- [ ] Image connectors không bị sửa ngoài scope.
- [ ] Voice providers không bị sửa ngoài scope.
- [ ] Renderer không bị sửa ngoài scope.
- [ ] Queue/background/notification vẫn hoạt động.
- [ ] Preview/export/publish vẫn hoạt động.

## B. Storyboard

- [ ] Mở phiên vào thẳng Storyboard.
- [ ] Hiển thị đúng thứ tự cảnh.
- [ ] Card cảnh có nội dung, query, ảnh, duration và status.
- [ ] Ảnh map bằng `sceneId`.
- [ ] Có placeholder cho cảnh thiếu ảnh.
- [ ] 20 cảnh vẫn cuộn mượt.
- [ ] Giao diện dùng tốt ở màn hình Android hẹp.

## C. Scene CRUD

- [ ] Sửa scene persist sau reload.
- [ ] Kill app/mở lại vẫn còn.
- [ ] Thêm cảnh sinh `sceneId` duy nhất.
- [ ] Xóa cảnh không ảnh hưởng artifact cảnh khác.
- [ ] Nhân bản cảnh sinh ID mới.
- [ ] Move cảnh không đổi `sceneId`.
- [ ] `ordinal` liên tục sau add/delete/move.
- [ ] Có chống ghi đè revision cũ.

## D. Invalidation

- [ ] Sửa `voiceText` chỉ stale voice/subtitle/video.
- [ ] Sửa `onScreenText` chỉ stale subtitle/video.
- [ ] Sửa query/prompt ảnh stale ảnh/video.
- [ ] Thay ảnh chỉ stale video.
- [ ] Thêm/xóa/move cảnh stale combined voice/video/metadata/review.
- [ ] Artifact cũ không bị xóa trước khi artifact mới thành công.
- [ ] UI giải thích rõ phần nào sẽ được giữ và phần nào cập nhật.

## E. Planner

- [ ] Có scene rồi thì không tự gọi lại Gemini.
- [ ] Thiếu ảnh một cảnh chỉ plan cảnh đó.
- [ ] Voice stale một cảnh chỉ plan TTS cảnh đó nếu provider hỗ trợ.
- [ ] Voice tổng được ghép lại khi cần.
- [ ] Video chỉ render khi dependency sẵn sàng.
- [ ] `REGENERATE_ALL` có confirm.
- [ ] UI hiển thị task plan trước khi chạy.

## F. Image theo cảnh

- [ ] Regenerate Cảnh 7 không thay ảnh cảnh khác.
- [ ] Import ảnh thủ công gắn đúng `sceneId`.
- [ ] Tạo ảnh lỗi vẫn giữ ảnh cũ.
- [ ] Có thể giữ ảnh stale để preview/hoàn tác.

## G. Voice theo cảnh

- [ ] Có artifact `VOICE_SCENE` theo `sceneId`.
- [ ] Sửa một cảnh chỉ gọi TTS cảnh đó với Android TTS WAV.
- [ ] Voice tổng đúng thứ tự cảnh.
- [ ] Duration từng cảnh cập nhật đúng.
- [ ] Provider non-WAV vẫn dùng fallback cũ an toàn.

## H. Ba workflow bắt buộc

### Tự động toàn bộ

- [ ] Nhập topic → chạy đến video mà không cần chỉnh tay.

### AI rồi sửa tay

- [ ] AI tạo cảnh.
- [ ] Sửa nội dung một cảnh.
- [ ] Thay ảnh một cảnh khác.
- [ ] Không gọi lại Gemini.
- [ ] Không tạo lại ảnh/voice không liên quan.

### Tự làm thủ công

- [ ] Tạo scene không cần Gemini.
- [ ] Tự nhập nội dung.
- [ ] Tự thêm một số ảnh.
- [ ] App hoàn thành phần thiếu, tạo voice và render.

## I. Chạy nền và khôi phục

- [ ] UI không đơ khi task chạy.
- [ ] Mở tab web khác được.
- [ ] Notification đúng task hiện tại.
- [ ] Pause/cancel/resume hoạt động.
- [ ] App kill giữa chừng không mất scene/artifact đã hoàn thành.
- [ ] Mở lại hiển thị đúng pending/stale state.

## J. Điều kiện bật V36 mặc định

- [ ] Tất cả mục critical phía trên đạt.
- [ ] Không còn lỗi crash/ANR liên quan workspace.
- [ ] Có rollback V35 trong ít nhất một phiên bản.
- [ ] Có migration test với job V35 cũ.
- [ ] Có build debug thành công.
