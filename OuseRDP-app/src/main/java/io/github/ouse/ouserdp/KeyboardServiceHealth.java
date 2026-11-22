package io.github.ouse.ouserdp;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.iiordanov.bVNC.WindowsKeyCaptureService;
import java.util.ArrayList;
import java.util.List;

/** State and recovery of this app's service. Never enables a service the user has not authorized. */
final class KeyboardServiceHealth {
    enum State { DISABLED, CONNECTING, RUNNING, UNAVAILABLE }
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final long GRACE_MS = 4000;
    private static final long RETRY_MS = 60000;
    private static long graceUntil = SystemClock.elapsedRealtime() + GRACE_MS;
    private static boolean recovering;
    private static final String PREFS = "keyboardServiceHealth";
    private static final String PENDING = "pendingRecovery";
    private static final String LAST_ATTEMPT = "lastAttempt";

    static ComponentName component(Context context) {
        return new ComponentName(context, WindowsKeyCaptureService.class);
    }

    static boolean contains(String value, ComponentName target) {
        if (value == null) return false;
        for (String entry : value.split(":")) {
            if (target.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    static String withService(String value, ComponentName target, boolean enabled) {
        List<String> entries = new ArrayList<>();
        if (value != null) for (String entry : value.split(":")) {
            if (!entry.isEmpty() && !target.equals(ComponentName.unflattenFromString(entry))) entries.add(entry);
        }
        if (enabled) entries.add(target.flattenToString());
        return String.join(":", entries);
    }

    private static String enabledServices(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    }

    static boolean hasRecoveryPermission(Context context) {
        return androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static State state(Context context) {
        boolean pending = context.getSharedPreferences(PREFS, 0).getBoolean(PENDING, false);
        if (Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0) return State.DISABLED;
        if (!contains(enabledServices(context), component(context))) {
            return recovering || (pending && hasRecoveryPermission(context)) ? State.CONNECTING : State.DISABLED;
        }
        // The connected callback is authoritative. The manager's list can lag or
        // be filtered on OEM builds; never restart a working service because of it.
        if (WindowsKeyCaptureService.isConnected()) return State.RUNNING;
        return recovering || SystemClock.elapsedRealtime() < graceUntil ? State.CONNECTING : State.UNAVAILABLE;
    }

    static String summary(State state) {
        return switch (state) {
            case DISABLED -> "键盘服务未开启";
            case CONNECTING -> "键盘服务正在连接";
            case RUNNING -> "键盘服务运行正常";
            case UNAVAILABLE -> "键盘服务已开启，但没有运行";
        };
    }

    static void poll(Context context) {
        Context app = context.getApplicationContext();
        if (recovering) return;
        var preferences = app.getSharedPreferences(PREFS, 0);
        if (!hasRecoveryPermission(app)) {
            if (preferences.getBoolean(PENDING, false)) preferences.edit().remove(PENDING).commit();
            return;
        }
        if (Settings.Secure.getInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0) {
            preferences.edit().remove(PENDING).commit();
            return;
        }
        // Complete a recovery interrupted by process death before trying a new one.
        if (preferences.getBoolean(PENDING, false)) {
            restore(app);
            return;
        }
        if (state(app) != State.UNAVAILABLE) return;
        long last = preferences.getLong(LAST_ATTEMPT, 0);
        long now = System.currentTimeMillis();
        if (last != 0 && now >= last && now - last < RETRY_MS) return;
        recover(app);
    }

    static boolean recover(Context context) {
        Context app = context.getApplicationContext();
        if (recovering) return true;
        if (!hasRecoveryPermission(app) || state(app) == State.DISABLED
                || !contains(enabledServices(app), component(app))) return false;
        var preferences = app.getSharedPreferences(PREFS, 0);
        // Persist intent before disabling: a process interruption must not leave the switch off.
        if (!preferences.edit().putBoolean(PENDING, true).putLong(LAST_ATTEMPT, System.currentTimeMillis()).commit()) return false;
        recovering = true;
        graceUntil = SystemClock.elapsedRealtime() + GRACE_MS;
        try {
            if (!Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    withService(enabledServices(app), component(app), false))) {
                preferences.edit().remove(PENDING).commit();
                recovering = false;
                return false;
            }
            android.util.Log.i("OuseKeyboard", "Rebinding own accessibility service");
            handler.postDelayed(() -> restore(app), 700);
            return true;
        } catch (RuntimeException error) {
            android.util.Log.e("OuseKeyboard", "Unable to request service recovery", error);
            restore(app);
            return false;
        }
    }

    private static void restore(Context app) {
        try {
            if (!hasRecoveryPermission(app)) return;
            if (Settings.Secure.getInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 0) {
                app.getSharedPreferences(PREFS, 0).edit().remove(PENDING).commit();
                return;
            }
            // Read again: preserve other services changed while our service was off.
            if (Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    withService(enabledServices(app), component(app), true))) {
                app.getSharedPreferences(PREFS, 0).edit().remove(PENDING).commit();
                graceUntil = SystemClock.elapsedRealtime() + GRACE_MS;
            }
        } catch (RuntimeException error) {
            android.util.Log.e("OuseKeyboard", "Unable to restore service authorization", error);
        } finally { recovering = false; }
    }

    static void showRecovery(Activity activity) {
        State state = state(activity);
        if (state == State.UNAVAILABLE && hasRecoveryPermission(activity) && recover(activity)) return;
        String message = switch (state) {
            case DISABLED -> "在安卓无障碍设置中开启「OuseRDP 键盘快捷键」。";
            case RUNNING -> "服务已连接，可以捕获远程桌面的键盘快捷键。";
            case CONNECTING -> "正在等待安卓连接键盘服务，可以稍后查看状态。";
            case UNAVAILABLE -> "关闭「OuseRDP 键盘快捷键」，再重新开启，让系统恢复连接。其他无障碍服务无需更改。";
        };
        var dialog = new MaterialAlertDialogBuilder(activity).setTitle(summary(state))
                .setMessage(message).setPositiveButton("打开服务设置", (d, which) -> openSettings(activity))
                .setNegativeButton("返回", null).show();
        TextView text = dialog.findViewById(android.R.id.message);
        if (text != null) text.setTextSize(20);
    }

    static void openSettings(Activity activity) {
        // Details settings is permission-protected on ZUI/Android 16. Use the
        // public entry, with an optional highlight hint that OEMs may ignore.
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        android.os.Bundle hint = new android.os.Bundle();
        hint.putString(":settings:fragment_args_key", component(activity).flattenToString());
        settings.putExtra(":settings:fragment_args_key", component(activity).flattenToString());
        settings.putExtra(":settings:show_fragment_args", hint);
        try { activity.startActivity(settings); }
        catch (android.content.ActivityNotFoundException | SecurityException error) {
            android.util.Log.w("OuseKeyboard", "Accessibility settings unavailable", error);
            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (android.content.ActivityNotFoundException | SecurityException fallbackError) {
                android.widget.Toast.makeText(activity, "请在系统设置中打开无障碍", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    static String diagnostics(Context context) {
        StringBuilder report = new StringBuilder("OuseRDP keyboard service diagnostics\n");
        try {
            report.append("Version: ").append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName).append('\n');
        } catch (PackageManager.NameNotFoundException ignored) {}
        report.append("Android: ").append(Build.VERSION.RELEASE).append(" / ").append(Build.MODEL).append('\n')
                .append("State: ").append(state(context)).append('\n')
                .append("Authorized: ").append(contains(enabledServices(context), component(context))).append('\n')
                .append("Bound in system: ").append(WindowsKeyCaptureService.isEnabled(context)).append('\n')
                .append("Connected callback: ").append(WindowsKeyCaptureService.isConnected()).append('\n')
                .append("Recovery permission: ").append(hasRecoveryPermission(context)).append('\n');
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                for (var exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8)) {
                    report.append("Exit: time=").append(exit.getTimestamp()).append(" reason=").append(exit.getReason())
                            .append(" signal/status=").append(exit.getStatus()).append(" description=").append(exit.getDescription()).append('\n');
                }
            } catch (RuntimeException error) { report.append("Exit history unavailable\n"); }
        }
        return report.toString();
    }
}
