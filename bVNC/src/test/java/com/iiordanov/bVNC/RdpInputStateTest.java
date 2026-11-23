package com.iiordanov.bVNC;

import com.undatech.opaque.RdpInputState;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class RdpInputStateTest {
    @Test public void focusLossReleasesOrdinaryKeysModifiersUnicodeAndMouseAtLastPosition() {
        RdpInputState state = new RdpInputState();
        state.key(65, true, false); state.key(0xa2, true, false);
        state.key(0x4e2d, true, true);
        state.pointer(10, 20, 0x9000); // Button 3 down.
        state.pointer(30, 40, 0x0800); // Drag/move.
        state.pointer(900, 900, 0x0200 | 120); // Wheel must not change position.
        RdpInputState.Sink sink = mock(RdpInputState.Sink.class);
        state.release(sink);
        verify(sink).keyUp(65); verify(sink).keyUp(0xa2);
        verify(sink).unicodeUp(0x4e2d); verify(sink).mouseUp(30, 40, 0x1000);
        verifyNoMoreInteractions(sink);
    }
    @Test public void completedKeysAndButtonsAreNotReleasedAgain() {
        RdpInputState state = new RdpInputState();
        state.key(65, true, false); state.key(65, false, false);
        state.pointer(1, 2, 0xa000); state.pointer(1, 2, 0x2000);
        RdpInputState.Sink sink = mock(RdpInputState.Sink.class);
        state.release(sink); verifyNoInteractions(sink);
    }
    @Test public void repeatsAndRepeatedFocusLossEmitOnlyOneRelease() {
        RdpInputState state = new RdpInputState();
        for (int i = 0; i < 30; i++) state.key(65, true, false);
        RdpInputState.Sink sink = mock(RdpInputState.Sink.class);
        state.release(sink); state.release(sink);
        verify(sink, times(1)).keyUp(65); verifyNoMoreInteractions(sink);
    }
    @Test public void newConnectionDoesNotInheritHeldInput() {
        RdpInputState state = new RdpInputState();
        state.key(65, true, false); state.pointer(1, 2, 0xa000); state.clear();
        RdpInputState.Sink sink = mock(RdpInputState.Sink.class);
        state.release(sink); verifyNoInteractions(sink);
    }
}
