# Active Task

## Active task / outcome
Validate Atmosynq v0.2.1 after replacing the rejected split branding/UI with the user-approved integrated phone-safe Atmosynq logo and a weather-first dashboard.

## User-approved brand rule
Use the approved single-piece Atmosynq logo from now on:
- luminous cyan/blue/violet vortex
- **Atmosynq name is inside the logo**
- no separate wordmark outside the logo
- designed to read cleanly on phones

Old split `atmosynq_mark.webp` + `atmosynq_wordmark.webp` assets are removed.

## Current branch
`feat/integrated-logo-weather-first-ui`

## Version
- versionCode: 4
- versionName: 0.2.1

## Implemented on this branch
- new integrated `atmosynq_logo.webp`
- legacy launcher icon uses the integrated logo
- Android adaptive launcher icon uses a safe-zone foreground wrapper
- round adaptive icon uses the same integrated logo
- splash screen uses the integrated logo
- widget badge and widget picker use the integrated logo
- app header uses one compact integrated logo only
- removed the old separate wordmark beneath the icon
- removed the oversized animated logo treatment
- redesigned Animated / Static as a compact segmented control
- redesigned the dashboard around a large live weather hero
- hero uses the same Atmosynq weather scene renderer as the widget
- Animated hero cycles weather frames
- Static hero freezes on a representative weather frame
- clear/day/night/cloud/rain/snow/fog/thunder visuals remain weather-driven
- compact premium action buttons replace the old giant gray default buttons
- tighter spacing and darker cyan/navy visual system
- hourly and 7-day forecast stay below the primary actions

## Motion behavior
Animated:
- app weather hero cycles scene frames with short fades
- widget keeps its supported ViewFlipper weather animation
- live wallpaper keeps its full motion path

Static:
- app weather hero freezes
- widget remains still
- live wallpaper freezes motion while retaining the correct weather styling

The integrated logo itself is intentionally not bobbing/pulsing anymore. Motion belongs to the weather experience, not the brand mark.

## Validation gate
Open a PR from `feat/integrated-logo-weather-first-ui` to `main`.

Before merge, exact-head CI must pass:
- Android resource linking
- Kotlin compilation
- all unit tests
- debug APK assembly
- artifact upload

Then verify post-merge `main` CI and install that exact APK on the Android test device.

## Real-device checks after CI
1. integrated logo is readable and not cropped on Smart Launcher / Samsung masks
2. app header is compact and does not dominate the screen
3. no separate Atmosynq text/wordmark appears below the logo
4. Animated / Static looks like a segmented control, not a default Android button
5. current-weather hero is visually dominant
6. hero scene matches real weather
7. Animated hero visibly moves without looking like a slideshow
8. Static hero stays still
9. actions look consistent and premium
10. hourly + 7-day sections remain readable
11. widget uses the integrated logo
12. splash uses the integrated logo cleanly
13. wallpaper/widget behavior from prior merged work still passes regression checks

## Existing boundary
The public repo still does not ship third-party/copyrighted scene video. The live wallpaper uses the procedural fallback unless an original/licensed private scene is provided.

## Backlog after this gate
- Xiaomi/rear-display Weather Portal work
- Apple SwiftUI / WidgetKit / ActivityKit target
- richer original/licensed scene library

## Next step
Run exact-head CI for this redesign. Fix real build failures only. Merge when green, verify `main`, then provide the exact v0.2.1 APK for device validation.
