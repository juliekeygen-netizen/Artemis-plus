package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        StringBuilder haystack = new StringBuilder();
        haystack.append(normalizeSearch(displayName)).append(' ')
                .append(normalizeSearch(KeyEvent.keyCodeToString(keyCode))).append(' ')
                .append(searchAliasesForKey(keyCode));

        for (String token : normalizedQuery.split("\\s+")) {
            if (!token.isEmpty() && haystack.indexOf(token) < 0) {
                return false;
            }
        }
        return true;
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
                        button.getRegularKeys()));
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
            Toast.makeText(context, "Unable to load key list: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Context dialogContext = new ContextThemeWrapper(context, R.style.ArtemisEditorDialogTheme);
        float density = context.getResources().getDisplayMetrics().density;
        int padding = Math.max(12, Math.round(16 * density));

        LinearLayout content = new LinearLayout(dialogContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        TextView intro = label(dialogContext,
                "Add one normal key, or build an ordered chord. Modifiers are held while the keys below are pressed in order.",
                14f, 0xFFBDBDBD);
        intro.setPadding(0, 0, 0, Math.round(8 * density));
        content.addView(intro);

        TextView nameLabel = label(dialogContext, "Button label", 13f, Color.WHITE);
        content.addView(nameLabel);

        EditText nameInput = new EditText(dialogContext);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setHint("What should the bubble show?");
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

        TextView keysLabel = label(dialogContext, "Keys", 13f, Color.WHITE);
        keysLabel.setPadding(0, Math.round(10 * density), 0, Math.round(4 * density));
        content.addView(keysLabel);

        AutoCompleteTextView search = new AutoCompleteTextView(dialogContext);
        search.setHint("Search and add a key…");
        search.setSingleLine(true);
        search.setThreshold(0);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF8E8E93);
        search.setDropDownHeight(Math.round(300 * density));
        KeySearchAdapter searchAdapter = new KeySearchAdapter(dialogContext, availableKeys);
        search.setAdapter(searchAdapter);
        search.setOnClickListener(v -> search.showDropDown());
        search.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                search.showDropDown();
            }
        });
        content.addView(search);

        List<KeyOption> selectedKeys = new ArrayList<>();
        if (existing != null) {
            for (int code : existing.keys) {
                KeyOption option = findOptionByCode(availableKeys, code);
                selectedKeys.add(option != null ? option : new KeyOption("Key " + code, code));
            }
        }

        TextView orderHint = label(dialogContext,
                "Selected keys — hold and drag a row to change press order.",
                12f, 0xFF9E9E9E);
        orderHint.setPadding(0, Math.round(7 * density), 0, Math.round(4 * density));
        content.addView(orderHint);

        RecyclerView selectedList = new RecyclerView(dialogContext);
        selectedList.setLayoutManager(new LinearLayoutManager(dialogContext));
        SelectedKeysAdapter selectedAdapter = new SelectedKeysAdapter(dialogContext, selectedKeys);
        selectedList.setAdapter(selectedAdapter);
        int selectedListHeight = Math.min(
                Math.round(210 * density),
                Math.max(Math.round(110 * density), context.getResources().getDisplayMetrics().heightPixels / 3));
        content.addView(selectedList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                selectedListHeight));

        ItemTouchHelper selectedTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
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
                Collections.swap(selectedKeys, from, to);
                selectedAdapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        selectedTouchHelper.attachToRecyclerView(selectedList);

        TextView summary = label(dialogContext, "", 13f, 0xFFE0E0E0);
        summary.setPadding(0, Math.round(8 * density), 0, 0);
        content.addView(summary);

        Runnable updateSummary = () -> summary.setText(buildSummary(
                ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(), selectedKeys));
        selectedAdapter.setOnChanged(updateSummary);
        ctrl.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        alt.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        shift.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        meta.setOnCheckedChangeListener((buttonView, isChecked) -> updateSummary.run());
        updateSummary.run();

        search.setOnItemClickListener((parent, view, position, id) -> {
            KeyOption option = (KeyOption) parent.getItemAtPosition(position);
            selectedKeys.add(option);
            selectedAdapter.notifyItemInserted(selectedKeys.size() - 1);
            selectedList.scrollToPosition(selectedKeys.size() - 1);
            if (nameInput.getText().toString().trim().isEmpty() && selectedKeys.size() == 1) {
                nameInput.setText(option.name);
                nameInput.setSelection(nameInput.length());
            }
            search.setText("", false);
            updateSummary.run();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext)
                .setTitle(existing == null ? "Add Keys" : "Edit Key")
                .setView(content)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton(android.R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton("Delete", null);
        }
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    nameInput.setError("Enter a button label");
                    return;
                }
                if (selectedKeys.isEmpty()) {
                    Toast.makeText(dialogContext, "Add at least one key", Toast.LENGTH_SHORT).show();
                    search.requestFocus();
                    search.showDropDown();
                    return;
                }

                List<Integer> modifiers = new ArrayList<>(4);
                if (ctrl.isChecked()) modifiers.add(KeyEvent.KEYCODE_CTRL_LEFT);
                if (alt.isChecked()) modifiers.add(KeyEvent.KEYCODE_ALT_LEFT);
                if (shift.isChecked()) modifiers.add(KeyEvent.KEYCODE_SHIFT_LEFT);
                if (meta.isChecked()) modifiers.add(KeyEvent.KEYCODE_META_LEFT);

                List<Integer> regular = new ArrayList<>(selectedKeys.size());
                for (KeyOption option : selectedKeys) {
                    regular.add(option.code);
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

    private static String buildSummary(boolean ctrl,
                                       boolean alt,
                                       boolean shift,
                                       boolean meta,
                                       List<KeyOption> keys) {
        StringBuilder result = new StringBuilder("Sends: ");
        boolean hasModifier = false;
        if (ctrl) { result.append("Ctrl + "); hasModifier = true; }
        if (alt) { result.append("Alt + "); hasModifier = true; }
        if (shift) { result.append("Shift + "); hasModifier = true; }
        if (meta) { result.append("Win + "); hasModifier = true; }

        if (keys.isEmpty()) {
            result.append(hasModifier ? "…" : "nothing selected yet");
            return result.toString();
        }
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                result.append("  →  ");
            }
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

    private static final class SelectedKeysAdapter extends RecyclerView.Adapter<SelectedKeysAdapter.Holder> {
        private final Context context;
        private final List<KeyOption> keys;
        private Runnable onChanged;

        SelectedKeysAdapter(Context context, List<KeyOption> keys) {
            this.context = context;
            this.keys = keys;
        }

        void setOnChanged(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            float density = context.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(8 * density), Math.round(5 * density),
                    Math.round(4 * density), Math.round(5 * density));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF2B2B2F);
            background.setCornerRadius(10 * density);
            row.setBackground(background);

            TextView drag = label(context, "≡", 22f, 0xFF9E9E9E);
            drag.setGravity(Gravity.CENTER);
            drag.setContentDescription("Hold and drag to reorder");
            row.addView(drag, new LinearLayout.LayoutParams(
                    Math.round(34 * density), LinearLayout.LayoutParams.MATCH_PARENT));

            TextView name = label(context, "", 16f, Color.WHITE);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = label(context, "×", 24f, 0xFFFF8A80);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("Remove key");
            row.addView(remove, new LinearLayout.LayoutParams(
                    Math.round(42 * density), Math.round(42 * density)));

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Math.round(3 * density), 0, Math.round(3 * density));
            row.setLayoutParams(params);
            return new Holder(row, name, remove);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            holder.name.setText(keys.get(position).selectionLabel());
            holder.remove.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                keys.remove(adapterPosition);
                notifyItemRemoved(adapterPosition);
                if (onChanged != null) {
                    onChanged.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView remove;

            Holder(View itemView, TextView name, TextView remove) {
                super(itemView);
                this.name = name;
                this.remove = remove;
            }
        }
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
        definitions.removeIf(definition -> definition.id.equals(id));
        saveDefinitions(context, definitions);
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
