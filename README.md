# Orbit TV Launcher — v2.0.0-preview1 (Mi Box-safe preview)

> **Safety notice:** Do NOT change your TV's default Home launcher to this preview before testing it. A freeze was reported with v1.1.1 on Mi Box. The older builds remain unsafe as default Home; the site links to preview releases for cautious testing. Previous versions should not be used as Home.

## Repair notes
- Replaced the v1.1.1 zero-height All Apps dialog (which could make app selection inaccessible) with a full-screen GridView app picker that uses the TV remote.
- Removed synchronous PackageManager queries and icon loading from the UI thread: apps now load on a worker thread and the Home screen stays responsive while scanning.
- Home, All apps, and Settings are permanent top-level navigation screens, with Back returning to Home. Favorites and startup app are managed with visible OK actions rather than relying on long-press.
- On a fresh installation, auto-launch is OFF until an app is explicitly selected as the startup app. Any previously saved preference is preserved.
- Startup target is attempted only from the foreground HOME Activity, at most once per boot. The original 350 ms transition is gone; firmware animation may still flash.
- Android's system Home role is requested only after an explicit warning; the app never disables system launchers.
- If an app is missing or fails to launch, the Home screen remains accessible.
- No remote ADB, accessibility, or root permissions are requested. Only APK Center uses INTERNET and the standard user-approved APK installer permission.

## Preview is isolated from your old launcher
Debug builds use the distinct app package **`io.github.tbenitz.tvautolaunch.preview`**, install alongside the disabled production launcher, and are labeled **Orbit TV Preview**. They do not replace or re-enable the broken app. This helps avoid signature errors and keeps the existing Home package untouched. The preview still supports becoming HOME, but **do not choose it as Home before manually testing its screens**.

## Safe test plan (without changing your working Mi Box Home)
1. Restore and retain your original Mi Box Home, as you already did.
2. Install this preview as an ordinary sideloaded app. The preview has a different package identifier, so it installs alongside the disabled old app; no uninstall is required.
3. Open TV Auto Launch from your original TV's Apps list.
4. Confirm **All apps** opens, icons load, D-pad can move through every row, OK shows actions, you can add/remove favorites, set a startup target, launch another app and return, and enter Android settings.
5. **Do not assign it as Home until those tests succeed.** This GitHub APK is a preview, not a firmware-verified fix.

## Emergency recovery from a connected Android-phone ADB shell
**Only if** your original Xiaomi Android TV Home package is `com.google.android.tvlauncher`:
```sh
pm enable --user 0 com.google.android.tvlauncher
pm disable-user --user 0 io.github.tbenitz.tvautolaunch.preview
input keyevent 3
```
Those recovery commands are for the preview app. To disable the older non-preview app instead, use `io.github.tbenitz.tvautolaunch`. Verify that the first command succeeded before disabling any launcher. This leaves the app installed and retains its settings. Do not factory reset or uninstall a launcher while it is the only enabled HOME handler.

## Building
The workflow on GitHub Actions builds Android API 35, Java 17, Gradle 8.9. Preview APKs use an ephemeral CI debug signer; Android generally cannot update an APK that was signed by a different debug key. Stable publishing requires a persistent private release keystore, not a key committed to the public repository.

## What it does
The new remote-friendly Home has a startup card, scrolling favorites tiles, searchable All apps, dedicated Settings, a clock toggle, 3–6 grid columns, a dark theme switch, and immediate/1/3/5-second startup options. Favorites migrate from the existing SharedPreferences where upgrade signing permits.

The app is MIT licensed, uses Internet only for user-initiated APK sources, contains no advertising or tracking, and targets Android 6.0+ devices.

## v2: Orbit TV features
- Keep the old production package untouched: GitHub Actions preview remains `io.github.tbenitz.tvautolaunch.preview`.
- Home quick actions: APK Center, All apps, Android settings, and recovery.
- **OK launches an app. Hold OK (or use MENU/INFO when supported) for app actions.** The app action menu manages favorites, startup selection, ordering, app information, and Android-confirmed uninstall.
- APK Center supports public GitHub Releases (choose an actual .apk asset), HTTPS APK URLs, an official F-Droid APK download, and the Android system file picker for USB/local files.
- APKs download in background, with progress and a 400 MB safety limit. GitHub-provided SHA-256 is checked when present, and downloads are checked for a recognizable APK package before asking Android to install.
- No silent installs: user grants Android's 'Install unknown apps' permission for the launcher and confirms every install in Android's installer.
- GitHub repository names are remembered locally. This is not yet a complete catalog, background auto-updater, or phone-to-TV uploader.
- **Do not set preview as Home without testing on real hardware.** The old Mi Box freeze warning still applies. Re-enable stock launcher before attempting any ADB changes.
