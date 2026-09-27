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

For example:

- Rainfall controls rain density and splash frequency
- Snowfall controls snow intensity
- Wind changes cloud and precipitation movement
- Cloud cover changes environmental lighting
- Visibility controls fog strength
- Sunrise and sunset gradually change scene lighting
- Thunderstorms enable lightning effects
- Day/night state changes the entire atmosphere

The wallpaper and weather dashboard use the same weather state so the visual scene matches the conditions being reported.

## Wallpaper Display Modes

Users will be able to choose how much weather information appears directly on their wallpaper:

- **Clean** — animated scene only
- **Minimal** — temperature and current condition
- **Detailed** — temperature, feels-like, high/low, precipitation, and more

Weather information will also be repositionable so it does not cover important parts of a scene.

## Privacy

Atmosynq is being designed with location privacy in mind.

- Location is used to retrieve local weather
- Weather location can be stored locally on the device
- Continuous background location access is not required for the core design
- No weather API key is required for the current weather provider implementation

## Technology

- Android
- Kotlin
- Android `WallpaperService`
- OpenGL / custom rendering
- Open-Meteo weather data
- Location-based weather synchronization
- GitHub Actions automated Android builds

## Project Status

Atmosynq is currently in active development.

The first development milestone focuses on:

1. Reliable Android live-wallpaper rendering
2. Real weather synchronization
3. Accurate day/night and sunrise/sunset transitions
4. Dynamic rain, snow, fog, wind, and thunder effects
5. Weather dashboard and forecasts
6. Performance and battery optimization
7. Real-device testing

## Future Plans

Atmosynq is being designed as more than a single live wallpaper.

Planned expansion includes:

- Multiple animated environments
- Scene library
- User-selectable wallpaper packs
- Seasonal environments
- Custom scene support
- Weather widgets
- Lock-screen integration where supported
- Advanced weather overlays
- Severe-weather visuals
- Scene customization
- Performance profiles for different devices
- Android release
- iPhone/iPad companion experience where platform capabilities allow

## Assets

Atmosynq's rendering engine is designed to support original, licensed, and user-provided visual scenes.

Copyrighted characters, artwork, video, or other third-party assets are not intended to be distributed commercially without the appropriate rights.

## License

License information will be added before the first public release.

---

### Atmosynq

**Your world, synced to the sky.**
