package com.undatech.opaque;

import android.content.Context;
import java.io.File;
import java.io.IOException;

/** The one app-owned transfer directory exported by OuseRDP. */
public final class RdpSharedFolder {
    public static final String DRIVE_NAME = "OuseRDP";
    public static final String WINDOWS_PATH = "\\\\tsclient\\OuseRDP";
    private RdpSharedFolder() {}

    public static File directory(Context context) throws IOException {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File directory = new File(base, "OuseRDP-Shared");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
            throw new IOException("Shared folder unavailable");
        return directory.getCanonicalFile();
    }
}
