package com.iiordanov.bVNC;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_E;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_ESCAPE;
import static android.view.KeyEvent.KEYCODE_F1;
import static android.view.KeyEvent.KEYCODE_F12;
import static android.view.KeyEvent.KEYCODE_F13;
import static android.view.KeyEvent.KEYCODE_F24;
import static android.view.KeyEvent.KEYCODE_F4;
import static android.view.KeyEvent.KEYCODE_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_ALT_LEFT;
import static android.view.KeyEvent.KEYCODE_SHIFT_LEFT;
import static android.view.KeyEvent.KEYCODE_TAB;
import static android.view.KeyEvent.KEYCODE_META_LEFT;
import static android.view.KeyEvent.KEYCODE_META_RIGHT;
import static android.view.KeyEvent.META_META_ON;
import static android.view.KeyEvent.META_ALT_ON;
import static android.view.KeyEvent.META_SHIFT_ON;
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
    public void f12TogglesBothWaysWithoutLeakingModifiersOrRepeating() {
        int chord = KeyEvent.META_CTRL_ON | META_ALT_ON;
        toggleDown(chord);
        assertTrue(capture.isAndroidKeyboardMode());
        assertEquals(4, sent.size());
        assertEquals(ACTION_UP, sent.get(2).getAction());
        assertEquals(ACTION_UP, sent.get(3).getAction());
        assertTrue(capture.onKeyEvent(new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_F12, 4, chord, 1, 0),
                true, true, sent::add));
        assertTrue(capture.isAndroidKeyboardMode());
        toggleUp(chord);
        assertFalse(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true, true));
        assertFalse(key(ACTION_DOWN, KEYCODE_TAB, META_ALT_ON, true, true));
        assertFalse(key(ACTION_UP, KEYCODE_TAB, META_ALT_ON, true, true));
        assertFalse(key(ACTION_UP, KEYCODE_ALT_LEFT, 0, true, true));
        assertFalse(key(ACTION_DOWN, KEYCODE_META_LEFT, META_META_ON, true, true));
        assertFalse(key(ACTION_UP, KEYCODE_META_LEFT, 0, true, true));
        assertEquals(4, sent.size());
        toggleDown(chord);
        assertFalse(capture.isAndroidKeyboardMode());
        toggleUp(chord);
        assertEquals(4, sent.size());
        assertTrue(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_TAB, META_ALT_ON, true, true));
        assertEquals(6, sent.size());
    }

    @Test
    public void ctrlAltTabAndAltWinTabAreOrdinaryWindowsChordsAgain() {
        int chord = KeyEvent.META_CTRL_ON | META_ALT_ON;
        key(ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON, true, true);
        key(ACTION_DOWN, KEYCODE_ALT_LEFT, chord, true, true);
        assertTrue(key(ACTION_DOWN, KEYCODE_TAB, chord, true, true));
        assertEquals(KEYCODE_TAB, sent.get(2).getKeyCode());
        assertFalse(capture.isAndroidKeyboardMode());
        key(ACTION_UP, KEYCODE_TAB, chord, true, true);
        key(ACTION_UP, KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON, true, true);
        key(ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, true, true);
        key(ACTION_DOWN, KEYCODE_META_LEFT, META_META_ON, true, true);
        key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_META_ON | META_ALT_ON, true, true);
        assertTrue(key(ACTION_DOWN, KEYCODE_TAB, META_META_ON | META_ALT_ON, true, true));
        assertEquals(KEYCODE_TAB, sent.get(sent.size()-1).getKeyCode());
        assertFalse(capture.isAndroidKeyboardMode());
    }

    @Test
    public void toggleDoesNotActivateOutsideSessionOrWithExtraModifiers() {
        int chord = KeyEvent.META_CTRL_ON | META_ALT_ON;
        assertFalse(key(ACTION_DOWN, KEYCODE_F12, chord, false, true));
        assertFalse(key(ACTION_DOWN, KEYCODE_F12, chord, true, false));
        assertTrue(key(ACTION_DOWN, KEYCODE_F12, chord | META_SHIFT_ON, true, true));
        assertFalse(capture.isAndroidKeyboardMode());
    }

    @Test
    public void disconnectingKeyboardDoesNotLeaveToggleChordStuck() {
        int chord = KeyEvent.META_CTRL_ON | META_ALT_ON;
        toggleDown(chord);
        capture.inputDeviceRemoved(1, sent::add);
        toggleDown(chord);
        assertFalse(capture.isAndroidKeyboardMode());
        toggleUp(chord);
        assertTrue(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true, true));
    }

    @Test
    public void oemF12ScanCodeCanRestoreRemoteModeAndNewSessionDefaultsRemote() {
        int chord = KeyEvent.META_CTRL_ON | META_ALT_ON;
        toggleDown(chord);
        toggleUp(chord);
        key(ACTION_DOWN, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.META_CTRL_ON, true, true);
        key(ACTION_DOWN, KeyEvent.KEYCODE_ALT_RIGHT, chord, true, true);
        KeyEvent oem = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_VOLUME_DOWN, 0, chord, 1, 88);
        assertTrue(capture.onKeyEvent(oem, true, true, sent::add));
        assertFalse(capture.isAndroidKeyboardMode());
        capture.reset();
        toggleDown(chord);
        assertTrue(capture.isAndroidKeyboardMode());
        capture.useRemoteKeyboard();
        assertFalse(capture.isAndroidKeyboardMode());
    }

    private void toggleDown(int chord) {
        key(ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON, true, true);
        key(ACTION_DOWN, KEYCODE_ALT_LEFT, chord, true, true);
        assertTrue(key(ACTION_DOWN, KEYCODE_F12, chord, true, true));
    }

    private void toggleUp(int chord) {
        assertTrue(key(ACTION_UP, KEYCODE_F12, chord, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON, true, true));
        assertTrue(key(ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, true, true));
    }

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
        assertTrue(capture.onKeyEvent(repeat, false, false, sent::add));
        assertTrue(key(ACTION_UP, KEYCODE_E, META_META_ON, false));
        assertTrue(key(ACTION_UP, KEYCODE_META_LEFT, 0, false));
        assertFalse(key(ACTION_DOWN, KEYCODE_E, 0, false));
        assertEquals(4, sent.size());
    }

    @Test
    public void capturesAltTabAndReverseTabOnlyWhenEnabled() {
        assertFalse(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true));
        assertFalse(key(ACTION_DOWN, KEYCODE_TAB, META_ALT_ON, true));

        assertTrue(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_TAB, META_ALT_ON, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_TAB, META_ALT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_SHIFT_LEFT, META_ALT_ON | META_SHIFT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_TAB, META_ALT_ON | META_SHIFT_ON, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_TAB, META_ALT_ON | META_SHIFT_ON, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_SHIFT_LEFT, META_ALT_ON, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_ALT_LEFT, 0, true, true));
        assertEquals(8, sent.size());
    }

    @Test
    public void capturesAltF4AndRemapsMicrophoneKeyOnlyWithAltHeld() {
        assertFalse(key(ACTION_DOWN, KEYCODE_MUTE, 0, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_ALT_LEFT, META_ALT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_F4, META_ALT_ON, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_F4, META_ALT_ON, true, true));
        assertTrue(key(ACTION_DOWN, KEYCODE_MUTE, 0, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_ALT_LEFT, 0, true, true));
        assertTrue(key(ACTION_UP, KEYCODE_MUTE, 0, true, true));
        assertEquals(KEYCODE_F4, sent.get(1).getKeyCode());
        assertEquals(KEYCODE_F4, sent.get(3).getKeyCode());
        assertEquals(KEYCODE_F4, sent.get(5).getKeyCode());
        assertEquals(ACTION_UP, sent.get(5).getAction());
    }

    @Test
    public void forwardsAllFunctionKeysAndPreservesModifiers() {
        for (int index = 0; index < 24; index++) {
            int code = index < 12 ? KEYCODE_F1 + index : KEYCODE_F13 + index - 12;
            int modifiers = index % 2 == 0 ? 0 : KeyEvent.META_CTRL_ON | META_SHIFT_ON;
            assertFalse(key(ACTION_DOWN, code, modifiers, true, false));
            assertTrue(key(ACTION_DOWN, code, modifiers, true, true));
            assertTrue(key(ACTION_UP, code, 0, true, true));
            assertEquals(code, sent.get(index * 2).getKeyCode());
            assertEquals(modifiers, sent.get(index * 2).getMetaState());
            assertEquals(code, sent.get(index * 2 + 1).getKeyCode());
        }
    }

    @Test
    public void remapsAnOemMediaKeyOnlyWhenItsScanCodeIdentifiesAFunctionKey() {
        KeyEvent media = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_VOLUME_DOWN, 0, 0, 1, 114);
        assertFalse(capture.onKeyEvent(media, true, true, sent::add));
        KeyEvent function = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_VOLUME_DOWN, 0, 0, 1, 88);
        assertTrue(capture.onKeyEvent(function, true, true, sent::add));
        assertTrue(capture.onKeyEvent(KeyEvent.changeAction(function, ACTION_UP), true, true, sent::add));
        assertEquals(KEYCODE_F12, sent.get(0).getKeyCode());
        assertEquals(KEYCODE_F12, sent.get(1).getKeyCode());
    }

    @Test
    public void forwardsEscapeAndOnlyRemapsBackWhenItsScanCodeIsEscape() {
        KeyEvent escape = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_ESCAPE, 0, 0, 1, 1);
        assertTrue(capture.onKeyEvent(escape, true, true, sent::add));
        assertTrue(capture.onKeyEvent(KeyEvent.changeAction(escape, ACTION_UP), true, true, sent::add));
        KeyEvent remapped = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_BACK, 0, 0, 1, 1);
        assertTrue(capture.onKeyEvent(remapped, true, true, sent::add));
        assertTrue(capture.onKeyEvent(KeyEvent.changeAction(remapped, ACTION_UP), true, true, sent::add));
        KeyEvent back = new KeyEvent(0, 0, ACTION_DOWN, KEYCODE_BACK, 0, 0, 1, 158);
        assertFalse(capture.onKeyEvent(back, true, true, sent::add));
        assertEquals(4, sent.size());
        for (KeyEvent event : sent) {
            assertEquals(KEYCODE_ESCAPE, event.getKeyCode());
        }
    }

    @Test
    public void mapsExtendedFunctionKeysToWindowsVirtualKeys() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(true, false);
        mapper.init(RuntimeEnvironment.getApplication());
        RdpKeyboardMapper.KeyProcessingListener listener =
                mock(RdpKeyboardMapper.KeyProcessingListener.class);
        mapper.reset(listener);
        for (int code = KEYCODE_F13; code <= KEYCODE_F24; code++) {
            assertTrue(mapper.processAndroidKeyEvent(new KeyEvent(ACTION_DOWN, code), false, true));
            assertTrue(mapper.processAndroidKeyEvent(new KeyEvent(ACTION_UP, code), false, true));
            int windowsCode = 0x7C + code - KEYCODE_F13;
            verify(listener).processVirtualKey(windowsCode, true);
            verify(listener).processVirtualKey(windowsCode, false);
        }
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
        return key(action, code, metaState, canStart, false);
    }

    private boolean key(int action, int code, int metaState, boolean canStart, boolean captureAltTab) {
        KeyEvent event = new KeyEvent(0, 0, action, code, 0, metaState, 1, 0);
        return capture.onKeyEvent(event, canStart, captureAltTab, sent::add);
    }
}
