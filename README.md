# PirateFlix TV — Android TV full-screen wrapper (v1.2)

TV-first app that opens https://pirate-flix.pages.dev/ in Android WebView. This is a wrapper, not an independent movie/TV streaming service. Content and source URLs belong to the site.

## v1.2 fixes

* **Live TV cards are selectable:** parent channel tiles remain available even when they contain a nested Favorite icon. OK opens the selected card, not the favorite button. D-pad holding repeats navigation for long channel grids.
* **Actual cinema isolation:** when an HTML5 player or supported embedded video is launched, other page sections (including header, tabs, search box, and sidebar) are hidden, not overlaid across the player. Back restores the original page. A manual exit no longer automatically re-enters cinema while the video keeps playing.
* **Video seek controls:** Left/Right taps attempt ±15 seconds. Hold to accelerate to 30, 60, 120, then 240 seconds/second. For a long movie, **Hold OK → Jump to time / percentage** allows 10%, 25%, 50%, 75%, 90%, or `hh:mm:ss` (for example `01:15:30`). Seek is clamped to the media's duration.
* **Iframe seek support:** uses AndroidX WebKit's optional document-start injection to put a minimal media-control listener in HTML5 iframe players, including supported cross-origin frames, without reading or changing stream URLs. Older System WebViews that lack the injection feature fall back to native/top-page video controls.
* **Source selection:** Hold OK → Change video source exits cinema, highlights the website's actual server/source picker if present. No fake sources, DRM bypass, video extraction, or transcoding.
* **Search input:** D-pad selection does not focus Search; press OK to open the keyboard. Up/Down exits the keyboard to browse results.
* **Zoom:** default 115%, configurable from 90% to 140% in the app menu, remembered between launches.
* **Ads:** lightweight known-ad-host blocking and popup suppression remain in place without disrupting ordinary media providers where possible.

## Remote controls

| Remote | Website browsing | Movie/video playing |
| --- | --- | --- |
| D-pad Up/Down | Select cards, tabs and buttons; scroll longer lists | Show controls hint |
| D-pad Left/Right | Move selection horizontally | Tap: ±15 seconds; hold: faster seek |
| OK / Enter | Open selected channel/card; on Search opens keyboard | Pause/Play if HTML5 player is accessible |
| Hold OK (~0.65s), MENU, or INFO | Options including Home and page size | Video options including **Jump to time / percentage**, Change source |
| Back | Previous web page | Exit cinema back to page, or step back |
| Hold Back (~0.65s) | App control menu | App control menu |

**Video limitation:** Some external players use DRM, proprietary runtimes, canvas rendering, cross-origin restrictions, or custom protections, so controlling their timeline from a WebView wrapper is not guaranteed. For a player that cannot be sought directly, its own timeline may still be required. Live TV streams without a seekable timeline cannot jump halfway through a movie.

## Build on GitHub

The code is on the [`pirateflix-tv-wrapper` branch](https://github.com/tbenitz/android-tv-auto-launcher/tree/pirateflix-tv-wrapper). The original `main` branch is not modified.

1. Open the [GitHub Actions builds](https://github.com/tbenitz/android-tv-auto-launcher/actions/workflows/build-apk.yml).
2. Select the `pirateflix-tv-wrapper` branch and open the latest successful run.
3. Under **Artifacts**, download the `PirateFlix-TV-APK` archive; extract `PirateFlix-TV.apk`.
4. Sideload that APK on the Android TV box.

Build requirements: Java 17, Android SDK 35, Gradle 8.10.2; installs on Android 6.0+ (API 23) if the dependencies permit, including Android TV / Mi Box. A debug APK may not install over an earlier debug APK signed by a different key; uninstalling the old app removes its browser storage and saved URL. To preserve data on subsequent upgrades, use a stable signing key.

## Implementation

* `app/src/main/java/dev/pirateflix/tv/MainActivity.java` — native WebView, D-pad, menu, zoom, media bridge, cookies, last URL, advertising host filter.
* `app/src/main/assets/tv-nav.js` — card-aware TV navigation, cinema isolation, source selection, seeking and time-jump UI commands.
* `app/src/main/assets/iframe-media.js` — tiny start-of-document iframe message listener for supported HTML5 video elements.

The site and its third-party resources must be used lawfully. The APK does not provide media, host video, circumvent DRM, or guarantee an external player will support seeking.

## Testing

JavaScript syntax is checked in CI. Build success does not guarantee playback support on every third-party video host; actual TV navigation and embedded seeks must be tested on device.
