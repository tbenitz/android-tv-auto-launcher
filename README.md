# PirateFlix TV 2.3 — Android TV grid + native playback

This Android TV browser wraps `https://pirate-flix.pages.dev/` and builds a TV-friendly browsing interface from the **actual page content** (not a hard-coded catalog). Android System WebView must be installed and the site must be reachable.

## TV layout and browsing

- Exactly **five poster columns**, with **three complete visible rows**; use the D-pad to scroll more titles.
- Loads poster URLs from `<img>`, lazy image attributes, and `<picture>` sources, and scans additional offscreen results. The catalog depends on the website's DOM and does not synthesize nonexistent titles or channels.
- Search: move to Search using the D-pad and press **OK**. A **native Android TV keyboard dialog** opens. Choose Search to apply the query to the website and rebuild the grid.
- Movies / TV Shows / Watch Later use the site's corresponding tabs.
- Live TV: chooses the site's **US** filter where identifiable and only shows known-US channel cards, or cards from a confirmed US-filtered site view. If the site exposes no region metadata/control, the app explicitly says so rather than labeling unverified channels as US.
- Title selection fades the grid and opens its page. Select a source/episode using the website as needed.
- Website view's page zoom remains adjustable from **40% to 160%**.

## Native video and mini-player

For **direct, accessible HTTP(S) media URLs** exposed by a page (`<video>`, `<source>`, or a direct link ending in mp4, m3u8, mpd, m4v, webm, mkv), the app can switch to a separately controlled Media3/ExoPlayer video surface. It supports MP4, DASH, HLS, and other formats supported by Android decoders. Direct stream selection is not always possible; the site may use a third-party iframe/DRM or transient blob URL. **The app does not extract protected streams, bypass CORS/DRM, or guarantee all sources work.**

- Native fullscreen **Left/Right**: 15-second tap; keep held to accelerate up to 240 seconds per second. **Menu → Seek** has direct percentages and jump to exact time.
- Native fullscreen **Back**: return to the **TV grid** with a **bottom-left mini-player**, keeping playback and position.
- From the sidebar press **Left** to focus the mini-player. Use **Left/Right** to choose **Play/Pause** or **Full screen**, press OK to activate. Press Up/Down to return to the grid. You can also access mini-player actions from Menu.
- **Menu → Use native player** retries opening a direct URL exposed by the current site page. Unsupported embedded players use the WebView fallback.
- A successful native player will pause the webpage's own HTML5 video to avoid duplicate audio. Close mini-player in Menu to stop it.

## GitHub APK build

GitHub Actions builds a debug APK after changes to the `pirateflix-tv-wrapper` branch in `tbenitz/android-tv-auto-launcher`, and offers it under the artifact **PirateFlix-TV-APK**. The existing launcher's `main` branch is unchanged.

Requirements: Android TV 6.0+, Android System WebView, network access. Media3 1.5.1 is included at build time. Debug APK signatures may differ between Actions runs; if an upgrade reports a signature conflict, uninstall and reinstall (this clears local app data).

**Not yet verified on physical TV hardware.** The web site can change HTML/JavaScript independently of this app, so poster and channel extraction requires follow-up testing against the actual live site.

## v2.3 — Sources, auto fullscreen, and fallback seeking

- **When selecting a movie**, the app now retries fullscreen promotion while an embedded player loads, rather than requiring the web player to be already playing. The site must still actually provide an open player; this does not bypass a host's play button or autoplay policy.
- **Choose available sources (TV picker)** opens a native Android dialog listing real source/server controls identified on the page and, where supported, its embedded player. It provides a count and in-place selection. Some player providers never expose source controls to injected scripts; the website fallback remains available.
- **Seeking inside embedded players** first attempts HTML5 `currentTime`, then checks for recognizable 10-second rewind/forward buttons in that player's own iframe. Repeated button clicks are rate-limited and capped per batch. A reported click count confirms commands were issued, **not** that the proprietary player's timeline actually moved. Players that hide controls or reject scripted clicks may remain unseekable.
- **Native playback** automatically tries ordinary supported direct media URLs exposed by a video's `<video>` element (including those in frames on WebView versions supporting document-start scripts). For the manual native-player option, it may also use a recognized MP4/HLS/DASH/etc. URL that the WebView requested while opening the chosen title; it does **not** decrypt or bypass any protection or source authorization. Its video formats/codecs and live seeking remain constrained by Media3 and the device.
- If native playback fails after starting, the app returns to the web player rather than repeatedly reopening the same failing URL.

**Limitations:** A player inside an inaccessible or protected iframe cannot be made universally controllable. A request URL alone may lack required cookies, headers, expiring tokens or codecs, and a DRM-protected stream is not transferable to an ordinary player. Source counts describe controls the app can discover, not the total hidden on a provider's server. Fullscreen still depends on the selected site's player becoming available.
