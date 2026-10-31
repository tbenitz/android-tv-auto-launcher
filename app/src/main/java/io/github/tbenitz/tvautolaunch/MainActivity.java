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
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
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

/**
 * TV Auto Launch: an offline, remote-first Android TV Home replacement.
 * Startup is launched from the foreground HOME Activity, never a boot background receiver.
 */
public final class MainActivity extends Activity {
    private static final int WHITE = 0xfff4f8fc;
    private static final int MUTED = 0xffa7b8ca;
    private static final int AQUA = 0xff6fe5df;
    private static final int GOLD = 0xffffd18e;
    private static final int SKY = 0xff91baff;

    private SharedPreferences prefs;
    private PackageManager pm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, AppEntry> installed = new LinkedHashMap<>();
    private final List<String> favoritePackages = new ArrayList<>();
    private final List<AppEntry> favoriteEntries = new ArrayList<>();
    private GridView favoritesGrid;
    private FavoritesAdapter favoritesAdapter;
    private TextView startupName, startupDescription, clockView, favoriteCount;
    private ImageView startupIcon;
    private View rootView;
    private boolean bootLaunchInProgress;
    private boolean leftForBootTarget;
    private boolean startedBootTarget;
    private Runnable delayedLaunch;
    private final Runnable clockTask = new Runnable() {
        @Override public void run() {
            refreshClock();
            handler.postDelayed(this, 60000);
        }
    };

    private int background() {
        return prefs.getString("theme", "ocean").equals("charcoal") ? 0xff101217 : 0xff081526;
    }
    private int surface() {
        return prefs.getString("theme", "ocean").equals("charcoal") ? 0xff22262e : 0xff122b43;
    }
    private int surfaceAlt() {
        return prefs.getString("theme", "ocean").equals("charcoal") ? 0xff303642 : 0xff1d3b56;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
        pm = getPackageManager();
        getWindow().setBackgroundDrawable(new ColorDrawable(background()));
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        refreshInstalledApps();
        loadFavorites();
        if (!startAtBootIfNeeded(getIntent())) {
            showHome();
        }
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Returning Home cancels any queued startup and never re-launches the target.
        if (delayedLaunch != null) {
            handler.removeCallbacks(delayedLaunch);
            delayedLaunch = null;
        }
        if (bootLaunchInProgress) {
            bootLaunchInProgress = false;
            leftForBootTarget = false;
            startedBootTarget = false;
        }
        if (rootView == null) showHome();
        else {
            refreshInstalledApps();
            syncFavorites();
            updateHome();
            if (favoritesGrid != null) favoritesGrid.requestFocus();
        }
    }

    @Override public void onResume() {
        super.onResume();
        // The startup Activity intentionally has no layout until the target returns.
        if (bootLaunchInProgress && leftForBootTarget) {
            bootLaunchInProgress = false;
            leftForBootTarget = false;
            startedBootTarget = false;
            showHome();
        } else if (!bootLaunchInProgress && rootView != null) {
            refreshInstalledApps();
            syncFavorites();
            updateHome();
        }
        handler.removeCallbacks(clockTask);
        if (rootView != null) handler.post(clockTask);
    }

    @Override public void onPause() {
        if (bootLaunchInProgress && startedBootTarget) leftForBootTarget = true;
        handler.removeCallbacks(clockTask);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (delayedLaunch != null) handler.removeCallbacks(delayedLaunch);
        handler.removeCallbacks(clockTask);
        super.onDestroy();
    }

    private boolean startAtBootIfNeeded(Intent intent) {
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_HOME)) return false;
        if (!prefs.getBoolean("enabled", true)) return false;
        String pkg = prefs.getString("chosen_package", "");
        if (pkg.isEmpty()) return false;
        int currentBoot = BootReceiver.getBootCount(this);
        int previousBoot = prefs.getInt("last_auto_launch_boot", -1);
        boolean pending = prefs.getBoolean("pending_boot_launch", false);
        if (!pending && (currentBoot < 0 || currentBoot == previousBoot)) return false;
        // This must be committed before opening the target so Home/Back never loop.
        prefs.edit().putInt("last_auto_launch_boot", currentBoot)
                .putBoolean("pending_boot_launch", false).commit();
        bootLaunchInProgress = true;
        int delayMs = prefs.getInt("boot_delay", 0);
        if (delayMs > 0) {
            delayedLaunch = () -> {
                delayedLaunch = null;
                launchBootTarget();
            };
            handler.postDelayed(delayedLaunch, delayMs);
        } else {
            launchBootTarget();
        }
        // No Home content or grid has been drawn yet: avoid the old 350ms UI flash.
        return bootLaunchInProgress;
    }

    private void launchBootTarget() {
        if (!bootLaunchInProgress) return;
        startedBootTarget = launchPackage(prefs.getString("chosen_package", ""),
                prefs.getString("chosen_class", ""), false);
        if (!startedBootTarget) {
            bootLaunchInProgress = false;
            showHome();
        }
    }

    private void showHome() {
        int oldSelection = favoritesGrid == null ? 0 : favoritesGrid.getSelectedItemPosition();
        LinearLayout root = vertical();
        root.setPadding(dp(38), dp(24), dp(38), dp(20));
        root.setBackgroundColor(background());
        root.setFocusableInTouchMode(true);
        rootView = root;
        setContentView(root);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(55)));
        LinearLayout titleBox = vertical();
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        titleBox.addView(text("TV AUTO LAUNCH", 13, AQUA, true));
        titleBox.addView(text("Home", 27, WHITE, true));
        clockView = text("", 16, MUTED, true);
        top.addView(clockView, params(-2, -2, 0, 0, 18, 0));
        addButton(top, "＋  Add apps", v -> showAppBrowser(), false);
        addButton(top, "⚙  Settings", v -> showSettings(), false);
        refreshClock();

        LinearLayout hero = horizontal();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(12), dp(16), dp(12));
        hero.setBackground(panel(surface(), 19, 0xff345873, 1));
        root.addView(hero, params(-1, dp(114), 0, 20, 0, 22));
        startupIcon = new ImageView(this);
        startupIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(startupIcon, params(dp(56), dp(56), 0, 0, 22, 0));
        LinearLayout heroText = vertical();
        heroText.addView(text("STARTS WHEN TV RESTARTS", 11, AQUA, true));
        startupName = text("Choose a startup app", 25, WHITE, true);
        heroText.addView(startupName, params(-1, -2, 0, 4, 0, 2));
        startupDescription = text("Set your default app in Settings", 12, MUTED, false);
        heroText.addView(startupDescription);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(hero, "▶  Open", v -> launchChosen(), false);

        LinearLayout sectionHead = horizontal();
        sectionHead.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(sectionHead, params(-1, dp(49), 0, 0, 0, 12));
        TextView heading = text("Your favorites", 24, WHITE, true);
        sectionHead.addView(heading);
        favoriteCount = text("", 13, MUTED, false);
        sectionHead.addView(favoriteCount, params(-2, -2, 16, 5, 0, 0));
        View space = new View(this);
        sectionHead.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
        addButton(sectionHead, "All apps", v -> showAppBrowser(), false);

        android.widget.FrameLayout content = new android.widget.FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        favoritesGrid = new GridView(this);
        favoritesGrid.setNumColumns(prefs.getInt("grid_columns", 5));
        favoritesGrid.setHorizontalSpacing(dp(15));
        favoritesGrid.setVerticalSpacing(dp(14));
        favoritesGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        favoritesGrid.setVerticalScrollBarEnabled(false);
        favoritesGrid.setClipToPadding(false);
        favoritesGrid.setPadding(dp(4), dp(5), dp(4), dp(12));
        favoritesGrid.setSelector(focusRing());
        favoritesGrid.setDrawSelectorOnTop(true);
        favoritesGrid.setCacheColorHint(Color.TRANSPARENT);
        favoritesGrid.setBackgroundColor(Color.TRANSPARENT);
        favoritesAdapter = new FavoritesAdapter();
        favoritesGrid.setAdapter(favoritesAdapter);
        favoritesGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < favoriteEntries.size()) {
                AppEntry e = favoriteEntries.get(position);
                launchPackage(e.pkg, e.cls, true);
            }
        });
        favoritesGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < favoriteEntries.size()) {
                showFavoriteOptions(favoriteEntries.get(position));
                return true;
            }
            return false;
        });
        content.addView(favoritesGrid, new android.widget.FrameLayout.LayoutParams(-1, -1));
        TextView empty = text("No favorites yet.\nChoose  + Add apps  to pin your first app.", 19, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1));
        favoritesGrid.setEmptyView(empty);

        TextView hint = text("OK  Open app     •     Hold OK  Manage favorite     •     Home  Return here     •     Settings  Configure startup",
                12, MUTED, false);
        root.addView(hint, params(-1, -2, 0, 14, 0, 0));
        syncFavorites();
        updateHome();
        if (!favoriteEntries.isEmpty()) {
            favoritesGrid.requestFocus();
            favoritesGrid.setSelection(Math.max(0, Math.min(oldSelection, favoriteEntries.size() - 1)));
        }
    }

    private void refreshClock() {
        if (clockView == null) return;
        boolean show = prefs.getBoolean("show_clock", true);
        clockView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) clockView.setText(DateFormat.format("EEE  •  h:mm a", new Date()));
    }

    private void refreshInstalledApps() {
        Map<String, AppEntry> found = new LinkedHashMap<>();
        collectApps(Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        collectApps(Intent.CATEGORY_LAUNCHER, found);
        installed.clear();
        installed.putAll(found);
    }

    private void collectApps(String category, Map<String, AppEntry> found) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        try {
            List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
            for (ResolveInfo info : results) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || found.containsKey(pkg)) continue;
                String name = String.valueOf(info.loadLabel(pm));
                Drawable icon = null;
                try { icon = info.loadIcon(pm); } catch (RuntimeException ignored) { }
                found.put(pkg, new AppEntry(name, pkg, info.activityInfo.name, icon));
            }
        } catch (RuntimeException ignored) { }
    }

    private void loadFavorites() {
        favoritePackages.clear();
        String encoded = prefs.getString("favorites", "");
        if (!encoded.isEmpty()) {
            for (String pkg : encoded.split("\\|")) {
                if (!pkg.isEmpty() && !favoritePackages.contains(pkg)) favoritePackages.add(pkg);
            }
        }
        // Migrate v1.0: keep the existing selected startup app visibly pinned.
        if (!prefs.contains("favorites")) {
            String pkg = prefs.getString("chosen_package", "");
            if (!pkg.isEmpty()) favoritePackages.add(pkg);
            saveFavorites();
        }
        syncFavorites();
    }

    private void saveFavorites() {
        prefs.edit().putString("favorites", android.text.TextUtils.join("|", favoritePackages)).apply();
        syncFavorites();
    }

    private void syncFavorites() {
        favoriteEntries.clear();
        for (String pkg : favoritePackages) {
            AppEntry app = installed.get(pkg);
            if (app != null) favoriteEntries.add(app);
        }
        if (favoritesAdapter != null) favoritesAdapter.notifyDataSetChanged();
        if (favoriteCount != null) favoriteCount.setText(favoriteEntries.size() + " apps  •  scroll for more");
    }

    private void updateHome() {
        if (startupName == null) return;
        String chosenPkg = prefs.getString("chosen_package", "");
        AppEntry chosen = installed.get(chosenPkg);
        startupName.setText(chosenPkg.isEmpty() ? "Choose a startup app"
                : chosen != null ? chosen.name : prefs.getString("chosen_label", chosenPkg));
        startupDescription.setText(chosenPkg.isEmpty() ? "Choose any installed app in Settings"
                : prefs.getBoolean("enabled", true) ? "Auto-launch on  •  Home returns to favorites"
                : "Auto-launch paused  •  Home returns to favorites");
        if (startupIcon != null) {
            startupIcon.setImageDrawable(chosen != null ? chosen.icon : getDrawable(android.R.drawable.sym_def_app_icon));
        }
        if (favoritesAdapter != null) favoritesAdapter.notifyDataSetChanged();
        refreshClock();
    }

    private void pin(AppEntry entry) {
        if (!favoritePackages.contains(entry.pkg)) favoritePackages.add(entry.pkg);
        saveFavorites();
    }

    private void setStartup(AppEntry entry) {
        int boot = BootReceiver.getBootCount(this);
        prefs.edit().putString("chosen_package", entry.pkg)
                .putString("chosen_class", entry.cls)
                .putString("chosen_label", entry.name)
                .putInt("last_auto_launch_boot", boot)
                .putBoolean("pending_boot_launch", false).apply();
        pin(entry);
        updateHome();
        Toast.makeText(this, entry.name + " starts on the next TV boot", Toast.LENGTH_SHORT).show();
    }

    private void showFavoriteOptions(AppEntry entry) {
        String[] choices = {"▶  Open", "★  Make startup app", "←  Move left",
                "→  Move right", "✕  Remove favorite", "ⓘ  Android app info"};
        new AlertDialog.Builder(this).setTitle(entry.name).setItems(choices, (dialog, pos) -> {
            switch (pos) {
                case 0: launchPackage(entry.pkg, entry.cls, true); break;
                case 1: setStartup(entry); break;
                case 2: moveFavorite(entry.pkg, -1); break;
                case 3: moveFavorite(entry.pkg, 1); break;
                case 4: favoritePackages.remove(entry.pkg); saveFavorites(); break;
                case 5: openAppInfo(entry.pkg); break;
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void moveFavorite(String pkg, int delta) {
        int from = favoritePackages.indexOf(pkg);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= favoritePackages.size()) return;
        Collections.swap(favoritePackages, from, to);
        saveFavorites();
        if (favoritesGrid != null) {
            favoritesGrid.requestFocus();
            favoritesGrid.setSelection(to);
        }
    }

    private void showAppBrowser() {
        refreshInstalledApps();
        List<AppEntry> options = new ArrayList<>(installed.values());
        Collections.sort(options, (a, b) -> a.name.compareToIgnoreCase(b.name));
        LinearLayout layout = vertical();
        layout.setPadding(dp(16), dp(9), dp(16), dp(8));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setHint("Search installed apps…");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(search, params(-1, dp(50), 0, 0, 0, 12));
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        List<AppEntry> shown = new ArrayList<>(options);
        ArrayAdapter<String> names = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override public View getView(int position, View reused, ViewGroup parent) {
                LinearLayout item = horizontal();
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(12), dp(7), dp(12), dp(7));
                AppEntry app = shown.get(position);
                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageDrawable(app.icon);
                item.addView(icon, params(dp(39), dp(39), 0, 0, 15, 0));
                LinearLayout textGroup = vertical();
                TextView label = text(app.name + (favoritePackages.contains(app.pkg) ? "  ★" : ""), 17, WHITE, true);
                textGroup.addView(label);
                textGroup.addView(text(app.pkg, 11, MUTED, false));
                item.addView(textGroup);
                item.setBackgroundColor(surface());
                return item;
            }
            @Override public int getCount() { return shown.size(); }
            @Override public String getItem(int position) { return shown.get(position).name; }
        };
        list.setAdapter(names);
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                shown.clear();
                String q = s.toString().toLowerCase(Locale.ROOT).trim();
                for (AppEntry entry : options) {
                    if (entry.name.toLowerCase(Locale.ROOT).contains(q)
                            || entry.pkg.toLowerCase(Locale.ROOT).contains(q)) shown.add(entry);
                }
                names.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("All installed apps")
                .setView(layout).setNegativeButton("Close", null).create();
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos >= shown.size()) return;
            AppEntry app = shown.get(pos);
            dlg.dismiss();
            showAppActions(app);
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (pos >= shown.size()) return false;
            AppEntry app = shown.get(pos);
            dlg.dismiss();
            pin(app);
            Toast.makeText(this, app.name + " added to favorites", Toast.LENGTH_SHORT).show();
            return true;
        });
        dlg.setOnShowListener(v -> {
            dlg.getWindow().setLayout(dp(Math.min(730, Math.max(450, getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density - 120))), dp(510));
            list.requestFocus();
        });
        dlg.show();
    }

    private void showAppActions(AppEntry entry) {
        List<String> choices = new ArrayList<>();
        choices.add("▶  Open app");
        choices.add(favoritePackages.contains(entry.pkg) ? "✕  Remove from favorites" : "＋  Add to favorites");
        choices.add("★  Make startup app");
        choices.add("ⓘ  Android app info");
        new AlertDialog.Builder(this).setTitle(entry.name)
                .setItems(choices.toArray(new String[0]), (d, which) -> {
                    if (which == 0) launchPackage(entry.pkg, entry.cls, true);
                    if (which == 1) {
                        if (favoritePackages.contains(entry.pkg)) favoritePackages.remove(entry.pkg);
                        else favoritePackages.add(entry.pkg);
                        saveFavorites();
                    }
                    if (which == 2) setStartup(entry);
                    if (which == 3) openAppInfo(entry.pkg);
                })
                .setNegativeButton("Back to Home", null).show();
    }

    private void showSettings() {
        String target = prefs.getString("chosen_label", "Not selected");
        String[] choices = {
                "Auto-launch after restart: " + (prefs.getBoolean("enabled", true) ? "ON" : "OFF"),
                "Startup app: " + (target.isEmpty() ? "Not selected" : target),
                "Startup delay: " + (prefs.getInt("boot_delay", 0) / 1000) + " seconds",
                "Favorites grid: " + prefs.getInt("grid_columns", 5) + " columns",
                "Show clock: " + (prefs.getBoolean("show_clock", true) ? "ON" : "OFF"),
                "Theme: " + (prefs.getString("theme", "ocean").equals("charcoal") ? "Charcoal" : "Ocean"),
                "Manage / add apps",
                "Set this as the default Home launcher",
                "Mi Box Home setup instructions",
                "Android TV system settings",
                "Clear startup selection"
        };
        new AlertDialog.Builder(this).setTitle("TV Auto Launch  •  Settings")
                .setItems(choices, (d, which) -> {
                    switch (which) {
                        case 0: prefs.edit().putBoolean("enabled", !prefs.getBoolean("enabled", true)).apply(); updateHome(); showSettings(); break;
                        case 1: showAppBrowser(); break;
                        case 2: chooseDelay(); break;
                        case 3: chooseColumns(); break;
                        case 4: prefs.edit().putBoolean("show_clock", !prefs.getBoolean("show_clock", true)).apply(); updateHome(); showSettings(); break;
                        case 5:
                            prefs.edit().putString("theme", prefs.getString("theme", "ocean").equals("charcoal") ? "ocean" : "charcoal").apply();
                            showHome(); showSettings(); break;
                        case 6: showAppBrowser(); break;
                        case 7: requestHomeRole(); break;
                        case 8: showHomeSetup(); break;
                        case 9: openSystemSettings(); break;
                        case 10:
                            new AlertDialog.Builder(this).setTitle("Clear startup app?")
                                    .setMessage("Favorites will stay. The TV will open this Home screen on restart.")
                                    .setPositiveButton("Clear", (confirm, n) -> {
                                        prefs.edit().remove("chosen_package").remove("chosen_class")
                                                .remove("chosen_label").putBoolean("pending_boot_launch", false).apply();
                                        updateHome();
                                    }).setNegativeButton("Cancel", null).show();
                            break;
                    }
                })
                .setNegativeButton("Done", null).show();
    }

    private void chooseDelay() {
        int[] delays = {0, 1000, 3000, 5000};
        String[] names = {"Immediate (recommended, least visible flash)", "1 second", "3 seconds", "5 seconds"};
        new AlertDialog.Builder(this).setTitle("Startup delay")
                .setSingleChoiceItems(names, indexOf(delays, prefs.getInt("boot_delay", 0)), (dialog, which) -> {
                    prefs.edit().putInt("boot_delay", delays[which]).apply();
                    dialog.dismiss();
                    showSettings();
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseColumns() {
        int[] columns = {3, 4, 5, 6};
        String[] names = {"3  •  Extra large", "4  •  Large", "5  •  Balanced", "6  •  Compact"};
        new AlertDialog.Builder(this).setTitle("Favorites grid size")
                .setSingleChoiceItems(names, indexOf(columns, prefs.getInt("grid_columns", 5)), (dialog, which) -> {
                    prefs.edit().putInt("grid_columns", columns[which]).apply();
                    dialog.dismiss();
                    showHome();
                    showSettings();
                }).setNegativeButton("Cancel", null).show();
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }

    private boolean launchPackage(String pkg, String cls, boolean showErrors) {
        if (pkg.isEmpty()) {
            if (showErrors) Toast.makeText(this, "Choose an app first", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null && !cls.isEmpty()) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setComponent(new ComponentName(pkg, cls));
            }
            if (launch == null) throw new ActivityNotFoundException("No launchable activity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            // No 350ms wait, no Home animation when jumping directly to startup target.
            if (Build.VERSION.SDK_INT >= 34) {
                overridePendingTransition(0, 0);
            } else {
                overridePendingTransition(0, 0);
            }
            return true;
        } catch (RuntimeException ex) {
            if (showErrors) Toast.makeText(this, "Could not start that app. Try Refresh from All apps.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void launchChosen() {
        launchPackage(prefs.getString("chosen_package", ""),
                prefs.getString("chosen_class", ""), true);
    }

    private void openAppInfo(String pkg) {
        try {
            Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + pkg));
            startActivity(info);
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "App info unavailable on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager role = getSystemService(RoleManager.class);
                if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)
                        && role.isRoleHeld(RoleManager.ROLE_HOME)) return true;
            } catch (RuntimeException ignored) { }
        }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && getPackageName().equals(resolved.activityInfo.packageName);
    }

    private void requestHomeRole() {
        if (isDefaultHome()) {
            Toast.makeText(this, "This is already your default Home", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager role = getSystemService(RoleManager.class);
                if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME), 100);
                    return;
                }
            } catch (RuntimeException ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (RuntimeException ex) {
            showHomeSetup();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && !isDefaultHome()) showHomeSetup();
    }

    private void showHomeSetup() {
        String command = "adb shell cmd package set-home-activity " + getPackageName() + "/.MainActivity";
        new AlertDialog.Builder(this).setTitle("Mi Box • Set default Home")
                .setMessage("Some Mi Box firmware hides the Home picker. This app already supports Home.\n\n"
                        + "1. Enable ADB debugging in your Mi Box Developer options.\n"
                        + "2. Connect with an Android phone ADB client on the same Wi-Fi.\n"
                        + "3. Execute:\n\n" + command + "\n\n"
                        + "If Mi Box ignores the change, see the GitHub website's recovery instructions. "
                        + "Do not disable system apps until you have working ADB recovery.")
                .setPositiveButton("Copy command", (d, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            ClipData.newPlainText("Home command", command));
                })
                .setNeutralButton("TV settings", (d, which) -> openSystemSettings())
                .setNegativeButton("Close", null).show();
    }

    private void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException ignored) {
            Toast.makeText(this, "System settings unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed() {
        // Never finish a Home replacement when the user presses Back at its root.
        if (isDefaultHome()) {
            if (favoritesGrid != null && !favoriteEntries.isEmpty()) favoritesGrid.requestFocus();
        } else {
            super.onBackPressed();
        }
    }

    private Button addButton(LinearLayout parent, String title, View.OnClickListener action, boolean prominent) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setFocusable(true);
        b.setBackground(buttonBackground(prominent));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(action);
        parent.addView(b, params(-2, dp(45), 0, 0, 12, 0));
        return b;
    }

    private StateListDrawable buttonBackground(boolean prominent) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, panel(AQUA, 12, WHITE, 2));
        s.addState(new int[]{}, panel(prominent ? surfaceAlt() : surface(), 12, 0xff35526d, 1));
        return s;
    }

    private StateListDrawable tileBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, panel(surfaceAlt(), 18, AQUA, 3));
        s.addState(new int[]{}, panel(surface(), 18, 0xff365269, 1));
        return s;
    }

    private StateListDrawable focusRing() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(3), AQUA);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape);
        states.addState(new int[]{android.R.attr.state_selected}, shape);
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private GradientDrawable panel(int background, int radius, int border, int borderDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(radius));
        if (borderDp > 0) bg.setStroke(dp(borderDp), border);
        return bg;
    }

    private LinearLayout vertical() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }
    private LinearLayout horizontal() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        return ll;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(s);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private LinearLayout.LayoutParams params(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w),
                h < 0 ? h : dp(h));
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private static class AppEntry {
        final String name, pkg, cls;
        final Drawable icon;
        AppEntry(String name, String pkg, String cls, Drawable icon) {
            this.name = name;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }

    private class FavoritesAdapter extends BaseAdapter {
        @Override public int getCount() { return favoriteEntries.size(); }
        @Override public Object getItem(int position) { return favoriteEntries.get(position); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppEntry app = favoriteEntries.get(position);
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setFocusable(false);
            item.setPadding(dp(14), dp(12), dp(14), dp(9));
            item.setBackground(tileBackground());
            ImageView icon = new ImageView(MainActivity.this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(app.icon);
            item.addView(icon, params(dp(65), dp(65), 0, 0, 0, 7));
            TextView label = text(app.name, 16, WHITE, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(label, params(-1, -2, 0, 0, 0, 6));
            TextView meta = text(app.pkg.equals(prefs.getString("chosen_package", ""))
                    ? "★  STARTUP" : "OK  •  Open", 11,
                    app.pkg.equals(prefs.getString("chosen_package", "")) ? GOLD : MUTED, true);
            meta.setGravity(Gravity.CENTER);
            item.addView(meta);
            item.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(145)));
            return item;
        }
    }
}
