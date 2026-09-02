package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
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
    private SharedPreferences basePrefs;

    public static class PreferenceHostActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.AppTheme);
            super.onCreate(savedInstanceState);

            FrameLayout container = new FrameLayout(this);
            container.setId(android.R.id.content);
            setContentView(container);
        }
    }

    public static class InspectingSettingsFragment extends StreamSettings.SettingsFragment {
        private SharedPreferences capturedPrefs;

        @Override
        public void initializePreferences() {
            capturedPrefs = getPrefs();
        }

        SharedPreferences getCapturedPrefs() {
            return capturedPrefs;
        }
    }

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
    public void wrongTypedBaseIntegerFallsBackBeforeSettingsInflation() {
        basePrefs.edit()
                .putString(PreferenceConfiguration.BITRATE_PREF_STRING, "corrupt")
                .commit();

        try (ActivityController<PreferenceHostActivity> controller =
                     Robolectric.buildActivity(PreferenceHostActivity.class)) {
            PreferenceHostActivity activity = controller.create().start().resume().get();
            InspectingSettingsFragment fragment = new InspectingSettingsFragment();

            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commitNow();

            SharedPreferences prefs = fragment.getCapturedPrefs();
            assertNotNull(prefs);
            assertEquals(1234, prefs.getInt(PreferenceConfiguration.BITRATE_PREF_STRING, 1234));
        }
    }
}
