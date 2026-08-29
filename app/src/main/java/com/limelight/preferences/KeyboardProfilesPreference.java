package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesDialog;

/** Settings entry that opens the same profile manager used by the in-stream editor. */
public class KeyboardProfilesPreference extends Preference {
    public KeyboardProfilesPreference(Context context) {
        this(context, null);
    }

    public KeyboardProfilesPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public KeyboardProfilesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnPreferenceClickListener(preference -> {
            KeyboardProfilesDialog.show(getContext(), null);
            return true;
        });
    }
}
