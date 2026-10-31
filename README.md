# TV Auto Launch — Android TV startup app launcher

A lightweight, remote-controlled Android TV HOME replacement that lets you choose **one installed app** (Kodi, IPTV app, streaming player, etc.) and open it **once after each TV boot**. Fully local, zero trackers, no Internet permission, no Accessibility service, no root.

> **State of project:** Source and automated build pipeline provided. Actual hardware behavior has **not** been verified on every Android TV/Google TV firmware. GitHub Actions produces an APK after the code is uploaded; no APK binary is committed to the repository.

## New in v1.1.0 — Favorites Home screen

This version turns the original setup screen into a remote-friendly Home launcher. It includes a scrolling app-icon **favorites grid** (3, 4, 5 or 6 columns), a prominent **startup app** card, a searchable **All apps** chooser, hold-OK actions to reorder and unpin favorites, and a separate **Settings** panel. Startup apps are automatically pinned to favorites. Existing v1.0 selections migrate into the grid.

**Remote controls:** D-pad navigates, OK opens, hold OK on a favorite for options, Home returns here, Back at Home does not close the launcher. In All apps, OK opens the action menu and holding OK pins that app.

**Settings:** auto-start toggle, startup app, immediate/1/3/5 second boot delay (immediate is default), grid size, clock, Ocean/Charcoal theme, app management, change default Home, Mi Box help, Android system settings, and clear startup selection (favorites remain). No network permissions or tracking.

**Boot transition:** The Home grid is no longer constructed before the automatic startup launch; the target starts immediately from the foreground Home Activity. The window background remains dark during any optional startup delay. Device firmware and the target app can still show their own transitions: a completely invisible launcher-to-app transition is not guaranteed.

**APK signing warning:** Previous CI APKs were signed by a temporary debug key. Installing this build may require re-enabling the original Home first, uninstalling the old version, installing v1.1.0, choosing the startup app again, and reassigning the Home role. Use a persistent private signing keystore for seamless future upgrades.

## Features

- Select from installed TV apps and sideloaded apps that provide a launcher activity.
- Search by app name or package name, refresh list, and save selection persistently.
- One automatic launch per boot **when this app is the system Home launcher**.
- Pressing **Home** later goes to this settings screen, not an endless relaunch loop.
- Start the selected app immediately with **Launch selected app**.
- Toggle auto-launch on or off, change target, or clear choice.
- Remote-first focus indicators and scrollable app list.
- Works without a server, without Google Play Services, without device admin or root.

## Build and release with GitHub (no local Android SDK required)

1. The complete source is hosted on the public `main` branch of `tbenitz/android-tv-auto-launcher`.
2. The initial push to `main` runs **Actions → Build and release Android TV APK**. After it builds successfully, a **v1.0.0 public GitHub Release is automatically created**, with `TVAutoLaunch.apk` and a SHA-256 checksum. Later pushes to main produce CI artifacts but do not silently replace a published version.
3. To publish a new version, update `versionCode`/`versionName` in `app/build.gradle` and push a version tag like `v1.0.1` in GitHub (or create a GitHub Release on that tag). Signed release builds use optional signing secrets; see below.
4. Open **Settings → Pages → Build and deployment → Deploy from a branch**, select `main` and `/(root)`, then Save. The public installation site will be `https://tbenitz.github.io/android-tv-auto-launcher/`.

**Direct public release URL (after the first tagged release succeeds):**
`https://github.com/tbenitz/android-tv-auto-launcher/releases/latest/download/TVAutoLaunch.apk`

## Sideload onto Android TV

**Without a computer:** Open the public website in the TV browser/Downloader (or open the release URL on a phone and use a file-transfer app to send the APK to your TV). Grant your file manager/Downloader permission to install unknown apps if Android asks, install, then open **TV Auto Launch** once.

**Using ADB from a computer:**

```bash
adb connect TV_IP_ADDRESS:5555
adb install -r TVAutoLaunch.apk
```

With Android 11+ wireless debugging, pairing may be required and the address/port may differ. Developer options → Wireless debugging shows the details. On older Android TV versions, enable USB/Network debugging.

## Enable automatic startup

1. Open TV Auto Launch from Apps. Choose the target app. Leave Auto-launch set to **ON**.
2. Select **Set as default Home**. If the TV shows a picker, choose **TV Auto Launch → Always**. Restart the device to test.
3. If the TV refuses the Home picker, you can **optionally** use Android Debug Bridge (ADB), subject to your TV's firmware restrictions:

```bash
adb shell cmd package set-home-activity io.github.tbenitz.tvautolaunch/.MainActivity
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

**Important:** Some certified Google TV/Android TV devices force their manufacturer/Google Home UI even after this command. This is a device/firmware limitation, not something an ordinary APK can bypass. The app deliberately does not abuse Accessibility or overlays, and it does not disable system packages automatically. Do **not** blindly disable TV launcher or setup-wizard packages; this can leave you without a usable Home screen. If a device cannot set this app as its default Home, boot autostart is not guaranteed.

## Mi Box / Mi Box S: Home chooser doesn't open

The app **already declares itself as a HOME launcher**. Some Mi Box firmware ignores the default Home picker; modifying the APK manifest or a system file will not solve that firmware restriction. Version 1.0.1 adds a **Mi Box / Home setup help** button to the app.

1. Install TV Auto Launch, open it from Apps, choose your startup target, and leave Auto-launch ON.
2. On the Mi Box go to **Settings → Device Preferences → About → Build**, press OK seven times, and enable the available debugging method under **Developer options**.
3. Find the TV IP address in Network settings. From a computer or Android phone with an ADB client on the same network, connect and assign the launcher:

\`\`\`bash
adb connect TV_IP:5555
adb shell cmd package set-home-activity io.github.tbenitz.tvautolaunch/.MainActivity
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
\`\`\`

Port \`5555\` is typical only for network ADB; modern Wireless debugging may require a pairing code and a different port. Press Home to test and reboot to verify automatic startup. Some Xiaomi firmware ignores the assigned Home even when the command reports success.

**Advanced fallback, only if the Mi Box is still forcing its original launcher:** First confirm its package is exactly \`com.google.android.tvlauncher\`, that TV Auto Launch opens from Apps, and that you can recover using ADB. Then disable **only** that confirmed competing launcher:

\`\`\`bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
\`\`\`

**Recovery (save this command before changing anything):**

\`\`\`bash
adb shell pm enable --user 0 com.google.android.tvlauncher
\`\`\`

Google TV devices may have a different original launcher such as \`com.google.android.apps.tv.launcherx\`. Don't blindly disable multiple packages, uninstall the original Home app, or disable any setup/system service. Losing Home can require ADB recovery. If you prefer an interface, [TV Box Tools](https://github.com/smartago/tvboxtools) can manage Android TV Home selection using ADB. No root is necessary.

**APK upgrade:** Version 1.0.0 was debug-signed in CI. If production signing secrets have not been configured, 1.0.1 is signed with a different temporary key and Android will refuse an in-place upgrade. Restore a working original Home if necessary, uninstall the old APK, install the new APK and reselect your startup target.

## Restore your original Home launcher

- Open Android settings (or TV Auto Launch → Android TV settings), search for default apps/Home, then choose your original launcher.
- If your TV supports ADB, inspect Home handlers with:

```bash
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

- If you *manually* disabled a system launcher, re-enable **the exact package you disabled** (examples below only apply to devices with those packages):

```bash
adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell pm enable --user 0 com.google.android.tvlauncher
```

- Never uninstall TV Auto Launch while it is the only enabled Home launcher. Restore a working Home app first.

## Stable release signing (recommended)

GitHub Actions can publish a public release automatically using a **persistent private keystore** stored only as GitHub Actions secrets. Never commit the keystore or its password to the public repository.

Generate once on a trusted computer with Java `keytool`:

```bash
keytool -genkeypair -v -keystore tv-auto-launch-release.jks -alias tv-auto-launch -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 tv-auto-launch-release.jks
```

On systems without GNU `base64 -w0`, use a base64 tool that emits one line. Add these repository **Settings → Secrets and variables → Actions → New repository secret**:

- `SIGNING_KEYSTORE_BASE64`: base64-encoded contents of the `.jks` file.
- `SIGNING_STORE_PASSWORD`: keystore password.
- `SIGNING_KEY_ALIAS`: `tv-auto-launch` (or your chosen alias).
- `SIGNING_KEY_PASSWORD`: key password.

The automatic first v1.0.0 release uses CI debug signing unless you create a separate stable-signing release with the four secrets. For each newer `v*` tag, workflow creates a production-signed APK **only if all four secrets exist**. Otherwise it publishes a debug-signed APK whose signature changes across CI runs. In that fallback, installing future versions over old ones may fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; uninstall first (settings are then lost).

**Keep a secure backup of your keystore and passwords**, since losing them prevents seamless updates for that package name.

## Technical notes

- Java and standard Android APIs, no libraries or runtime network calls.
- `compileSdk`/`targetSdk`: 35; `minSdk`: 23 (Android 6.0+).
- APK is architecture-independent (no native libraries).
- `RECEIVE_BOOT_COMPLETED` is used to mark a pending boot. No background activity launch is attempted by the receiver (restricted on modern Android).
- Uses `<queries>` for `LEANBACK_LAUNCHER` and `LAUNCHER` rather than broad `QUERY_ALL_PACKAGES`.
- Tracks system boot count to avoid opening the selected app repeatedly when Home is pressed later.
- Selecting the target mid-boot arms it for the **next** startup; use **Launch selected app** to test right away.
- App list includes only packages with a discoverable launcher activity; hidden system apps are intentionally not exposed.

## Build locally (optional)

Install JDK 17, Android SDK 35, build-tools 35.0.0, and Gradle 8.9, then run:

```bash
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Or open this folder in Android Studio and configure Gradle 8.9. This repo uses a versioned build script rather than committing an external Gradle wrapper JAR.

## Privacy

No INTERNET permission. No analytics, tracking, cloud APIs, accessibility hooks, or background network calls. The selected package and preferences are stored only on the TV in private `SharedPreferences`.

## License
MIT.
