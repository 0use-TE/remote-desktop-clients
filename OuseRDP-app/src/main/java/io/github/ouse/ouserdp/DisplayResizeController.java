package io.github.ouse.ouserdp;

import android.os.Handler;

/** Debounce layout changes and wait briefly for the server's display channel. */
final class DisplayResizeController {
    interface Target {
        boolean ready();
        void resize(int width, int height, int scale);
    }
    private final Handler handler;
    private final Target target;
    private boolean active;
    private int width, height, scale, sentWidth, sentHeight, sentScale, attempts;
    private final Runnable apply = new Runnable() {
        public void run() {
            if (!active || width < 200 || height < 200) return;
            if (width == sentWidth && height == sentHeight && scale == sentScale) return;
            if (!target.ready()) {
                if (++attempts < 20) handler.postDelayed(this, 500);
                return;
            }
            target.resize(width, height, scale);
            sentWidth = width; sentHeight = height; sentScale = scale;
        }
    };
    DisplayResizeController(Handler handler, Target target) { this.handler = handler; this.target = target; }
    void update(int w, int h, int desktopScale) {
        width = w; height = h; scale = desktopScale; attempts = 0;
        handler.removeCallbacks(apply);
        if (active) handler.postDelayed(apply, 500);
    }
    void start() { active = true; update(width, height, scale); }
    void stop() { active = false; handler.removeCallbacks(apply); }
    void reconnect() { sentWidth = sentHeight = sentScale = 0; update(width, height, scale); }
}
