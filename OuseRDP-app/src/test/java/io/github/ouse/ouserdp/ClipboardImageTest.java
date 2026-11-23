package io.github.ouse.ouserdp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application.class)
public class ClipboardImageTest {
    @Test public void pngAndDibPreservePixelsAlphaDimensionsAndRowOrder() throws Exception {
        Bitmap source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        source.setPixels(new int[]{Color.RED, Color.GREEN, Color.BLUE, 0x800000ff, Color.TRANSPARENT, Color.WHITE}, 0, 3, 0, 0, 3, 2);
        ClipboardImage image = ClipboardImage.encode(source);
        Bitmap png = BitmapFactory.decodeByteArray(image.png, 0, image.png.length);
        try {
            assertEquals(3, png.getWidth()); assertEquals(2, png.getHeight());
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) assertEquals(source.getPixel(x, y), png.getPixel(x, y));
            ByteBuffer dib = ByteBuffer.wrap(image.dibV5).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(124, dib.getInt(0)); assertEquals(3, dib.getInt(4)); assertEquals(-2, dib.getInt(8));
            assertEquals(1, dib.getShort(12)); assertEquals(32, dib.getShort(14));
            assertEquals(3, dib.getInt(16)); assertEquals(0xff000000, dib.getInt(52));
            assertEquals(0x73524742, dib.getInt(56)); assertEquals(4, dib.getInt(108));
            assertEquals(148, image.dibV5.length);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) assertEquals(source.getPixel(x, y), dib.getInt(124 + (y * 3 + x) * 4));
        } finally { png.recycle(); source.recycle(); }
    }

    @Test public void invalidOrOversizedDimensionsAreRejectedBeforeDecode() throws Exception {
        for (int[] dimensions : new int[][]{{0, 1}, {-1, 4}, {9000, 1}, {5000, 5000}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
            try { ClipboardImage.checkSize(dimensions[0], dimensions[1]); fail("Expected size rejection"); }
            catch (IOException expected) { /* Invalid or too large. */ }
        }
        ClipboardImage.checkSize(3504, 2190); // Full tablet screenshot retains its pixels.
    }

    @Test @Config(sdk = 28) public void photoPickerImageReadHonorsExifRotation() throws Exception { checkRotatedRead(); }
    @Test @Config(sdk = 27) public void legacyPickerReadHonorsExifRotation() throws Exception { checkRotatedRead(); }

    private void checkRotatedRead() throws Exception {
        File file = File.createTempFile("clipboard-orientation", ".jpg", RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap source = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888); source.eraseColor(Color.RED);
        try {
            try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, output)); }
            ExifInterface metadata = new ExifInterface(file.getAbsolutePath());
            metadata.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(ExifInterface.ORIENTATION_ROTATE_90)); metadata.saveAttributes();
            ClipboardImage image = ClipboardImage.read(RuntimeEnvironment.getApplication().getContentResolver(), Uri.fromFile(file));
            Bitmap decoded = BitmapFactory.decodeByteArray(image.png, 0, image.png.length);
            try { assertEquals(4, decoded.getWidth()); assertEquals(6, decoded.getHeight()); }
            finally { decoded.recycle(); }
        } finally { source.recycle(); assertTrue(file.delete()); }
    }

    @Test public void cancelledEncodingDoesNotContinueAllocatingOrPublishing() throws Exception {
        Bitmap source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Thread.currentThread().interrupt();
        try { ClipboardImage.encode(source); fail("Expected cancellation"); }
        catch (IOException expected) { assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); source.recycle(); }
    }
}
