# Active Task

## Active task / outcome
Continue Atmosynq Live Surfaces after the Android weather widget merge by adding real Android 16 per-surface live-wallpaper instances for home, lock, or both screens without breaking the legacy wallpaper engine path.

## Proven baseline
PR #1 merged the Android weather dashboard + live wallpaper baseline.

PR #2 merged the Android weather home widget.

Current main SHA:
`21560591d34a2d21039a836c9c73bb928229f40c`

Post-merge widget CI:
- Actions run #24
- unit tests passed
- debug APK assembly passed
- artifact upload passed

## Current branch
`feat/android-wallpaper-surfaces`

## Android wallpaper-surface implementation
- Keep the legacy `onCreateEngine()` path for older Android versions.
- Use Android 16 `WallpaperService.onCreateEngine(WallpaperDescription)` when the framework supplies persisted instance content.
- Use Android 16 `Engine.onApplyWallpaper(which)` to capture whether the user applies Atmosynq to home, lock, or both.
- Persist a unique `WallpaperDescription` id/content payload for each destination.
- Use Android 14+ wallpaper flags as a runtime fallback to identify the current render target.
- Pass the resolved surface profile into the OpenGL renderer.
- Preserve identical real-weather state/effects on every surface.
- Home profile: full brightness, ~33 FPS target.
- Lock profile: 86% brightness, ~24 FPS target to reduce glare/GPU work.
- Both profile: balanced 94% brightness, ~29 FPS target.
- Add pure unit tests for flag/profile resolution.
- Label the Android 16 app action as “Set home / lock live wallpaper”.

## Existing Live Surfaces
- Android weather dashboard: merged and build-proven.
- Android continuously animated weather wallpaper: merged and build-proven.
- Android weather home widget with rendered rain/snow/fog/clouds/lightning: merged and build-proven.
- Android 16 per-surface home/lock live wallpaper instances: current work.
- iOS/iPadOS WidgetKit, Lock Screen widgets, Live Activities, and generated wallpaper support: next platform slice after Android per-surface CI/device validation.

## Platform evidence
Android 16 added `WallpaperDescription` and distinct live-wallpaper instances. The framework calls `Engine.onApplyWallpaper(which)` with home/lock/both and can persist the returned description for later `onCreateEngine(description)` calls.

## Validation gate
Open a PR to `main`, run exact-head Android CI, repair any API/signature/shader failures, and merge only after unit tests, debug APK assembly, and artifact upload all pass.

## Remaining device gates
- Real Samsung/Pixel wallpaper picker behavior for separate home and lock application.
- Whether OEM launchers preserve distinct instances exactly as AOSP describes.
- Real lock-screen GPU/battery behavior.
- Widget resize/rendering behavior on-device.
- Public build still uses the procedural scene fallback unless original/licensed scene media is supplied.

## Next step
Run Android CI for the per-surface wallpaper implementation. If green, review for pre-Android-16 compatibility, merge, verify post-merge main CI, then move to real-device testing before starting the Apple target.
