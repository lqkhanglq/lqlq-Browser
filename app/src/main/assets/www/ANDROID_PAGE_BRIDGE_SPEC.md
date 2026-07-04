# ANDROID_PAGE_BRIDGE_SPEC — lấy chương từ active WebView

## Vấn đề cần giải quyết

HTML giao diện của lqlq Browser và website đang mở là hai ngữ cảnh khác nhau.
JavaScript trong giao diện không thể gọi `document` của một WebView khác nếu APK
không cung cấp cầu nối.

Nút Reader và Chapter Clipper hiện cùng dùng:

```javascript
window.LqlqChapterExtractor.extractCurrentChapter()
```

## Cách nên dùng trong APK

Phương án tốt nhất là APK lấy nội dung từ **active WebView** bằng
`evaluateJavascript`, rồi trả JSON đã trích xuất cho giao diện.

Bridge JavaScript nên đăng ký tên:

```kotlin
webView.addJavascriptInterface(pageBridge, "LqlqPageBridge")
```

## Giao diện tối thiểu

Module HTML nhận một trong hai kiểu sau.

### Kiểu A — trả chương đã trích xuất

```text
extractCurrentChapter(): String
```

JSON trả về:

```json
{
  "storyTitle": "Nữ Đế Tọa Hạ Đệ Nhất Chó Săn",
  "chapterTitle": "Chương 10: mâu thuẫn lời chứng",
  "chapterNumber": 10,
  "body": "Trong phòng giam...\n\nChờ những người còn lại rút đi...",
  "url": "https://example.com/truyen/chuong-10",
  "charCount": 8321
}
```

Đây là phương án ổn định nhất.

### Kiểu B — trả HTML và URL của active WebView

```text
getCurrentPageHtml(): String
getCurrentPageUrl(): String
```

HTML sẽ được đưa qua cùng thuật toán của Chapter Clipper ở phía giao diện.

## Lưu ý quan trọng với Android WebView

`addJavascriptInterface` không thể trả một đối tượng DOM JavaScript thật.
Vì vậy không triển khai `getDocument()` bằng Kotlin.

Thay vào đó:

```kotlin
activeWebView.evaluateJavascript(
    "(function(){ return document.documentElement.outerHTML; })()"
) { encodedHtml ->
    // Giải mã chuỗi JSON rồi gửi sang giao diện.
}
```

Tốt hơn nữa là inject trực tiếp thuật toán trích xuất vào active WebView và chỉ
trả JSON chương, tránh chuyển toàn bộ HTML lớn qua bridge.

## Tên hàm dự phòng được hỗ trợ

lqlq Browser cũng nhận:

```text
getCurrentChapter()
extractReadableText()
getReadablePageText()
getCurrentPageText()
getPageHtml()
getHtml()
getUrl()
getCurrentUrl()
```

## An toàn

Chỉ expose bridge cho WebView giao diện nội bộ, không expose bridge native có
quyền nhạy cảm trực tiếp cho website bên ngoài.
