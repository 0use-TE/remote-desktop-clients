package io.github.ouse.ouserdp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded-memory recursive copying, with one newly created root as the rollback boundary. */
final class TransferEngine {
    interface Cancel { boolean requested(); }
    interface Progress { void update(String file, long bytes, long total, int files, int totalFiles); }
    interface Commit { void apply(TransferNode staged, String desiredName, boolean directory) throws IOException; }
    private final Cancel cancel;
    private final Progress progress;
    private final byte[] buffer = new byte[128 * 1024];
    private long bytes, total;
    private int files, totalFiles, entries;
    private boolean unknownSize;

    TransferEngine(Cancel cancel, Progress progress) { this.cancel = cancel; this.progress = progress; }

    private static final class Plan {
        final TransferNode node;
        final boolean directory;
        final long size, modified;
        final List<Plan> children = new ArrayList<>();
        Plan(TransferNode node) {
            this.node = node; directory = node.directory(); size = node.size(); modified = node.modified();
        }
    }

    void copy(List<TransferNode> sources, TransferNode destination, Commit commit) throws IOException {
        List<Plan> plans = new ArrayList<>();
        Set<String> roots = new HashSet<>();
        for (TransferNode source : sources) if (roots.add(source.identity())) plans.add(plan(source, new HashSet<>(), 0));
        Set<String> names = names(destination);
        for (Plan plan : plans) {
            checkCancelled();
            String name = uniqueName(plan.node.name(), plan.directory, names);
            TransferNode target = destination.create(name, plan.node.mimeType(), plan.directory);
            try {
                copy(plan, target);
                checkCancelled();
                if (commit != null) commit.apply(target, plan.node.name(), plan.directory);
            } catch (IOException | RuntimeException error) {
                try { target.delete(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
        }
        progress.update("", bytes, unknownSize ? -1 : total, files, totalFiles);
    }

    private Plan plan(TransferNode node, Set<String> ancestors, int depth) throws IOException {
        checkCancelled();
        if (++entries > 100_000 || depth > 64 || !ancestors.add(node.identity())) throw new IOException("Directory tree is too deep or cyclic");
        Plan result = new Plan(node);
        if (result.directory) {
            for (TransferNode child : node.children()) result.children.add(plan(child, ancestors, depth + 1));
        } else {
            totalFiles++;
            if (result.size < 0) unknownSize = true;
            else total = result.size > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + result.size;
        }
        ancestors.remove(node.identity());
        return result;
    }

    private void copy(Plan source, TransferNode target) throws IOException {
        checkCancelled();
        source.node.verify(source.size, source.modified);
        progress.update(source.node.name(), bytes, unknownSize ? -1 : total, files, totalFiles);
        if (source.directory) {
            Set<String> names = names(target);
            for (Plan child : source.children) {
                checkCancelled();
                String name = uniqueName(child.node.name(), child.directory, names);
                copy(child, target.create(name, child.node.mimeType(), child.directory));
            }
        } else {
            long copied = 0;
            try (InputStream input = source.node.read(); OutputStream output = target.write()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (count == 0) continue;
                    output.write(buffer, 0, count);
                    copied += count;
                    bytes = count > Long.MAX_VALUE - bytes ? Long.MAX_VALUE : bytes + count;
                    progress.update(source.node.name(), bytes, unknownSize ? -1 : total, files, totalFiles);
                }
                output.flush();
            }
            if (source.size >= 0 && copied != source.size) throw new SourceChangedException();
            files++;
        }
        source.node.verify(source.size, source.modified);
        target.completed(source.modified);
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancel.requested() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Copy cancelled");
    }

    static Set<String> names(TransferNode node) throws IOException {
        Set<String> names = new HashSet<>();
        for (TransferNode child : node.children()) names.add(child.name().toLowerCase(Locale.ROOT));
        return names;
    }

    static String safeName(String name) {
        String value = name == null ? "" : name.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]", "_").replaceAll("[ .]+$", "");
        if (value.isEmpty() || value.equals(".") || value.equals("..")) value = "_";
        if (value.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")) value = "_" + value;
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 220) {
            int dot = value.lastIndexOf('.');
            String extension = dot > 0 ? value.substring(dot) : "";
            int extensionBytes = extension.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            value = extensionBytes > 32 ? truncateUtf8(value, 220)
                    : truncateUtf8(dot > 0 ? value.substring(0, dot) : value, 220 - extensionBytes) + extension;
        }
        return value;
    }

    private static String truncateUtf8(String value, int limit) {
        int offset = 0, bytes = 0;
        while (offset < value.length()) {
            int point = value.codePointAt(offset);
            int size = point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            if (bytes + size > limit) break;
            bytes += size; offset += Character.charCount(point);
        }
        return value.substring(0, offset);
    }

    static String uniqueName(String desired, boolean directory, Set<String> occupied) {
        String name = safeName(desired), stem = name, extension = "";
        int dot = name.lastIndexOf('.');
        if (!directory && dot > 0) { stem = name.substring(0, dot); extension = name.substring(dot); }
        String candidate = name;
        int suffix = 2;
        while (!occupied.add(candidate.toLowerCase(Locale.ROOT))) candidate = stem + " (" + suffix++ + ")" + extension;
        return candidate;
    }

    static final class SourceChangedException extends IOException {}
}
