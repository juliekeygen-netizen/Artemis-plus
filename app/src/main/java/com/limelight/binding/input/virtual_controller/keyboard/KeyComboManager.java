package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

        Definition(String id, String name, int[] modifiers, int[] keys) {
            this.id = id;
            this.name = name;
            this.modifiers = modifiers == null ? new int[0] : modifiers.clone();
            this.keys = keys == null ? new int[0] : keys.clone();
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
                    keys);
        }
    }

    private static final class KeyOption {
        final String name;
        final int code;

        KeyOption(String name, int code) {
            this.name = name;
            this.code = code;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class KeyRowModel {
        KeyOption selected;

        KeyRowModel(KeyOption selected) {
            this.selected = selected;
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
                        button.getRegularKeys()));
    }

    static void restore(KeyBoardController controller, Context context) {
        for (Definition definition : loadDefinitions(context)) {
            if (findButton(controller, definition.id) != null) {
                continue;
            }

            int size = controller.getDefaultKeyButtonSize();
            Point position = controller.findGroupedSpawnPosition(size, size);
            KeyComboButton button = new KeyComboButton(controller, context, definition);
            controller.addElement(button, position.x, position.y, size, size);
            controller.loadSavedElementConfiguration(button);
        }
    }

    private static void showDefinitionDialog(KeyBoardController controller,
                                             Context context,
                                             Definition existing) {
        final List<KeyOption> availableKeys;
        try {
            availableKeys = loadAvailableKeys(context);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to load key list: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        int padding = Math.max(12,
                Math.round(16 * context.getResources().getDisplayMetrics().density));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        TextView nameLabel = new TextView(context);
        nameLabel.setText("Display name");
        content.addView(nameLabel);

        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setHint("Example: Back");
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.length());
        }
        content.addView(nameInput);

        TextView modifiersLabel = new TextView(context);
        modifiersLabel.setText("Modifiers (optional)");
        modifiersLabel.setPadding(0, padding / 2, 0, 0);
        content.addView(modifiersLabel);

        LinearLayout modifierRow = new LinearLayout(context);
        modifierRow.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox ctrl = modifierCheckBox(context, "Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, existing);
        CheckBox alt = modifierCheckBox(context, "Alt", KeyEvent.KEYCODE_ALT_LEFT, existing);
        CheckBox shift = modifierCheckBox(context, "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, existing);
        CheckBox meta = modifierCheckBox(context, "Win", KeyEvent.KEYCODE_META_LEFT, existing);
        modifierRow.addView(ctrl);
        modifierRow.addView(alt);
        modifierRow.addView(shift);
        modifierRow.addView(meta);
        content.addView(modifierRow);

        TextView keysLabel = new TextView(context);
        keysLabel.setText("Keys (pressed from top to bottom)");
        keysLabel.setPadding(0, padding / 2, 0, padding / 4);
        content.addView(keysLabel);

        LinearLayout keysContainer = new LinearLayout(context);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(keysContainer);

        List<KeyRowModel> keyRows = new ArrayList<>();
        if (existing != null && existing.keys.length > 0) {
            for (int code : existing.keys) {
                KeyOption selected = findOptionByCode(availableKeys, code);
                if (selected == null) {
                    selected = new KeyOption("Key " + code, code);
                }
                keyRows.add(new KeyRowModel(selected));
            }
        }
        if (keyRows.isEmpty()) {
            keyRows.add(new KeyRowModel(null));
        }

        Runnable[] rerenderHolder = new Runnable[1];
        rerenderHolder[0] = () -> renderKeyRows(
                context,
                keysContainer,
                keyRows,
                availableKeys,
                nameInput,
                rerenderHolder[0]);
        rerenderHolder[0].run();

        Button addKeyRow = new Button(context);
        addKeyRow.setText("+ Add another key");
        addKeyRow.setOnClickListener(v -> {
            keyRows.add(new KeyRowModel(null));
            rerenderHolder[0].run();
        });
        content.addView(addKeyRow);

        TextView help = new TextView(context);
        help.setText("Type to filter the list, then tap a suggestion to select it. " +
                "A single key with no modifiers is valid too.");
        help.setPadding(0, padding / 3, 0, 0);
        content.addView(help);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "Add Keys" : "Edit Key")
                .setView(content)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError("Enter a display name");
                        return;
                    }

                    List<Integer> selectedRegularKeys = new ArrayList<>();
                    for (int i = 0; i < keyRows.size(); i++) {
                        KeyOption selected = keyRows.get(i).selected;
                        if (selected == null) {
                            Toast.makeText(context,
                                    "Select a key from the dropdown in row " + (i + 1),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedRegularKeys.add(selected.code);
                    }

                    List<Integer> modifiers = new ArrayList<>(4);
                    if (ctrl.isChecked()) modifiers.add(KeyEvent.KEYCODE_CTRL_LEFT);
                    if (alt.isChecked()) modifiers.add(KeyEvent.KEYCODE_ALT_LEFT);
                    if (shift.isChecked()) modifiers.add(KeyEvent.KEYCODE_SHIFT_LEFT);
                    if (meta.isChecked()) modifiers.add(KeyEvent.KEYCODE_META_LEFT);

                    Definition updated = new Definition(
                            existing == null ? newId() : existing.id,
                            name,
                            toIntArray(modifiers),
                            toIntArray(selectedRegularKeys));

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

                    KeyComboButton button = findButton(controller, updated.id);
                    if (button == null) {
                        int size = controller.getDefaultKeyButtonSize();
                        Point position = controller.findGroupedSpawnPosition(size, size);
                        button = new KeyComboButton(controller, context, updated);
                        controller.addElement(button, position.x, position.y, size, size);
                    } else {
                        button.updateDefinition(updated);
                        button.hidden = false;
                        button.setVisibility(View.VISIBLE);
                    }

                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                    controller.vibrate(KeyEvent.ACTION_DOWN);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static void renderKeyRows(Context context,
                                      LinearLayout container,
                                      List<KeyRowModel> rows,
                                      List<KeyOption> availableKeys,
                                      EditText displayName,
                                      Runnable rerender) {
        container.removeAllViews();
        for (int index = 0; index < rows.size(); index++) {
            final int rowIndex = index;
            KeyRowModel model = rows.get(index);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);

            AutoCompleteTextView selector = new AutoCompleteTextView(context);
            selector.setHint("Search key…");
            selector.setSingleLine(true);
            selector.setThreshold(0);
            selector.setDropDownHeight(Math.round(300 * context.getResources().getDisplayMetrics().density));
            ArrayAdapter<KeyOption> adapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    availableKeys);
            selector.setAdapter(adapter);

            if (model.selected != null) {
                selector.setText(model.selected.name, false);
            }

            selector.setOnClickListener(v -> selector.showDropDown());
            selector.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selector.showDropDown();
                }
            });
            selector.setOnItemClickListener((parent, view, position, id) -> {
                KeyOption option = (KeyOption) parent.getItemAtPosition(position);
                model.selected = option;
                selector.setText(option.name, false);
                selector.setSelection(selector.length());
                if (displayName.getText().toString().trim().isEmpty() && rows.size() == 1) {
                    displayName.setText(option.name);
                    displayName.setSelection(displayName.length());
                }
            });
            selector.addTextChangedListener(new TextWatcher() {
                private boolean internal;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!internal && model.selected != null && !model.selected.name.contentEquals(s)) {
                        model.selected = null;
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f);
            row.addView(selector, selectorParams);

            Button up = compactButton(context, "↑");
            up.setEnabled(index > 0);
            up.setOnClickListener(v -> {
                if (rowIndex > 0) {
                    KeyRowModel item = rows.remove(rowIndex);
                    rows.add(rowIndex - 1, item);
                    rerender.run();
                }
            });
            row.addView(up);

            Button down = compactButton(context, "↓");
            down.setEnabled(index < rows.size() - 1);
            down.setOnClickListener(v -> {
                if (rowIndex < rows.size() - 1) {
                    KeyRowModel item = rows.remove(rowIndex);
                    rows.add(rowIndex + 1, item);
                    rerender.run();
                }
            });
            row.addView(down);

            Button remove = compactButton(context, "×");
            remove.setEnabled(rows.size() > 1);
            remove.setOnClickListener(v -> {
                if (rows.size() > 1) {
                    rows.remove(rowIndex);
                    rerender.run();
                }
            });
            row.addView(remove);

            container.addView(row);
        }
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

    private static CheckBox modifierCheckBox(Context context,
                                             String label,
                                             int keyCode,
                                             Definition existing) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(label);
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

    private static List<Definition> loadDefinitions(Context context) {
        return loadDefinitionsForLayout(context, activeLayout(context));
    }

    private static List<Definition> loadDefinitionsForLayout(Context context, String layout) {
        String serialized = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(definitionsKey(layout), "[]");
        List<Definition> definitions = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(serialized);
            List<String> seenIds = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Definition definition = Definition.fromJson(array.getJSONObject(i));
                if (!definition.id.isEmpty() && definition.keys.length > 0 && !seenIds.contains(definition.id)) {
                    seenIds.add(definition.id);
                    definitions.add(definition);
                }
            }
        } catch (JSONException ignored) {
            // Treat corrupt metadata as no custom keys; geometry preferences remain untouched.
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
        String serialized = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(definitionsKey(layout), "[]");
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
        String value = preferences.getString(definitionsKey(fromLayout), "[]");
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
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
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
