# My Watermark Remover

PaliaAPK HUB · Developer By shanpalia.

## Online AI inpainting backend

The GitHub Pages UI can use a separately deployed FastAPI backend. It calls the Replicate `allenhooo/lama` inpainting model to fill the selected rectangular region. The Replicate token stays on the server and must never be placed in browser JavaScript or committed to Git.

### Deploy the backend on Render

1. Create a Replicate account and create an API token at https://replicate.com/account/api-tokens. AI predictions and file uploads can incur charges; check current pricing before processing videos.
2. In Render, create a Web Service from this repository, or use the included `render.yaml` Blueprint. If creating manually, set **Root Directory** to `backend`, **Build Command** to `pip install -r requirements.txt`, and **Start Command** to `uvicorn app:app --host 0.0.0.0 --port $PORT`.
3. Add `REPLICATE_API_TOKEN` as a secret environment variable in Render. Never put this token in GitHub or the webpage.
4. Once deployed, copy the Render service URL (for example, `https://your-service.onrender.com`) into the **Backend URL** field on both the image and video pages, then press **Connect backend**. The pages save the URL in that browser.
5. Confirm the backend health response shows `configured: true`. If false, add the token in Render and redeploy.

### API behavior

- Image: `POST /api/inpaint/image`, multipart fields `image, x, y, w, h, brush`. Returns a PNG only when AI produces a valid result.
- Video: `POST /api/inpaint/video`, multipart fields `video, x, y, w, h, brush`. Processes frames with LaMa and returns an MP4 with source audio where present.
- Video beta limit: 10 seconds and 80 MB; processed at 12 fps. Every frame invokes the AI model separately, so video can take time and cost significantly more than one image. It may have a lower frame rate than the original.
- Backend limits: image 20 MB. Processing errors are returned as errors; the UI does not display a fake success.
- Replicate receives the uploaded source image/video frames and masks. Review Replicate's current pricing and data-retention terms before use.

### Local backend

```bash
cd backend
python -m venv .venv
# activate the environment, then:
pip install -r requirements.txt
export REPLICATE_API_TOKEN=your_token_here
uvicorn app:app --reload --port 8000
```

Then enter `http://127.0.0.1:8000` as the backend URL in the webpage. Never commit a real token.

## Android project

The repository also contains a native Android app. Its existing OpenCV-based offline image cleanup is separate from the online web backend and is not automatically connected to the Render API.
