package com.undatech.opaque;

/** Image offers on the existing CLIPRDR channel. No file transfer or screen encoding. */
public final class NativeRdpClipboard {
    private NativeRdpClipboard() {}
    public static boolean ready(long instance) { return NativeRdpTouch.available() && nativeReady(instance); }
    public static boolean send(long instance, byte[] png, byte[] dibV5) {
        return NativeRdpTouch.available() && nativeSend(instance, png, dibV5);
    }
    public static void clear(long instance) { if (NativeRdpTouch.available()) nativeClear(instance); }
    private static native boolean nativeReady(long instance);
    private static native boolean nativeSend(long instance, byte[] png, byte[] dibV5);
    private static native void nativeClear(long instance);
}
