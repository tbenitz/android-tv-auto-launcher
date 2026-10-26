package io.github.tbenitz.tvautolaunch;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A dependency-free remote-controlled Android TV home app + one-per-boot app launcher. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff09121e;
    private static final int SURFACE = 0xff132438;
    private static final int SURFACE_ALT = 0xff1b3046;
    private static final int WHITE = 0xfff2f7fa;
    private static final int MUTED = 0xffa4b7c9;
    private static final int AQUA = 0xff69e0ea;
    private static final int GOLD = 0xffffce81;

    private SharedPreferences prefs;
    private PackageManager pm;
    private TextView chosenView;
    private TextView homeView;
    private TextView instructionView;
    private Button toggleButton;
    private ListView appList;
    private EditText searchBox;
    private AppAdapter adapter;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
        pm = getPackageManager();
        buildScreen();
        loadApps();
        maybeAutoLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Pressing Home after the target is already launched shows this control screen.
        maybeAutoLaunch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private boolean isHomeRequest(Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME);
    }

    private void maybeAutoLaunch(Intent intent) {
        if (!isHomeRequest(intent) || !prefs.getBoolean("enabled", true)) return;
        if (prefs.getString("chosen_package", "").isEmpty()) return;
        final int boot = BootReceiver.getBootCount(this);
        final int last = prefs.getInt("last_auto_launch_boot", -1);
        final boolean pending = prefs.getBoolean("pending_boot_launch", false);
        if (!pending && (boot < 0 || last == boot)) return;
        // Mark BEFORE starting the external app: Home presses must not loop forever.
        prefs.edit().putInt("last_auto_launch_boot", boot)
                .putBoolean("pending_boot_launch", false).apply();
        handler.postDelayed(() -> launchChosen(true), 350);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(26), dp(24), dp(26), dp(24));
        root.setBackgroundColor(BG);
        setContentView(root);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(false);
        leftScroll.setVerticalScrollBarEnabled(false);
        int widthDp = (int)(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int leftDp = Math.max(280, Math.min(375, Math.round(widthDp * .37f)));
        root.addView(leftScroll, new LinearLayout.LayoutParams(dp(leftDp), -1));
        LinearLayout left = column();
        leftScroll.addView(left);

        TextView eyebrow = text("YOUR TV • YOUR STARTUP", 12, AQUA, true);
        left.addView(eyebrow);
        TextView title = text("TV Auto\nLaunch", 37, WHITE, true);
        title.setLineSpacing(0, 1.04f);
        LinearLayout.LayoutParams titleLp = lp(-1, -2, 0, 10, 0, 8);
        left.addView(title, titleLp);
        TextView sub = text("Choose the app your TV should open after power-on.", 15, MUTED, false);
        left.addView(sub, lp(-1, -2, 0, 0, 0, 20));

        LinearLayout summary = column();
        summary.setPadding(dp(18), dp(16), dp(18), dp(14));
        summary.setBackground(panel(SURFACE, 18, AQUA, 1));
        left.addView(summary, lp(-1, -2, 0, 0, 0, 14));
        summary.addView(text("STARTUP TARGET", 11, AQUA, true));
        chosenView = text("No app selected", 20, WHITE, true);
        summary.addView(chosenView, lp(-1, -2, 0, 7, 0, 7));
        instructionView = text("Choose an app from the list →", 12, MUTED, false);
        summary.addView(instructionView);

        toggleButton = addButton(left, "Auto-launch: ON", true, v -> {
            boolean newValue = !prefs.getBoolean("enabled", true);
            prefs.edit().putBoolean("enabled", newValue).apply();
            updateStatus();
        });
        addButton(left, "▶  Launch selected app", false, v -> launchChosen(false));
        addButton(left, "⌂  Set as default Home", false, v -> requestHomeRole());
        homeView = text("Checking default launcher…", 12, MUTED, false);
        left.addView(homeView, lp(-1, -2, 8, 0, 4, 8));
        addButton(left, "↻  Refresh installed apps", false, v -> loadApps());
        addButton(left, "⚙  Android TV settings", false, v -> openSystemSettings());
        addButton(left, "✕  Clear selected app", false, v -> {
            prefs.edit().remove("chosen_package").remove("chosen_class").remove("chosen_label")
                    .putBoolean("pending_boot_launch", false).apply();
            updateStatus();
            filterApps(searchBox.getText().toString());
        });
        TextView note = text("Home button returns to this screen. The target opens automatically only once per boot.\n\nNo account • No ads • No network access", 12, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        left.addView(note, lp(-1, -2, 9, 9, 0, 0));

        View separator = new View(this);
        separator.setBackgroundColor(0xff284156);
        root.addView(separator, new LinearLayout.LayoutParams(dp(1), -1));

        LinearLayout right = column();
        right.setPadding(dp(24), 0, 0, 0);
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        right.addView(text("INSTALLED APPS", 11, AQUA, true));
        right.addView(text("Select an app to make it your startup destination", 23, WHITE, true),
                lp(-1, -2, 6, 0, 0, 12));
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(MUTED);
        searchBox.setTextSize(15);
        searchBox.setHint("Search apps…");
        searchBox.setPadding(dp(17), dp(10), dp(14), dp(10));
        searchBox.setBackground(focusBackground(SURFACE, true));
        right.addView(searchBox, lp(-1, dp(48), 0, 0, 0, 13));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable e) {}
        });
        appList = new ListView(this);
        appList.setDividerHeight(dp(8));
        appList.setDivider(null);
        // Draw the outline above app-row backgrounds so D-pad focus is always visible.
        appList.setSelector(listFocusRing());
        appList.setDrawSelectorOnTop(true);
        appList.setCacheColorHint(Color.TRANSPARENT);
        appList.setBackgroundColor(Color.TRANSPARENT);
        appList.setClipToPadding(false);
        appList.setPadding(dp(2), dp(3), dp(2), dp(6));
        appList.setVerticalScrollBarEnabled(true);
        adapter = new AppAdapter();
        appList.setAdapter(adapter);
        appList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleApps.size()) return;
            AppEntry target = visibleApps.get(position);
            int boot = BootReceiver.getBootCount(this);
            prefs.edit()
                    .putString("chosen_package", target.packageName)
                    .putString("chosen_class", target.className)
                    .putString("chosen_label", target.label)
                    // Saving a selection should not trigger an automatic launch during this boot.
                    .putInt("last_auto_launch_boot", boot)
                    .putBoolean("pending_boot_launch", false)
                    .apply();
            updateStatus();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, target.label + " selected for next boot", Toast.LENGTH_SHORT).show();
        });
        right.addView(appList, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView footer = text("D-pad to navigate • OK to select • BACK to close keyboard", 12, MUTED, false);
        right.addView(footer, lp(-1, -2, 12, 0, 0, 0));
        appList.requestFocus();
    }

    private void loadApps() {
        Map<String, AppEntry> lookup = new LinkedHashMap<>();
        addAppsFromCategory(Intent.CATEGORY_LEANBACK_LAUNCHER, lookup);
        addAppsFromCategory(Intent.CATEGORY_LAUNCHER, lookup);
        allApps.clear();
        allApps.addAll(lookup.values());
        Collections.sort(allApps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label));
        filterApps(searchBox == null ? "" : searchBox.getText().toString());
        updateStatus();
    }

    private void addAppsFromCategory(String category, Map<String, AppEntry> result) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        List<ResolveInfo> matches;
        try { matches = pm.queryIntentActivities(query, 0); }
        catch (RuntimeException e) { return; }
        for (ResolveInfo info : matches) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName()) || result.containsKey(pkg)) continue;
            CharSequence rawLabel = info.loadLabel(pm);
            String label = rawLabel == null ? pkg : rawLabel.toString();
            Drawable icon = null;
            try { icon = info.loadIcon(pm); } catch (RuntimeException ignored) {}
            result.put(pkg, new AppEntry(label, pkg, info.activityInfo.name, icon));
        }
    }

    private void filterApps(String filter) {
        String key = filter.toLowerCase(Locale.ROOT).trim();
        visibleApps.clear();
        for (AppEntry e : allApps) {
            if (key.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(key)
                    || e.packageName.toLowerCase(Locale.ROOT).contains(key)) visibleApps.add(e);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (appList != null) appList.setEmptyView(null);
    }

    private void updateStatus() {
        if (chosenView == null) return;
        String chosen = prefs.getString("chosen_label", "");
        chosenView.setText(chosen.isEmpty() ? "No app selected" : chosen);
        boolean enabled = prefs.getBoolean("enabled", true);
        toggleButton.setText(enabled ? "✓  Auto-launch: ON" : "○  Auto-launch: OFF");
        instructionView.setText(chosen.isEmpty() ? "Select an app on the right →"
                : enabled ? "Opens at next TV startup" : "Auto-launch is paused");
        if (homeView != null) {
            boolean home = isDefaultHome();
            homeView.setText(home ? "✓ This app is your default Home launcher"
                    : "! Not default Home yet. Choose Home above.");
            homeView.setTextColor(home ? AQUA : GOLD);
        }
    }

    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)
                        && rm.isRoleHeld(RoleManager.ROLE_HOME)) return true;
            } catch (RuntimeException ignored) {}
        }
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Toast.makeText(this, "Already the default Home app", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 100);
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Home selection is blocked on this TV. See the GitHub setup guide for ADB steps.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) updateStatus();
    }

    private void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Settings screen unavailable", Toast.LENGTH_SHORT).show(); }
    }

    private void launchChosen(boolean fromBoot) {
        String pkg = prefs.getString("chosen_package", "");
        String cls = prefs.getString("chosen_class", "");
        if (pkg.isEmpty()) {
            if (!fromBoot) Toast.makeText(this, "Select an app first", Toast.LENGTH_SHORT).show();
            return;
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
        } catch (RuntimeException error) {
            Toast.makeText(this, "Cannot open selected app. Refresh apps or choose another.", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private TextView text(String string, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(string);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable panel(int background, int corner, int stroke, int sw) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(background);
        d.setCornerRadius(dp(corner));
        if (sw > 0) d.setStroke(dp(sw), stroke);
        return d;
    }
    private StateListDrawable focusBackground(int background, boolean darker) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, panel(darker ? SURFACE_ALT : 0xff25435a, 13, AQUA, 2));
        states.addState(new int[] { android.R.attr.state_pressed }, panel(0xff24425a, 13, AQUA, 2));
        states.addState(new int[] { android.R.attr.state_selected }, panel(SURFACE_ALT, 13, AQUA, 2));
        states.addState(new int[] {}, panel(background, 13, 0xff2c4558, 1));
        return states;
    }
    private StateListDrawable listFocusRing() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(dp(13));
        outline.setStroke(dp(3), AQUA);
        states.addState(new int[] { android.R.attr.state_focused }, outline);
        states.addState(new int[] { android.R.attr.state_pressed }, outline);
        states.addState(new int[] { android.R.attr.state_selected }, outline);
        states.addState(new int[] {}, new GradientDrawable());
        return states;
    }

    private Button addButton(LinearLayout parent, String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setTextColor(ColorStateList.valueOf(WHITE));
        b.setPadding(dp(18), 0, dp(14), 0);
        b.setBackground(focusBackground(primary ? 0xff204457 : SURFACE, false));
        b.setFocusable(true);
        b.setOnClickListener(click);
        parent.addView(b, lp(-1, 48, 0, 0, 0, 9));
        return b;
    }

    private static final class AppEntry {
        final String label, packageName, className;
        final Drawable icon;
        AppEntry(String name, String pkg, String cls, Drawable icon) {
            label = name; packageName = pkg; className = cls; this.icon = icon;
        }
    }

    private final class AppAdapter extends ArrayAdapter<AppEntry> {
        AppAdapter() { super(MainActivity.this, android.R.layout.simple_list_item_1, visibleApps); }
        @Override public int getCount() { return visibleApps.size(); }
        @Override public AppEntry getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AppEntry item = visibleApps.get(position);
            LinearLayout line = new LinearLayout(MainActivity.this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(dp(68));
            line.setPadding(dp(16), dp(9), dp(14), dp(9));
            boolean chosen = item.packageName.equals(prefs.getString("chosen_package", ""));
            line.setBackground(panel(chosen ? 0xff1c4050 : SURFACE, 13, chosen ? AQUA : 0xff243c50, 1));
            ImageView image = new ImageView(MainActivity.this);
            if (item.icon != null) image.setImageDrawable(item.icon);
            else image.setImageResource(R.drawable.ic_launcher);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            line.addView(image, lp(43, 43, 0, 0, 13, 0));
            LinearLayout detail = column();
            detail.addView(text(item.label, 17, WHITE, true));
            detail.addView(text(item.packageName, 11, MUTED, false), lp(-1, -2, 2, 1, 0, 0));
            line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
            if (chosen) {
                TextView tag = text("✓  STARTUP", 11, AQUA, true);
                tag.setPadding(dp(10), dp(8), dp(10), dp(8));
                tag.setBackground(panel(0xff235469, 8, 0xff32788b, 1));
                line.addView(tag);
            }
            return line;
        }
    }
}
