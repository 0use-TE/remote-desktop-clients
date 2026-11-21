package com.iiordanov.bVNC.input;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NativeTouchInputTest {
    private MotionEvent event(int action, int[] ids, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = xs[i]; coords[i].y = ys[i]; coords[i].pressure = 1;
        }
        return MotionEvent.obtain(0, 10, action, ids.length, properties, coords, 0, 0, 1, 1, 1, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }
    private boolean send(NativeTouchInput input, int action, int[] ids, float[] xs, float[] ys) {
        MotionEvent e = event(action, ids, xs, ys);
        try { return input.onTouch(e, 2, 10, 20, 100, 100); } finally { e.recycle(); }
    }
    @Test public void forwardsMultipleStableContactsAndTransformsCoordinates() {
        NativeTouchInput.Sink sink = mock(NativeTouchInput.Sink.class); when(sink.ready()).thenReturn(true);
        NativeTouchInput input = new NativeTouchInput(sink);
        assertTrue(send(input, MotionEvent.ACTION_DOWN, new int[]{7}, new float[]{40}, new float[]{60}));
        assertTrue(send(input, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{7, 2}, new float[]{40, 80}, new float[]{60, 100}));
        assertTrue(send(input, MotionEvent.ACTION_MOVE, new int[]{7, 2}, new float[]{50, 1000}, new float[]{70, -1000}));
        assertTrue(send(input, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{7, 2}, new float[]{50, 1000}, new float[]{70, -1000}));
        assertTrue(send(input, MotionEvent.ACTION_UP, new int[]{7}, new float[]{50}, new float[]{70}));
        verify(sink).send(0, 7, 30, 50); verify(sink).send(0, 2, 50, 70);
        verify(sink).send(1, 7, 35, 55); verify(sink).send(1, 2, 99, 0);
        verify(sink).send(2, 2, 99, 0); verify(sink).send(2, 7, 35, 55);
        assertFalse(input.isActive());
    }
    @Test public void cancelAndFocusLossReleaseTouchesOnlyOnce() {
        NativeTouchInput.Sink sink = mock(NativeTouchInput.Sink.class); when(sink.ready()).thenReturn(true);
        NativeTouchInput input = new NativeTouchInput(sink);
        send(input, MotionEvent.ACTION_DOWN, new int[]{3}, new float[]{10}, new float[]{10});
        assertTrue(send(input, MotionEvent.ACTION_CANCEL, new int[]{3}, new float[]{10}, new float[]{10}));
        input.cancel(); verify(sink, times(1)).cancel(); assertFalse(input.isActive());
    }
    @Test public void neverSwitchesAnUnsupportedGestureMidstream() {
        NativeTouchInput.Sink sink = mock(NativeTouchInput.Sink.class);
        NativeTouchInput input = new NativeTouchInput(sink);
        assertFalse(send(input, MotionEvent.ACTION_DOWN, new int[]{0}, new float[]{10}, new float[]{10}));
        when(sink.ready()).thenReturn(true);
        assertFalse(send(input, MotionEvent.ACTION_MOVE, new int[]{0}, new float[]{20}, new float[]{20}));
        assertFalse(send(input, MotionEvent.ACTION_UP, new int[]{0}, new float[]{20}, new float[]{20}));
        verify(sink, never()).send(anyInt(), anyInt(), anyInt(), anyInt());
    }
}
