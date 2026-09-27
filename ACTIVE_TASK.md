# Active Task

## Active task / outcome
Validate the merged Atmosynq v0.2.0 official branding and app-wide Animated / Static visual modes on the real Android device, then root-cause any launcher/widget/wallpaper issues before starting another implementation task.

## Current main
Repository:
`UglyGameFace/atmosynq`

Current main SHA:
`0a5e21f08459f2c656e04554e8538135ed43a943`

Functional merged implementation SHA:
`9fa5df52c499dbeaefcf26e7a3363f4732e443b3`

Merged PR:
#4 — Add official Atmosynq branding and animated/static modes

PR exact head:
`132cb785ffadaca3bf4bd950f5174e2634a57275`

PR CI:
Actions run #31 passed.

Post-merge main CI:
Actions run #32 passed:
- Android resource linking
- Kotlin compilation
- unit tests
- debug APK assembly
- artifact upload

The later documentation-only main commit `0a5e21f08459f2c656e04554e8538135ed43a943` has no workflow run/status attached. It does not change app code.

## Status
Implementation is merged and CI-validated.

The active task is currently blocked on real-device validation. That device-only gate has not been completed from repository evidence, so the task is not marked complete and no unrelated implementation task has been started.

## Version
- versionCode: 3
- versionName: 0.2.0

## Official brand now in code
- selected luminous cyan/blue/violet atmospheric vortex
- selected stylized Atmosynq cyan/blue/violet wordmark
- launcher icon
- round icon
- Android adaptive icon
- Android launch/splash mark
- in-app header
- widget brand mark
- widget picker preview

## Motion modes
Animated is the default.

### Animated
App:
- Atmosynq vortex continuously pulses, floats, and glows

Home widget:
- supported Android RemoteViews/ViewFlipper animation
- three 320x187 weather-rendered frames
- ~1.35 second fade/flip cadence
- sun/moon glow changes
- cloud placement changes
- rain streak positions change
- snow positions change
- fog drifts
- thunderstorm sequence includes a lightning frame

Live wallpaper:
- scene video plays when present
- weather transitions tween smoothly
- rain/snow particles move
- lightning can flash
- normal per-surface rendering cadence is used

### Static
App:
- brand mark remains still

Home widget:
- separate non-auto-start layout
- one representative weather frame
- no scene cycling
- static thunderstorm frame can retain visible lightning artwork

Live wallpaper:
- scene video pauses
- current weather styling remains
- rain/snow particles stay visible but freeze
- lightning flashing stops
- weather state updates without tweening
- renderer uses a 1 FPS low-motion refresh cadence

Changing the visual mode immediately requests a widget refresh, and the wallpaper reads the preference continuously.

## Honest platform boundary
The Android launcher/adaptive icon is static. Ordinary launchers do not expose an arbitrary continuously running app-icon renderer.

The app, widget, and live wallpaper provide motion where Android supports it.

## Latest validated APK
Source main SHA:
`9fa5df52c499dbeaefcf26e7a3363f4732e443b3`

Actions run:
#32

Artifact:
`atmosynq-debug-apk`

Extracted APK size:
1,032,763 bytes

APK SHA-256:
`3bbf9b46652b27b14453f2e1e3834fbddb3d4de87c4d6dc1d6488b151e26164f`

## Real-device validation next
Install v0.2.0 and verify:

1. launcher icon uses the selected vortex and survives Samsung/Smart Launcher masks cleanly
2. splash screen uses the selected mark without awkward cropping
3. app header shows the selected vortex + stylized Atmosynq wordmark
4. Animated mode makes the app mark continuously pulse/float/glow
5. Static mode stops that motion immediately
6. widget uses the Atmosynq mark
7. Animated widget visibly cycles weather frames on Smart Launcher
8. Static widget stays visually still
9. clear-weather animation still has subtle sun/moon motion
10. rain/snow/fog/cloud/storm scenes visibly change between animated frames
11. thunder animation is not obnoxiously frequent
12. switching modes refreshes the widget promptly
13. Animated live wallpaper resumes video/particles/lightning
14. Static live wallpaper pauses/freezes motion while keeping the correct weather scene
15. home / lock / both wallpaper behavior from PR #3 still works
16. no new crashes, clipping, heat, or abnormal battery use

## Existing known boundary
The public repo intentionally does not bundle the supplied third-party wallpaper video. Without an original/licensed private `scene_neutral.mp4`, the wallpaper uses the procedural fallback.

## Backlog
### Atmosynq Weather Portal / Xiaomi rear display
Requested next implementation after the active v0.2.0 device-validation gate is cleared, unless the user explicitly issues a FORCE SWITCH.

Goal:
Turn supported rear/secondary phone displays into a sensor-reactive, forecast-scrubbable living weather environment instead of a conventional weather card.

Planned product modes:
- **NOW** — live weather scene synchronized to current conditions, time, sunrise/sunset, wind and precipitation.
- **FUTURE** — physically scrub the forecast timeline; the scene transitions continuously through upcoming weather rather than swapping forecast cards.
- **WORLD** — optional camera-backed AR-style forecast visualization over the user's surroundings.

Core scene behavior:
- precipitation/cloud movement follows real wind/storm direction when data supports it
- gyroscope/accelerometer influence parallax and surface droplets
- rain droplets can slide over a virtual glass layer
- touch can clear condensation or brush visual snow
- real sun/moon timing and moon phase feed the scene
- camera-ring-aware HUD can display compact precipitation/temperature/UV/AQI progress arcs
- face-down ambient mode can reduce text and behave as atmospheric art
- Animated / Static accessibility preference remains authoritative

Architecture requirement:
- extend the existing shared Atmosynq weather/scene model rather than create a duplicate weather stack
- introduce a platform-neutral rear/secondary-display scene contract
- isolate Xiaomi-specific integration behind an adapter
- **do not make Shizuku a required dependency**
- prefer official Xiaomi/Android display paths when available
- any optional privileged/Shizuku bridge must remain a separable enthusiast adapter, never the core renderer

Validation requirement before claiming Xiaomi support:
- compile/unit tests
- existing widget/wallpaper regression suite
- secondary-display rendering tests where testable
- real Xiaomi hardware validation
- battery/thermal behavior
- confirm current official Xiaomi API/whitelisting path before store-facing claims

## Next step
Complete the v0.2.0 real-device validation gate. Root-cause any observed Android launcher/widget/wallpaper problems before beginning the Weather Portal implementation.
