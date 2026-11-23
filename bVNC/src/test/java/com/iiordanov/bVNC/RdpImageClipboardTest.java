package com.iiordanov.bVNC;

import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.undatech.opaque.NativeRdpClipboard;
import com.undatech.opaque.RdpCommunicator;
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
@Config(shadows = {RdpImageClipboardTest.Images.class, RdpImageClipboardTest.Text.class},
        instrumentedPackages = {"com.freerdp.freerdpcore.services"})
public class RdpImageClipboardTest {
    @Implements(NativeRdpClipboard.class) public static class Images {
        static int offers, clears;
        @Implementation protected static boolean send(long instance, byte[] png, byte[] dib) { offers++; return true; }
        @Implementation protected static void clear(long instance) { clears++; }
        @Implementation protected static boolean ready(long instance) { return true; }
    }
    @Implements(value = LibFreeRDP.class, isInAndroidSdk = false) public static class Text {
        static int sent;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static boolean sendClipboardData(long instance, String value) { sent++; return true; }
    }
    private RdpCommunicator backend() {
        Images.offers = Images.clears = Text.sent = 0;
        RdpCommunicator backend = mock(RdpCommunicator.class, CALLS_REAL_METHODS);
        SessionState session = mock(SessionState.class); when(session.getInstance()).thenReturn(42L);
        ReflectionHelpers.setField(backend, "session", session);
        ReflectionHelpers.setField(backend, "isInNormalProtocol", true);
        ReflectionHelpers.setField(backend, "clipboardGeneration", 1L);
        return backend;
    }
    @Test public void imageIsNotOverwrittenByUnchangedAndroidText() {
        var backend = backend();
        assertTrue(backend.writeClipboardImage(1, new byte[1], new byte[1], "old text"));
        backend.writeClientCutText("old text");
        assertEquals(1, Images.offers); assertEquals(0, Text.sent); assertEquals(0, Images.clears);
        backend.writeClientCutText("new text");
        assertEquals(1, Text.sent); assertEquals(1, Images.clears);
        backend.writeClientCutText("old text"); assertEquals(2, Text.sent);
    }
    @Test public void reconnectOrCloseCannotPublishAnImageIntoAnotherSession() {
        var backend = backend();
        ReflectionHelpers.setField(backend, "clipboardGeneration", 2L);
        assertFalse(backend.writeClipboardImage(1, new byte[1], new byte[1], "text"));
        ReflectionHelpers.setField(backend, "disconnectRequested", true);
        assertFalse(backend.writeClipboardImage(2, new byte[1], new byte[1], "text"));
        assertEquals(0, Images.offers); assertEquals(0, backend.clipboardSession());
    }
    @Test public void disconnectedSessionDoesNotOfferImages() {
        var backend = backend(); ReflectionHelpers.setField(backend, "isInNormalProtocol", false);
        assertFalse(backend.canCopyImage());
        assertFalse(backend.writeClipboardImage(1, new byte[1], new byte[1], null));
        assertEquals(0, Images.offers);
    }
}
