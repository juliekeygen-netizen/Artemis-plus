package com.limelight;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

/**
 * Actions executed locally by Artemis Plus OSC buttons instead of being sent to the streamed PC.
 * Kept in com.limelight so it can call the same Game methods used by GameMenu without widening
 * their visibility or duplicating their implementation.
 */
public enum ArtemisAction {
    SOFT_KEYBOARD("soft_keyboard", R.string.artemis_action_label_soft_keyboard),
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
                    Toast.makeText(game, R.string.artemis_action_nothing_sent_to_pc, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;

            case FETCH_CLIPBOARD:
                if (!game.getClipboard(0)) {
                    Toast.makeText(game, R.string.artemis_action_clipboard_fetch_failed, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;

            case MOUSE_MODE:
                if (game.allowChangeMouseMode) {
                    game.selectMouseMode(game);
                    return true;
                }
                Toast.makeText(game, R.string.artemis_action_mouse_mode_unavailable, Toast.LENGTH_SHORT).show();
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
