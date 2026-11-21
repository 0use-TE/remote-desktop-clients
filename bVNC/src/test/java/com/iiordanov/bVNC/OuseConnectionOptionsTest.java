package com.iiordanov.bVNC;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class OuseConnectionOptionsTest {
    @Test
    public void defaultsToClipboardOnlyAndStoresOptionsPerComputer() {
        Context context = RuntimeEnvironment.getApplication();
        OuseConnectionOptions.save(context, "work-computer", false, true, true);

        OuseConnectionOptions work = OuseConnectionOptions.load(context, "work-computer");
        assertFalse(work.clipboard);
        assertTrue(work.sound);
        assertTrue(work.microphone);

        OuseConnectionOptions other = OuseConnectionOptions.load(context, "other-computer");
        assertTrue(other.clipboard);
        assertFalse(other.sound);
        assertFalse(other.microphone);
    }
}
