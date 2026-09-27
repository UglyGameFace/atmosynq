# Active Task

## Active task / outcome
Ship and validate the first Atmosynq Android baseline and weather dashboard: real local weather drives the live wallpaper, the app shows current/hourly/7-day weather, and exact-head GitHub CI produces an installable debug APK before device testing and visual refinement.

## Scope
- Preserve the supplied animation as the visual base.
- Remove most baked rain so dry weather can exist.
- Use foreground location setup only; store coordinates in app-private storage.
- Pull current weather, hourly forecast, 7-day forecast, and sunrise/sunset from Open-Meteo.
- Render day/night grading plus rain, snow, fog, wind-driven precipitation, cloud dimming, sunset warmth, and thunder flashes.
- Show current conditions, feels-like, high/low, humidity, wind/gusts, clouds, visibility, precipitation, sunrise/sunset, next 12 hours, and 7-day forecast in the app.
- Brand the Android project and package as Atmosynq.
- Run tests and APK assembly in GitHub Actions on pull requests and main.
- Keep third-party/private scene media out of the public repository.

## Status
PR #1 is open and mergeable on branch `feat/android-weather-wallpaper-baseline`.

The baseline live-wallpaper implementation passed exact-head CI at `e0ec4afb7acd82acb7d14c95ae38e5f658dc3ce8` in Actions run #11: unit tests passed, debug APK assembled, and artifact upload passed.

After that green gate, the same active task continued into the weather dashboard. Final review then found and fixed two correctness/privacy issues:
- hourly clear-sky icons now use the hour's actual day/night state;
- saved location/weather SharedPreferences are excluded from Android backup/device-transfer extraction, with app backup disabled.

Exact-head CI for the review fixes passed at `99b801f08c92113cf398d9198d33415239094c70` in Actions run #19: all 13 unit tests passed, debug APK assembly passed, and artifact upload passed. A final documentation-only head check is the current gate before merge.

## Findings / root cause
The source clip has nighttime color and rain permanently baked into every frame. Overlaying weather directly would make dry/day conditions wrong, so the source is first de-rained with a 9-frame temporal median and then relit/effected at runtime.

Open-Meteo `precipitation` includes rain, showers, and snowfall water-equivalent. Treating generic precipitation as rain can render rain during a snow event. The mapper therefore uses liquid-only rain + showers for rain intensity, uses WMO condition codes as an active-condition fallback, and keeps snow-only conditions free of invented rain.

Cached sunrise/sunset values can survive an offline midnight. The mapper reuses the last known sunrise/sunset clock times on the current local date until fresh weather returns.

The original workflow relied on an obsolete Android setup action path and only ran on pushes to `main`. CI now uses the runner SDK directly and validates pull requests before merge.

## Execution path
MainActivity -> LocationStore -> OpenMeteoClient -> WeatherReport/WeatherSnapshot -> WeatherController -> WeatherVisualMapper -> WeatherWallpaperService -> WeatherRendererThread -> OpenGL shader + ParticleSystem.

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
- Migrated Kotlin 2.3 JVM target configuration to the current compiler-options DSL.
- Fixed the OpenGL texture matrix uniform call caught by Android CI.
- Added the real current-weather, next-12-hours, and 7-day dashboard.
- Expanded the shared Open-Meteo request/model path with feels-like temperature, humidity, hourly forecast, daily forecast, and hourly day/night state.
- Added WMO condition descriptions and forecast symbols.
- Added parser and condition regression tests.
- Disabled app backup and excluded SharedPreferences from cloud backup/device-transfer extraction.

## Validation / results
- Source media: H.264, 720x1280, 30 fps, ~10.12 s.
- Neutralized loop preserves 302 source frames at ~10.07 s / 30 fps.
- Weather visual mapping regression checks pass for snow-without-fake-rain, condition fallbacks, rain+showers, WMO 97 lightning, fog, and offline-midnight daylight rollover.
- Baseline exact-head Android CI at `e0ec4afb7acd82acb7d14c95ae38e5f658dc3ce8` passed unit tests, APK assembly, and artifact upload.
- Exact-head CI at `99b801f08c92113cf398d9198d33415239094c70` passed all 13 unit tests, APK assembly, and artifact upload in Actions run #19.

## Cleanup
- Audio removed from wallpaper video.
- Weather/network ownership remains centralized through OpenMeteoClient/WeatherController.
- No background-location service or permission added.
- Saved weather/location preferences are excluded from backup/device transfer.
- Renderer thread is explicitly stopped and joined during teardown.
- Old `com.uglygameface.weatherscenelive` package path and branding removed from application source.

## Conflicts
None currently known. The README is intentional project documentation and has been updated to match the implemented dashboard and privacy behavior.

## Blockers / risks
- On-device GPU/battery behavior is not yet proven.
- The public APK artifact intentionally does not contain the supplied third-party scene media; it uses the procedural fallback unless an original/licensed private scene is added.
- With a private night scene supplied, daytime remains shader-based relighting of a night master, not true source-art daytime animation.
- Temporal median rain removal can subtly soften fast motion.
- Wallpaper Clean/Minimal/Detailed weather-text modes are not implemented yet.

## Backlog
- Install the green APK on a real Android device and validate location/weather/network/rendering/battery behavior.
- Add wallpaper Clean/Minimal/Detailed weather-text modes.
- Add semantic sky/window/water masks after first real-device renderer validation.
- Add dedicated hail particles for WMO 96/99 after renderer validation.
- Add optional effect-intensity calibration after device testing.

## Git / CI state
- Repository: `UglyGameFace/atmosynq`
- Default branch: `main`
- Baseline main before source import: `5f4331981af8c54119f4372bd8c4e8f686fca51a`
- Implementation branch: `feat/android-weather-wallpaper-baseline`
- Pull request: #1
- Last fully green implementation head: `99b801f08c92113cf398d9198d33415239094c70`
- Last green Actions run: #19

## Next step
Let the documentation-only final head CI pass, mark PR #1 ready, merge it, verify post-merge `main` CI, download the resulting APK artifact, and move immediately into real-device validation.
