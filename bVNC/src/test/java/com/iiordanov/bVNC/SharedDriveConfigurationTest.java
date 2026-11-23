package com.iiordanov.bVNC;

import android.app.Application;
import android.content.Context;
import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.domain.BookmarkBase;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.undatech.opaque.NativeRdpTouch;
import com.undatech.opaque.NativeSharedFolder;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.RdpSharedFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, shadows = {SharedDriveConfigurationTest.Native.class, SharedDriveConfigurationTest.Drive.class,
        SharedDriveConfigurationTest.Touch.class}, instrumentedPackages = {"com.freerdp.freerdpcore.services"})
public class SharedDriveConfigurationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static final List<String> calls = new ArrayList<>();
    @Implements(value = LibFreeRDP.class, isInAndroidSdk = false) public static class Native {
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static boolean setConnectionInfo(Context context, long instance, BookmarkBase bookmark) { calls.add("parse"); return true; }
        @Implementation protected static boolean connect(long instance) { calls.add("connect"); return true; }
    }
    @Implements(NativeSharedFolder.class) public static class Drive {
        static String directory;
        @Implementation protected static boolean configure(long instance, String path) { calls.add("share"); directory = path; return true; }
    }
    @Implements(NativeRdpTouch.class) public static class Touch {
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static boolean attach(long instance) { calls.add("touch"); return true; }
    }
    private Context context(String name, File external) {
        Context context = spy(RuntimeEnvironment.getApplication());
        doReturn(name).when(context).getPackageName(); doReturn(external).when(context).getExternalFilesDir(null);
        return context;
    }
    private RdpCommunicator backend(Context context, SessionState session) {
        calls.clear(); Drive.directory = null;
        RdpCommunicator backend = mock(RdpCommunicator.class, CALLS_REAL_METHODS);
        when(session.getInstance()).thenReturn(42L);
        ReflectionHelpers.setField(backend, "context", context); ReflectionHelpers.setField(backend, "session", session);
        ReflectionHelpers.setField(backend, "bookmark", mock(BookmarkBase.class));
        return backend;
    }
    @Test public void onlyTransferDirectoryIsAddedAfterParsingAndBeforeConnecting() throws Exception {
        File external = temp.newFolder("external"); Context context = context("io.github.ouse.ouserdp", external);
        SessionState session = mock(SessionState.class); backend(context, session).connect();
        assertEquals(Arrays.asList("touch", "parse", "share", "connect"), calls);
        assertEquals(new File(external, "OuseRDP-Shared").getCanonicalPath(), Drive.directory);
        verify(session, never()).connect(any());
    }
    @Test public void otherClientsKeepTheirExistingConnectionPath() throws Exception {
        Context context = context("com.iiordanov.aRDP", temp.newFolder("external"));
        SessionState session = mock(SessionState.class); backend(context, session).connect();
        verify(session).connect(context); assertTrue(calls.isEmpty());
    }
    @Test public void missingExternalVolumeFallsBackToAppOwnedInternalStorage() throws Exception {
        Context context = context("io.github.ouse.ouserdp", null);
        assertEquals(new File(context.getFilesDir(), "OuseRDP-Shared").getCanonicalFile(), RdpSharedFolder.directory(context));
    }
    @Test public void unusableTransferStorageDoesNotPreventTheDesktopConnection() throws Exception {
        Context context = context("io.github.ouse.ouserdp", temp.newFile("not-a-directory"));
        backend(context, mock(SessionState.class)).connect();
        assertEquals(Arrays.asList("touch", "parse", "connect"), calls);
    }
}
