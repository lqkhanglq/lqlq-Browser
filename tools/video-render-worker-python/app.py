from __future__ import annotations

import json
import math
import re
import uuid
from pathlib import Path
from typing import List

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from moviepy.editor import AudioFileClip, CompositeVideoClip, ImageClip, concatenate_videoclips
from PIL import Image, ImageDraw, ImageFont


APP_VERSION = "lqlq-render-worker-v1"
ROOT_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT_DIR / "output"
TEMP_DIR = ROOT_DIR / "temp"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
TEMP_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="LQLQ Video Render Worker")


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "renderer": "moviepy",
        "version": APP_VERSION,
    }


@app.get("/videos/{filename}")
def get_video(filename: str):
    safe_name = Path(filename).name
    target = OUTPUT_DIR / safe_name
    if not target.exists():
        raise HTTPException(status_code=404, detail="Video not found")
    return FileResponse(target, media_type="video/mp4", filename=safe_name)


@app.post("/render")
async def render(
    renderPlanJson: str = Form(...),
    metadataJson: str | None = Form(None),
    subtitleJson: str | None = Form(None),
    voice: UploadFile = File(...),
    images: List[UploadFile] = File(...),
) -> dict:
    try:
        plan = json.loads(renderPlanJson)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"Invalid renderPlanJson: {exc}") from exc

    width = int(plan.get("width") or 1080)
    height = int(plan.get("height") or 1920)
    fps = int(plan.get("fps") or 30)
    scenes = list(plan.get("scenes") or [])
    if not scenes:
        raise HTTPException(status_code=400, detail="Render plan has no scenes")
    if not images:
        raise HTTPException(status_code=400, detail="No image files uploaded")

    metadata = {}
    if metadataJson:
        try:
            metadata = json.loads(metadataJson)
        except json.JSONDecodeError:
            metadata = {}

    render_id = metadata.get("jobId") or uuid.uuid4().hex[:12]
    render_dir = TEMP_DIR / render_id
    render_dir.mkdir(parents=True, exist_ok=True)

    voice_path = render_dir / f"voice{guess_extension(voice.content_type, 'wav')}"
    voice_path.write_bytes(await voice.read())

    ordered_images = sorted(images, key=lambda item: extract_order(item.filename or ""))
    image_paths = []
    for index, uploaded in enumerate(ordered_images, start=1):
        suffix = guess_extension(uploaded.content_type, "png")
        raw_path = render_dir / f"scene_{index:03d}{suffix}"
        raw_path.write_bytes(await uploaded.read())
        processed_path = render_dir / f"scene_{index:03d}_processed.png"
        prepare_cover_image(raw_path, processed_path, width, height)
        image_paths.append(processed_path)

    audio_clip = AudioFileClip(str(voice_path))
    voice_duration = max(audio_clip.duration, 0.1)
    scene_durations = build_scene_durations(scenes, voice_duration)

    clips = []
    for index, scene in enumerate(scenes):
        image_path = image_paths[min(index, len(image_paths) - 1)]
        duration = scene_durations[index]
        base_clip = ImageClip(str(image_path)).set_duration(duration)
        base_clip = base_clip.resize(lambda t: 1.0 + 0.06 * (t / max(duration, 0.1)))
        base_clip = base_clip.set_position("center")
        subtitle_text = (scene.get("subtitleText") or "").strip()
        if subtitle_text:
            overlay_path = render_dir / f"subtitle_{index:03d}.png"
            create_subtitle_overlay(subtitle_text, overlay_path, width, height)
            overlay_clip = ImageClip(str(overlay_path)).set_duration(duration).set_position(("center", "bottom"))
            scene_clip = CompositeVideoClip([base_clip, overlay_clip], size=(width, height)).set_duration(duration)
        else:
            scene_clip = CompositeVideoClip([base_clip], size=(width, height)).set_duration(duration)
        clips.append(scene_clip)

    final_video = concatenate_videoclips(clips, method="compose").set_fps(fps)
    final_video = final_video.set_audio(audio_clip)
    final_video = final_video.set_duration(audio_clip.duration)

    output_file = OUTPUT_DIR / f"{render_id}.mp4"
    final_video.write_videofile(
        str(output_file),
        fps=fps,
        codec="libx264",
        audio_codec="aac",
        preset="veryfast",
        threads=2,
        logger=None,
    )

    return {
        "status": "completed",
        "videoPath": str(output_file),
        "downloadUrl": f"/videos/{output_file.name}",
        "durationMs": int(audio_clip.duration * 1000),
        "width": width,
        "height": height,
        "fps": fps,
        "sceneCount": len(scenes),
    }


def build_scene_durations(scenes: list, voice_duration: float) -> List[float]:
    durations = []
    for scene in scenes:
        duration_ms = int(scene.get("durationMs") or 0)
        durations.append(max(duration_ms / 1000.0, 0.0))
    if not any(durations):
        return [voice_duration / max(len(scenes), 1)] * len(scenes)
    total = sum(durations)
    if total <= 0:
        return [voice_duration / max(len(scenes), 1)] * len(scenes)
    ratio = voice_duration / total
    scaled = [max(duration * ratio, 0.2) for duration in durations]
    drift = voice_duration - sum(scaled)
    scaled[-1] = max(scaled[-1] + drift, 0.2)
    return scaled


def prepare_cover_image(source: Path, target: Path, width: int, height: int) -> None:
    with Image.open(source) as image:
        image = image.convert("RGB")
        src_ratio = image.width / image.height
        dst_ratio = width / height
        if src_ratio > dst_ratio:
            new_height = image.height
            new_width = int(new_height * dst_ratio)
            left = (image.width - new_width) // 2
            image = image.crop((left, 0, left + new_width, image.height))
        else:
            new_width = image.width
            new_height = int(new_width / dst_ratio)
            top = (image.height - new_height) // 2
            image = image.crop((0, top, image.width, top + new_height))
        image = image.resize((width, height), Image.Resampling.LANCZOS)
        image.save(target, format="PNG")


def create_subtitle_overlay(text: str, target: Path, width: int, height: int) -> None:
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default()
    wrapped = wrap_text(draw, text, font, width - 120)
    text_box_height = 80 + (len(wrapped) * 28)
    draw.rounded_rectangle(
        (40, height - text_box_height - 80, width - 40, height - 40),
        radius=32,
        fill=(0, 0, 0, 120),
    )
    y = height - text_box_height - 40
    for line in wrapped:
        bbox = draw.textbbox((0, 0), line, font=font)
        line_width = bbox[2] - bbox[0]
        x = (width - line_width) / 2
        draw.text((x, y), line, fill=(255, 255, 255, 255), font=font)
        y += 28
    canvas.save(target, format="PNG")


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> List[str]:
    words = text.split()
    if not words:
        return [""]
    lines = []
    current = words[0]
    for word in words[1:]:
        candidate = f"{current} {word}"
        bbox = draw.textbbox((0, 0), candidate, font=font)
        if bbox[2] - bbox[0] <= max_width:
            current = candidate
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def guess_extension(content_type: str | None, fallback: str) -> str:
    mapping = {
        "image/png": ".png",
        "image/jpeg": ".jpg",
        "image/webp": ".webp",
        "audio/wav": ".wav",
        "audio/mpeg": ".mp3",
    }
    return mapping.get((content_type or "").lower(), f".{fallback}")


def extract_order(filename: str) -> int:
    match = re.search(r"(\d+)", filename or "")
    return int(match.group(1)) if match else math.inf
