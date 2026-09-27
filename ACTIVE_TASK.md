# Active Task

## Active task / outcome
Push Atmosynq's Android weather portal to a production-quality Filament presentation and bring the dashboard UI/UX in line with the approved Atmosynq storm mockup, while preserving worldwide city/postal location support.

## Scope lock
This remains one continuous task. The Samsung v0.4.2 device test passed the location-label / postal-code path but failed the visual-quality gate: the hero still read as primitive geometry and the dashboard chrome did not match the approved mockup closely enough.

Do not switch to iOS, Xiaomi rear-display work, unrelated widget redesign, or live-wallpaper renderer migration until this Android dashboard pass clears CI and the new Samsung device gate.

## Production baseline
- main at branch start: `c713b52f62850ffa65441ba09f4377097115c797`
- draft PR: #12
- branch: `feat/v0.4.1-interactive-weather-portal`
- last device-tested build: 0.4.2 / versionCode 10
- v0.4.2 exact head `4ae6faee045267b1f68f9d8d1cc5026f214a0e41`
- Android CI run #101 passed on that head

## Target build
- versionCode: 11
- versionName: 0.5.0
- Filament: 1.77.1

## Real-device finding
The location work is real:
- selected city / postal code appears in the hero
- manual city/postal search works
- flat-location mountain suppression is functioning

The art gate is not.

The screenshot still looked fake because:
- the checked-in `atmos_scene.glb` was only 6,440 bytes
- it consisted of prototype geometry, including low-detail clouds and terrain
- Canvas settlement / vegetation silhouettes were still being drawn over Filament
- the dashboard still used the earlier temporary composition rather than the approved mockup
- Android edge-to-edge behavior let the top branding crowd the status bar on the Samsung

No amount of bloom or SSAO can turn a 6 KB prototype into cinematic environment art.

## New authoritative visual pipeline

`MainActivity`
→ worldwide city / postal / approximate-location selection
→ `LocationStore`
→ `SceneProfileResolver`
→ settlement / terrain / latitude profile
→ `FilamentWeatherHeroView`
→ generated production PBR environment kit
→ high-quality Filament post stack
→ `WeatherFxOverlayView` for optical weather only
→ dashboard glass / data overlays

The Canvas overlay no longer draws fake city skylines / neighborhoods over the 3D environment.

## Production scene generation
The binary scene is now reproducibly generated during Android CI from:
- `tools/build_atmos_scene.py`
- `tools/scene-requirements.txt`

The generator creates an embedded glTF/GLB PBR environment kit with:
- textured ground
- wet asphalt road
- reflective water
- detailed ridged mountain layers
- independent rolling terrain
- multi-part city buildings
- glass facades
- emissive window grids
- multi-part houses and roofs
- pine, broadleaf and palm vegetation kits
- street poles and emissive lights
- smoother clustered cloud geometry
- sun
- Atmosynq cyan / violet atmospheric ribbons
- embedded base-color / normal textures

The generator validates required nodes and refuses to emit a scene below 500 KB so the 6 KB prototype cannot silently return.

## Location-aware 3D composition
Filament now controls which 3D groups appear:
- metro: dense city kit, full street layer
- city: reduced city kit plus neighborhood depth
- town: houses dominate, city geometry nearly removed
- local/rural: city geometry removed, lighter houses / roads
- flat: mountains hidden
- rolling: rolling terrain shown
- highland: partial high-relief terrain
- mountain: full mountain kit
- tropical: palms + broadleaf
- warm: broadleaf + some palms
- temperate: broadleaf + pine
- cool: pine dominant
- polar: sparse pine

No finite list of handcrafted cities is required; every resolved global location gets a scene recipe from location metadata.

## Filament quality pass
The dashboard renderer now targets the high end of Filament's mobile feature set while retaining dynamic resolution:
- HDR color buffer
- PCSS soft shadows
- temporal dithering
- TAA
- 4x MSAA
- high-quality dynamic resolution
- ULTRA ambient occlusion
- bent normals
- screen-space refraction
- long-range SSR for wet surfaces / water
- guard band
- high-quality bloom
- lens flare / starburst
- subtle chromatic aberration
- vignette
- weather-driven native Filament fog
- physically exposed camera

Dynamic resolution remains enabled because "maximum quality" does not mean deliberately missing frames and cooking the phone.

## Approved mockup UI/UX pass
The Android dashboard is being aligned to the approved Atmosynq storm dashboard:
- safe system-bar insets so branding is not clipped
- compact horizontal Atmosynq brand chip
- settings control in the top-right
- centered `Weather that comes alive.` line
- taller glass Animated / Static selector
- compact tappable sync / refresh pill
- larger cinematic hero
- location text protected from collision with the hero control
- mockup-style three-dot scene control instead of the developer-looking `LIVE 3D • DRAG • PINCH • TAP` badge
- larger temperature / condition hierarchy
- clearer translucent metrics glass
- larger gradient live-wallpaper CTA
- larger secondary actions
- section headers with `See Details` / `See More`
- bulky privacy footer removed from the main dashboard; privacy explanation remains available through settings/location flow

## Preserved behavior
Must remain intact:
- global city search
- postal / ZIP search without location permission
- approximate current location as an optional path
- selected-place persistence
- weather retrieval
- hourly forecast
- 7-day forecast
- Animated / Static preference
- drag / pinch / tap hero interaction
- widget pinning
- wallpaper picker
- current live wallpaper renderer
- old saved-location compatibility
- Filament fallback behavior

## Regression coverage
Exact final head must pass:
- global geocoder tests
- location scene-profile / terrain tests
- weather tests
- wallpaper tests
- production GLB size test
- production GLB required-node test
- production GLB PBR-material test
- Kotlin compilation
- Android resource linking
- debug APK assembly
- artifact upload

## Real-device gate after exact-head CI
Install the exact green v0.5.0 APK on the Samsung and verify:
1. top branding is fully below the status bar / cutout
2. visual hierarchy resembles the approved mockup
3. city/postal selection still works
4. long location + postal labels do not collide with controls
5. a flat town has no mountain geometry
6. a metro location visibly swaps to the city kit
7. a mountainous location retains high-relief terrain
8. different latitude bands visibly change vegetation
9. PBR textures / windows / wet road / water are visibly richer than the prototype
10. no Canvas skyline/tree silhouettes sit over the 3D kit
11. rain, snow, fog, thunder, wind and day/night remain correct
12. drag, pinch and tap remain stable without breaking vertical scroll
13. Animated / Static still work
14. hourly / daily forecasts render correctly
15. location, widget and wallpaper actions still work
16. background/resume is stable
17. frame pacing, heat and battery are acceptable

## Merge rule
PR #12 stays draft. Do not call this visual task complete and do not merge until the exact green v0.5.0 APK passes the Samsung gate.

## Next step
Let CI generate the production scene and compile the new Filament/UI pass. Root-cause any build/test failures on the exact head. When green, install that exact APK and compare it directly against the approved mockup and the previous v0.4.2 screenshot.
