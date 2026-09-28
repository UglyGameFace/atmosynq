# Active Task

## Active task / outcome
Replace Atmosynq Worlds' distorted backdrop / fake coordinate gameplay with real high-resolution, source-aware interactive scene packs while preserving the premium v0.8.1 dashboard/forecast cleanup.

## Scope lock
This is the single active task.

Stay inside Atmosynq Android Worlds / dashboard interaction integration until the exact v0.8.2 APK passes CI and Samsung validation.

Do not switch to iOS, Xiaomi rear-display work, unrelated widgets, live-wallpaper renderer migration, or custom Vulkan during this task.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- v0.8.1 validated head: `fff0165f43138384ae4871f76626d683f910e416`
- current target: 0.8.2 / versionCode 18
- Filament: 1.77.1

## Device evidence / root cause

Samsung screenshot showed Weather Trail as a smeared full-screen image with a Daily Eye Spy game that did not correspond to real visible objects.

Confirmed causes:

### 1. World asset was fundamentally too small
The only scenic asset in the entire repo was:
- `app/src/main/assets/backdrops/scene_temperate_town.webp`
- 512 x 384
- ~13.8 KB
- 4:3

WorldExploreActivity stretched/cropped that tiny dashboard backdrop into a tall phone viewport. The image was never suitable for full-screen exploration.

### 2. Five World names were not five real worlds
CABIN / STORM / CITY / COAST / FOREST were resolver identities, but the cinematic path had only one bundled town image.

Changing the title did not change the place.

### 3. Eye Spy was screen-coordinate guessing
The old game used fixed `xFraction/yFraction` points plus a 58dp hit radius on a touch-stealing overlay.

It did not know what a cabin, tree, cloud, shoreline or fireplace was and could drift away from the visible content as the scene moved/scaled.

### 4. Cinematic pinch did not meaningfully zoom the image
Camera distance changed, but the photographic layer did not share a proper source-space interaction model.

## v0.8.2 implemented architecture

### Semantic scene coordinates
`WorldExperience.kt` now defines:
- `NormalizedRegion`
- region center / contains logic
- `WorldHotspotAction` (FOCUS / ENTER / OBSERVE)
- `WorldSceneLayer`
- scene-specific asset path
- source dimensions
- semantic hotspots

Eye Spy and ordinary exploration share the same scene-space regions.

### Source-aware cinematic renderer
`CinematicBackdropView` now:
- supports exploration mode
- maps view taps back into source-image UV coordinates
- supports focus/zoom on semantic source regions
- animates focus transitions
- applies pinch zoom to the cinematic image
- safely switches scene assets
- resets transform state when a scene changes

### Renderer interaction bridge
`FilamentWeatherHeroView` now:
- exposes explicit cinematic scene asset switching
- exposes semantic scene-tap callback
- maps cinematic taps to source coordinates
- shares pinch/parallax with the cinematic backdrop
- exposes focus/reset helpers

### Rebuilt WorldExploreActivity
Removed the old EyeSpyOverlayView coordinate-circle game.

The World screen now:
- sends drag / pinch / tap directly through the scene renderer
- uses semantic scene regions
- focuses the visible region that was actually tapped
- provides haptic correct/miss feedback
- reuses the same regions for Daily Eye Spy
- uses source-aspect-aware framing rather than forcing a 4:3 image across the full phone
- disables Daily Eye Spy on Worlds that do not yet have a dedicated semantic asset pack
- keeps unsupported World modes honest as live 3D rendering rather than fake image gameplay
- uses stable stored view references rather than child-index layout assumptions

## Real high-resolution World assets

Three licensed source scenes were imported, normalized to 1440 x 2160 WebP, validated, committed, and the one-shot importer workflow was removed afterward.

### Forest / Weather Trail
- `app/src/main/assets/worlds/forest_mist_lake.webp`
- 1440 x 2160
- ~800 KB
- dedicated forest/lake semantic hotspots

### Cabin exterior / Fireside Highlands
- `app/src/main/assets/worlds/cabin_night_exterior.webp`
- 1440 x 2160
- ~923 KB
- cabin / roofline / forest-edge semantic hotspots
- cabin region is ENTER, not a decorative target

### Cabin interior / Fireside
- `app/src/main/assets/worlds/cabin_fireplace_interior.webp`
- 1440 x 2160
- ~270 KB
- fireplace / window / hearth semantic hotspots

Asset sources/licensing are recorded in `WORLD_ASSETS.md`.

## Actual enterable Cabin
Cabin is now a two-scene World:
1. Fireside Highlands exterior
2. tap the cabin semantic region
3. renderer swaps to the real fireplace interior
4. interior gets its own title, subtitle and hotspots
5. Back / OUTSIDE returns to the exterior

Eye Spy remains on the exterior discovery set and does not trap the player inside a different target set.

## Honest support boundary
Dedicated semantic photographic Worlds currently ship for:
- FOREST
- CABIN exterior + interior

STORM / CITY / COAST remain live 3D renderer modes until their own production scene packs are added.

Daily Eye Spy is disabled for those unsupported semantic modes instead of pretending arbitrary screen coordinates are gameplay.

## Regression coverage
`WorldExperienceResolverTest` now checks:
- Cabin resolves with its exterior asset
- Cabin has its fireplace interior asset
- Cabin has an ENTER hotspot
- ordinary temperate local profile resolves to the 1440x2160 forest asset
- semantic regions remain normalized and contain their own centers

## Version
- versionName: 0.8.2
- versionCode: 18

## Validation so far
- semantic interaction head `1ba7d4b10442afe326606c6768fb2c95b112e74e`: Android CI #165 passed
- later intermediate runs were cancelled by newer branch pushes under the workflow concurrency rule
- final exact-head CI required after this task-record commit

## Samsung v0.8.2 device gate
Do not call this task complete until the exact final APK is tested on the Samsung.

Verify:
1. Weather Trail uses the high-resolution forest/lake image, not the old town backdrop
2. image is sharp and naturally framed rather than smeared/cropped
3. drag changes the scene without breaking landmark mapping
4. pinch zoom affects the photographic World
5. tapping a visible semantic region focuses that actual region
6. Daily Eye Spy uses the same real regions and no invisible magic circles
7. misses do not incorrectly count as discoveries
8. completion / replay / streak still persist
9. Cabin exterior uses the real cabin asset
10. tapping the cabin enters the fireplace interior
11. interior fireplace/window/hearth hotspots respond
12. Back / OUTSIDE returns to cabin exterior
13. unsupported Storm / City / Coast do not launch fake Eye Spy
14. background/resume preserves a valid renderer state
15. Full Forecast / Timeline premium screens still work
16. Settings / Location branded sheets still work
17. LIVE/STILL, refresh, widget and wallpaper controls still work
18. heat, RAM and frame pacing remain acceptable

## Backlog after this gate
- dedicated Storm asset pack + lightning photography
- dedicated City asset pack + rooftop interaction
- dedicated Coast asset pack + stone skipping
- richer Forest interaction beyond Eye Spy
- production fireplace marshmallow-roast interaction
- original Atmosynq-owned art pipeline to reduce dependence on licensed scene bases
- migrate more effects from 2.5D composition to specialized GPU/Filament effects where visually justified

## Merge rule
PR #12 remains draft.

Do not merge and do not claim completion until:
- final exact-head CI passes
- exact v0.8.2 APK passes Samsung visual/interaction validation
