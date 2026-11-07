# RetroCam 📷✨

An authentic, ultra-responsive retro camera app for Android that delivers iPhone-level quality and beats Dazz Cam in speed, polish, and film aesthetics.

Built natively in **Kotlin** with **Jetpack Compose**, **CameraX**, and **OpenGL ES 3.0** hardware-accelerated shaders powered by 3D LUTs and procedural film simulation layers.

---

## 📥 Download

**👉 [Download RetroCam.apk](https://github.com/Koushikv18/retrocam/releases/download/latest/RetroCam.apk)**

*Built automatically on every push by GitHub Actions with debug signing. Download and open on any Android device (allow "Install unknown apps").*

---

## ✨ Features

### 🎞️ 22 Data-Driven Retro Camera Looks
Each look is defined via an extensible JSON preset with authentic color response curves, custom aspect ratios, procedural grain, and vintage date stamps:
1. **CPM35** (3:2) — Classic 35mm point-and-shoot, warm lifted shadows, subtle halation.
2. **Classic Cam** (4:3) — 90s compact film camera, muted highlights, gentle rolloff.
3. **1984 HR** (4:3) — Early 80s high-res color film, rich contrast, cool cyan shadows.
4. **Inst-C** (1:1) — Square instant color film with iconic creamy Polaroid border frame.
5. **Fuji CCD** (4:3) — Early 2000s Super CCD digicam, vibrant emerald greens, crisp clarity.
6. **Kodak Gold** (3:2) — Warm golden-hour amber saturation, vintage grain, red halation.
7. **Disposable** (3:2) — Flash party disposable camera, harsh direct flash, deep vignette.
8. **VHS Cam** (4:3) — 1992 home video magnetic tape, scanlines, chromatic aberration, tape noise.
9. **Noir 35** (3:2) — High-contrast black & white street film, silver grain, rich shadows.
10. **Y2K Digicam** (4:3) — 2000s compact cyber digicam, cool blues, digital sharpening.
11. **Portra 400** (3:2) — Legendary fine-grain portrait film, flattering creamy skin tones.
12. **Cine 800T** (3:2) — Tungsten cinema film with distinct red halation glow around neon/lights.
13. **Tri-X 400** (3:2) — Iconic street photography black & white, gritty newspaper grain, 35mm film strip.
14. **Ektachrome** (3:2) — Vibrant color slide film with cardboard slide mount border.
15. **Aerochrome** (3:2) — Surreal infrared false-color film turning foliage into vivid crimson/pink.
16. **Super 8** (4:3) — Vintage 8mm motion film gate, dust specks, rounded vignette.
17. **GR Digital** (3:2) — High micro-contrast street snap aesthetic.
18. **Lomo LC-A** (3:2) — Vignetting toy camera, punchy saturated colors, soft plastic corners.
19. **Polaroid 600** (1:1) — Square instant film with milky blacks and classic white frame.
20. **Cyber-shot** (4:3) — 2005 vintage Sony CCD digicam, sensor blooming, digital date stamp.
21. **Half Frame 72** (3:4) — Vertical 35mm diptych format with perforated film border.
22. **Expired 1999** (3:2) — Magenta emulsion shift, green shadows, erratic warm light leaks.

### 🎨 Graphics & Color Pipeline (OpenGL ES 3.0)
- **3D LUT Color Grading**: 33×33×33 hardware-interpolated color cubes.
- **Linear Light Physics**: Halation and bloom computed in linear RGB space.
- **Luminance-Weighted Film Grain**: Authentic grain density adhering to film physics (stronger in midtones, falling off in highlights).
- **Procedural Film Layers**: Organic light leaks, chromatic aberration, dust & scratches, scanlines, and vignette.
- **Dither Engine**: High-frequency triangular dither eliminates 8-bit color banding.

### 🖼️ Frames & Borders
- **Polaroid Frame**: Classic off-white paper frame with extended bottom chin.
- **35mm Film Strip**: Authentic black film base with perforated sprocket holes and edge markings.
- **Slide Mount**: Cardboard 35mm projection mount.
- **Vintage Rounded**: CRT / retro rounded corners in black and cream.

### 🕒 Vintage Date Stamps
- Per-look typography: **Quartz Orange**, **LED 7-Segment**, **Dot Matrix**, and **VHS White**.
- Formats: `'YY  M  D`, `YYYY-MM-DD`, `M D 'YY HH:mm`.

### 🎥 Photo & Video
- **60 FPS Live Preview**: Real-time shader preview with zero stutter when switching looks.
- **Full-Resolution Stills**: CameraX `CAPTURE_MODE_MAXIMIZE_QUALITY` processed through the GL pipeline at 12MP+.
- **Video Recording**: High-definition video with live filters baked in and AAC audio.
- **Double Exposure**: Capture two exposures blended seamlessly.
- **Pro Camera Controls**: Tap-to-focus, exposure slider, pinch-to-zoom (1×–5×), flash/torch, 3s/5s/10s timer, rule-of-thirds grid, and horizon spirit level.

### 🎞️ In-App Gallery & Edit Mode
- Grid viewer with full-screen pinch-to-zoom viewer.
- **Import & Edit**: Import any photo from your gallery, apply any retro look, tweak intensity, warmth, grain, and re-export.
- System Gallery Integration: Saves directly to `Pictures/RetroCam` and `Movies/RetroCam` via Android `MediaStore`.

---

## 🛠️ Build & Development

### Requirements
- Android Studio Hedgehog / Iguana / Ladybug or JDK 17+
- Android SDK 34+
- Min Android version: Android 8.0 (API 26)
- Target Android version: Android 15 (API 35)

### Build Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```
