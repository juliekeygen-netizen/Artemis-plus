/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeyBoardController {

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    public boolean shown = false;

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final NvConnection conn;
    private final Context context;
    private final Handler handler;

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();

    private Button buttonConfigure = null;
    private Button buttonClearAll = null;
    private Button buttonAddKeys = null;
    private Button buttonAddActions = null;

    private Vibrator vibrator;
    private List<keyBoardVirtualControllerElement> elements = new ArrayList<>();

    public KeyBoardController(final NvConnection conn, FrameLayout layout, final Context context) {
        this.conn = conn;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Configure button
        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.5f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_keyboard_setting);

        // Add long click listener for moving the configure button
        buttonConfigure.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(context, context.getString(R.string.keyboard_configure_movable), Toast.LENGTH_SHORT).show();
                buttonConfigure.setTag("movable");
                vibrator.vibrate(100); // Give haptic feedback
                return true;
            }
        });

        // Add touch listener for moving
        buttonConfigure.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float lastTouchX, lastTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if ("movable".equals(view.getTag())) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            dX = view.getX() - event.getRawX();
                            dY = view.getY() - event.getRawY();
                            lastTouchX = event.getRawX();
                            lastTouchY = event.getRawY();
                            isMoving = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;

                            if (Math.abs(event.getRawX() - lastTouchX) > 5 ||
                                    Math.abs(event.getRawY() - lastTouchY) > 5) {
                                isMoving = true;
                            }

                            if (isMoving) {
                                newX = Math.max(0, Math.min(newX, frame_layout.getWidth() - view.getWidth()));
                                newY = Math.max(0, Math.min(newY, frame_layout.getHeight() - view.getHeight()));

                                view.setX(newX);
                                view.setY(newY);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            view.setTag(null);
                            if (!isMoving) {
                                view.performClick();
                            }
                            break;
                    }
                    return true;
                }
                return false;
            }
        });

        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active) {
                    currentMode = ControllerMode.DisableEnableButtons;
                    showElements();
                    showControlButtons(true);
                    message = context.getString(R.string.configuration_mode_disable_enable_buttons);
                } else if (currentMode == ControllerMode.DisableEnableButtons) {
                    currentMode = ControllerMode.MoveButtons;
                    showEnabledElements();
                    showControlButtons(false);
                    message = context.getString(R.string.configuration_mode_move_buttons);
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = context.getString(R.string.configuration_mode_resize_buttons);
                } else {
                    currentMode = ControllerMode.Active;
                    KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                    message = context.getString(R.string.configuration_mode_exiting);
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (keyBoardVirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

        // Clear All button
        buttonClearAll = new Button(context);
        buttonClearAll.setBackgroundColor(Color.DKGRAY);
        buttonClearAll.setText(context.getString(R.string.keyboard_clear_all));
        buttonClearAll.setAlpha(0.7f);
        buttonClearAll.setVisibility(View.GONE);
        buttonClearAll.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.keyboard_clear_all_confirm_title));
            builder.setMessage(context.getString(R.string.keyboard_clear_all_confirm_message));
            builder.setPositiveButton(context.getString(R.string.yes), (dialog, which) -> {
                for (keyBoardVirtualControllerElement element : elements) {
                    element.hidden = true;
                    element.setVisibility(View.GONE);
                }
                KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                vibrate(KeyEvent.ACTION_DOWN);
            });
            builder.setNegativeButton(context.getString(R.string.no), null);
            builder.show();
        });

        // Add Keys button
        buttonAddKeys = new Button(context);
        buttonAddKeys.setBackgroundColor(Color.DKGRAY);
        buttonAddKeys.setText(context.getString(R.string.keyboard_add_keys));
        buttonAddKeys.setAlpha(0.7f);
        buttonAddKeys.setVisibility(View.GONE);
        buttonAddKeys.setOnClickListener(v -> showKeySelectionDialog());

        // Artemis Plus local-action buttons. These live in the same movable/resizable custom OSC
        // layer but call Artemis itself instead of sending a key to the streamed PC.
        buttonAddActions = new Button(context);
        buttonAddActions.setBackgroundColor(Color.DKGRAY);
        buttonAddActions.setText("Add Actions");
        buttonAddActions.setAlpha(0.7f);
        buttonAddActions.setVisibility(View.GONE);
        buttonAddActions.setOnClickListener(v ->
                ArtemisActionButtonFactory.showPicker(KeyBoardController.this, context));

        refreshLayout();
    }

    Handler getHandler() {
        return handler;
    }

    public void hide(boolean temporary) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
        if (!temporary) {
            shown = false;
        }
    }

    public void hide() {
        hide(false);
    }

    public void show() {
        showEnabledElements();
        buttonConfigure.setVisibility(View.VISIBLE);
        shown = true;
    }

    public void showElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            if (currentMode == ControllerMode.DisableEnableButtons) {
                element.setVisibility(element.hidden ? View.GONE : View.VISIBLE);
            } else {
                element.setVisibility((element.hidden || !element.enabled) ? View.GONE : View.VISIBLE);
            }
        }
    }

    public void showEnabledElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            if (currentMode == ControllerMode.DisableEnableButtons) {
                element.setVisibility(element.hidden ? View.GONE : View.VISIBLE);
            } else {
                element.setVisibility((!element.hidden && element.enabled) ? View.VISIBLE : View.GONE);
            }
        }
    }

    public void toggleVisibility() {
        if (buttonConfigure.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void removeElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
        frame_layout.removeView(buttonClearAll);
        frame_layout.removeView(buttonAddKeys);
        frame_layout.removeView(buttonAddActions);
    }

    public void setOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void addElement(keyBoardVirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int buttonSize = (int) (screen.heightPixels * 0.06f);

        FrameLayout.LayoutParams configParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        configParams.leftMargin = 20 + buttonSize;
        configParams.topMargin = 15;
        frame_layout.addView(buttonConfigure, configParams);

        buttonClearAll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonAddKeys.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonAddActions.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int clearAllWidth = buttonClearAll.getMeasuredWidth();
        int addKeysWidth = buttonAddKeys.getMeasuredWidth();
        int addActionsWidth = buttonAddActions.getMeasuredWidth();

        int totalWidth = clearAllWidth + addKeysWidth + addActionsWidth + 6;
        int screenCenter = screen.widthPixels / 2;
        int startX = screenCenter - (totalWidth / 2);

        FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        clearParams.leftMargin = startX;
        clearParams.topMargin = 15;
        frame_layout.addView(buttonClearAll, clearParams);

        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        addParams.leftMargin = startX + clearAllWidth + 3;
        addParams.topMargin = 15;
        frame_layout.addView(buttonAddKeys, addParams);

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        actionParams.leftMargin = startX + clearAllWidth + addKeysWidth + 6;
        actionParams.topMargin = 15;
        frame_layout.addView(buttonAddActions, actionParams);

        KeyBoardControllerConfigurationLoader.createDefaultLayout(this, context, conn);
        KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
        ArtemisActionButtonFactory.restoreSelectedActions(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(keyEvent.getKeyCode(), KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }

        if (keyEvent.getSource() != 2) {
            vibrate(keyEvent.getAction());
        }
    }

    public void sendMouseMove(int x,int y){
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseMove(x,y);
    }

    public void vibrate(int action) {
        if (PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate && vibrator.hasVibrator()) {
            switch (action) {
                case KeyEvent.ACTION_DOWN:
                    frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    break;
                case KeyEvent.ACTION_UP:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                    } else {
                        frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    break;
                default:
                    frame_layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
    }

    private void showControlButtons(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        buttonClearAll.setVisibility(visibility);
        buttonAddKeys.setVisibility(visibility);
        buttonAddActions.setVisibility(visibility);
    }

    private void showKeySelectionDialog() {
        try {
            InputStream is = context.getAssets().open("config/keyboard.json");
            int length = is.available();
            byte[] buffer = new byte[length];
            is.read(buffer);
            is.close();
            String jsonConfig = new String(buffer, "utf8");

            JSONObject json = new JSONObject(jsonConfig);
            JSONObject data = json.getJSONObject("data");
            JSONArray keystrokeList = data.getJSONArray("keystroke");
            JSONArray mouseList = data.getJSONArray("mouse");
            JSONArray rockerList = data.getJSONArray("rocker");
            JSONArray dpadList = data.getJSONArray("dpad");

            List<JSONObject> allItemsList = new ArrayList<>();
            List<String> keyNamesList = new ArrayList<>();

            for (int i = 0; i < keystrokeList.length(); i++) {
                JSONObject key = keystrokeList.getJSONObject(i);
                key.put("type", 0);
                allItemsList.add(key);
                keyNamesList.add(key.getString("name"));
            }

            for (int i = 0; i < mouseList.length(); i++) {
                JSONObject obj = mouseList.getJSONObject(i);
                obj.put("type", 1);
                allItemsList.add(obj);
                keyNamesList.add(obj.getString("name"));
            }

            for (int i = 0; i < rockerList.length(); i++) {
                JSONObject obj = rockerList.getJSONObject(i);
                obj.put("type", 2);
                allItemsList.add(obj);
                keyNamesList.add(obj.getString("name") + " (Joystick)");
            }

            for (int i = 0; i < dpadList.length(); i++) {
                JSONObject obj = dpadList.getJSONObject(i);
                obj.put("type", 3);
                allItemsList.add(obj);
                keyNamesList.add(obj.getString("name") + " (D-Pad)");
            }

            android.content.SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Context.MODE_PRIVATE);
            String value = preferences.getString(GameMenu.KEY_NAME, "");

            if (!TextUtils.isEmpty(value)) {
                try {
                    KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                    if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                        List<KeyConfigHelper.Shortcut> shortcutData = shortcutFile.data;
                        for (int idx = 0; idx < shortcutData.size(); idx++) {
                            KeyConfigHelper.Shortcut sc = shortcutData.get(idx);

                            String id = (sc.id == null || sc.id.isEmpty()) ? Integer.toString(idx) : sc.id;
                            String name = sc.name;

                            JSONObject customKey = new JSONObject();
                            customKey.put("type", 4);
                            customKey.put("name", name);
                            customKey.put("elementId", "custom_" + id);
                            customKey.put("sticky", sc.sticky);

                            JSONArray keyCodesJson = new JSONArray();
                            for (String code : sc.keys) {
                                keyCodesJson.put(code);
                            }
                            customKey.put("keys", keyCodesJson);

                            allItemsList.add(customKey);
                            keyNamesList.add(context.getString(R.string.keyboard_key_custom, name));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Error loading custom keys: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            String[] keyNames = keyNamesList.toArray(new String[0]);
            boolean[] checkedItems = new boolean[keyNames.length];

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.keyboard_select_keys));
            builder.setMultiChoiceItems(keyNames, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
            });

            builder.setPositiveButton(context.getString(R.string.keyboard_add), (dialog, which) -> {
                DisplayMetrics screen = context.getResources().getDisplayMetrics();
                int height = screen.heightPixels;

                int BUTTON_SIZE = 10;
                int w = KeyBoardControllerConfigurationLoader.screenScale(BUTTON_SIZE, height);
                int maxW = screen.widthPixels / 18;

                if (w > maxW) {
                    BUTTON_SIZE = KeyBoardControllerConfigurationLoader.screenScaleSwitch(maxW, height);
                    w = KeyBoardControllerConfigurationLoader.screenScale(BUTTON_SIZE, height);
                }

                Set<String> existingElementIds = new HashSet<>();
                List<Rect> existingPositions = new ArrayList<>();
                for (keyBoardVirtualControllerElement element : elements) {
                    if (element.getVisibility() != View.GONE) {
                        existingElementIds.add(element.elementId);
                        try {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
                            if (params != null) {
                                existingPositions.add(new Rect(
                                        params.leftMargin,
                                        params.topMargin,
                                        params.leftMargin + params.width,
                                        params.topMargin + params.height));
                            }
                        } catch (ClassCastException e) {
                            existingPositions.add(new Rect(
                                    (int) element.getX(),
                                    (int) element.getY(),
                                    (int) element.getX() + element.getWidth(),
                                    (int) element.getY() + element.getHeight()));
                        }
                    }
                }

                int elementsAdded = 0;
                int duplicatesFound = 0;
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) {
                        try {
                            JSONObject obj = allItemsList.get(i);
                            int type = obj.optInt("type", 0);

                            String elementId;
                            if (type == 2 || type == 3 || type == 4) {
                                elementId = obj.getString("elementId");
                            } else {
                                int code = obj.getInt("code");
                                int switchButton = obj.optInt("switchButton", 0);
                                elementId = type == 0 ? "key_" + code : "m_" + code;
                                if (switchButton == 1) {
                                    elementId = type == 0 ? "key_s_" + code : "m_s_" + code;
                                }
                            }

                            if (existingElementIds.contains(elementId)) {
                                duplicatesFound++;
                                continue;
                            }

                            int elementSize = (type >= 2) ? (int)(w * 2.5) : w;
                            Point position = findNonOverlappingPosition(existingPositions, elementSize);

                            keyBoardVirtualControllerElement newElement = null;

                            if (type == 4) {
                                String name = obj.getString("name");
                                boolean sticky = obj.getBoolean("sticky");
                                JSONArray keysJson = obj.getJSONArray("keys");

                                short[] vkKeyCodes = new short[keysJson.length()];
                                for (int j = 0; j < keysJson.length(); j++) {
                                    String code = keysJson.getString(j);
                                    int keycode;
                                    if (code.startsWith("0x")) {
                                        keycode = Integer.parseInt(code.substring(2), 16);
                                    } else if (code.startsWith("VK_")) {
                                        Field field = KeyMapper.class.getDeclaredField(code);
                                        keycode = field.getInt(null);
                                    } else {
                                        throw new IllegalArgumentException("Unknown key code: " + code);
                                    }
                                    vkKeyCodes[j] = (short) keycode;
                                }

                                newElement = KeyBoardControllerConfigurationLoader.createCustomButton(
                                        elementId, vkKeyCodes, 1, name, -1, sticky, this, conn, context);
                                addElement(newElement, position.x, position.y, w, w);

                            } else if (type == 2) {
                                int[] keys = new int[]{
                                        obj.getInt("upCode"),
                                        obj.getInt("downCode"),
                                        obj.getInt("leftCode"),
                                        obj.getInt("rightCode"),
                                        obj.getInt("middleCode")
                                };

                                newElement = KeyBoardControllerConfigurationLoader.createKeyBoardAnalogStickButton(
                                        this, elementId, context, keys);
                                addElement(newElement, position.x, position.y, elementSize, elementSize);

                            } else if (type == 3) {
                                newElement = KeyBoardControllerConfigurationLoader.createDiaitalPadButton(
                                        elementId,
                                        obj.getInt("leftCode"),
                                        obj.getInt("rightCode"),
                                        obj.getInt("upCode"),
                                        obj.getInt("downCode"),
                                        this, context);
                                addElement(newElement, position.x, position.y, elementSize, elementSize);

                            } else {
                                String name = obj.getString("name");
                                int code = obj.getInt("code");

                                if (elementId.equals("m_9") || elementId.equals("m_10") || elementId.equals("m_11")) {
                                    newElement = KeyBoardControllerConfigurationLoader.createDigitalTouchButton(
                                            elementId, code, type, 1, name, -1, this, context);
                                } else {
                                    newElement = KeyBoardControllerConfigurationLoader.createDigitalButton(
                                            elementId, code, type, 1, name, -1,
                                            PreferenceConfiguration.readPreferences(context).stickyModifierKey &&
                                                    KeyBoardControllerConfigurationLoader.isModifierKey(code),
                                            this, context);
                                }
                                addElement(newElement, position.x, position.y, w, w);
                            }

                            existingPositions.add(new Rect(position.x, position.y,
                                    position.x + elementSize, position.y + elementSize));
                            existingElementIds.add(elementId);

                            elementsAdded++;
                            vibrate(KeyEvent.ACTION_DOWN);

                        } catch (JSONException e) {
                            LimeLog.warning("Error adding key: " + e.getMessage());
                            e.printStackTrace();
                        } catch (Exception e) {
                            LimeLog.warning("Unexpected error adding key: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                StringBuilder feedback = new StringBuilder();
                if (elementsAdded > 0) {
                    KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                    feedback.append(context.getString(R.string.keyboard_keys_added, elementsAdded));
                }
                if (duplicatesFound > 0) {
                    if (feedback.length() > 0) {
                        feedback.append("\n");
                    }
                    feedback.append(context.getString(R.string.keyboard_duplicates_skipped, duplicatesFound));
                }

                if (feedback.length() > 0) {
                    Toast.makeText(context, feedback.toString(), Toast.LENGTH_LONG).show();
                }
            });

            builder.setNegativeButton(context.getString(R.string.cancel), null);
            builder.show();

        } catch (Exception e) {
            LimeLog.warning("Error loading keyboard configuration: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.keyboard_load_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private Point findNonOverlappingPosition(List<Rect> existingPositions, int elementSize) {
        Point position = findPositionNextToExisting(existingPositions, elementSize);
        if (position != null) {
            return position;
        }
        return findNonOverlappingPositionFromTopLeft(existingPositions, elementSize);
    }

    private Point findPositionNextToExisting(List<Rect> existingPositions, int elementSize) {
        int spacing = 10;

        for (Rect existingRect : existingPositions) {
            Point[] potentialPositions = {
                    new Point(existingRect.right + spacing, existingRect.top),
                    new Point(existingRect.left - elementSize - spacing, existingRect.top),
                    new Point(existingRect.left, existingRect.bottom + spacing),
                    new Point(existingRect.left, existingRect.top - elementSize - spacing)
            };

            for (Point p : potentialPositions) {
                if (isPositionFree(p, elementSize, existingPositions)) {
                    return p;
                }
            }
        }

        return null;
    }

    private boolean isPositionFree(Point pos, int elementSize, List<Rect> existingPositions) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int screenWidth = screen.widthPixels;
        int screenHeight = screen.heightPixels;
        int spacing = 10;

        Rect newRect = new Rect(pos.x, pos.y, pos.x + elementSize, pos.y + elementSize);

        if (newRect.left < spacing || newRect.right > screenWidth - spacing || newRect.top < 100 || newRect.bottom > screenHeight - 50) {
            return false;
        }

        for (Rect existing : existingPositions) {
            if (Rect.intersects(existing, newRect)) {
                return false;
            }
        }

        Rect configButtonArea = new Rect(0, 0, 150, 100);
        return !Rect.intersects(configButtonArea, newRect);
    }

    private Point findNonOverlappingPositionFromTopLeft(List<Rect> existingPositions, int elementSize) {
        int spacing = 10;
        int startY = 100;
        int x = spacing;
        int y = startY;

        while (isPositionFree(new Point(x, y), elementSize, existingPositions)) {
            x += elementSize + spacing;

            if (!isPositionFree(new Point(x, y), elementSize, existingPositions)) {
                x = spacing;
                y += elementSize + spacing;
            }

            if (isPositionFree(new Point(x, y), elementSize, existingPositions)) {
                return new Point(x, y);
            }
        }

        return new Point(spacing, startY);
    }

    private int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }
}
