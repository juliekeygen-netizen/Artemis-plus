package com.limelight.quickmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable catalog of actions that may be placed in the customizable in-stream Quick Menu.
 *
 * Runtime availability and execution remain owned by GameMenu because those depend on the
 * active stream, display, and input device. Keeping stable IDs and editor metadata here makes
 * persisted layouts independent from translated/dynamic labels and from transient device state.
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
        public final String label;
        public final String category;
        public final String description;

        private ActionDefinition(String id, String label, String category, String description) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.description = description;
        }
    }

    private static final Map<String, ActionDefinition> ACTIONS;
    private static final List<ActionDefinition> ORDERED_ACTIONS;

    static {
        LinkedHashMap<String, ActionDefinition> actions = new LinkedHashMap<>();
        add(actions, DISCONNECT, "Disconnect", "Session", "Disconnect Artemis while leaving the host session running.");
        add(actions, QUIT_SESSION, "Quit session", "Session", "End the current host session.");
        add(actions, UPLOAD_CLIPBOARD, "Upload clipboard", "Clipboard", "Send the Android clipboard to the host.");
        add(actions, FETCH_CLIPBOARD, "Fetch clipboard", "Clipboard", "Copy the host clipboard to Android.");
        add(actions, SERVER_COMMANDS, "Server commands", "Host", "Open the commands advertised by the current host.");
        add(actions, TOGGLE_KEYBOARD, "Toggle keyboard", "Input", "Show or hide the Android system keyboard.");
        add(actions, TOGGLE_ZOOM, "Zoom mode", "Display", "Enable or disable stream Zoom/Pan mode.");
        add(actions, ROTATE_SCREEN, "Rotate screen", "Display", "Rotate the stream Activity orientation.");

        add(actions, SELECT_MOUSE_MODE, "Select mouse mode", "Input", "Choose the active Artemis mouse/touch mode.");
        add(actions, TOGGLE_HUD, "Toggle performance HUD", "Overlays", "Show or hide stream performance statistics.");
        add(actions, TOGGLE_FLOATING_BUTTON, "Toggle floating menu button", "Overlays", "Show or hide the floating Quick Menu button.");
        add(actions, TOGGLE_KEYBOARD_CONTROLLER, "Toggle special keys", "Overlays", "Show or hide the customizable special-key overlay.");
        add(actions, TOGGLE_VIRTUAL_CONTROLLER, "Toggle virtual controller", "Overlays", "Show or hide the on-screen gamepad.");
        add(actions, TOGGLE_FULL_KEYBOARD, "Toggle full keyboard", "Overlays", "Show or hide the full on-screen keyboard.");
        add(actions, TASK_MANAGER, "Task Manager", "Windows", "Send Ctrl+Shift+Esc to the host.");
        add(actions, SEND_KEYS, "Send keys", "Windows", "Open Artemis' special key-combination list.");
        add(actions, SWITCH_TOUCH_SENSITIVITY, "Switch touch sensitivity", "Input", "Cycle the configured touch sensitivity mode.");
        add(actions, DEVICE_ACTIONS, "Device actions", "Dynamic", "Insert actions supplied by the currently active controller or input device.");

        ACTIONS = Collections.unmodifiableMap(actions);
        ORDERED_ACTIONS = Collections.unmodifiableList(new ArrayList<>(actions.values()));
    }

    private StreamActionRegistry() {}

    private static void add(Map<String, ActionDefinition> actions, String id, String label,
                            String category, String description) {
        if (actions.containsKey(id)) {
            throw new IllegalStateException("Duplicate Quick Menu action ID: " + id);
        }
        actions.put(id, new ActionDefinition(id, label, category, description));
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

    public static List<String> getCategories() {
        ArrayList<String> categories = new ArrayList<>();
        for (ActionDefinition action : ORDERED_ACTIONS) {
            if (!categories.contains(action.category)) {
                categories.add(action.category);
            }
        }
        return categories;
    }

}
