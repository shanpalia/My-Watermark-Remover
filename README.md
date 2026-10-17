# My Watermark Remover

Android app by **PaliaAPK HUB** — **Developer By shanpalia**.

## Current implementation

- Native Android app written in Kotlin.
- White and mint/emerald theme.
- Select an image from the device.
- Paint over a watermark area with a touch brush and reset the mask.
- Remove the selected area offline using OpenCV Telea inpainting.
- Save the result to the Pictures folder.
- Large images are scaled down to a maximum dimension of 1800 px to reduce memory use.

## Build a debug APK

1. Open the **Actions** tab.
2. Select **Build Android APK**.
3. Run the workflow, or push a commit to `main`.
4. When the run succeeds, download the artifact named `My-Watermark-Remover-debug-apk`.

## Important limitations

This version implements **image** cleanup with classical OpenCV inpainting, not a neural AI model. Video watermark removal, cloud/online AI processing, subscriptions, and the full visual mockup's secondary screens are not implemented yet. Results depend on the selected mask and may not perfectly reconstruct complex backgrounds.

GitHub Actions builds the APK; it is not a processing server. Online processing requires a separately deployed backend/API. Do not commit API secrets to this repository.

## Branding

- App: My Watermark Remover
- Platform: PaliaAPK HUB
- Developer: Developer By shanpalia
