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
                return ArtemisOrientationHelper.rotate(game);

            case QUICK_MENU:
                if (game.gameMenuCallbacks != null) {
                    game.gameMenuCallbacks.showMenu(null);
                    return true;
                }
                return false;

            case TOGGLE_HUD:
                game.toggleHUD();
                return true;

            case TOGGLE_STATS_OVERLAY:
                game.toggleStatsOverlay();
                return true;

            case TOGGLE_FLOATING_MENU:
                game.toggleFloatingButtonVisibility();
                return true;

            case TOUCH_SENSITIVITY:
                game.showTouchSensitivityDialog();
                return true;

            case SEND_CLIPBOARD:
                if (!game.sendClipboard(true)) {
                    Toast.makeText(game, "Nothing was sent to the PC", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;

            case FETCH_CLIPBOARD:
                if (!game.getClipboard(0)) {
                    Toast.makeText(game, "Clipboard fetch could not start", Toast.LENGTH_SHORT).show();
                    return false;
                }
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
                // ArtemisActionButtonFactory handles this one specially so the button that
                // restores the custom layer remains visible after the other buttons are hidden.
                return false;

            default:
                return false;
        }
    }
}
