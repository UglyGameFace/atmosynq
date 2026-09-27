# Active Task

## Active task / outcome
Validate the merged Atmosynq Android Live Surfaces build on real hardware before beginning the Apple WidgetKit/Live Activity target.

## Merged Android baseline

### PR #1 — weather app + live wallpaper
Merged main SHA:
`d82a63ca5fc934c7ae47749312e4d0943738e765`

Implemented:
- real local weather dashboard
- next 12 hours
- 7-day forecast
- Open-Meteo shared weather path
- foreground location setup
- cached weather fallback
- OpenGL live wallpaper with day/night, rain, snow, fog, cloud dimming, wind, sunset warmth, and lightning

Post-merge CI run #21 passed.

### PR #2 — Android weather home widget
Merged main SHA:
`21560591d34a2d21039a836c9c73bb928229f40c`

Implemented:
- resizable Android home-screen widget
- rendered day/night sky
- sun/moon
- cloud density
- visible rain streaks
- wind-slanted rain
- snow particles
- fog/haze
- thunderstorm lightning
- system + manual refresh
- cached weather fallback
- shared weather-cache write-through
- one-tap widget pinning
- Binder-safe 480x280 scene bitmap

Exact-head PR CI run #23 passed.
Post-merge main CI run #24 passed.

### PR #3 — Android 16 home / lock / both live wallpaper
Merged main SHA:
`bf67b7f1d1dbbcf2853bb36cd4d7aa79064695cf`

Implemented:
- Android 16 `WallpaperDescription` instance support
- persistent home / lock / both surface identity
- legacy `onCreateEngine()` path for older Android
- Android 14+ wallpaper-flag runtime fallback
- per-surface OpenGL render profile
- home: 100% brightness / ~33 FPS target
- lock: 86% brightness / ~24 FPS target
- both: 94% brightness / ~29 FPS target
- unit tests for flag/profile resolution

Exact-head PR CI run #25 passed.
Post-merge main CI run #26 passed.

## Current repository state
Repository:
`UglyGameFace/atmosynq`

Default branch:
`main`

Latest functional merge SHA:
`bf67b7f1d1dbbcf2853bb36cd4d7aa79064695cf`

README was updated after the functional merge to document widgets and per-surface wallpaper support.

## Current Android test artifact
Build source:
PR #3 exact implementation head `11f06f938e62baa57ee6450f7d54f9198c2eb5ee`

Actions run:
#25

Artifact:
`atmosynq-debug-apk`

APK:
`app-debug.apk`

APK size:
957,421 bytes

APK SHA-256:
`423cde4bec24a7cb362dc9af747886e7e2ee9ec4ba45665760baf4b8dec1850f`

The squash merge contains the same implementation tree; main post-merge run #26 also passed.

## Real-device validation checklist
Install the debug APK on Android and verify:

1. App launches.
2. Foreground/approximate location permission works.
3. Current weather loads.
4. Next-12-hours and 7-day forecasts render correctly.
5. “Add Atmosynq home widget” opens the launcher pin flow.
6. Widget renders current day/night and condition visuals.
7. Widget manual refresh works.
8. Widget resizes without clipped/garbled content.
9. Offline/cached widget state remains usable.
10. Live wallpaper preview opens.
11. Rain/snow/fog/cloud/lightning effects render correctly.
12. Android 16 picker exposes home, lock, or both placement as supported by the device/OEM.
13. Home wallpaper profile renders at expected brightness.
14. Lock wallpaper profile is slightly dimmer and remains animated.
15. Unlock/relock does not leak renderer threads or freeze video.
16. Screen-off/background state stops unnecessary rendering.
17. Battery/GPU use is acceptable during normal use.
18. No crashes when switching widget/wallpaper/location repeatedly.

## Known limitations / honest boundaries
- The public build intentionally does not ship the supplied third-party scene video; it uses the procedural renderer fallback unless an original/licensed private scene is added.
- Widget weather scenes are rendered snapshots per refresh, not continuous 30 FPS animation.
- OEM launchers and wallpaper pickers can behave differently from AOSP and must be tested on-device.
- Wallpaper Clean/Minimal/Detailed text overlays are not implemented yet.
- Semantic scene masks for true high-fidelity daytime conversion are not implemented yet.
- Dedicated hail particles are not implemented yet.

## Apple target after Android device gate
Planned iPhone/iPad work:
- SwiftUI Atmosynq weather app
- WidgetKit Home Screen widgets
- WidgetKit Lock Screen accessory widgets
- ActivityKit Live Activities
- Dynamic Island views for user-started weather-event tracking
- weather-reactive generated wallpaper exports
- Live Photo export support where appropriate

Apple does not expose an Android-style continuously running third-party wallpaper service, so Atmosynq will use the native Apple surfaces rather than falsely promising identical wallpaper behavior.

## Next step
Install the build-proven APK on a real Android device and report screenshots/behavior for the app dashboard, widget, and home/lock live wallpaper. Root-cause any real-device failures before starting the Apple target.
