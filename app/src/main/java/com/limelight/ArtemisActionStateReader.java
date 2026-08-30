package com.limelight;

import android.view.View;
import android.widget.ImageButton;

import com.limelight.overlay.StatsOverlay;
import com.limelight.preferences.PreferenceConfiguration;

import java.lang.reflect.Field;

/**
 * Reads the current runtime state behind Artemis action toggles without duplicating the action
 * implementations. Game predates Artemis Plus and keeps several of these fields private, so this
 * small compatibility reader centralizes the reflective access in one place. The project disables
 * obfuscation, making the field names stable for these debug/community builds.
 */
public final class ArtemisActionStateReader {
    private static Field prefConfigField;
    private static Field statsOverlayField;
    private static Field floatingMenuButtonField;
    private static boolean resolved;

    private ArtemisActionStateReader() {
    }

    public static Boolean getToggleState(ArtemisAction action, Game game) {
        if (action == null || game == null || game.isFinishing()) {
            return null;
        }

        try {
            resolveFields();
            PreferenceConfiguration prefConfig = prefConfigField == null
                    ? null : (PreferenceConfiguration) prefConfigField.get(game);

            switch (action) {
                case FULL_KEYBOARD:
                    return game.isKeyboardLayoutVisible();

                case TOGGLE_HUD:
                    return prefConfig == null ? null : prefConfig.enablePerfOverlay;

                case TOGGLE_STATS_OVERLAY: {
                    StatsOverlay overlay = statsOverlayField == null
                            ? null : (StatsOverlay) statsOverlayField.get(game);
                    return overlay == null ? Boolean.FALSE : overlay.getMode() != StatsOverlay.Mode.OFF;
                }

                case TOGGLE_FLOATING_MENU: {
                    ImageButton button = floatingMenuButtonField == null
                            ? null : (ImageButton) floatingMenuButtonField.get(game);
                    return button == null ? Boolean.FALSE : button.getVisibility() == View.VISIBLE;
                }

                case TOGGLE_ZOOM:
                    return game.isZoomModeEnabled();

                case TOGGLE_VIRTUAL_CONTROLLER:
                    return prefConfig == null ? null : prefConfig.onscreenController;

                default:
                    return null;
            }
        } catch (IllegalAccessException | RuntimeException e) {
            LimeLog.warning("Unable to read Artemis action state: " + e.getMessage());
            return null;
        }
    }

    private static synchronized void resolveFields() {
        if (resolved) {
            return;
        }
        resolved = true;
        prefConfigField = resolve("prefConfig");
        statsOverlayField = resolve("statsOverlay");
        floatingMenuButtonField = resolve("floatingMenuButton");
    }

    private static Field resolve(String name) {
        try {
            Field field = Game.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LimeLog.warning("Unable to resolve Game." + name + " for action state: " + e.getMessage());
            return null;
        }
    }
}
