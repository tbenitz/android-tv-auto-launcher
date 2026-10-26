package io.github.tbenitz.tvautolaunch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Android 10+ blocks ordinary background apps from opening activities on boot.
 * This receiver only records a pending start; the HOME activity performs the launch.
 */
public final class BootReceiver extends BroadcastReceiver {
    public static final String PREFS = "tv_auto_launch";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int currentBoot = getBootCount(context);
        int lastLaunchBoot = prefs.getInt("last_auto_launch_boot", -1);
        // The Home Activity may have launched the target BEFORE this broadcast arrived.
        if (currentBoot < 0 || currentBoot != lastLaunchBoot) {
            prefs.edit().putBoolean("pending_boot_launch", true).apply();
        }
    }

    public static int getBootCount(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 24) return -1;
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }
}
