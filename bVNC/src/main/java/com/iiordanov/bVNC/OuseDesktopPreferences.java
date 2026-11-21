package com.iiordanov.bVNC;

import android.content.Context;

public final class OuseDesktopPreferences {
    private static final int AUTO_MAX_EDGE = 2560;
    private static final int[] SUPPORTED_SCALES = {100, 125, 150, 175, 200, 250, 300};

    private OuseDesktopPreferences() {}

    public static boolean captureAltTab(Context context) {
        return Utils.querySharedPreferenceBoolean(context, "ouseCaptureAltTab", true);
    }

    public static boolean showWindowsWhileDragging(Context context) {
        return Utils.querySharedPreferenceBoolean(context, "ouseShowWindowsWhileDragging", true);
    }

    public static Display displayFor(Context context, int width, int height) {
        int maxEdge = readNumber(context, "ouseMaxDesktopEdge");
        int scale = readNumber(context, "ouseWindowsScale");
        int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        return resolveDisplay(width, height, densityDpi, maxEdge, scale);
    }

    static Display resolveDisplay(int width, int height, int dpi, int maxEdge, int scale) {
        if (width <= 0 || height <= 0) {
            width = 1280;
            height = 720;
        }
        int limit = maxEdge == -1 ? 8192 : maxEdge == 1920 ? 1920 : AUTO_MAX_EDGE;
        float ratio = Math.min(1f, limit / (float) Math.max(width, height));
        int remoteWidth = Math.max(200, Math.round(width * ratio) & ~1);
        int remoteHeight = Math.max(200, Math.round(height * ratio) & ~1);

        // Account for fitting a reduced framebuffer back onto the physical display.
        float wantedScale = scale > 0 ? scale : Math.max(160, dpi) * ratio * 100f / 160;
        int closest = SUPPORTED_SCALES[0];
        for (int supported : SUPPORTED_SCALES) {
            if (Math.abs(supported - wantedScale) < Math.abs(closest - wantedScale)) {
                closest = supported;
            }
        }
        return new Display(remoteWidth, remoteHeight, closest);
    }

    private static int readNumber(Context context, String key) {
        try {
            return Integer.parseInt(Utils.querySharedPreferenceString(context, key, "0"));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    public static final class Display {
        public final int width;
        public final int height;
        public final int scale;

        private Display(int width, int height, int scale) {
            this.width = width;
            this.height = height;
            this.scale = scale;
        }
    }
}
