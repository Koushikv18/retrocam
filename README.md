# RetroCam
Dazz-style retro camera app. 13 looks incl. CPM35, Classic Cam, 1984 HR, Inst-C (Polaroid frame), Fuji CCD, Disposable. Capacitor (Android) + WebGL film/CCD shaders.

## Features
- Live WebGL preview: CCD, Y2K, Kodak Gold, Fuji, Instant, VHS, Noir, CPM35, Classic Cam
- Grain, halation, bloom, chromatic aberration, light leaks, vignette, scanlines, date stamp
- Full-resolution stills (ImageCapture) processed through the same shader, up to ~3000px
- Saved locally in app storage + exported to the system gallery (Pictures/RetroCam)
- In-app gallery: view, share, re-save to Photos, delete
- Timer (3s/10s), grid, flash/torch, front/back camera, pinch zoom, swipe to change look
- Remembers your settings, handles app pause/resume, back button, portrait lock

## Download
**[Download RetroCam.apk](https://github.com/Koushikv18/retrocam/releases/download/latest/RetroCam.apk)** (works after the first successful build, see below)

## Get the APK
1. Move `ci/build-apk.yml` to `.github/workflows/build-apk.yml` (GitHub web UI, or a token with `workflow` scope).
2. It builds on every push to `main` (or run it manually in the Actions tab) and publishes `RetroCam.apk` to the Releases page, which the Download link above points to.
3. Open the link on your phone, install the APK (allow "install unknown apps").

## Local preview in a desktop browser
`npm run serve` and open http://localhost:8080 (saves to IndexedDB, exports via download).
