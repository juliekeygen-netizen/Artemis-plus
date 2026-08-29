from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Pure taps/double taps in Move mode must not run the old final snap/resize pass.
replace_once(
    "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java",
    '''                    } else {
                        checkAndApplyResize();
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || moveGestureMoved) {''',
    '''                    } else {
                        if (moveGestureMoved) {
                            checkAndApplyResize();
                        }
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || moveGestureMoved) {'''
)

# 2) Keep group outline hidden until group move mode, and keep editor chrome above rebuilt controls.
controller = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java"
replace_once(
    controller,
    '''        groupOutline.setBackground(outline);
    }''',
    '''        groupOutline.setBackground(outline);
        groupOutline.setVisibility(View.GONE);
    }'''
)
replace_once(
    controller,
    '''        ArtemisActionButtonFactory.restoreSelectedActions(this, context);
        KeyComboManager.restore(this, context);

        // Re-apply editor visibility because refreshLayout() is also used for profile switching.''',
    '''        ArtemisActionButtonFactory.restoreSelectedActions(this, context);
        KeyComboManager.restore(this, context);

        // Native/custom controls are appended after the editor chrome. Bring the editor controls
        // back above them so Profiles/top buttons cannot be covered by a large user control.
        buttonConfigure.bringToFront();
        buttonClearAll.bringToFront();
        buttonAddKeys.bringToFront();
        buttonAddActions.bringToFront();
        buttonResetAll.bringToFront();
        buttonProfiles.bringToFront();
        buttonAcceptGroupMove.bringToFront();

        // Re-apply editor visibility because refreshLayout() is also used for profile switching.'''
)

# 3) AlertDialog weight+0-height layouts can collapse. Give the profile list a bounded real height.
profile_dialog = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesDialog.java"
replace_once(
    profile_dialog,
    '''        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));''',
    '''        int listHeight = Math.min(
                Math.round(360 * context.getResources().getDisplayMetrics().density),
                Math.max(1, context.getResources().getDisplayMetrics().heightPixels / 2));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listHeight));'''
)

# 4) Make Settings wording match the new additive multi-profile behavior.
preferences = "app/src/main/res/xml/preferences.xml"
replace_once(
    preferences,
    '''        <Preference
            android:key="import_keyboard_file"
            android:summary="@string/summary_import_keyboard_layout"
            android:title="@string/title_import_keyboard_layout"
            app:iconSpaceReserved="false" />''',
    '''        <Preference
            android:key="import_keyboard_file"
            android:summary="Add profiles from an Artemis Plus profile bundle or legacy layout file."
            android:title="Import Keyboard Profiles"
            app:iconSpaceReserved="false" />'''
)
replace_once(
    preferences,
    '''        <Preference
            android:key="export_keyboard_file"
            android:summary="@string/summary_export_keyboard_layout"
            android:title="@string/title_export_keyboard_layout"
            app:iconSpaceReserved="false" />''',
    '''        <Preference
            android:key="export_keyboard_file"
            android:summary="Export all keyboard profiles, custom keys, and Artemis action selections."
            android:title="Export Keyboard Profiles"
            app:iconSpaceReserved="false" />'''
)
