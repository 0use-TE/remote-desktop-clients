package com.iiordanov.bVNC;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.Viewable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = RdpGraphicsUpdateTest.NativeGraphics.class,
        instrumentedPackages = {"com.freerdp.freerdpcore.services"})
public class RdpGraphicsUpdateTest {
    @Implements(value = LibFreeRDP.class, isInAndroidSdk = false)
    public static class NativeGraphics {
        static int calls;
        static boolean success;
        static Rect copied;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static boolean updateGraphics(long instance, Bitmap bitmap,
                int x, int y, int width, int height) {
            assertEquals(42L, instance);
            assertTrue(Thread.holdsLock(bitmap));
            calls++;
            copied = new Rect(x, y, x + width, y + height);
            return success;
        }
    }
    private RdpCommunicator backend;
    private Viewable view;
    private Bitmap bitmap;

    @Before public void setup() {
        NativeGraphics.calls = 0;
        NativeGraphics.success = true;
        NativeGraphics.copied = null;
        backend = mock(RdpCommunicator.class, CALLS_REAL_METHODS);
        view = mock(Viewable.class);
        bitmap = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888);
        when(view.getBitmap()).thenReturn(bitmap);
        SessionState session = mock(SessionState.class);
        when(session.getInstance()).thenReturn(42L);
        ReflectionHelpers.setField(backend, "session", session);
        ReflectionHelpers.setField(backend, "viewable", view);
        ReflectionHelpers.setField(backend, "handler", new Handler(Looper.getMainLooper()));
    }

    @Test public void clipsPartialUpdatesAndSharesBitmapLockWithDisposal() {
        doAnswer(call -> { assertFalse(Thread.holdsLock(bitmap)); return null; })
                .when(view).reDraw(0, 70, 20, 10);
        backend.OnGraphicsUpdate(-5, 70, 25, 30);
        assertEquals(1, NativeGraphics.calls);
        assertEquals(new Rect(0, 70, 20, 80), NativeGraphics.copied);
        verify(view).reDraw(0, 70, 20, 10);
    }

    @Test public void failedNativeCopyNeverPublishesAFrame() {
        NativeGraphics.success = false;
        backend.OnGraphicsUpdate(5, 5, 20, 30);
        verify(view, never()).reDraw(anyInt(), anyInt(), anyInt(), anyInt());
        assertFalse(ReflectionHelpers.getField(backend, "receivedFirstGraphicsFrame"));
    }

    @Test public void recycledOrOutsideBuffersNeverReachNativeCode() {
        backend.OnGraphicsUpdate(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 1);
        backend.OnGraphicsUpdate(0, 0, -1, 1);
        bitmap.recycle(); backend.OnGraphicsUpdate(0, 0, 1, 1);
        assertEquals(0, NativeGraphics.calls);
    }

    @Test public void userDisconnectIgnoresQueuedFrames() {
        ReflectionHelpers.setField(backend, "disconnectRequested", true);
        backend.OnGraphicsUpdate(0, 0, 1, 1);
        assertEquals(0, NativeGraphics.calls);
        verifyNoInteractions(view);
    }
}
