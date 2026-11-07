# RetroCam
Dazz-style retro camera app. Capacitor (Android) + WebGL film/CCD shaders.

Features (v0.1): live shader preview, 7 presets (CCD, Y2K, Kodak Gold, Fuji, Instant, VHS, Noir),
grain, halation, bloom, chromatic aberration, light leaks, vignette, scanlines, date stamp,
front/back camera, flash, save to gallery (RetroCam album).

## Get the APK
Move `ci/build-apk.yml` to `.github/workflows/build-apk.yml` (needs a token with `workflow` scope, or add via the GitHub web UI), then push to `main` -> GitHub Actions -> Actions tab -> latest run -> artifact `retrocam-debug-apk`.
Download, unzip, install `app-debug.apk` on your phone (allow "install unknown apps").

## Local dev
`npm install && npx cap add android && npx cap sync android` then open `android/` in Android Studio.
