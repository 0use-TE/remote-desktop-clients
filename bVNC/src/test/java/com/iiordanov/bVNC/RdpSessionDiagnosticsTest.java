package com.iiordanov.bVNC;

import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.RdpSessionDiagnostics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class RdpSessionDiagnosticsTest {
    @Test public void summarizesRealCopyCountsAndDurationsWithoutCallingThemFps() {
        var diagnostics = RdpSessionDiagnostics.start();
        diagnostics.display(2560, 1600, 150); diagnostics.view(3504, 2190);
        diagnostics.frame(10, 20, 2_000_000, true);
        diagnostics.frame(10, 20, 4_000_000, true);
        diagnostics.frame(10, 20, 1_000_000, false);
        String report = diagnostics.report();
        assertTrue(report.contains("Copied updates: 2; failed copies: 1"));
        assertTrue(report.contains("Copied pixels: 400"));
        assertTrue(report.contains("Copy mean/max ms: 3.000 / 4.000"));
        assertTrue(report.contains("Remote desktop: 2560x1600"));
        assertFalse(report.contains("FPS"));
    }
    @Test public void eventHistoryStaysBoundedAcrossManyReconnects() {
        var diagnostics = RdpSessionDiagnostics.start();
        for (int i = 0; i < 1000; i++) diagnostics.state(RdpCommunicator.ConnectionState.RECONNECTING, i, 1000);
        String report = diagnostics.report();
        assertEquals(32, report.lines().filter(line -> line.matches("[0-9]+ ms: .*" )).count());
        assertFalse(report.contains("attempt=1 "));
        assertTrue(report.contains("attempt=999 "));
    }
    @Test public void lastSessionMetadataCanBeExportedAfterProcessRestart() {
        var diagnostics = RdpSessionDiagnostics.start();
        diagnostics.display(1920, 1200, 125);
        diagnostics.state(RdpCommunicator.ConnectionState.DISCONNECTED, 0, 0);
        diagnostics.persist(RuntimeEnvironment.getApplication());
        ReflectionHelpers.setStaticField(RdpSessionDiagnostics.class, "latest", null);
        assertTrue(RdpSessionDiagnostics.export(RuntimeEnvironment.getApplication()).contains("Remote desktop: 1920x1200"));
    }
}
