package com.iiordanov.bVNC;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.undatech.opaque.Viewable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TrueColorFramebufferTest {
    @Test public void preservesColorsThatWouldBeQuantizedInRgb565() {
        UltraCompactBitmapData data = new UltraCompactBitmapData(3, 1, mock(Viewable.class), true);
        try {
            assertEquals(Bitmap.Config.ARGB_8888, data.mbitmap.getConfig());
            int[] colors = {0xff123456, 0xff789abc, 0xffcdef01};
            data.mbitmap.setPixels(colors, 0, 3, 0, 0, 3, 1);
            Bitmap output = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888);
            data.drawable.draw(new Canvas(output));
            int[] actual = new int[3]; output.getPixels(actual, 0, 3, 0, 0, 3, 1);
            assertArrayEquals(colors, actual);
            output.recycle();
        } finally { data.dispose(); }
    }

    @Test public void staleDrawableCanBeCalledAfterDisposalWithoutDrawingRecycledBitmap() {
        UltraCompactBitmapData data = new UltraCompactBitmapData(3, 1, mock(Viewable.class), true);
        var drawable = data.drawable;
        data.dispose();
        Bitmap output = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(output));
        assertEquals(0, output.getPixel(0, 0));
        output.recycle();
    }
}
