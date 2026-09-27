# Active Task

## Active task / outcome
Real-device validation of merged Atmosynq v0.2.1 after replacing the rejected split branding/UI with the approved integrated phone-safe logo and weather-first dashboard.

## Current production source
Repository:
`UglyGameFace/atmosynq`

Merged PR:
#5 — Use integrated Atmosynq logo and weather-first dashboard

Functional merged main SHA:
`f5fb3fe26fb1a9ef151e553a3b2e61ed14930c7e`

PR exact head:
`cbaccebc899987d367cfcb81edeea3f5f5c69c74`

PR CI:
Actions run #39 passed.

Post-merge main CI:
Actions run #40 passed:
- Android resource linking
- Kotlin compilation
- unit tests
- debug APK assembly
- APK artifact upload

## Version
- versionCode: 4
- versionName: 0.2.1

## Locked brand rule
Use this single integrated Atmosynq logo from now on:
- cyan/blue/violet atmospheric vortex
- **Atmosynq name inside the logo**
- no separate external wordmark
- phone-safe composition

The obsolete split `atmosynq_mark.webp` and `atmosynq_wordmark.webp` assets are removed.

## Merged UI changes
- integrated logo used for launcher icon
- phone-safe adaptive icon wrapper
- integrated round icon
- integrated splash branding
- integrated app header
- integrated widget badge and widget-picker branding
- compact brand area instead of oversized split logo treatment
- weather-first dashboard
- high-resolution current-weather hero scene
- Animated / Static segmented control
- compact rounded primary/secondary actions
- hourly + 7-day forecasts retained
- darker cyan/navy visual system

## Motion behavior
Animated:
- high-resolution app hero cycles weather scene frames with short fades
- widget continues its supported ViewFlipper weather animation
- live wallpaper keeps full motion

Static:
- app hero freezes on a representative weather scene
- widget remains still
- live wallpaper freezes motion while retaining weather styling

The logo itself no longer bobs/pulses. Motion belongs to the weather experience.

## Latest validated APK
Source SHA:
`f5fb3fe26fb1a9ef151e553a3b2e61ed14930c7e`

Actions run:
#40

Artifact:
`atmosynq-debug-apk`

APK size:
997,042 bytes

APK SHA-256:
`ac59325971eb087dde9f5cff8057ac35854b6a698032a2737ac68a2d72a6f220`

## Real-device validation now
Install the exact v0.2.1 APK and verify:

1. integrated logo is readable and not cropped by Smart Launcher / Samsung masks
2. Atmosynq name is inside the icon and no duplicate external wordmark appears
3. splash is centered and not awkwardly cropped
4. app header is compact
5. current-weather hero visually dominates the dashboard
6. hero scene matches real local conditions
7. Animated hero moves cleanly without looking like a slideshow
8. Static hero stays completely still
9. segmented Animated / Static control is visually clear
10. action buttons no longer resemble default gray Android buttons
11. hourly and 7-day forecast remain readable
12. widget uses the integrated logo cleanly
13. widget Animated / Static behavior still works
14. live wallpaper Animated / Static behavior still works
15. home / lock / both wallpaper behavior remains intact
16. no clipping, crash, heat, or obvious battery regression

## Existing boundary
The public repository does not distribute the supplied third-party/copyrighted scene video. Without an original/licensed private `scene_neutral.mp4`, the wallpaper uses the procedural fallback.

## Backlog after this device gate
- Xiaomi/rear-display Weather Portal
- Apple SwiftUI / WidgetKit / ActivityKit target
- richer original/licensed scene library

## Next step
Install and visually validate the exact v0.2.1 APK from post-merge run #40. Use screenshots or screen recording from the device to root-cause any remaining visual issue before starting the next platform feature.
