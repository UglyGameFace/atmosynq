# Active Task

## Active task / outcome
Finish and validate Atmosynq v0.2.0 branding plus app-wide Animated / Static visual modes after the first real-device Android test.

## Real-device evidence
The merged Android baseline was installed on a Samsung Android device.

Confirmed working:
- Atmosynq launches
- current/hourly/7-day weather renders
- launcher widget pin flow opens
- live-wallpaper preview opens

Observed:
- the public wallpaper build shows the procedural fallback because third-party/private scene video is intentionally not bundled
- launcher/widget integration works but still needs visual/device refinement
- the old generic app branding needed replacement

## Selected official brand
Use the user-selected Atmosynq design:
- luminous cyan/blue/violet atmospheric vortex
- dark navy background
- stylized Atmosynq cyan/blue/violet wordmark

This branch adds that design as real Android resources, not concept-only artwork.

## Current branch
`feat/brand-and-motion-modes`

## Implemented on this branch
- selected Atmosynq vortex mark added as a binary Android resource
- selected stylized Atmosynq wordmark added as a binary Android resource
- launcher icon + round icon
- Android adaptive icon resources
- branded Android launch/splash treatment
- branded in-app header
- branded home widget mark + widget picker preview
- app version bumped to 0.2.0 / versionCode 3
- global `MotionPreferenceStore`, Animated default
- in-app Animated / Static toggle
- toggle refreshes the home widget immediately
- animated in-app vortex pulse/float/glow loop
- static in-app vortex mode
- live wallpaper reads the motion preference continuously

## Animated wallpaper behavior
- scene video plays
- weather state transitions smoothly
- rain/snow particles move
- lightning can flash
- normal home/lock/both frame cadence is used

## Static wallpaper behavior
- scene video pauses
- current weather styling remains
- rain/snow particles remain visible but freeze
- lightning flashing stops
- weather state updates without tweening
- renderer drops to a 1 FPS low-motion refresh cadence

## Animated widget behavior
Android RemoteViews officially supports ViewFlipper, so the widget uses a supported lightweight animation path rather than claiming it can host the full OpenGL renderer.

Animated mode:
- three 320x187 weather-rendered frames
- ~1.35 second ViewFlipper cadence with fade transitions
- clear sun/moon glow changes subtly
- cloud placement changes
- rain streaks change
- snowflake placement changes
- fog drifts
- thunderstorm cycle includes a lightning frame

Combined raw bitmap payload is kept around 718 KiB for the three ARGB frames.

## Static widget behavior
- separate non-auto-start ViewFlipper layout
- one representative weather frame
- no scene cycling
- thunderstorm static art still includes lightning

## Platform boundary
The launcher/adaptive icon is static. Ordinary Android launchers do not give apps a continuously running arbitrary icon renderer. Motion is provided in the app, widget, and live wallpaper where Android supports it.

## Validation gate
Open PR #4 from `feat/brand-and-motion-modes` to `main`.

Before merge, exact-head CI must pass:
- Android resource linking
- Kotlin compilation
- all unit tests
- debug APK assembly
- artifact upload

Then verify post-merge main CI and download the exact APK for another device test.

## Remaining real-device checks
- selected launcher/adaptive icon appearance under Samsung icon masks
- branded splash appearance
- stylized wordmark scaling on phone
- Animated / Static toggle behavior
- animated widget frame flipping on Smart Launcher
- static widget truly remains still
- live wallpaper stops/starts motion immediately when the preference changes
- battery/GPU difference between Animated and Static
- widget resize behavior after ViewFlipper conversion

## Known boundaries
- the public repo still does not ship copyrighted/third-party wallpaper footage
- private/original licensed `scene_neutral.mp4` remains supported
- widget animation is lightweight frame flipping, not 30 FPS OpenGL
- Apple implementation remains a later platform-specific target

## Next step
Open the brand/motion PR, let exact-head Android CI attack the resource and Kotlin changes, repair anything real, merge only when green, then install the new v0.2.0 APK on the Android device and validate the official branding plus both visual modes.
