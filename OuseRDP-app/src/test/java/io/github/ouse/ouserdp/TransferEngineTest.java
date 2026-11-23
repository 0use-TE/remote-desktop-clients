package io.github.ouse.ouserdp;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TransferEngineTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private LocalTransferNode node(File root, File entry) throws IOException { return new LocalTransferNode(root, entry); }
    private TransferEngine engine() { return new TransferEngine(() -> false, (name, bytes, total, files, all) -> {}); }

    @Test public void recursiveCopyKeepsUnicodeNamesEmptyFoldersAndOriginalVideoBytes() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File folder = new File(source, "相册🌟"); assertTrue(folder.mkdir());
        assertTrue(new File(folder, "空目录").mkdir());
        byte[] bytes = new byte[350_000]; new Random(42).nextBytes(bytes);
        Files.write(new File(folder, "原片.mp4").toPath(), bytes);
        engine().copy(Collections.singletonList(node(source, folder)), node(destination, destination), null);
        assertArrayEquals(bytes, Files.readAllBytes(new File(destination, "相册🌟/原片.mp4").toPath()));
        assertTrue(new File(destination, "相册🌟/空目录").isDirectory());
    }

    @Test public void caseInsensitiveCollisionsPreserveExistingFilesAndBothFolders() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File file = new File(source, "Photo.PNG"); Files.write(file.toPath(), new byte[]{2});
        Files.write(new File(destination, "photo.png").toPath(), new byte[]{1});
        File folder = new File(source, "照片"); assertTrue(folder.mkdir()); assertTrue(new File(destination, "照片").mkdir());
        engine().copy(Arrays.asList(node(source, file), node(source, folder)), node(destination, destination), null);
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(new File(destination, "photo.png").toPath()));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(new File(destination, "Photo (2).PNG").toPath()));
        assertTrue(new File(destination, "照片 (2)").isDirectory());
    }

    @Test public void cancellationRemovesOnlyTheCurrentPartialRoot() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File complete = new File(source, "complete.txt"), partial = new File(source, "partial.mp4");
        Files.write(complete.toPath(), new byte[]{1, 2}); Files.write(partial.toPath(), new byte[400_000]);
        AtomicBoolean cancel = new AtomicBoolean();
        TransferEngine engine = new TransferEngine(cancel::get, (name, bytes, total, files, all) -> { if (bytes > 128_000) cancel.set(true); });
        try { engine.copy(Arrays.asList(node(source, complete), node(source, partial)), node(destination, destination), null); fail(); }
        catch (InterruptedIOException expected) {}
        assertTrue(new File(destination, "complete.txt").isFile()); assertFalse(new File(destination, "partial.mp4").exists());
        assertTrue(partial.isFile());
    }

    @Test public void changingWindowsFileIsRejectedAndPartialExportRemoved() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File changing = new File(source, "upload.mp4"); Files.write(changing.toPath(), new byte[400_000]);
        AtomicBoolean changed = new AtomicBoolean();
        TransferEngine engine = new TransferEngine(() -> false, (name, bytes, total, files, all) -> {
            if (bytes > 0 && changed.compareAndSet(false, true)) {
                try (FileOutputStream append = new FileOutputStream(changing, true)) { append.write(1); }
                catch (IOException error) { throw new UncheckedIOException(error); }
            }
        });
        try { engine.copy(Collections.singletonList(node(source, changing)), node(destination, destination), null); fail(); }
        catch (TransferEngine.SourceChangedException expected) {}
        assertFalse(new File(destination, "upload.mp4").exists()); assertEquals(400_001, changing.length());
    }

    @Test public void providerReadFailureRollsBackTheNewFolderWithoutRemovingOlderContent() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File file = new File(source, "broken.bin"); Files.write(file.toPath(), new byte[]{1});
        Files.write(new File(destination, "keep.txt").toPath(), new byte[]{9});
        TransferNode broken = new Forward(node(source, file)) {
            @Override public InputStream read() { return new InputStream() { @Override public int read() throws IOException { throw new IOException("Provider disconnected"); } }; }
        };
        try { engine().copy(Collections.singletonList(broken), node(destination, destination), null); fail(); }
        catch (IOException expected) {}
        assertEquals(1, destination.listFiles().length); assertTrue(new File(destination, "keep.txt").exists());
    }

    @Test public void largeVideosUseLongCountersAndOnly128KiBChunks() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File seed = new File(source, "large.mp4"); assertTrue(seed.createNewFile());
        long length = (1L << 31) + 123;
        long[] written = {0}, progress = {0};
        TransferNode generated = new Forward(node(source, seed)) {
            @Override public long size() { return length; }
            @Override public void verify(long size, long modified) {}
            @Override public InputStream read() {
                return new InputStream() {
                    long remaining = length;
                    @Override public int read() { throw new AssertionError("Byte-at-a-time copying"); }
                    @Override public int read(byte[] bytes) {
                        assertTrue(bytes.length <= 128 * 1024);
                        if (remaining == 0) return -1;
                        int count = (int) Math.min(remaining, bytes.length); remaining -= count; return count;
                    }
                };
            }
        };
        TransferNode sink = new Forward(node(destination, destination)) {
            @Override public TransferNode create(String name, String mime, boolean directory) throws IOException {
                return new Forward(super.create(name, mime, directory)) {
                    @Override public OutputStream write() {
                        return new OutputStream() {
                            @Override public void write(int b) { throw new AssertionError(); }
                            @Override public void write(byte[] bytes, int offset, int count) { written[0] += count; }
                        };
                    }
                };
            }
        };
        new TransferEngine(() -> false, (name, bytes, total, files, all) -> { progress[0] = bytes; assertEquals(length, total); })
                .copy(Collections.singletonList(generated), sink, null);
        assertEquals(length, written[0]); assertEquals(length, progress[0]);
    }

    @Test public void unknownSourceSizeStillStreamsAndCompletes() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File file = new File(source, "cloud.txt"); Files.write(file.toPath(), new byte[]{7, 8});
        TransferNode unknown = new Forward(node(source, file)) {
            @Override public long size() { return -1; }
            @Override public void verify(long size, long modified) {}
        };
        new TransferEngine(() -> false, (name, bytes, total, files, all) -> assertEquals(-1, total))
                .copy(Collections.singletonList(unknown), node(destination, destination), null);
        assertArrayEquals(new byte[]{7, 8}, Files.readAllBytes(new File(destination, "cloud.txt").toPath()));
    }

    @Test public void pathsCannotEscapeTheShareAndNamesRemainWindowsCompatible() throws Exception {
        File root = temp.newFolder("shared");
        try { node(root, new File(root, "../outside.txt")); fail(); } catch (IOException expected) {}
        assertEquals("_CON.txt", TransferEngine.safeName("CON.txt"));
        assertEquals("a_b_c_", TransferEngine.safeName("a/b:c?. "));
        assertEquals("_", TransferEngine.safeName(".."));
        Set<String> occupied = new HashSet<>(); String longName = new String(new char[300]).replace('\0', 'a');
        String first = TransferEngine.uniqueName(longName, false, occupied), second = TransferEngine.uniqueName(longName, false, occupied);
        assertTrue(first.length() <= 240 && second.length() <= 240); assertNotEquals(first, second);
        String chinese = TransferEngine.safeName(new String(new char[200]).replace('\0', '照') + ".mp4");
        assertTrue(chinese.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 220); assertTrue(chinese.endsWith(".mp4"));
    }

    @Test public void shortProviderStreamCannotSilentlyProduceATruncatedSuccessfulCopy() throws Exception {
        File source = temp.newFolder("source"), destination = temp.newFolder("destination");
        File file = new File(source, "cloud.bin"); Files.write(file.toPath(), new byte[]{1, 2});
        TransferNode shortStream = new Forward(node(source, file)) {
            @Override public long size() { return 10; }
            @Override public void verify(long size, long modified) {}
        };
        try { engine().copy(Collections.singletonList(shortStream), node(destination, destination), null); fail(); }
        catch (TransferEngine.SourceChangedException expected) {}
        assertEquals(0, destination.listFiles().length);
    }

    private static class Forward implements TransferNode {
        final TransferNode node;
        Forward(TransferNode node) { this.node = node; }
        public String name() { return node.name(); } public String identity() { return node.identity(); }
        public String mimeType() { return node.mimeType(); } public boolean directory() { return node.directory(); }
        public long size() { return node.size(); } public long modified() { return node.modified(); }
        public List<TransferNode> children() throws IOException { return node.children(); }
        public InputStream read() throws IOException { return node.read(); } public OutputStream write() throws IOException { return node.write(); }
        public TransferNode create(String name, String mime, boolean directory) throws IOException { return node.create(name, mime, directory); }
        public void delete() throws IOException { node.delete(); } public void completed(long modified) throws IOException { node.completed(modified); }
        public void verify(long size, long modified) throws IOException { node.verify(size, modified); }
    }
}
