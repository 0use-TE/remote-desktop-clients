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
