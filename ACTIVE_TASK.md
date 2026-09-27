# Active Task

## Active task / outcome
Make Atmosynq's Android weather hero location-aware worldwide: users can choose any city or postal code without granting device location, the dashboard shows the selected place, and the visual scene stops showing mountains unless local terrain supports them.

## Scope lock
This remains the same Atmosynq dashboard visual/location task. Do not switch to iOS, Xiaomi rear-display support, widget redesign, wallpaper-engine migration, or unrelated work until the current real-device gate passes.

## Production baseline
- main SHA at branch start: `c713b52f62850ffa65441ba09f4377097115c797`
- merged PR #11: v0.4.0 Filament dashboard hero
- production version before this branch: 0.4.0 / versionCode 8
- current draft PR: #12

## Current branch
`feat/v0.4.1-interactive-weather-portal`

## Target version
- versionCode: 10
- versionName: 0.4.2

## Root cause confirmed
The Samsung screenshots proved the renderer itself works, but the visible environment was wrong:
- the bundled Filament GLB is a tiny prototype with three mountain meshes, so every location visually inherited mountains
- weather effects were location-independent
- the dashboard displayed generic "Local weather" rather than a resolved place
- the only obvious location path was device location, which is a poor privacy default for users who prefer city/postal entry

This is not fixed by more bloom, AO, or rain. The scene needs a worldwide location context.

## Authoritative execution path
`MainActivity`
→ user chooses worldwide city/postal search or approximate device location
→ `GeocodingClient` resolves city/postal queries globally
→ `TerrainContextClient` samples surrounding global elevation
→ `LocationStore` persists selected place + terrain metadata
→ `SceneProfileResolver` classifies settlement / terrain / latitude band
→ `FilamentWeatherHeroView` scales prototype mountain geometry according to real terrain
→ `WeatherFxOverlayView` renders settlement/vegetation/weather-aware foreground FX
→ `WeatherHeroView` follows the same terrain profile if Filament falls back
→ Open-Meteo weather continues to drive conditions

## Worldwide location support
There is no hard-coded city whitelist.

Users can:
- search a city
- search city + region/state/province/country
- search a postal/ZIP code
- use approximate device location as an explicit alternative

Search results retain:
- latitude / longitude
- locality
- region
- country / country code
- postal match when available
- population
- feature code
- elevation

Device location remains optional. City/postal search does not request GPS permission.

## Location-aware scene profile
Scene selection is data-driven for every resolved location:
- settlement: metro / city / town / local
- terrain: flat / rolling / highland / mountain
- latitude band: tropical / warm / temperate / cool / polar

Terrain classification now uses local relief sampled around the chosen coordinates, not altitude alone.

Examples of intended behavior:
- a flat dense city gets an urban silhouette and no mountain peaks
- a flat town gets neighborhood/local foreground treatment
- a tropical place gets warmer/tropical vegetation treatment
- a high-relief location can retain mountain terrain
- current rain/snow/fog/wind/thunder still layer on top

## Current implementation
- worldwide city/postal search UI added
- search works without location permission
- approximate device-location option preserved
- resolved place is displayed in the hero
- selected place + scene metadata persist locally
- global terrain sampling added
- Filament mountain visibility/scale follows the location profile
- atmospheric overlay follows settlement and latitude profile
- Canvas fallback follows the same terrain profile
- interactive drag / pinch / tap weather portal remains intact
- existing weather, hourly forecast, daily forecast, widget pinning, and wallpaper picker remain in scope for regression checks
- duplicate geocoding/scene implementations discovered during this task were consolidated

## Validation required
Exact final PR head must pass:
- all JVM/unit tests
- geocoder parsing tests
- global scene-profile / terrain-relief regression tests
- GLB integrity regression test
- Kotlin compilation
- Android resource linking
- debug APK assembly
- APK artifact upload

## Real-device gate
Before merge, install the exact green APK and verify:
1. city/postal search works without granting location permission
2. multiple countries/cities resolve and can be selected
3. selected city/region appears in the hero
4. a flat place does not show mountain geometry
5. a genuinely mountainous place does show meaningful terrain
6. metro/city/town foreground treatment changes with selected place
7. weather FX still match current conditions
8. drag, pinch, tap and vertical page scrolling remain usable
9. Animated / Static behavior remains correct
10. location, forecasts, widget and wallpaper picker still work
11. app survives background/resume
12. performance, heat and battery are acceptable

## Important visual limitation
This pass makes Atmosynq globally location-aware; it does not claim to recreate every city's exact buildings or landmarks. The scene is generated from global place/terrain metadata so it scales worldwide instead of requiring a handcrafted asset for every municipality. Exact landmark-level city reconstruction is a separate asset/map-data problem and must not be falsely implied.

## Cleanup / conflict inspection
Before merge:
- confirm only one global geocoder implementation remains
- confirm only one scene-profile implementation remains
- scan affected files for stale class names / duplicate paths
- remove debug or temporary code
- inspect final diff for unrelated/generated/secret-bearing changes

## Next step
Get exact-head CI green, inspect the final diff, then test the resulting v0.4.2 APK on the Samsung against both a flat city and a mountainous city. Do not merge until the device gate passes.
