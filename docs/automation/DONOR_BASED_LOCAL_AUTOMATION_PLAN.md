# Donor-Based Local Automation Plan

Date: 2026-07-08
Branch baseline: `feature/lqlq-automation`
Status: `AUDIT_ONLY_NO_RUNTIME_CHANGES`

## 1. Decision Lock

This plan replaces the earlier OS1 direction that introduced:

- Python gateway
- Docker runtime
- backend-first orchestration
- self-hosted automation stack inside this repository

This turn does **not** clean up OS1 code. It only records the donor-backed direction that will be implemented next.

## 2. Product Direction

`lqlq Browser` remains the user-facing orchestrator.

It must:

- collect topic and provider choices
- start a real automation run
- track real progress
- preview artifacts
- require approval before publishing
- connect to user-owned external accounts and services

It must **not**:

- run a Python server inside the product architecture
- require Docker/VPS as the primary runtime
- embed a new local AI engine
- ship fake progress, fake media, fake upload success

## 3. Local Target Architecture

Primary runtime direction for the next implementation pass:

```text
Automation Center UI
  -> AutomationBridge
  -> AutomationFacade
  -> LocalAutomationRuntime
  -> Provider Connectors
       - LLM/API providers
       - image/media providers
       - TTS providers
       - YouTube OAuth/API
       - optional Upload-Post account
       - optional user-guided WebView automation for web-only providers
```

Rules:

- orchestration lives inside Android/Kotlin
- external generation and publishing happen through user-configured real services
- no shell command execution from Automation Center
- no arbitrary filesystem path input from JavaScript
- no raw cookie/token exposure to WebView JavaScript

## 4. Donor Inventory

### 4.1 MoneyPrinterTurbo

- Repository: `MoneyPrinterTurbo`
- Commit: `18d577f980c1ea571b60e09cf71b33b6a838f8ea`
- License: `MIT`
- Role: primary donor for local workflow structure, provider abstraction, progress model, script/metadata contracts

Important donor files:

- `app/services/task.py`
- `app/services/state.py`
- `app/services/llm.py`
- `app/services/material.py`
- `app/services/voice.py`
- `app/services/video.py`
- `app/services/upload_post.py`
- `app/models/schema.py`
- `app/controllers/v1/llm.py`
- `app/controllers/v1/video.py`

Observed reusable concepts:

- staged workflow orchestration in `task.py`
- task progress and checkpoint state in `state.py`
- request/response model boundaries in `schema.py`
- provider-driven script generation in `llm.py`
- social metadata generation in `llm.py`
- voice provider dispatch and subtitle timing concepts in `voice.py`
- stock-material search and dedupe rules in `material.py`
- upload-post publishing contract in `upload_post.py`

### 4.2 gemini-youtube-automation

- Repository: `gemini-youtube-automation`
- Commit: `be43d43308776d40227c7956f270c8dc3d21de5d`
- License: `MIT`
- Role: primary donor for official YouTube OAuth/upload flow and small end-to-end scripted content pipeline

Important donor files:

- `src/generator.py`
- `src/uploader.py`
- `main.py`

Observed reusable concepts:

- official Google OAuth + YouTube Data API path in `src/uploader.py`
- content-plan to artifact pipeline decomposition in `main.py`
- simple provider-facing content generation contract in `src/generator.py`

### 4.3 YASGU

- Repository: `YASGU`
- Commit: `e81ea7a53ab931204cf1a837c8895c0df3908c59`
- License: local repo contains `LICENSE`; treat as donor with attribution required
- Role: reference donor for video-job decomposition and upload lifecycle

Important donor files:

- `src/classes/generator.py`
- `src/utils/llm.py`
- `src/utils/image_generator.py`
- `src/utils/tts.py`
- `src/utils/video_generator.py`
- `src/utils/web_browser.py`

Observed reusable concepts:

- single-job `generate -> assemble -> upload` flow
- separation between script/image/tts/video/upload steps
- browser automation as last-resort adapter

### 4.4 n8n-nodes-upload-post

- Repository: `n8n-nodes-upload-post`
- Commit: `e238b9b64d771029125b2c89474e021a04dce1f9`
- License: `MIT`
- Role: primary donor for Upload-Post request contract and multi-platform publish field mapping

Important donor files:

- `nodes/UploadPost/UploadPost.node.ts`
- `credentials/UploadPostApi.credentials.ts`

Observed reusable concepts:

- API key auth header format
- request polling with `request_id`
- per-platform fields for YouTube, TikTok, Facebook, LinkedIn, Pinterest, X
- manual account/profile selection behavior

### 4.5 awesome-n8n-templates

- Repository: `awesome-n8n-templates`
- Commit: `254ba47f7678292c4c09a64e8c7587ffd3697824`
- License: `CC-BY-4.0`
- Role: reference-only workflow graph inspiration

Important donor files inspected:

- `OpenAI_and_LLMs/Generate Text-to-Speech Using Elevenlabs via API.json`
- `AI_Research_RAG_and_Data_Analysis/Hacker News to Video Content.json`

Observed reusable concepts:

- webhook-to-provider flow shape
- approval/poll/wait node patterns
- cross-service orchestration examples

Not suitable for direct code port into APK because template provenance is mixed and the repo is a collection rather than a single authored runtime.

## 5. Reuse Classification

### 5.1 DIRECT_PORT

These are logic shapes or data contracts that can be ported almost directly into Kotlin with the same control flow.

- `MoneyPrinterTurbo/app/services/task.py`
  - `generate_script`
  - `generate_terms`
  - `generate_audio`
  - `generate_subtitle`
  - `get_video_materials`
  - `generate_final_videos`
  - `start`
- `MoneyPrinterTurbo/app/services/state.py`
  - task state object shape
  - progress update semantics
  - bounded progress normalization
- `MoneyPrinterTurbo/app/models/schema.py`
  - request model boundaries
  - video/social metadata parameter grouping
- `MoneyPrinterTurbo/app/services/llm.py`
  - `generate_script`
  - `generate_terms`
  - `generate_social_metadata`
- `gemini-youtube-automation/src/uploader.py`
  - YouTube upload request model
  - metadata mapping
  - upload result extraction

### 5.2 ADAPT_TO_ANDROID_API

These donors are good sources, but their runtime must be replaced with Android or mobile-safe integrations.

- `MoneyPrinterTurbo/app/services/material.py`
  - search-provider selection
  - material dedupe logic
  - script-order material matching
  - replace `requests` and filesystem behavior with `OkHttp`, scoped storage, app-owned files
- `MoneyPrinterTurbo/app/services/voice.py`
  - provider selection contract
  - subtitle timing concepts
  - no direct port of Python audio stack
  - replace with provider HTTP APIs and Android media handling
- `n8n-nodes-upload-post/nodes/UploadPost/UploadPost.node.ts`
  - translate field mapping into Kotlin request builders
  - translate polling behavior into repository/runtime job updates
- `n8n-nodes-upload-post/credentials/UploadPostApi.credentials.ts`
  - translate auth shape into encrypted app-held credential config
- `gemini-youtube-automation/src/generator.py`
  - content contract reuse only
  - replace Python media render path

### 5.3 ADAPT_TO_AUTOMATION_WEBVIEW

These donors imply browser-driven behavior, but inside this product they must become user-guided WebView/browser-session automation rather than Selenium or headless workers.

- `YASGU/src/utils/web_browser.py`
  - upload flow structure
  - page-step sequencing
  - success URL extraction concept
- any donor flow that depends on manual authenticated website state without official API

Constraints for this class:

- user-owned logged-in session only
- no hidden credential injection from WebView JavaScript
- no CAPTCHA bypass
- explicit `USER_ACTION_REQUIRED` state when blocked

### 5.4 REFERENCE_ONLY_LICENSE

- `awesome-n8n-templates/...`
  - flow ordering ideas only
  - no direct node-definition port into APK runtime
- `YASGU` high-level workflow decomposition when code details are tied to Python/Selenium-only paths

### 5.5 NOT_USABLE_IN_APK

These are not acceptable as runtime components inside the APK and must not be ported as-is.

- Python web servers
- Docker compose stacks
- Selenium/Firefox browser control
- MoviePy render pipeline
- ImageMagick-dependent composition
- FFmpeg shell-pipeline orchestration copied from Python server runtimes
- local background daemons assumed by donor repos

Concrete donor examples:

- `YASGU/src/utils/video_generator.py`
- `YASGU/src/utils/web_browser.py` runtime implementation
- `MoneyPrinterTurbo/app/services/video.py`
- most of `gemini-youtube-automation/src/generator.py` media-render path

## 6. Selected Donor Sources Per Capability

### Content/script generation

Primary:

- `MoneyPrinterTurbo/app/services/llm.py`
  - `generate_script`
  - `generate_terms`
  - `generate_social_metadata`

Secondary:

- `gemini-youtube-automation/src/generator.py`

Why:

- strongest provider abstraction
- already separates subject/script/terms/social metadata
- easiest to port into a local Kotlin orchestration layer

### Image/material sourcing

Primary:

- `MoneyPrinterTurbo/app/services/material.py`

Why:

- already handles provider selection, dedupe, duration fit, ordered matching

### Voice/subtitle pipeline

Primary:

- `MoneyPrinterTurbo/app/services/voice.py`

Reference:

- `awesome-n8n-templates/OpenAI_and_LLMs/Generate Text-to-Speech Using Elevenlabs via API.json`

Why:

- best donor for provider dispatch and subtitle timing semantics
- n8n template confirms a minimal provider contract for webhook/API style voice generation

### Local workflow orchestration

Primary:

- `MoneyPrinterTurbo/app/services/task.py`
- `MoneyPrinterTurbo/app/services/state.py`

Reference:

- `YASGU/src/classes/generator.py`

Why:

- `task.py` is the clearest end-to-end staged workflow donor
- `state.py` provides a clean progress update shape
- `YASGU` reinforces the same stage order at a smaller scale

### YouTube publishing

Primary:

- `gemini-youtube-automation/src/uploader.py`

Why:

- official OAuth/API path
- no dependence on clicking YouTube Studio

### Multi-platform publishing

Primary:

- `n8n-nodes-upload-post/nodes/UploadPost/UploadPost.node.ts`
- `n8n-nodes-upload-post/credentials/UploadPostApi.credentials.ts`
- `MoneyPrinterTurbo/app/services/upload_post.py`

Why:

- best concrete contract for a user-supplied Upload-Post account
- good fallback when a user wants one external publish service instead of many direct platform integrations

## 7. First Kotlin Port Targets

Next implementation should start with new local components, not with OS1 backend code.

Proposed local modules:

- `automation/runtime/LocalAutomationRuntime.kt`
- `automation/runtime/LocalAutomationJobRunner.kt`
- `automation/runtime/LocalAutomationProgress.kt`
- `automation/provider/llm/`
- `automation/provider/material/`
- `automation/provider/voice/`
- `automation/provider/youtube/`
- `automation/provider/publish/`
- `automation/provider/webview/`

First donor-backed methods to port conceptually:

1. `task.py:start` -> `LocalAutomationJobRunner.start`
2. `state.py:update_task` -> local Room-backed progress updater
3. `llm.py:generate_script` -> provider-neutral content generation use case
4. `llm.py:generate_terms` -> visual/material query generation use case
5. `llm.py:generate_social_metadata` -> publish metadata use case
6. `material.py:download_videos` -> material fetch/orchestration layer
7. `src/uploader.py:upload_to_youtube` -> Android YouTube publish adapter

## 8. OS1 Audit: Keep, Remove Later, Rewrite

This is a planning classification only. No file is changed in this pass.

### 8.1 KEEP

These remain useful and should survive the cleanup with little or no structural change.

- `docs/automation/OPEN_SOURCE_INTAKE.md`
- `docs/automation/OPEN_SOURCE_LICENSE_MATRIX.md`
- Automation Center UI existence and menu entry concept already added in:
  - `app/src/main/assets/www/index.html`
  - `app/src/main/assets/www/styles.css`
  - `app/src/main/assets/www/v33-automation-center.js`
- bridge/facade integration points as ownership locations, but not current OS1 behavior:
  - `app/src/main/java/com/lqlq/browser/AutomationBridge.kt`
  - `app/src/main/java/com/lqlq/browser/automation/AutomationFacade.kt`

### 8.2 REMOVE_LATER

These are artifacts of the backend-server direction and should be removed in the cleanup pass before donor-port implementation continues.

- `automation-stack/README.md`
- `automation-stack/docker-compose.yml`
- `automation-stack/.env.example`
- `automation-stack/THIRD_PARTY_NOTICES.md`
- `automation-stack/gateway/**`
- `automation-stack/flows/**`
- `automation-stack/comfyui-workflows/**`
- `automation-stack/adapters/**`
- `automation-stack/scripts/**`
- `app/src/main/java/com/lqlq/browser/automation/connector/remote/AutomationGatewayClient.kt`
- `app/src/main/java/com/lqlq/browser/automation/connector/remote/AutomationGatewayConfig.kt`
- `app/src/main/java/com/lqlq/browser/automation/connector/remote/AutomationGatewayConnector.kt`
- `app/src/main/java/com/lqlq/browser/automation/connector/remote/AutomationGatewayError.kt`
- `app/src/main/java/com/lqlq/browser/automation/connector/remote/AutomationGatewayModels.kt`
- `app/src/debug/AndroidManifest.xml`

### 8.3 REWRITE

These should stay as product surfaces or ownership points, but their implementation must pivot from remote gateway control to local donor-based orchestration.

- `app/src/main/assets/www/index.html`
- `app/src/main/assets/www/styles.css`
- `app/src/main/assets/www/v33-automation-center.js`
- `app/src/main/java/com/lqlq/browser/AutomationBridge.kt`
- `app/src/main/java/com/lqlq/browser/automation/AutomationFacade.kt`
- `app/src/main/java/com/lqlq/browser/LqlqApp.kt`
- `app/src/main/java/com/lqlq/browser/MainActivity.kt`
- `app/src/test/java/com/lqlq/browser/automation/AutomationBridgeTest.kt`

## 9. Security and Validation Rules for the New Direction

The donor-based local implementation must preserve these rules:

- plain-text topic/content input only
- bounded content length at native boundary
- no arbitrary callback URL
- no arbitrary filesystem path
- no executable script from UI
- no token logging
- no raw OAuth credential exposure to JavaScript
- redacted error messages
- publish requires approval by default
- YouTube default privacy remains `private`

## 10. Immediate Next Pass

Recommended pass id:

`OS1_CLEANUP_AND_FIRST_DONOR_PORT`

Scope:

1. remove gateway/backend-only files listed in `REMOVE_LATER`
2. preserve UI shell and docs
3. replace remote connector dependency with local runtime skeleton
4. port `task.py` stage model and `state.py` progress model into Kotlin
5. port `llm.py` script/terms/social metadata contracts
6. keep all work non-fake and provider-backed

Not in that pass:

- full real media generation
- full YouTube publish completion
- final multi-platform publish

Those come after the local runtime skeleton and provider contracts are in place.

## 11. Expected Deliverables After Cleanup Pass

- local runtime skeleton exists
- remote gateway code removed
- Automation Center points to local orchestration
- donor mappings are traceable file-to-file
- progress states are real local states, not mock timers
- YouTube and Upload-Post connectors are represented as provider interfaces, even if credentials are still pending

## 12. Notes on Donor Intent

The chosen donor pattern is:

- reuse workflow structure and API contracts
- translate into Kotlin/Android where appropriate
- keep attribution
- do not transplant Python runtime assumptions

This keeps the product aligned with the clarified requirement:

`lqlq Browser` orchestrates real user-owned external services, but does not become a backend platform of its own.
