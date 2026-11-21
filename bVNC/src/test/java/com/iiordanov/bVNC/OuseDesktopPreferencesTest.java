package com.iiordanov.bVNC;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OuseDesktopPreferencesTest {
    @Test
    public void automaticLimitsHighResolutionAndAdjustsScale() {
        check(2560, 1600, 150, OuseDesktopPreferences.resolveDisplay(3200, 2000, 320, 0, 0));
    }

    @Test
    public void lowBandwidthModePreservesPortraitAspectRatio() {
        check(1200, 1920, 175, OuseDesktopPreferences.resolveDisplay(1600, 2560, 360, 1920, 0));
    }

    @Test
    public void screenModeKeepsNativeResolutionAndManualScale() {
        check(3200, 2000, 125, OuseDesktopPreferences.resolveDisplay(3200, 2000, 320, -1, 125));
    }

    @Test
    public void automaticNeverUpscalesAndMakesDimensionsEven() {
        check(1366, 768, 125, OuseDesktopPreferences.resolveDisplay(1367, 769, 200, 0, 0));
    }

    private static void check(int width, int height, int scale, OuseDesktopPreferences.Display actual) {
        assertEquals(width, actual.width);
        assertEquals(height, actual.height);
        assertEquals(scale, actual.scale);
    }
}
