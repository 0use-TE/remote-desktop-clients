package io.github.ouse.ouserdp;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Lossless sRGB image offer. Work stays off the UI and RDP input threads. */
final class ClipboardImage {
    static final long MAX_PIXELS = 16L * 1024 * 1024;
    static final int MAX_PNG_BYTES = 64 * 1024 * 1024;
    final byte[] png, dibV5;

    ClipboardImage(byte[] png, byte[] dibV5) { this.png = png; this.dibV5 = dibV5; }

    static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1) throw new IOException("Cannot read image dimensions");
        if (width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS)
            throw new TooLargeException();
    }

    static ClipboardImage read(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                    try { checkSize(info.getSize().getWidth(), info.getSize().getHeight()); }
                    catch (TooLargeException error) { throw new ImageSizeLimitException(); }
                    catch (IOException error) { throw new IllegalArgumentException(error); }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                });
            } catch (ImageSizeLimitException error) { throw new TooLargeException(); }
        } else {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream stream = resolver.openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, bounds); }
            checkSize(bounds.outWidth, bounds.outHeight);
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream stream = resolver.openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
            if (bitmap == null) throw new IOException("Cannot decode image");
            try {
                try (InputStream stream = resolver.openInputStream(uri)) {
                    if (stream != null) {
                        ExifInterface exif = new ExifInterface(stream);
                        Matrix matrix = new Matrix();
                        if (exif.isFlipped()) matrix.postScale(-1, 1);
                        matrix.postRotate(exif.getRotationDegrees());
                        if (!matrix.isIdentity()) {
                            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            if (oriented != bitmap) { bitmap.recycle(); bitmap = oriented; }
                        }
                    }
                }
            } catch (IOException ignored) { /* Media without EXIF remains a valid image. */ }
        }
        try { return encode(bitmap); }
        finally { bitmap.recycle(); }
    }

    static ClipboardImage encode(Bitmap bitmap) throws IOException {
        checkSize(bitmap.getWidth(), bitmap.getHeight());
        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] data, int offset, int length) {
                if (length > MAX_PNG_BYTES - count) throw new ImageSizeLimitException();
                super.write(data, offset, length);
            }
        };
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG encoding failed");
        } catch (ImageSizeLimitException error) { throw new TooLargeException(); }
        byte[] png = output.toByteArray();
        // BITMAPV5HEADER followed by straight-alpha BGRA, top-down, in sRGB.
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        byte[] dib = new byte[124 + width * height * 4];
        ByteBuffer header = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(124).putInt(width).putInt(-height).putShort((short) 1).putShort((short) 32);
        header.putInt(3).putInt(width * height * 4); // BI_BITFIELDS
        header.position(40);
        header.putInt(0x00ff0000).putInt(0x0000ff00).putInt(0x000000ff).putInt(0xff000000);
        header.putInt(0x73524742); // LCS_sRGB
        header.position(108); header.putInt(4); // LCS_GM_IMAGES
        int[] row = new int[width];
        int offset = 124;
        for (int y = 0; y < height; y++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int pixel : row) {
                dib[offset++] = (byte) pixel; dib[offset++] = (byte) (pixel >>> 8);
                dib[offset++] = (byte) (pixel >>> 16); dib[offset++] = (byte) (pixel >>> 24);
            }
        }
        return new ClipboardImage(png, dib);
    }

    static final class TooLargeException extends IOException {}
    private static final class ImageSizeLimitException extends RuntimeException {}
}
