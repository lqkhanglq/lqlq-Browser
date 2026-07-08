# lqlq Browser Android

Android project for `lqlq Browser`.

Current app version in source:

- `versionName`: `0.32.12`
- `versionCode`: `79`
- `minSdk`: `24`
- `targetSdk`: `35`

## Build with GitHub Actions

The repository should not store a release keystore anymore.

Required GitHub secrets for a signed release build:

- `LQLQ_RELEASE_KEYSTORE_BASE64`
- `LQLQ_RELEASE_STORE_PASSWORD`
- `LQLQ_RELEASE_KEY_ALIAS`
- `LQLQ_RELEASE_KEY_PASSWORD`

Optional GitHub variable:

- `LQLQ_DYNAMIC_LOOT_ENDPOINT`

Workflow:

1. Push the project to GitHub.
2. Open `Actions` -> `Build lqlq Browser APK`.
3. Run the workflow.
4. Download the `lqlq-browser-apk` artifact.

Artifact behavior:

- Always includes `lqlq-browser-v...-debug.apk`
- Includes `lqlq-browser-v...-release-signed.apk` only when signing secrets are configured
- Includes `RELEASE_SIGNING_REQUIRED.txt` when signed release was skipped

## Local release build

Provide signing values through Gradle properties or environment variables:

```powershell
gradle assembleRelease `
  -PLQLQ_RELEASE_STORE_FILE=keystore/release-upload.jks `
  -PLQLQ_RELEASE_STORE_PASSWORD=... `
  -PLQLQ_RELEASE_KEY_ALIAS=... `
  -PLQLQ_RELEASE_KEY_PASSWORD=... `
  -PLQLQ_DYNAMIC_LOOT_ENDPOINT="https://example.workers.dev"
```

## Security changes already applied

- Release signing is no longer hardcoded in `app/build.gradle.kts`.
- GitHub Actions reads the release keystore from secrets.
- `android:allowBackup` is disabled.
- Mixed content is blocked by default in WebView.
- HTTP pages and HTTP downloads now show explicit warnings.
- `PageToolsBridge.saveTextFile()` is gated and rate-limited.

## Known follow-up work

- The old release key must still be treated as exposed if it was ever committed publicly.
- Git history cleanup is still needed if you want the old keystore removed from past commits too.
- Full incognito isolation is still a larger architectural task.
