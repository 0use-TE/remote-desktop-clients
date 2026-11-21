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
    private final Set<Integer> localShortcutDevices = new HashSet<>();
    private boolean androidKeyboardMode;
    private final Set<Pair<Integer, Integer>> physicalModifiers = new HashSet<>();

    boolean onKeyEvent(KeyEvent event, boolean canStart, boolean captureExtendedShortcuts, KeySender sender) {
        int device = event.getDeviceId();
        int code = captureExtendedShortcuts || localShortcutDevices.contains(device)
                ? resolveDesktopKey(event) : event.getKeyCode();
        Pair<Integer, Integer> key = Pair.create(device, code);
        if (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT
                || code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) physicalModifiers.add(key);
            else if (event.getAction() == KeyEvent.ACTION_UP) physicalModifiers.remove(key);
        }
        // Finish consuming the mode-switch chord even after Android takes focus away.
        // No partially held modifiers may reach Windows when the session regains focus.
        if (localShortcutDevices.contains(device)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                awaitingRelease.remove(key);
                if (awaitingRelease.stream().noneMatch(held -> held.first == device)) {
                    localShortcutDevices.remove(device);
                }
            } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
                awaitingRelease.add(key);
            }
            return true;
        }
        if (canStart && captureExtendedShortcuts && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && code == KeyEvent.KEYCODE_F12
                && event.isCtrlPressed() && event.isAltPressed()
                && !event.isMetaPressed() && !event.isShiftPressed()) {
            localShortcutDevices.add(device);
            releaseKeys(sender);
            for (Pair<Integer, Integer> modifier : physicalModifiers) {
                if (modifier.first == device) awaitingRelease.add(modifier);
            }
            awaitingRelease.add(key);
            androidKeyboardMode = !androidKeyboardMode;
            return true;
        }
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

        if (androidKeyboardMode) return false;

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
        boolean ctrlKey = code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT;
        boolean altHeld = pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_ALT_LEFT))
                || pressed.containsKey(Pair.create(device, KeyEvent.KEYCODE_ALT_RIGHT));
        boolean altShortcutKey = code == KeyEvent.KEYCODE_TAB || code == KeyEvent.KEYCODE_MUTE
                || code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT;
        boolean extendedShortcut = captureExtendedShortcuts
                && (code == KeyEvent.KEYCODE_ESCAPE || isFunctionKey(code)
                    || altKey || ctrlKey || (altHeld && altShortcutKey));
        if (!canStart || !(windowsKey || windowsChord || extendedShortcut)) {
            return false;
        }

        // Some Android keyboards report their F4/microphone key as mute even with Alt held.
        int forwardedCode = captureExtendedShortcuts && altHeld && code == KeyEvent.KEYCODE_MUTE
                ? KeyEvent.KEYCODE_F4 : code;
        KeyEvent forwarded = withKeyCode(event, forwardedCode);
        pressed.put(key, new KeyEvent(forwarded));
        sender.send(forwarded);
        return true;
    }

    private static boolean isFunctionKey(int code) {
        return (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12)
                || (code >= KeyEvent.KEYCODE_F13 && code <= KeyEvent.KEYCODE_F24);
    }

    static int resolveEscape(KeyEvent event) {
        int code = event.getKeyCode();
        // Linux KEY_ESC is 1. KEY_BACK (158) and Android gestures retain their usual behavior.
        if ((code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_UNKNOWN)
                && event.getScanCode() == 1) {
            return KeyEvent.KEYCODE_ESCAPE;
        }
        return code;
    }

    private static int resolveDesktopKey(KeyEvent event) {
        int code = resolveEscape(event);
        switch (code) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_BRIGHTNESS_DOWN:
            case KeyEvent.KEYCODE_BRIGHTNESS_UP:
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN:
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP:
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                // For an OEM remap, use the underlying Linux code only if it identifies an F key.
                int scan = event.getScanCode();
                if (scan >= 59 && scan <= 68) {
                    return KeyEvent.KEYCODE_F1 + scan - 59;
                }
                if (scan == 87 || scan == 88) {
                    return KeyEvent.KEYCODE_F11 + scan - 87;
                }
                if (scan >= 183 && scan <= 194) {
                    return KeyEvent.KEYCODE_F13 + scan - 183;
                }
                break;
            default:
                break;
        }
        return code;
    }

    static KeyEvent withKeyCode(KeyEvent event, int keyCode) {
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
        localShortcutDevices.clear();
        androidKeyboardMode = false;
        physicalModifiers.clear();
    }

    boolean isAndroidKeyboardMode() { return androidKeyboardMode; }
    void useRemoteKeyboard() { androidKeyboardMode = false; }

    void inputDeviceRemoved(int deviceId, KeySender sender) {
        releaseKeys(sender);
        localShortcutDevices.remove(deviceId);
        awaitingRelease.removeIf(key -> key.first == deviceId);
        physicalModifiers.removeIf(key -> key.first == deviceId);
    }
}
