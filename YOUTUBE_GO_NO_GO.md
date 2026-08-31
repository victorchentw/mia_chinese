# YouTube Phase 0B Go/No-Go

## Decision

**No-Go for the release build.** The release policy remains MP4-only until a
real supported Android TV / set-top-box is selected and passes the matrix
below. Debug builds keep the IFrame path available for a repeatable spike.

## Test run

| Item | Result |
| --- | --- |
| Device | `emulator-5554`, Android TV emulator, 1920×1080 |
| Android API | 28 |
| WebView | `com.google.android.webview` 66.0.3359.158 |
| Hardware acceleration | Application enabled; WebView created a H.264 decoder |
| Test video | Public catalog video `XPvJgDZ06A8` |
| Local player page | Loaded through `https://appassets.androidplatform.net/` |
| IFrame API / duration | `onReady` arrived; duration `1:44:32` was reported |
| Native D-pad seek | Left/Right moved the native cache by 5 seconds |
| Native overlay / Back path | Present; error state has retry and return actions |
| Play / pause | **Failed on this WebView**: playback did not advance and the IFrame reported an autoplay/playback restriction; the surface stayed black |
| WebView lifecycle | No app fatal exception; decoder was released when the player left the route |

The emulator also logged that this old Chromium does not provide newer Web
APIs used by the current YouTube bootstrap. That is sufficient to reject
YouTube for a production baseline; it is not evidence that every target TV
will fail.

## Release policy

- `PlaybackPolicy.youtubeEnabled` is false in release builds and true only in
  debug builds for the spike.
- Release course pages show an explicit `YouTube・待 Go/No-Go` state rather than
  a button that can enter an infinite loading screen.
- No Google login, cookie workaround, stream URL extraction, or policy bypass
  is used.

## Required re-test before changing to Go

1. Record the actual lowest-spec TV/box model, Android TV version, WebView /
   Chromium version, hardware acceleration and network conditions.
2. Repeat ready, play, pause, seek, ended, Back checkpoint, process/background
   recreation, network loss/recovery, and media-key tests.
3. Test public, private/unlisted, age-restricted, region-restricted,
   embedding-disabled and removed videos.
4. Obtain product approval for ads, branding, controls and YouTube terms.
5. Only then change the release gate and run the full signed-release smoke
   test; otherwise move the content to managed MP4/CDN assets.
