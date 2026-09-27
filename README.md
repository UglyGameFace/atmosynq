# Atmosynq

**Weather that comes alive.**

Atmosynq is a real-time weather app and dynamic live-wallpaper engine for Android, designed to make your phone visually react to the world around you.

Instead of showing a static wallpaper beside an unrelated weather widget, Atmosynq connects the two. Your animated scene changes with your actual local weather, time of day, sunrise, sunset, cloud cover, precipitation, wind, visibility, and storms.

## Core Features

- Real-time local weather
- Current temperature and feels-like temperature
- Daily high and low
- Hourly forecast
- 7-day forecast
- Rain and snow forecasts
- Humidity
- Wind speed, direction, and gusts
- Visibility
- Sunrise and sunset
- Dynamic day/night transitions
- Weather-reactive live wallpapers
- Real-time rain effects
- Snow effects
- Fog and atmospheric haze
- Cloud-cover-based lighting
- Wind-driven particle direction
- Thunderstorm and lightning effects
- Smooth transitions as conditions change
- Cached weather when temporarily offline
- Optional weather information displayed directly on the wallpaper

## Live Weather Engine

Atmosynq maps real weather conditions directly into the visual scene.

- Rainfall controls rain density and splash frequency
- Snowfall controls snow intensity
- Wind changes cloud and precipitation movement
- Cloud cover changes environmental lighting
- Visibility controls fog strength
- Sunrise and sunset gradually change scene lighting
- Thunderstorms enable lightning effects
- Day/night state changes the entire atmosphere

The wallpaper and weather dashboard use the same weather data path so the visual scene matches the conditions being reported.

## Weather Dashboard

The Android app currently includes:

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

Hourly conditions also carry their own day/night state so clear-sky forecast icons do not show a daytime sun at night.

## Wallpaper Display Modes

Planned display modes:

- **Clean** — animated scene only
- **Minimal** — temperature and current condition
- **Detailed** — temperature, feels-like, high/low, precipitation, and more

Weather information will also be repositionable so it does not cover important parts of a scene.

## Privacy

Atmosynq is being designed with location privacy in mind.

- Location is used to retrieve local weather
- Saved weather coordinates are stored in app-private preferences on the device
- Those preferences are excluded from Android cloud backup and device-transfer extraction
- Continuous background location access is not required for the core design
- No weather API key is required for the current weather provider implementation

## Technology

- Android
- Kotlin
- Android `WallpaperService`
- OpenGL ES / custom rendering
- Open-Meteo weather data
- Location-based weather synchronization
- GitHub Actions automated Android builds

## Development status

The current source implements the first live-wallpaper/weather synchronization milestone plus the first real weather dashboard and forecast experience.

The Android CI workflow runs unit tests, assembles the debug APK, and uploads the APK as a GitHub Actions artifact.

Still under active development:

- On-device renderer and battery validation
- Wallpaper Clean/Minimal/Detailed weather-text modes
- Semantic scene masks for more convincing day/night relighting
- Dedicated hail rendering
- Store release hardening

## Scene assets

The public repository does not bundle copyrighted or third-party wallpaper footage. The renderer has a built-in procedural fallback so the app and CI remain functional without a video asset.

For local/private development, place an original or properly licensed vertical loop at:

`app/src/main/res/raw/scene_neutral.mp4`

`tools/derain_source.sh` can prepare a supplied loop before it is copied into that location. The path is ignored by Git so private scene media is not accidentally published.

## Assets

Atmosynq's rendering engine is designed to support original, licensed, and user-provided visual scenes.

Copyrighted characters, artwork, video, or other third-party assets are not intended to be distributed commercially without the appropriate rights.

## License

License information will be added before the first public release.

---

### Atmosynq

**Your world, synced to the sky.**
