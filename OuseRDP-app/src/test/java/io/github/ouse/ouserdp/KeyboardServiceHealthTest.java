package io.github.ouse.ouserdp;

import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class KeyboardServiceHealthTest {
    private Application app;
    private ComponentName own;
    private final String other = "example.other/.KeyService";

    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        own = KeyboardServiceHealth.component(app);
        Settings.Secure.putInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    private String enabled() {
        return Settings.Secure.getString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    }
    private void enabled(String value) {
        Settings.Secure.putString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value);
    }
    private void grant() { Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS); }

    @Test public void matchesExactComponentsAndPreservesOtherServices() {
        ComponentName target = new ComponentName("example", "example.KeyService");
        String list = other + ":example/.KeyService:example.extra/.KeyService";
        assertTrue(KeyboardServiceHealth.contains(list, target));
        assertFalse(KeyboardServiceHealth.contains(other, target));
        assertEquals(other + ":example.extra/.KeyService", KeyboardServiceHealth.withService(list, target, false));
        assertEquals(other + ":example.extra/.KeyService:example/example.KeyService", KeyboardServiceHealth.withService(list, target, true));
    }

    @Test public void authorizedButUnboundDoesNotAppearHealthy() {
        enabled(own.flattenToString());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(KeyboardServiceHealth.State.UNAVAILABLE, KeyboardServiceHealth.state(app));
    }

    @Test public void permissionMissingNeverChangesSettings() {
        enabled(other + ":" + own.flattenToString());
        String before = enabled();
        assertFalse(KeyboardServiceHealth.recover(app));
        KeyboardServiceHealth.poll(app);
        assertEquals(before, enabled());
    }

    @Test public void connectedCallbackIsAuthoritativeWhenManagerListLags() {
        enabled(own.flattenToString());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(KeyboardServiceHealth.State.UNAVAILABLE, KeyboardServiceHealth.state(app));
        try (var service = org.mockito.Mockito.mockStatic(com.iiordanov.bVNC.WindowsKeyCaptureService.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            service.when(com.iiordanov.bVNC.WindowsKeyCaptureService::isConnected).thenReturn(true);
            var manager = (android.view.accessibility.AccessibilityManager) app.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
            Shadows.shadowOf(manager).setEnabledAccessibilityServiceList(java.util.List.of());
            assertEquals(KeyboardServiceHealth.State.RUNNING, KeyboardServiceHealth.state(app));
            grant();
            String before = enabled();
            KeyboardServiceHealth.poll(app);
            assertEquals(before, enabled());
        }
    }

    @Test public void recoveryChangesOnlyOwnEntryAndMergesConcurrentChanges() {
        grant();
        enabled(other + ":" + own.flattenToString());
        assertTrue(KeyboardServiceHealth.recover(app));
        assertEquals(other, enabled());
        assertEquals(KeyboardServiceHealth.State.CONNECTING, KeyboardServiceHealth.state(app));
        enabled(other + ":new.service/.Service");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(other + ":new.service/.Service:" + own.flattenToString(), enabled());
        assertFalse(app.getSharedPreferences("keyboardServiceHealth", 0).getBoolean("pendingRecovery", false));
        assertEquals(1, Settings.Secure.getInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0));
    }

    @Test public void neverEnablesServiceUserHasDisabled() {
        grant(); enabled(other);
        assertFalse(KeyboardServiceHealth.recover(app));
        KeyboardServiceHealth.poll(app);
        assertEquals(other, enabled());
        assertEquals(KeyboardServiceHealth.State.DISABLED, KeyboardServiceHealth.state(app));
    }

    @Test public void globalDisableWhileRebindingCancelsRestoration() {
        grant(); enabled(other + ":" + own.flattenToString());
        assertTrue(KeyboardServiceHealth.recover(app));
        Settings.Secure.putInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(other, enabled());
        assertEquals(KeyboardServiceHealth.State.DISABLED, KeyboardServiceHealth.state(app));
    }

    @Test public void pendingRecoverySurvivesProcessInterruption() {
        grant(); enabled(other);
        app.getSharedPreferences("keyboardServiceHealth", 0).edit().putBoolean("pendingRecovery", true).commit();
        KeyboardServiceHealth.poll(app);
        assertEquals(other + ":" + own.flattenToString(), enabled());
    }

    @Test public void automaticRecoveryHasCooldown() {
        grant(); enabled(other + ":" + own.flattenToString());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        KeyboardServiceHealth.poll(app);
        assertEquals(other, enabled());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        String restored = enabled();
        KeyboardServiceHealth.poll(app);
        assertEquals(restored, enabled());
        assertTrue(KeyboardServiceHealth.contains(restored, own));
    }
}
