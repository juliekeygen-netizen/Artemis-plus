package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

/** In-stream OSC profile/configuration UI opened from the controller settings button. */
public final class OscProfileDialog {
    private OscProfileDialog() {
    }

    public static void show(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        String activeName = active != null ? active.getName() : "Default";

        String[] items = new String[]{
                "Switch profile",
                "New profile",
                "Rename current profile",
                "Delete current profile",
                "Save current layout",
                "Snapping: " + (controller.isSnappingEnabled() ? "On" : "Off"),
                "Paired sizing: " + (controller.isPairedSizingEnabled() ? "On" : "Off")
        };

        new AlertDialog.Builder(context)
                .setTitle("OSC Profiles — " + activeName)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showProfilePicker(controller, context);
                            break;
                        case 1:
                            showCreateDialog(controller, context);
                            break;
                        case 2:
                            showRenameDialog(controller, context);
                            break;
                        case 3:
                            showDeleteDialog(controller, context);
                            break;
                        case 4:
                            VirtualControllerConfigurationLoader.saveProfile(controller, context);
                            Toast.makeText(context, "OSC layout saved", Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
                            boolean snapping = controller.toggleSnapping();
                            Toast.makeText(context,
                                    "OSC snapping " + (snapping ? "enabled" : "disabled"),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case 6:
                            boolean paired = controller.togglePairedSizing();
                            Toast.makeText(context,
                                    "Paired sizing " + (paired ? "enabled" : "disabled"),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showProfilePicker(VirtualController controller, Context context) {
        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);
        String activeId = OscProfilesManager.getActiveProfileId(context);
        String[] names = new String[profiles.size()];

        for (int i = 0; i < profiles.size(); i++) {
            OscProfile profile = profiles.get(i);
            names[i] = (profile.getId().equals(activeId) ? "✓ " : "") + profile.getName();
        }

        new AlertDialog.Builder(context)
                .setTitle("Switch OSC Profile")
                .setItems(names, (dialog, which) -> {
                    OscProfile selected = profiles.get(which);
                    if (OscProfilesManager.switchProfile(context, controller, selected.getId())) {
                        Toast.makeText(context,
                                "OSC profile: " + selected.getName(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showCreateDialog(VirtualController controller, Context context) {
        EditText input = buildNameInput(context);
        input.setHint("Profile name");

        new AlertDialog.Builder(context)
                .setTitle("New OSC Profile")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    OscProfile profile = OscProfilesManager.createProfile(
                            context,
                            input.getText().toString());
                    OscProfilesManager.switchProfile(context, controller, profile.getId());
                    Toast.makeText(context,
                            "Created " + profile.getName(),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showRenameDialog(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        if (active == null) {
            return;
        }

        EditText input = buildNameInput(context);
        input.setText(active.getName());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(context)
                .setTitle("Rename OSC Profile")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    OscProfilesManager.renameProfile(
                            context,
                            active.getId(),
                            input.getText().toString());
                    Toast.makeText(context, "Profile renamed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showDeleteDialog(VirtualController controller, Context context) {
        OscProfile active = OscProfilesManager.getActiveProfile(context);
        if (active == null) {
            return;
        }
        if (active.isDefault()) {
            Toast.makeText(context,
                    "The Default OSC profile cannot be deleted",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Delete OSC Profile?")
                .setMessage("Delete \"" + active.getName() + "\"? The saved layout will be removed.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    String deletedId = active.getId();
                    // Switch first so the working OSC set is restored to a valid profile.
                    OscProfilesManager.switchProfile(
                            context,
                            controller,
                            OscProfile.DEFAULT_ID);
                    OscProfilesManager.deleteProfile(context, deletedId);
                    Toast.makeText(context, "OSC profile deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
