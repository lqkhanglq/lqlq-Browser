# lqlq Browser v0.23 — handoff cho Claude Fable 5

## Mục tiêu tiếp theo

Chuyển HTML prototype hiện tại thành Android project có thể build APK.

## App identity bắt buộc

```text
Tên ứng dụng: lqlq Browser
Launcher icon: icon do người dùng cung cấp trong gói v0.23
```

## Icon

Nguồn chính:

```text
assets/icons/lqlq-browser-icon-original-1254.png
```

Nguồn đã xóa nền trắng ở bốn góc:

```text
assets/icons/lqlq-browser-icon-transparent-1254.png
```

Bộ tài nguyên Android dựng sẵn:

```text
android-icon-pack/res/
```

Không được thay icon bằng icon Shield cũ hoặc icon mặc định của Android Studio.

## HTML/PWA

`index.html` đã được nối:

- favicon
- Apple touch icon
- `manifest.webmanifest`
- theme color

## Android project

Khi dựng project:

1. Sao chép `android-icon-pack/res/` vào `app/src/main/res/`.
2. Đặt `android:icon="@mipmap/ic_launcher"`.
3. Đặt `android:roundIcon="@mipmap/ic_launcher_round"`.
4. Đặt label ứng dụng là `lqlq Browser`.
5. Giữ nguyên ảnh gốc trong repo để có thể tái tạo launcher asset về sau.
