# Active Task

## Active task / outcome
Migrate Atmosynq's dashboard weather hero from the procedural Android Canvas renderer to Google Filament and push mobile real-time rendering quality aggressively without breaking the working weather app.

## Baseline
- production/main before this branch: `8dd937bf844693a481e7ab17eda65d55cd802649`
- production version: 0.3.1 / versionCode 7
- v0.3.1 fixed the real logo and improved Canvas motion, but the real-device result still looked visibly procedural / primitive

## Current branch
`feat/v0.4.0-filament-max`

## Version
- versionCode: 8
- versionName: 0.4.0

## Renderer migration implemented
### Filament runtime
Pinned from Maven Central:
- `com.google.android.filament:filament-android:1.77.0`
- `com.google.android.filament:gltfio-android:1.77.0`
- `com.google.android.filament:filament-utils-android:1.77.0`

### New dashboard renderer
`FilamentWeatherHeroView` now owns the intended dashboard hero.

It uses:
- Filament `ModelViewer`
- Android `TextureView` so existing text / metric overlays compose normally
- a real glTF / GLB PBR scene
- weather-driven sun and environment lighting
- Choreographer-based animation
- named scene-node animation for clouds, Atmosynq cyan/purple ribbons and sun
- existing `WeatherHeroView` underneath as a fail-safe fallback

If Filament initialization, model loading, or rendering fails, the app must show the existing Canvas hero instead of a black/blank card.

## First PBR scene
Embedded scene includes:
- PBR water surface
- foreground terrain
- three 3D mountain forms
- three translucent cloud objects
- emissive sun
- emissive cyan Atmosynq atmosphere ribbon
- emissive purple Atmosynq atmosphere ribbon

Weather drives:
- sky color
- indirect-light intensity
- direct sun intensity
- sun color
- cloud animation speed from current wind
- storm light reduction
- day / night lighting balance

## Quality stack
The first Filament configuration enables:
- HDR color buffer: HIGH
- dynamic resolution: HIGH
- dynamic-resolution range: 58% to 100%
- dynamic-resolution sharpness: 0.92
- 4x MSAA
- FXAA
- ambient occlusion
- bloom
- screen-space reflections
- screen-space refraction
- post-processing
- direct SUN light with shadows
- spherical-harmonic indirect light

This is deliberately aggressive. Dynamic resolution is the safety valve before removing visual features.

## Asset integrity
The embedded Filament scene is stored as:
`app/src/main/assets/filament/atmos_scene.glb.b64`

A JVM regression test validates:
- glTF magic
- GLB version 2
- declared file length
- chunk boundaries
- JSON chunk
- BIN chunk

This exists because Android resource packaging succeeding does not prove a shipped binary scene is valid.

## Preserved features
Do not regress:
- current Open-Meteo weather
- foreground location capture
- Animated / Static mode
- hourly forecast
- 7-day forecast
- widget pinning
- current Android widget behavior
- wallpaper picker
- existing live wallpaper
- approved integrated Atmosynq logo

## Not migrated yet
The Android live wallpaper still uses the existing EGL/GLES renderer in this PR.

Reason:
1. validate Filament on the dashboard and real Samsung hardware first
2. then extract/share the scene controller
3. migrate the WallpaperService through Filament's SurfaceHolder path

Do not delete the existing wallpaper renderer until the Filament wallpaper path passes real-device validation.

## Validation gate
PR #11 — `v0.4.0: migrate dashboard weather hero to Filament` — is open to `main`.

Exact-head CI must pass:
- dependency resolution
- GLB integrity regression test
- all existing unit tests
- Android resource linking
- Kotlin compilation
- debug APK assembly
- APK artifact upload

Do not merge until exact-head CI is green.

## Real-device validation after build
1. dashboard hero actually renders Filament rather than fallback Canvas
2. no black / transparent / white TextureView
3. text and metrics remain above the Filament surface
4. PBR water has visible specular lighting
5. terrain has real light / shadow response
6. clouds have real 3D depth
7. cyan / purple emissive elements bloom
8. AO adds visible contact depth
9. SSR does not create catastrophic artifacts
10. dynamic resolution keeps animation smooth
11. Animated mode moves scene nodes smoothly
12. Static mode renders a stable frame
13. weather refresh changes lighting without recreating the app
14. no crash when backgrounding / returning
15. fallback Canvas activates cleanly on Filament failure
16. location, forecasts, widget and wallpaper picker remain intact
17. heat and battery are measured before increasing quality further

## Next visual phase after this foundation
Once the real device proves the Filament path:
- replace primitive prototype geometry with a production scenic environment pack
- HDR IBL / sky environment
- higher-detail terrain and foliage
- physically wet materials
- GPU rain / snow particles
- cloud-volume / layered cloud material work
- water normal animation and stronger reflections
- lightning that illuminates the entire 3D scene
- color grading / exposure curves
- quality presets with Ultra tuned for flagship devices
- move the same renderer architecture into the live wallpaper

## Next step
Run exact-head Android CI on the v0.4.0 Filament foundation. Fix real API/build failures before merge. Then install the exact green APK on the Samsung and judge Filament itself before adding production-quality scene assets.
