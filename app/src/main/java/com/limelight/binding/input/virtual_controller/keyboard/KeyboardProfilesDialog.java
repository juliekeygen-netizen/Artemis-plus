package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/** Shared keyboard-profile editor used in-stream and from Settings. */
public final class KeyboardProfilesDialog {
    private KeyboardProfilesDialog() {
    }

    public static void show(Context context, KeyBoardController controller) {
        KeyboardProfilesManager.ensureInitialized(context);
        Context dialogContext = new ContextThemeWrapper(context, R.style.ArtemisEditorDialogTheme);
        float density = context.getResources().getDisplayMetrics().density;
        int padding = Math.max(12, Math.round(14 * density));

        LinearLayout root = new LinearLayout(dialogContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, Math.round(4 * density), padding, 0);
        root.setBackgroundColor(0xFF18181B);

        TextView helper = new TextView(dialogContext);
        helper.setText("Tap a profile to use it. Hold and drag a row to reorder.");
        helper.setTextColor(0xFFB5B5BA);
        helper.setTextSize(13f);
        helper.setPadding(0, Math.round(4 * density), 0, Math.round(8 * density));
        root.addView(helper);

        RecyclerView list = new RecyclerView(dialogContext);
        list.setLayoutManager(new LinearLayoutManager(dialogContext));
        list.setClipToPadding(false);
        list.setPadding(0, Math.round(2 * density), 0, Math.round(6 * density));
        int listHeight = Math.min(
                Math.round(390 * density),
                Math.max(Math.round(180 * density), context.getResources().getDisplayMetrics().heightPixels / 2));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listHeight));

        Button add = new Button(dialogContext);
        add.setText("+  Add profile");
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        add.setBackground(roundedBackground(0xFF303036, 11 * density, 0, 0));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(48 * density));
        addParams.setMargins(0, Math.round(5 * density), 0, Math.round(8 * density));
        root.addView(add, addParams);

        ProfileAdapter adapter = new ProfileAdapter(
                dialogContext,
                context,
                controller,
                new ArrayList<>(KeyboardProfilesManager.getProfiles(context)));
        list.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView,
                                  RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) {
                    return false;
                }
                return adapter.moveProfile(from, to);
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelper.attachToRecyclerView(list);

        AlertDialog dialog = new AlertDialog.Builder(dialogContext)
                .setTitle("Keyboard Profiles")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();

        add.setOnClickListener(v -> promptForName(
                dialogContext,
                "Add Profile",
                "Profile name",
                "",
                name -> {
                    KeyboardProfilesManager.Profile profile =
                            KeyboardProfilesManager.createProfile(context, name);
                    if (profile != null) {
                        switchProfile(context, controller, profile.id);
                        adapter.replaceProfiles(KeyboardProfilesManager.getProfiles(context));
                    }
                }));

        dialog.show();
    }

    private static final class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.Holder> {
        private final Context uiContext;
        private final Context appContext;
        private final KeyBoardController controller;
        private final List<KeyboardProfilesManager.Profile> profiles = new ArrayList<>();
        private String activeId;

        ProfileAdapter(Context uiContext,
                       Context appContext,
                       KeyBoardController controller,
                       List<KeyboardProfilesManager.Profile> profiles) {
            this.uiContext = uiContext;
            this.appContext = appContext;
            this.controller = controller;
            replaceProfiles(profiles);
        }

        void replaceProfiles(List<KeyboardProfilesManager.Profile> updated) {
            profiles.clear();
            profiles.addAll(updated);
            KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(appContext);
            activeId = active == null ? "" : active.id;
            notifyDataSetChanged();
        }

        boolean moveProfile(int from, int to) {
            if (from < 0 || to < 0 || from >= profiles.size() || to >= profiles.size() || from == to) {
                return false;
            }
            KeyboardProfilesManager.Profile moving = profiles.get(from);
            int direction = to > from ? 1 : -1;
            int steps = Math.abs(to - from);
            for (int i = 0; i < steps; i++) {
                if (!KeyboardProfilesManager.moveProfile(appContext, moving.id, direction)) {
                    replaceProfiles(KeyboardProfilesManager.getProfiles(appContext));
                    return false;
                }
            }
            profiles.remove(from);
            profiles.add(to, moving);
            notifyItemMoved(from, to);
            return true;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            float density = uiContext.getResources().getDisplayMetrics().density;

            CardView card = new CardView(uiContext);
            card.setCardBackgroundColor(0xFF27272B);
            card.setRadius(12 * density);
            card.setCardElevation(0);
            card.setUseCompatPadding(false);

            LinearLayout row = new LinearLayout(uiContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(8 * density), Math.round(8 * density),
                    Math.round(6 * density), Math.round(8 * density));
            card.addView(row, new CardView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView drag = new TextView(uiContext);
            drag.setText("≡");
            drag.setTextColor(0xFF94949B);
            drag.setTextSize(24f);
            drag.setGravity(Gravity.CENTER);
            drag.setContentDescription("Hold and drag profile to reorder");
            row.addView(drag, new LinearLayout.LayoutParams(
                    Math.round(42 * density), Math.round(48 * density)));

            LinearLayout labels = new LinearLayout(uiContext);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(labels, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(uiContext);
            name.setTextColor(Color.WHITE);
            name.setTextSize(17f);
            name.setMaxLines(1);
            labels.addView(name);

            TextView state = new TextView(uiContext);
            state.setTextSize(11f);
            state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            state.setPadding(0, Math.round(2 * density), 0, 0);
            labels.addView(state);

            ImageButton more = new ImageButton(uiContext);
            more.setImageResource(android.R.drawable.ic_menu_more);
            more.setColorFilter(Color.WHITE);
            more.setContentDescription("Profile options");
            more.setPadding(Math.round(10 * density), Math.round(10 * density),
                    Math.round(10 * density), Math.round(10 * density));
            more.setBackground(roundedBackground(0x332FFFFFFF, 22 * density, 0, 0));
            row.addView(more, new LinearLayout.LayoutParams(
                    Math.round(44 * density), Math.round(44 * density)));

            RecyclerView.LayoutParams outer = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            outer.setMargins(0, Math.round(4 * density), 0, Math.round(4 * density));
            card.setLayoutParams(outer);

            return new Holder(card, name, state, more);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            KeyboardProfilesManager.Profile profile = profiles.get(position);
            boolean active = profile.id.equals(activeId);
            holder.name.setText(profile.name);
            holder.name.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            holder.state.setText(active ? "ACTIVE" : "");
            holder.state.setTextColor(active ? 0xFF8BE9A8 : 0xFF8A8A90);
            ((CardView) holder.itemView).setCardBackgroundColor(active ? 0xFF30363A : 0xFF27272B);

            holder.itemView.setOnClickListener(v -> {
                switchProfile(appContext, controller, profile.id);
                KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(appContext);
                activeId = current == null ? "" : current.id;
                notifyDataSetChanged();
            });

            holder.more.setOnClickListener(v -> showProfileMenu(
                    uiContext,
                    appContext,
                    controller,
                    profile,
                    profiles.size(),
                    holder.more,
                    () -> replaceProfiles(KeyboardProfilesManager.getProfiles(appContext))));
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView state;
            final ImageButton more;

            Holder(View itemView, TextView name, TextView state, ImageButton more) {
                super(itemView);
                this.name = name;
                this.state = state;
                this.more = more;
            }
        }
    }

    private static void showProfileMenu(Context uiContext,
                                        Context appContext,
                                        KeyBoardController controller,
                                        KeyboardProfilesManager.Profile profile,
                                        int profileCount,
                                        View anchor,
                                        Runnable rebuild) {
        PopupMenu menu = new PopupMenu(uiContext, anchor);
        menu.getMenu().add("Rename");
        menu.getMenu().add("Duplicate");
        menu.getMenu().add("Delete").setEnabled(profileCount > 1);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Rename".equals(title)) {
                promptForName(uiContext, "Rename Profile", "Profile name", profile.name, name -> {
                    KeyboardProfilesManager.renameProfile(appContext, profile.id, name);
                    rebuild.run();
                });
            } else if ("Duplicate".equals(title)) {
                KeyboardProfilesManager.Profile duplicate =
                        KeyboardProfilesManager.duplicateProfile(appContext, profile.id);
                if (duplicate != null) {
                    rebuild.run();
                }
            } else if ("Delete".equals(title)) {
                new AlertDialog.Builder(uiContext)
                        .setTitle("Delete Profile?")
                        .setMessage("Delete “" + profile.name + "”? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            KeyboardProfilesManager.Profile before =
                                    KeyboardProfilesManager.getActiveProfile(appContext);
                            if (KeyboardProfilesManager.deleteProfile(appContext, profile.id)) {
                                if (controller != null && before != null && before.id.equals(profile.id)) {
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
        if (current != null && current.id.equals(profileId)) {
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
        float density = context.getResources().getDisplayMetrics().density;
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(initial);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF8E8E93);
        input.setPadding(Math.round(14 * density), input.getPaddingTop(),
                Math.round(14 * density), input.getPaddingBottom());
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

    private static GradientDrawable roundedBackground(int color,
                                                      float radius,
                                                      int strokeWidth,
                                                      int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }
}
