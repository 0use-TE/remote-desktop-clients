package com.iiordanov.bVNC;

import android.util.Pair;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WindowsKeyCapture {
    interface KeySender {
        void send(KeyEvent event);
    }

    private final Map<Pair<Integer, Integer>, KeyEvent> pressed = new LinkedHashMap<>();
    private final Set<Pair<Integer, Integer>> awaitingRelease = new HashSet<>();

    boolean onKeyEvent(KeyEvent event, boolean canStart, boolean captureAltTab, KeySender sender) {
        int device = event.getDeviceId();
        int code = event.getKeyCode();
        Pair<Integer, Integer> key = Pair.create(device, code);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (awaitingRelease.remove(key)) {
                return true;
            }
            KeyEvent original = pressed.remove(key);
            if (original != null) {
                sender.send(withKeyCode(event, original.getKeyCode()));
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (event.getRepeatCount() > 0) {
            KeyEvent original = pressed.get(key);
            if (original != null) {
                sender.send(withKeyCode(event, original.getKeyCode()));
                return true;
            }
            return awaitingRelease.contains(key);
        }

        awaitingRelease.remove(key);
        boolean windowsKey = code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT;
        boolean windowsChord = event.isMetaPressed()
                && (pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_META_LEFT))
                    || pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_META_RIGHT)));
        boolean altKey = code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT;
        boolean altHeld = pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_ALT_LEFT))
                || pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_ALT_RIGHT));
        boolean altShortcutKey = code == KeyEvent.KEYCODE_TAB || code == KeyEvent.KEYCODE_F4
                || code == KeyEvent.KEYCODE_MUTE
                || code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT;
        if (!canStart || !(windowsKey || windowsChord || (captureAltTab && (altKey || (altHeld && altShortcutKey))))) {
            return false;
        }

        // Some Android keyboards report their F4/microphone key as mute even with Alt held.
        KeyEvent forwarded = captureAltTab && altHeld && code == KeyEvent.KEYCODE_MUTE
                ? withKeyCode(event, KeyEvent.KEYCODE_F4) : event;
        pressed.put(key, new KeyEvent(forwarded));
        sender.send(forwarded);
        return true;
    }

    private static KeyEvent withKeyCode(KeyEvent event, int keyCode) {
        if (event.getKeyCode() == keyCode) {
            return event;
        }
        return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), keyCode,
                event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                event.getFlags(), event.getSource());
    }

    void releaseKeys(KeySender sender) {
        List<KeyEvent> events = new ArrayList<>(pressed.values());
        awaitingRelease.addAll(pressed.keySet());
        pressed.clear();
        // Release the chord before the modifiers that started it.
        for (int index = events.size() - 1; index >= 0; index--) {
            sender.send(KeyEvent.changeAction(events.get(index), KeyEvent.ACTION_UP));
        }
    }

    void reset() {
        pressed.clear();
        awaitingRelease.clear();
    }
}
