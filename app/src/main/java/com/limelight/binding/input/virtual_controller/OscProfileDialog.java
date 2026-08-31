package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.ui.ArtemisEditorUi;

import java.util.ArrayList;
import java.util.List;

/** In-stream OSC profile/configuration UI opened from the controller settings button. */
public final class OscProfileDialog {
    private OscProfileDialog() {
    }

    public static void show(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        String activeName = active != null ? active.getName()
                : context.getString(R.string.artemis_osc_default_name);
        String gameKey = controller.getGameProfileKey();
        String gameName = gameLabel(context, controller.getGameDisplayName());
        String mappedProfileId = gameKey != null
                ? OscProfilesManager.getProfileForGame(context, gameKey)
                : null;
        String mappedProfileName = findProfileName(context, mappedProfileId);

        ArrayList<String> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();

        addItem(items, actions, context.getString(R.string.artemis_osc_switch_profile),
                () -> showProfilePicker(controller, context));
        addItem(items, actions, context.getString(R.string.artemis_osc_new_profile),
                () -> showCreateDialog(controller, context));
        addItem(items, actions, context.getString(R.string.artemis_osc_rename_current),
                () -> showRenameDialog(controller, context));
        addItem(items, actions, context.getString(R.string.artemis_osc_delete_current),
                () -> showDeleteDialog(controller, context));
        addItem(items, actions, context.getString(R.string.artemis_osc_save_layout), () -> {
            VirtualControllerConfigurationLoader.saveProfile(controller, context);
            Toast.makeText(context, R.string.artemis_osc_layout_saved, Toast.LENGTH_SHORT).show();
        });

        if (gameKey != null) {
            String mappingLabel = mappedProfileName == null
                    ? context.getString(R.string.artemis_osc_not_set) : mappedProfileName;
            addItem(items, actions,
                    context.getString(R.string.artemis_osc_auto_profile, gameName, mappingLabel),
                    () -> showGameProfilePicker(controller, context, gameKey, gameName));
            if (mappedProfileId != null) {
                addItem(items, actions,
                        context.getString(R.string.artemis_osc_clear_auto_profile, gameName), () -> {
                    if (OscProfilesManager.clearProfileForGame(context, gameKey)) {
                        Toast.makeText(context, context.getString(
                                R.string.artemis_osc_auto_profile_cleared, gameName),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        addItem(items, actions,
                context.getString(R.string.artemis_osc_snapping,
                        toggleLabel(context, controller.isSnappingEnabled())), () -> {
                    boolean snapping = controller.toggleSnapping();
                    Toast.makeText(context, context.getString(R.string.artemis_osc_snapping_state,
                            enabledLabel(context, snapping)),
                            Toast.LENGTH_SHORT).show();
                });
        addItem(items, actions,
                context.getString(R.string.artemis_osc_paired_sizing,
                        toggleLabel(context, controller.isPairedSizingEnabled())), () -> {
                    boolean paired = controller.togglePairedSizing();
                    Toast.makeText(context, context.getString(R.string.artemis_osc_paired_sizing_state,
                            enabledLabel(context, paired)),
                            Toast.LENGTH_SHORT).show();
                });

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_profiles_title, activeName))
                .setItems(items.toArray(new String[0]), (selectionDialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 460);
    }

    private static void addItem(List<String> items,
                                List<Runnable> actions,
                                String label,
                                Runnable action) {
        items.add(label);
        actions.add(action);
    }

    private static void showProfilePicker(VirtualController controller, Context context) {
        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);
        String activeId = OscProfilesManager.getActiveProfileId(context);
        String[] names = new String[profiles.size()];

        for (int i = 0; i < profiles.size(); i++) {
            OscProfile profile = profiles.get(i);
            names[i] = (profile.getId().equals(activeId) ? "✓ " : "") + profile.getName();
        }

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_switch_title))
                .setItems(names, (selectionDialog, which) -> {
                    OscProfile selected = profiles.get(which);
                    if (OscProfilesManager.switchProfile(context, controller, selected.getId())) {
                        Toast.makeText(context, context.getString(
                                R.string.artemis_osc_profile_selected, selected.getName()),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 440);
    }

    private static void showGameProfilePicker(VirtualController controller,
                                              Context context,
                                              String gameKey,
                                              String gameName) {
        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);
        String mappedId = OscProfilesManager.getProfileForGame(context, gameKey);
        String[] names = new String[profiles.size()];

        for (int i = 0; i < profiles.size(); i++) {
            OscProfile profile = profiles.get(i);
            names[i] = (profile.getId().equals(mappedId) ? "✓ " : "") + profile.getName();
        }

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_auto_title, gameName))
                .setItems(names, (selectionDialog, which) -> {
                    OscProfile selected = profiles.get(which);
                    if (!OscProfilesManager.setProfileForGame(
                            context, gameKey, selected.getId())) {
                        Toast.makeText(context, R.string.artemis_osc_save_game_profile_error,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Apply the newly selected mapping immediately so the current stream matches
                    // what will be chosen automatically on future launches.
                    OscProfilesManager.switchProfile(context, controller, selected.getId());
                    Toast.makeText(context, context.getString(R.string.artemis_osc_auto_profile,
                            gameName, selected.getName()),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 440);
    }

    private static void showCreateDialog(VirtualController controller, Context context) {
        EditText input = buildNameInput(ArtemisEditorUi.context(context));
        input.setHint(R.string.artemis_osc_profile_name_hint);

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_new_title))
                .setView(input)
                .setPositiveButton(R.string.artemis_create, (confirmation, which) -> {
                    OscProfile profile = OscProfilesManager.createProfile(
                            context,
                            input.getText().toString());
                    OscProfilesManager.switchProfile(context, controller, profile.getId());
                    Toast.makeText(context, context.getString(R.string.artemis_osc_created,
                            profile.getName()),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 420);
    }

    private static void showRenameDialog(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        if (active == null) {
            return;
        }

        EditText input = buildNameInput(ArtemisEditorUi.context(context));
        input.setText(active.getName());
        input.setSelection(input.getText().length());

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_rename_title))
                .setView(input)
                .setPositiveButton(R.string.artemis_rename, (confirmation, which) -> {
                    OscProfilesManager.renameProfile(
                            context,
                            active.getId(),
                            input.getText().toString());
                    Toast.makeText(context, R.string.artemis_osc_renamed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 420);
    }

    private static void showDeleteDialog(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        if (active == null) {
            return;
        }
        if (active.isDefault()) {
            Toast.makeText(context, R.string.artemis_osc_default_delete_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog dialog = ArtemisEditorUi.builder(context,
                        context.getString(R.string.artemis_osc_delete_title))
                .setMessage(context.getString(R.string.artemis_osc_delete_message, active.getName()))
                .setPositiveButton(R.string.artemis_delete, (confirmation, which) -> {
                    String deletedId = active.getId();
                    // Switch first so the working OSC set is restored to a valid profile.
                    OscProfilesManager.switchProfile(
                            context,
                            controller,
                            OscProfile.DEFAULT_ID);
                    OscProfilesManager.deleteProfile(context, deletedId);
                    Toast.makeText(context, R.string.artemis_osc_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showStyled(context, dialog, 420);
    }

    private static String findProfileName(Context context, String profileId) {
        if (profileId == null) {
            return null;
        }
        for (OscProfile profile : OscProfilesManager.getProfiles(context)) {
            if (profileId.equals(profile.getId())) {
                return profile.getName();
            }
        }
        return null;
    }

    private static String gameLabel(Context context, String value) {
        return value == null || value.trim().isEmpty()
                ? context.getString(R.string.artemis_osc_this_game) : value.trim();
    }

    private static String toggleLabel(Context context, boolean enabled) {
        return context.getString(enabled ? R.string.artemis_osc_on : R.string.artemis_osc_off);
    }

    private static String enabledLabel(Context context, boolean enabled) {
        return context.getString(enabled ? R.string.artemis_osc_enabled
                : R.string.artemis_osc_disabled);
    }

    private static void showStyled(Context context, AlertDialog dialog, int widthDp) {
        dialog.setOnShowListener(ignored -> ArtemisEditorUi.styleDialog(dialog, context, widthDp));
        dialog.show();
    }

    private static EditText buildNameInput(Context context) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        return input;
    }
}
