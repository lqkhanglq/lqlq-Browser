# Release Signing Setup

This repo no longer reads a bundled keystore or hardcoded passwords from `app/build.gradle.kts`.

## Local release build

Provide release signing values through Gradle properties or environment variables:

```powershell
./gradlew assembleRelease `
  -PLQLQ_RELEASE_STORE_FILE=keystore/release-upload.jks `
  -PLQLQ_RELEASE_STORE_PASSWORD=... `
  -PLQLQ_RELEASE_KEY_ALIAS=... `
  -PLQLQ_RELEASE_KEY_PASSWORD=... `
  -PLQLQ_DYNAMIC_LOOT_ENDPOINT="https://example.workers.dev"
```

If those values are missing, `assembleRelease` will build an unsigned release locally, but GitHub Actions is now configured to skip uploading a misleading unsigned `release.apk`.

## GitHub Actions secrets

Set these repository secrets before expecting a signed release artifact:

- `LQLQ_RELEASE_KEYSTORE_BASE64`
- `LQLQ_RELEASE_STORE_PASSWORD`
- `LQLQ_RELEASE_KEY_ALIAS`
- `LQLQ_RELEASE_KEY_PASSWORD`

Optional repository variable:

- `LQLQ_DYNAMIC_LOOT_ENDPOINT`

To create the Base64 secret locally:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\\release-upload.jks"))
```

## Manual cleanup still required

These changes stop future hardcoded signing leaks, but they do not remove an already tracked keystore from git history.

You still need to:

1. Rotate to a new release key if the old key was exposed.
2. Remove the tracked keystore from the repository and its history.
3. Reconfigure CI and local machines to use the new secret-backed signing flow.
