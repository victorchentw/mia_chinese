# YouTube Phase 0B Go/No-Go

## Decision

**No-Go as a universal WebView guarantee.** The tested API 28 WebView is not a
reliable baseline, but the current test build intentionally tries the system
WebView first and then offers an explicit external-player fallback. A
conservative MP4-only build is available with
`-PmiaEnableYoutubeWebView=false`. The fallback tries SmartTube before the
system YouTube／browser handler.

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
| Play / pause | Manual native Play advanced the position to `00:33` on this run, but the IFrame still logged the old-WebView `queueMicrotask` issue and the video surface stayed black; pause／resume needs real-device confirmation |
| WebView lifecycle | No app fatal exception; decoder was released when the player left the route |
| External player fallback | Implemented with SmartTube stable／beta package detection and generic `ACTION_VIEW`; SmartTube was not installed on this emulator, so real deep-link／Back behavior remains unverified |

The emulator also logged that this old Chromium does not provide newer Web
APIs used by the current YouTube bootstrap. The manual play progress is useful
for the spike, but the black surface and old-WebView errors are sufficient to
reject it as a universal production guarantee; it is not evidence that every
target TV will fail. Android TV may receive a newer Android System WebView／Chrome from
Play Store or a system update, but the installed provider is selected by the
OS. Installing an APK cannot force a provider update or switch.

## Release policy

- `PlaybackPolicy.youtubeEnabled` is true by default in both variants; use
  `-PmiaEnableYoutubeWebView=false` to produce a conservative MP4-only build.
- Course pages try WebView by default. The player overlay always offers
  `外部播放`; a WebView timeout／playback failure also shows retry and error
  messaging rather than silently waiting forever.
- MP4-only builds show `YouTube・待 Go/No-Go` plus an intentional external-player
  action.
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
5. Only then consider declaring WebView a universal release guarantee and run
   the full signed-release smoke test; otherwise keep the WebView-first／external
   fallback as an exploration build or produce the conservative MP4-only／managed
   MP4-CDN release.

## WebView update note

The app reports the current WebView provider and version in Settings. On a real
Android TV, check Android System WebView／Chrome in system apps and update it
through the device's supported Play Store/system update path. API 28 emulator
images may not have a usable update path, and a newer package is not enough if
the OS still selects the old provider.
