package io.github.ouse.ouserdp;

import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import com.undatech.opaque.NativeSharedFolder;
import com.undatech.opaque.RdpSharedFolder;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, shadows = SafTransferNodeTest.OldFilesystem.class)
public class SafTransferNodeTest {
    private static final String AUTHORITY = "io.github.ouse.transfer.test";
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Provider provider;
    private Context context;
    @Implements(NativeSharedFolder.class) public static class OldFilesystem {
        static String source;
        @Implementation protected static int publish(String from, String to) { source = from; return NativeSharedFolder.UNSUPPORTED; }
    }

    @Before public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        provider = new Provider(); provider.root = temp.newFolder("documents").getCanonicalFile();
        ProviderInfo info = new ProviderInfo(); info.authority = AUTHORITY; info.exported = true; info.grantUriPermissions = true;
        info.readPermission = info.writePermission = "android.permission.MANAGE_DOCUMENTS";
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider);
    }
    private SafTransferNode tree(String id) throws IOException {
        return SafTransferNode.tree(context.getContentResolver(), DocumentsContract.buildTreeDocumentUri(AUTHORITY, id.isEmpty() ? "root" : id));
    }
    private TransferEngine engine() { return new TransferEngine(() -> false, (name, bytes, total, files, all) -> {}); }

    @Test public void fullDirectoryRoundTripThroughARealDocumentsContractPreservesHierarchyAndBytes() throws Exception {
        File incoming = new File(provider.root, "incoming"); assertTrue(incoming.mkdir());
        File original = new File(incoming, "项目🌟"); assertTrue(original.mkdir()); assertTrue(new File(original, "empty").mkdir());
        byte[] payload = new byte[250_000]; new Random(7).nextBytes(payload);
        Files.write(new File(original, "视频.mp4").toPath(), payload);
        File share = temp.newFolder("shared");
        engine().copy(tree("incoming").children(), new LocalTransferNode(share, share), null);
        File downloaded = new File(share, "项目🌟");
        assertArrayEquals(payload, Files.readAllBytes(new File(downloaded, "视频.mp4").toPath()));
        File outgoing = new File(provider.root, "outgoing"); assertTrue(outgoing.mkdir());
        engine().copy(Collections.singletonList(new LocalTransferNode(share, downloaded)), tree("outgoing"), null);
        assertArrayEquals(payload, Files.readAllBytes(new File(outgoing, "项目🌟/视频.mp4").toPath()));
        assertTrue(new File(outgoing, "项目🌟/empty").isDirectory());
    }

    @Test public void exportKeepsAnExistingDocumentAndCreatesARenamedCopy() throws Exception {
        File local = temp.newFolder("shared"); File source = new File(local, "report.txt"); Files.write(source.toPath(), new byte[]{2});
        File existing = new File(provider.root, "report.txt"); Files.write(existing.toPath(), new byte[]{1});
        engine().copy(Collections.singletonList(new LocalTransferNode(local, source)), tree(""), null);
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(existing.toPath()));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(new File(provider.root, "report (2).txt").toPath()));
    }

    @Test public void revokedProviderAccessDoesNotBecomeAnEmptySuccessfulDirectory() throws Exception {
        provider.failListing = true;
        try { tree("").children(); fail(); } catch (IOException expected) {}
    }

    @Test public void misbehavingProviderCannotMakeExportTruncateOrDeleteAnExistingFile() throws Exception {
        File local = temp.newFolder("shared"); File source = new File(local, "new.txt"); Files.write(source.toPath(), new byte[]{2});
        File existing = new File(provider.root, "keep.txt"); Files.write(existing.toPath(), new byte[]{1});
        provider.returnExisting = "keep.txt";
        try { engine().copy(Collections.singletonList(new LocalTransferNode(local, source)), tree(""), null); fail(); }
        catch (IOException expected) {}
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(existing.toPath()));
        assertEquals(1, provider.root.listFiles().length);
    }

    @Test public void backgroundJobImportsOutsideTheShareThenExportsWithProgressOnAnOldFilesystem() throws Exception {
        File input = new File(provider.root, "phone"); assertTrue(input.mkdir());
        File directory = new File(input, "项目"); assertTrue(directory.mkdir());
        byte[] data = new byte[200_000]; new Random(19).nextBytes(data); Files.write(new File(directory, "原片.mp4").toPath(), data);
        File share = RdpSharedFolder.directory(context);
        File destination = new File(share, "test-" + UUID.randomUUID()); assertTrue(destination.mkdir());
        File keep = new File(destination, "项目"); assertTrue(keep.mkdir());
        SharedTransfers manager = ReflectionHelpers.callConstructor(SharedTransfers.class, ClassParameter.from(Context.class, context));
        List<Boolean> observed = new ArrayList<>(); SharedTransfers.Observer observer = status -> observed.add(status.busy);
        manager.observe(observer);
        try {
            Uri source = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "phone/项目");
            manager.importFiles(Collections.singletonList(source), destination.getName(), true);
            await(manager);
            assertEquals(R.string.ouse_transfer_done, manager.status().message); assertTrue(observed.contains(true));
            assertTrue(OldFilesystem.source.contains("OuseRDP-TransferWork"));
            File imported = new File(destination, "项目 (2)");
            assertTrue(keep.isDirectory()); assertEquals(0, keep.listFiles().length);
            assertArrayEquals(data, Files.readAllBytes(new File(imported, "原片.mp4").toPath()));
            File output = new File(provider.root, "saved"); assertTrue(output.mkdir());
            manager.exportFiles(Collections.singletonList(destination.getName() + File.separator + imported.getName()),
                    DocumentsContract.buildTreeDocumentUri(AUTHORITY, "saved"));
            await(manager);
            assertEquals(R.string.ouse_transfer_done, manager.status().message);
            assertArrayEquals(data, Files.readAllBytes(new File(output, "项目 (2)/原片.mp4").toPath()));
        } finally {
            manager.remove(observer);
            ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(manager, "worker")).shutdownNow();
            new LocalTransferNode(share, destination).delete();
        }
    }
    private void await(SharedTransfers manager) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (manager.status().busy && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); assertFalse("Background copy did not finish", manager.status().busy);
    }

    public static class Provider extends DocumentsProvider {
        File root;
        boolean failListing;
        String returnExisting;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(new String[]{"root_id", "document_id", "title"}); }
        private File file(String id) throws FileNotFoundException {
            try {
                File file = id.equals("root") ? root : new File(root, id);
                if (!file.getCanonicalPath().equals(root.getPath()) && !file.getCanonicalPath().startsWith(root.getPath() + File.separator)) throw new IOException();
                return file;
            } catch (IOException error) { throw new FileNotFoundException(); }
        }
        private String id(File file) { return file.equals(root) ? "root" : file.getAbsolutePath().substring(root.getPath().length() + 1); }
        @Override public boolean isChildDocument(String parent, String child) {
            try { return file(child).getAbsolutePath().startsWith(file(parent).getAbsolutePath() + File.separator); }
            catch (FileNotFoundException error) { return false; }
        }
        private MatrixCursor cursor(String[] projection) {
            return new MatrixCursor(projection == null ? new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED} : projection);
        }
        private void add(MatrixCursor cursor, File file) {
            Object[] values = new Object[cursor.getColumnCount()];
            for (int i = 0; i < values.length; i++) switch (cursor.getColumnNames()[i]) {
                case Document.COLUMN_DOCUMENT_ID: values[i] = id(file); break;
                case Document.COLUMN_DISPLAY_NAME: values[i] = file.getName(); break;
                case Document.COLUMN_MIME_TYPE: values[i] = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream"; break;
                case Document.COLUMN_SIZE: values[i] = file.length(); break;
                case Document.COLUMN_LAST_MODIFIED: values[i] = file.lastModified(); break;
            }
            cursor.addRow(values);
        }
        @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
            MatrixCursor cursor = cursor(projection); File file = file(documentId); if (!file.exists()) throw new FileNotFoundException(); add(cursor, file); return cursor;
        }
        @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) throws FileNotFoundException {
            if (failListing) return null;
            MatrixCursor cursor = cursor(projection); File[] children = file(parent).listFiles();
            if (children == null) throw new FileNotFoundException(); for (File child : children) add(cursor, child); return cursor;
        }
        @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
            if (returnExisting != null) return returnExisting;
            File child = new File(file(parent), name);
            try {
                if (Document.MIME_TYPE_DIR.equals(mime) ? !child.mkdir() : !child.createNewFile()) throw new IOException();
                return id(child);
            } catch (IOException error) { throw new FileNotFoundException(); }
        }
        @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
        }
        @Override public void deleteDocument(String id) throws FileNotFoundException {
            File file = file(id);
            try { new LocalTransferNode(root, file).delete(); } catch (IOException error) { throw new FileNotFoundException(); }
        }
    }
}
