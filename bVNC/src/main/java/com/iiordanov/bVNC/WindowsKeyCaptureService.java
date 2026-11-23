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
    private static volatile WindowsKeyCaptureService connectedService;
    private boolean inputListenerRegistered;

    /** Actual system binding in this process, independent of the settings switch. */
    public static boolean isConnected() { return connectedService != null; }
    private static java.lang.ref.WeakReference<RemoteCanvasActivity> modeOwner = new java.lang.ref.WeakReference<>(null);

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
            if (modeOwner.get() != activity) {
                capture.useRemoteKeyboard();
                modeOwner = new java.lang.ref.WeakReference<>(activity);
            }
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

    public static boolean isAndroidKeyboardMode(RemoteCanvasActivity activity) {
        return session == activity && capture.isAndroidKeyboardMode();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!inputListenerRegistered) {
            ((InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(this, null);
            inputListenerRegistered = true;
        }
        connectedService = this;
        android.util.Log.i("OuseKeyboard", "Accessibility service connected");
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
        boolean previousMode = capture.isAndroidKeyboardMode();
        boolean handled = capture.onKeyEvent(event, sessionActive && physicalKeyboard,
                session != null && session.capturesExtendedShortcuts(),
                WindowsKeyCaptureService::sendKey);
        if (session != null && previousMode != capture.isAndroidKeyboardMode()) {
            session.onKeyboardRoutingChanged(capture.isAndroidKeyboardMode());
        }
        return handled;
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
        cleanup();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        // An older instance may finish destroying after its replacement connected.
        // It must not clear the new connection or its keyboard state.
        if (connectedService == this) {
            connectedService = null;
            try { capture.releaseKeys(WindowsKeyCaptureService::sendKey); }
            catch (RuntimeException error) { android.util.Log.e("OuseKeyboard", "Unable to release remote keys during service shutdown", error); }
            finally {
                capture.reset();
            }
            if (session != null) session.onKeyboardRoutingChanged(false);
        }
        if (inputListenerRegistered) {
            ((InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(this);
            inputListenerRegistered = false;
        }
        android.util.Log.i("OuseKeyboard", "Accessibility service disconnected");
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {}

    @Override
    public void onInputDeviceChanged(int deviceId) {}

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        capture.inputDeviceRemoved(deviceId, WindowsKeyCaptureService::sendKey);
        if (session != null) session.releaseRemoteInputs();
    }
}
