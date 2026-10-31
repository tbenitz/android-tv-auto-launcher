# PirateFlix TV — Android fullscreen browser (v1.1)

Dedicated Android TV / Mi Box WebView app for https://pirate-flix.pages.dev/. The site itself supplies movie lists, playback and the available source choices; this project does not host media or invent streams.

## TV controls

| TV remote | Browsing | Video / cinema |
|---|---|---|
| D-pad ↑ / ↓ | Move focused tile vertically; scroll long pages/lists as needed | Reserved for web video UI where available |
| D-pad ← / → | Move focus horizontally | Seek 15 seconds for a tap; hold to accelerate towards **240 seconds per second** |
| OK / Enter | Open selected item, **only open search keyboard when selected and OK is pressed** | Toggle play / pause when HTML5 video controls are accessible |
| Hold OK (0.65 seconds) | App control menu | App control menu, including **Change video source** |
| Short Back | Back website page / close keyboard | Close fullscreen first |
| Hold Back (0.65 seconds) | Home/back/source/player/zoom menu | Same menu |
| Menu or Info | App control menu | Same menu |
| Media FF / RW | Seek compatible HTML5 video | Progressive seek |

**Source selection:** choose **Change video source (site picker)** from the app menu. The app returns to the webpage and highlights any source/server/mirror button that the page exposes. The site—not the APK—owns the actual source URLs, so switching depends on its controls.

**Automatic cinema mode:** compatible HTML5 videos are promoted to edge-to-edge cinema view on playback. Large embedded video iframes are expanded after selecting Watch/Play or entering a video view. Press Back to return to the source and episode page. Native WebChrome fullscreen video remains supported.

**Seeking limitation:** many websites use cross-origin iframes for their video players. Android WebView cannot read/control `currentTime` inside those third-party iframes; the progressive seek feature works only when an HTML5 media element is accessible to the site page. For incompatible players the app leaves the native player's own controls available rather than pretending to seek.

**Page scale:** default 115% (larger TV labels and tiles). Choose 90%, 100%, 115%, 125% or 140% from **Hold OK → Page size / zoom**. Zoom preference survives restarts.

**Lightweight ad filtering:** intercepts requests to a curated set of known advertising/malvertising domains and discards unwanted cross-origin popups. Does not replace a full desktop ad blocker, and aggressive filtering may affect some streaming hosts. This app cannot prevent ads drawn directly by the site or inside a cross-origin video iframe.

**Last-page restore:** automatically saves the last first-party URL and restores it on reopening. The website manages its own browser storage and playback resume.

## Build on GitHub

This project lives on branch [`pirateflix-tv-wrapper`](https://github.com/tbenitz/android-tv-auto-launcher/tree/pirateflix-tv-wrapper) of an existing repository. The main branch and the original TV launcher are unchanged.

1. Visit **[Build PirateFlix TV APK — Actions](https://github.com/tbenitz/android-tv-auto-launcher/actions/workflows/build-apk.yml)**.
2. Select the `pirateflix-tv-wrapper` branch, trigger **Run workflow** if necessary, and open the latest successful run.
3. In **Artifacts**, download `PirateFlix-TV-APK` and extract the installable `PirateFlix-TV.apk`.
4. Sideload the APK on your Android TV box using your preferred file manager.

Build environment: Ubuntu, JDK 17, Android SDK 35, Gradle 8.10.2, and an automatic debug-signed APK. Android 6.0+ minimum. Debug-signed APKs created in different CI runs may use different keys, so installing a newer build may require uninstalling the older one (which clears the app's saved web data). Use a persistent release signing key for painless updates.

## Scope and security

App permission: INTERNET only. JavaScript and site browser storage are enabled because the website needs them. File access from the page is disabled. Third-party cookies stay enabled for embedded player compatibility. The app doesn't include an ad-blocking VPN or inspect HTTPS payloads. It doesn't bypass DRM/streaming restrictions or turn unsupported iframe players into native Android media players.

### Files

- `app/src/main/java/dev/pirateflix/tv/MainActivity.java`: Android TV controls, WebView, video shortcuts and ad-domain interception.
- `app/src/main/assets/tv-nav.js`: Web navigation and cinema integration. No libraries or trackers.
- `app/src/main/AndroidManifest.xml`: Android/TV launcher and network permissions.
- `.github/workflows/build-apk.yml`: GitHub APK build and artifact.
