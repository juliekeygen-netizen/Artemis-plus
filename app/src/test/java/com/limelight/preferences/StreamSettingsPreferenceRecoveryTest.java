package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

import java.util.Collections;
import java.util.Set;

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

        boolean usesRecoveringPreferenceDataStore() {
            return getPreferenceManager().getPreferenceDataStore()
                    instanceof RecoveringPreferenceDataStore;
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
            assertTrue(prefs instanceof RecoveringPreferenceDataStore);
            assertTrue(fragment.usesRecoveringPreferenceDataStore());
            assertEquals(1234, prefs.getInt(PreferenceConfiguration.BITRATE_PREF_STRING, 1234));
        }
    }

    @Test
    public void recoveringStoreFallsBackAcrossTypesAndWritesThroughToBase() {
        basePrefs.edit()
                .putInt("string", 1)
                .putInt("string_set", 1)
                .putString("int", "wrong")
                .putBoolean("long", true)
                .putString("float", "wrong")
                .putString("boolean", "wrong")
                .commit();

        RecoveringPreferenceDataStore store = new RecoveringPreferenceDataStore(basePrefs);
        Set<String> fallbackSet = Collections.singleton("fallback");

        assertEquals("fallback", store.getString("string", "fallback"));
        assertEquals(fallbackSet, store.getStringSet("string_set", fallbackSet));
        assertEquals(7, store.getInt("int", 7));
        assertEquals(9L, store.getLong("long", 9L));
        assertEquals(1.5f, store.getFloat("float", 1.5f), 0.0f);
        assertTrue(store.getBoolean("boolean", true));

        store.putInt("int", 42);
        store.putString("string", "repaired");

        assertEquals(42, basePrefs.getInt("int", 0));
        assertEquals("repaired", basePrefs.getString("string", null));
    }
}
