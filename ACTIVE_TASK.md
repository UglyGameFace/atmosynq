# Active Task

## Active task / outcome
Repair the two real-device regressions found in Atmosynq v0.2.1: the dashboard logo is invisible and the app weather hero looks like a skipping slideshow.

## Root cause from real-device test
1. The dashboard reserves space for the integrated logo resource, but that resource is not visibly rendering on-device in the header.
2. The app hero is intentionally swapping three pre-rendered weather bitmaps every 1.35 seconds, so Animated mode literally behaves like a slideshow.

## Repair branch
`feat/integrated-logo-weather-first-ui`

## Version
- versionCode: 5
- versionName: 0.2.2

## Fixes now implemented
- replaced the invisible header resource path with a phone-safe composed Atmosynq tile using the launcher artwork known to render on-device
- keeps the **Atmosynq name inside the logo tile**, not as a detached outside wordmark
- removed the app hero's three-bitmap slideshow loop entirely
- added `WeatherHeroView`, a real continuously drawn Canvas weather surface
- Animated mode now moves clouds continuously, moves rain continuously, drifts snow/fog, softly pulses sun/moon glow, and flashes lightning on a continuous timeline
- Static mode uses the same weather scene but does not schedule continuous redraws
- dashboard hero now receives the live `WeatherSnapshot` directly instead of generating three bitmap frames
- widget animation remains separate because Android widgets are constrained to RemoteViews-compatible motion

## Brand rule
Use one integrated phone-safe Atmosynq logo treatment:
- luminous vortex
- Atmosynq name inside the tile
- no detached wordmark under or beside it

## Validation gate
Open a new repair PR to `main`.

Before merge, exact-head CI must pass:
- Android resource linking
- Kotlin compilation
- all unit tests
- debug APK assembly
- artifact upload

Then verify post-merge `main` CI and install that exact v0.2.2 APK.

## Device validation after CI
1. logo is visibly present at the top
2. Atmosynq name is inside the logo tile
3. logo is compact and phone-safe
4. weather hero no longer jumps between discrete frames
5. clouds move smoothly
6. rain/snow/fog move smoothly when active
7. clear weather has subtle continuous motion
8. thunder lightning is brief and not constant
9. Static mode stops app hero motion
10. widget behavior remains intact
11. wallpaper behavior remains intact
12. no heat/crash regression

## Existing boundary
The public repo still does not ship third-party/copyrighted scene video. The live wallpaper uses the procedural fallback unless an original/licensed private scene is provided.

## Next step
Run exact-head Android CI on the v0.2.2 repair, fix only real failures, merge when green, verify main, then provide the exact APK for device testing.
