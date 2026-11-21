package com.iiordanov.bVNC;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

public final class WindowsKeyCaptureService extends AccessibilityService
        implements InputManager.InputDeviceListener {
    // The service callbacks and activity lifecycle both run on the main thread.
    private static final WindowsKeyCapture capture = new WindowsKeyCapture();
    private static RemoteCanvasActivity session;

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ComponentName component = new ComponentName(context, WindowsKeyCaptureService.class);
        for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (component.equals(ComponentName.unflattenFromString(service.getId()))) {
                return true;
            }
        }
        return false;
    }

    static void attach(RemoteCanvasActivity activity) {
        if (session != activity) {
            capture.releaseKeys(WindowsKeyCaptureService::sendKey);
            session = activity;
        }
    }

    static void detach(RemoteCanvasActivity activity) {
        if (session == activity) {
            releaseKeys(activity);
            session = null;
        }
    }

    static void releaseKeys(RemoteCanvasActivity activity) {
        if (session == activity) {
            capture.releaseKeys(activity::sendCapturedKey);
        }
    }

    private static void sendKey(KeyEvent event) {
        if (session != null) {
            session.sendCapturedKey(event);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ((InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(this, null);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        boolean sessionActive = session != null && session.hasWindowFocus()
                && session.canSendCapturedKeys();
        if (!sessionActive) {
            capture.releaseKeys(WindowsKeyCaptureService::sendKey);
        }
        InputDevice device = event.getDevice();
        boolean physicalKeyboard = event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                && device != null && !device.isVirtual()
                && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        return capture.onKeyEvent(event, sessionActive && physicalKeyboard,
                session != null && session.capturesExtendedShortcuts(),
                WindowsKeyCaptureService::sendKey);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service only requests key filtering.
    }

    @Override
    public void onInterrupt() {
        capture.releaseKeys(WindowsKeyCaptureService::sendKey);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        capture.releaseKeys(WindowsKeyCaptureService::sendKey);
        capture.reset();
        ((InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {}

    @Override
    public void onInputDeviceChanged(int deviceId) {}

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        capture.releaseKeys(WindowsKeyCaptureService::sendKey);
    }
}
