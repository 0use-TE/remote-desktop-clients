package com.iiordanov.bVNC;

import android.app.Application;
import android.view.InputDevice;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class WindowsKeyCaptureServiceTest {
    @Test public void physicalShortcutSwitchesRoutingWithoutOpeningRecents() {
        RemoteCanvasActivity session = mock(RemoteCanvasActivity.class);
        when(session.hasWindowFocus()).thenReturn(true);
        when(session.canSendCapturedKeys()).thenReturn(true);
        when(session.capturesExtendedShortcuts()).thenReturn(true);
        InputDevice keyboard = mock(InputDevice.class);
        when(keyboard.isVirtual()).thenReturn(false);
        when(keyboard.getKeyboardType()).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        WindowsKeyCaptureService service = Robolectric.buildService(WindowsKeyCaptureService.class).create().get();
        WindowsKeyCaptureService.attach(session);
        try {
            int ctrl = KeyEvent.META_CTRL_ON;
            int chord = ctrl | KeyEvent.META_ALT_ON;
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, ctrl)));
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, chord)));
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12, chord)));
            assertTrue(WindowsKeyCaptureService.isAndroidKeyboardMode(session));
            verify(session).onKeyboardRoutingChanged(true);
            assertTrue(Shadows.shadowOf(service).getGlobalActionsPerformed().isEmpty());
            WindowsKeyCaptureService.detach(session);
            WindowsKeyCaptureService.attach(session);
            assertTrue(WindowsKeyCaptureService.isAndroidKeyboardMode(session));
            RemoteCanvasActivity anotherSession = mock(RemoteCanvasActivity.class);
            WindowsKeyCaptureService.detach(session);
            WindowsKeyCaptureService.attach(anotherSession);
            assertFalse(WindowsKeyCaptureService.isAndroidKeyboardMode(anotherSession));
            WindowsKeyCaptureService.detach(anotherSession);
            when(session.hasWindowFocus()).thenReturn(false);
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F12, chord)));
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, ctrl)));
            assertTrue(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0)));
            assertFalse(service.onKeyEvent(event(keyboard, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12, chord)));
            assertTrue(Shadows.shadowOf(service).getGlobalActionsPerformed().isEmpty());
        } finally {
            WindowsKeyCaptureService.detach(session);
            service.onInputDeviceRemoved(43);
        }
    }

    private KeyEvent event(InputDevice keyboard, int action, int code, int meta) {
        KeyEvent event = spy(new KeyEvent(0, 0, action, code, 0, meta, 43, 0, 0,
                InputDevice.SOURCE_KEYBOARD));
        doReturn(keyboard).when(event).getDevice();
        return event;
    }
}
