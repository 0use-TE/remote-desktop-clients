package io.github.ouse.ouserdp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Foreground lifecycle polling with no UI, dialogs, or notifications. */
final class KeyboardServiceMonitor {
    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            KeyboardServiceHealth.poll(app);
            handler.postDelayed(this, 1000);
        }
    };

    KeyboardServiceMonitor(Context context) { app = context.getApplicationContext(); }
    void start() { stop(); active = true; handler.post(refresh); }
    void stop() { active = false; handler.removeCallbacks(refresh); }
}
