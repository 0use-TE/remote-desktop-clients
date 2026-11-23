package io.github.ouse.ouserdp;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.undatech.opaque.RdpSharedFolder;
import com.undatech.opaque.NativeSharedFolder;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Application-owned job: leaving the file screen does not cancel the copy or retain its Activity. */
final class SharedTransfers {
    interface Observer { void changed(Status status); }
    static final class Status {
        final boolean busy;
        final int message;
        final String name;
        final long bytes, total;
        final int files, totalFiles;
        Status(boolean busy, int message, String name, long bytes, long total, int files, int totalFiles) {
            this.busy = busy; this.message = message; this.name = name; this.bytes = bytes; this.total = total;
            this.files = files; this.totalFiles = totalFiles;
        }
    }
    private static SharedTransfers instance;
    static synchronized SharedTransfers get(Context context) {
        if (instance == null) instance = new SharedTransfers(context.getApplicationContext());
        return instance;
    }
    private final android.app.Application context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Observer> observers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private volatile Status status = new Status(false, 0, "", 0, -1, 0, 0);
    private long lastProgress;
    private SharedTransfers(Context context) { this.context = (android.app.Application) context.getApplicationContext(); }
    Status status() { return status; }
    void observe(Observer observer) { observers.add(observer); observer.changed(status); }
    void remove(Observer observer) { observers.remove(observer); }
    void cancel() { cancel.set(true); }

    synchronized void importFiles(List<Uri> uris, String relative, boolean tree) {
        if (!begin()) return;
        worker.execute(() -> {
            File stage = null;
            try {
                File root = RdpSharedFolder.directory(context);
                LocalTransferNode destination = local(root, relative);
                File workspace = new File(root.getParentFile(), "OuseRDP-TransferWork");
                if (!workspace.isDirectory() && !workspace.mkdirs() && !workspace.isDirectory()) throw new IOException("No staging storage");
                // Single worker: previous process-death remnants cannot belong to another active job.
                File[] abandoned = workspace.listFiles();
                if (abandoned != null) for (File old : abandoned) if (old.getName().startsWith("job-")) {
                    try { new LocalTransferNode(workspace, old).delete(); } catch (IOException ignored) {}
                }
                stage = new File(workspace, "job-" + UUID.randomUUID());
                if (!stage.mkdir()) throw new IOException("Cannot stage copy");
                List<TransferNode> sources = new ArrayList<>();
                for (Uri uri : uris) sources.add(tree ? SafTransferNode.tree(context.getContentResolver(), uri)
                        : SafTransferNode.document(context.getContentResolver(), uri));
                Set<String> names = TransferEngine.names(destination);
                new TransferEngine(cancel::get, this::progress).copy(sources, new LocalTransferNode(stage, stage),
                        (staged, desired, directory) -> publish((LocalTransferNode) staged, destination, desired, directory, names));
                finish(R.string.ouse_transfer_done);
            } catch (InterruptedIOException error) { finish(R.string.ouse_transfer_cancelled); }
            catch (TransferEngine.SourceChangedException error) { finish(R.string.ouse_transfer_source_changed); }
            catch (IOException | RuntimeException error) { finish(R.string.ouse_transfer_failed); }
            finally {
                if (stage != null) try { new LocalTransferNode(stage, stage).delete(); } catch (IOException ignored) {}
            }
        });
    }

    synchronized void exportFiles(List<String> relativeFiles, Uri tree) {
        if (!begin()) return;
        worker.execute(() -> {
            try {
                File root = RdpSharedFolder.directory(context);
                List<TransferNode> sources = new ArrayList<>();
                for (String relative : relativeFiles) sources.add(local(root, relative));
                SafTransferNode destination = SafTransferNode.tree(context.getContentResolver(), tree);
                new TransferEngine(cancel::get, this::progress).copy(sources, destination, null);
                finish(R.string.ouse_transfer_done);
            } catch (InterruptedIOException error) { finish(R.string.ouse_transfer_cancelled); }
            catch (TransferEngine.SourceChangedException error) { finish(R.string.ouse_transfer_source_changed); }
            catch (IOException | RuntimeException error) { finish(R.string.ouse_transfer_failed); }
        });
    }

    private void publish(LocalTransferNode staged, LocalTransferNode destination, String desired, boolean directory, Set<String> names) throws IOException {
        while (true) {
            if (cancel.get()) throw new InterruptedIOException();
            String name = TransferEngine.uniqueName(desired, directory, names);
            File target = new File(destination.file, name);
            int result = NativeSharedFolder.publish(staged.file.getAbsolutePath(), target.getAbsolutePath());
            if (result == NativeSharedFolder.OK) return;
            if (result == NativeSharedFolder.EXISTS) continue;
            if (result == NativeSharedFolder.UNSUPPORTED) {
                // Old kernels/filesystems: exclusive create + streamed copy retains no-overwrite semantics.
                List<TransferNode> sources = java.util.Collections.singletonList(staged);
                new TransferEngine(cancel::get, (file, bytes, total, files, all) -> {}).copy(sources, destination, null);
                return;
            }
            throw new IOException("Cannot publish shared file");
        }
    }

    static LocalTransferNode local(File root, String relative) throws IOException {
        if (relative == null || new File(relative).isAbsolute()) throw new IOException("Invalid shared path");
        return new LocalTransferNode(root, relative.isEmpty() ? root : new File(root, relative));
    }
    private boolean begin() {
        if (status.busy) return false;
        cancel.set(false); lastProgress = 0;
        update(new Status(true, R.string.ouse_transfer_preparing, "", 0, -1, 0, 0));
        return true;
    }
    private void progress(String name, long bytes, long total, int files, int all) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastProgress < 200 && !name.isEmpty()) return;
        lastProgress = now;
        update(new Status(true, R.string.ouse_transfer_copying, name, bytes, total, files, all));
    }
    private void finish(int message) {
        Status previous = status;
        update(new Status(false, message, previous.name, previous.bytes, previous.total, previous.files, previous.totalFiles));
    }
    private void update(Status next) {
        status = next;
        main.post(() -> { for (Observer observer : observers) observer.changed(next); });
    }
}
