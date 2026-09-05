from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path, old, new):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}: found {count}\n--- needle ---\n{old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")


def write_new(path, content):
    if path.exists():
        raise SystemExit(f"Refusing to overwrite existing file: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


# ArtemisAction: stable IDs remain literal Strings; only display metadata becomes @StringRes-backed.
action = ROOT / "app/src/main/java/com/limelight/ArtemisAction.java"
replace_once(
    action,
    "import android.widget.Toast;\n",
    "import android.content.Context;\nimport android.widget.Toast;\n\nimport androidx.annotation.StringRes;\n",
)
replace_once(
    action,
    '''    SOFT_KEYBOARD("soft_keyboard", "Soft Keyboard"),
    FULL_KEYBOARD("full_keyboard", "Full Keyboard"),
    ROTATE_SCREEN("rotate_screen", "Rotate Screen"),
    QUICK_MENU("quick_menu", "Quick Menu"),
    TOGGLE_HUD("toggle_hud", "Performance HUD"),
    TOGGLE_STATS_OVERLAY("toggle_stats_overlay", "Stats Overlay"),
    TOGGLE_FLOATING_MENU("toggle_floating_menu", "Floating Menu Button"),
    TOUCH_SENSITIVITY("touch_sensitivity", "Touch Sensitivity"),
    SEND_CLIPBOARD("send_clipboard", "Clipboard to PC"),
    FETCH_CLIPBOARD("fetch_clipboard", "Clipboard from PC"),
    MOUSE_MODE("mouse_mode", "Mouse Mode"),
    TOGGLE_ZOOM("toggle_zoom", "Toggle Zoom"),
    TOGGLE_VIRTUAL_CONTROLLER("toggle_virtual_controller", "Gamepad Overlay"),
    TOGGLE_KEYBOARD_CONTROLLER("toggle_keyboard_controller", "Custom Buttons" );

    private final String id;
    private final String label;

    ArtemisAction(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
''',
    '''    SOFT_KEYBOARD("soft_keyboard", R.string.artemis_action_label_soft_keyboard),
    FULL_KEYBOARD("full_keyboard", R.string.artemis_action_label_full_keyboard),
    ROTATE_SCREEN("rotate_screen", R.string.artemis_action_label_rotate_screen),
    QUICK_MENU("quick_menu", R.string.artemis_action_label_quick_menu),
    TOGGLE_HUD("toggle_hud", R.string.artemis_action_label_performance_hud),
    TOGGLE_STATS_OVERLAY("toggle_stats_overlay", R.string.artemis_action_label_stats_overlay),
    TOGGLE_FLOATING_MENU("toggle_floating_menu", R.string.artemis_action_label_floating_menu_button),
    TOUCH_SENSITIVITY("touch_sensitivity", R.string.artemis_action_label_touch_sensitivity),
    SEND_CLIPBOARD("send_clipboard", R.string.artemis_action_label_clipboard_to_pc),
    FETCH_CLIPBOARD("fetch_clipboard", R.string.artemis_action_label_clipboard_from_pc),
    MOUSE_MODE("mouse_mode", R.string.artemis_action_label_mouse_mode),
    TOGGLE_ZOOM("toggle_zoom", R.string.artemis_action_label_toggle_zoom),
    TOGGLE_VIRTUAL_CONTROLLER("toggle_virtual_controller", R.string.artemis_action_label_gamepad_overlay),
    TOGGLE_KEYBOARD_CONTROLLER("toggle_keyboard_controller", R.string.artemis_action_label_custom_buttons);

    private final String id;
    @StringRes
    private final int labelResId;

    ArtemisAction(String id, @StringRes int labelResId) {
        this.id = id;
        this.labelResId = labelResId;
    }

    public String getId() {
        return id;
    }

    @StringRes
    public int getLabelResId() {
        return labelResId;
    }

    public String getLabel(Context context) {
        return context.getString(labelResId);
    }
''',
)
replace_once(action, 'Toast.makeText(game, "Nothing was sent to the PC", Toast.LENGTH_SHORT).show();',
             'Toast.makeText(game, R.string.artemis_action_nothing_sent_to_pc, Toast.LENGTH_SHORT).show();')
replace_once(action, 'Toast.makeText(game, "Clipboard fetch could not start", Toast.LENGTH_SHORT).show();',
             'Toast.makeText(game, R.string.artemis_action_clipboard_fetch_failed, Toast.LENGTH_SHORT).show();')
replace_once(action, 'Toast.makeText(game, "Mouse mode cannot be changed in this session", Toast.LENGTH_SHORT).show();',
             'Toast.makeText(game, R.string.artemis_action_mouse_mode_unavailable, Toast.LENGTH_SHORT).show();')

# Artemis action picker/display consumers resolve localized labels at the UI edge.
factory = ROOT / "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButtonFactory.java"
replace_once(factory, "            box.setText(actions[i].getLabel());",
             "            box.setText(actions[i].getLabel(ui));")
replace_once(factory, '        AlertDialog dialog = ArtemisEditorUi.builder(ui, "Add Artemis Actions")',
             '        AlertDialog dialog = ArtemisEditorUi.builder(ui, ui.getString(R.string.artemis_action_picker_title))')
replace_once(factory, '                .setPositiveButton("Apply", (d, which) -> {',
             '                .setPositiveButton(R.string.artemis_apply, (d, which) -> {')
replace_once(factory, '                    Toast.makeText(context, "Artemis action buttons updated", Toast.LENGTH_SHORT).show();',
             '                    Toast.makeText(context, R.string.artemis_action_buttons_updated, Toast.LENGTH_SHORT).show();')
replace_once(factory, '        button.setContentDescription(action.getLabel());',
             '        button.setContentDescription(action.getLabel(context));')

# Quick Menu registry: keep stable action IDs as Strings, use resource IDs for all display metadata.
registry = ROOT / "app/src/main/java/com/limelight/quickmenu/StreamActionRegistry.java"
registry.write_text('''package com.limelight.quickmenu;

import androidx.annotation.StringRes;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable catalog of actions that may be placed in the customizable in-stream Quick Menu.
 *
 * Runtime availability and execution remain owned by GameMenu because those depend on the
 * active stream, display, and input device. Persisted layouts contain only stable action IDs;
 * translated labels, categories, and descriptions are resolved from Android resources at the
 * UI boundary and are never used as persistence or matching keys.
 */
public final class StreamActionRegistry {
    public static final String DISCONNECT = "session.disconnect";
    public static final String QUIT_SESSION = "session.quit";
    public static final String UPLOAD_CLIPBOARD = "clipboard.upload";
    public static final String FETCH_CLIPBOARD = "clipboard.fetch";
    public static final String SERVER_COMMANDS = "host.server_commands";
    public static final String TOGGLE_KEYBOARD = "input.system_keyboard";
    public static final String TOGGLE_ZOOM = "display.zoom_mode";
    public static final String ROTATE_SCREEN = "display.rotate";

    public static final String SELECT_MOUSE_MODE = "input.mouse_mode";
    public static final String TOGGLE_HUD = "overlay.performance_hud";
    public static final String TOGGLE_FLOATING_BUTTON = "overlay.floating_menu_button";
    public static final String TOGGLE_KEYBOARD_CONTROLLER = "overlay.special_keys";
    public static final String TOGGLE_VIRTUAL_CONTROLLER = "overlay.virtual_controller";
    public static final String TOGGLE_FULL_KEYBOARD = "overlay.full_keyboard";
    public static final String TASK_MANAGER = "windows.task_manager";
    public static final String SEND_KEYS = "windows.send_keys";
    public static final String SWITCH_TOUCH_SENSITIVITY = "input.touch_sensitivity";
    public static final String DEVICE_ACTIONS = "dynamic.device_actions";

    public static final class ActionDefinition {
        public final String id;
        @StringRes public final int labelResId;
        @StringRes public final int categoryResId;
        @StringRes public final int descriptionResId;

        private ActionDefinition(String id, @StringRes int labelResId,
                                 @StringRes int categoryResId, @StringRes int descriptionResId) {
            this.id = id;
            this.labelResId = labelResId;
            this.categoryResId = categoryResId;
            this.descriptionResId = descriptionResId;
        }
    }

    private static final Map<String, ActionDefinition> ACTIONS;
    private static final List<ActionDefinition> ORDERED_ACTIONS;
    private static final List<Integer> CATEGORY_RES_IDS;

    static {
        LinkedHashMap<String, ActionDefinition> actions = new LinkedHashMap<>();
        add(actions, DISCONNECT,
                R.string.artemis_quick_menu_action_disconnect_label,
                R.string.artemis_quick_menu_category_session,
                R.string.artemis_quick_menu_action_disconnect_description);
        add(actions, QUIT_SESSION,
                R.string.artemis_quick_menu_action_quit_session_label,
                R.string.artemis_quick_menu_category_session,
                R.string.artemis_quick_menu_action_quit_session_description);
        add(actions, UPLOAD_CLIPBOARD,
                R.string.artemis_quick_menu_action_upload_clipboard_label,
                R.string.artemis_quick_menu_category_clipboard,
                R.string.artemis_quick_menu_action_upload_clipboard_description);
        add(actions, FETCH_CLIPBOARD,
                R.string.artemis_quick_menu_action_fetch_clipboard_label,
                R.string.artemis_quick_menu_category_clipboard,
                R.string.artemis_quick_menu_action_fetch_clipboard_description);
        add(actions, SERVER_COMMANDS,
                R.string.artemis_quick_menu_action_server_commands_label,
                R.string.artemis_quick_menu_category_host,
                R.string.artemis_quick_menu_action_server_commands_description);
        add(actions, TOGGLE_KEYBOARD,
                R.string.artemis_quick_menu_action_toggle_keyboard_label,
                R.string.artemis_quick_menu_category_input,
                R.string.artemis_quick_menu_action_toggle_keyboard_description);
        add(actions, TOGGLE_ZOOM,
                R.string.artemis_quick_menu_action_zoom_mode_label,
                R.string.artemis_quick_menu_category_display,
                R.string.artemis_quick_menu_action_zoom_mode_description);
        add(actions, ROTATE_SCREEN,
                R.string.artemis_quick_menu_action_rotate_screen_label,
                R.string.artemis_quick_menu_category_display,
                R.string.artemis_quick_menu_action_rotate_screen_description);

        add(actions, SELECT_MOUSE_MODE,
                R.string.artemis_quick_menu_action_select_mouse_mode_label,
                R.string.artemis_quick_menu_category_input,
                R.string.artemis_quick_menu_action_select_mouse_mode_description);
        add(actions, TOGGLE_HUD,
                R.string.artemis_quick_menu_action_toggle_performance_hud_label,
                R.string.artemis_quick_menu_category_overlays,
                R.string.artemis_quick_menu_action_toggle_performance_hud_description);
        add(actions, TOGGLE_FLOATING_BUTTON,
                R.string.artemis_quick_menu_action_toggle_floating_button_label,
                R.string.artemis_quick_menu_category_overlays,
                R.string.artemis_quick_menu_action_toggle_floating_button_description);
        add(actions, TOGGLE_KEYBOARD_CONTROLLER,
                R.string.artemis_quick_menu_action_toggle_special_keys_label,
                R.string.artemis_quick_menu_category_overlays,
                R.string.artemis_quick_menu_action_toggle_special_keys_description);
        add(actions, TOGGLE_VIRTUAL_CONTROLLER,
                R.string.artemis_quick_menu_action_toggle_virtual_controller_label,
                R.string.artemis_quick_menu_category_overlays,
                R.string.artemis_quick_menu_action_toggle_virtual_controller_description);
        add(actions, TOGGLE_FULL_KEYBOARD,
                R.string.artemis_quick_menu_action_toggle_full_keyboard_label,
                R.string.artemis_quick_menu_category_overlays,
                R.string.artemis_quick_menu_action_toggle_full_keyboard_description);
        add(actions, TASK_MANAGER,
                R.string.artemis_quick_menu_action_task_manager_label,
                R.string.artemis_quick_menu_category_windows,
                R.string.artemis_quick_menu_action_task_manager_description);
        add(actions, SEND_KEYS,
                R.string.artemis_quick_menu_action_send_keys_label,
                R.string.artemis_quick_menu_category_windows,
                R.string.artemis_quick_menu_action_send_keys_description);
        add(actions, SWITCH_TOUCH_SENSITIVITY,
                R.string.artemis_quick_menu_action_touch_sensitivity_label,
                R.string.artemis_quick_menu_category_input,
                R.string.artemis_quick_menu_action_touch_sensitivity_description);
        add(actions, DEVICE_ACTIONS,
                R.string.artemis_quick_menu_action_device_actions_label,
                R.string.artemis_quick_menu_category_dynamic,
                R.string.artemis_quick_menu_action_device_actions_description);

        ACTIONS = Collections.unmodifiableMap(actions);
        ORDERED_ACTIONS = Collections.unmodifiableList(new ArrayList<>(actions.values()));

        ArrayList<Integer> categoryResIds = new ArrayList<>();
        for (ActionDefinition action : ORDERED_ACTIONS) {
            if (!categoryResIds.contains(action.categoryResId)) {
                categoryResIds.add(action.categoryResId);
            }
        }
        CATEGORY_RES_IDS = Collections.unmodifiableList(categoryResIds);
    }

    private StreamActionRegistry() {}

    private static void add(Map<String, ActionDefinition> actions, String id,
                            @StringRes int labelResId, @StringRes int categoryResId,
                            @StringRes int descriptionResId) {
        if (actions.containsKey(id)) {
            throw new IllegalStateException("Duplicate Quick Menu action ID: " + id);
        }
        actions.put(id, new ActionDefinition(id, labelResId, categoryResId, descriptionResId));
    }

    public static ActionDefinition find(String id) {
        return id == null ? null : ACTIONS.get(id);
    }

    public static boolean contains(String id) {
        return find(id) != null;
    }

    public static List<ActionDefinition> getAll() {
        return ORDERED_ACTIONS;
    }

    public static List<Integer> getCategoryResIds() {
        return CATEGORY_RES_IDS;
    }
}
''', encoding="utf-8", newline="\n")

# Quick Menu picker resolves metadata for display/search but compares category resource IDs.
editor = ROOT / "app/src/main/java/com/limelight/quickmenu/QuickMenuEditorDialog.java"
replace_once(
    editor,
    '''            List<String> categories = new ArrayList<>();
            categories.add(ui.getString(R.string.artemis_quick_menu_all_categories));
            categories.addAll(StreamActionRegistry.getCategories());
            Spinner category = new Spinner(ui);
''',
    '''            List<Integer> categoryResIds = new ArrayList<>();
            categoryResIds.add(0); // Sentinel for "All categories"; never persisted or compared to action IDs.
            categoryResIds.addAll(StreamActionRegistry.getCategoryResIds());
            List<String> categories = new ArrayList<>();
            categories.add(ui.getString(R.string.artemis_quick_menu_all_categories));
            for (int categoryResId : StreamActionRegistry.getCategoryResIds()) {
                categories.add(ui.getString(categoryResId));
            }
            Spinner category = new Spinner(ui);
''',
)
replace_once(
    editor,
    '''                String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                String selectedCategory = String.valueOf(category.getSelectedItem());
                rows.removeAllViews();
                for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
                    if (!ui.getString(R.string.artemis_quick_menu_all_categories).equals(selectedCategory) &&
                            !action.category.equals(selectedCategory)) continue;
                    String haystack = (action.label + " " + action.category + " " + action.description)
                            .toLowerCase(Locale.ROOT);
                    if (!query.isEmpty() && !haystack.contains(query)) continue;
                    rows.addView(actionPickerRow(action, picker), new LinearLayout.LayoutParams(
''',
    '''                String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                int selectedPosition = category.getSelectedItemPosition();
                int selectedCategoryResId = selectedPosition >= 0 && selectedPosition < categoryResIds.size()
                        ? categoryResIds.get(selectedPosition) : 0;
                rows.removeAllViews();
                for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
                    if (selectedCategoryResId != 0 && action.categoryResId != selectedCategoryResId) continue;
                    String label = ui.getString(action.labelResId);
                    String categoryLabel = ui.getString(action.categoryResId);
                    String description = ui.getString(action.descriptionResId);
                    String haystack = (label + " " + categoryLabel + " " + description)
                            .toLowerCase(Locale.ROOT);
                    if (!query.isEmpty() && !haystack.contains(query)) continue;
                    rows.addView(actionPickerRow(action, picker), new LinearLayout.LayoutParams(
''',
)
replace_once(
    editor,
    '''            TextView label = ArtemisEditorUi.label(ui, action.label, 14.5f, ArtemisEditorUi.TEXT_PRIMARY);
            TextView detail = ArtemisEditorUi.label(ui,
                    action.category + "  ·  " + action.description,
                    11.5f, ArtemisEditorUi.TEXT_SECONDARY);
''',
    '''            TextView label = ArtemisEditorUi.label(ui,
                    ui.getString(action.labelResId), 14.5f, ArtemisEditorUi.TEXT_PRIMARY);
            TextView detail = ArtemisEditorUi.label(ui,
                    ui.getString(action.categoryResId) + "  ·  " + ui.getString(action.descriptionResId),
                    11.5f, ArtemisEditorUi.TEXT_SECONDARY);
''',
)

# Dedicated default resources make the catalogs translatable without fabricating translations.
resources = ROOT / "app/src/main/res/values/artemis_action_catalog.xml"
write_new(resources, '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Local Artemis action buttons. Stable action IDs remain in ArtemisAction.java. -->
    <string name="artemis_action_label_soft_keyboard">Soft Keyboard</string>
    <string name="artemis_action_label_full_keyboard">Full Keyboard</string>
    <string name="artemis_action_label_rotate_screen">Rotate Screen</string>
    <string name="artemis_action_label_quick_menu">Quick Menu</string>
    <string name="artemis_action_label_performance_hud">Performance HUD</string>
    <string name="artemis_action_label_stats_overlay">Stats Overlay</string>
    <string name="artemis_action_label_floating_menu_button">Floating Menu Button</string>
    <string name="artemis_action_label_touch_sensitivity">Touch Sensitivity</string>
    <string name="artemis_action_label_clipboard_to_pc">Clipboard to PC</string>
    <string name="artemis_action_label_clipboard_from_pc">Clipboard from PC</string>
    <string name="artemis_action_label_mouse_mode">Mouse Mode</string>
    <string name="artemis_action_label_toggle_zoom">Toggle Zoom</string>
    <string name="artemis_action_label_gamepad_overlay">Gamepad Overlay</string>
    <string name="artemis_action_label_custom_buttons">Custom Buttons</string>
    <string name="artemis_action_picker_title">Add Artemis Actions</string>
    <string name="artemis_action_buttons_updated">Artemis action buttons updated</string>
    <string name="artemis_action_nothing_sent_to_pc">Nothing was sent to the PC</string>
    <string name="artemis_action_clipboard_fetch_failed">Clipboard fetch could not start</string>
    <string name="artemis_action_mouse_mode_unavailable">Mouse mode cannot be changed in this session</string>

    <!-- Quick Menu catalog categories. Resource IDs, not translated text, are category identities. -->
    <string name="artemis_quick_menu_category_session">Session</string>
    <string name="artemis_quick_menu_category_clipboard">Clipboard</string>
    <string name="artemis_quick_menu_category_host">Host</string>
    <string name="artemis_quick_menu_category_input">Input</string>
    <string name="artemis_quick_menu_category_display">Display</string>
    <string name="artemis_quick_menu_category_overlays">Overlays</string>
    <string name="artemis_quick_menu_category_windows">Windows</string>
    <string name="artemis_quick_menu_category_dynamic">Dynamic</string>

    <string name="artemis_quick_menu_action_disconnect_label">Disconnect</string>
    <string name="artemis_quick_menu_action_disconnect_description">Disconnect Artemis while leaving the host session running.</string>
    <string name="artemis_quick_menu_action_quit_session_label">Quit session</string>
    <string name="artemis_quick_menu_action_quit_session_description">End the current host session.</string>
    <string name="artemis_quick_menu_action_upload_clipboard_label">Upload clipboard</string>
    <string name="artemis_quick_menu_action_upload_clipboard_description">Send the Android clipboard to the host.</string>
    <string name="artemis_quick_menu_action_fetch_clipboard_label">Fetch clipboard</string>
    <string name="artemis_quick_menu_action_fetch_clipboard_description">Copy the host clipboard to Android.</string>
    <string name="artemis_quick_menu_action_server_commands_label">Server commands</string>
    <string name="artemis_quick_menu_action_server_commands_description">Open the commands advertised by the current host.</string>
    <string name="artemis_quick_menu_action_toggle_keyboard_label">Toggle keyboard</string>
    <string name="artemis_quick_menu_action_toggle_keyboard_description">Show or hide the Android system keyboard.</string>
    <string name="artemis_quick_menu_action_zoom_mode_label">Zoom mode</string>
    <string name="artemis_quick_menu_action_zoom_mode_description">Enable or disable stream Zoom/Pan mode.</string>
    <string name="artemis_quick_menu_action_rotate_screen_label">Rotate screen</string>
    <string name="artemis_quick_menu_action_rotate_screen_description">Rotate the stream Activity orientation.</string>
    <string name="artemis_quick_menu_action_select_mouse_mode_label">Select mouse mode</string>
    <string name="artemis_quick_menu_action_select_mouse_mode_description">Choose the active Artemis mouse/touch mode.</string>
    <string name="artemis_quick_menu_action_toggle_performance_hud_label">Toggle performance HUD</string>
    <string name="artemis_quick_menu_action_toggle_performance_hud_description">Show or hide stream performance statistics.</string>
    <string name="artemis_quick_menu_action_toggle_floating_button_label">Toggle floating menu button</string>
    <string name="artemis_quick_menu_action_toggle_floating_button_description">Show or hide the floating Quick Menu button.</string>
    <string name="artemis_quick_menu_action_toggle_special_keys_label">Toggle special keys</string>
    <string name="artemis_quick_menu_action_toggle_special_keys_description">Show or hide the customizable special-key overlay.</string>
    <string name="artemis_quick_menu_action_toggle_virtual_controller_label">Toggle virtual controller</string>
    <string name="artemis_quick_menu_action_toggle_virtual_controller_description">Show or hide the on-screen gamepad.</string>
    <string name="artemis_quick_menu_action_toggle_full_keyboard_label">Toggle full keyboard</string>
    <string name="artemis_quick_menu_action_toggle_full_keyboard_description">Show or hide the full on-screen keyboard.</string>
    <string name="artemis_quick_menu_action_task_manager_label">Task Manager</string>
    <string name="artemis_quick_menu_action_task_manager_description">Send Ctrl+Shift+Esc to the host.</string>
    <string name="artemis_quick_menu_action_send_keys_label">Send keys</string>
    <string name="artemis_quick_menu_action_send_keys_description">Open Artemis’s special key-combination list.</string>
    <string name="artemis_quick_menu_action_touch_sensitivity_label">Switch touch sensitivity</string>
    <string name="artemis_quick_menu_action_touch_sensitivity_description">Cycle the configured touch sensitivity mode.</string>
    <string name="artemis_quick_menu_action_device_actions_label">Device actions</string>
    <string name="artemis_quick_menu_action_device_actions_description">Insert actions supplied by the currently active controller or input device.</string>
</resources>
''')

# Focused regression: resource localization must never alter stable IDs or use translated text as identity.
test = ROOT / "app/src/test/java/com/limelight/ArtemisCatalogLocalizationTest.java"
write_new(test, '''package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.quickmenu.StreamActionRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class ArtemisCatalogLocalizationTest {
    private static final String[] ARTEMIS_ACTION_IDS = {
            "soft_keyboard", "full_keyboard", "rotate_screen", "quick_menu",
            "toggle_hud", "toggle_stats_overlay", "toggle_floating_menu",
            "touch_sensitivity", "send_clipboard", "fetch_clipboard", "mouse_mode",
            "toggle_zoom", "toggle_virtual_controller", "toggle_keyboard_controller"
    };

    private static final String[] QUICK_MENU_ACTION_IDS = {
            "session.disconnect", "session.quit", "clipboard.upload", "clipboard.fetch",
            "host.server_commands", "input.system_keyboard", "display.zoom_mode", "display.rotate",
            "input.mouse_mode", "overlay.performance_hud", "overlay.floating_menu_button",
            "overlay.special_keys", "overlay.virtual_controller", "overlay.full_keyboard",
            "windows.task_manager", "windows.send_keys", "input.touch_sensitivity",
            "dynamic.device_actions"
    };

    @Test
    public void artemisActionIdsRemainStableWhileLabelsResolveFromResources() {
        Context context = ApplicationProvider.getApplicationContext();
        ArtemisAction[] actions = ArtemisAction.values();
        assertEquals(ARTEMIS_ACTION_IDS.length, actions.length);

        for (int i = 0; i < actions.length; i++) {
            assertEquals(ARTEMIS_ACTION_IDS[i], actions[i].getId());
            assertTrue(actions[i].getLabelResId() != 0);
            String label = actions[i].getLabel(context);
            assertFalse(label.trim().isEmpty());
            assertEquals(context.getString(actions[i].getLabelResId()), label);
        }
    }

    @Test
    public void quickMenuIdsRemainStableAndDisplayMetadataResolvesFromResources() {
        Context context = ApplicationProvider.getApplicationContext();
        List<StreamActionRegistry.ActionDefinition> actions = StreamActionRegistry.getAll();
        assertEquals(QUICK_MENU_ACTION_IDS.length, actions.size());

        for (int i = 0; i < actions.size(); i++) {
            StreamActionRegistry.ActionDefinition action = actions.get(i);
            assertEquals(QUICK_MENU_ACTION_IDS[i], action.id);
            assertFalse(context.getString(action.labelResId).trim().isEmpty());
            assertFalse(context.getString(action.categoryResId).trim().isEmpty());
            assertFalse(context.getString(action.descriptionResId).trim().isEmpty());
        }
    }

    @Test
    public void quickMenuCategoryIdentityUsesUniqueResourceIds() {
        List<Integer> categories = StreamActionRegistry.getCategoryResIds();
        Set<Integer> unique = new HashSet<>(categories);
        assertEquals(categories.size(), unique.size());
        assertFalse(categories.contains(0));

        for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
            assertTrue(categories.contains(action.categoryResId));
        }
    }
}
''')

# Ensure the focused CI lane exercises the resource/ID boundary explicitly.
ci = ROOT / ".github/workflows/android-ci.yml"
replace_once(
    ci,
    '          --tests "com.limelight.ArtemisOrientationHelperTest"\n',
    '          --tests "com.limelight.ArtemisOrientationHelperTest"\n          --tests "com.limelight.ArtemisCatalogLocalizationTest"\n',
)

# Fail closed if any stable ID set drifted while applying display-only localization.
action_text = action.read_text(encoding="utf-8")
for stable_id in [
    "soft_keyboard", "full_keyboard", "rotate_screen", "quick_menu", "toggle_hud",
    "toggle_stats_overlay", "toggle_floating_menu", "touch_sensitivity", "send_clipboard",
    "fetch_clipboard", "mouse_mode", "toggle_zoom", "toggle_virtual_controller",
    "toggle_keyboard_controller",
]:
    if action_text.count(f'"{stable_id}"') != 1:
        raise SystemExit(f"ArtemisAction stable ID drift: {stable_id}")

registry_text = registry.read_text(encoding="utf-8")
for stable_id in [
    "session.disconnect", "session.quit", "clipboard.upload", "clipboard.fetch",
    "host.server_commands", "input.system_keyboard", "display.zoom_mode", "display.rotate",
    "input.mouse_mode", "overlay.performance_hud", "overlay.floating_menu_button",
    "overlay.special_keys", "overlay.virtual_controller", "overlay.full_keyboard",
    "windows.task_manager", "windows.send_keys", "input.touch_sensitivity",
    "dynamic.device_actions",
]:
    if registry_text.count(f'"{stable_id}"') != 1:
        raise SystemExit(f"Quick Menu stable ID drift: {stable_id}")

print("Action catalog localization patch applied; stable IDs preserved.")
