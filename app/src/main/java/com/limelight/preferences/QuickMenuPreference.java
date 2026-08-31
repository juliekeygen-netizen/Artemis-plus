package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.limelight.quickmenu.QuickMenuEditorDialog;

/** Settings entry for the shared customizable in-stream Quick Menu editor. */
public class QuickMenuPreference extends Preference {
    public QuickMenuPreference(Context context) {
        this(context, null);
    }

    public QuickMenuPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public QuickMenuPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnPreferenceClickListener(preference -> {
            QuickMenuEditorDialog.show(getContext());
            return true;
        });
    }
}
