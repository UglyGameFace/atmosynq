# Active Task

## Active task / outcome
Ship Atmosynq v0.3.0 as a real weather-first UI reset based on the approved premium dashboard mockup, without faking unsupported surfaces or replacing the app with a screenshot.

## Current state
PR #9 — `v0.3.0: premium weather-first dashboard reset` — is merged into `main`.

Merged functional SHA:
`af23a9c54841849817640ee3b8c562a63a7b742d`

## Baseline
- current production/main before this branch: `c41bb5de705e044b551dcb047e06d623fd80f337`
- v0.2.2 fixed the missing header logo and removed the three-frame slideshow from the Android app hero
- the real-device screenshots then showed the overall layout still looked too boxy/dev-like

## v0.3.0 design contract
The approved mockup is a visual target, not a bitmap to paste into the app.

Real code must implement:
- compact integrated Atmosynq brand chip
- weather-first hierarchy
- premium animated hero scene
- glass-style weather metrics
- live wallpaper as the strongest CTA
- current-location and widget actions as secondary controls
- one unified hourly surface
- one unified 7-day surface
- fewer borders / fewer stacked pills
- Animated and Static modes remain real behavior, not decoration

## Implemented
### Branding
- removed the oversized square header treatment
- added a compact integrated brand chip
- chip uses the regular Atmosynq vortex drawable already proven to render on-device
- Atmosynq name stays inside the same branded element
- tagline reduced so branding no longer dominates the weather

### Weather hero
- expanded the hero into the main visual focus
- current temperature, condition, high/low and precip remain in the hero
- added glass-style metric strip:
  - feels like
  - humidity
  - wind + gusts
  - visibility
  - precipitation
  - sunrise / sunset

### Continuous scene renderer
`WeatherHeroView` now renders a layered scene in code:
- weather-driven sky gradient
- atmospheric sun/moon glow
- moving cloud banks
- distant mountain silhouettes
- foreground ridge
- lake / reflection layer
- moving rain
- drifting snow
- moving fog
- brief thunder lightning
- bottom vignette for text legibility
- Animated mode targets ~30 FPS
- Static mode renders the same scene without continuous redraws

This is intentionally not advertised as photo-real scenery. The public repo does not yet ship a licensed scenic asset pack.

### Action hierarchy
- Set Live Wallpaper is now the primary gradient CTA
- Current Location and Add Home Widget are secondary side-by-side actions
- wallpaper remains disabled until a weather location is available

### Forecast surfaces
- hourly forecast moved into one glass section
- current hour gets a highlighted card
- 7-day forecast moved into one unified panel
- daily rows use dividers instead of seven separate rounded boxes

## Version
- versionCode: 6
- versionName: 0.3.0

## Validation
PR exact head `267fe7db45bc2732f3020ae1fad60726ca4021d6` passed Actions run #48: resource linking, Kotlin compilation, unit tests, debug APK assembly, and artifact upload all passed.

PR #9 then merged to `main` as `af23a9c54841849817640ee3b8c562a63a7b742d`.

Post-merge main Actions run #49 passed the functional build steps and uploaded the exact APK artifact.

## Real-device validation after build
1. compact brand chip visibly shows vortex + Atmosynq
2. no giant empty logo square
3. hero visually dominates the screen
4. hero weather motion is smooth, not frame-flipping
5. scenic depth reads as sky / mountains / water rather than blob clouds
6. Static mode stops hero animation
7. Set Live Wallpaper is visually the primary action
8. location + widget controls fit without clipping
9. hourly section feels like one surface
10. 7-day forecast feels like one surface
11. no regression in location capture, widget pinning, wallpaper picker or forecasts
12. no crash, heat, or obvious UI-thread jank

## Known boundary
The public repo intentionally does not bundle third-party/copyrighted wallpaper video. The live wallpaper still falls back to its procedural renderer unless an original/licensed private `scene_neutral.mp4` is supplied.

## Validated APK
- versionName: 0.3.0
- versionCode: 6
- source main SHA: `af23a9c54841849817640ee3b8c562a63a7b742d`
- artifact: `atmosynq-debug-apk`
- APK size: 1,004,422 bytes
- APK SHA-256: `77369d0249d21e7a5fd81c46d8bcd9a697130a00046d3284652e4ea7c4916f29`

## Next step
Install the exact v0.3.0 APK on the Android device and judge the real UI against the approved mockup: logo visibility, hero depth, action hierarchy, forecast surfaces, Animated/Static motion, clipping, heat, and smoothness. Root-cause observed device issues before starting Apple work.
