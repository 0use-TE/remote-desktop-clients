package com.iiordanov.bVNC;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.freerdp.freerdpcore.application.SessionState;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.ReconnectBackoff;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class RdpRecoveryCallbacksTest {
    private RdpCommunicator backend(RdpCommunicator.ConnectionStateListener listener) {
        RdpCommunicator backend = mock(RdpCommunicator.class, CALLS_REAL_METHODS);
        Context context = mock(Context.class);
        when(context.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        when(context.getSharedPreferences("generalSettings", Context.MODE_PRIVATE)).thenReturn(
                RuntimeEnvironment.getApplication().getSharedPreferences("recovery-test", Context.MODE_PRIVATE));
        SessionState session = mock(SessionState.class); when(session.getInstance()).thenReturn(42L);
        ReconnectBackoff policy = new ReconnectBackoff(); policy.connected();
        ReflectionHelpers.setField(backend, "context", context);
        ReflectionHelpers.setField(backend, "handler", new Handler(Looper.getMainLooper()));
        ReflectionHelpers.setField(backend, "session", session);
        ReflectionHelpers.setField(backend, "myself", backend);
        ReflectionHelpers.setField(backend, "reconnectBackoff", policy);
        ReflectionHelpers.setField(backend, "connectionState", RdpCommunicator.ConnectionState.CONNECTED);
        ReflectionHelpers.setField(backend, "stateListener", listener);
        return backend;
    }
    @Test public void transportFailureSchedulesRecoveryEvenWithoutDisconnectedCallback() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        backend.OnConnectionFailure(42L); shadowOf(Looper.getMainLooper()).idle();
        verify(listener).changed(RdpCommunicator.ConnectionState.RECONNECTING, 1, 1000);
    }
    @Test public void duplicateTerminalNotificationsScheduleOnlyOneAttempt() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        backend.OnConnectionFailure(42L); backend.OnDisconnected(42L);
        shadowOf(Looper.getMainLooper()).idle();
        verify(listener, times(1)).changed(RdpCommunicator.ConnectionState.RECONNECTING, 1, 1000);
    }
    @Test public void staleConnectionCannotDisconnectTheCurrentSession() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        backend.OnDisconnected(11L); backend.OnConnectionFailure(11L);
        shadowOf(Looper.getMainLooper()).idle(); verifyNoInteractions(listener);
    }
    @Test public void userDisconnectPreventsAutomaticRecovery() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        ReflectionHelpers.setField(backend, "disconnectRequested", true);
        backend.OnDisconnected(42L); backend.OnConnectionFailure(42L);
        shadowOf(Looper.getMainLooper()).idle(); verifyNoInteractions(listener);
    }
}
