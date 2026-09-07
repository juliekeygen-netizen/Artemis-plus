package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.ui.ArtemisEditorUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creation, editing and per-profile persistence for user-defined key/chord buttons. */
final class KeyComboManager {
    private static final String PREFERENCES = "ArtemisPlusKeyCombos";
    private static final String DEFINITIONS_PREFIX = "definitions_";

    private KeyComboManager() {
    }

    static final class Definition {
        final String id;
        final String name;
        final int[] modifiers;
        final int[] keys;
        final boolean longPressToggle;

        Definition(String id, String name, int[] modifiers, int[] keys) {
            this(id, name, modifiers, keys, false);
        }

        Definition(String id, String name, int[] modifiers, int[] keys, boolean longPressToggle) {
            this.id = id;
            this.name = name;
            this.modifiers = modifiers == null ? new int[0] : modifiers.clone();
            this.keys = keys == null ? new int[0] : keys.clone();
            this.longPressToggle = longPressToggle;
        }

        String elementId() {
            return "artemis_combo_" + id;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", name);

            JSONArray modifierArray = new JSONArray();
            for (int modifier : modifiers) {
                modifierArray.put(modifier);
            }
            object.put("modifiers", modifierArray);

            JSONArray keyArray = new JSONArray();
            for (int key : keys) {
                keyArray.put(key);
            }
            object.put("keys", keyArray);
            object.put("longPressToggle", longPressToggle);
            return object;
        }

        static Definition fromJson(JSONObject object) throws JSONException {
            JSONArray modifierArray = object.optJSONArray("modifiers");
            JSONArray keyArray = object.optJSONArray("keys");
            int[] modifiers = new int[modifierArray == null ? 0 : modifierArray.length()];
            int[] keys = new int[keyArray == null ? 0 : keyArray.length()];

            for (int i = 0; i < modifiers.length; i++) {
                modifiers[i] = modifierArray.getInt(i);
            }
            for (int i = 0; i < keys.length; i++) {
                keys[i] = keyArray.getInt(i);
            }

            return new Definition(
                    object.getString("id"),
                    object.optString("name", "Key"),
                    modifiers,
                    keys,
                    object.optBoolean("longPressToggle", false));
        }
    }

    private static final class KeyOption {
        final String name;
        final int code;

        KeyOption(String name, int code) {
            this.name = name;
            this.code = code;
        }

        String selectionLabel() {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return "←   Left Arrow";
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return "→   Right Arrow";
                case KeyEvent.KEYCODE_DPAD_UP:
                    return "↑   Up Arrow";
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return "↓   Down Arrow";
                case KeyEvent.KEYCODE_DEL:
                    return "⌫   Backspace";
                default:
                    return name;
            }
        }

        boolean matches(CharSequence query) {
            return keySearchMatches(name, code, query == null ? "" : query.toString());
        }
    }

    /** Pure semantic matching helper kept package-visible for regression tests. */
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

        for (String token : normalizedQuery.split("\\s+")) {
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
        for (String word : haystack.split("\\s+")) {
            if (word.startsWith(query)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String searchAliasesForKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "left left arrow arrow left ← cursor left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "right right arrow arrow right → cursor right";
            case KeyEvent.KEYCODE_DPAD_UP:
                return "up up arrow arrow up ↑ cursor up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "down down arrow arrow down ↓ cursor down";
            case KeyEvent.KEYCODE_DEL:
                return "backspace back space bksp erase delete backward ⌫";
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "delete del forward delete";
            case KeyEvent.KEYCODE_ENTER:
                return "enter return newline";
            case KeyEvent.KEYCODE_ESCAPE:
                return "escape esc";
            case KeyEvent.KEYCODE_SPACE:
                return "space spacebar space bar";
            case KeyEvent.KEYCODE_TAB:
                return "tab tabulator";
            case KeyEvent.KEYCODE_PAGE_UP:
                return "page up pageup pgup";
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return "page down pagedown pgdn pgdown";
            case KeyEvent.KEYCODE_MOVE_HOME:
                return "home beginning start";
            case KeyEvent.KEYCODE_MOVE_END:
                return "end ending";
            case KeyEvent.KEYCODE_INSERT:
                return "insert ins";
            case KeyEvent.KEYCODE_CAPS_LOCK:
                return "caps capslock caps lock";
            default:
                return "";
        }
    }

    static void showCreateDialog(KeyBoardController controller, Context context) {
        showDefinitionDialog(controller, context, null);
    }

    static void showEditDialog(KeyBoardController controller, Context context, KeyComboButton button) {
        if (button == null) {
            return;
        }
        showDefinitionDialog(
                controller,
                context,
                new Definition(
                        button.getComboId(),
                        button.getDisplayName(),
                        button.getModifierKeys(),
                        button.getRegularKeys(),
                        button.isLongPressToggleEnabled()));
    }

    static void restore(KeyBoardController controller, Context context) {
        for (Definition definition : loadDefinitions(context)) {
            if (findButton(controller, definition.id) != null) {
                continue;
            }

            int size = controller.getDefaultKeyButtonSize();
            int width = KeyBoardDigitalButton.minimumWidthForText(context, definition.name, size);
            Point position = controller.findGroupedSpawnPosition(width, size);
            KeyComboButton button = new KeyComboButton(controller, context, definition);
            controller.addElement(button, position.x, position.y, width, size);
            controller.loadSavedElementConfiguration(button);
            button.post(() -> controller.ensureTextButtonWidth(button, definition.name));
        }
    }

    private static void showDefinitionDialog(KeyBoardController controller,
                                             Context context,
                                             Definition existing) {
        final List<KeyOption> availableKeys;
        try {
            availableKeys = loadAvailableKeys(context);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.artemis_key_load_error,
                    String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            return;
        }

        Context dialogContext = ArtemisEditorUi.context(context);
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

        TextView intro = label(dialogContext, dialogContext.getString(R.string.artemis_key_intro),
                12.5f, ArtemisEditorUi.TEXT_SECONDARY);
        intro.setPadding(0, 0, 0, Math.round(8 * density));
        content.addView(intro);

        TextView nameLabel = label(dialogContext,
                dialogContext.getString(R.string.artemis_key_display_name), 13f, Color.WHITE);
        content.addView(nameLabel);

        EditText nameInput = new EditText(dialogContext);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setHint(R.string.artemis_key_display_name_hint);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(0xFF8E8E93);
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.length());
        }
        content.addView(nameInput);

        TextView modifiersLabel = label(dialogContext,
                dialogContext.getString(R.string.artemis_key_hold_modifiers), 13f, Color.WHITE);
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

        CheckBox longPressToggle = new CheckBox(dialogContext);
        longPressToggle.setText(R.string.artemis_key_long_press_toggle);
        longPressToggle.setTextColor(Color.WHITE);
        longPressToggle.setChecked(existing != null && existing.longPressToggle);
        content.addView(longPressToggle);
        TextView longPressSummary = label(dialogContext,
                dialogContext.getString(R.string.artemis_key_long_press_toggle_summary),
                11.5f, ArtemisEditorUi.TEXT_SECONDARY);
        longPressSummary.setPadding(Math.round(4 * density), 0, 0, Math.round(4 * density));
        content.addView(longPressSummary);

        TextView keysLabel = label(dialogContext,
                dialogContext.getString(R.string.artemis_key_press_order_label), 13f, Color.WHITE);
        keysLabel.setPadding(0, Math.round(10 * density), 0, Math.round(4 * density));
        content.addView(keysLabel);

        List<KeyRowModel> keyRows = new ArrayList<>();
        if (existing != null && existing.keys.length > 0) {
            for (int code : existing.keys) {
                KeyOption option = findOptionByCode(availableKeys, code);
                keyRows.add(new KeyRowModel(option != null ? option : new KeyOption(
                        dialogContext.getString(R.string.artemis_key_unknown, code), code)));
            }
        }
        // New combos start with one normal-key row for convenience. Existing modifier-only
        // combos intentionally reopen with no normal-key row so editing them does not invent a key.
        if (keyRows.isEmpty() && existing == null) {
            keyRows.add(new KeyRowModel(null));
        }

        LinearLayout keyRowsContainer = new LinearLayout(dialogContext);
        keyRowsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(keyRowsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button addRow = new Button(dialogContext);
        addRow.setText(R.string.artemis_key_add_row);
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
        TextView pressOrder = label(dialogContext, "", 11.5f, ArtemisEditorUi.TEXT_SECONDARY);
        pressOrder.setPadding(0, Math.round(2 * density), 0, 0);
        content.addView(pressOrder);

        Runnable updateSummary = () -> {
            List<KeyOption> selected = selectedOptions(keyRows);
            summary.setText(buildSummary(
                    ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(), selected));
            String order = buildPressOrder(selected);
            pressOrder.setText(order);
            pressOrder.setVisibility(order.isEmpty() ? View.GONE : View.VISIBLE);
        };

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

        AlertDialog.Builder builder = ArtemisEditorUi.builder(dialogContext,
                        dialogContext.getString(existing == null ? R.string.keyboard_add_keys
                                : R.string.artemis_key_edit_title))
                .setView(scrollView)
                .setPositiveButton(existing == null ? R.string.keyboard_add
                        : R.string.artemis_key_save, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.artemis_delete, null);
        }
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(dialog, context, 520, 620, true);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();

                List<Integer> modifiers = new ArrayList<>(4);
                if (ctrl.isChecked()) modifiers.add(KeyEvent.KEYCODE_CTRL_LEFT);
                if (alt.isChecked()) modifiers.add(KeyEvent.KEYCODE_ALT_LEFT);
                if (shift.isChecked()) modifiers.add(KeyEvent.KEYCODE_SHIFT_LEFT);
                if (meta.isChecked()) modifiers.add(KeyEvent.KEYCODE_META_LEFT);

                List<Integer> regular = new ArrayList<>(keyRows.size());
                for (KeyRowModel row : keyRows) {
                    if (row.selected != null) {
                        regular.add(row.selected.code);
                        continue;
                    }

                    // Untouched regular-key rows are optional. This lets a user simply check Win,
                    // Ctrl, Alt, or Shift and save a modifier-only button without first deleting
                    // the convenience row. Non-empty text that is not a valid selected key still
                    // blocks saving so typos cannot silently disappear.
                    String typed = row.field == null ? "" : row.field.getText().toString().trim();
                    if (!typed.isEmpty()) {
                        row.field.setError(context.getString(R.string.artemis_key_selection_required));
                        row.field.performClick();
                        return;
                    }
                }

                // Do not persist an inert button. If all rows were removed, recreate the normal-key
                // convenience row so the user has an obvious place to make a valid selection.
                if (!hasAnySelection(modifiers.size(), regular.size())) {
                    if (keyRows.isEmpty()) {
                        keyRows.add(new KeyRowModel(null));
                        rerenderRows[0].run();
                    }
                    KeyRowModel first = keyRows.get(0);
                    if (first.field != null) {
                        first.field.setError(context.getString(R.string.artemis_key_selection_required));
                        first.field.performClick();
                    }
                    return;
                }

                // A selected regular key fills this automatically. Modifier-only buttons should
                // be equally frictionless, including when the convenience key row remains empty.
                if (name.isEmpty()) {
                    String generated = buildSummary(
                            ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(),
                            selectedOptions(keyRows));
                    name = generated.startsWith("Sends: ")
                            ? generated.substring("Sends: ".length())
                            : generated;
                    if (name.isEmpty() || "nothing selected yet".equals(name)) {
                        nameInput.setError(context.getString(R.string.artemis_key_display_name_required));
                        return;
                    }
                    nameInput.setText(name);
                }

                Definition updated = new Definition(
                        existing == null ? newId() : existing.id,
                        name,
                        toIntArray(modifiers),
                        toIntArray(regular),
                        longPressToggle.isChecked());
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
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ArtemisEditorUi.DANGER);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    AlertDialog confirmation = ArtemisEditorUi.builder(dialogContext,
                                    dialogContext.getString(R.string.artemis_key_delete_title))
                            .setMessage(dialogContext.getString(R.string.artemis_key_delete_message,
                                    existing.name))
                            .setPositiveButton(R.string.artemis_delete, (confirm, which) -> {
                                    deleteDefinition(context, existing.id);
                                    KeyComboButton button = findButton(controller, existing.id);
                                    if (button != null) {
                                        controller.removeElement(button);
                                    }
                                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                                    dialog.dismiss();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .create();
                    confirmation.setOnShowListener(shown ->
                            ArtemisEditorUi.styleDialog(confirmation, context, 420));
                    confirmation.show();
                });
            }
        });
        dialog.show();
    }

    /** Save-policy helper kept package-visible for focused regression tests. */
    static boolean hasAnySelection(int modifierCount, int regularKeyCount) {
        return modifierCount > 0 || regularKeyCount > 0;
    }

    static String buildSummary(boolean ctrl,
                               boolean alt,
                               boolean shift,
                               boolean meta,
                               List<KeyOption> keys) {
        StringBuilder result = new StringBuilder("Sends: ");
        boolean wrote = false;
        if (ctrl) { result.append("Ctrl"); wrote = true; }
        if (alt) { if (wrote) result.append(" + "); result.append("Alt"); wrote = true; }
        if (shift) { if (wrote) result.append(" + "); result.append("Shift"); wrote = true; }
        if (meta) { if (wrote) result.append(" + "); result.append("Win"); wrote = true; }
        for (KeyOption key : keys) {
            if (wrote) result.append(" + ");
            result.append(key.selectionLabel().replace("   ", " "));
            wrote = true;
        }
        if (!wrote) result.append("nothing selected yet");
        return result.toString();
    }

    static String buildPressOrder(List<KeyOption> keys) {
        if (keys.size() <= 1) return "";
        StringBuilder result = new StringBuilder("Press order: ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) result.append("  →  ");
            result.append(keys.get(i).selectionLabel().replace("   ", " "));
        }
        return result.toString();
    }

    private static TextView label(Context context, String text, float size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static final class KeyRowModel {
        KeyOption selected;
        EditText field;

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

            EditText field = new EditText(context);
            model.field = field;
            field.setHint(rowIndex == 0 ? R.string.artemis_key_choose_first
                    : R.string.artemis_key_choose_another);
            field.setSingleLine(true);
            ArtemisEditorUi.suppressTextActionMenu(field);
            field.setTextColor(Color.WHITE);
            field.setHintTextColor(0xFF8E8E93);
            field.setPadding(Math.round(14 * density), 0, Math.round(12 * density), 0);
            field.setFocusable(false);
            field.setCursorVisible(false);

            GradientDrawable fieldBackground = new GradientDrawable();
            fieldBackground.setColor(0xFF2B2B2F);
            fieldBackground.setCornerRadius(10 * density);
            fieldBackground.setStroke(Math.max(1, Math.round(density)), 0xFF505057);
            field.setBackground(fieldBackground);
            if (model.selected != null) {
                field.setText(model.selected.selectionLabel());
            }

            field.setOnClickListener(v -> showKeyPickerDialog(context, availableKeys, option -> {
                model.selected = option;
                field.setText(option.selectionLabel());
                field.setError(null);
                if (rowIndex == 0 && nameInput.getText().toString().trim().isEmpty()) {
                    nameInput.setText(option.name);
                    nameInput.setSelection(nameInput.length());
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            }));

            row.addView(field, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f));

            // Every regular-key row can be removed. With at least one modifier selected,
            // removing the final row creates a valid modifier-only chord. Save restores a blank
            // row if the user tries to leave both modifiers and regular keys empty.
            if (!rows.isEmpty()) {
                TextView remove = label(context, "×", 25f, 0xFFFF8A80);
                remove.setGravity(Gravity.CENTER);
                remove.setContentDescription(context.getString(
                        R.string.artemis_key_remove_row_description));
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

    private interface KeySelectionListener {
        void onSelected(KeyOption option);
    }

    private static void showKeyPickerDialog(Context context,
                                            List<KeyOption> availableKeys,
                                            KeySelectionListener listener) {
        Context dialogContext = ArtemisEditorUi.context(context);
        float density = dialogContext.getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(dialogContext);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * density);
        content.setPadding(padding, Math.round(8 * density), padding, Math.round(8 * density));

        EditText search = new EditText(dialogContext);
        search.setSingleLine(true);
        search.setHint(R.string.artemis_key_search_hint);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF8E8E93);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ArtemisEditorUi.suppressTextActionMenu(search);
        content.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(48 * density)));

        FrameLayout resultsFrame = new FrameLayout(dialogContext);
        ListView results = new ListView(dialogContext);
        results.setDividerHeight(0);
        KeySearchAdapter adapter = new KeySearchAdapter(dialogContext, availableKeys);
        results.setAdapter(adapter);
        resultsFrame.addView(results, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView empty = label(dialogContext,
                dialogContext.getString(R.string.artemis_key_no_matches),
                14f, ArtemisEditorUi.TEXT_SECONDARY);
        empty.setGravity(Gravity.CENTER);
        resultsFrame.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        results.setEmptyView(empty);
        content.addView(resultsFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog picker = new AlertDialog.Builder(dialogContext)
                .setView(content)
                .create();

        Runnable selectBestMatch = () -> {
            if (adapter.getCount() <= 0) return;
            KeyOption option = adapter.getItem(0);
            if (option != null) {
                listener.onSelected(option);
                picker.dismiss();
            }
        };
        results.setOnItemClickListener((parent, view, position, id) -> {
            KeyOption option = adapter.getItem(position);
            if (option != null) {
                listener.onSelected(option);
                picker.dismiss();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN)) {
                selectBestMatch.run();
                return true;
            }
            return false;
        });

        picker.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(picker, context, 520, 520, true);
            Window window = picker.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            search.requestFocus();
            search.post(() -> {
                InputMethodManager input = (InputMethodManager) context.getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (input != null) {
                    input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        });
        picker.show();
    }

    private static final class KeySearchAdapter extends ArrayAdapter<KeyOption> implements Filterable {
        private final List<KeyOption> all;
        private final List<KeyOption> filtered = new ArrayList<>();

        KeySearchAdapter(Context context, List<KeyOption> options) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            all = new ArrayList<>(options);
            filtered.addAll(options);
        }

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public KeyOption getItem(int position) {
            return filtered.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView text = convertView instanceof TextView ? (TextView) convertView : new TextView(getContext());
            float density = getContext().getResources().getDisplayMetrics().density;
            text.setText(getItem(position).selectionLabel());
            text.setTextColor(Color.WHITE);
            text.setTextSize(16f);
            text.setPadding(Math.round(14 * density), Math.round(11 * density),
                    Math.round(14 * density), Math.round(11 * density));
            text.setBackgroundColor(0xFF262629);
            return text;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    ArrayList<KeyOption> matches = new ArrayList<>();
                    for (KeyOption option : all) {
                        if (option.matches(constraint)) {
                            matches.add(option);
                        }
                    }
                    final String query = constraint == null ? "" : constraint.toString();
                    Collections.sort(matches, new Comparator<KeyOption>() {
                        @Override
                        public int compare(KeyOption first, KeyOption second) {
                            return Integer.compare(
                                    keySearchScore(first.name, first.code, query),
                                    keySearchScore(second.name, second.code, query));
                        }
                    });
                    FilterResults results = new FilterResults();
                    results.values = matches;
                    results.count = matches.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filtered.clear();
                    if (results.values != null) {
                        filtered.addAll((List<KeyOption>) results.values);
                    }
                    notifyDataSetChanged();
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return resultValue instanceof KeyOption
                            ? ((KeyOption) resultValue).selectionLabel()
                            : super.convertResultToString(resultValue);
                }
            };
        }
    }

    private static CheckBox modifierCheckBox(Context context,
                                             String label,
                                             int keyCode,
                                             Definition existing) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(label);
        checkBox.setTextColor(Color.WHITE);
        if (existing != null) {
            for (int existingCode : existing.modifiers) {
                if (existingCode == keyCode) {
                    checkBox.setChecked(true);
                    break;
                }
            }
        }
        return checkBox;
    }

    private static List<KeyOption> loadAvailableKeys(Context context) throws Exception {
        InputStream input = context.getAssets().open("config/keyboard.json");
        byte[] bytes = new byte[input.available()];
        int read = input.read(bytes);
        input.close();
        if (read <= 0) {
            throw new IllegalStateException("keyboard.json is empty");
        }

        JSONObject root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
        JSONArray keys = root.getJSONObject("data").getJSONArray("keystroke");
        List<KeyOption> options = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();

        for (int i = 0; i < keys.length(); i++) {
            JSONObject object = keys.getJSONObject(i);
            int code = object.optInt("code", KeyEvent.KEYCODE_UNKNOWN);
            if (code == KeyEvent.KEYCODE_UNKNOWN ||
                    KeyBoardControllerConfigurationLoader.isModifierKey(code) ||
                    seen.contains(code)) {
                continue;
            }
            seen.add(code);
            options.add(new KeyOption(object.optString("name", "Key " + code), code));
        }
        return options;
    }

    private static KeyOption findOptionByCode(List<KeyOption> options, int code) {
        for (KeyOption option : options) {
            if (option.code == code) {
                return option;
            }
        }
        return null;
    }

    private static KeyComboButton findButton(KeyBoardController controller, String id) {
        String elementId = "artemis_combo_" + id;
        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            if (elementId.equals(element.elementId) && element instanceof KeyComboButton) {
                return (KeyComboButton) element;
            }
        }
        return null;
    }

    private static void saveOrReplaceDefinition(Context context, Definition updated) {
        List<Definition> definitions = loadDefinitions(context);
        boolean replaced = false;
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).id.equals(updated.id)) {
                definitions.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            definitions.add(updated);
        }
        saveDefinitions(context, definitions);
    }

    private static void deleteDefinition(Context context, String id) {
        List<Definition> definitions = loadDefinitions(context);
        for (int i = definitions.size() - 1; i >= 0; i--) {
            if (definitions.get(i).id.equals(id)) {
                definitions.remove(i);
            }
        }
        saveDefinitions(context, definitions);
    }

    private static List<Definition> loadDefinitions(Context context) {
        return loadDefinitionsForLayout(context, activeLayout(context));
    }

    static List<Definition> loadDefinitionsForLayout(Context context, String layout) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String serialized = SafePreferenceValues.getString(preferences, definitionsKey(layout), "[]");
        List<Definition> definitions = new ArrayList<>();
        JSONArray array;
        try {
            array = new JSONArray(serialized);
        } catch (JSONException ignored) {
            return definitions;
        }

        List<String> seenIds = new ArrayList<>();
        JSONArray repairedArray = new JSONArray();
        boolean corruptedEntry = false;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (!(value instanceof JSONObject)) {
                corruptedEntry = true;
                continue;
            }
            JSONObject object = (JSONObject) value;
            try {
                Definition definition = Definition.fromJson(object);
                if (definition.id.isEmpty() || definition.keys.length == 0 || seenIds.contains(definition.id)) {
                    corruptedEntry = true;
                    continue;
                }
                seenIds.add(definition.id);
                definitions.add(definition);
                // Preserve fields unknown to this build when cleaning only the damaged siblings.
                repairedArray.put(object);
            } catch (JSONException ignored) {
                corruptedEntry = true;
            }
        }

        if (corruptedEntry) {
            preferences.edit().putString(definitionsKey(layout), repairedArray.toString()).apply();
        }
        return definitions;
    }

    private static void saveDefinitions(Context context, List<Definition> definitions) {
        saveDefinitionsForLayout(context, activeLayout(context), definitions);
    }

    private static void saveDefinitionsForLayout(Context context,
                                                 String layout,
                                                 List<Definition> definitions) {
        JSONArray array = new JSONArray();
        for (Definition definition : definitions) {
            try {
                array.put(definition.toJson());
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(definitionsKey(layout), array.toString())
                .apply();
    }

    static JSONArray exportDefinitionsForLayout(Context context, String layout) {
        String serialized = SafePreferenceValues.getString(
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
                definitionsKey(layout),
                "[]");
        try {
            return new JSONArray(serialized);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    static void importDefinitionsForLayout(Context context, String layout, JSONArray definitions) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(definitionsKey(layout), definitions == null ? "[]" : definitions.toString())
                .apply();
    }

    static void copyDefinitionsForLayout(Context context, String fromLayout, String toLayout) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String value = SafePreferenceValues.getString(
                preferences, definitionsKey(fromLayout), "[]");
        preferences.edit().putString(definitionsKey(toLayout), value).apply();
    }

    static void deleteDefinitionsForLayout(Context context, String layout) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(definitionsKey(layout))
                .apply();
    }

    private static String activeLayout(Context context) {
        KeyboardProfilesManager.ensureInitialized(context);
        return SafePreferenceValues.getString(
                PreferenceManager.getDefaultSharedPreferences(context),
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
    }

    private static String definitionsKey(String layout) {
        return DEFINITIONS_PREFIX + layout;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int[] toIntArray(Iterable<Integer> values) {
        List<Integer> copy = new ArrayList<>();
        for (Integer value : values) {
            copy.add(value);
        }
        int[] result = new int[copy.size()];
        for (int i = 0; i < copy.size(); i++) {
            result[i] = copy.get(i);
        }
        return result;
    }
}
