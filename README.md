# TV Auto Launch — v1.2.0-rc1 stability preview

> **Safety notice:** Do NOT change your TV's default Home launcher to this preview before testing it. A freeze was reported with v1.1.1 on Mi Box. Downloads on the GitHub Pages landing site are paused until the redesigned launcher has been checked on physical hardware. Previous versions should not be used as Home.

## Repair notes
- Replaced the v1.1.1 zero-height All Apps dialog (which could make app selection inaccessible) with a full-screen GridView app picker that uses the TV remote.
- Removed synchronous PackageManager queries and icon loading from the UI thread: apps now load on a worker thread and the Home screen stays responsive while scanning.
- Home, All apps, and Settings are permanent top-level navigation screens, with Back returning to Home. Favorites and startup app are managed with visible OK actions rather than relying on long-press.
- On a fresh installation, auto-launch is OFF until an app is explicitly selected as the startup app. Any previously saved preference is preserved.
- Startup target is attempted only from the foreground HOME Activity, at most once per boot. The original 350 ms transition is gone; firmware animation may still flash.
- Android's system Home role is requested only after an explicit warning; the app never disables system launchers.
- If an app is missing or fails to launch, the Home screen remains accessible.
- No remote ADB, internet, accessibility, or root permissions are requested by the launcher.

## Safe test plan (without changing your working Mi Box Home)
1. Restore and retain your original Mi Box Home, as you already did.
2. Install this preview as an ordinary sideloaded app. If an earlier APK signature conflicts, keep original Home enabled, uninstall the previous TV Auto Launch APK, and then install this preview.
3. Open TV Auto Launch from your original TV's Apps list.
4. Confirm **All apps** opens, icons load, D-pad can move through every row, OK shows actions, you can add/remove favorites, set a startup target, launch another app and return, and enter Android settings.
5. **Do not assign it as Home until those tests succeed.** This GitHub APK is a preview, not a firmware-verified fix.

## Emergency recovery from a connected Android-phone ADB shell
**Only if** your original Xiaomi Android TV Home package is `com.google.android.tvlauncher`:
```sh
pm enable --user 0 com.google.android.tvlauncher
pm disable-user --user 0 io.github.tbenitz.tvautolaunch
input keyevent 3
```
Verify that the first command succeeded before disabling this launcher. This leaves the app installed and retains its settings. Do not factory reset or uninstall a launcher while it is the only enabled HOME handler.

## Building
The workflow on GitHub Actions builds Android API 35, Java 17, Gradle 8.9. Preview APKs use an ephemeral CI debug signer; Android generally cannot update an APK that was signed by a different debug key. Stable publishing requires a persistent private release keystore, not a key committed to the public repository.

## What it does
The new remote-friendly Home has a startup card, scrolling favorites tiles, searchable All apps, dedicated Settings, a clock toggle, 3–6 grid columns, a dark theme switch, and immediate/1/3/5-second startup options. Favorites migrate from the existing SharedPreferences where upgrade signing permits.

The app is MIT licensed, has no internet permission or tracking, and is Android 6.0+ compatible in the build configuration.
