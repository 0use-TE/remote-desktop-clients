package com.iiordanov.bVNC;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RemoteCanvasLifecycleTest {
    @Test public void repeatedSessionCleanupReleasesDisplayedAndRetiredBuffers() {
        var activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        RemoteCanvas canvas = new RemoteCanvas(activity, null);
        activity.setContentView(canvas);
        java.util.List<com.undatech.opaque.AbstractDrawableData> retired = ReflectionHelpers.getField(canvas, "retiredDrawables");
        for (int i = 0; i < 100; i++) {
            UltraCompactBitmapData current = new UltraCompactBitmapData(16, 16, canvas, true);
            UltraCompactBitmapData old = new UltraCompactBitmapData(16, 16, canvas, true);
            Bitmap currentPixels = current.mbitmap, oldPixels = old.mbitmap;
            canvas.myDrawable = current; current.setImageDrawable(canvas); retired.add(old);
            canvas.releaseBuffers(); canvas.releaseBuffers();
            assertTrue(currentPixels.isRecycled()); assertTrue(oldPixels.isRecycled());
            assertNull(canvas.getDrawable()); assertNull(canvas.getBitmap()); assertTrue(retired.isEmpty());
        }
    }
}
