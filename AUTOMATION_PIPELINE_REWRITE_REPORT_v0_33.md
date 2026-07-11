# LQLQ Automation Pipeline Rewrite v0.33

Muc tieu pass nay: chinh lai xuong song automation theo mo hinh san xuat video that te:

`SCRIPT -> SCENE -> ASSET_PLAN -> VISUAL -> VOICE -> SUBTITLE -> VIDEO -> METADATA -> REVIEW -> PUBLISH`

Khong co gang lam het renderer/upload trong mot lan. Pass nay tap trung vao nut that: bo tu duy "moi scene deu tao anh AI", them lop chon asset de uu tien stock/template truoc khi dot credit AI.

## Da them

### 1. ASSET_PLAN stage

File moi:

- `app/src/main/java/com/lqlq/browser/automation/visual/VisualAssetPlanner.kt`

Chuc nang:

- Doc topic + scene prompts.
- Phan loai noi dung: quote/tips, story, education, general short.
- Chon strategy theo provider:
  - `STOCK_PHOTO_KEN_BURNS` cho Pexels/Pixabay/stock media.
  - `STOCK_VIDEO_LOOP` cho Coverr/video stock ve sau.
  - `AI_IMAGE_KEN_BURNS` cho OpenAI/Cloudflare.
  - `REUSABLE_BACKGROUND_TEMPLATE` khi chua co provider.
  - `PREMIUM_IMAGE_TO_VIDEO` de danh cho pass tra phi/scene quan trong.
- Tao `assetQuery`, `templateId`, `renderMode`, `durationMs`, `rationale` cho tung scene.

UI da hien Asset plan ben duoi Scene prompts.

### 2. Workflow version 4

Workflow da doi tu 8 step cu thanh 11 step:

1. TOPIC
2. CONTENT
3. SCENE_PROMPTS
4. ASSET_PLAN
5. IMAGES_VISUALS
6. VOICE
7. SUBTITLE
8. VIDEO
9. METADATA
10. REVIEW
11. PUBLISH

Cac step SUBTITLE/VIDEO/METADATA/PUBLISH van `NOT_CONFIGURED`; REVIEW dang doi video. Day la dung chu y de chia nho cong viec.

### 3. Pexels stock photo connector

File moi:

- `app/src/main/java/com/lqlq/browser/automation/connector/image/PexelsStockImageConnector.kt`

Chuc nang:

- Them provider `Pexels Stock Photos` vao image provider registry.
- Luu cau hinh Pexels API key rieng trong native credential store.
- Search anh portrait/landscape/square theo aspect ratio.
- Tai anh stock ve thanh artifact `IMAGE` nhu cac image provider khac.
- Phu hop lam background shorts + Ken Burns 2.5D o pass renderer sau.

### 4. UI Automation Center

File sua:

- `app/src/main/assets/www/v33-automation-center.js`

Thay doi:

- Doi step list theo pipeline moi.
- Them metadata/hint cho Pexels.
- Hien Asset plan: strategy, query, template, renderMode, rationale.
- Cap nhat copy runtime theo pipeline moi.

## Donor project da tham khao

`gemini-youtube-automation` dung pipeline: curriculum/script -> slide visuals -> TTS -> moviepy render -> YouTube upload.

Nhung khong copy code Python/MoviePy vao Android. Chi lay y tuong kien truc:

- tach tung stage,
- dung stock media/slide/template thay vi AI image moi canh,
- render sau cung tu visual + voice,
- upload tach thanh stage rieng.

## Viec tiep theo nen lam

### Pass 2: VIDEO_RENDER local/export spec

Tao renderer service rieng thay vi ep Android lam nang:

Input:

- generated text
- scene prompts
- asset plans
- image/stock artifacts
- voice artifact

Output:

- mp4 9:16
- subtitle burned-in
- title card
- watermark rieng

Huong goi y:

- PC/local Python FFmpeg hoac server render.
- Android chi tao job + preview + gui render request.

### Pass 3: Metadata + Review

- Tao title/description/hashtags tu script.
- Them checkbox review: title OK, video OK, source/ban quyen OK, AI disclosure OK.

### Pass 4: Publish

- YouTube Data API truoc.
- TikTok/Instagram sau.
- Khong nen auto click UI neu co API chinh thuc.
