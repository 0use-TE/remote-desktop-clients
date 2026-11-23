package com.undatech.opaque;

import java.util.HashSet;
import java.util.Set;

/** Track only currently held input so loss of focus can release it. Never logged. */
public final class RdpInputState {
    public interface Sink {
        void keyUp(int key);
        void unicodeUp(int code);
        void mouseUp(int x, int y, int button);
    }
    private final Set<Integer> keys = new HashSet<>(), unicode = new HashSet<>();
    private int buttons, x, y;
    public synchronized void key(int code, boolean down, boolean isUnicode) {
        Set<Integer> held = isUnicode ? unicode : keys;
        if (down) held.add(code); else held.remove(code);
    }
    public synchronized void pointer(int px, int py, int flags) {
        if ((flags & 0x0600) != 0) return; // Wheel events do not move the pointer or hold buttons.
        x = px; y = py;
        int changed = flags & 0x7000;
        if ((flags & 0x8000) != 0) buttons |= changed; else buttons &= ~changed;
    }
    public synchronized void release(Sink sink) {
        for (int code : keys) sink.keyUp(code);
        for (int code : unicode) sink.unicodeUp(code);
        for (int bit : new int[]{0x1000, 0x2000, 0x4000}) if ((buttons & bit) != 0) sink.mouseUp(x, y, bit);
        clear();
    }
    public synchronized void clear() { keys.clear(); unicode.clear(); buttons = 0; }
}
