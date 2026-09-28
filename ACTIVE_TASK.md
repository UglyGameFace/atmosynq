# Active Task

## Active task / outcome
Ship Atmosynq Android v0.8.1 as one coherent modern weather experience: remove legacy visual/UI code that can bleed through the new renderer, keep the live scene interactive, and make every dashboard entry point feel like the same premium product.

## Scope lock
This remains the single active task.

Do not switch to iOS, Xiaomi rear-display work, live-wallpaper renderer migration, unrelated widget redesign, or a custom Vulkan rewrite until the Samsung v0.8.1 gate is clean.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- production baseline at task start: `c713b52f62850ffa65441ba09f4377097115c797`
- pre-Worlds v0.7 head: `47f98f55d29973faa3244683b4f82cd2eebc408f`
- v0.8 interaction head: `eb6622450dd2db7b3ecbe8b23081217ff9da3971`
- current target: 0.8.1 / versionCode 17
- Filament: 1.77.1

## Root cause / findings

### Legacy geometric renderer conflict
`FilamentWeatherHeroView` still instantiated the old `WeatherHeroView` Canvas renderer underneath the modern scene and explicitly showed it while Filament was initializing or if Filament failed.

That obsolete renderer drew:
- path-based hills / landscape silhouettes
- oval clouds
- neon sky ribbons
- flat water bands
- circles / lines / primitive precipitation effects

That is the source of the 2D geometric scenery still appearing behind the new experience.

### Multiple simultaneous visual layers
The cinematic path hid Filament primarily through alpha while leaving the TextureView visible. That left unnecessary renderer composition under the cinematic layer.

v0.8.1 makes the scene modes mutually exclusive by Android visibility as well as alpha.

### Legacy Android UI
Forecast detail, scene controls, settings, location selection, location search, privacy information and location results still used stock `AlertDialog` surfaces.

The white 7-day dialog seen on the Samsung was therefore not a theme bug. It was real legacy UI still in the execution path.

### Generated 3D scene status
`tools/build_atmos_scene.py` is separate from the removed Canvas fallback. It still constructs fallback 3D archetypes from generated meshes/primitives for profiles not covered by the cinematic asset path.

This is not allowed to bleed under cinematic mode anymore, but it remains a future visual-asset replacement target for mountain / metro / uncovered profiles. Do not blindly remove it before production scene assets replace those coverage paths.

## v0.8.1 implemented changes

### Retired old 2D weather scene
- deleted `app/src/main/java/com/uglygameface/atmosynq/render/WeatherHeroView.kt`
- removed every fallback call from `FilamentWeatherHeroView`
- replaced the old scene fallback with a neutral atmospheric loading surface
- graphics-failure / loading states can no longer resurrect old Canvas geometry

### Mutually exclusive render modes
`FilamentWeatherHeroView.updateSceneMode()` now enforces:
- cinematic mode: cinematic visible, Filament invisible, loading invisible
- Filament mode: Filament visible, cinematic invisible, loading invisible
- loading/failure mode: neutral loading visible, both scene layers invisible

### Premium forecast experience
Added `ForecastActivity`:
- full-screen Atmosynq design
- live weather scene hero
- selected location
- current temperature and condition
- NEXT 24 HOURS / 7-DAY OUTLOOK mode switch
- custom forecast cards
- precipitation rails
- wind / sun information
- animated content-mode transition
- no stock white Android dialog

Dashboard forecast CTAs now route into this screen.

### Branded Atmosynq sheets
Added `ui/AtmosynqSheet.kt`.

Replaced stock MainActivity dialogs for:
- Atmosynq World menu
- scene controls help
- quick settings
- location/privacy
- location chooser
- city / ZIP / postal search
- location result picker

The search sheet uses a branded dark input surface and the result picker is scrollable.

### Forecast payload contract
`WeatherReport` now has JSON serialization/deserialization so the forecast Activity receives the same report data rather than refetching and drifting from the dashboard state.

Added `WeatherReportJsonTest` for round-trip coverage.

### Version
- versionName: 0.8.1
- versionCode: 17

## Validation

### Green v0.8 baseline
Final v0.8 head:
`eb6622450dd2db7b3ecbe8b23081217ff9da3971`
- Actions #150 passed

### Green v0.8.1 code head
`8e9caaaca9aec29aa50225fc6a7580291f97a1ef`
- Actions #159 passed
- production Filament scene generation passed
- complete unit-test suite passed
- WeatherReport JSON regression passed
- Android compilation/resource processing passed
- debug APK assembly passed
- artifact upload passed

## Samsung v0.8.1 device gate
Do not call the visual task complete until the exact green v0.8.1 APK is exercised on the Samsung.

Verify:
1. old 2D geometric Canvas scenery never appears at startup
2. old 2D scenery never flashes during weather refresh
3. old 2D scenery never appears after background/resume
4. cinematic path does not visibly mix with Filament underneath
5. FULL FORECAST opens the full-screen Atmosynq forecast experience
6. TIMELINE opens the same experience in hourly mode
7. hourly / daily mode switching works
8. no white stock Android forecast dialog remains
9. Settings uses the branded Atmosynq sheet
10. Location chooser uses the branded sheet
11. location text search accepts city / ZIP / postal queries
12. location results remain selectable and scrollable
13. scene menu uses the branded sheet
14. hero -> Atmosynq Worlds still works
15. Eye Spy still works and persists completion/streak
16. LIVE / STILL, refresh, widget and wallpaper controls remain functional
17. no black/blank hero if Filament is delayed
18. frame pacing, heat and battery remain acceptable

## Important boundary
The 2D Canvas renderer is removed.

The generated 3D fallback scene is still intentionally present for location profiles without production cinematic assets. Its primitive buildings/trees/clouds are not considered final visual quality. Replacing those archetypes with production asset-backed Worlds is the next visual-content phase after this gate.

## Backlog after current gate
- production cabin interior + fireplace / marshmallow interaction
- production metro/city scene assets replacing primitive generated buildings
- production mountain scene assets replacing generated fallback terrain
- coast / forest scene packs
- storm photography interaction
- stone skipping
- wind / leaf interaction
- night-only / seasonal discoveries
- shareable Atmosynq Moments

## Merge rule
PR #12 remains draft.

Do not merge until:
- final documentation head CI is green
- the exact v0.8.1 APK passes the Samsung visual/interaction gate

## Next step
Run CI on the documentation-only head, then install that exact v0.8.1 artifact on the Samsung and validate against the gate above.
