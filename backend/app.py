import os
import io
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

import requests
import imageio_ffmpeg
from fastapi import FastAPI, File, Form, UploadFile, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from PIL import Image, ImageFilter, ImageOps

APP_NAME = "My Watermark Remover API"
REPLICATE_TOKEN = os.getenv("REPLICATE_API_TOKEN", "").strip()
MODEL_VERSION = os.getenv("REPLICATE_INPAINT_VERSION", "allenhooo/lama:cdac78a1bec5b23c07fd29692fb70baa513ea403a39e643c48ec5edadb15fe72")
MAX_IMAGE_BYTES = 20 * 1024 * 1024
MAX_VIDEO_BYTES = 80 * 1024 * 1024
MAX_VIDEO_SECONDS = 10
VIDEO_FPS = 12
API_TIMEOUT = 180
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

app = FastAPI(title=APP_NAME, version="1.0.0")
origins = [
    "https://shanpalia.github.io",
    "https://shanpalia.github.io/My-Watermark-Remover",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
]
extra = os.getenv("CORS_ORIGINS", "")
if extra:
    origins.extend([x.strip().rstrip("/") for x in extra.split(",") if x.strip()])
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(set(origins)),
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type"],
)


def require_token():
    if not REPLICATE_TOKEN:
        raise HTTPException(status_code=503, detail="Backend is not configured: add REPLICATE_API_TOKEN to the Render environment variables.")


def upload_to_replicate(data: bytes, filename: str, content_type: str) -> str:
    require_token()
    try:
        response = requests.post(
            "https://api.replicate.com/v1/files",
            headers={"Authorization": f"Token {REPLICATE_TOKEN}"},
            files={"content": (filename, data, content_type)},
            timeout=60,
        )
        response.raise_for_status()
        payload = response.json()
        urls = payload.get("urls") or {}
        url = urls.get("get") or urls.get("download")
        if not url:
            raise RuntimeError("Replicate file upload did not return a usable URL.")
        # Replicate file resource URLs can be passed to model inputs; download URL is preferred if present.
        return urls.get("download") or url
    except requests.RequestException as exc:
        detail = "Replicate file upload failed."
        if getattr(exc, "response", None) is not None:
            detail += f" HTTP {exc.response.status_code}: {exc.response.text[:300]}"
        raise HTTPException(status_code=502, detail=detail) from exc


def run_lama(image_bytes: bytes, mask_bytes: bytes, suffix: str = "png") -> bytes:
    require_token()
    image_url = upload_to_replicate(image_bytes, f"source.{suffix}", "image/png" if suffix == "png" else "image/jpeg")
    mask_url = upload_to_replicate(mask_bytes, "mask.png", "image/png")
    try:
        response = requests.post(
            "https://api.replicate.com/v1/predictions",
            headers={
                "Authorization": f"Token {REPLICATE_TOKEN}",
                "Content-Type": "application/json",
                "Prefer": "wait",
            },
            json={"version": MODEL_VERSION, "input": {"image": image_url, "mask": mask_url}},
            timeout=API_TIMEOUT,
        )
        response.raise_for_status()
        prediction = response.json()
        if prediction.get("status") not in ("succeeded", "starting", "processing"):
            raise HTTPException(status_code=502, detail="AI inpainting request failed: " + str(prediction.get("error") or prediction.get("status")))
        if prediction.get("status") != "succeeded":
            get_url = prediction.get("urls", {}).get("get")
            if not get_url:
                raise HTTPException(status_code=502, detail="AI model has not returned a result URL.")
            for _ in range(60):
                import time
                time.sleep(2)
                poll = requests.get(get_url, headers={"Authorization": f"Token {REPLICATE_TOKEN}"}, timeout=20)
                poll.raise_for_status()
                prediction = poll.json()
                if prediction.get("status") == "succeeded":
                    break
                if prediction.get("status") in ("failed", "canceled"):
                    raise HTTPException(status_code=502, detail="AI inpainting failed: " + str(prediction.get("error") or prediction.get("status")))
            else:
                raise HTTPException(status_code=504, detail="AI inpainting timed out. Try a smaller image or shorter video.")
        output = prediction.get("output")
        if isinstance(output, list):
            output = output[0] if output else None
        if isinstance(output, dict):
            output = output.get("url")
        if not isinstance(output, str) or not output.startswith("https://"):
            raise HTTPException(status_code=502, detail="AI model completed without a downloadable image.")
        result = requests.get(output, timeout=60)
        result.raise_for_status()
        return result.content
    except requests.RequestException as exc:
        detail = "AI service request failed."
        if getattr(exc, "response", None) is not None:
            detail += f" HTTP {exc.response.status_code}: {exc.response.text[:300]}"
        raise HTTPException(status_code=502, detail=detail) from exc


def make_mask(width: int, height: int, x: float, y: float, w: float, h: float, brush: int = 20) -> bytes:
    if not (0 <= x < 1 and 0 <= y < 1 and 0 < w <= 1 and 0 < h <= 1 and x + w <= 1.001 and y + h <= 1.001):
        raise HTTPException(status_code=400, detail="Selection area is outside the image. Please reset the selection and try again.")
    mask = Image.new("L", (width, height), 0)
    left, top = round(x * width), round(y * height)
    right, bottom = min(width, round((x + w) * width)), min(height, round((y + h) * height))
    if right <= left or bottom <= top:
        raise HTTPException(status_code=400, detail="Selection area is too small.")
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rectangle((left, top, right - 1, bottom - 1), fill=255)
    radius = max(1, min(brush, 100))
    kernel = radius * 2 + 1
    if kernel % 2 == 0:
        kernel += 1
    mask = mask.filter(ImageFilter.MaxFilter(kernel))
    out = io.BytesIO()
    mask.save(out, format="PNG")
    return out.getvalue()


def cleanup(path: str):
    try:
        if os.path.isdir(path):
            shutil.rmtree(path, ignore_errors=True)
        elif os.path.exists(path):
            os.remove(path)
    except OSError:
        pass


@app.get("/")
def root():
    return {"service": APP_NAME, "status": "online", "engine": "Replicate LaMa inpainting", "video": "frame-by-frame; 12 fps; max 10 seconds"}


@app.get("/health")
def health():
    return {"ok": True, "configured": bool(REPLICATE_TOKEN), "engine": "Replicate LaMa inpainting"}


@app.post("/api/inpaint/image")
async def inpaint_image(
    background_tasks: BackgroundTasks,
    image: UploadFile = File(...),
    x: float = Form(...), y: float = Form(...), w: float = Form(...), h: float = Form(...),
    brush: int = Form(20),
):
    require_token()
    data = await image.read()
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image must be under 20 MB.")
    try:
        with Image.open(io.BytesIO(data)) as opened:
            original = ImageOps.exif_transpose(opened).convert("RGB")
            if original.width * original.height > 30_000_000:
                raise HTTPException(status_code=413, detail="Image dimensions are too large. Please resize it and retry.")
            mask = make_mask(original.width, original.height, x, y, w, h, brush)
            src = io.BytesIO()
            original.save(src, format="PNG")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=400, detail="The uploaded file is not a supported image.") from exc
    result = run_lama(src.getvalue(), mask)
    # Validate model output before returning a download.
    try:
        with Image.open(io.BytesIO(result)) as im:
            im.verify()
    except Exception as exc:
        raise HTTPException(status_code=502, detail="AI returned an invalid image file.") from exc
    return FileResponse(
        _write_temp(result, ".png"),
        media_type="image/png",
        filename="watermark-removed.png",
        background=background_tasks,
    )


def _write_temp(data: bytes, suffix: str) -> str:
    fd, path = tempfile.mkstemp(suffix=suffix)
    with os.fdopen(fd, "wb") as f:
        f.write(data)
    return path


@app.post("/api/inpaint/video")
async def inpaint_video(
    background_tasks: BackgroundTasks,
    video: UploadFile = File(...),
    x: float = Form(...), y: float = Form(...), w: float = Form(...), h: float = Form(...),
    brush: int = Form(20),
):
    require_token()
    data = await video.read()
    if not data or len(data) > MAX_VIDEO_BYTES:
        raise HTTPException(status_code=413, detail="Video must be under 80 MB.")
    work = tempfile.mkdtemp(prefix="wmr-video-")
    background_tasks.add_task(cleanup, work)
    input_path = os.path.join(work, "input_video")
    with open(input_path, "wb") as f:
        f.write(data)
    probe = subprocess.run([FFMPEG, "-i", input_path], capture_output=True, text=True, timeout=30)
    import re
    match = re.search(r"Duration: (\d+):(\d+):(\d+(?:\.\d+)?)", probe.stderr)
    if not match:
        raise HTTPException(status_code=400, detail="Could not read this video. Try an MP4 file.")
    duration = int(match.group(1)) * 3600 + int(match.group(2)) * 60 + float(match.group(3))
    if duration > MAX_VIDEO_SECONDS:
        raise HTTPException(status_code=413, detail=f"Video is {duration:.1f}s. The AI frame-by-frame beta is limited to {MAX_VIDEO_SECONDS} seconds to control processing time and cost.")
    frames_dir = os.path.join(work, "frames")
    out_dir = os.path.join(work, "clean")
    os.makedirs(frames_dir)
    os.makedirs(out_dir)
    subprocess.run(
        [FFMPEG, "-y", "-i", input_path, "-vf", f"fps={VIDEO_FPS}", "-frames:v", str(MAX_VIDEO_SECONDS * VIDEO_FPS), os.path.join(frames_dir, "%05d.png")],
        check=True, capture_output=True, timeout=60,
    )
    frames = sorted(Path(frames_dir).glob("*.png"))
    if not frames:
        raise HTTPException(status_code=400, detail="No frames could be extracted from this video.")
    for index, frame_path in enumerate(frames):
        try:
            with Image.open(frame_path) as opened:
                frame = opened.convert("RGB")
                mask = make_mask(frame.width, frame.height, x, y, w, h, brush)
                src = io.BytesIO()
                frame.save(src, format="PNG")
            result = run_lama(src.getvalue(), mask)
            with open(os.path.join(out_dir, frame_path.name), "wb") as f:
                f.write(result)
        except HTTPException as exc:
            raise HTTPException(status_code=exc.status_code, detail=f"Frame {index + 1}/{len(frames)}: {exc.detail}") from exc
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"AI processing failed at video frame {index + 1}/{len(frames)}. No completed video was produced.") from exc
    output_path = os.path.join(work, "watermark-removed.mp4")
    subprocess.run(
        [FFMPEG, "-y", "-framerate", str(VIDEO_FPS), "-i", os.path.join(out_dir, "%05d.png"), "-i", input_path,
         "-map", "0:v:0", "-map", "1:a?", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20",
         "-c:a", "aac", "-b:a", "128k", "-shortest", output_path],
        check=True, capture_output=True, timeout=90,
    )
    return FileResponse(
        output_path,
        media_type="video/mp4",
        filename="watermark-removed.mp4",
        background=background_tasks,
    )
