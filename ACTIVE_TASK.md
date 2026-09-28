# Active Task

## Active task / outcome
Turn the Atmosynq Android dashboard into an actually interactive weather product: repair decorative/dead controls, make the live scene enterable, and establish Atmosynq Worlds as the retention layer for weather-reactive exploration.

## Scope lock
This is the single active task. Keep work inside the Android dashboard / Atmosynq Worlds interaction path until the exact-head build and Samsung device gate pass.

Do not switch to iOS, Xiaomi rear-display work, live-wallpaper renderer migration, unrelated widget redesign, or a custom Vulkan rewrite during this task.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- production baseline at task start: `c713b52f62850ffa65441ba09f4377097115c797`
- pre-Worlds v0.7 head: `47f98f55d29973faa3244683b4f82cd2eebc408f`
- current target: 0.8.0 / versionCode 16
- Filament: 1.77.1

## Root cause / findings
The v0.7 Atmosphere Deck looked interactive in several places without having complete action paths.

Confirmed defects:
- `TIMELINE ›` was plain decorative TextView content with no click handler.
- `FULL FORECAST ›` was plain decorative TextView content with no click handler.
- `FilamentWeatherHeroView` detected scene taps, but `performClick()` only produced the existing touch pulse; MainActivity attached no scene-navigation action.
- the top-right Live Sky control only explained interaction in a dialog rather than opening an interactive experience.

Existing controls that already had real handlers were preserved:
- LIVE / STILL
- sync / refresh
- settings
- location
- widget
- live wallpaper

## Implemented v0.8 interaction repair

### Real forecast actions
The shared forecast header now requires an action callback.

- `TIMELINE ›` opens a real 12-hour forecast view with time, condition, temperature, rain probability and wind.
- `FULL FORECAST ›` opens a real 7-day detail view with condition, high, low and rain probability.
- unsynced forecast actions request/refresh weather instead of silently doing nothing.

### Enterable live scene
The dashboard weather scene is now a real doorway:
- tapping the hero launches `WorldExploreActivity`
- current weather is transferred into the world as serialized `WeatherSnapshot`
- saved location is resolved into the same `SceneProfile`
- the same `FilamentWeatherHeroView` is reused full-screen
- drag and pinch remain available inside the world
- the top-right scene menu offers Explore, Eye Spy, and actual controls help

### Atmosynq Worlds foundation
`WorldExperienceResolver` maps the current weather + location profile into a world identity:

- CABIN / Fireside Highlands
- STORM / Storm Watch
- CITY / Skyline Drift
- COAST / Weather Pier
- FOREST / Weather Trail

World selection reacts to:
- snow
- precipitation / thunderstorms
- settlement size
- terrain
- latitude band

This is the stable product contract for later asset-backed scene-specific interactions. It avoids hardcoding feature logic into MainActivity.

### Daily Eye Spy
The first repeatable world activity is live:
- each world owns scene-appropriate hidden targets
- targets use normalized coordinates so the interaction scales across phone sizes
- targets remain invisible; the UI does not point at the answer
- correct / miss feedback appears at the tapped position
- target order rotates by local world date
- Eye Spy can launch directly from the dashboard scene menu

### Daily progress / retention
`WorldProgressStore` persists:
- daily completion per world type
- replay state
- consecutive-day world streak

Multiple world types can be completed on the same date without clobbering each other.

Pure streak policy has regression coverage for:
- consecutive-day increment
- skipped-day reset
- duplicate same-day completion

## Files added
- `app/src/main/java/com/uglygameface/atmosynq/WorldExploreActivity.kt`
- `app/src/main/java/com/uglygameface/atmosynq/worlds/WorldExperience.kt`
- `app/src/main/java/com/uglygameface/atmosynq/worlds/WorldProgressStore.kt`
- `app/src/test/java/com/uglygameface/atmosynq/worlds/WorldExperienceResolverTest.kt`
- `app/src/test/java/com/uglygameface/atmosynq/worlds/WorldStreakPolicyTest.kt`

## Files changed
- `app/src/main/java/com/uglygameface/atmosynq/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## Validation so far
### Green intermediate head
`034b42eea82e3fb2fe8622639d0cb3c95335fcd5`
- Actions run #145 passed
- production Filament scene generation passed
- unit tests passed
- Android compilation/resource processing passed
- debug APK assembly passed
- artifact upload passed

### Green interaction + daily progress head
`9b7ccf39d8e30c19c3aa931a2e67ddefe64ab2bc`
- Actions run #148 passed
- production Filament scene generation passed
- World resolver tests passed
- streak policy tests passed
- full unit-test suite passed
- Android compilation/resource processing passed
- debug APK assembly passed
- artifact upload passed

### Green final code/task-record head
`9858af894993e9bd290e0e26ad03e5c9145b9015`
- Actions run #149 passed
- production Filament scene generation passed
- full unit-test suite passed
- Android compilation/resource processing passed
- debug APK assembly passed
- artifact upload passed

## Samsung device gate
The task is not complete until the exact green v0.8 APK is tested on the Samsung.

Verify:
1. tapping the hero opens Atmosynq Worlds
2. full-screen scene renders rather than black/blank/fallback failure
3. drag and pinch still work inside the world
4. back returns cleanly to dashboard
5. scene menu Explore opens the world
6. scene menu Start Eye Spy opens the world with the challenge active
7. Eye Spy does not reveal target positions
8. correct and miss taps give visible feedback
9. completion persists after leaving/re-entering
10. replay does not increase the same-day streak
11. next-day completion advances the streak
12. `TIMELINE ›` opens real hourly detail
13. `FULL FORECAST ›` opens real daily detail
14. location / widget / wallpaper / motion / refresh controls still work
15. background/resume is stable
16. heat, frame pacing and battery remain acceptable

## Important boundary
The interactive framework is now real, but the production-quality enterable cabin interior / fireplace / marshmallow scene asset is not yet shipped in this slice.

Do not fake that with low-detail procedural boxes. The next visual-content phase should attach production assets and scene-specific interactions to the Worlds contract only after this interaction foundation passes the Samsung gate.

## Backlog after this task clears
- production cabin interior scene with fireplace + marshmallow roast
- storm lightning hunt / photography interaction
- coast stone-skipping interaction
- forest wind / leaf interaction
- city rooftop interaction
- weather-event-only discoveries
- night-only / seasonal secrets
- shareable Atmosynq Moments
- richer scene assets / materials before considering a custom Vulkan renderer

## Merge rule
PR #12 remains draft. Do not merge and do not call the task complete until final exact-head CI is green and the v0.8 Samsung interaction gate is exercised.

## Next step
Verify the final documentation-only head in CI, then install the exact green v0.8 APK on the Samsung and run the interaction/device gate. Keep PR #12 draft until that device evidence is clean.
