package io.github.ouse.ouserdp;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class DisplayResizeControllerTest {
    static class Server implements DisplayResizeController.Target {
        boolean ready = true;
        final ArrayList<String> requests = new ArrayList<>();
        public boolean ready() { return ready; }
        public void resize(int w, int h, int s) { requests.add(w + "x" + h + "@" + s); }
    }
    private void waitMs(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    @Test public void resizeGestureSendsOnlyLatestStableDimensions() {
        Server server = new Server();
        var controller = new DisplayResizeController(new Handler(Looper.getMainLooper()), server);
        controller.start(); controller.update(2000, 1000, 125); waitMs(400);
        controller.update(1600, 2560, 150); waitMs(400);
        assertTrue(server.requests.isEmpty()); waitMs(100);
        assertEquals(java.util.List.of("1600x2560@150"), server.requests);
    }
    @Test public void lateServerCapabilitiesApplyLatestSize() {
        Server server = new Server(); server.ready = false;
        var controller = new DisplayResizeController(new Handler(Looper.getMainLooper()), server);
        controller.start(); controller.update(2560, 1600, 150); waitMs(1500);
        server.ready = true; waitMs(500);
        assertEquals(1, server.requests.size());
    }
    @Test public void unsupportedServerStopsPollingWithoutReconnectOrResize() {
        Server server = new Server(); server.ready = false;
        var controller = new DisplayResizeController(new Handler(Looper.getMainLooper()), server);
        controller.start(); controller.update(2560, 1600, 150); waitMs(20000);
        server.ready = true; waitMs(20000);
        assertTrue(server.requests.isEmpty());
    }
    @Test public void hiddenActivityCancelsPendingResizeAndAppliesItOnReturn() {
        Server server = new Server();
        var controller = new DisplayResizeController(new Handler(Looper.getMainLooper()), server);
        controller.start(); controller.update(2560, 1600, 150); controller.stop(); waitMs(1000);
        assertTrue(server.requests.isEmpty());
        controller.start(); waitMs(500); assertEquals(1, server.requests.size());
        controller.update(2560, 1600, 150); waitMs(500); assertEquals(1, server.requests.size());
    }
    @Test public void reconnectAllowsResendingSameSizeToTheNewServerSession() {
        Server server = new Server();
        var controller = new DisplayResizeController(new Handler(Looper.getMainLooper()), server);
        controller.start(); controller.update(2560, 1600, 150); waitMs(500);
        controller.reconnect(); waitMs(500); assertEquals(2, server.requests.size());
    }
}
