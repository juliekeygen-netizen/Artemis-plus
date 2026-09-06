package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.preference.PreferenceManager;

import com.limelight.ArtemisAction;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.ui.ArtemisEditorUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
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
        Set<String> selected = new HashSet<>(getSelectedActionIds(context));
        Context ui = ArtemisEditorUi.context(context);
        LinearLayout list = new LinearLayout(ui);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ArtemisEditorUi.dp(ui, 14), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 14), ArtemisEditorUi.dp(ui, 6));
        CheckBox[] boxes = new CheckBox[actions.length];
        for (int i = 0; i < actions.length; i++) {
            CheckBox box = new CheckBox(ui);
            box.setText(actions[i].getLabel(ui));
            box.setTextSize(15f);
            box.setTextColor(ArtemisEditorUi.TEXT_PRIMARY);
            box.setChecked(selected.contains(actions[i].getId()));
            box.setMinHeight(ArtemisEditorUi.dp(ui, 44));
            boxes[i] = box;
            list.addView(box, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 44)));
        }
        ScrollView scroll = new ScrollView(ui);
        scroll.addView(list);
        AlertDialog dialog = ArtemisEditorUi.builder(ui, ui.getString(R.string.artemis_action_picker_title))
                .setView(scroll)
                .setPositiveButton(R.string.artemis_apply, (d, which) -> {
                    // Preserve opaque IDs from newer clients. They remain inert on this build,
                    // but editing known actions must not silently erase forward-compatible data.
                    HashSet<String> requested = new HashSet<>();
                    for (String id : selected) {
                        if (ArtemisAction.fromId(id) == null && id != null && !id.isEmpty()) {
                            requested.add(id);
                        }
                    }
                    for (int i = 0; i < actions.length; i++) {
                        if (boxes[i].isChecked()) {
                            requested.add(actions[i].getId());
                            ensureActionPresent(controller, context, actions[i], true);
                        } else hideExistingAction(controller, actions[i]);
                    }
                    if (!requested.contains(ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER.getId())) {
                        COLLAPSED_CONTROLLERS.remove(controller);
                        controller.showEnabledElements();
                    } else applyCollapsedState(controller);
                    saveSelectedActionIds(context, requested);
                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                    Toast.makeText(context, R.string.artemis_action_buttons_updated, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> ArtemisEditorUi.styleDialog(dialog, context, 520, 600, true));
        dialog.show();
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
        button.setContentDescription(action.getLabel(context));
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
        Point position = controller.findGroupedSpawnPosition(size, size);
        KeyBoardDigitalButton button = createButton(action, controller, context);
        controller.addElement(button, position.x, position.y, size, size);
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

    private static void loadSavedConfiguration(keyBoardVirtualControllerElement element,
                                               Context context) {
        String layoutPreference = SafePreferenceValues.getString(
                PreferenceManager.getDefaultSharedPreferences(context),
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        SharedPreferences preferences = context.getSharedPreferences(
                layoutPreference,
                Context.MODE_PRIVATE);
        String serialized = SafePreferenceValues.getString(preferences, element.elementId, null);
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
        return getSelectedActionIdsForLayout(context, activeLayout(context));
    }

    private static Set<String> getSelectedActionIdsForLayout(Context context, String layout) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return SafePreferenceValues.getStringSetCopy(preferences, selectionKey(layout));
    }

    private static void saveSelectedActionIds(Context context, Set<String> actionIds) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(selectionKey(activeLayout(context)), new HashSet<>(actionIds))
                .apply();
    }

    static JSONArray exportSelectionForLayout(Context context, String layout) {
        JSONArray array = new JSONArray();
        Set<String> selected = getSelectedActionIdsForLayout(context, layout);
        java.util.TreeSet<String> unknown = new java.util.TreeSet<>(selected);
        // Known IDs use enum order. Unknown IDs are kept inert and sorted after them so an older
        // client can round-trip selections created by a newer client without executing them.
        for (ArtemisAction action : ArtemisAction.values()) {
            if (selected.contains(action.getId())) {
                array.put(action.getId());
            }
            unknown.remove(action.getId());
        }
        for (String id : unknown) {
            if (id != null && !id.isEmpty()) {
                array.put(id);
            }
        }
        return array;
    }

    static void importSelectionForLayout(Context context, String layout, JSONArray array) {
        HashSet<String> values = new HashSet<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(selectionKey(layout), values)
                .apply();
    }

    static void copySelectionForLayout(Context context, String fromLayout, String toLayout) {
        Set<String> values = getSelectedActionIdsForLayout(context, fromLayout);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(selectionKey(toLayout), new HashSet<>(values))
                .apply();
    }

    static void deleteSelectionForLayout(Context context, String layout) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(selectionKey(layout))
                .apply();
    }

    private static String activeLayout(Context context) {
        KeyboardProfilesManager.ensureInitialized(context);
        return SafePreferenceValues.getString(
                PreferenceManager.getDefaultSharedPreferences(context),
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
    }

    private static String selectionKey(String layout) {
        return SELECTED_PREFIX + layout;
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
