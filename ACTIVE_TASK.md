# Active Task

## Active outcome
Ship the Atmosynq Android dashboard as a distinctive premium weather product before cutoff, with believable weather motion, cinematic location-aware scenery, and a layout that does not look like a generic Android weather app.

## Scope lock
This remains the single active task. Do not switch to iOS, Xiaomi rear-display work, unrelated widget redesign, live-wallpaper renderer migration, or another project until the Android dashboard clears the Samsung visual gate.

## Branch / PR
- branch: `feat/v0.4.1-interactive-weather-portal`
- draft PR: #12
- production baseline at task start: `c713b52f62850ffa65441ba09f4377097115c797`
- current target: 0.7.0 / versionCode 15
- Filament: 1.77.1

## Device evidence leading to v0.7.0
The exact green v0.6.0 Samsung build proved the cinematic hybrid backdrop is a major improvement over the toy procedural settlement, but the product shell still failed the premium-layout bar.

Observed issues:
- dashboard still read as stacked generic rounded cards
- top brand + controls consumed too much visual attention without feeling distinctive
- Animated / Static and sync were separate generic blocks
- hero metrics were still one large generic glass rectangle
- secondary actions looked like ordinary Android buttons
- hourly forecast looked like standard cards
- daily forecast looked like a plain list
- the rain / wind visualization could read as sideways precipitation

The next build therefore treats the entire dashboard layout as a product system, not a series of component tweaks.

## v0.7.0 visual system: Atmosphere Deck

### Orbit Header
The top of the app is rebuilt into a compact asymmetric header:
- horizontal Atmosynq wordmark on the left
- micro-brand line: `WEATHER THAT COMES ALIVE`
- compact settings control on the right
- cyan-to-violet spectrum divider

The old centered-logo / centered-tagline stack is removed.

### Atmosphere Control Deck
Animated / Static and sync status now live inside one shared glass surface.

Motion modes are renamed:
- `LIVE`
- `STILL`

Sync language is compact:
- `OFFLINE`
- `SYNCING…`
- `SEARCHING…`
- `LOCATING…`
- `● LIVE • <time> ↻`

This removes a full row of generic UI and makes the mode/status relationship feel intentional.

### Live Sky Hero
The hero becomes the visual identity of the app:
- 30 dp cinematic shell
- stronger top/bottom readability scrim
- vertical cyan → indigo → violet signature spectrum rail
- `NOW // LIVE SKY` eyebrow
- location line
- larger 84 sp temperature
- stronger condition hierarchy
- scene control remains available in the top-right

The cinematic / Filament hybrid scene pipeline remains underneath this layout.

### Metric Clusters
The previous one-piece metrics panel is removed.

The hero now ends in a modular two-cluster dock:
- thermal + humidity
- wind + visibility

A separate lower strip carries:
- precipitation
- sunrise / sunset

This gives the hero layered depth instead of one generic translucent rectangle.

### Atmosphere Dock
Wallpaper, location and widget actions are grouped into one product surface:
- micro header `ATMOSPHERE DOCK / MAKE IT YOURS`
- large primary Live Wallpaper action
- compact Location and Widget secondary controls
- press-scale interaction feedback

### Forecast Stream
Forecast sections now use a distinct "stream" language:
- cyan/violet vertical spectrum rail in section headers
- `FORECAST STREAM` eyebrow
- timeline action on hourly forecast
- full forecast action on daily forecast

Hourly cards now include:
- stronger active `NOW` state
- uppercase time
- weather icon
- temperature
- `RAIN xx%` label
- cyan→violet precipitation probability rail

Daily rows now include:
- one-line day
- icon
- one-line condition
- high / low
- precipitation pill
- precipitation probability rail
- independent rounded row surface rather than generic separators

## Rain / wind physics correction
The old wind visualization used full-width horizontal streaks and rain slant was based on screen width. On-device this could read as rain flying sideways.

v0.7.0 changes:
- wind is represented by a few low-opacity curved upper-sky wisps
- wind no longer draws bright horizontal lines across the whole hero
- rain slant is derived from drop length rather than screen width
- wind influence is capped
- rain therefore falls primarily downward even in moderate wind

## Cinematic hybrid renderer retained
For lowland temperate town/local profiles such as the Samsung Shelton test:
- cinematic raster environment is primary
- drag parallax remains
- Animated mode adds slow scene drift
- weather/time calibrates scene color
- live rain/snow/fog/thunder FX remain above it

Filament remains active for uncovered profiles and retains:
- HDR
- PCSS
- TAA + 4x MSAA
- ULTRA AO + bent normals
- SSR / refraction
- native fog
- dynamic resolution
- color grading
- interactive camera infrastructure

## Worldwide location support retained
- global city search
- city + state/province/country search
- ZIP / postal search
- no location permission required for manual search
- optional approximate current location
- terrain relief sampling
- settlement / terrain / latitude classification
- selected-place persistence

## Functional behavior retained
- Open-Meteo weather
- hourly forecast
- 7-day forecast
- LIVE / STILL preference
- drag / pinch / tap
- widget pinning
- wallpaper picker
- live wallpaper renderer
- old saved-location compatibility
- Filament fallback

## Exact-head CI gate
The final 0.7.0 head must pass:
- cinematic backdrop asset test
- production Filament scene generation
- geocoder / scene / weather / wallpaper regression tests
- Kotlin compilation
- Android resource processing
- debug APK assembly
- artifact upload

## Samsung visual gate
Install the exact green 0.7.0 APK and verify:
1. top header feels compact and branded rather than generic
2. LIVE / STILL + sync control reads as one intentional deck
3. hero feels like the product centerpiece
4. metric clusters are readable and no longer look like one generic glass table
5. action dock feels integrated and premium
6. hourly timeline is visually distinct
7. daily rows stay aligned and Tomorrow never wraps
8. precipitation rails reflect percentages
9. rain falls mostly downward
10. wind wisps no longer look like sideways rain
11. location / forecast / widget / wallpaper behavior remains intact
12. background/resume remains stable
13. frame pacing / heat / battery remain acceptable

## Merge rule
PR #12 remains draft. Do not merge and do not call the visual task complete until the exact green 0.7.0 APK passes the Samsung visual gate.

## Next step
Run exact-head CI on v0.7.0. Root-cause any real compiler/test failure from logs. Once green, install that exact APK immediately and judge the complete top-to-bottom layout against the current Samsung screenshot, not against code intent.
