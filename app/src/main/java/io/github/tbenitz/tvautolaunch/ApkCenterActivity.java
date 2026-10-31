package io.github.tbenitz.tvautolaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remote-first APK center. No silent installs, third-party credentials or hidden
 * installer privileges. User approves each APK and Android controls installation.
 */
public final class ApkCenterActivity extends Activity {
    private static final int BG = 0xff091624, PANEL = 0xff1c3148, WHITE = 0xfff4f8ff;
    private static final int MUTED = 0xffa9c2d7, ACCENT = 0xff79dfde;
    private static final int PICK_APK = 504;
    private static final long MAX_APK_BYTES = 400L * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private EditText repoField, urlField;
    private TextView status;
    private Button repoButton, downloadButton, installButton;
    private Uri pendingInstall;
    private volatile boolean busy = false;
    private boolean alive = true;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout screen = column();
        screen.setPadding(dp(28), dp(18), dp(28), dp(18));
        screen.setBackgroundColor(BG);
        setContentView(screen);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label("APK CENTER", 27, WHITE, true);
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(55), 1));
        Button back = button("← Home", () -> finish());
        top.addView(back);
        screen.addView(top);
        screen.addView(label("Install apps from public GitHub releases, trusted HTTPS URLs, or local files. Android confirms each installation.", 14, MUTED, false));

        LinearLayout body = row();
        body.setPadding(0, dp(14), 0, 0);
        screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout left = column();
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout right = column();
        right.setPadding(dp(20), 0, 0, 0);
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1));

        left.addView(label("GITHUB RELEASES", 15, ACCENT, true));
        left.addView(label("Find APK assets from the newest releases.", 12, MUTED, false));
        LinearLayout repoRow = row();
        repoRow.setGravity(Gravity.CENTER_VERTICAL);
        repoField = field("owner/repository");
        repoRow.addView(repoField, new LinearLayout.LayoutParams(0, dp(50), 1));
        repoButton = button("Find APKs", this::findReleases);
        repoRow.addView(repoButton);
        left.addView(repoRow, margins(0, 6));

        left.addView(label("DIRECT APK DOWNLOAD", 15, ACCENT, true), margins(0, 23));
        left.addView(label("Paste a trusted HTTPS link to a standalone APK.", 12, MUTED, false));
        LinearLayout directRow = row();
        directRow.setGravity(Gravity.CENTER_VERTICAL);
        urlField = field("https://example.com/app.apk");
        directRow.addView(urlField, new LinearLayout.LayoutParams(0, dp(50), 1));
        downloadButton = button("Download", () ->
                confirmDownload(urlField.getText().toString().trim(), null, "Direct HTTPS APK"));
        directRow.addView(downloadButton);
        left.addView(directRow, margins(0, 6));
        left.addView(label("Downloads are limited to 400 MB and checked as APKs before installation.", 12, MUTED, false),
                margins(0, 12));

        right.addView(label("LOCAL AND ALTERNATIVE SOURCES", 15, ACCENT, true));
        LinearLayout quick = row();
        Button files = button("USB / Files", this::pickFile);
        quick.addView(files, new LinearLayout.LayoutParams(0, dp(49), 1));
        Button fdroid = button("Get F-Droid", () ->
                confirmDownload("https://f-droid.org/F-Droid.apk", null, "Official F-Droid installer"));
        quick.addView(fdroid, new LinearLayout.LayoutParams(0, dp(49), 1));
        right.addView(quick, margins(0, 7));
        right.addView(button("Allow unknown-app installation", this::openPermissionSettings),
                margins(0, 8));

        right.addView(label("SAVED GITHUB REPOSITORY", 13, ACCENT, true), margins(0, 13));
        String recent = getPreferences(MODE_PRIVATE).getString("saved_repo", "");
        Button recentButton = button(recent.isEmpty() ? "None yet" : recent, () -> {
            String r = getPreferences(MODE_PRIVATE).getString("saved_repo", "");
            if (!r.isEmpty()) { repoField.setText(r); findReleases(); }
        });
        recentButton.setEnabled(!recent.isEmpty());
        right.addView(recentButton, margins(0, 5));

        status = label("Select a source. Use only APKs from developers you trust.", 13, MUTED, false);
        status.setPadding(dp(11), dp(10), dp(11), dp(10));
        status.setBackground(panel(PANEL));
        right.addView(status, margins(0, 12));
        installButton = button("Install downloaded APK", this::installDownloaded);
        right.addView(installButton, margins(0, 7));
        installButton.setEnabled(false);
        files.requestFocus();
    }

    private void findReleases() {
        if (busy) return;
        String value = repoField.getText().toString().trim().replaceFirst("^https?://(www\\.)?github\\.com/", "");
        value = value.replaceFirst("\\.git$", "").replaceFirst("/+$", "");
        if (!value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            tell("Enter a GitHub repository like owner/repository."); return;
        }
        final String repo = value;
        busy(true, "Checking GitHub releases for " + repo + "…");
        worker.execute(() -> {
            try {
                String json = readText("https://api.github.com/repos/" + repo + "/releases?per_page=5", 1024 * 1024);
                JSONArray releases = new JSONArray(json);
                ArrayList<String> names = new ArrayList<>();
                ArrayList<String> links = new ArrayList<>();
                ArrayList<String> hashes = new ArrayList<>();
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    String tag = release.optString("tag_name", "release");
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) continue;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name", "");
                        String url = asset.optString("browser_download_url", "");
                        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk") || !url.startsWith("https://")) continue;
                        names.add(tag + "  •  " + name + "  (" + (asset.optLong("size", 0) / 1048576) + " MB)");
                        links.add(url);
                        hashes.add(asset.optString("digest", ""));
                    }
                }
                main.post(() -> {
                    if (!alive) return;
                    busy(false, names.isEmpty() ? "No APK release assets found in the five newest GitHub releases." : "Choose the APK appropriate for your device.");
                    if (names.isEmpty()) return;
                    getPreferences(MODE_PRIVATE).edit().putString("saved_repo", repo).apply();
                    new AlertDialog.Builder(this).setTitle("GitHub: " + repo)
                            .setItems(names.toArray(new String[0]), (d, index) ->
                                    confirmDownload(links.get(index), hashes.get(index), repo + " / " + names.get(index)))
                            .setNegativeButton("Cancel", null).show();
                });
            } catch (Exception e) {
                main.post(() -> { if (alive) busy(false, "GitHub check failed: " + readable(e)); });
            }
        });
    }

    private void confirmDownload(String link, String hash, String title) {
        if (busy) return;
        if (link == null || !link.startsWith("https://")) {
            tell("Only HTTPS downloads are supported."); return;
        }
        new AlertDialog.Builder(this).setTitle("Download APK?")
                .setMessage(title + "\n\nSource: " + link + "\n\nOnly continue if you trust this source. Android will ask you to approve installation.")
                .setPositiveButton("Download", (dialog, which) -> download(link, hash))
                .setNegativeButton("Cancel", null).show();
    }

    private void download(String link, String hash) {
        busy(true, "Downloading…");
        installButton.setEnabled(false);
        worker.execute(() -> {
            File folder = new File(getCacheDir(), "apk");
            if (!folder.exists() && !folder.mkdirs()) {
                main.post(() -> busy(false, "Could not prepare download directory")); return;
            }
            File temp = new File(folder, "current.part");
            File target = new File(folder, "current.apk");
            HttpURLConnection connection = null;
            try {
                if (temp.exists()) temp.delete();
                connection = connection(link);
                int length = connection.getContentLength();
                if (length > MAX_APK_BYTES) throw new Exception("APK is larger than 400 MB");
                long total = 0;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32768];
                    int n;
                    int lastPercent = -1;
                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_APK_BYTES) throw new Exception("Download exceeded 400 MB");
                        out.write(buffer, 0, n);
                        digest.update(buffer, 0, n);
                        int percent = length > 0 ? (int) (total * 100L / length) : -1;
                        if (percent >= 0 && percent / 5 != lastPercent / 5) {
                            lastPercent = percent;
                            final int progress = percent;
                            main.post(() -> { if (alive) tell("Downloading APK… " + progress + "%"); });
                        }
                    }
                }
                if (total < 1024) throw new Exception("Download is too small to be an APK");
                String computed = hex(digest.digest());
                if (hash != null && hash.toLowerCase(Locale.ROOT).startsWith("sha256:")
                        && !computed.equalsIgnoreCase(hash.substring(7))) {
                    throw new Exception("SHA-256 checksum mismatch. Installation blocked.");
                }
                PackageInfo info = getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(), 0);
                if (info == null || info.packageName == null) throw new Exception("Downloaded file is not a valid APK");
                String installed = "";
                try {
                    PackageInfo existing = getPackageManager().getPackageInfo(info.packageName, 0);
                    installed = "\nCurrently installed: " + existing.versionName;
                } catch (PackageManager.NameNotFoundException ignored) {}
                if (target.exists() && !target.delete()) throw new Exception("Unable to replace previous APK");
                if (!temp.renameTo(target)) throw new Exception("Unable to finalize downloaded APK");
                final String message = "Package: " + info.packageName + "\nVersion: " + info.versionName + installed
                        + "\nSize: " + (total / 1048576) + " MB\nSHA-256: " + computed;
                main.post(() -> {
                    if (!alive) return;
                    busy(false, "APK downloaded and checked. Select Install to continue.");
                    pendingInstall = Uri.parse("content://" + getPackageName() + ".apks/current.apk");
                    installButton.setEnabled(true);
                    new AlertDialog.Builder(this).setTitle("Ready to install")
                            .setMessage(message + "\n\nAndroid will request your confirmation.")
                            .setPositiveButton("Install", (d, w) -> installDownloaded())
                            .setNegativeButton("Later", null).show();
                });
            } catch (Exception error) {
                temp.delete();
                main.post(() -> { if (alive) busy(false, "Download failed: " + readable(error)); });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static HttpURLConnection connection(String link) throws Exception {
        URL url = new URL(link);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Exception("HTTPS required");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "OrbitTV-APK-Center");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200 || !"https".equalsIgnoreCase(c.getURL().getProtocol())) {
            c.disconnect();
            throw new Exception("Server returned HTTP " + code);
        }
        return c;
    }

    private static String readText(String url, int limit) throws Exception {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new Exception("GitHub response too large");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder();
        for (byte b : data) s.append(String.format(Locale.US, "%02x", b & 0xff));
        return s.toString();
    }

    private void pickFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_APK);
        } catch (RuntimeException error) { tell("No compatible file picker installed on this TV."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_APK && result == RESULT_OK && data != null && data.getData() != null) {
            pendingInstall = data.getData();
            installButton.setEnabled(true);
            new AlertDialog.Builder(this).setTitle("Install selected file?")
                    .setMessage("Only install a trusted APK. Android will verify and request your approval.")
                    .setPositiveButton("Install", (d, w) -> installDownloaded())
                    .setNegativeButton("Cancel", null).show();
        }
    }

    private void installDownloaded() {
        if (pendingInstall == null) {
            tell("Download or select an APK first."); return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("Permission required")
                    .setMessage("Allow Orbit TV to request app installations, then return and choose Install again. This does not enable silent installation.")
                    .setPositiveButton("Open Android settings", (d, w) -> openPermissionSettings())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setDataAndType(pendingInstall, "application/vnd.android.package-archive");
            install.setClipData(ClipData.newRawUri("APK", pendingInstall));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (RuntimeException error) { tell("No installer available: " + readable(error)); }
    }

    private void openPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } else {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        } catch (RuntimeException error) { tell("Open Android Settings > Security to allow app installs."); }
    }

    private void tell(String message) { if (status != null) status.setText(message); }
    private void busy(boolean value, String message) {
        busy = value;
        if (repoButton != null) repoButton.setEnabled(!value);
        if (downloadButton != null) downloadButton.setEnabled(!value);
        tell(message);
    }
    private static String readable(Exception e) {
        String detail = e.getMessage();
        return detail == null || detail.trim().isEmpty() ? e.getClass().getSimpleName() : detail;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(0); return l; }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setTextSize(15); e.setTextColor(WHITE);
        e.setHintTextColor(MUTED); e.setHint(hint);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(panel(PANEL));
        return e;
    }
    private Button button(String name, Runnable action) {
        Button b = new Button(this); b.setText(name); b.setTextSize(13);
        b.setAllCaps(false); b.setTextColor(WHITE); b.setBackground(panel(PANEL));
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focus) -> {
            b.setBackground(focus ? outlined(ACCENT) : panel(PANEL));
            b.setTextColor(focus ? BG : WHITE);
        });
        return b;
    }
    private GradientDrawable outlined(int color) {
        GradientDrawable d = panel(color); d.setStroke(dp(3), WHITE); return d;
    }
    private GradientDrawable panel(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(12)); d.setStroke(dp(1), 0xff355571); return d;
    }
    private LinearLayout.LayoutParams margins(int left, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), 0, 0);
        return p;
    }
    @Override protected void onDestroy() {
        alive = false;
        worker.shutdownNow();
        super.onDestroy();
    }
}
