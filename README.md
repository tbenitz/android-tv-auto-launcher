# PirateFlix TV — fullscreen Android app

A minimal, remote-friendly Android APK that opens **https://pirate-flix.pages.dev/** as a full-screen website. It is a WebView wrapper, **not** a copy of the website, content catalog, or independent media player.

## What it does

- **Edge-to-edge fullscreen** website; no browser address bar or permanent toolbar.
- **Open last page on restart.** Saves the website's last URL as you navigate, including routes and query parameters. Existing cookies and site local storage are handled by Android WebView.
- **Go Home anytime** via the remote MENU button or by holding BACK for about 0.65 seconds. The menu also includes Back, Reload, and Exit.
- **Back one page** with a short press of BACK; when there is no browser history, it shows the menu instead of immediately exiting.
- **HTML5 fullscreen video**, including user-initiated fullscreen playback.
- **TV remote D-pad / OK** and ordinary taps supported by Android WebView, with a standard Android file chooser for any file inputs.
- **Android TV launcher tile** and ordinary Android phone/tablet launcher entry. No account, settings screen, or API key needed.
- **Automatic retry screen** if the main website cannot load; Chrome/WebView manages its usual browser cache and cookies.

> **Restore scope:** The app remembers the last URL on `pirate-flix.pages.dev`. It does not save a video's exact playback position or website views that don't change URL; those depend on what the website itself stores in the browser. Links to unrelated sites are not restored on startup.
>
> **Media limitations:** Playback and DRM depend on the website and the installed **Android System WebView**. An embedded video or codec unsupported by that system component may not work simply because this wrapper is installed.

## Install the APK from GitHub Actions

1. Create a new public repository named `pirateflix-tv-android` at https://github.com/new (if one does not exist).
2. Upload **all the files and folders inside this ZIP**, preserving their paths including the hidden `.github/workflows/build-apk.yml` file, to the repository's main branch. You can also push them with Git.
3. In the repository, open **Actions → Build Android APK**. The push build runs automatically if Actions are enabled; otherwise use **Run workflow**.
4. After the build finishes successfully, open the run's **Artifacts**, download `PirateFlix-TV-APK` (ZIP), and extract `PirateFlix-TV.apk`.
5. Copy the APK onto your TV (USB, shared folder, or file-transfer app), allow installation from that source, and install. The app appears as **PirateFlix TV** in your TV's app list.

**No Android Studio or local build tools required** for the GitHub build. Artifacts may require signing in to GitHub to download. Each default workflow build is debug-signed with the runner's Android debug key; later builds may have a different signature, so you may have to uninstall before installing a new build. For seamless upgrades, configure a persistent release-signing keystore and a release build before distributing updates.

## Optional local build

With JDK 17+, the Android SDK 35 installed, and Gradle 8.10.2:

```sh
gradle assembleDebug
```

The installable APK is then at `app/build/outputs/apk/debug/app-debug.apk`.

## Controls

| Remote action | Result |
|---|---|
| D-pad / OK | Navigate and interact with the website (where supported by the webpage) |
| Short BACK | Exit fullscreen video first, otherwise previous webpage; shows menu if at start |
| Hold BACK for about 0.65 seconds | Open navigation menu |
| MENU button | Open navigation menu |
| Menu → Go to PirateFlix Home | Navigate to homepage and clear WebView back history |
| Menu → Reload page | Refresh current page |
| Menu → Exit app | Close the app |

## Files

- `app/src/main/java/dev/pirateflix/tv/MainActivity.java` — WebView, navigation, fullscreen video, URL restoration.
- `app/src/main/AndroidManifest.xml` — permissions and TV launcher intent.
- `.github/workflows/build-apk.yml` — GitHub Actions APK build and optional tag release.
- `app/src/main/res/drawable-nodpi/tv_banner.png` — Android TV launcher banner.

## Privacy & scope

The app requests **INTERNET only**, and does not itself bundle content or host video. Page data and network requests go to the destination site (and any third-party services it loads). The user is responsible for ensuring they access media lawfully. This is an independent WebView wrapper, not an official app or a substitute for the underlying website.
