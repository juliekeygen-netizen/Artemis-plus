package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creation, editing and per-OSC-layout persistence for user-defined key-combo buttons. */
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
                    object.optString("name", "Combo"),
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
            Point position = controller.findFreePositionForElement(size);
            KeyComboButton button = new KeyComboButton(controller, context, definition);
            controller.addElement(button, position.x, position.y, size, size);
            controller.loadSavedElementConfiguration(button);
        }
    }

    private static void showDefinitionDialog(KeyBoardController controller,
                                             Context context,
                                             Definition existing) {
        List<KeyOption> availableKeys;
        try {
            availableKeys = loadAvailableKeys(context);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to load key list: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Set<Integer> selectedKeys = new LinkedHashSet<>();
        if (existing != null) {
            for (int key : existing.keys) {
                selectedKeys.add(key);
            }
        }

        int padding = Math.max(12,
                Math.round(16 * context.getResources().getDisplayMetrics().density));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        TextView nameLabel = new TextView(context);
        nameLabel.setText("Button text");
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
        modifiersLabel.setText("Hold modifiers");
        modifiersLabel.setPadding(0, padding / 2, 0, 0);
        content.addView(modifiersLabel);

        CheckBox ctrl = modifierCheckBox(context, "Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, existing);
        CheckBox alt = modifierCheckBox(context, "Alt", KeyEvent.KEYCODE_ALT_LEFT, existing);
        CheckBox shift = modifierCheckBox(context, "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, existing);
        CheckBox meta = modifierCheckBox(context, "Win / Meta", KeyEvent.KEYCODE_META_LEFT, existing);
        content.addView(ctrl);
        content.addView(alt);
        content.addView(shift);
        content.addView(meta);

        TextView keyHelp = new TextView(context);
        keyHelp.setText("Choose one or more non-modifier keys. They are pressed together as one chord.");
        keyHelp.setPadding(0, padding / 2, 0, padding / 4);
        content.addView(keyHelp);

        Button chooseKeys = new Button(context);
        updateChooseKeysLabel(chooseKeys, selectedKeys.size());
        chooseKeys.setOnClickListener(v -> showKeyPicker(
                context,
                availableKeys,
                selectedKeys,
                () -> updateChooseKeysLabel(chooseKeys, selectedKeys.size())));
        content.addView(chooseKeys);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "Add Key Combo" : "Edit Key Combo")
                .setView(content)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError("Enter button text");
                        return;
                    }
                    if (selectedKeys.isEmpty()) {
                        Toast.makeText(context, "Choose at least one non-modifier key", Toast.LENGTH_SHORT).show();
                        return;
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
                            toIntArray(selectedKeys));

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
                        Point position = controller.findFreePositionForElement(size);
                        button = new KeyComboButton(controller, context, updated);
                        controller.addElement(button, position.x, position.y, size, size);
                    } else {
                        // Avoid changing an actively-held chord while it is down. Editor mode never
                        // sends runtime key presses, so updating here is safe.
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

    private static void showKeyPicker(Context context,
                                      List<KeyOption> options,
                                      Set<Integer> selectedKeys,
                                      Runnable onChanged) {
        String[] labels = new String[options.size()];
        boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++) {
            KeyOption option = options.get(i);
            labels[i] = option.name;
            checked[i] = selectedKeys.contains(option.code);
        }

        new AlertDialog.Builder(context)
                .setTitle("Combo keys")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    selectedKeys.clear();
                    for (int i = 0; i < options.size(); i++) {
                        if (checked[i]) {
                            selectedKeys.add(options.get(i).code);
                        }
                    }
                    onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        Set<Integer> seen = new LinkedHashSet<>();

        for (int i = 0; i < keys.length(); i++) {
            JSONObject object = keys.getJSONObject(i);
            int code = object.optInt("code", KeyEvent.KEYCODE_UNKNOWN);
            if (code == KeyEvent.KEYCODE_UNKNOWN ||
                    KeyBoardControllerConfigurationLoader.isModifierKey(code) ||
                    !seen.add(code)) {
                continue;
            }
            options.add(new KeyOption(object.optString("name", "Key " + code), code));
        }
        return options;
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
        String serialized = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(definitionsKey(context), "[]");
        List<Definition> definitions = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(serialized);
            Set<String> seenIds = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                Definition definition = Definition.fromJson(array.getJSONObject(i));
                if (!definition.id.isEmpty() && definition.keys.length > 0 && seenIds.add(definition.id)) {
                    definitions.add(definition);
                }
            }
        } catch (JSONException ignored) {
            // Treat corrupt metadata as no custom combos; geometry preferences remain untouched.
        }
        return definitions;
    }

    private static void saveDefinitions(Context context, List<Definition> definitions) {
        JSONArray array = new JSONArray();
        for (Definition definition : definitions) {
            try {
                array.put(definition.toJson());
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(definitionsKey(context), array.toString())
                .apply();
    }

    private static String definitionsKey(Context context) {
        String layout = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        return DEFINITIONS_PREFIX + layout;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void updateChooseKeysLabel(Button button, int count) {
        button.setText(count == 0 ? "Choose combo key(s)…" : "Selected keys: " + count);
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
