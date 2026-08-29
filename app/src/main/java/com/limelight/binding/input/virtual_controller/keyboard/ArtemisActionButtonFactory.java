package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.ArtemisAction;
import com.limelight.Game;
import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Creates and restores local Artemis-action buttons in the custom keyboard OSC layer. */
public final class ArtemisActionButtonFactory {
    private static final String PREFERENCES = "ArtemisPlusActionButtons";
    private static final String SELECTED_PREFIX = "selected_actions_";

    private static final Map<KeyBoardController, Boolean> COLLAPSED_CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ArtemisActionButtonFactory() {
    }

    public static void showPicker(KeyBoardController controller, Context context) {
        ArtemisAction[] actions = ArtemisAction.values();
        String[] labels = new String[actions.length];
        boolean[] checked = new boolean[actions.length];
        Set<String> selected = new HashSet<>(getSelectedActionIds(context));

        for (int i = 0; i < actions.length; i++) {
            labels[i] = actions[i].getLabel();
            checked[i] = selected.contains(actions[i].getId());
        }

        new AlertDialog.Builder(context)
                .setTitle("Add Artemis Actions")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton("Apply", (dialog, which) -> {
                    HashSet<String> requested = new HashSet<>();
                    for (int i = 0; i < actions.length; i++) {
                        if (checked[i]) {
                            requested.add(actions[i].getId());
                            ensureActionPresent(controller, context, actions[i], true);
                        } else {
                            hideExistingAction(controller, actions[i]);
                        }
                    }

                    if (!requested.contains(ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER.getId())) {
                        COLLAPSED_CONTROLLERS.remove(controller);
                        controller.showEnabledElements();
                    } else {
                        applyCollapsedState(controller);
                    }

                    saveSelectedActionIds(context, requested);
                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                    Toast.makeText(context, "Artemis action buttons updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void restoreSelectedActions(KeyBoardController controller, Context context) {
        Set<String> selected = getSelectedActionIds(context);
        if (selected.isEmpty()) {
            return;
        }

        for (ArtemisAction action : ArtemisAction.values()) {
            if (selected.contains(action.getId())) {
                ensureActionPresent(controller, context, action, false);
            }
        }
        applyCollapsedState(controller);
    }

    public static KeyBoardDigitalButton createButton(ArtemisAction action,
                                                     KeyBoardController controller,
                                                     Context context) {
        ArtemisActionButton button = new ArtemisActionButton(
                controller,
                elementId(action),
                context,
                action,
                primaryIcon(action),
                alternateIcon(action));
        button.setContentDescription(action.getLabel());
        button.setSlideActivationEnabled(false);

        button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                if (controller != null) {
                    controller.vibrate(KeyEvent.ACTION_DOWN);
                }
                if (action == ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER) {
                    toggleCustomButtonsKeepingToggle(controller, button);
                } else {
                    action.execute(Game.instance);
                    // Runtime state is read from Game by ArtemisActionButton. Redraw immediately so
                    // toggles respond visually on the same tap rather than waiting for polling.
                    button.invalidate();
                }
            }

            @Override
            public void onLongClick() {
                // Local actions intentionally have no separate long-press command.
            }

            @Override
            public void onRelease() {
                if (controller != null) {
                    controller.vibrate(KeyEvent.ACTION_UP);
                }
            }
        });
        return button;
    }

    private static void toggleCustomButtonsKeepingToggle(KeyBoardController controller,
                                                          keyBoardVirtualControllerElement toggle) {
        if (controller == null) {
            return;
        }

        boolean collapsed = Boolean.TRUE.equals(COLLAPSED_CONTROLLERS.get(controller));
        if (collapsed) {
            COLLAPSED_CONTROLLERS.put(controller, false);
            setToggleCollapsedVisual(toggle, false);
            controller.showEnabledElements();
            if (!toggle.hidden && toggle.enabled) {
                toggle.setVisibility(View.VISIBLE);
            }
            return;
        }

        COLLAPSED_CONTROLLERS.put(controller, true);
        setToggleCollapsedVisual(toggle, true);
        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            element.setVisibility(element == toggle ? View.VISIBLE : View.GONE);
        }
    }

    private static void applyCollapsedState(KeyBoardController controller) {
        keyBoardVirtualControllerElement toggle = findElement(
                controller,
                ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER);

        boolean collapsed = Boolean.TRUE.equals(COLLAPSED_CONTROLLERS.get(controller));
        if (!collapsed) {
            setToggleCollapsedVisual(toggle, false);
            return;
        }

        if (toggle == null || toggle.hidden || !toggle.enabled) {
            COLLAPSED_CONTROLLERS.remove(controller);
            setToggleCollapsedVisual(toggle, false);
            return;
        }

        setToggleCollapsedVisual(toggle, true);
        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            element.setVisibility(element == toggle ? View.VISIBLE : View.GONE);
        }
    }

    private static void setToggleCollapsedVisual(keyBoardVirtualControllerElement toggle,
                                                 boolean collapsed) {
        if (toggle instanceof ArtemisActionButton) {
            ArtemisActionButton button = (ArtemisActionButton) toggle;
            // Expanded: closed-eye glyph means "hide" and green ring means the custom layer is ON.
            // Collapsed: open-eye glyph means "show" and no green ring means the layer is OFF.
            button.setAlternateIcon(collapsed);
            button.setExplicitToggleState(!collapsed);
        }
    }

    private static void ensureActionPresent(KeyBoardController controller,
                                            Context context,
                                            ArtemisAction action,
                                            boolean forceVisible) {
        keyBoardVirtualControllerElement existing = findElement(controller, action);
        if (existing != null) {
            if (forceVisible) {
                existing.hidden = false;
                existing.enabled = true;
                existing.setVisibility(View.VISIBLE);
                existing.invalidate();
            }
            return;
        }

        int size = calculateButtonSize(context);
        int[] position = findFreePosition(controller, context, size);
        KeyBoardDigitalButton button = createButton(action, controller, context);
        controller.addElement(button, position[0], position[1], size, size);
        loadSavedConfiguration(button, context);

        if (forceVisible) {
            button.hidden = false;
            button.enabled = true;
            button.setVisibility(View.VISIBLE);
            button.invalidate();
        }
    }

    private static void hideExistingAction(KeyBoardController controller, ArtemisAction action) {
        keyBoardVirtualControllerElement existing = findElement(controller, action);
        if (existing != null) {
            existing.hidden = true;
            existing.setVisibility(View.GONE);
        }
    }

    private static keyBoardVirtualControllerElement findElement(KeyBoardController controller,
                                                                 ArtemisAction action) {
        if (controller == null) {
            return null;
        }

        String id = elementId(action);
        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            if (id.equals(element.elementId)) {
                return element;
            }
        }
        return null;
    }

    private static int calculateButtonSize(Context context) {
        return Math.max(1, Math.round(
                ArtemisActionButton.DEFAULT_SIZE_DP * context.getResources().getDisplayMetrics().density));
    }

    private static int[] findFreePosition(KeyBoardController controller, Context context, int size) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int spacing = Math.max(8, Math.round(8 * screen.density));
        int startY = Math.max(100, Math.round(72 * screen.density));
        List<Rect> occupied = new ArrayList<>();

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            if (element.getVisibility() == View.GONE || element.getLayoutParams() == null) {
                continue;
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            occupied.add(new Rect(
                    params.leftMargin,
                    params.topMargin,
                    params.leftMargin + params.width,
                    params.topMargin + params.height));
        }

        for (int y = startY; y + size < screen.heightPixels; y += size + spacing) {
            for (int x = spacing; x + size < screen.widthPixels; x += size + spacing) {
                Rect candidate = new Rect(x, y, x + size, y + size);
                boolean intersects = false;
                for (Rect rect : occupied) {
                    if (Rect.intersects(candidate, rect)) {
                        intersects = true;
                        break;
                    }
                }
                if (!intersects) {
                    return new int[]{x, y};
                }
            }
        }
        return new int[]{spacing, startY};
    }

    private static void loadSavedConfiguration(keyBoardVirtualControllerElement element,
                                               Context context) {
        String layoutPreference = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        SharedPreferences preferences = context.getSharedPreferences(
                layoutPreference,
                Context.MODE_PRIVATE);
        String serialized = preferences.getString(element.elementId, null);
        if (serialized == null) {
            return;
        }

        try {
            element.loadConfiguration(new JSONObject(serialized));
        } catch (JSONException ignored) {
            preferences.edit().remove(element.elementId).apply();
        }
    }

    private static Set<String> getSelectedActionIds(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        Set<String> values = preferences.getStringSet(selectionKey(context), null);
        return values == null ? new HashSet<>() : new HashSet<>(values);
    }

    private static void saveSelectedActionIds(Context context, Set<String> actionIds) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(selectionKey(context), new HashSet<>(actionIds))
                .apply();
    }

    private static String selectionKey(Context context) {
        String layoutPreference = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        return SELECTED_PREFIX + layoutPreference;
    }

    private static String elementId(ArtemisAction action) {
        return "artemis_action_" + action.getId();
    }

    private static int primaryIcon(ArtemisAction action) {
        switch (action) {
            case SOFT_KEYBOARD:
                return R.drawable.ic_artemis_action_keyboard_soft;
            case FULL_KEYBOARD:
                return R.drawable.ic_artemis_action_keyboard_full;
            case ROTATE_SCREEN:
                return R.drawable.ic_artemis_action_rotate;
            case QUICK_MENU:
                return R.drawable.ic_artemis_action_menu;
            case TOGGLE_HUD:
                return R.drawable.ic_artemis_action_gauge;
            case TOGGLE_STATS_OVERLAY:
                return R.drawable.ic_artemis_action_chart;
            case TOGGLE_FLOATING_MENU:
                return R.drawable.ic_artemis_action_menu_floating;
            case TOUCH_SENSITIVITY:
                return R.drawable.ic_artemis_action_touch;
            case SEND_CLIPBOARD:
                return R.drawable.ic_artemis_action_clipboard_out;
            case FETCH_CLIPBOARD:
                return R.drawable.ic_artemis_action_clipboard_in;
            case MOUSE_MODE:
                return R.drawable.ic_artemis_action_mouse;
            case TOGGLE_ZOOM:
                return R.drawable.ic_artemis_action_zoom_pan;
            case TOGGLE_VIRTUAL_CONTROLLER:
                return R.drawable.ic_artemis_action_gamepad;
            case TOGGLE_KEYBOARD_CONTROLLER:
                return R.drawable.ic_artemis_action_eye_closed;
            default:
                throw new IllegalArgumentException("No icon for action: " + action);
        }
    }

    private static int alternateIcon(ArtemisAction action) {
        return action == ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER
                ? R.drawable.ic_artemis_action_eye
                : -1;
    }
}
