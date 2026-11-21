package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;

import com.undatech.opaque.InputCarriable;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.Viewable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class RemoteRdpPointerTest {
    @Test
    public void wheelNotchesLeaveThePointerAndMouseButtonsAlone() {
        Viewable canvas = mock(Viewable.class);
        RdpCommunicator rdp = mock(RdpCommunicator.class);
        InputCarriable input = mock(InputCarriable.class);
        com.undatech.opaque.input.RemoteKeyboard keyboard =
                mock(com.undatech.opaque.input.RemoteKeyboard.class);
        when(input.getKeyboard()).thenReturn(keyboard);
        when(keyboard.getMetaState()).thenReturn(4);

        RemoteRdpPointer pointer = new RemoteRdpPointer(rdp, RuntimeEnvironment.getApplication(),
                input, canvas, new Handler(Looper.getMainLooper()), false, false);
        pointer.setX(400);
        pointer.setY(300);
        pointer.sendWheelSteps(0, 0);
        pointer.sendWheelSteps(2, 1);
        pointer.sendWheelSteps(-1, 0);
        pointer.sendWheelDelta(30, 1);
        pointer.sendWheelDelta(-30, 0);
        pointer.sendWheelDelta(0, 0);
        pointer.sendWheelDelta(300, 0);

        verify(rdp, times(2)).writeWheelEvent(400, 300, 5, 0x0278);
        verify(rdp).writeWheelEvent(400, 300, 4, 0x0388);
        verify(rdp).writeWheelEvent(400, 300, 5, 0x021e);
        verify(rdp).writeWheelEvent(400, 300, 4, 0x03e2);
        verify(rdp).writeWheelEvent(400, 300, 4, 0x02ff);
        verify(rdp).writeWheelEvent(400, 300, 4, 0x022d);
        verifyNoMoreInteractions(rdp);
        verifyNoInteractions(canvas);
        assertEquals(400, pointer.getX());
        assertEquals(300, pointer.getY());
    }
}
