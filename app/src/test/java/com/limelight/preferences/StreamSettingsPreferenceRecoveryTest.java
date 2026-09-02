package com.limelight.preferences;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class StreamSettingsPreferenceRecoveryTest {
    private static final String ENABLE_PIP_KEY = "checkbox_enable_pip";

    private SharedPreferences basePrefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        basePrefs = PreferenceManager.getDefaultSharedPreferences(context);
        basePrefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        basePrefs.edit().clear().commit();
    }

    @Test
    public void wrongTypedBaseBooleanDoesNotCrashSettingsInflation() {
        basePrefs.edit()
                .putString(ENABLE_PIP_KEY, "corrupt")
                .commit();

        try (ActivityController<StreamSettings> controller = Robolectric.buildActivity(StreamSettings.class)) {
            StreamSettings activity = controller.create().start().resume().get();
            StreamSettings.SettingsFragment fragment = new StreamSettings.SettingsFragment();

            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.stream_settings, fragment)
                    .commitNow();

            assertNotNull(fragment.findPreference(ENABLE_PIP_KEY));
        }
    }
}
