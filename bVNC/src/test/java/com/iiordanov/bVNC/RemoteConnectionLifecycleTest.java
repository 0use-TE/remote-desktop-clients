package com.iiordanov.bVNC;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.iiordanov.bVNC.protocol.RemoteConnection;
import com.undatech.opaque.RfbConnectable;
import com.undatech.opaque.Viewable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class RemoteConnectionLifecycleTest {
    private RemoteConnection connection(RfbConnectable backend) {
        RemoteConnection connection = mock(RemoteConnection.class, CALLS_REAL_METHODS);
        Context context = mock(Context.class);
        when(context.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        ReflectionHelpers.setField(connection, "context", context);
        ReflectionHelpers.setField(connection, "closed", new AtomicBoolean());
        ReflectionHelpers.setField(connection, "canvas", mock(Viewable.class));
        ReflectionHelpers.setField(connection, "rfbConn", backend);
        connection.setHandler(new Handler(Looper.getMainLooper()));
        return connection;
    }
    @Test public void closingOldSessionDoesNotCancelNewSessionsCallbacks() {
        var first = connection(mock(RfbConnectable.class));
        var second = connection(mock(RfbConnectable.class));
        AtomicInteger firstCalls = new AtomicInteger(), secondCalls = new AtomicInteger();
        first.handler.post(firstCalls::incrementAndGet);
        second.handler.post(secondCalls::incrementAndGet);
        first.closeConnection();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, firstCalls.get()); assertEquals(1, secondCalls.get());
        assertNotSame(first.handler, second.handler);
    }
    @Test public void serviceAndActivityCanBothCloseWithoutDuplicatingTeardown() {
        RfbConnectable backend = mock(RfbConnectable.class);
        var connection = connection(backend);
        connection.closeConnection(); connection.closeConnection();
        verify(backend, times(1)).close();
        verify(connection, times(1)).onDestroy();
    }
}
