# Active Task

## Active outcome
Finish the Android Atmosynq dashboard before the cutoff with a believable cinematic weather hero, mockup-aligned UI, and worldwide city / postal-code support.

## Scope lock
This remains the single active task. Do not switch to iOS, Xiaomi rear-display work, unrelated widgets, live-wallpaper renderer migration, or another project until the Android dashboard clears the Samsung visual gate.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- production baseline at task start: `c713b52f62850ffa65441ba09f4377097115c797`
- current target: 0.6.0 / versionCode 14
- Filament: 1.77.1

## Why v0.6.0 exists
Samsung testing made the remaining problem undeniable.

The generated Filament environment pipeline technically worked, but the actual phone still exposed:
- dark synthetic-looking terrain
- bright vertical / floating-looking geometry
- toy-scene depth
- low-believability vegetation and settlement detail
- a hero that still looked like a renderer demo instead of the approved cinematic mockup

The UI below the hero was much closer, and the horizontal Atmosynq brand was materially better, but the main visual still failed.

The root cause is no longer "not enough post-processing." The generated procedural 3D environment itself is not photoreal enough to be the primary visual layer on a phone-sized hero.

## v0.6.0 architecture pivot
Atmosynq now uses a hybrid visual system instead of insisting that weak generated geometry be visible everywhere.

### CinematicBackdropView
New:
`app/src/main/java/com/uglygameface/atmosynq/render/CinematicBackdropView.kt`

For supported lowland town / local / temperate city profiles:
- a real cinematic raster environment is the primary visual layer
- the image is center-cropped with overscan
- drag interaction creates parallax
- Animated mode adds extremely slow camera drift
- weather/time modifies saturation, exposure and cool/warm balance
- a controlled top/bottom data scrim protects the weather text
- a left-side readability gradient matches the approved mockup hierarchy

The first production backdrop is:
`app/src/main/assets/backdrops/scene_temperate_town.webp`

It is a cinematic wooded lowland town / water scene suited to the Shelton / Connecticut device-test profile.

The asset is intentionally small enough for mobile delivery while still being photographic rather than procedural geometry.

### FilamentWeatherHeroView
Filament remains in the product and continues to own the genuinely useful high-end paths:
- 3D / PBR rendering for profiles not yet covered by a cinematic backdrop
- HDR
- PCSS shadows
- TAA
- 4x MSAA
- ULTRA AO + bent normals
- SSR
- refraction
- native fog
- dynamic resolution
- color grading
- interactive camera / pinch / drag infrastructure

For a profile covered by the cinematic layer:
- the cinematic backdrop is shown above the TextureView
- the bad procedural Filament settlement is hidden rather than allowed to contaminate the photo
- WeatherFxOverlayView still supplies live rain, snow, fog, wind, thunder and touch response
- drag / pinch / tap remain active
- Filament is not deleted, because it remains the engine for profiles / features where it adds visual value

This is a quality-first decision. "Push Filament to the max" does not mean forcing visibly fake meshes to remain on screen merely because they are 3D.

## Worldwide location support remains
Location behavior is unchanged:
- search any city
- search city + state/province/country
- search ZIP / postal code
- manual search requires no location permission
- optional approximate current location
- resolved place name shown in the hero
- local terrain relief sampling
- settlement / terrain / latitude classification
- saved place persistence

The global scene classifier is still the routing layer. Cinematic backdrop coverage can expand by scene archetype without creating a city whitelist.

## Existing mockup/UI corrections retained
- safe system-bar spacing
- approved horizontal Atmosynq brand treatment
- settings control
- centered tagline
- glass Animated / Static selector
- tappable sync pill
- large cinematic hero
- readable location text
- larger temperature / condition hierarchy
- translucent metrics panel
- gradient live-wallpaper CTA
- location / widget actions
- forecast section headers
- privacy explanation moved out of the main dashboard

## New forecast cleanup
The 7-day rows now reserve a fixed single-line day column so `Tomorrow` cannot split into `Tomorro / w`.
Condition text is a single ellipsized line so the row stays aligned.

## Regression protection
New test:
`CinematicBackdropAssetTest.kt`

CI now rejects a missing or suspiciously tiny cinematic backdrop and verifies the RIFF / WEBP signature.

Existing tests continue to cover:
- production GLB integrity / size / material nodes
- global geocoding
- postal-code metadata
- location scene classification
- terrain relief
- weather
- wallpaper behavior

## Preserved behavior
Must remain intact:
- Open-Meteo weather
- hourly forecast
- 7-day forecast
- Animated / Static
- drag / pinch / tap
- global city / postal search
- approximate current location
- widget pinning
- wallpaper picker
- live wallpaper renderer
- old saved-location compatibility
- Filament fallback

## Exact-head CI gate
The final 0.6.0 head must pass:
- cinematic backdrop asset test
- production Filament scene generation
- unit / regression tests
- Kotlin compilation
- Android resource processing
- debug APK assembly
- artifact upload

## Samsung visual gate
Install the exact green 0.6.0 APK and verify:
1. Shelton no longer shows the dark procedural toy settlement.
2. The hero reads as a cinematic wooded New England / lowland scene.
3. No floating lights / vertical white geometry.
4. No giant mesh clouds.
5. No radioactive red / green / magenta bloom.
6. Night remains visible rather than becoming a black card.
7. Weather FX layer correctly adds live rain / snow / fog / thunder.
8. Location text and weather hierarchy remain readable.
9. Drag creates believable parallax and does not break vertical scrolling.
10. Static mode stops idle scene motion.
11. `Tomorrow` stays on one line.
12. Forecast / location / widget / wallpaper controls still work.
13. Background/resume is stable.
14. Frame pacing / heat / battery remain acceptable.

## Merge rule
PR #12 remains draft. Do not merge and do not call the visual task complete until the exact green 0.6.0 APK passes the Samsung visual gate.

## Next step
Run exact-head CI on the 0.6.0 hybrid renderer. If green, install that exact APK immediately and compare it against the latest failed Samsung screenshot and the approved Atmosynq mockup.
