# Active Task

## Active task / outcome
Finish the Android Atmosynq dashboard visual overhaul: push Filament hard enough to read as a believable living weather world, match the approved dashboard mockup more closely, and keep worldwide city / postal-code support intact.

## Scope lock
This is still one continuous task. Do not switch to iOS, Xiaomi rear-display work, unrelated widget redesign, live-wallpaper renderer migration, or another project until this dashboard clears the Samsung visual gate.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- base production main at task start: `c713b52f62850ffa65441ba09f4377097115c797`

## Device-tested checkpoints

### v0.4.2
Worldwide location selection and terrain classification worked, but the 6 KB prototype GLB still looked like basic geometry.

### v0.5.0
Exact green head: `6270a2418c4d8107984d2bc06b79b64ae7db2378`
CI run #113: success.

Samsung screenshots proved the production scene generator was actually being used and the dashboard hierarchy improved, but the visual gate still failed:
- cloud clusters looked like giant white marshmallow geometry
- bloom / emissive lighting created radioactive white hotspots
- cyan / purple / green accents overwhelmed the weather scene
- the unsynced state showed an ugly vertical color / transparency artifact
- houses still read too much like repeated boxes
- the no-location state rendered far too much environment detail
- hero location text could wash out against the scene
- top UI still consumed more space than the approved mockup

The result was an improvement, not a finished product.

## Current target
- versionCode: 13
- versionName: 0.5.2
- Filament: 1.77.1

## v0.5.2 Samsung findings and root-cause fixes

The exact green v0.5.1 Samsung screenshots still failed the visual gate. New device evidence showed:
- bright lamp heads still read as floating lights
- red / green / white glow was still overpowering the actual weather scene
- the top header used a square icon plus plain Android text instead of the approved Atmosynq horizontal wordmark
- the house layer still dominated too much of the hero
- the unsynced state still showed road / water geometry despite having no selected place
- cloud geometry still read as synthetic blobs instead of the mockup's broad storm deck
- the overall color treatment still looked like an effects demo rather than the approved cinematic weather dashboard

These are treated as failed device validation, not subjective polish.

### v0.5.2 direct corrections
- extracted the approved horizontal Atmosynq logo from the existing brand system and added it as a real app drawable
- removed the duplicate plain-text Atmosynq header treatment
- hid the entire street-pole / lamp-head layer in runtime scene composition
- removed emissive energy from generated window / lamp material
- disabled bloom completely until scene lighting is trustworthy
- replaced cyan / purple / green aurora wash with neutral blue-gray atmosphere
- added a soft radial cloud-deck overlay so overcast reads as a broad sky mass rather than floating 3D marshmallows
- pulled the camera back and increased focal length so the environment reads as a landscape instead of toy houses in the user's face
- reduced town / local house scale substantially
- no-location state now hides ground, wet road, water, road marks, terrain, settlement, vegetation, streetlights and clouds
- unresolved state therefore shows only a subdued atmospheric sky until the user actually chooses a place

## v0.5.1 root-cause fixes

### PBR materials
The generated GLB now avoids large alpha-blended environment surfaces that were producing unstable / fake-looking composition:
- building glass is opaque dark reflective PBR instead of a large alpha-blended slab
- water is opaque glossy PBR instead of alpha-blended
- cloud material is opaque muted gray rather than bright translucent white
- emissive window energy is heavily reduced
- sun emissive energy is reduced
- metal palette is darkened

### Environment geometry
- cloud clusters are much smaller, flatter and farther from the camera
- clouds use more but smaller lobes so they read like distant layers rather than giant blobs
- houses now vary width, depth, story count and roof pitch
- houses gain attached garage / porch masses
- houses gain doors
- houses gain optional chimneys
- facade material varies between siding / brick / stucco
- house windows use dark reflective glass instead of always-on bright emissive material

### Filament calibration
Filament still uses the high-end mobile stack, but effects are now calibrated for realism instead of simply being enabled at maximum visual strength:
- HDR
- PCSS shadows
- TAA
- 4x MSAA
- ULTRA AO + bent normals
- SSR
- refraction
- native weather fog
- dynamic resolution
- ULTRA color-grading LUT
- luminance scaling
- gamut mapping
- restrained saturation / vibrance
- cooler white balance
- darker exposure
- restrained bloom
- lens flare / starburst / chromatic aberration disabled after device evidence showed they were contaminating the image

"Push Filament" means use the engine's serious capabilities intelligently, not turn every post effect knob until the scene glows like a broken arcade cabinet.

### Weather / decorative scene state
- 3D cyan / purple ribbons remain in the environment kit but are hidden from normal rendering
- Canvas atmospheric ribbons are reduced to near-invisible during daytime
- reflective weather streaks are much subtler
- clouds scale with cloud cover and move more slowly
- no-location / unsynced state hides clouds and place-specific 3D groups
- sun is hidden when heavily overcast and when no weather is loaded
- no-location state no longer pretends it knows what the user's environment looks like

### UI / UX calibration
- horizontal Atmosynq brand header is smaller
- top header consumes less vertical space
- tagline is smaller
- Animated / Static selector is slightly tighter
- hero location is now a dark glass pill with ellipsis support
- hero temperature / condition / high-low text gains controlled text shadow
- hero top/bottom scrim is stronger where data is rendered
- hero card is slightly shorter so more useful content fits above the fold
- settings, sync refresh, location, widget and wallpaper actions remain directly available

## Production scene generator
CI still generates the GLB from:
- `tools/build_atmos_scene.py`
- `tools/scene-requirements.txt`

The generator must still emit a GLB larger than 500 KB and contain required production nodes/materials. The old 6 KB prototype cannot silently return.

## Global place behavior retained
- city search worldwide
- city + state/province/country
- postal / ZIP search
- manual search without location permission
- optional approximate current location
- place label persistence
- terrain relief sampling
- settlement / terrain / latitude scene classification
- flat places suppress mountains
- metro / city / town / local choose different scene groups

## Existing product behavior retained
- Open-Meteo weather
- hourly forecast
- 7-day forecast
- Animated / Static preference
- drag / pinch / tap scene interaction
- widget pinning
- wallpaper picker
- current live wallpaper renderer
- old saved-location compatibility
- Filament fallback

## Previous exact-head CI status
Exact v0.5.1 head: `5451de9f91d30633b143984b3257f1f9198fec53`

Android CI run #121 passed:
- production scene generation: success
- unit/regression tests: success
- Kotlin compilation: success
- Android resource processing: success
- debug APK assembly: success
- artifact upload: success

Generated production GLB in CI: 1,325,312 bytes / 279 geometries.

The final v0.5.1 head passed:
- production scene generation
- global geocoder tests
- location scene / terrain tests
- weather tests
- wallpaper tests
- production GLB size / node / material tests
- Kotlin compilation
- Android resource linking
- debug APK assembly
- artifact upload

## v0.5.2 exact-head CI status
Exact head: `90f8f92cf2ff6f7c9a7fa1285cc620d447658226`

Android CI run #128 passed on that exact head:
- production scene generation: success
- unit/regression tests: success
- Kotlin compilation: success
- Android resource processing: success
- debug APK assembly: success
- artifact upload: success

The exact green APK is now the Samsung device-test candidate.

## Samsung device gate after CI
1. No vertical green / transparency split in the unsynced state.
2. Unsynced hero is subdued and does not show fake location-specific scenery.
3. Synced overcast scene has no giant white marshmallow clouds.
4. White emissive hotspots no longer dominate.
5. Neon cyan / magenta / green accents are atmospheric, not the subject.
6. Shelton / similar town scene has more believable house variation.
7. Flat places still have no mountains.
8. Metro and mountain locations still visibly differ.
9. Location pill is readable over every sky condition.
10. Top UI is fully below system bars and more compact.
11. Weather data hierarchy still matches the approved mockup direction.
12. Rain / snow / fog / thunder / wind and day-night behavior remain correct.
13. Drag / pinch / tap and vertical dashboard scrolling remain stable.
14. Animated / Static works.
15. Forecasts, location search, widget and wallpaper actions work.
16. Background/resume remains stable.
17. Frame pacing, heat and battery are acceptable.

## Merge rule
PR #12 remains draft. Do not merge and do not call the visual task complete until the exact green v0.5.1 APK passes the Samsung gate.

## Next step
Install the exact green v0.5.2 APK from CI run #128 on the Samsung and compare it directly against the approved storm-dashboard mockup, specifically checking the horizontal brand, elimination of floating lights / colored glow, neutral storm palette, unsynced empty state, softer cloud deck and wider cinematic framing.
