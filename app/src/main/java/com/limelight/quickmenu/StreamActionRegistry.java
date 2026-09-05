package com.limelight.quickmenu;

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
