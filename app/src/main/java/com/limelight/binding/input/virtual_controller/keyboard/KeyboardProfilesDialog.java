package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Shared keyboard-profile editor used in-stream and from Settings. */
public final class KeyboardProfilesDialog {
    private KeyboardProfilesDialog() {
    }

    public static void show(Context context, KeyBoardController controller) {
        KeyboardProfilesManager.ensureInitialized(context);

        int padding = Math.max(12,
                Math.round(12 * context.getResources().getDisplayMetrics().density));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding / 2, padding, 0);

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        Button add = new Button(context);
        add.setText("+ Add Profile");
        root.addView(add);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Keyboard Profiles")
                .setView(root)
                .setNegativeButton(android.R.string.closeButtonLabel, null)
                .create();

        Runnable[] rebuildHolder = new Runnable[1];
        rebuildHolder[0] = () -> rebuildRows(context, controller, rows, rebuildHolder[0]);

        add.setOnClickListener(v -> promptForName(context, "Add Profile", "Profile name", "", name -> {
            KeyboardProfilesManager.Profile profile = KeyboardProfilesManager.createProfile(context, name);
            if (profile != null) {
                switchProfile(context, controller, profile.id);
                rebuildHolder[0].run();
            }
        }));

        dialog.setOnShowListener(ignored -> rebuildHolder[0].run());
        dialog.show();
    }

    private static void rebuildRows(Context context,
                                    KeyBoardController controller,
                                    LinearLayout container,
                                    Runnable rebuild) {
        container.removeAllViews();
        List<KeyboardProfilesManager.Profile> profiles = KeyboardProfilesManager.getProfiles(context);
        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);

        for (int i = 0; i < profiles.size(); i++) {
            final int index = i;
            KeyboardProfilesManager.Profile profile = profiles.get(i);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button up = compactButton(context, "↑");
            up.setEnabled(i > 0);
            up.setContentDescription("Move profile up");
            up.setOnClickListener(v -> {
                KeyboardProfilesManager.moveProfile(context, profile.id, -1);
                rebuild.run();
            });
            row.addView(up);

            Button down = compactButton(context, "↓");
            down.setEnabled(i < profiles.size() - 1);
            down.setContentDescription("Move profile down");
            down.setOnClickListener(v -> {
                KeyboardProfilesManager.moveProfile(context, profile.id, 1);
                rebuild.run();
            });
            row.addView(down);

            TextView name = new TextView(context);
            name.setText((profile.id.equals(active.id) ? "✓  " : "   ") + profile.name);
            name.setTextSize(17f);
            name.setGravity(Gravity.CENTER_VERTICAL);
            if (profile.id.equals(active.id)) {
                name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            name.setPadding(10, 12, 10, 12);
            name.setOnClickListener(v -> {
                switchProfile(context, controller, profile.id);
                rebuild.run();
            });
            row.addView(name, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f));

            Button more = compactButton(context, "⋮");
            more.setContentDescription("Profile options");
            more.setOnClickListener(v -> showProfileMenu(
                    context,
                    controller,
                    profile,
                    profiles.size(),
                    more,
                    rebuild));
            row.addView(more);

            container.addView(row);

            if (index < profiles.size() - 1) {
                View divider = new View(context);
                divider.setBackgroundColor(0x33FFFFFF);
                container.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(context.getResources().getDisplayMetrics().density))));
            }
        }
    }

    private static void showProfileMenu(Context context,
                                        KeyBoardController controller,
                                        KeyboardProfilesManager.Profile profile,
                                        int profileCount,
                                        View anchor,
                                        Runnable rebuild) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add("Rename");
        menu.getMenu().add("Duplicate");
        menu.getMenu().add("Delete").setEnabled(profileCount > 1);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Rename".equals(title)) {
                promptForName(context, "Rename Profile", "Profile name", profile.name, name -> {
                    KeyboardProfilesManager.renameProfile(context, profile.id, name);
                    rebuild.run();
                });
            } else if ("Duplicate".equals(title)) {
                KeyboardProfilesManager.Profile duplicate =
                        KeyboardProfilesManager.duplicateProfile(context, profile.id);
                if (duplicate != null) {
                    rebuild.run();
                }
            } else if ("Delete".equals(title)) {
                new AlertDialog.Builder(context)
                        .setTitle("Delete Profile?")
                        .setMessage("Delete “" + profile.name + "”? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            KeyboardProfilesManager.Profile before =
                                    KeyboardProfilesManager.getActiveProfile(context);
                            if (KeyboardProfilesManager.deleteProfile(context, profile.id)) {
                                KeyboardProfilesManager.Profile after =
                                        KeyboardProfilesManager.getActiveProfile(context);
                                if (controller != null && before.id.equals(profile.id)) {
                                    controller.reloadCurrentProfile();
                                }
                                rebuild.run();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private static void switchProfile(Context context,
                                      KeyBoardController controller,
                                      String profileId) {
        KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(context);
        if (current.id.equals(profileId)) {
            return;
        }
        if (controller != null) {
            controller.switchKeyboardProfile(profileId);
        } else {
            KeyboardProfilesManager.setActiveProfile(context, profileId);
            Toast.makeText(context, "Keyboard profile selected", Toast.LENGTH_SHORT).show();
        }
    }

    private interface NameCallback {
        void onName(String name);
    }

    private static void promptForName(Context context,
                                      String title,
                                      String hint,
                                      String initial,
                                      NameCallback callback) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(initial);
        if (!initial.isEmpty()) {
            input.setSelection(initial.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError("Enter a profile name");
                        return;
                    }
                    callback.onName(value);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static Button compactButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        int horizontal = Math.max(2, Math.round(4 * context.getResources().getDisplayMetrics().density));
        button.setPadding(horizontal, 0, horizontal, 0);
        return button;
    }
}
