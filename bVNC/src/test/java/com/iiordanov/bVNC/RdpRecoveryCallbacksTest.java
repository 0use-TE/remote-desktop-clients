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
    @Test public void invalidCredentialsStopAutomaticRetriesAndDoNotShowTransportError() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        ReflectionHelpers.setField(backend, "lastFailure", (long) 0x20009 << 32);
        backend.OnDisconnected(42L); shadowOf(Looper.getMainLooper()).idle();
        verify(listener).changed(RdpCommunicator.ConnectionState.AUTHENTICATION_FAILED, 0, 0);
        verifyNoMoreInteractions(listener);
    }
    @Test public void remoteLogoffDoesNotImmediatelyLogTheUserBackIn() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        ReflectionHelpers.setField(backend, "lastFailure", 12L);
        backend.OnDisconnected(42L); shadowOf(Looper.getMainLooper()).idle();
        verify(listener).changed(RdpCommunicator.ConnectionState.SERVER_ENDED, 0, 0);
        verifyNoMoreInteractions(listener);
    }
    @Test public void threeMinutesOfflineDoNotExhaustTransportRetries() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        backend.OnDisconnected(42L);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(180));
        ReconnectBackoff policy = ReflectionHelpers.getField(backend, "reconnectBackoff");
        org.junit.Assert.assertEquals(1, policy.attempts());
        verify(listener, atLeastOnce()).changed(RdpCommunicator.ConnectionState.WAITING_FOR_NETWORK, 1, 0);
        verify(listener, never()).changed(eq(RdpCommunicator.ConnectionState.RETRY_FAILED), anyInt(), anyInt());
        ReflectionHelpers.setField(backend, "disconnectRequested", true);
        Handler handler = ReflectionHelpers.getField(backend, "handler");
        handler.removeCallbacksAndMessages(null);
    }
    @Test public void newNetworkCanRestartExhaustedTransportRecovery() {
        var listener = mock(RdpCommunicator.ConnectionStateListener.class);
        var backend = backend(listener);
        ReconnectBackoff policy = ReflectionHelpers.getField(backend, "reconnectBackoff");
        for (int i = 0; i < 8; i++) policy.nextDelayMillis();
        ReflectionHelpers.setField(backend, "connectionState", RdpCommunicator.ConnectionState.RETRY_FAILED);
        backend.onNetworkAvailable(); shadowOf(Looper.getMainLooper()).idle();
        org.junit.Assert.assertEquals(0, policy.attempts());
        verify(listener).changed(RdpCommunicator.ConnectionState.RECONNECTING, 0, 0);
        ReflectionHelpers.setField(backend, "disconnectRequested", true);
        Handler handler = ReflectionHelpers.getField(backend, "handler");
        handler.removeCallbacksAndMessages(null);
    }
}
