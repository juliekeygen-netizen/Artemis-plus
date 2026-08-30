package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.ui.ArtemisEditorUi;

import java.util.ArrayList;
import java.util.List;

/** Shared keyboard-profile editor used in-stream and from Settings. */
public final class KeyboardProfilesDialog {
    private KeyboardProfilesDialog() {}

    public static void show(Context context, KeyBoardController controller) {
        KeyboardProfilesManager.ensureInitialized(context);
        Context ui = ArtemisEditorUi.context(context);
        int padding = ArtemisEditorUi.dp(ui, 12);

        LinearLayout root = new LinearLayout(ui);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, ArtemisEditorUi.dp(ui, 8), padding, ArtemisEditorUi.dp(ui, 6));
        root.setBackgroundColor(ArtemisEditorUi.SURFACE);

        RecyclerView list = new RecyclerView(ui);
        list.setLayoutManager(new LinearLayoutManager(ui));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, ArtemisEditorUi.dp(ui, 4));
        int listHeight = Math.min(ArtemisEditorUi.dp(ui, 330),
                Math.max(ArtemisEditorUi.dp(ui, 120),
                        context.getResources().getDisplayMetrics().heightPixels / 2));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight));

        ProfileAdapter adapter = new ProfileAdapter(
                ui, context, controller, new ArrayList<>(KeyboardProfilesManager.getProfiles(context)));
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                int from = vh.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
                return from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION &&
                        from != to && adapter.moveProfile(from, to);
            }
            @Override public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override public boolean isLongPressDragEnabled() { return true; }
        }).attachToRecyclerView(list);

        AlertDialog dialog = ArtemisEditorUi.builder(ui, "Keyboard Profiles")
                .setView(root)
                .setNeutralButton("+ Add profile", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(dialog, context, 520);
            Button add = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            ArtemisEditorUi.styleFooterButton(add, ArtemisEditorUi.ACCENT);
            add.setOnClickListener(v -> promptForName(ui, "Add Profile", "Profile name", "", name -> {
                KeyboardProfilesManager.Profile profile = KeyboardProfilesManager.createProfile(context, name);
                if (profile != null) {
                    switchProfile(context, controller, profile.id);
                    adapter.replaceProfiles(KeyboardProfilesManager.getProfiles(context));
                    list.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                }
            }));
        });
        dialog.show();
    }

    private static final class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.Holder> {
        private final Context ui;
        private final Context app;
        private final KeyBoardController controller;
        private final List<KeyboardProfilesManager.Profile> profiles = new ArrayList<>();
        private String activeId;

        ProfileAdapter(Context ui, Context app, KeyBoardController controller,
                       List<KeyboardProfilesManager.Profile> initial) {
            this.ui = ui; this.app = app; this.controller = controller; replaceProfiles(initial);
        }

        void replaceProfiles(List<KeyboardProfilesManager.Profile> updated) {
            profiles.clear(); profiles.addAll(updated);
            KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(app);
            activeId = active == null ? "" : active.id;
            notifyDataSetChanged();
        }

        boolean moveProfile(int from, int to) {
            if (from < 0 || to < 0 || from >= profiles.size() || to >= profiles.size()) return false;
            KeyboardProfilesManager.Profile moving = profiles.get(from);
            int direction = to > from ? 1 : -1;
            for (int i = 0; i < Math.abs(to - from); i++) {
                if (!KeyboardProfilesManager.moveProfile(app, moving.id, direction)) {
                    replaceProfiles(KeyboardProfilesManager.getProfiles(app));
                    return false;
                }
            }
            profiles.remove(from); profiles.add(to, moving); notifyItemMoved(from, to); return true;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardView card = new CardView(ui);
            card.setRadius(ArtemisEditorUi.dp(ui, 10));
            card.setCardElevation(0);
            card.setUseCompatPadding(false);
            LinearLayout row = new LinearLayout(ui);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ArtemisEditorUi.dp(ui, 6), 0, ArtemisEditorUi.dp(ui, 4), 0);
            card.addView(row, new CardView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 54)));

            TextView drag = new TextView(ui);
            drag.setText("≡"); drag.setTextColor(0xFF94949B); drag.setTextSize(21f);
            drag.setGravity(Gravity.CENTER); drag.setContentDescription("Hold and drag profile to reorder");
            row.addView(drag, new LinearLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 48), ArtemisEditorUi.dp(ui, 48)));

            TextView name = new TextView(ui);
            name.setTextColor(Color.WHITE); name.setTextSize(15.5f); name.setMaxLines(1);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            FrameLayout moreTouch = new FrameLayout(ui);
            TextView moreIcon = new TextView(ui);
            moreIcon.setText("⋮"); moreIcon.setTextColor(0xFFE8E8EA); moreIcon.setTextSize(21f);
            moreIcon.setGravity(Gravity.CENTER);
            moreIcon.setBackground(ArtemisEditorUi.rounded(ui, 0x22FFFFFF, 8, 0, 0));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 32), ArtemisEditorUi.dp(ui, 32), Gravity.CENTER);
            moreTouch.addView(moreIcon, iconParams);
            moreTouch.setContentDescription("Profile options");
            row.addView(moreTouch, new LinearLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 48), ArtemisEditorUi.dp(ui, 48)));

            RecyclerView.LayoutParams outer = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 54));
            outer.setMargins(0, ArtemisEditorUi.dp(ui, 3), 0, ArtemisEditorUi.dp(ui, 3));
            card.setLayoutParams(outer);
            return new Holder(card, name, moreTouch);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            KeyboardProfilesManager.Profile profile = profiles.get(position);
            boolean active = profile.id.equals(activeId);
            holder.name.setText(profile.name);
            ((CardView) holder.itemView).setCardBackgroundColor(
                    active ? ArtemisEditorUi.SURFACE_SELECTED : ArtemisEditorUi.SURFACE_RAISED);
            holder.itemView.setOnClickListener(v -> {
                switchProfile(app, controller, profile.id);
                KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(app);
                activeId = current == null ? "" : current.id;
                notifyDataSetChanged();
            });
            holder.more.setOnClickListener(v -> showProfileMenu(
                    ui, app, controller, profile, profiles.size(), holder.more,
                    () -> replaceProfiles(KeyboardProfilesManager.getProfiles(app))));
        }

        @Override public int getItemCount() { return profiles.size(); }
        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name; final View more;
            Holder(View item, TextView name, View more) { super(item); this.name = name; this.more = more; }
        }
    }

    private static void showProfileMenu(Context ui, Context app, KeyBoardController controller,
                                        KeyboardProfilesManager.Profile profile, int count,
                                        View anchor, Runnable rebuild) {
        int popupWidth = ArtemisEditorUi.dp(ui, 132);
        int itemHeight = ArtemisEditorUi.dp(ui, 38);
        int popupHeight = itemHeight * 3 + ArtemisEditorUi.dp(ui, 8);
        LinearLayout content = new LinearLayout(ui);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, ArtemisEditorUi.dp(ui, 4), 0, ArtemisEditorUi.dp(ui, 4));
        content.setBackground(ArtemisEditorUi.rounded(ui, ArtemisEditorUi.SURFACE_RAISED,
                10, 1, ArtemisEditorUi.BORDER));
        PopupWindow popup = new PopupWindow(content, popupWidth, popupHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(ArtemisEditorUi.dp(ui, 10));

        content.addView(menuItem(ui, "Rename", Color.WHITE, true, itemHeight, () -> {
            popup.dismiss();
            promptForName(ui, "Rename Profile", "Profile name", profile.name, name -> {
                KeyboardProfilesManager.renameProfile(app, profile.id, name); rebuild.run();
            });
        }));
        content.addView(menuItem(ui, "Duplicate", Color.WHITE, true, itemHeight, () -> {
            popup.dismiss(); KeyboardProfilesManager.duplicateProfile(app, profile.id); rebuild.run();
        }));
        content.addView(menuItem(ui, "Delete", count > 1 ? ArtemisEditorUi.DANGER : 0xFF77777D,
                count > 1, itemHeight, () -> {
                    popup.dismiss();
                    new AlertDialog.Builder(ui).setTitle("Delete Profile?")
                            .setMessage("Delete “" + profile.name + "”? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) -> {
                                KeyboardProfilesManager.Profile before = KeyboardProfilesManager.getActiveProfile(app);
                                if (KeyboardProfilesManager.deleteProfile(app, profile.id)) {
                                    if (controller != null && before != null && before.id.equals(profile.id))
                                        controller.reloadCurrentProfile();
                                    rebuild.run();
                                }
                            }).setNegativeButton(android.R.string.cancel, null).show();
                }));

        int xOffset = -popupWidth + anchor.getWidth();
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int screenHeight = ui.getResources().getDisplayMetrics().heightPixels;
        int spaceBelow = screenHeight - (location[1] + anchor.getHeight());
        int yOffset = spaceBelow >= popupHeight + ArtemisEditorUi.dp(ui, 6)
                ? -ArtemisEditorUi.dp(ui, 2)
                : -(anchor.getHeight() + popupHeight + ArtemisEditorUi.dp(ui, 4));
        popup.showAsDropDown(anchor, xOffset, yOffset);
    }

    private static TextView menuItem(Context context, String text, int color, boolean enabled,
                                     int height, Runnable action) {
        TextView item = new TextView(context);
        item.setText(text); item.setTextColor(color); item.setTextSize(13f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ArtemisEditorUi.dp(context, 12), 0, ArtemisEditorUi.dp(context, 8), 0);
        item.setEnabled(enabled); item.setAlpha(enabled ? 1f : .55f);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
        item.setOnClickListener(v -> { if (enabled) action.run(); });
        return item;
    }

    private static void switchProfile(Context context, KeyBoardController controller, String id) {
        KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(context);
        if (current != null && current.id.equals(id)) return;
        if (controller != null) controller.switchKeyboardProfile(id);
        else {
            KeyboardProfilesManager.setActiveProfile(context, id);
            Toast.makeText(context, "Keyboard profile selected", Toast.LENGTH_SHORT).show();
        }
    }

    private interface NameCallback { void onName(String name); }
    private static void promptForName(Context context, String title, String hint, String initial,
                                      NameCallback callback) {
        EditText input = new EditText(context);
        input.setSingleLine(true); input.setHint(hint); input.setText(initial);
        input.setTextColor(Color.WHITE); input.setHintTextColor(0xFF8E8E93);
        input.setPadding(ArtemisEditorUi.dp(context, 14), input.getPaddingTop(),
                ArtemisEditorUi.dp(context, 14), input.getPaddingBottom());
        if (!initial.isEmpty()) input.setSelection(initial.length());
        AlertDialog dialog = ArtemisEditorUi.builder(context, title)
                .setView(input).setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(dialog, context, 420);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) { input.setError("Enter a profile name"); return; }
                callback.onName(value); dialog.dismiss();
            });
        });
        dialog.show();
    }
}
