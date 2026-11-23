package com.undatech.opaque;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.Locale;

/** Bounded metadata only: never accepts addresses, credentials, clipboard or input payloads. */
public final class RdpSessionDiagnostics {
    private static volatile RdpSessionDiagnostics latest;
    private final long started = SystemClock.elapsedRealtime();
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private long updates, failures, copiedPixels, copyNanos, maxCopyNanos;
    private int width, height, scale, viewWidth, viewHeight;
    private boolean visible = true, displayControl, outputSuppressed;
    private String state = "CONNECTING";

    public static RdpSessionDiagnostics start() {
        RdpSessionDiagnostics result = new RdpSessionDiagnostics();
        latest = result;
        return result;
    }
    private void event(String value) {
        if (events.size() == 32) events.removeFirst();
        events.addLast((SystemClock.elapsedRealtime() - started) + " ms: " + value);
    }
    public synchronized void state(RdpCommunicator.ConnectionState value, int attempt, int delay) {
        state = value.name();
        event(state + " attempt=" + attempt + " delayMs=" + delay);
    }
    public synchronized void failure(long code) { event(String.format(Locale.ROOT, "error=0x%08x info=0x%08x", code >>> 32, code & 0xffffffffL)); }
    public synchronized void display(int w, int h, int desktopScale) {
        width = w; height = h; scale = desktopScale;
        event("desktop=" + w + "x" + h + " scale=" + desktopScale);
    }
    public synchronized void view(int w, int h) { viewWidth = w; viewHeight = h; }
    public synchronized void visibility(boolean shown, boolean suppressed) {
        visible = shown; outputSuppressed = suppressed;
        event("visible=" + shown + " outputSuppressed=" + suppressed);
    }
    public synchronized void capabilities(boolean supported) {
        if (displayControl != supported) { displayControl = supported; event("displayControl=" + supported); }
    }
    public synchronized void resize(boolean supported, boolean sent, int w, int h) {
        displayControl = supported;
        event("displayControl=" + supported + " resizeSent=" + sent + " target=" + w + "x" + h);
    }
    public synchronized void frame(int w, int h, long nanos, boolean copied) {
        if (copied) { updates++; copiedPixels += (long) w * h; copyNanos += nanos; maxCopyNanos = Math.max(maxCopyNanos, nanos); }
        else failures++;
    }
    public synchronized String report() {
        Runtime vm = Runtime.getRuntime();
        StringBuilder text = new StringBuilder("RDP session diagnostics\n")
                .append("State: ").append(state).append('\n')
                .append("Session elapsed ms: ").append(SystemClock.elapsedRealtime() - started).append('\n')
                .append("Viewport: ").append(viewWidth).append('x').append(viewHeight).append('\n')
                .append("Remote desktop: ").append(width).append('x').append(height).append('\n')
                .append("Windows scale: ").append(scale).append("%\n")
                .append("Local pixel format: ARGB_8888\n")
                .append("Visible: ").append(visible).append("; server output suppressed: ").append(outputSuppressed).append('\n')
                .append("Display control observed: ").append(displayControl).append('\n')
                .append("Copied updates: ").append(updates).append("; failed copies: ").append(failures).append('\n')
                .append("Copied pixels: ").append(copiedPixels).append('\n')
                .append("Copy mean/max ms: ").append(String.format(Locale.ROOT, "%.3f / %.3f", updates == 0 ? 0 : copyNanos / (updates * 1e6), maxCopyNanos / 1e6)).append('\n')
                .append("Java heap used bytes: ").append(vm.totalMemory() - vm.freeMemory()).append('\n')
                .append("Native heap allocated bytes: ").append(Debug.getNativeHeapAllocatedSize()).append('\n')
                .append("Recent events (up to 32):\n");
        for (String item : events) text.append(item).append('\n');
        return text.toString();
    }
    public void persist(Context context) {
        context.getSharedPreferences("rdpDiagnostics", Context.MODE_PRIVATE).edit().putString("lastReport", report()).apply();
    }
    public static String export(Context context) {
        RdpSessionDiagnostics current = latest;
        return current != null ? current.report() : context.getSharedPreferences("rdpDiagnostics", Context.MODE_PRIVATE)
                .getString("lastReport", "No RDP session recorded yet.\n");
    }
}
