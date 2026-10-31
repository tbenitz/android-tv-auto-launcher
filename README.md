# PirateFlix TV 2.2 — Android TV grid + native playback

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
