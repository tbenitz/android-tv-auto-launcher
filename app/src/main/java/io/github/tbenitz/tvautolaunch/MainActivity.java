package io.github.tbenitz.tvautolaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Android TV HOME screen.
 *
 * All screens are normal, full-size Activity views. No zero-height dialog lists.
 * Installed packages/icons are scanned on a worker thread to avoid blocking the
 * remote / UI. Launching a startup target is opt-in and only happens once/boot.
 */
public final class MainActivity extends Activity {
    private static final int BG = 0xff071522;
    private static final int PANEL = 0xff142b40;
    private static final int PANEL2 = 0xff203f56;
    private static final int WHITE = 0xfff2f8ff;
    private static final int MUTED = 0xffb1c3d4;
    private static final int CYAN = 0xff76e2e0;
    private static final int GOLD = 0xffffd18a;
    private static final int ROW = 0xff182f45;
    private static final int HOME = 0, APPS = 1, SETTINGS = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final Map<String, Entry> allApps = new LinkedHashMap<>();
    private final ArrayList<String> favorites = new ArrayList<>();
    private final ArrayList<Entry> displayed = new ArrayList<>();
    private SharedPreferences prefs;
    private PackageManager pm;
    private int page = HOME;
    private GridView grid;
    private TileAdapter adapter;
    private TextView statusText;
    private EditText appSearch;
    private String currentSearch = "";
    private boolean scanRunning = false;
    private long lastScan = -100000;
    private boolean startingTarget = false;
    private boolean targetPaused = false;
    private Runnable pendingLaunch;
    private boolean alive = true;
    private TextView clockView;
    private final Runnable clockUpdater = new Runnable() {
        @Override public void run() {
            if (clockView != null) clockView.setText(DateFormat.format("EEE, MMM d   h:mm a", new Date()));
            handler.postDelayed(this, 30000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
        pm = getPackageManager();
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        loadFavorites();
        if (!startOnBoot(getIntent())) {
            showPage(HOME);
            scanApps();
        }
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // The HOME key must always open Home, not endlessly relaunch startup.
        if (pendingLaunch != null) handler.removeCallbacks(pendingLaunch);
        pendingLaunch = null;
        startingTarget = false;
        targetPaused = false;
        showPage(HOME);
        scanAppsIfStale();
    }

    @Override public void onResume() {
        super.onResume();
        if (startingTarget && targetPaused) {
            startingTarget = false;
            targetPaused = false;
            showPage(HOME);
            scanAppsIfStale();
        } else if (!startingTarget) {
            scanAppsIfStale();
        }
    }

    @Override public void onPause() {
        if (startingTarget) targetPaused = true;
        super.onPause();
    }

    @Override public void onDestroy() {
        alive = false;
        handler.removeCallbacks(clockUpdater);
        if (pendingLaunch != null) handler.removeCallbacks(pendingLaunch);
        scanner.shutdownNow();
        super.onDestroy();
    }

    private boolean startOnBoot(Intent intent) {
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_HOME)) return false;
        if (!prefs.getBoolean("enabled", false)) return false;
        final String pkg = prefs.getString("chosen_package", "");
        if (pkg.isEmpty()) return false;
        final int boot = BootReceiver.getBootCount(this);
        final boolean pending = prefs.getBoolean("pending_boot_launch", false);
        final int last = prefs.getInt("last_auto_launch_boot", -1);
        if (!pending && (boot < 0 || last == boot)) return false;

        // Crash-loop protection: do this before opening another Activity.
        prefs.edit().putInt("last_auto_launch_boot", boot)
                .putBoolean("pending_boot_launch", false).commit();
        startingTarget = true;
        int delay = prefs.getInt("boot_delay", 0);
        pendingLaunch = () -> {
            pendingLaunch = null;
            if (!startingTarget) return;
            if (!openApp(pkg, prefs.getString("chosen_class", ""), false)) {
                startingTarget = false;
                showPage(HOME);
                scanApps();
            }
        };
        // The Activity has no layout at this point. This reduces (but cannot
        // completely eliminate) TV firmware's initial HOME transition flash.
        handler.postDelayed(pendingLaunch, Math.max(0, delay));
        return true;
    }

    private void loadFavorites() {
        favorites.clear();
        String raw = prefs.getString("favorites", "");
        if (!raw.isEmpty()) for (String part : raw.split("\\|")) {
            if (!part.isEmpty() && !favorites.contains(part)) favorites.add(part);
        }
        if (!prefs.contains("favorites")) {
            String previous = prefs.getString("chosen_package", "");
            if (!previous.isEmpty()) favorites.add(previous);
            saveFavorites();
        }
    }

    private void saveFavorites() {
        prefs.edit().putString("favorites", android.text.TextUtils.join("|", favorites)).apply();
        refreshTiles();
    }

    private boolean isDark() {
        return prefs.getString("theme", "ocean").equals("charcoal");
    }

    private int backgroundColor() { return isDark() ? 0xff0f1219 : BG; }
    private int surfaceColor() { return isDark() ? 0xff252a34 : PANEL; }

    private void showPage(int selected) {
        page = selected;
        clockView = null;
        appSearch = null;
        grid = null;
        adapter = null;
        statusText = null;
        handler.removeCallbacks(clockUpdater);
        LinearLayout root = column();
        root.setPadding(dp(30), dp(18), dp(30), dp(18));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{backgroundColor(), isDark() ? 0xff171d27 : 0xff10324a, backgroundColor()}));
        setContentView(root);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(60)));
        LinearLayout wordmark = column();
        wordmark.addView(text("TV  •  AUTO LAUNCH", 11, CYAN, true));
        wordmark.addView(text(selected == HOME ? "Home" : selected == APPS ? "All apps" : "Settings",
                26, WHITE, true));
        header.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        if (selected == HOME && prefs.getBoolean("show_clock", true)) {
            clockView = text("", 13, MUTED, true);
            header.addView(clockView, margins(-2, -2, 0, 0, 16, 0));
            handler.post(clockUpdater);
        }
        navigation(header, "⌂  Home", HOME, selected);
        navigation(header, "▦  Apps", APPS, selected);
        navigation(header, "⚙  Settings", SETTINGS, selected);

        if (selected == HOME) buildHome(root);
        if (selected == APPS) buildApps(root);
        if (selected == SETTINGS) buildSettings(root);
    }

    private void navigation(LinearLayout parent, String title, int destination, int active) {
        Button button = button(title, () -> {
            if (destination == APPS) currentSearch = "";
            showPage(destination);
            if (destination == APPS) scanAppsIfStale();
        }, destination == active);
        parent.addView(button, margins(-2, 45, 0, 0, 8, 0));
    }

    private void buildHome(LinearLayout root) {
        LinearLayout hero = row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(12), dp(14), dp(12));
        hero.setBackground(panel(surfaceColor(), 16, 0xff3e6b83));
        root.addView(hero, margins(-1, 112, 0, 16, 0, 10));
        String selected = prefs.getString("chosen_package", "");
        Entry target = allApps.get(selected);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(target != null ? target.icon : getDrawable(android.R.drawable.sym_def_app_icon));
        hero.addView(icon, margins(58, 58, 0, 0, 17, 0));
        LinearLayout caption = column();
        caption.addView(text("YOUR STARTUP APP", 11, CYAN, true));
        caption.addView(text(selected.isEmpty() ? "Choose a startup app"
                : target != null ? target.label : prefs.getString("chosen_label", selected),
                22, WHITE, true));
        caption.addView(text(selected.isEmpty() ? "Start by choosing an app in All apps"
                : prefs.getBoolean("enabled", false)
                ? "Auto-start ON   •   Home returns here" : "Auto-start OFF   •   Enable it in Settings",
                12, MUTED, false));
        hero.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(button("▶  Open", () -> launchChosen(), false),
                margins(-2, 45, 5, 0, 8, 0));
        hero.addView(button("Change", () -> showPage(APPS), false),
                margins(-2, 45, 0, 0, 0, 0));

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading, margins(-1, 49, 0, 0, 0, 3));
        heading.addView(text("Your favorites", 23, WHITE, true));
        heading.addView(text("    D-pad to browse  •  OK for options", 12, MUTED, false));
        heading.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        heading.addView(button("+ Add apps", () -> showPage(APPS), false),
                margins(-2, 41, 0, 0, 0, 0));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        createGrid(content, 5, true);

        statusText = text("", 12, MUTED, false);
        root.addView(statusText, margins(-1, -2, 0, 8, 0, 0));
        updateStatus();
        if (!favorites.isEmpty() && grid != null) grid.requestFocus();
    }

    private void buildApps(LinearLayout root) {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, margins(-1, 53, 0, 12, 0, 11));
        bar.addView(text("Installed TV apps", 20, WHITE, true), margins(-2, -2, 0, 0, 22, 0));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setTextSize(16);
        search.setHint("Search installed apps");
        search.setText(currentSearch);
        search.setBackground(panel(surfaceColor(), 12, 0xff416b7c));
        search.setPadding(dp(15), 0, dp(15), 0);
        bar.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        appSearch = search;
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                currentSearch = s.toString();
                refreshTiles();
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        bar.addView(button("↻ Refresh", () -> scanApps(), false), margins(-2, 44, 14, 0, 0, 0));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        createGrid(content, 5, false);
        statusText = text("Select an app for Open, Favorite, or Set startup.  •  BACK returns Home",
                12, MUTED, false);
        root.addView(statusText, margins(-1, -2, 0, 9, 0, 0));
        if (grid != null) grid.requestFocus();
    }

    private void createGrid(FrameLayout frame, int defaultColumns, boolean favoritesOnly) {
        grid = new GridView(this);
        grid.setNumColumns(prefs.getInt("grid_columns", defaultColumns));
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(12));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(dp(4), dp(4), dp(4), dp(8));
        grid.setClipToPadding(false);
        grid.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        grid.setSelector(focusRing());
        grid.setDrawSelectorOnTop(true);
        grid.setVerticalScrollBarEnabled(true);
        grid.setCacheColorHint(Color.TRANSPARENT);
        adapter = new TileAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= displayed.size()) return;
            showAppOptions(displayed.get(position));
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= displayed.size()) return false;
            showAppOptions(displayed.get(position));
            return true;
        });
        TextView empty = text(favoritesOnly
                ? "No favorites yet.\nChoose + Add apps to select your first favorite."
                : "Loading installed apps…", 18, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        grid.setEmptyView(empty);
        refreshTiles();
    }

    private void refreshTiles() {
        displayed.clear();
        if (page == HOME) {
            for (String pkg : favorites) {
                Entry entry = allApps.get(pkg);
                if (entry != null) displayed.add(entry);
            }
        } else if (page == APPS) {
            String filter = currentSearch.toLowerCase(Locale.ROOT).trim();
            for (Entry entry : allApps.values()) {
                if (filter.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(filter)
                        || entry.pkg.toLowerCase(Locale.ROOT).contains(filter)) displayed.add(entry);
            }
            Collections.sort(displayed, (a, b) -> a.label.compareToIgnoreCase(b.label));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showAppOptions(Entry item) {
        if (!alive) return;
        boolean pinned = favorites.contains(item.pkg);
        String[] actions = {
                "▶  Open " + item.label,
                pinned ? "★  Remove from favorites" : "＋  Add to favorites",
                "★  Set as startup app",
                "←  Move left in favorites",
                "→  Move right in favorites",
                "ⓘ  Android app information"
        };
        new AlertDialog.Builder(this).setTitle(item.label)
                .setItems(actions, (dlg, which) -> {
                    switch (which) {
                        case 0: openApp(item.pkg, item.cls, true); break;
                        case 1:
                            if (pinned) favorites.remove(item.pkg);
                            else favorites.add(item.pkg);
                            saveFavorites();
                            updateStatus();
                            break;
                        case 2: chooseStartup(item); break;
                        case 3: moveFavorite(item.pkg, -1); break;
                        case 4: moveFavorite(item.pkg, 1); break;
                        case 5: openAppInfo(item.pkg); break;
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    private void moveFavorite(String pkg, int delta) {
        int pos = favorites.indexOf(pkg);
        int target = pos + delta;
        if (pos < 0 || target < 0 || target >= favorites.size()) {
            Toast.makeText(this, "Add this app to favorites first", Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.swap(favorites, pos, target);
        saveFavorites();
        if (grid != null && page == HOME) grid.setSelection(target);
    }

    private void chooseStartup(Entry item) {
        int boot = BootReceiver.getBootCount(this);
        prefs.edit().putString("chosen_package", item.pkg)
                .putString("chosen_class", item.cls)
                .putString("chosen_label", item.label)
                .putBoolean("enabled", true)
                .putInt("last_auto_launch_boot", boot)
                .putBoolean("pending_boot_launch", false).apply();
        if (!favorites.contains(item.pkg)) favorites.add(item.pkg);
        saveFavorites();
        Toast.makeText(this, item.label + " selected. Test it before changing Home.", Toast.LENGTH_LONG).show();
        showPage(HOME);
    }

    private void updateStatus() {
        if (statusText == null || page != HOME) return;
        statusText.setText(favorites.size() + " favorites    •    Press HOME to return here    •    "
                + (isDefaultHome() ? "Default Home active" : "Test mode: original Home still available"));
    }

    private void buildSettings(LinearLayout root) {
        TextView subtitle = text("Customize your TV, test app launching, and change Home only when ready.",
                14, MUTED, false);
        root.addView(subtitle, margins(-1, -2, 0, 15, 0, 12));
        String[] settings = {
                "Startup app        " + prefs.getString("chosen_label", "Not selected"),
                "Auto-launch on reboot        " + (prefs.getBoolean("enabled", false) ? "ON" : "OFF"),
                "Startup delay        " + prefs.getInt("boot_delay", 0) / 1000 + " seconds",
                "Favorites grid size        " + prefs.getInt("grid_columns", 5) + " columns",
                "Show clock        " + (prefs.getBoolean("show_clock", true) ? "ON" : "OFF"),
                "Color theme        " + (isDark() ? "Charcoal" : "Ocean blue"),
                "Manage apps and favorites",
                "Open Android TV system settings",
                "Check / set default Home",
                "Mi Box debugging and recovery instructions",
                "Clear startup app (keep favorites)"
        };
        ListView list = new ListView(this);
        list.setDividerHeight(dp(8));
        list.setDivider(null);
        list.setSelector(focusRing());
        list.setDrawSelectorOnTop(true);
        list.setVerticalScrollBarEnabled(true);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, settings) {
            @Override public View getView(int pos, View previous, ViewGroup parent) {
                TextView row = text(getItem(pos), 18, WHITE, false);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(19), dp(10), dp(17), dp(10));
                row.setBackground(panel(surfaceColor(), 11, 0xff3d5d73));
                row.setLayoutParams(new AbsListView.LayoutParams(-1, dp(57)));
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> settingsAction(position));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(text("BACK  Home    •    No account, ads or internet required", 12, MUTED, false));
        list.requestFocus();
    }

    private void settingsAction(int index) {
        switch (index) {
            case 0: showPage(APPS); break;
            case 1:
                prefs.edit().putBoolean("enabled", !prefs.getBoolean("enabled", false)).apply();
                showPage(SETTINGS); break;
            case 2:
                int[] delay = {0, 1000, 3000, 5000};
                chooseSetting("Delay before opening startup app",
                        new String[]{"Immediate (recommended)", "1 second", "3 seconds", "5 seconds"},
                        prefs.getInt("boot_delay", 0) / 1000 == 0 ? 0
                        : prefs.getInt("boot_delay", 0) == 1000 ? 1
                        : prefs.getInt("boot_delay", 0) == 3000 ? 2 : 3,
                        which -> prefs.edit().putInt("boot_delay", delay[which]).apply());
                break;
            case 3:
                int[] columns = {3, 4, 5, 6};
                chooseSetting("Favorites per row",
                        new String[]{"3  •  Extra large", "4  •  Large", "5  •  Balanced", "6  •  Compact"},
                        Math.max(0, Math.min(3, prefs.getInt("grid_columns", 5) - 3)),
                        which -> prefs.edit().putInt("grid_columns", columns[which]).apply());
                break;
            case 4:
                prefs.edit().putBoolean("show_clock", !prefs.getBoolean("show_clock", true)).apply();
                showPage(SETTINGS); break;
            case 5:
                prefs.edit().putString("theme", isDark() ? "ocean" : "charcoal").apply();
                showPage(SETTINGS); break;
            case 6: showPage(APPS); break;
            case 7:
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (RuntimeException error) {
                    Toast.makeText(this, "System settings unavailable", Toast.LENGTH_SHORT).show();
                }
                break;
            case 8: requestHomeRole(); break;
            case 9: showRecoveryHelp(); break;
            case 10:
                new AlertDialog.Builder(this).setTitle("Clear startup selection?")
                    .setMessage("Your favorites stay. The launcher will open normally after a restart.")
                    .setPositiveButton("Clear", (d, w) -> {
                        prefs.edit().remove("chosen_package").remove("chosen_class")
                                .remove("chosen_label").putBoolean("enabled", false)
                                .putBoolean("pending_boot_launch", false).apply();
                        showPage(SETTINGS);
                    }).setNegativeButton("Cancel", null).show();
                break;
        }
    }

    private interface ChoiceAction { void choose(int selected); }

    private void chooseSetting(String title, String[] choices, int selected, ChoiceAction callback) {
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    callback.choose(which);
                    dialog.dismiss();
                    showPage(SETTINGS);
                }).setNegativeButton("Cancel", null).show();
    }

    private void scanAppsIfStale() {
        if (SystemClock.elapsedRealtime() - lastScan > 10000) scanApps();
    }

    private void scanApps() {
        if (!alive || scanRunning) return;
        scanRunning = true;
        if (statusText != null && page == APPS) statusText.setText("Loading installed apps…");
        scanner.execute(() -> {
            Map<String, Entry> found = new LinkedHashMap<>();
            collectApps(found, Intent.CATEGORY_LEANBACK_LAUNCHER);
            collectApps(found, Intent.CATEGORY_LAUNCHER);
            handler.post(() -> {
                if (!alive) return;
                scanRunning = false;
                lastScan = SystemClock.elapsedRealtime();
                allApps.clear();
                allApps.putAll(found);
                refreshTiles();
                if (page == HOME) {
                    // Refresh the startup icon/label without disturbing focus.
                    showPage(HOME);
                } else if (page == APPS && statusText != null) {
                    statusText.setText(found.size() + " installed apps   •   OK to manage   •   BACK for Home");
                    if (grid != null) grid.requestFocus();
                }
            });
        });
    }

    private void collectApps(Map<String, Entry> found, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        try {
            List<ResolveInfo> matches = pm.queryIntentActivities(query, 0);
            for (ResolveInfo r : matches) {
                if (Thread.currentThread().isInterrupted()) return;
                if (r.activityInfo == null) continue;
                String pkg = r.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || found.containsKey(pkg)) continue;
                String label = pkg;
                Drawable icon = null;
                try {
                    CharSequence name = r.loadLabel(pm);
                    if (name != null) label = name.toString();
                    icon = r.loadIcon(pm);
                } catch (RuntimeException ignored) { }
                found.put(pkg, new Entry(pkg, r.activityInfo.name, label, icon));
            }
        } catch (RuntimeException ignored) { }
    }

    private void launchChosen() {
        if (!openApp(prefs.getString("chosen_package", ""),
                prefs.getString("chosen_class", ""), true)) {
            showPage(APPS);
            scanAppsIfStale();
        }
    }

    private boolean openApp(String pkg, String cls, boolean toastErrors) {
        if (pkg == null || pkg.isEmpty()) {
            if (toastErrors) Toast.makeText(this, "Choose an app first", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null && cls != null && !cls.isEmpty()) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setComponent(new ComponentName(pkg, cls));
            }
            if (launch == null) throw new ActivityNotFoundException("Not installed");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            overridePendingTransition(0, 0);
            return true;
        } catch (RuntimeException error) {
            if (toastErrors) Toast.makeText(this,
                    "Cannot open app. Try another or refresh All apps.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + pkg)));
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "App info is unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)
                        && rm.isRoleHeld(RoleManager.ROLE_HOME)) return true;
            } catch (RuntimeException ignored) { }
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && getPackageName().equals(info.activityInfo.packageName);
        } catch (RuntimeException ignored) { return false; }
    }

    private void requestHomeRole() {
        new AlertDialog.Builder(this).setTitle("Make this your TV Home?")
                .setMessage("Test All apps, favorites, and app launching first. "
                    + "Mi Box firmware may require a separate ADB Home change.\n\n"
                    + "Keep the original TV launcher enabled until you are sure this one works.")
                .setPositiveButton("Continue", (dlg, which) -> {
                    if (Build.VERSION.SDK_INT >= 29) {
                        try {
                            RoleManager rm = getSystemService(RoleManager.class);
                            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 100);
                                return;
                            }
                        } catch (RuntimeException ignored) { }
                    }
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (RuntimeException ignored) { showRecoveryHelp(); }
                }).setNegativeButton("Cancel", null).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && !isDefaultHome()) {
            Toast.makeText(this, "Home change not accepted. See Mi Box Help.", Toast.LENGTH_LONG).show();
        }
    }

    private void showRecoveryHelp() {
        final String app = getPackageName();
        final String restore = "pm enable --user 0 com.google.android.tvlauncher\n"
                + "pm disable-user --user 0 " + app + "\n"
                + "input keyevent 3";
        new AlertDialog.Builder(this).setTitle("Mi Box Home and recovery")
                .setMessage("Use an ADB client on your Android phone on the same Wi-Fi.\n\n"
                    + "Set Home from an ADB shell:\n"
                    + "cmd package set-home-activity " + app + "/.MainActivity\n\n"
                    + "If this launcher ever stops responding, FIRST re-enable your verified original "
                    + "Home package, THEN disable this app. Typical Mi Box recovery:\n\n"
                    + restore + "\n\nOnly use the first command when that stock package is actually installed.")
                .setPositiveButton("Copy recovery steps", (dialog, which) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("TV recovery", restore));
                }).setNegativeButton("Close", null).show();
    }

    @Override public void onBackPressed() {
        if (page != HOME) showPage(HOME);
        else if (grid != null && !displayed.isEmpty()) grid.requestFocus();
        // Never finish the Home Activity at root.
    }

    private Button button(String label, Runnable callback, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xff0b2530, WHITE}));
        b.setPadding(dp(13), 0, dp(13), 0);
        b.setBackground(buttonBackground(active));
        b.setOnClickListener(v -> callback.run());
        return b;
    }

    private StateListDrawable buttonBackground(boolean active) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, panel(CYAN, 11, WHITE));
        states.addState(new int[]{}, panel(active ? PANEL2 : surfaceColor(), 11, 0xff426278));
        return states;
    }

    private StateListDrawable focusRing() {
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(0x4447b5bd);
        selected.setCornerRadius(dp(15));
        selected.setStroke(dp(3), CYAN);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, selected);
        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private GradientDrawable panel(int fill, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        return result;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width), height < 0 ? height : dp(height));
        param.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return param;
    }

    private static final class Entry {
        final String pkg, cls, label;
        final Drawable icon;
        Entry(String pkg, String cls, String label, Drawable icon) {
            this.pkg = pkg; this.cls = cls; this.label = label; this.icon = icon;
        }
    }

    private final class TileAdapter extends BaseAdapter {
        @Override public int getCount() { return displayed.size(); }
        @Override public Object getItem(int position) { return displayed.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View old, ViewGroup parent) {
            Entry e = displayed.get(position);
            LinearLayout tile = column();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(9), dp(8), dp(9), dp(8));
            tile.setBackground(panel(surfaceColor(), 15, 0xff45647b));
            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(e.icon != null ? e.icon
                    : getDrawable(android.R.drawable.sym_def_app_icon));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon, margins(62, 62, 0, 0, 0, 8));
            TextView label = text(e.label, 15, WHITE, true);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(label, margins(-1, -2, 0, 0, 0, 5));
            boolean startup = e.pkg.equals(prefs.getString("chosen_package", ""));
            TextView note = text(startup ? "★ STARTUP" : favorites.contains(e.pkg) ? "★ FAVORITE" : "OK  OPTIONS",
                    10, startup ? GOLD : MUTED, true);
            note.setGravity(Gravity.CENTER);
            tile.addView(note);
            tile.setLayoutParams(new AbsListView.LayoutParams(-1, dp(139)));
            return tile;
        }
    }
}
