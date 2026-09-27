# Atmosynq

**Weather that comes alive.**

Atmosynq is a real-time weather app and dynamic live-weather surface engine for Android. It connects the forecast, widgets, and live wallpaper to one shared weather state so your phone can visually react to the world around you instead of showing three unrelated guesses in three different places.

Your Atmosynq surfaces can react to local weather, time of day, sunrise, sunset, cloud cover, precipitation, wind, visibility, snow, fog, and thunderstorms.

## Core Features

- Real-time local weather
- Current temperature and feels-like temperature
- Daily high and low
- Next 12 hours
- 7-day forecast
- Rain and snow forecasts
- Humidity
- Wind speed, direction, and gusts
- Visibility
- Sunrise and sunset
- Dynamic day/night transitions
- Weather-reactive live wallpaper
- Rain, snow, fog, cloud, wind, and lightning visuals
- Cached weather fallback while temporarily offline
- Android weather home-screen widget
- Android 16 home / lock / both live-wallpaper instances
- One-tap widget pinning on compatible launchers
- Official phone-safe Atmosynq integrated logo with the name inside the mark
- **Animated** and **Static** visual modes

## Weather Dashboard

The Android dashboard is weather-first rather than brand-first. A compact integrated Atmosynq logo sits above a live current-weather hero scene, with Animated / Static shown as a segmented control instead of a full-width utility button.

The current hero uses the same rendered weather state as the widget, so the app itself visually reacts to day/night, clouds, rain, snow, fog, and thunderstorms.

The Android app currently shows:

- Current conditions and temperature
- Feels-like temperature
- Daily high and low
- Humidity
- Wind and gusts
- Cloud cover
- Visibility
- Current precipitation
- Sunrise and sunset
- Next 12 hours
- 7-day forecast

Hourly conditions carry their own day/night state so a clear 2 AM forecast does not proudly display a daytime sun.

## Live Weather Engine

Atmosynq maps real weather conditions into the visual scene.

- Rainfall controls rain density
- Snowfall controls snow intensity
- Wind changes precipitation direction
- Cloud cover changes environmental lighting
- Visibility controls fog strength
- Sunrise and sunset gradually change scene lighting
- Thunderstorms enable lightning effects
- Day/night state changes the atmosphere

The weather dashboard, widget, and wallpaper all use the same Atmosynq/Open-Meteo weather path.

## Android Live Surfaces

### Live wallpaper

Atmosynq uses Android `WallpaperService` with an OpenGL ES renderer for continuous effects while the wallpaper is visible.

The renderer supports:

- Day/night relighting
- Rain particles
- Snow particles
- Fog
- Cloud-based dimming
- Sunrise/sunset warmth
- Wind-driven precipitation
- Thunderstorm lightning
- Optional private/licensed animated scene video

### Home-screen weather widget

The Android widget renders a miniature Atmosynq weather scene instead of only swapping generic icons.

Its rendered scene can show:

- Day or night sky
- Sun or moon
- Cloud density
- Visible rain streaks
- Wind-slanted rain
- Snow particles
- Fog/haze
- Thunderstorm clouds and lightning

The widget supports launcher resizing, manual refresh, system refresh scheduling, cached weather fallback, tap-to-open, and one-tap pinning from inside Atmosynq.

In **Animated** mode, the widget cycles three lightweight weather-rendered frames with fades. Rain/snow positions change, clouds shift, fog drifts, clear skies pulse subtly around the sun/moon, and thunderstorms include a lightning frame. This uses Android's supported `RemoteViews`/`ViewFlipper` path rather than pretending a widget can host the full OpenGL wallpaper engine.

In **Static** mode, the widget freezes on a representative weather frame. Thunderstorms still retain visible storm/lightning artwork without repeated motion.

Full continuous high-frame-rate animation remains in the live wallpaper.

### Home and lock wallpaper instances

On Android 16, Atmosynq supports distinct live-wallpaper instances for:

- **Home**
- **Lock**
- **Home + Lock**

Each instance preserves the same real weather while allowing a surface-specific render profile.

Current profiles:

- **Home:** full brightness, approximately 33 FPS target
- **Lock:** 86% brightness, approximately 24 FPS target
- **Both:** 94% brightness, approximately 29 FPS target

Older Android versions retain the normal live-wallpaper engine path.

## Visual Motion Modes

Atmosynq now exposes one app-wide visual preference:

- **Animated** — animated in-app brand treatment, moving/transitioning widget weather art, full live-wallpaper video/particles/weather transitions, and lightning animation.
- **Static** — the same current weather styling without continuous motion; wallpaper video pauses, precipitation particles stay visible but frozen, lightning flashing stops, and the renderer uses a low refresh cadence.

The Android launcher icon itself remains static because normal launchers do not run arbitrary continuous icon animation. Atmosynq now uses one integrated phone-safe logo, with the Atmosynq name inside the mark, across the launcher/adaptive icon, splash, app header, widget badge, and widget picker.

## Wallpaper Display Modes

Planned weather-text display modes:

- **Clean** — animated scene only
- **Minimal** — temperature and current condition
- **Detailed** — temperature, feels-like, high/low, precipitation, and more

Weather information will also be repositionable so it does not cover important parts of a scene.

## Privacy

Atmosynq is designed with location privacy in mind.

- Location is used to retrieve local weather
- Saved weather coordinates are stored in app-private preferences on the device
- Those preferences are excluded from Android cloud backup and device-transfer extraction
- Continuous background location access is not required for the core design
- No weather API key is required for the current Open-Meteo implementation

## Technology

- Android
- Kotlin
- Android `WallpaperService`
- Android App Widgets / `RemoteViews`
- OpenGL ES / custom rendering
- Open-Meteo weather data
- Location-based weather synchronization
- GitHub Actions automated Android builds

## Build Status

The Android baseline, weather dashboard, home widget, and Android 16 per-surface wallpaper work have all passed GitHub Actions unit tests and debug APK assembly.

Current merged Android main includes:

- PR #1 — weather dashboard + live wallpaper baseline
- PR #2 — rendered Android weather widget
- PR #3 — Android 16 home / lock / both live-wallpaper instances

The next Android gate is real-device validation of launcher widget rendering, wallpaper placement, GPU behavior, and battery use.

## Apple / iOS Direction

Atmosynq is intended to expand to iPhone and iPad with platform-native surfaces rather than pretending iOS exposes Android's live-wallpaper engine.

Planned Apple surfaces include:

- SwiftUI weather app
- WidgetKit Home Screen widgets
- WidgetKit Lock Screen widgets
- ActivityKit Live Activities
- Dynamic Island weather-event views where appropriate
- Weather-reactive generated wallpaper exports
- Live Photo exports where supported

Continuous third-party live-wallpaper rendering is an Android capability; Apple surfaces will use WidgetKit, Live Activities, and generated wallpaper media within Apple's platform limits.

## Scene Assets

The public repository does not bundle copyrighted or third-party wallpaper footage. The renderer has a built-in procedural fallback so the app and CI remain functional without a video asset.

For local/private development, place an original or properly licensed vertical loop at:

`app/src/main/res/raw/scene_neutral.mp4`

`tools/derain_source.sh` can prepare a supplied loop before it is copied into that location. The path is ignored by Git so private scene media is not accidentally published.

Atmosynq's rendering engine is designed to support original, licensed, and user-provided visual scenes.

Copyrighted characters, artwork, video, or other third-party assets are not intended to be distributed commercially without the appropriate rights.

## License

License information will be added before the first public release.

---

### Atmosynq

**Your world, synced to the sky.**
