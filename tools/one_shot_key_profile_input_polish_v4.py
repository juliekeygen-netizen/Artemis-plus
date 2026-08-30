from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 regex match, found {count}")
    return updated


# ---------------------------------------------------------------------------
# Add Keys dialog: one searchable field per ordered key row, first row fixed,
# removable extra rows, full-width Add row button, fixed-height scrolling dialog,
# and ranked semantic search.
# ---------------------------------------------------------------------------
combo_path = ROOT / "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManager.java"
combo = combo_path.read_text(encoding="utf-8")
combo = replace_once(combo, "import android.text.InputType;\n", "import android.text.Editable;\nimport android.text.InputType;\nimport android.text.TextWatcher;\n", "KeyComboManager text imports")
combo = replace_once(combo, "import android.widget.CheckBox;\n", "import android.widget.Button;\nimport android.widget.CheckBox;\n", "KeyComboManager Button import")
combo = replace_once(combo, "import android.widget.LinearLayout;\n", "import android.widget.LinearLayout;\nimport android.widget.ScrollView;\n", "KeyComboManager ScrollView import")
combo = replace_once(combo, "import java.util.Collections;\n", "import java.util.Collections;\nimport java.util.Comparator;\n", "KeyComboManager Comparator import")

# Improve matching to rank prefix/semantic results rather than merely filtering.
old_search = '''    /** Pure semantic matching helper kept package-visible for regression tests. */
    static boolean keySearchMatches(String displayName, int keyCode, String query) {
        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        StringBuilder haystack = new StringBuilder();
        haystack.append(normalizeSearch(displayName)).append(' ')
                .append(normalizeSearch(KeyEvent.keyCodeToString(keyCode))).append(' ')
                .append(searchAliasesForKey(keyCode));

        for (String token : normalizedQuery.split("\\\\s+")) {
            if (!token.isEmpty() && haystack.indexOf(token) < 0) {
                return false;
            }
        }
        return true;
    }
'''
new_search = '''    /** Pure semantic matching helper kept package-visible for regression tests. */
    static boolean keySearchMatches(String displayName, int keyCode, String query) {
        return keySearchScore(displayName, keyCode, query) != Integer.MAX_VALUE;
    }

    /** Lower scores are shown first in the autocomplete list. */
    static int keySearchScore(String displayName, int keyCode, String query) {
        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return 0;
        }

        String display = normalizeSearch(displayName);
        String keyName = normalizeSearch(KeyEvent.keyCodeToString(keyCode));
        String aliases = normalizeSearch(searchAliasesForKey(keyCode));
        String haystack = display + " " + keyName + " " + aliases;

        for (String token : normalizedQuery.split("\\\\s+")) {
            if (!token.isEmpty() && !haystack.contains(token)) {
                return Integer.MAX_VALUE;
            }
        }

        if (display.equals(normalizedQuery)) {
            return 0;
        }
        if (display.startsWith(normalizedQuery) || aliases.startsWith(normalizedQuery)) {
            return 1;
        }
        if (startsWithWord(display, normalizedQuery) || startsWithWord(aliases, normalizedQuery)) {
            return 2;
        }
        if (keyName.startsWith(normalizedQuery)) {
            return 3;
        }
        return 4;
    }

    private static boolean startsWithWord(String haystack, String query) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String word : haystack.split("\\\\s+")) {
            if (word.startsWith(query)) {
                return true;
            }
        }
        return false;
    }
'''
combo = replace_once(combo, old_search, new_search, "KeyComboManager semantic search")

new_dialog = r'''    private static void showDefinitionDialog(KeyBoardController controller,
                                             Context context,
                                             Definition existing) {
        final List<KeyOption> availableKeys;
        try {
            availableKeys = loadAvailableKeys(context);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to load key list: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Context dialogContext = new ContextThemeWrapper(context, R.style.ArtemisEditorDialogTheme);
        float density = context.getResources().getDisplayMetrics().density;
        int padding = Math.max(12, Math.round(16 * density));

        ScrollView scrollView = new ScrollView(dialogContext);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);

        LinearLayout content = new LinearLayout(dialogContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView intro = label(dialogContext,
                "Add one normal key, or build an ordered chord. Modifiers are held while the key rows are pressed from top to bottom.",
                14f, 0xFFBDBDBD);
        intro.setPadding(0, 0, 0, Math.round(8 * density));
        content.addView(intro);

        TextView nameLabel = label(dialogContext, "Display name", 13f, Color.WHITE);
        content.addView(nameLabel);

        EditText nameInput = new EditText(dialogContext);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setHint("Example: Back");
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(0xFF8E8E93);
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.length());
        }
        content.addView(nameInput);

        TextView modifiersLabel = label(dialogContext, "Hold modifiers (optional)", 13f, Color.WHITE);
        modifiersLabel.setPadding(0, Math.round(10 * density), 0, 0);
        content.addView(modifiersLabel);

        LinearLayout modifierRow = new LinearLayout(dialogContext);
        modifierRow.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox ctrl = modifierCheckBox(dialogContext, "Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, existing);
        CheckBox alt = modifierCheckBox(dialogContext, "Alt", KeyEvent.KEYCODE_ALT_LEFT, existing);
        CheckBox shift = modifierCheckBox(dialogContext, "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, existing);
        CheckBox meta = modifierCheckBox(dialogContext, "Win", KeyEvent.KEYCODE_META_LEFT, existing);
        modifierRow.addView(ctrl);
        modifierRow.addView(alt);
        modifierRow.addView(shift);
        modifierRow.addView(meta);
        content.addView(modifierRow);

        TextView keysLabel = label(dialogContext, "Keys (pressed top to bottom)", 13f, Color.WHITE);
        keysLabel.setPadding(0, Math.round(10 * density), 0, Math.round(4 * density));
        content.addView(keysLabel);

        List<KeyRowModel> keyRows = new ArrayList<>();
        if (existing != null && existing.keys.length > 0) {
            for (int code : existing.keys) {
                KeyOption option = findOptionByCode(availableKeys, code);
                keyRows.add(new KeyRowModel(option != null ? option : new KeyOption("Key " + code, code)));
            }
        }
        if (keyRows.isEmpty()) {
            keyRows.add(new KeyRowModel(null));
        }

        LinearLayout keyRowsContainer = new LinearLayout(dialogContext);
        keyRowsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(keyRowsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button addRow = new Button(dialogContext);
        addRow.setText("+  Add row");
        addRow.setAllCaps(false);
        addRow.setTextColor(Color.WHITE);
        GradientDrawable addRowBackground = new GradientDrawable();
        addRowBackground.setColor(0xFF303036);
        addRowBackground.setCornerRadius(10 * density);
        addRow.setBackground(addRowBackground);
        LinearLayout.LayoutParams addRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(46 * density));
        addRowParams.setMargins(0, Math.round(5 * density), 0, Math.round(3 * density));
        content.addView(addRow, addRowParams);

        TextView summary = label(dialogContext, "", 13f, 0xFFE0E0E0);
        summary.setPadding(0, Math.round(8 * density), 0, 0);
        content.addView(summary);

        Runnable updateSummary = () -> summary.setText(buildSummary(
                ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(), selectedOptions(keyRows)));

        Runnable[] rerenderRows = new Runnable[1];
        rerenderRows[0] = () -> renderKeyRows(
                dialogContext,
                keyRowsContainer,
                keyRows,
                availableKeys,
                nameInput,
                updateSummary,
                rerenderRows[0]);
        rerenderRows[0].run();

        addRow.setOnClickListener(v -> {
            keyRows.add(new KeyRowModel(null));
            rerenderRows[0].run();
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });

        ctrl.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        alt.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        shift.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        meta.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        updateSummary.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext)
                .setTitle(existing == null ? "Add Keys" : "Edit Key")
                .setView(scrollView)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton(android.R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton("Delete", null);
        }
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                int maxWidth = Math.round(760 * density);
                int maxHeight = Math.round(650 * density);
                int width = Math.min(maxWidth,
                        Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.90f));
                int height = Math.min(maxHeight,
                        Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.84f));
                dialog.getWindow().setLayout(Math.max(Math.round(340 * density), width),
                        Math.max(Math.round(300 * density), height));
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    nameInput.setError("Enter a display name");
                    return;
                }

                for (KeyRowModel row : keyRows) {
                    if (row.selected == null) {
                        if (row.field != null) {
                            row.field.setError("Choose a key from the list");
                            row.field.requestFocus();
                            row.field.showDropDown();
                        }
                        return;
                    }
                }

                List<Integer> modifiers = new ArrayList<>(4);
                if (ctrl.isChecked()) modifiers.add(KeyEvent.KEYCODE_CTRL_LEFT);
                if (alt.isChecked()) modifiers.add(KeyEvent.KEYCODE_ALT_LEFT);
                if (shift.isChecked()) modifiers.add(KeyEvent.KEYCODE_SHIFT_LEFT);
                if (meta.isChecked()) modifiers.add(KeyEvent.KEYCODE_META_LEFT);

                List<Integer> regular = new ArrayList<>(keyRows.size());
                for (KeyRowModel row : keyRows) {
                    regular.add(row.selected.code);
                }

                Definition updated = new Definition(
                        existing == null ? newId() : existing.id,
                        name,
                        toIntArray(modifiers),
                        toIntArray(regular));
                saveOrReplaceDefinition(context, updated);

                KeyComboButton button = findButton(controller, updated.id);
                if (button == null) {
                    int size = controller.getDefaultKeyButtonSize();
                    int width = KeyBoardDigitalButton.minimumWidthForText(context, name, size);
                    Point position = controller.findGroupedSpawnPosition(width, size);
                    button = new KeyComboButton(controller, context, updated);
                    controller.addElement(button, position.x, position.y, width, size);
                } else {
                    button.updateDefinition(updated);
                    button.hidden = false;
                    button.setVisibility(View.VISIBLE);
                    controller.ensureTextButtonWidth(button, name);
                }

                KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                controller.vibrate(KeyEvent.ACTION_DOWN);
                dialog.dismiss();
            });

            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xFFFF6B6B);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                        new AlertDialog.Builder(dialogContext)
                                .setTitle("Delete Key?")
                                .setMessage("Delete “" + existing.name + "” from this keyboard profile?")
                                .setPositiveButton("Delete", (confirm, which) -> {
                                    deleteDefinition(context, existing.id);
                                    KeyComboButton button = findButton(controller, existing.id);
                                    if (button != null) {
                                        controller.removeElement(button);
                                    }
                                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                                    dialog.dismiss();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show());
            }
        });
        dialog.show();
    }

'''
combo = regex_once(
    combo,
    r'    private static void showDefinitionDialog\(KeyBoardController controller,.*?\n    private static String buildSummary',
    new_dialog + '    private static String buildSummary',
    "KeyComboManager dialog replacement")

new_rows = r'''    private static final class KeyRowModel {
        KeyOption selected;
        AutoCompleteTextView field;

        KeyRowModel(KeyOption selected) {
            this.selected = selected;
        }
    }

    private static List<KeyOption> selectedOptions(List<KeyRowModel> rows) {
        List<KeyOption> selected = new ArrayList<>();
        for (KeyRowModel row : rows) {
            if (row.selected != null) {
                selected.add(row.selected);
            }
        }
        return selected;
    }

    private static void renderKeyRows(Context context,
                                      LinearLayout container,
                                      List<KeyRowModel> rows,
                                      List<KeyOption> availableKeys,
                                      EditText nameInput,
                                      Runnable onChanged,
                                      Runnable rerender) {
        container.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;

        for (int i = 0; i < rows.size(); i++) {
            final int rowIndex = i;
            KeyRowModel model = rows.get(i);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.round(50 * density));
            rowParams.setMargins(0, Math.round(3 * density), 0, Math.round(3 * density));
            container.addView(row, rowParams);

            AutoCompleteTextView field = new AutoCompleteTextView(context);
            model.field = field;
            field.setHint(rowIndex == 0 ? "Choose a key…" : "Choose another key…");
            field.setSingleLine(true);
            field.setThreshold(0);
            field.setTextColor(Color.WHITE);
            field.setHintTextColor(0xFF8E8E93);
            field.setDropDownHeight(Math.round(300 * density));
            field.setPadding(Math.round(14 * density), 0, Math.round(12 * density), 0);

            GradientDrawable fieldBackground = new GradientDrawable();
            fieldBackground.setColor(0xFF2B2B2F);
            fieldBackground.setCornerRadius(10 * density);
            fieldBackground.setStroke(Math.max(1, Math.round(density)), 0xFF505057);
            field.setBackground(fieldBackground);
            field.setAdapter(new KeySearchAdapter(context, availableKeys));

            if (model.selected != null) {
                field.setText(model.selected.selectionLabel(), false);
            }

            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (model.selected == null) {
                        return;
                    }
                    String value = editable == null ? "" : editable.toString().trim();
                    if (!value.equals(model.selected.selectionLabel()) &&
                            !value.equals(model.selected.name)) {
                        model.selected = null;
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    }
                }
            });

            field.setOnClickListener(v -> field.showDropDown());
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    field.showDropDown();
                }
            });
            field.setOnItemClickListener((parent, view, position, id) -> {
                KeyOption option = (KeyOption) parent.getItemAtPosition(position);
                model.selected = option;
                field.setText(option.selectionLabel(), false);
                field.setSelection(field.length());
                field.setError(null);
                if (rowIndex == 0 && nameInput.getText().toString().trim().isEmpty()) {
                    nameInput.setText(option.name);
                    nameInput.setSelection(nameInput.length());
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            });

            row.addView(field, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f));

            if (rowIndex > 0) {
                TextView remove = label(context, "×", 25f, 0xFFFF8A80);
                remove.setGravity(Gravity.CENTER);
                remove.setContentDescription("Remove key row");
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        Math.round(44 * density),
                        Math.round(44 * density));
                removeParams.setMarginStart(Math.round(5 * density));
                row.addView(remove, removeParams);
                remove.setOnClickListener(v -> {
                    rows.remove(rowIndex);
                    rerender.run();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
            }
        }
    }

    private static final class KeySearchAdapter'''
combo = regex_once(
    combo,
    r'    private static final class SelectedKeysAdapter.*?\n    private static final class KeySearchAdapter',
    new_rows,
    "KeyComboManager row adapter replacement")

# Rank search results by semantic relevance while preserving original keyboard.json order for ties.
old_filter_loop = '''                    ArrayList<KeyOption> matches = new ArrayList<>();
                    for (KeyOption option : all) {
                        if (option.matches(constraint)) {
                            matches.add(option);
                        }
                    }
                    FilterResults results = new FilterResults();
'''
new_filter_loop = '''                    ArrayList<KeyOption> matches = new ArrayList<>();
                    for (KeyOption option : all) {
                        if (option.matches(constraint)) {
                            matches.add(option);
                        }
                    }
                    matches.sort(Comparator.comparingInt(option ->
                            keySearchScore(option.name, option.code,
                                    constraint == null ? "" : constraint.toString())));
                    FilterResults results = new FilterResults();
'''
combo = replace_once(combo, old_filter_loop, new_filter_loop, "KeyComboManager ranked filter")
combo_path.write_text(combo, encoding="utf-8")


# ---------------------------------------------------------------------------
# Keyboard Profiles: add action in the real AlertDialog button bar, compact custom
# three-dot control and popup, no platform drawable/menu width surprises.
# ---------------------------------------------------------------------------
profiles_path = ROOT / "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesDialog.java"
profiles_path.write_text(r'''package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
        root.setPadding(padding, Math.round(4 * density), padding, Math.round(4 * density));
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
        list.setPadding(0, Math.round(2 * density), 0, Math.round(8 * density));
        int listHeight = Math.min(
                Math.round(350 * density),
                Math.max(Math.round(160 * density),
                        context.getResources().getDisplayMetrics().heightPixels / 2 - Math.round(44 * density)));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listHeight));

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
                .setNeutralButton("+ Add profile", null)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Button addButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            addButton.setTextColor(0xFF8BE9A8);
            addButton.setOnClickListener(v -> promptForName(
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
                            list.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                        }
                    }));
        });

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
                    Math.round(7 * density), Math.round(8 * density));
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

            TextView more = new TextView(uiContext);
            more.setText("⋮");
            more.setTextColor(0xFFE8E8EA);
            more.setTextSize(24f);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Profile options");
            more.setBackground(roundedBackground(0x332FFFFFFF, 10 * density, 0, 0));
            row.addView(more, new LinearLayout.LayoutParams(
                    Math.round(40 * density), Math.round(40 * density)));

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
            final TextView more;

            Holder(View itemView, TextView name, TextView state, TextView more) {
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
        float density = uiContext.getResources().getDisplayMetrics().density;
        int popupWidth = Math.round(168 * density);

        LinearLayout content = new LinearLayout(uiContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Math.round(5 * density), 0, Math.round(5 * density));
        content.setBackground(roundedBackground(0xFF242428, 11 * density,
                Math.max(1, Math.round(density)), 0xFF3B3B40));

        PopupWindow popup = new PopupWindow(
                content,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(12 * density);

        content.addView(profileMenuItem(uiContext, "Rename", Color.WHITE, true, () -> {
            popup.dismiss();
            promptForName(uiContext, "Rename Profile", "Profile name", profile.name, name -> {
                KeyboardProfilesManager.renameProfile(appContext, profile.id, name);
                rebuild.run();
            });
        }));
        content.addView(profileMenuItem(uiContext, "Duplicate", Color.WHITE, true, () -> {
            popup.dismiss();
            KeyboardProfilesManager.Profile duplicate =
                    KeyboardProfilesManager.duplicateProfile(appContext, profile.id);
            if (duplicate != null) {
                rebuild.run();
            }
        }));
        content.addView(profileMenuItem(uiContext, "Delete",
                profileCount > 1 ? 0xFFFF7777 : 0xFF77777D,
                profileCount > 1,
                () -> {
                    popup.dismiss();
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
                }));

        int xOffset = -popupWidth + anchor.getWidth();
        popup.showAsDropDown(anchor, xOffset, -Math.round(4 * density));
    }

    private static TextView profileMenuItem(Context context,
                                            String label,
                                            int color,
                                            boolean enabled,
                                            Runnable action) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView item = new TextView(context);
        item.setText(label);
        item.setTextColor(color);
        item.setTextSize(16f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Math.round(16 * density), 0, Math.round(12 * density), 0);
        item.setEnabled(enabled);
        item.setAlpha(enabled ? 1f : 0.55f);
        item.setOnClickListener(v -> {
            if (enabled) {
                action.run();
            }
        });
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(46 * density)));
        return item;
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
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# Touch Sensitivity action: open a real in-stream settings dialog and persist/live
# apply the same settings as the Settings screen instead of silently toggling a flag.
# ---------------------------------------------------------------------------
game_path = ROOT / "app/src/main/java/com/limelight/Game.java"
game = game_path.read_text(encoding="utf-8")
game = replace_once(game, "import android.widget.FrameLayout;\n", "import android.widget.FrameLayout;\nimport android.widget.CheckBox;\nimport android.widget.LinearLayout;\nimport android.widget.SeekBar;\n", "Game sensitivity imports")
old_switch = '''    //切换触控灵敏度开关
    public void switchTouchSensitivity(){
        prefConfig.enableTouchSensitivity = !prefConfig.enableTouchSensitivity;
    }
'''
new_switch = '''    //切换触控灵敏度开关
    public void switchTouchSensitivity(){
        prefConfig.enableTouchSensitivity = !prefConfig.enableTouchSensitivity;
    }

    /**
     * In-stream editor for the same touch-sensitivity settings exposed in Settings.
     * The Artemis action opens this dialog instead of acting like a mystery toggle.
     */
    public void showTouchSensitivityDialog() {
        android.view.ContextThemeWrapper themedContext =
                new android.view.ContextThemeWrapper(this, R.style.ArtemisEditorDialogTheme);
        float density = getResources().getDisplayMetrics().density;
        int padding = Math.round(16 * density);

        LinearLayout root = new LinearLayout(themedContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, Math.round(6 * density), padding, 0);

        CheckBox enabled = new CheckBox(themedContext);
        enabled.setText("Enable touch sensitivity");
        enabled.setChecked(prefConfig.enableTouchSensitivity);
        root.addView(enabled);

        TextView horizontalLabel = new TextView(themedContext);
        horizontalLabel.setTextColor(android.graphics.Color.WHITE);
        horizontalLabel.setPadding(0, Math.round(8 * density), 0, 0);
        root.addView(horizontalLabel);
        SeekBar horizontal = new SeekBar(themedContext);
        horizontal.setMax(29); // 10% .. 300% in 10% steps
        horizontal.setProgress(Math.max(0, Math.min(29, prefConfig.touchSensitivityX / 10 - 1)));
        root.addView(horizontal);

        TextView verticalLabel = new TextView(themedContext);
        verticalLabel.setTextColor(android.graphics.Color.WHITE);
        verticalLabel.setPadding(0, Math.round(6 * density), 0, 0);
        root.addView(verticalLabel);
        SeekBar vertical = new SeekBar(themedContext);
        vertical.setMax(29); // 10% .. 300% in 10% steps
        vertical.setProgress(Math.max(0, Math.min(29, prefConfig.touchSensitivityY / 10 - 1)));
        root.addView(vertical);

        CheckBox global = new CheckBox(themedContext);
        global.setText("Apply sensitivity globally");
        global.setChecked(prefConfig.touchSensitivityGlobal);
        root.addView(global);

        CheckBox rotate = new CheckBox(themedContext);
        rotate.setText("Rotate sensitivity axes automatically");
        rotate.setChecked(prefConfig.touchSensitivityRotationAuto);
        root.addView(rotate);

        Runnable refreshLabels = () -> {
            horizontalLabel.setText("Horizontal sensitivity: " + ((horizontal.getProgress() + 1) * 10) + "%");
            verticalLabel.setText("Vertical sensitivity: " + ((vertical.getProgress() + 1) * 10) + "%");
        };
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refreshLabels.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        horizontal.setOnSeekBarChangeListener(listener);
        vertical.setOnSeekBarChangeListener(listener);
        refreshLabels.run();

        Runnable refreshEnabledState = () -> {
            boolean on = enabled.isChecked();
            horizontal.setEnabled(on);
            vertical.setEnabled(on);
            global.setEnabled(on);
            rotate.setEnabled(on);
            horizontalLabel.setAlpha(on ? 1f : 0.5f);
            verticalLabel.setAlpha(on ? 1f : 0.5f);
        };
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> refreshEnabledState.run());
        refreshEnabledState.run();

        new AlertDialog.Builder(themedContext)
                .setTitle("Touch Sensitivity")
                .setView(root)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int sensitivityX = (horizontal.getProgress() + 1) * 10;
                    int sensitivityY = (vertical.getProgress() + 1) * 10;
                    prefConfig.enableTouchSensitivity = enabled.isChecked();
                    prefConfig.touchSensitivityX = sensitivityX;
                    prefConfig.touchSensitivityY = sensitivityY;
                    prefConfig.touchSensitivityGlobal = global.isChecked();
                    prefConfig.touchSensitivityRotationAuto = rotate.isChecked();

                    PreferenceManager.getDefaultSharedPreferences(this)
                            .edit()
                            .putBoolean("checkbox_enable_touch_sensitivity", enabled.isChecked())
                            .putInt("seekbar_touch_sensitivity_opacity_x", sensitivityX)
                            .putInt("seekbar_touch_sensitivity_opacity_y", sensitivityY)
                            .putBoolean("checkbox_enable_global_touch_sensitivity", global.isChecked())
                            .putBoolean("checkbox_enable_touch_sensitivity_rotation_auto", rotate.isChecked())
                            .apply();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
'''
game = replace_once(game, old_switch, new_switch, "Game touch sensitivity dialog")
game_path.write_text(game, encoding="utf-8")

action_path = ROOT / "app/src/main/java/com/limelight/ArtemisAction.java"
action = action_path.read_text(encoding="utf-8")
action = replace_once(
    action,
    '''            case TOUCH_SENSITIVITY:\n                game.switchTouchSensitivity();\n                Toast.makeText(game, "Touch sensitivity toggled", Toast.LENGTH_SHORT).show();\n                return true;\n''',
    '''            case TOUCH_SENSITIVITY:\n                game.showTouchSensitivityDialog();\n                return true;\n''',
    "ArtemisAction touch sensitivity")
action_path.write_text(action, encoding="utf-8")

state_path = ROOT / "app/src/main/java/com/limelight/ArtemisActionStateReader.java"
state = state_path.read_text(encoding="utf-8")
state = replace_once(
    state,
    '''                case TOUCH_SENSITIVITY:\n                    return prefConfig == null ? null : prefConfig.enableTouchSensitivity;\n\n''',
    '',
    "ArtemisActionStateReader touch sensitivity toggle state")
state_path.write_text(state, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage for ranked semantic search.
# ---------------------------------------------------------------------------
test_path = ROOT / "app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManagerTest.java"
test = test_path.read_text(encoding="utf-8")
needle = '''    @Test\n    public void semanticSearchFindsBackspaceAndCommonAliases() {\n        assertTrue(KeyComboManager.keySearchMatches("⌫", KeyEvent.KEYCODE_DEL, "backspace"));\n        assertTrue(KeyComboManager.keySearchMatches("⌫", KeyEvent.KEYCODE_DEL, "bksp"));\n        assertTrue(KeyComboManager.keySearchMatches("Esc", KeyEvent.KEYCODE_ESCAPE, "escape"));\n        assertTrue(KeyComboManager.keySearchMatches("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, "page down"));\n    }\n\n'''
addition = needle + '''    @Test\n    public void semanticSearchRanksPrefixAndAliasesAheadOfLooseMatches() {\n        assertTrue(KeyComboManager.keySearchScore("F1", KeyEvent.KEYCODE_F1, "f")\n                < KeyComboManager.keySearchScore("Page Up", KeyEvent.KEYCODE_PAGE_UP, "f"));\n        assertTrue(KeyComboManager.keySearchScore("←", KeyEvent.KEYCODE_DPAD_LEFT, "left")\n                < KeyComboManager.keySearchScore("Bracket", KeyEvent.KEYCODE_LEFT_BRACKET, "left"));\n        assertEquals(Integer.MAX_VALUE,\n                KeyComboManager.keySearchScore("←", KeyEvent.KEYCODE_DPAD_LEFT, "backspace"));\n    }\n\n'''
test = replace_once(test, needle, addition, "KeyComboManager ranked-search test")
test_path.write_text(test, encoding="utf-8")


# ---------------------------------------------------------------------------
# Document the Apollo permission trap that presents exactly as a dead client after
# reinstall/re-pair: video works, all input silently does nothing.
# ---------------------------------------------------------------------------
readme_path = ROOT / "README.md"
readme = readme_path.read_text(encoding="utf-8")
marker = "## Artemis Plus troubleshooting\n"
block = '''\n## Artemis Plus troubleshooting\n\n### Stream works but touch / mouse / keyboard / controller input does nothing\n\nApollo permissions are per paired client. A newly paired client can have permission to view a\nstream while its input permissions remain disabled. In Apollo's **PIN / paired clients** page,\ngrant the Artemis client **Mouse Input**, **Keyboard Input**, **Touch Input**, and **Controller\nInput** (plus **Launch Apps** when needed). This is especially easy to hit after uninstalling the\nAndroid app because the reinstall pairs as a new client.\n'''
if marker not in readme:
    readme = readme.rstrip() + "\n" + block
readme_path.write_text(readme, encoding="utf-8")

print("Applied Artemis Plus key/profile/input polish v4 patch")
