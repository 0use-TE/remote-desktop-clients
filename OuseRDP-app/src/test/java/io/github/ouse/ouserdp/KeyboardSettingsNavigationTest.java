package io.github.ouse.ouserdp;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class KeyboardSettingsNavigationTest {
    @Test public void opensPublicAccessibilitySettingsWithOwnServiceHint() {
        Activity activity = spy(Robolectric.buildActivity(Activity.class).setup().get());
        doNothing().when(activity).startActivity(any(Intent.class));
        KeyboardServiceHealth.openSettings(activity);
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(activity).startActivity(intent.capture());
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.getValue().getAction());
        assertEquals(KeyboardServiceHealth.component(activity).flattenToString(),
                intent.getValue().getStringExtra(":settings:fragment_args_key"));
    }

    @Test public void protectedOemEntryFallsBackWithoutCrashing() {
        Activity activity = spy(Robolectric.buildActivity(Activity.class).setup().get());
        doThrow(new SecurityException("OEM restriction")).doNothing()
                .when(activity).startActivity(any(Intent.class));
        KeyboardServiceHealth.openSettings(activity);
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(activity, times(2)).startActivity(intent.capture());
        assertEquals(Settings.ACTION_SETTINGS, intent.getAllValues().get(1).getAction());
    }

    @Test public void missingSettingsAppShowsInstructionsWithoutCrashing() {
        Activity activity = spy(Robolectric.buildActivity(Activity.class).setup().get());
        doThrow(new ActivityNotFoundException()).when(activity).startActivity(any(Intent.class));
        KeyboardServiceHealth.openSettings(activity);
        assertEquals("请在系统设置中打开无障碍", ShadowToast.getTextOfLatestToast());
    }
}
