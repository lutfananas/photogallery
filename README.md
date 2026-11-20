# Gallery — Android photo gallery & editor

A photo gallery for Android 13+ (targets **Android 17 / API 37**), built with Kotlin and Jetpack Compose. The look follows a warm, minimal concept: cream/white Discover screen, rounded photo cards, a floating charcoal nav bar with an orange pill, and a dark editor.

## Features

**Gallery**
- Discover: album circles, staggered photo grid, search
- Pinch the Home grid to zoom, like iOS Photos: 2 large-card columns, or a dense 3–6 column square grid
- Long-press to multi-select, then share or favorite in bulk
- Liquid-glass nav and selection bars (backdrop blur, edge refraction, vibrancy)
- Albums, album detail, favorites, profile/stats
- Full-screen viewer: swipe, pinch/double-tap zoom, share, favorite, move to trash, details sheet
- Camera button opens the system camera and saves to DCIM/Camera
- Supports Android 14+ "selected photos only" access

**Editor** (saves a copy to `Pictures/PhotoGallery`, never overwrites the original)
- **Crop**: free/original/1:1/2:3/3:2/4:5/16:9, drag corners or move, straighten ruler (±45°), rotate 90°, flip
- **Adjust**: Auto, brightness, exposure, contrast, saturation, warmth, tint, highlights, shadows, fade, sharpen, grain, vignette
- **Filters**: 18 presets with intensity
- **Curves**: RGB / R / G / B tone curves (tap to add a point, drag it, double-tap to remove it)
- **Markup**: pen, highlighter, neon, line, arrow, rectangle, oval, eraser; colors, size, stroke undo/redo
- Global undo/redo, hold-to-compare, discard confirmation

### Performance (120 Hz)
- All color work (matrix, highlights/shadows, curves LUT, sharpen, vignette, grain) runs in **one AGSL GPU shader** as a `RenderEffect`. Moving a slider only changes shader uniforms, with no bitmap processing on the CPU.
- Export runs the same shader off-screen through `HardwareRenderer` at up to 4096 px.
- The app asks for the display's highest refresh-rate mode, because some skins (HyperOS) keep apps at 60 Hz unless they ask.
- Use the **release** APK for smooth performance. Compose debug builds are much slower.

## Build

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (debug-signed)
```

Requires JDK 17+ and Android SDK platform 37.

## Credits
- Liquid glass comes from [Kyant's backdrop library](https://github.com/Kyant0/AndroidLiquidGlass) (Apache-2.0).
- The editor's tool set (crop, filters, tone curves, draw modes) was modeled on the single-edit tools of
[ImageToolbox](https://github.com/T8RIN/ImageToolbox) by T8RIN (Apache-2.0). The implementation here is our own and contains no ImageToolbox code.
