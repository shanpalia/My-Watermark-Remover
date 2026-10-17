# ShanPalia Watermark Remover

Android app project for ShanPalia.

## Build APK with GitHub Actions

1. Open the **Actions** tab in this repository.
2. Select **Build Android APK**.
3. Click **Run workflow** (or push a commit to `main`).
4. Open the completed run and download the artifact named `ShanPalia-Watermark-Remover-debug-apk`.

## Current project status

This repository was initially empty. The workflow has been added, but the Android application source files and Gradle app configuration still need to be added before a successful APK build is possible.

## Online image/video processing

GitHub Actions only builds the APK; it is not an online image/video processing server. Online AI processing needs a separately deployed backend/API. Keep API credentials in backend environment secrets and never put private keys in the Android app or public repository.
