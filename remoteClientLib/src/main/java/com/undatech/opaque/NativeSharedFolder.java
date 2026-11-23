package com.undatech.opaque;

import java.nio.charset.StandardCharsets;

/** Same-filesystem publication without replacing an existing Windows upload. */
public final class NativeSharedFolder {
    public static final int OK = 0, EXISTS = 1, UNSUPPORTED = 2, FAILED = 3;
    private NativeSharedFolder() {}
    public static boolean configure(long instance, String path) {
        return NativeRdpTouch.available() && nativeConfigure(instance, path.getBytes(StandardCharsets.UTF_8));
    }
    public static int publish(String source, String target) {
        if (!NativeRdpTouch.available()) return UNSUPPORTED;
        return nativePublish(source.getBytes(StandardCharsets.UTF_8), target.getBytes(StandardCharsets.UTF_8));
    }
    private static native int nativePublish(byte[] source, byte[] target);
    private static native boolean nativeConfigure(long instance, byte[] path);
}
