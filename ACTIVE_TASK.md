# Active Task

## Active task / outcome
Expand Atmosynq from the merged Android weather/wallpaper baseline into Live Surfaces, starting with a build-proven Android home-screen weather widget that renders real weather visuals from the same Atmosynq weather state.

## Current baseline
PR #1 merged successfully into `main`.

Merged main SHA:
`d82a63ca5fc934c7ae47749312e4d0943738e765`

Post-merge Android CI:
- Actions run #21
- unit tests passed
- debug APK assembly passed
- artifact upload passed

## Current branch
`feat/android-live-surfaces`

## Current Live Surfaces scope
- Add a resizable Android home-screen widget.
- Render miniature weather scenes instead of generic condition icons.
- Use the same saved location and Open-Meteo weather path as the app and live wallpaper.
- Visually represent day/night, cloud cover, rain, snow, fog, and thunder/lightning.
- Scale rain/snow visual density from observed condition/rate.
- Use wind direction to angle rain.
- Refresh on the system widget cadence and allow manual refresh.
- Reuse cached Atmosynq weather if a refresh cannot reach the network.
- Feed successful widget refreshes back into the shared Atmosynq weather cache.
- Open the app when the widget body is tapped.
- Provide one-tap widget pinning from the Atmosynq app when supported by the launcher.
- Keep this Android home widget honest: it is a rich rendered snapshot surface, not a continuous 30 FPS OpenGL widget.

## Implemented on this branch
- `AtmosynqWidgetProvider`
- `WeatherWidgetSceneRenderer`
- resizable `widget_atmosynq.xml`
- `atmosynq_widget_info.xml`
- widget receiver registration in the manifest
- widget strings/accessibility labels
- in-app “Add Atmosynq home widget” action
- 30-minute system update request
- explicit tap-to-refresh
- shared weather-cache write-through

## Visual widget behavior
- clear/day: blue sky + sun
- clear/night: dark sky + moon
- cloud cover: rendered multi-layer clouds with density from cloud percentage
- rain/showers: visible rain streaks with intensity and wind slant
- snow: visible snow particles with intensity from snowfall/WMO condition
- fog: translucent haze bands from visibility/WMO fog state
- thunderstorm: storm clouds + visible lightning bolt + precipitation

## Platform boundaries
- Android home-screen widget: implemented here.
- Android live wallpaper remains the only continuously rendered full-frame weather surface.
- Android lock-screen/live-wallpaper target behavior is the next Android surface after this widget passes CI/device testing.
- iOS/iPadOS WidgetKit/Lock Screen/Live Activity work will be a separate platform target after the Android Live Surfaces slice is stable.

## Validation gate
Open a PR from `feat/android-live-surfaces` to `main`, run exact-head Android CI, fix any compiler/resource/runtime-contract issues, and only merge after unit tests + debug APK assembly + artifact upload pass.

## Remaining risks
- Real launcher rendering/resize behavior is not proven until installed on an Android device.
- Widget update timing is controlled by Android/launcher scheduling and is not exact.
- Widget weather art is rendered per refresh, not continuously animated.
- Real-device battery, memory, and bitmap scaling still require validation.
- The public APK still uses the procedural wallpaper fallback unless original/licensed private scene media is supplied.

## Next step
Open the Android Live Surfaces PR, run exact-head CI, repair any build failures, merge only when green, then validate the widget and live wallpaper on-device before starting the Apple WidgetKit target.
