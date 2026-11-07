# RetroCam
Dazz-style retro camera app. Capacitor (Android) + WebGL film/CCD shaders.

## Features
- Live WebGL preview: CCD, Y2K, Kodak Gold, Fuji, Instant, VHS, Noir, CPM35, Classic Cam
- Grain, halation, bloom, chromatic aberration, light leaks, vignette, scanlines, date stamp
- Full-resolution stills (ImageCapture) processed through the same shader, up to ~3000px
- Saved locally in app storage + exported to the system gallery (Pictures/RetroCam)
- In-app gallery: view, share, re-save to Photos, delete
- Timer (3s/10s), grid, flash/torch, front/back camera, pinch zoom, swipe to change look
- Remembers your settings, handles app pause/resume, back button, portrait lock

## Get the APK
1. Move `ci/build-apk.yml` to `.github/workflows/build-apk.yml` (GitHub web UI, or a token with `workflow` scope).
2. Push to `main` (or run the workflow manually). Actions tab -> latest run -> artifact `retrocam-debug-apk`.
3. Unzip, install `app-debug.apk` (allow "install unknown apps").

## Local preview in a desktop browser
`npm run serve` and open http://localhost:8080 (saves to IndexedDB, exports via download).
