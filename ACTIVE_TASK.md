# Active Task

## Active task / outcome
Ship and validate the first Atmosynq Android baseline: real local weather drives the live wallpaper, the project is hosted in `UglyGameFace/atmosynq`, and exact-head GitHub CI produces an installable debug APK before additional product expansion.

## Scope
- Preserve the supplied animation as the visual base.
- Remove most baked rain so dry weather can exist.
- Use foreground location setup only; store coordinates on-device.
- Pull current weather and sunrise/sunset from Open-Meteo.
- Render day/night grading plus rain, snow, fog, wind-driven precipitation, cloud dimming, sunset warmth, and thunder flashes.
- Brand the Android project and package as Atmosynq.
- Run tests and APK assembly in GitHub Actions on pull requests and main.
- After the baseline build is proven, continue the same product task into the actual weather dashboard/forecast UI described in the README.

## Status
Atmosynq repo exists. Source has been rebranded locally and PR CI has been enabled. Exact-head Android CI is the current gate.

## Findings / root cause
The source clip has nighttime color and rain permanently baked into every frame. Overlaying weather directly would make dry/day conditions wrong, so the source is first de-rained with a 9-frame temporal median and then relit/effected at runtime.

Open-Meteo `precipitation` includes rain, showers, and snowfall water-equivalent. Treating generic precipitation as rain can render rain during a snow event. The mapper therefore uses liquid-only rain + showers for rain intensity, uses WMO condition codes as an active-condition fallback, and keeps snow-only conditions free of invented rain.

Cached sunrise/sunset values can survive an offline midnight. The mapper reuses the last known sunrise/sunset clock times on the current local date until fresh weather returns.

The original generated workflow only ran on pushes to `main`; that would not validate the implementation branch before merge. Atmosynq CI now also runs on pull requests targeting `main`.

## Execution path
MainActivity -> LocationStore -> OpenMeteoClient -> WeatherController -> WeatherVisualMapper -> WeatherWallpaperService -> WeatherRendererThread -> OpenGL shader + ParticleSystem.

## Changes
- Generated `scene_neutral.mp4` using FFmpeg temporal median filtering.
- Added live wallpaper service and EGL/OpenGL ES 2 renderer.
- Added MediaPlayer -> SurfaceTexture video decoding.
- Added current weather + sunrise/sunset integration.
- Added precipitation, wind drift, fog, snow, cloud dimming, sunset warmth, and lightning effects.
- Added cached weather fallback.
- Added foreground location setup with approximate-location support and no background-location permission.
- Added WMO precipitation classification and thunder mapping regression coverage.
- Added offline-midnight sunrise/sunset rollover.
- Added renderer-thread join during engine destruction.
- Rebranded root project, Android namespace/application ID, UI strings, cache keys, thread names, and HTTP user-agent to Atmosynq.
- Added `.gitignore`.
- Made the scene video optional at runtime; the public repo uses a procedural fallback and ignores private/licensed `scene_neutral.mp4` media.
- Updated GitHub Actions to validate PRs and upload `atmosynq-debug-apk`.

## Validation / results
- Source media: H.264, 720x1280, 30 fps, ~10.12 s.
- Neutralized loop preserves 302 source frames at ~10.07 s / 30 fps.
- Weather visual mapping regression checks previously passed for snow-without-fake-rain, condition fallbacks, rain+showers, WMO 97 lightning, and offline-midnight daylight rollover.
- Full Android test/APK build is the active validation gate and must pass on the exact PR head before any completion claim.

## Cleanup
- Audio removed from wallpaper video.
- Weather/network ownership remains centralized through WeatherController/OpenMeteoClient.
- No background-location service or permission added.
- Renderer thread is explicitly stopped and joined during teardown.
- Old `com.uglygameface.weatherscenelive` package path and branding removed from application source.

## Conflicts
None currently known. The remote README is intentional user-created project documentation and is being retained/updated, not discarded.

## Blockers / risks
- Android compilation and on-device GPU/battery behavior are not yet proven.
- With a private scene supplied, daytime remains shader-based relighting of a night master, not true source-art daytime animation.
- Temporal median rain removal can subtly soften fast motion.
- The README describes the target weather dashboard and forecast experience; those screens are not yet implemented in the current baseline.

## Backlog
- Implement the real weather dashboard/hourly/7-day forecast after baseline APK CI passes.
- Add wallpaper Clean/Minimal/Detailed weather-text modes.
- Add semantic sky/window/water masks after first real-device renderer validation.
- Add dedicated hail particles for WMO 96/99 after renderer validation.
- Add optional effect-intensity calibration after device testing.

## Git / CI state
- Repository: `UglyGameFace/atmosynq`
- Default branch: `main`
- Baseline main before source import: `5f4331981af8c54119f4372bd8c4e8f686fca51a`
- Implementation branch/PR: to be created for this source import.

## Next step
Push this exact Atmosynq baseline to an implementation branch, open a PR, inspect exact-head CI, repair any real compiler/test/build failures, and obtain the APK artifact before expanding the weather dashboard.
