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

    boolean onKeyEvent(KeyEvent event, boolean canStart, KeySender sender) {
        int device = event.getDeviceId();
        int code = event.getKeyCode();
        Pair<Integer, Integer> key = Pair.create(device, code);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (awaitingRelease.remove(key)) {
                return true;
            }
            if (pressed.remove(key) != null) {
                sender.send(event);
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (event.getRepeatCount() > 0) {
            if (pressed.containsKey(key)) {
                sender.send(event);
                return true;
            }
            return awaitingRelease.contains(key);
        }

        awaitingRelease.remove(key);
        boolean windowsKey = code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT;
        boolean windowsChord = event.isMetaPressed()
                && (pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_META_LEFT))
                    || pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_META_RIGHT)));
        if (!canStart || (!windowsKey && !windowsChord)) {
            return false;
        }

        pressed.put(key, new KeyEvent(event));
        sender.send(event);
        return true;
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
