# Open Source License Matrix

| Repository | Commit | License | Reuse posture | Notes |
|---|---|---:|---|---|
| `mutonby/openshorts` | `fe87af6dd599b854e6eab2de0ca247ebafe13885` | MIT | External service integration and adapter contract allowed | Do not vendor entire repo |
| `activepieces/activepieces` | `6a404041da9bee45fa9800c193603f2e444b543a` | MIT for CE paths, commercial for `packages/ee/` and `packages/server/api/src/app/ee` | Reuse CE integration patterns only | Do not copy enterprise code |
| `Comfy-Org/ComfyUI` | `ffbecfffb953914f5b4bd8f61d810ff2300631de` | GPL-3.0 | Use as external service only | Do not copy source into APK |
| `microsoft/playwright` | `317210d54e189295861157a2758a0367008a2de9` | Apache-2.0 | Backend-only worker strategy if needed | No browser cookies to Android |
| `openid/AppAuth-Android` | `e5f51842fd1d3c7e49d9b6642e346f43f495dac8` | Apache-2.0 | Future native OAuth option | WebView not supported upstream |
| `googleapis/google-api-java-client-services` | `099d9cc4a7cb74a2800e6222b51e10bcc3d19a7b` | Apache-2.0 | Official YouTube API fallback path | Windows path-length issue during intake |
| `RayVentura/ShortGPT` | `3df4e0f7a422bf7386565d498bf4521a2544c614` | MIT | Comparative intake only | Not chosen as primary engine |
