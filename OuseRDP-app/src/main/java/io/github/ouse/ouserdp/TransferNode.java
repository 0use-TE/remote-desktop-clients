package io.github.ouse.ouserdp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** A filesystem entry or one explicitly granted SAF document. */
interface TransferNode {
    String name();
    String identity();
    String mimeType();
    boolean directory();
    long size();
    long modified();
    List<TransferNode> children() throws IOException;
    InputStream read() throws IOException;
    OutputStream write() throws IOException;
    TransferNode create(String name, String mime, boolean directory) throws IOException;
    void delete() throws IOException;
    default void completed(long modified) throws IOException {}
    default void verify(long size, long modified) throws IOException {}
}
