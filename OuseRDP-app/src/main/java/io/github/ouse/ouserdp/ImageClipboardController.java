package io.github.ouse.ouserdp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.undatech.opaque.RdpCommunicator;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Bound to a session Activity; no image or URI goes into logs or saved diagnostics. */
final class ImageClipboardController implements AutoCloseable {
    interface Feedback { void show(int message); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Feedback feedback;
    private volatile boolean closed;
    private boolean busy;

    ImageClipboardController(Context context, Feedback feedback) {
        this.context = context.getApplicationContext(); this.feedback = feedback;
    }

    boolean busy() { return busy; }

    void copy(Uri uri, RdpCommunicator backend, long instance) {
        if (closed || busy || uri == null) return;
        busy = true;
        feedback.show(R.string.ouse_image_preparing);
        execute(() -> {
            try {
                ClipboardImage image = ClipboardImage.read(context.getContentResolver(), uri);
                main.post(() -> {
                    if (closed) return;
                    // Read Android clipboard while foregrounded, then guard it against stale text sync.
                    String localText = clipboardText();
                    execute(() -> {
                        try {
                            boolean sent = backend.writeClipboardImage(instance, image.png, image.dibV5, localText);
                            finish(sent ? R.string.ouse_image_copied : R.string.ouse_image_connection_changed);
                        } catch (OutOfMemoryError error) { finish(R.string.ouse_image_too_large); }
                        catch (RuntimeException error) { finish(R.string.ouse_image_failed); }
                    });
                });
            } catch (ClipboardImage.TooLargeException | OutOfMemoryError error) {
                finish(R.string.ouse_image_too_large);
            } catch (IOException | RuntimeException error) {
                finish(R.string.ouse_image_failed);
            }
        });
    }

    private String clipboardText() {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = manager == null ? null : manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text == null ? null : text.toString();
        } catch (RuntimeException error) { return null; }
    }

    private void execute(Runnable task) {
        if (closed) return;
        try { worker.execute(task); }
        catch (RejectedExecutionException ignored) { /* Session closed during selection. */ }
    }

    private void finish(int message) {
        main.post(() -> { if (!closed) { busy = false; feedback.show(message); } });
    }

    @Override public void close() {
        closed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null);
    }
}
