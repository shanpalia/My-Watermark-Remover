# My Watermark Remover — Web UI

Branded web interface by **PaliaAPK HUB — Developer By shanpalia**.

## Current status

This folder contains a static HTML/CSS/JS frontend suitable for GitHub Pages. It supports local image selection, drag/drop, and preview. The “Clean selected area” control deliberately reports that a processing backend is not configured: a static page cannot run the repository's Python Florence-2/LaMA inference by itself.

## Deploy the website

1. In GitHub, open **Settings → Pages** for this repository.
2. Under **Build and deployment**, choose **Deploy from a branch**.
3. Select branch `main`, folder `/web`, and save.
4. Open the Pages URL shown in Settings → Pages.

## Enable real AI removal

Deploy the original Python inference runtime on a compatible server and implement a secure API that accepts images and masks, runs detection/inpainting, and returns the result. Then connect this interface to that API. GitHub Pages is static hosting and cannot run Python/PyTorch AI inference. Keep secrets server-side and do not expose API keys in browser JavaScript.

## Attribution and license

The upstream project is [D-Ogi/WatermarkRemover-AI](https://github.com/D-Ogi/WatermarkRemover-AI), licensed under the MIT License. This web UI is an independent frontend and is not the original desktop AI inference runtime. Refer to the original repository and its LICENSE/THIRD_PARTY_NOTICES for source and dependency notices.
