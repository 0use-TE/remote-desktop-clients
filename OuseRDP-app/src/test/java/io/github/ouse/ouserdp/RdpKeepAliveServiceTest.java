package io.github.ouse.ouserdp;

import android.app.Application;
import android.content.Intent;
import com.iiordanov.bVNC.protocol.RemoteConnection;
import com.undatech.opaque.RdpCommunicator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class RdpKeepAliveServiceTest {
    @Test public void backgroundDropsWifiBoostAndTerminalStateReleasesWakeLock() {
        SessionActivity activity = mock(SessionActivity.class);
        RdpCommunicator backend = mock(RdpCommunicator.class);
        RemoteConnection connection = mock(RemoteConnection.class);
        when(activity.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        when(activity.getIntent()).thenReturn(new Intent());
        when(activity.getRemoteConnection()).thenReturn(connection);
        RdpKeepAliveService.attach(activity, backend);
        var listener = org.mockito.ArgumentCaptor.forClass(RdpCommunicator.ConnectionStateListener.class);
        verify(backend).setConnectionStateListener(listener.capture());
        var controller = Robolectric.buildService(RdpKeepAliveService.class).create();
        RdpKeepAliveService service = controller.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            org.robolectric.util.ReflectionHelpers.setField(service, "screenOn", true);
            RdpKeepAliveService.setVisible(backend, true);
            android.os.PowerManager.WakeLock cpu = org.robolectric.util.ReflectionHelpers.getField(service, "wakeLock");
            android.net.wifi.WifiManager.WifiLock wifi = org.robolectric.util.ReflectionHelpers.getField(service, "wifiLock");
            assertTrue(cpu.isHeld()); assertTrue(wifi.isHeld());
            RdpKeepAliveService.setVisible(backend, false);
            assertTrue(cpu.isHeld()); assertFalse(wifi.isHeld());
            RdpKeepAliveService.setVisible(backend, true);
            assertTrue(wifi.isHeld());
            listener.getValue().changed(RdpCommunicator.ConnectionState.RETRY_FAILED, 8, 0);
            assertFalse(cpu.isHeld()); assertFalse(wifi.isHeld());
        } finally { controller.destroy(); }
        verify(connection).closeConnection();
        verify(activity).showConnectionState(RdpCommunicator.ConnectionState.DISCONNECTED, 0, 0);
    }
    @Test public void waitingForNetworkKeepsCpuButDoesNotHoldHighPerformanceWifi() {
        SessionActivity activity = mock(SessionActivity.class);
        RdpCommunicator backend = mock(RdpCommunicator.class);
        RemoteConnection connection = mock(RemoteConnection.class);
        when(activity.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        when(activity.getIntent()).thenReturn(new Intent());
        when(activity.getRemoteConnection()).thenReturn(connection);
        RdpKeepAliveService.attach(activity, backend);
        var listener = org.mockito.ArgumentCaptor.forClass(RdpCommunicator.ConnectionStateListener.class);
        verify(backend).setConnectionStateListener(listener.capture());
        var controller = Robolectric.buildService(RdpKeepAliveService.class).create();
        RdpKeepAliveService service = controller.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            listener.getValue().changed(RdpCommunicator.ConnectionState.WAITING_FOR_NETWORK, 1, 0);
            android.os.PowerManager.WakeLock cpu = org.robolectric.util.ReflectionHelpers.getField(service, "wakeLock");
            android.net.wifi.WifiManager.WifiLock wifi = org.robolectric.util.ReflectionHelpers.getField(service, "wifiLock");
            assertTrue(cpu.isHeld()); assertFalse(wifi.isHeld());
            listener.getValue().changed(RdpCommunicator.ConnectionState.AUTHENTICATION_FAILED, 1, 0);
            assertFalse(cpu.isHeld()); assertFalse(wifi.isHeld());
        } finally { controller.destroy(); }
    }
    @Test public void notificationDisconnectClosesSessionAndNeverRestartsIt() {
        SessionActivity activity = mock(SessionActivity.class);
        RdpCommunicator backend = mock(RdpCommunicator.class);
        RemoteConnection connection = mock(RemoteConnection.class);
        when(activity.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        when(activity.getIntent()).thenReturn(new Intent());
        when(activity.getRemoteConnection()).thenReturn(connection);
        RdpKeepAliveService.attach(activity, backend);
        assertTrue(RdpKeepAliveService.owns(backend));
        var controller = Robolectric.buildService(RdpKeepAliveService.class).create();
        RdpKeepAliveService service = controller.get();
        try {
            service.onStartCommand(new Intent(), 0, 1);
            service.onStartCommand(new Intent().setAction("io.github.ouse.ouserdp.STOP_SESSION"), 0, 2);
            verify(connection).closeConnection();
            verify(activity).finish();
            verify(backend, never()).retryConnectionNow();
            assertFalse(RdpKeepAliveService.owns(backend));
        } finally { controller.destroy(); }
        verify(connection, times(1)).closeConnection();
    }
    @Test public void disablingBackgroundModeDoesNotCloseTheVisibleSession() {
        SessionActivity activity = mock(SessionActivity.class);
        RdpCommunicator backend = mock(RdpCommunicator.class);
        RemoteConnection connection = mock(RemoteConnection.class);
        when(activity.getPackageName()).thenReturn("io.github.ouse.ouserdp");
        when(activity.getIntent()).thenReturn(new Intent());
        when(activity.getRemoteConnection()).thenReturn(connection);
        RdpKeepAliveService.attach(activity, backend);
        RdpKeepAliveService.detach(activity, backend);
        assertFalse(RdpKeepAliveService.owns(backend));
        verify(connection, never()).closeConnection();
    }
}
