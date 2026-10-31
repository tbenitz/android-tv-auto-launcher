# PirateFlix TV 2.0 — Android TV browsing shell

A TV-first wrapper for **https://pirate-flix.pages.dev/**. The catalog is read from the live website (no fake titles/channels), so the APK can display the site's real movie/channel cards in a dedicated TV grid. The underlying website remains responsible for content listings, playback and source/episode selection.

## New experience

- **TV grid:** separate fullscreen, scrolling poster/channel grid with a left-hand Movies / TV Shows / Live TV / Watch Later / Website view rail. The webpage's desktop header is no longer drawn over the grid. The TV shell rebuilds when the site updates its titles.
- **D-pad / Enter:** move selection in the grid and press OK to open a real website card. Navigation avoids Favorite buttons nested inside Live TV channel cards. Search opens the software keyboard only when OK is pressed on the Search field.
- **Website/detail view:** opens after selecting a TV card, hiding the site's large top header; select episodes and sources using real page controls. Press Back to return to the TV grid.
- **Fullscreen:** compatible video / embedded player elements can be isolated from the site header and expanded across the screen. The player is not replaced with an unrelated source.
- **Zoom:** menu → Page zoom from 40%, 50%, 60%, 70%, 80%, 85%, 90%, 100%, 115%, 125%, 140%, or 160%. This controls the underlying website; the TV grid keeps a readable layout.
- **Playback controls where supported:** Left/Right skips 15 s, held Left/Right accelerates up to 240 seconds per second; playback can be jumped to 10/25/50/75/90% or an exact time using Menu. A frame bridge attempts seeking in compatible embedded HTML5 players.
- **Clear failure behavior:** cross-origin hosts that don't expose controllable HTML5 video cannot be forced to seek by a WebView. The app reports unsupported seeking rather than claiming success. Use the actual site's source selection or a different compatible source.
- **Ad domain blocking:** conservative known-ad-host list; avoids intercepting normal media sources. No privileged VPN or DRM bypass.
- **Remembers the last website page** as a URL.

## Remote controls

| Button | TV grid | Website / player |
|---|---|---|
| D-pad | Move rail/grid selection | Move site focus; Left/Right seeks when a player is detected |
| OK | Open selected category, card or search field | Select focused button or play/pause in cinema mode |
| Back | Exit / browser menu | Return to TV grid or exit cinema first |
| Hold OK, Hold Back, Menu, Info | App control menu | App control menu |
| Menu → TV grid | TV browsing layout | Return to TV browsing layout |
| Menu → Website view | Original website controls without top banner | Browse sources, filters, episodes |
| Menu → Fullscreen current player | — | Attempts fullscreen video isolation |
| Menu → Change video source | — | Highlight an actual website source control when available |

## Build

The source is published to [the `pirateflix-tv-wrapper` branch](https://github.com/tbenitz/android-tv-auto-launcher/tree/pirateflix-tv-wrapper). GitHub Actions creates a debug signed `PirateFlix-TV.apk` artifact. Android TV 6+ (API 23+) / Mi Box compatible, dependent on Android System WebView.

Note: GitHub Actions debug builds may use different signing keys. If an update fails to install over an older version, uninstall the old package first; doing so clears its local website data.

## Technical limitations

This APK does not control the website's unpublished source code. It creates a TV layout from the current site's DOM, so significant changes to the website's markup could require an update. It cannot guarantee playback or seek in a proprietary or DRM-protected third-party iframe. Direct/native stream playback would require an explicit supported media URL from the website or authorized streaming source and a separate player integration.
