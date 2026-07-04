# LqlqTtsBridge — hợp đồng nối Android TextToSpeech

File HTML v0.17 đã chuẩn bị sẵn giao diện JavaScript cho Android WebView.

## Tên JavaScript interface

Nên đăng ký:

```kotlin
webView.addJavascriptInterface(lqlqTtsBridge, "LqlqTtsBridge")
```

## Các hàm JavaScript đang tìm

```text
getTtsEngineInfo(): String
getTtsVoices(): String
speakText(text: String, settingsJson: String)
pauseTts()
resumeTts()
stopTts()
openTtsSettings()
```

## getTtsEngineInfo

Trả JSON:

```json
{
  "name": "Speech Recognition & Synthesis from Google",
  "label": "Nhận dạng và tổng hợp giọng nói của Google",
  "packageName": "com.google.android.tts"
}
```

## getTtsVoices

Trả mảng JSON:

```json
[
  {
    "id": "vi-vn-x-gft-local",
    "name": "Tiếng Việt - 2",
    "lang": "vi-VN",
    "default": true,
    "networkRequired": false,
    "engine": "Google",
    "packageName": "com.google.android.tts"
  }
]
```

Danh sách nên lấy từ:

```kotlin
textToSpeech.voices
```

và chuyển mỗi `Voice` thành JSON.

## speakText

`settingsJson` có dạng:

```json
{
  "voice": "vi-vn-x-gft-local",
  "voiceName": "Tiếng Việt - 2",
  "language": "vi-VN",
  "preferGoogleEngine": true,
  "enginePackage": "com.google.android.tts",
  "rate": 100,
  "pitch": 100,
  "volume": 100,
  "utteranceId": "lqlq-reader-0"
}
```

Quy đổi:

```text
rate 100  → TextToSpeech.setSpeechRate(1.0f)
pitch 100 → TextToSpeech.setPitch(1.0f)
```

Khi đọc xong, WebView gọi:

```javascript
window.LqlqReader.onNativeUtteranceDone()
```

Khi lỗi:

```javascript
window.LqlqReader.onNativeUtteranceError("Nội dung lỗi")
```

## Ưu tiên Google TTS

Khi khởi tạo có thể thử package:

```kotlin
TextToSpeech(context, listener, "com.google.android.tts")
```

Nếu package Google không tồn tại hoặc khởi tạo thất bại, dùng engine mặc định của
Android thay vì làm ứng dụng văng.

## Mở cài đặt giọng nói

`openTtsSettings()` nên mở:

```kotlin
Intent("com.android.settings.TTS_SETTINGS")
```

và có phương án dự phòng bằng `Settings.ACTION_SETTINGS`.

## Lưu ý

- Không hardcode một tên voice duy nhất.
- Luôn đọc `textToSpeech.voices` vì tên giọng khác nhau theo máy và phiên bản.
- Chọn voice `Locale("vi", "VN")` trước.
- Ưu tiên voice không yêu cầu mạng để nghe truyện offline.
