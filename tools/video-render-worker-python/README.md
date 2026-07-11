# LQLQ Video Render Worker

Worker Python ben ngoai Android de render MP4 that tu:

- `renderPlanJson`
- file `voice`
- danh sach file `images`

## Cai dat

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Chay worker

```bash
uvicorn app:app --host 0.0.0.0 --port 8787
```

## API

- `GET /health`
- `POST /render`
- `GET /videos/{filename}`

## Cau hinh trong Android

Trong Automation Center:

1. Chon `External MoviePy Worker`
2. Nhap URL worker, vi du: `http://192.168.1.10:8787`
3. Bam `Test Worker`
4. Khi da co `IMAGE + VOICE + VIDEO_RENDER_PLAN`, bam retry video de worker xuat MP4.

## Luu y

- Worker can Python va FFmpeg hoat dong de MoviePy co the ghi MP4.
- Android app khong embed FFmpeg/MoviePy.
- Neu worker offline, app se fallback ve `VIDEO_RENDER_PLAN_READY`.
