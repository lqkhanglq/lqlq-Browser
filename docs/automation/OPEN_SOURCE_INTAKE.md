# Open Source Intake

Pass: `OPEN_SOURCE_AUTOMATION_OS1`

Baseline branch: `feature/lqlq-automation`
Baseline head: `eee6aebe8e7a`

## Primary engine decision

PRIMARY_ENGINE:
- OpenShorts

WHY_SELECTED:
- FastAPI entry server already exists in `app.py`
- upstream Docker support is present (`docker-compose.yml`, `Dockerfile`, `render-service/Dockerfile`)
- explicit AI Shorts pipeline already includes script, actor/image, voice, subtitles, FFmpeg composite, and social publish hooks
- status endpoints already exist for SaaS Shorts jobs
- better fit for an external gateway + orchestrator model than a Gradio-first local framework

REJECTED_ALTERNATIVES:
- ShortGPT
  - useful framework ideas and MIT license
  - but current runtime is centered on Gradio/local Docker and moviepy rather than a clean service boundary
  - weaker fit for gateway orchestration and production webhook integration

## Donor audit

### OpenShorts
- Repository: `https://github.com/mutonby/openshorts`
- Commit: `fe87af6dd599b854e6eab2de0ca247ebafe13885`
- License: MIT
- Runtime: Python / FastAPI + Docker + frontend + renderer
- Entry point: `app.py`
- API/server available: yes
- Docker support: yes
- Reusable components:
  - service boundary and endpoint map
  - AI shorts pipeline structure
  - subtitle + FFmpeg render contract
  - social publish handoff contract
- Do not reuse directly:
  - whole service source
  - dashboard UI
  - Upload-Post-specific credentials
  - donor branding/assets
- Accounts or APIs required:
  - content/image/voice providers
  - social publishing credentials

### Activepieces Community Edition
- Repository: `https://github.com/activepieces/activepieces`
- Commit: `6a404041da9bee45fa9800c193603f2e444b543a`
- License:
  - Community Edition outside `packages/ee/` and `packages/server/api/src/app/ee`: MIT
  - enterprise directories: commercial, not reusable here
- Runtime: TypeScript / Docker / Postgres / Redis
- API/server available: yes
- Docker support: yes
- Reusable components:
  - orchestration topology
  - webhook-driven flow entry
  - app connection model
  - YouTube community OAuth piece path
- Do not reuse directly:
  - anything in `packages/ee/`
  - server enterprise code
  - hosted cloud branding
- Accounts or APIs required:
  - provider app connections
  - Google OAuth client for YouTube

### ComfyUI
- Repository: `https://github.com/Comfy-Org/ComfyUI`
- Commit: `ffbecfffb953914f5b4bd8f61d810ff2300631de`
- License: GPL-3.0
- Runtime: Python
- API/server available: yes, workflow and API endpoints are documented by project structure/docs
- Docker support: not the preferred upstream local path in the cloned tree
- Reusable components:
  - external workflow API target
  - workflow JSON format
- Do not reuse directly:
  - source into Android app
  - models or weights
- Accounts or APIs required:
  - model/runtime availability on the machine hosting ComfyUI

### Playwright
- Repository: `https://github.com/microsoft/playwright`
- Commit: `317210d54e189295861157a2758a0367008a2de9`
- License: Apache-2.0
- Runtime: Node.js
- API/server available: library/CLI/MCP, not used inside APK
- Docker support: yes
- Reusable components:
  - backend-only web automation strategy when a provider has no official API
- Do not reuse directly:
  - browser session storage in Android
  - YouTube upload automation as primary path

### AppAuth-Android
- Repository: `https://github.com/openid/AppAuth-Android`
- Commit: `e5f51842fd1d3c7e49d9b6642e346f43f495dac8`
- License: Apache-2.0
- Runtime: Android library
- Reusable components:
  - future native OAuth path if Android ever needs first-party auth brokering
- Not reused in OS1 code:
  - no direct OAuth inside shell yet
- Important note:
  - upstream explicitly does not support WebView for OAuth, which matches this project's security direction

### google-api-java-client-services
- Repository: `https://github.com/googleapis/google-api-java-client-services`
- Commit: `099d9cc4a7cb74a2800e6222b51e10bcc3d19a7b`
- License: Apache-2.0
- Runtime: Java client services
- Reusable components:
  - official YouTube Data API path reference for fallback if Activepieces YouTube piece is insufficient
- Notes:
  - local Windows checkout hit path-length issues; commit and license were still verified from git metadata

### ShortGPT
- Repository: `https://github.com/RayVentura/ShortGPT`
- Commit: `3df4e0f7a422bf7386565d498bf4521a2544c614`
- License: MIT
- Runtime: Python / Gradio / moviepy / ffmpeg
- API/server available: not as cleanly service-first as OpenShorts
- Reusable components:
  - comparative pipeline ideas only
- Primary reason not selected:
  - weaker service boundary for OS1 integration
