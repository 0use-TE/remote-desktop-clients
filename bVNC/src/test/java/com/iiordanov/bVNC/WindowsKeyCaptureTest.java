package com.iiordanov.bVNC;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_E;
import static android.view.KeyEvent.KEYCODE_META_LEFT;
import static android.view.KeyEvent.KEYCODE_META_RIGHT;
import static android.view.KeyEvent.META_META_ON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.KeyEvent;

import com.undatech.opaque.input.RdpKeyboardMapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class WindowsKeyCaptureTest {
    private final WindowsKeyCapture capture = new WindowsKeyCapture();
    private final List<KeyEvent> sent = new ArrayList<>();

    @Test
    public void capturesBothSidesAndKeepsChordReleaseAfterWindowsKeyUp() {
        assertTrue(key(ACTION_DOWN, KEYCODE_META_RIGHT, META_META_ON, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_E, META_META_ON, true));
        assertTrue(key(ACTION_UP, KEYCODE_META_RIGHT, 0, true));
        assertTrue(key(ACTION_UP, KEYCODE_E, 0, true));

        assertTrue(key(ACTION_DOWN, KEYCODE_META_LEFT, META_META_ON, true));
        assertTrue(key(ACTION_UP, KEYCODE_META_LEFT, 0, true));
        assertEquals(6, sent.size());
    }

    @Test
    public void passesUnrelatedKeysAndDoesNotJoinAChordAlreadyInProgress() {
        assertFalse(key(ACTION_DOWN, KEYCODE_META_LEFT, META_META_ON, false));
        assertFalse(key(ACTION_DOWN, KEYCODE_E, META_META_ON, true));
        assertFalse(key(ACTION_UP, KEYCODE_E, META_META_ON, true));
        assertFalse(key(ACTION_UP, KEYCODE_META_LEFT, 0, true));
        assertFalse(key(ACTION_DOWN, KEYCODE_E, 0, true));
        assertFalse(key(ACTION_UP, KEYCODE_E, 0, true));
        assertTrue(sent.isEmpty());
    }

    @Test
    public void releasesChordOnFocusLossAndConsumesItsRemainingPhysicalEvents() {
        assertTrue(key(ACTION_DOWN, KEYCODE_META_LEFT, META_META_ON, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_E, META_META_ON, true));
        capture.releaseKeys(sent::add);
        assertEquals(4, sent.size());
        assertEquals(ACTION_UP, sent.get(2).getAction());
        assertEquals(KEYCODE_E, sent.get(2).getKeyCode());
        assertEquals(ACTION_UP, sent.get(3).getAction());
        assertEquals(KEYCODE_META_LEFT, sent.get(3).getKeyCode());

        KeyEvent repeat = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_E, 1, META_META_ON, 1, 0);
        assertTrue(capture.onKeyEvent(repeat, false, sent::add));
        assertTrue(key(ACTION_UP, KEYCODE_E, META_META_ON, false));
        assertTrue(key(ACTION_UP, KEYCODE_META_LEFT, 0, false));
        assertFalse(key(ACTION_DOWN, KEYCODE_E, 0, false));
        assertEquals(4, sent.size());
    }

    @Test
    public void shortcutUsesVirtualKeyWhenUnicodeInputIsPreferred() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(true, false);
        mapper.init(RuntimeEnvironment.getApplication());
        RdpKeyboardMapper.KeyProcessingListener listener =
                mock(RdpKeyboardMapper.KeyProcessingListener.class);
        mapper.reset(listener);
        KeyEvent event = mock(KeyEvent.class);
        when(event.getKeyCode()).thenReturn(KEYCODE_E);
        when(event.getUnicodeChar()).thenReturn((int) 'e');

        when(event.getAction()).thenReturn(ACTION_DOWN);
        assertTrue(mapper.processAndroidKeyEvent(event, false, true));
        when(event.getAction()).thenReturn(ACTION_UP);
        assertTrue(mapper.processAndroidKeyEvent(event, false, true));
        verify(listener).processVirtualKey(0x45, true);
        verify(listener).processVirtualKey(0x45, false);
    }

    private boolean key(int action, int code, int metaState, boolean canStart) {
        KeyEvent event = new KeyEvent(0, 0, action, code, 0, metaState, 1, 0);
        return capture.onKeyEvent(event, canStart, sent::add);
    }
}
