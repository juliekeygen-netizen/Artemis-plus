package com.limelight;

import android.widget.Toast;

/**
 * Actions executed locally by Artemis Plus OSC buttons instead of being sent to the streamed PC.
 * Kept in com.limelight so it can call the same Game methods used by GameMenu without widening
 * their visibility or duplicating their implementation.
 */
public enum ArtemisAction {
    SOFT_KEYBOARD("soft_keyboard", "Soft Keyboard"),
    FULL_KEYBOARD("full_keyboard", "Full Keyboard"),
    ROTATE_SCREEN("rotate_screen", "Rotate Screen"),
    QUICK_MENU("quick_menu", "Quick Menu"),
    TOGGLE_HUD("toggle_hud", "Toggle HUD"),
    MOUSE_MODE("mouse_mode", "Mouse Mode"),
    TOGGLE_ZOOM("toggle_zoom", "Toggle Zoom"),
    TOGGLE_VIRTUAL_CONTROLLER("toggle_virtual_controller", "Gamepad Overlay"),
    TOGGLE_KEYBOARD_CONTROLLER("toggle_keyboard_controller", "Custom Buttons Overlay");

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

    public static ArtemisAction fromId(String id) {
        if (id == null) {
            return null;
        }
        for (ArtemisAction action : values()) {
            if (action.id.equals(id)) {
                return action;
            }
        }
        return null;
    }

    public boolean execute(Game game) {
        if (game == null || game.isFinishing()) {
            return false;
        }

        switch (this) {
            case SOFT_KEYBOARD:
                game.toggleKeyboard();
                return true;

            case FULL_KEYBOARD:
                game.toggleFullKeyboard();
                return true;

            case ROTATE_SCREEN:
                game.rotateScreen();
                return true;

            case QUICK_MENU:
                if (game.gameMenuCallbacks != null) {
                    game.gameMenuCallbacks.showMenu(null);
                    return true;
                }
                return false;

            case TOGGLE_HUD:
                game.toggleHUD();
                return true;

            case MOUSE_MODE:
                if (game.allowChangeMouseMode) {
                    game.selectMouseMode(game);
                    return true;
                }
                Toast.makeText(game, "Mouse mode cannot be changed in this session", Toast.LENGTH_SHORT).show();
                return false;

            case TOGGLE_ZOOM:
                game.toggleZoomMode();
                return true;

            case TOGGLE_VIRTUAL_CONTROLLER:
                game.toggleVirtualController();
                return true;

            case TOGGLE_KEYBOARD_CONTROLLER:
                // This can hide the overlay containing the action button itself, matching the
                // equivalent Quick Menu action.
                game.toggleKeyboardController();
                return true;

            default:
                return false;
        }
    }
}
