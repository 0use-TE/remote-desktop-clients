package io.github.ouse.ouserdp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class LocalTransferNode implements TransferNode {
    private final File root;
    final File file;

    LocalTransferNode(File root, File file) throws IOException {
        this.root = root.getCanonicalFile();
        File canonical = file.getCanonicalFile();
        if (!canonical.equals(this.root) && !canonical.getPath().startsWith(this.root.getPath() + File.separator))
            throw new IOException("Path outside shared folder");
        // Never follow links, including links to another entry inside the share.
        if (!file.getAbsoluteFile().equals(canonical)) throw new IOException("Linked paths are unsupported");
        this.file = canonical;
    }

    @Override public String name() { return file.getName(); }
    @Override public String identity() { return file.getAbsolutePath(); }
    @Override public boolean directory() { return file.isDirectory(); }
    @Override public String mimeType() {
        if (directory()) return "vnd.android.document/directory";
        String type = java.net.URLConnection.guessContentTypeFromName(name());
        return type == null ? "application/octet-stream" : type;
    }
    @Override public long size() { return directory() ? 0 : file.length(); }
    @Override public long modified() { return file.lastModified(); }

    @Override public List<TransferNode> children() throws IOException {
        File[] children = file.listFiles();
        if (children == null) throw new IOException("Cannot list directory");
        List<TransferNode> result = new ArrayList<>();
        for (File child : children) result.add(new LocalTransferNode(root, child));
        return result;
    }
    @Override public InputStream read() throws IOException { return new FileInputStream(file); }
    @Override public OutputStream write() throws IOException { return new FileOutputStream(file, false); }
    @Override public TransferNode create(String name, String mime, boolean directory) throws IOException {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.length() > 240 || name.contains("/") || name.contains("\\"))
            throw new IOException("Invalid filename");
        LocalTransferNode child = new LocalTransferNode(root, new File(file, name));
        if (directory ? !child.file.mkdir() : !child.file.createNewFile()) throw new IOException("Cannot create destination");
        return child;
    }
    @Override public void delete() throws IOException {
        if (!file.exists()) return;
        if (directory()) for (TransferNode child : children()) child.delete();
        if (!file.delete()) throw new IOException("Cannot remove partial copy");
    }
    @Override public void completed(long modified) { if (modified > 0) file.setLastModified(modified); }
    @Override public void verify(long size, long modified) throws IOException {
        if (!file.exists() || (!directory() && file.length() != size) || (modified > 0 && file.lastModified() != modified))
            throw new TransferEngine.SourceChangedException();
    }
}
