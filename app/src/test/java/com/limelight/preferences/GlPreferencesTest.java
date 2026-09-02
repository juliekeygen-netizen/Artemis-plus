package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Config(sdk = 33)
@RunWith(RobolectricTestRunner.class)
public class GlPreferencesTest {
    @Test
    public void wrongTypedStoredValuesFallBackAndCanBeRepairedByWrite() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("GlPreferences", Context.MODE_PRIVATE);
        prefs.edit().clear()
                .putInt("Renderer", 7)
                .putBoolean("Fingerprint", true)
                .commit();

        try {
            GlPreferences glPrefs = GlPreferences.readPreferences(context);
            assertEquals("", glPrefs.glRenderer);
            assertEquals("", glPrefs.savedFingerprint);

            glPrefs.glRenderer = "test-renderer";
            glPrefs.savedFingerprint = "test-fingerprint";
            assertTrue(glPrefs.writePreferences());

            assertEquals("test-renderer", prefs.getString("Renderer", null));
            assertEquals("test-fingerprint", prefs.getString("Fingerprint", null));
        } finally {
            prefs.edit().clear().commit();
        }
    }
}
