# Active Task

## Active task / outcome
Turn Atmosynq's working Filament dashboard hero into a genuinely interactive, weather-reactive visual experience on Android, while preserving the production weather/location/widget/wallpaper paths.

## Scope lock
This is a continuation of the dashboard-renderer task. Do not switch to iOS, Xiaomi rear-display support, widget redesign, wallpaper-engine migration, API/provider work, or unrelated cleanup until this task passes its validation gate.

## Production baseline
- main SHA at branch start: `c713b52f62850ffa65441ba09f4377097115c797`
- merged PR #11: `v0.4.0 migrate Atmosynq dashboard hero to Filament`
- PR #11 exact-head CI run #65: passed
- production version before this branch: 0.4.0 / versionCode 8

## Current branch
`feat/v0.4.1-interactive-weather-portal`

## Target version
- versionCode: 9
- versionName: 0.4.1

## Real-device finding / root cause
The v0.4.0 Filament path is technically functioning on the Samsung test device, but the visual gate failed.

The screenshots prove:
- the Filament/TextureView path renders rather than presenting a blank surface
- weather data and dashboard overlays still render
- the scene itself looks primitive and low-detail
- the shipped `app/src/main/assets/filament/atmos_scene.glb` is only a tiny prototype environment, so HDR/AO/bloom/SSR cannot manufacture missing art detail
- the hero was passive: no direct drag, pinch, tap, camera, or weather-FX interaction
- the dashboard composition hid too much of the scene behind dark scrims and oversized chrome

The problem is therefore not another post-processing toggle. The renderer foundation works; the presentation and interaction layer were underbuilt.

## Execution path
`MainActivity`
→ creates `FilamentWeatherHeroView`
→ Filament `ModelViewer` renders `assets/filament/atmos_scene.glb` on a `TextureView`
→ live `WeatherSnapshot` drives lighting / scene motion
→ `WeatherFxOverlayView` adds high-frequency weather atmosphere above the 3D scene
→ touch gestures update the Filament camera and overlay parallax
→ existing `WeatherHeroView` remains underneath as the graphics failure fallback

## Changes on this branch

### Interactive 3D portal
`FilamentWeatherHeroView` now supports:
- horizontal drag to look around the scene
- bounded vertical camera tilt
- pinch-to-zoom
- tap atmosphere ripple
- subtle animated camera drift when Animated mode is enabled
- parallax coupling between camera movement and atmospheric FX
- gesture arbitration that does not intentionally disable the dashboard's vertical ScrollView until a horizontal drag or pinch is confirmed

### Live atmospheric FX
New `WeatherFxOverlayView` renders above Filament:
- cyan/purple atmospheric ribbons
- night stars
- wind streaks
- rain driven by rain/showers/precipitation and weather code
- snow driven by snowfall/weather code
- fog driven by fog code and visibility
- thunder flashes / lightning
- touch pulse rings
- wet moving reflection streaks
- parallax foreground pine silhouettes for stronger scene depth
- cinematic vignette
- Animated / Static support

The FX layer uses the existing `WeatherSnapshot`; no duplicate weather provider or second state pipeline was introduced.

### Dashboard composition
The dashboard now:
- gives the weather scene substantially more vertical space
- shrinks the oversized brand mark
- uses a compact sync pill
- lightens the full-card scrim so the graphics remain visible
- makes the bottom metric glass more transparent
- adds an explicit `LIVE 3D • DRAG • PINCH • TAP` affordance
- increases primary temperature presence without removing current metrics

## Preserved behavior
Must remain intact:
- Open-Meteo weather retrieval
- foreground location capture and private location persistence
- Animated / Static preference
- hourly forecast
- 7-day forecast
- widget pinning and current widget behavior
- live-wallpaper picker
- existing live wallpaper renderer
- approved Atmosynq logo
- Filament failure fallback to `WeatherHeroView`

## Current status
Implementation is complete on the feature branch, but the task is not closed because real-device validation is still required.

Validation evidence before this documentation update:
- code head: `310fafc043eb8de3a4fae8aac8109bf4202dbbf4`
- GitHub Actions: Atmosynq Android CI run #68
- unit tests: passed
- Kotlin / Android debug build: passed
- APK artifact upload: passed

The documentation-only head created by this status update must also remain green before the branch is treated as the exact validated PR head.

## Validation required
Exact branch head must pass:
- dependency resolution
- all JVM/unit tests
- GLB integrity regression test
- Kotlin compilation
- Android resource linking
- debug APK assembly
- APK artifact upload

Real-device gate after CI:
1. Filament scene renders, not the Canvas fallback
2. no black/white/transparent TextureView
3. drag rotates the view without breaking vertical page scrolling
4. pinch zoom is bounded and stable
5. tap produces visible atmosphere feedback
6. rain/snow/fog/thunder effects match current weather conditions
7. Animated mode moves continuously; Static mode stays still except direct user interaction
8. text/metrics remain readable but no longer bury the graphics
9. background/resume does not crash or leak renderer state
10. location, forecasts, widget and wallpaper picker remain working
11. animation remains smooth enough on the Samsung test device
12. heat/battery behavior is acceptable for dashboard use

## Cleanup / conflict inspection
Before merge:
- inspect the affected render package for duplicate animation or gesture logic
- ensure the Canvas fallback is still only a fallback and was not accidentally made a second live renderer after Filament succeeds
- remove temporary debug code
- inspect final diff for unrelated files and generated artifacts

## Backlog after this gate
The v0.4.1 interaction/atmosphere pass does not magically turn the tiny prototype GLB into final cinematic art. Once this branch is proven on-device, the same renderer can receive:
- a production scenic environment asset replacing the prototype GLB
- higher-detail terrain / foliage
- physically wet materials and animated water normals
- HDR image-based lighting
- more advanced cloud volume/layer work
- scene-wide lightning illumination
- quality presets
- eventual reuse in the live WallpaperService

Those remain behind the current validation gate rather than being stacked blindly into this branch.

## Next step
Confirm CI remains green on the final documentation-only branch head, inspect the final diff, then install the exact green APK on the Samsung for the real visual/interaction gate. Do not merge until the device check verifies the interaction, visuals, scroll arbitration, stability and preserved app paths.
