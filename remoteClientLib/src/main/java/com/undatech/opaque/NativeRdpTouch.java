package com.undatech.opaque;

import android.util.Log;

/** RDPEI bridge compiled against the headers shipped with the FreeRDP 2.11.7 binaries. */
public final class NativeRdpTouch {
    private static final boolean loaded;
    static {
        boolean success = false;
        try { System.loadLibrary("ouse-rdpei"); success = true; }
        catch (UnsatisfiedLinkError error) { Log.w("OuseTouch", "Native touch unavailable", error); }
        loaded = success;
    }
    private NativeRdpTouch() {}
    public static boolean attach(long instance) { return loaded && nativeAttach(instance); }
    public static boolean ready(long instance) { return loaded && nativeReady(instance); }
    public static void detach(long instance) { if (loaded) nativeDetach(instance); }
    public static boolean send(long instance, int action, int id, int x, int y) {
        return loaded && nativeSend(instance, action, id, x, y);
    }
    public static void cancel(long instance) { if (loaded) nativeCancel(instance); }
    private static native boolean nativeAttach(long instance);
    private static native boolean nativeReady(long instance);
    private static native void nativeDetach(long instance);
    private static native boolean nativeSend(long instance, int action, int id, int x, int y);
    private static native void nativeCancel(long instance);
}
