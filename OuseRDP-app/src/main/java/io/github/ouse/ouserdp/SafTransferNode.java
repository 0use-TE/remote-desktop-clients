package io.github.ouse.ouserdp;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SafTransferNode implements TransferNode {
    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
    private final ContentResolver resolver;
    private final Uri uri;
    private final String name, mime;
    private final long size, modified;
    private Set<String> knownChildren;

    private SafTransferNode(ContentResolver resolver, Uri uri, String name, String mime, long size, long modified) {
        this.resolver = resolver; this.uri = uri; this.name = name; this.mime = mime; this.size = size; this.modified = modified;
    }
    static SafTransferNode document(ContentResolver resolver, Uri uri) throws IOException {
        try (Cursor cursor = query(resolver, uri)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Document unavailable");
            return row(resolver, uri, cursor);
        }
    }
    private static Cursor query(ContentResolver resolver, Uri uri) {
        return Build.VERSION.SDK_INT >= 26 ? resolver.query(uri, COLUMNS, (Bundle) null, null)
                : resolver.query(uri, COLUMNS, null, null, null);
    }
    static SafTransferNode tree(ContentResolver resolver, Uri tree) throws IOException {
        return document(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)));
    }
    private static SafTransferNode row(ContentResolver resolver, Uri uri, Cursor cursor) {
        return new SafTransferNode(resolver, uri, cursor.getString(1), cursor.getString(2),
                cursor.isNull(3) ? -1 : cursor.getLong(3), cursor.isNull(4) ? 0 : cursor.getLong(4));
    }
    @Override public String name() { return name; }
    @Override public String identity() {
        return uri.getAuthority() + ":" + DocumentsContract.getDocumentId(uri);
    }
    @Override public String mimeType() { return mime == null ? "application/octet-stream" : mime; }
    @Override public boolean directory() { return Document.MIME_TYPE_DIR.equals(mime); }
    @Override public long size() { return size; }
    @Override public long modified() { return modified; }
    @Override public List<TransferNode> children() throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        List<TransferNode> result = new ArrayList<>();
        knownChildren = new HashSet<>();
        try (Cursor cursor = query(resolver, children)) {
            if (cursor == null) throw new IOException("Directory unavailable");
            while (cursor.moveToNext()) {
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0));
                SafTransferNode child = row(resolver, childUri, cursor);
                result.add(child); knownChildren.add(child.identity());
            }
        }
        return result;
    }
    @Override public InputStream read() throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("Cannot read document");
        return input;
    }
    @Override public OutputStream write() throws IOException {
        OutputStream output = resolver.openOutputStream(uri, "wt");
        if (output == null) throw new IOException("Cannot write document");
        return output;
    }
    @Override public TransferNode create(String name, String mime, boolean directory) throws IOException {
        if (knownChildren == null) children();
        Uri created = DocumentsContract.createDocument(resolver, uri, directory ? Document.MIME_TYPE_DIR : mime, name);
        if (created == null) throw new IOException("Cannot create document");
        String identity = created.getAuthority() + ":" + DocumentsContract.getDocumentId(created);
        if (knownChildren.contains(identity)) throw new IOException("Provider returned an existing document");
        try {
            SafTransferNode child = document(resolver, created);
            knownChildren.add(child.identity()); return child;
        } catch (IOException | RuntimeException error) {
            try { DocumentsContract.deleteDocument(resolver, created); } catch (IOException | RuntimeException ignored) {}
            throw error;
        }
    }
    @Override public void delete() throws IOException {
        if (!DocumentsContract.deleteDocument(resolver, uri)) throw new IOException("Cannot remove partial document");
    }
    @Override public void verify(long size, long modified) throws IOException {
        SafTransferNode current = document(resolver, uri);
        if ((!directory() && size >= 0 && current.size >= 0 && size != current.size)
                || (modified > 0 && current.modified > 0 && modified != current.modified))
            throw new TransferEngine.SourceChangedException();
    }
}
