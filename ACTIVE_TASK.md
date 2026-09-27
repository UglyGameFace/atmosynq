# Active Task

## Active task / outcome
Ship Atmosynq v0.3.1 to fix the two real-device failures still visible in v0.3.0: the app logo is blank and the weather experience still does not feel alive.

## Real-device evidence
v0.3.0 device screenshots showed:
- the top brand area rendered as an empty blue capsule with no visible logo
- launcher/recents branding could still fall back because the bundled logo resource was malformed
- synced overcast weather rendered as large gray cloud blobs over flat landscape geometry
- motion existed technically, but the still appearance and movement did not read as a living atmospheric scene
- unsynced state was visually dead because the hero renderer returned without drawing when no WeatherSnapshot existed

## Root cause
The bundled `drawable-nodpi/atmosynq_logo.webp` was a malformed 7.5 KB image. Build/resource linking passed because AAPT can package the file without proving Android can decode it at runtime.

The hero renderer also used simple large oval clouds and returned immediately when weather was null.

## Approved logo source
The exact previously approved integrated phone-safe logo was recovered:
- source file: `neon_atmosynq_vortex_icon.png`
- original: 1254x1254 PNG
- contains the luminous blue/cyan/violet vortex and the word `Atmosynq` inside the logo itself

A valid optimized WebP version is now committed and verified by decoding it directly from the branch.

## Current branch
`fix/v0.3.1-real-logo-live-scene`

## Implemented logo repair
- replaced malformed `atmosynq_logo.webp` with valid approved integrated logo
- replaced fallback launcher raster with the same approved logo
- replaced round launcher raster with the same approved logo
- adaptive icon foreground already references `atmosynq_logo`, so it now receives the valid asset
- changed in-app branding from the empty wide capsule to a real 112dp square integrated logo
- Animated mode gives the logo a subtle float / pulse / glow
- Static mode leaves the logo still
- brand animator stops while the Activity is paused

## Implemented live-scene repair
### Unsynced state
The hero now renders even before location/weather sync:
- deep atmospheric blue background
- moving cyan/purple light ribbons
- drifting light particles
- animated water/ripple layer
- vignette for readable overlay text

### Synced weather
The hero now renders:
- breathing weather-driven sky gradient
- moving atmospheric sun/moon glow
- two branded cyan/purple sky ribbons
- multi-speed parallax cloud layers
- smoother curved landscape silhouettes
- animated water reflections
- wind-driven atmospheric streaks
- continuous rain when active
- drifting snow when active
- moving fog when active
- brief lightning for thunderstorms
- readable vignette

Animated mode redraws continuously through `postInvalidateOnAnimation()`.
Static mode renders the same current scene without continuous redraws.

## Version
- versionCode: 7
- versionName: 0.3.1

## Validation gate
Open a fresh PR to `main`.

Exact-head CI must pass:
- Android resource linking
- Kotlin compilation
- unit tests
- debug APK assembly
- artifact upload

Do not merge until exact-head CI is green.

## Real-device validation after build
1. approved vortex + Atmosynq name visibly render at top
2. launcher and recents use Atmosynq instead of Android fallback
3. logo is not cropped and name remains inside mark
4. unsynced hero is visibly alive
5. synced overcast scene no longer looks like giant gray blob clouds
6. sky ribbons / haze / cloud parallax visibly move in Animated mode
7. water reflections visibly move
8. wind streaks respond to current wind
9. rain/snow/fog/thunder remain weather-driven
10. Static mode stops hero + logo motion
11. no regression in location, forecasts, widget pinning or wallpaper picker
12. no crash or obvious heat/jank regression

## Known boundary
The public repo still does not ship third-party/copyrighted scenic footage. The dashboard scene is a real procedural renderer, not fake photo-real artwork.

## Next step
Run exact-head Android CI for v0.3.1, repair any real failures, merge only when green, verify post-merge main CI, then provide the exact v0.3.1 APK for device testing.
