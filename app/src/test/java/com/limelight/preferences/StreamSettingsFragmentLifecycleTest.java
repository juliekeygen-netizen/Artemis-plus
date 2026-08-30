package com.limelight.preferences;

import static org.junit.Assert.assertNotNull;

import com.limelight.EditProfileActivity;
import org.junit.Test;

/** Guards FragmentManager restoration requirements for settings surfaces. */
public class StreamSettingsFragmentLifecycleTest {
    @Test
    public void settingsFragmentHasPublicNoArgConstructor() throws Exception {
        assertNotNull(StreamSettings.SettingsFragment.class.getConstructor());
    }

    @Test
    public void profileSettingsFragmentHasPublicNoArgConstructor() throws Exception {
        assertNotNull(EditProfileActivity.ProfilePreferenceFragment.class.getConstructor());
    }
}
