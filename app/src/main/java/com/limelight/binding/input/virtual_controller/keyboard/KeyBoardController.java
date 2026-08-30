/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.Game;
import com.limelight.ui.FloatingControlPositionStore;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyBoardController {

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    public boolean shown = false;

    private final NvConnection conn;
    private final Context context;
    private final Handler handler;
    private final FrameLayout frame_layout;

    ControllerMode currentMode = ControllerMode.Active;

    private Button buttonConfigure;
    private Button buttonClearAll;
    private Button buttonAddKeys;
    private Button buttonAddActions;
    private Button buttonResetAll;
    private Button buttonProfiles;
    private Button buttonAcceptGroupMove;
    private View groupOutline;

    private static final String SETTINGS_POSITION_ID = "keyboardSettingsButton";
    private boolean configureSessionPositionPrepared;

    private final Vibrator vibrator;
    private final List<keyBoardVirtualControllerElement> elements = new ArrayList<>();

    private final List<keyBoardVirtualControllerElement> activeMoveGroup = new ArrayList<>();
    private final List<keyBoardVirtualControllerElement> outlinedGroup = new ArrayList<>();
    private float groupLastRawX;
    private float groupLastRawY;

    public KeyBoardController(final NvConnection conn,
                              FrameLayout layout,
                              final Context context) {
        this.conn = conn;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        KeyboardProfilesManager.ensureInitialized(context);
        createEditorControls();
        refreshLayout();
    }

    private void createEditorControls() {
        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.5f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_keyboard_setting);

        final int configureTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final long configureLongPressMs = ViewConfiguration.getLongPressTimeout();
        final long configureResetHoldMs = Math.max(1300L, configureLongPressMs + 700L);
        buttonConfigure.setOnClickListener(v -> cycleEditorMode());
        buttonConfigure.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startViewX;
            private float startViewY;
            private boolean moveArmed;
            private boolean moved;
            private boolean resetPromptShown;

            private final Runnable armMove = () -> {
                if (moved || resetPromptShown) return;
                moveArmed = true;
                Toast.makeText(context,
                        context.getString(R.string.keyboard_configure_movable),
                        Toast.LENGTH_SHORT).show();
                vibrate(KeyEvent.ACTION_DOWN);
            };

            private final Runnable offerReset = () -> {
                if (moved || resetPromptShown) return;
                resetPromptShown = true;
                moveArmed = false;
                new AlertDialog.Builder(context)
                        .setTitle("Reset position?")
                        .setMessage("Reset the on-screen settings button to its default position?")
                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            };

            private void cancelTimers() {
                handler.removeCallbacks(armMove);
                handler.removeCallbacks(offerReset);
            }

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startViewX = view.getX();
                        startViewY = view.getY();
                        moveArmed = false;
                        moved = false;
                        resetPromptShown = false;
                        handler.postDelayed(armMove, configureLongPressMs);
                        handler.postDelayed(offerReset, configureResetHoldMs);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        boolean beyondSlop = Math.hypot(dx, dy) > configureTouchSlop;
                        if (beyondSlop) {
                            handler.removeCallbacks(offerReset);
                        }
                        if (moveArmed && beyondSlop) {
                            moved = true;
                            float maxX = Math.max(0, frame_layout.getWidth() - view.getWidth());
                            float maxY = Math.max(0, frame_layout.getHeight() - view.getHeight());
                            view.setX(Math.max(0, Math.min(startViewX + dx, maxX)));
                            view.setY(Math.max(0, Math.min(startViewY + dy, maxY)));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelTimers();
                        if (moved) {
                            FloatingControlPositionStore.save(view, SETTINGS_POSITION_ID);
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                                !moveArmed && !resetPromptShown) {
                            view.performClick();
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });

        buttonClearAll = editorButton(context.getString(R.string.keyboard_clear_all));
        buttonClearAll.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.keyboard_clear_all_confirm_title))
                .setMessage(context.getString(R.string.keyboard_clear_all_confirm_message))
                .setPositiveButton(context.getString(R.string.yes), (dialog, which) -> {
                    for (keyBoardVirtualControllerElement element : elements) {
                        element.hidden = true;
                        element.setVisibility(View.GONE);
                    }
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                    vibrate(KeyEvent.ACTION_DOWN);
                })
                .setNegativeButton(context.getString(R.string.no), null)
                .show());

        // Unified path: one key, modifiers, or an ordered multi-key chord all use the same editor.
        buttonAddKeys = editorButton("Add Keys");
        buttonAddKeys.setOnClickListener(v -> KeyComboManager.showCreateDialog(this, context));

        buttonAddActions = editorButton("Add Actions");
        buttonAddActions.setOnClickListener(v ->
                ArtemisActionButtonFactory.showPicker(this, context));

        buttonResetAll = editorButton("Reset All");
        buttonResetAll.setOnClickListener(v -> confirmResetAll(false));
        buttonResetAll.setOnLongClickListener(v -> {
            confirmResetAll(true);
            return true;
        });

        buttonProfiles = editorButton("Profiles");
        buttonProfiles.setTextSize(12f);
        buttonProfiles.setOnClickListener(v -> KeyboardProfilesDialog.show(context, this));

        buttonAcceptGroupMove = editorButton("Accept");
        buttonAcceptGroupMove.setTextSize(11f);
        buttonAcceptGroupMove.setPadding(12, 0, 12, 0);
        buttonAcceptGroupMove.setOnClickListener(v -> exitGroupMoveMode(true));

        groupOutline = new View(context);
        groupOutline.setClickable(false);
        groupOutline.setFocusable(false);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(Math.max(2,
                Math.round(2 * context.getResources().getDisplayMetrics().density)),
                0xEEFFFFFF);
        outline.setCornerRadius(Math.round(8 * context.getResources().getDisplayMetrics().density));
        groupOutline.setBackground(outline);
        groupOutline.setVisibility(View.GONE);
    }

    private Button editorButton(String text) {
        Button button = new Button(context);
        button.setBackgroundColor(Color.DKGRAY);
        button.setText(text);
        button.setAlpha(0.7f);
        button.setVisibility(View.GONE);
        return button;
    }

    private void cycleEditorMode() {
        String message;
        if (currentMode == ControllerMode.Active) {
            exitGroupMoveMode(false);
            currentMode = ControllerMode.DisableEnableButtons;
            showElements();
            showControlButtons(true);
            message = context.getString(R.string.configuration_mode_disable_enable_buttons);
        } else if (currentMode == ControllerMode.DisableEnableButtons) {
            exitGroupMoveMode(false);
            currentMode = ControllerMode.MoveButtons;
            showEnabledElements();
            showControlButtons(false);
            message = context.getString(R.string.configuration_mode_move_buttons);
        } else if (currentMode == ControllerMode.MoveButtons) {
            exitGroupMoveMode(true);
            currentMode = ControllerMode.ResizeButtons;
            showControlButtons(false);
            message = context.getString(R.string.configuration_mode_resize_buttons);
        } else {
            exitGroupMoveMode(true);
            currentMode = ControllerMode.Active;
            showControlButtons(false);
            KeyBoardControllerConfigurationLoader.saveProfile(this, context);
            message = context.getString(R.string.configuration_mode_exiting);
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        buttonConfigure.invalidate();
        for (keyBoardVirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    Handler getHandler() {
        return handler;
    }

    public void hide(boolean temporary) {
        exitGroupMoveMode(false);
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }
        buttonConfigure.setVisibility(View.GONE);
        showControlButtons(false);
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
        activeMoveGroup.clear();
        outlinedGroup.clear();
        for (keyBoardVirtualControllerElement element : new ArrayList<>(elements)) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
        frame_layout.removeView(buttonClearAll);
        frame_layout.removeView(buttonAddKeys);
        frame_layout.removeView(buttonAddActions);
        frame_layout.removeView(buttonResetAll);
        frame_layout.removeView(buttonProfiles);
        frame_layout.removeView(buttonAcceptGroupMove);
        frame_layout.removeView(groupOutline);
    }

    public void setOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void addElement(keyBoardVirtualControllerElement element,
                           int x,
                           int y,
                           int width,
                           int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);
        element.setDefaultGeometry(x, y, width, height);
        frame_layout.addView(element, layoutParams);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    void removeElement(keyBoardVirtualControllerElement element) {
        if (element == null) {
            return;
        }
        elements.remove(element);
        activeMoveGroup.remove(element);
        outlinedGroup.remove(element);
        frame_layout.removeView(element);
        context.getSharedPreferences(
                KeyboardProfilesManager.getActiveStorageName(context),
                Context.MODE_PRIVATE)
                .edit()
                .remove(element.elementId)
                .apply();
        if (outlinedGroup.size() <= 1) {
            clearGestureGroupOutline();
        } else {
            updateGroupOutline();
        }
    }

    /**
     * Grow a text button horizontally when its label no longer fits. Controls connected to its
     * right edge are shifted as a unit so the wider bubble cannot overlap the rest of the group.
     */
    void ensureTextButtonWidth(KeyBoardDigitalButton element, String text) {
        if (element == null || !(element.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
        int targetWidth = KeyBoardDigitalButton.minimumWidthForText(context, text, params.height);
        if (targetWidth <= params.width) {
            return;
        }

        List<keyBoardVirtualControllerElement> group = getConnectedGroup(element);
        if (group.isEmpty()) {
            group.add(element);
        }
        int oldRight = params.leftMargin + params.width;
        int delta = targetWidth - params.width;
        int elementTop = params.topMargin;
        int elementBottom = params.topMargin + params.height;
        params.width = targetWidth;
        element.requestLayout();

        for (keyBoardVirtualControllerElement other : group) {
            if (other == element || !(other.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams otherParams = (FrameLayout.LayoutParams) other.getLayoutParams();
            int overlap = Math.min(elementBottom, otherParams.topMargin + otherParams.height) -
                    Math.max(elementTop, otherParams.topMargin);
            int minHeight = Math.min(params.height, otherParams.height);
            if (otherParams.leftMargin >= oldRight - 6 && overlap >= minHeight * 0.35f) {
                otherParams.leftMargin += delta;
                other.requestLayout();
            }
        }

        int parentWidth = frame_layout.getWidth() > 0
                ? frame_layout.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        Rect bounds = getBounds(group);
        int shiftLeft = Math.max(0, bounds.right - parentWidth);
        if (shiftLeft > 0) {
            int available = Math.min(shiftLeft, bounds.left);
            for (keyBoardVirtualControllerElement member : group) {
                FrameLayout.LayoutParams memberParams =
                        (FrameLayout.LayoutParams) member.getLayoutParams();
                memberParams.leftMargin = Math.max(0, memberParams.leftMargin - available);
                member.requestLayout();
            }
        }

        element.invalidate();
        KeyBoardControllerConfigurationLoader.saveProfile(this, context);
    }

    int getDefaultKeyButtonSize() {
        return Math.max(1, Math.round(
                ArtemisActionButton.DEFAULT_SIZE_DP * context.getResources().getDisplayMetrics().density));
    }

    /**
     * Spawn new user controls directly adjacent to an existing visible control so they immediately
     * participate in the same snapping/group model. A compact 4 px grid is used as the fallback.
     */
    Point findGroupedSpawnPosition(int width, int height) {
        final int gap = 4;
        int screenWidth = frame_layout.getWidth() > 0
                ? frame_layout.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = frame_layout.getHeight() > 0
                ? frame_layout.getHeight()
                : context.getResources().getDisplayMetrics().heightPixels;
        int minY = 100;

        List<Rect> occupied = visibleRects();
        for (int i = elements.size() - 1; i >= 0; i--) {
            keyBoardVirtualControllerElement anchor = elements.get(i);
            if (anchor.hidden || anchor.getVisibility() != View.VISIBLE ||
                    !(anchor.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) anchor.getLayoutParams();
            Point[] candidates = {
                    new Point(p.leftMargin + p.width + gap, p.topMargin),
                    new Point(p.leftMargin, p.topMargin + p.height + gap),
                    new Point(p.leftMargin - width - gap, p.topMargin),
                    new Point(p.leftMargin, p.topMargin - height - gap)
            };
            for (Point candidate : candidates) {
                Rect rect = new Rect(candidate.x, candidate.y,
                        candidate.x + width, candidate.y + height);
                if (candidate.x >= gap && candidate.y >= minY &&
                        rect.right <= screenWidth - gap && rect.bottom <= screenHeight - gap &&
                        !intersectsAny(rect, occupied)) {
                    return candidate;
                }
            }
        }

        for (int y = minY; y + height <= screenHeight - gap; y += height + gap) {
            for (int x = gap; x + width <= screenWidth - gap; x += width + gap) {
                Rect candidate = new Rect(x, y, x + width, y + height);
                if (!intersectsAny(candidate, occupied)) {
                    return new Point(x, y);
                }
            }
        }
        return new Point(gap, minY);
    }

    Point findFreePositionForElement(int elementSize) {
        return findGroupedSpawnPosition(elementSize, elementSize);
    }

    private List<Rect> visibleRects() {
        List<Rect> occupied = new ArrayList<>();
        for (keyBoardVirtualControllerElement element : elements) {
            if (element.hidden || element.getVisibility() != View.VISIBLE ||
                    !(element.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) element.getLayoutParams();
            occupied.add(new Rect(p.leftMargin, p.topMargin,
                    p.leftMargin + p.width, p.topMargin + p.height));
        }
        return occupied;
    }

    private static boolean intersectsAny(Rect candidate, List<Rect> occupied) {
        for (Rect rect : occupied) {
            if (Rect.intersects(candidate, rect)) {
                return true;
            }
        }
        return false;
    }

    void loadSavedElementConfiguration(keyBoardVirtualControllerElement element) {
        String layoutPreference = KeyboardProfilesManager.getActiveStorageName(context);
        SharedPreferences preferences = context.getSharedPreferences(layoutPreference, Context.MODE_PRIVATE);
        String serialized = preferences.getString(element.elementId, null);
        if (serialized == null) {
            return;
        }
        try {
            element.loadConfiguration(new JSONObject(serialized));
        } catch (JSONException e) {
            preferences.edit().remove(element.elementId).apply();
        }
    }

    private void prepareConfigureButtonSessionPosition() {
        if (configureSessionPositionPrepared) return;
        configureSessionPositionPrepared = true;
        if (FloatingControlPositionStore.shouldResetBetweenSessions(context)) {
            FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        }
    }

    private void resetConfigureButtonPosition() {
        FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        if (buttonConfigure == null) return;
        buttonConfigure.setX(0f);
        buttonConfigure.setY(15f);
    }

    public void refreshLayout() {
        KeyboardProfilesManager.ensureInitialized(context);
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int oldButtonSize = (int) (screen.heightPixels * 0.06f);
        int buttonSize = Math.max(1, Math.round(oldButtonSize * 1.15f));

        FrameLayout.LayoutParams configParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        // Preserve the old anchor, then move it 50 physical pixels left as requested.
        configParams.leftMargin = 0; // old 20px anchor shifted 50 physical px left, clamped to edge
        configParams.topMargin = 15;
        frame_layout.addView(buttonConfigure, configParams);
        prepareConfigureButtonSessionPosition();
        buttonConfigure.post(() -> FloatingControlPositionStore.restore(
                buttonConfigure, SETTINGS_POSITION_ID));

        buttonClearAll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonAddKeys.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonAddActions.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonResetAll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        int clearWidth = buttonClearAll.getMeasuredWidth();
        int keysWidth = buttonAddKeys.getMeasuredWidth();
        int actionsWidth = buttonAddActions.getMeasuredWidth();
        int resetWidth = buttonResetAll.getMeasuredWidth();
        int gap = 3;
        int totalWidth = clearWidth + keysWidth + actionsWidth + resetWidth + gap * 3;
        int startX = Math.max(0, screen.widthPixels / 2 - totalWidth / 2);

        addTopButton(buttonClearAll, startX, 15);
        addTopButton(buttonAddKeys, startX + clearWidth + gap, 15);
        addTopButton(buttonAddActions,
                startX + clearWidth + keysWidth + gap * 2,
                15);
        addTopButton(buttonResetAll,
                startX + clearWidth + keysWidth + actionsWidth + gap * 3,
                15);

        buttonProfiles.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        FrameLayout.LayoutParams profileParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        profileParams.leftMargin = Math.max(0,
                screen.widthPixels / 2 - buttonProfiles.getMeasuredWidth() / 2);
        profileParams.topMargin = Math.max(0,
                screen.heightPixels - buttonProfiles.getMeasuredHeight() - 20);
        frame_layout.addView(buttonProfiles, profileParams);

        buttonAcceptGroupMove.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        FrameLayout.LayoutParams acceptParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Math.max(1, Math.round(buttonAcceptGroupMove.getMeasuredHeight() * 0.78f)));
        acceptParams.leftMargin = Math.max(0,
                screen.widthPixels / 2 - buttonAcceptGroupMove.getMeasuredWidth() / 2);
        acceptParams.topMargin = 15;
        frame_layout.addView(buttonAcceptGroupMove, acceptParams);

        FrameLayout.LayoutParams outlineParams = new FrameLayout.LayoutParams(1, 1);
        frame_layout.addView(groupOutline, outlineParams);

        KeyBoardControllerConfigurationLoader.createDefaultLayout(this, context, conn);
        KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
        ArtemisActionButtonFactory.restoreSelectedActions(this, context);
        KeyComboManager.restore(this, context);

        // Native/custom controls are appended after the editor chrome. Bring the editor controls
        // back above them so Profiles/top buttons cannot be covered by a large user control.
        buttonConfigure.bringToFront();
        buttonClearAll.bringToFront();
        buttonAddKeys.bringToFront();
        buttonAddActions.bringToFront();
        buttonResetAll.bringToFront();
        buttonProfiles.bringToFront();
        buttonAcceptGroupMove.bringToFront();

        // Re-apply editor visibility because refreshLayout() is also used for profile switching.
        if (currentMode == ControllerMode.DisableEnableButtons) {
            showElements();
            showControlButtons(true);
        } else if (currentMode == ControllerMode.MoveButtons ||
                currentMode == ControllerMode.ResizeButtons) {
            showEnabledElements();
            showControlButtons(false);
        } else {
            showControlButtons(false);
        }
    }

    private void addTopButton(Button button, int left, int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = left;
        params.topMargin = top;
        frame_layout.addView(button, params);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(
                    keyEvent.getKeyCode(),
                    KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            Game.instance.onKey(null, keyEvent.getKeyCode(), keyEvent);
        }

        if (keyEvent.getSource() != 2) {
            vibrate(keyEvent.getAction());
        }
    }

    public void sendMouseMove(int x, int y) {
        if (Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseMove(x, y);
    }

    public void vibrate(int action) {
        if (PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate &&
                vibrator != null && vibrator.hasVibrator()) {
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
        buttonResetAll.setVisibility(visibility);
        buttonProfiles.setVisibility(visibility);
        if (!show) {
            buttonProfiles.setVisibility(View.GONE);
        }
    }

    private void confirmResetAll(boolean sizesOnly) {
        String title = sizesOnly ? "Reset All Sizes?" : "Reset All Controls?";
        String message = sizesOnly
                ? "Reset every visible button to its default size while keeping its position?"
                : "Reset every visible button to its default size and arrange them together again?";
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Reset", (dialog, which) -> resetAllControls(sizesOnly))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetAllControls(boolean sizesOnly) {
        exitGroupMoveMode(false);
        List<keyBoardVirtualControllerElement> controls = new ArrayList<>();
        for (keyBoardVirtualControllerElement element : elements) {
            if (!element.hidden) {
                controls.add(element);
            }
        }
        if (controls.isEmpty()) {
            return;
        }

        if (sizesOnly) {
            for (keyBoardVirtualControllerElement element : controls) {
                element.resetSizeToDefault();
            }
        } else {
            // Keep the current visual ordering but repack all controls with a 4 px gap, making the
            // reset result one connected group instead of scattering them back over the screen.
            controls.sort(Comparator.comparingInt((keyBoardVirtualControllerElement e) -> {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) e.getLayoutParams();
                return p.topMargin;
            }).thenComparingInt(e -> {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) e.getLayoutParams();
                return p.leftMargin;
            }));

            final int gap = 4;
            final int margin = 8;
            int screenWidth = frame_layout.getWidth() > 0
                    ? frame_layout.getWidth()
                    : context.getResources().getDisplayMetrics().widthPixels;
            int x = margin;
            int y = 100;
            int rowHeight = 0;

            for (keyBoardVirtualControllerElement element : controls) {
                int width = element.getDefaultWidth();
                int height = element.getDefaultHeight();
                if (x > margin && x + width > screenWidth - margin) {
                    x = margin;
                    y += rowHeight + gap;
                    rowHeight = 0;
                }
                element.setGeometry(x, y, width, height);
                x += width + gap;
                rowHeight = Math.max(rowHeight, height);
            }
        }

        KeyBoardControllerConfigurationLoader.saveProfile(this, context);
        vibrate(KeyEvent.ACTION_DOWN);
    }

    /** Connected component of the geometry-derived snapped group containing seed. */
    List<keyBoardVirtualControllerElement> getConnectedGroup(keyBoardVirtualControllerElement seed) {
        List<keyBoardVirtualControllerElement> result = new ArrayList<>();
        if (seed == null || seed.hidden || seed.getVisibility() != View.VISIBLE) {
            return result;
        }

        Set<keyBoardVirtualControllerElement> connected = new HashSet<>();
        ArrayDeque<keyBoardVirtualControllerElement> pending = new ArrayDeque<>();
        connected.add(seed);
        pending.add(seed);

        while (!pending.isEmpty()) {
            keyBoardVirtualControllerElement candidate = pending.removeFirst();
            for (keyBoardVirtualControllerElement other : elements) {
                if (connected.contains(other) || other.hidden ||
                        other.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (LayoutSnappingHelper.areGrouped(candidate, other)) {
                    connected.add(other);
                    pending.addLast(other);
                }
            }
        }
        result.addAll(connected);
        result.sort(Comparator.comparingInt((keyBoardVirtualControllerElement e) -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) e.getLayoutParams();
            return p.topMargin;
        }).thenComparingInt(e -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) e.getLayoutParams();
            return p.leftMargin;
        }));
        return result;
    }

    boolean enterGroupMoveMode(keyBoardVirtualControllerElement seed) {
        if (currentMode != ControllerMode.MoveButtons) {
            return false;
        }
        List<keyBoardVirtualControllerElement> group = getConnectedGroup(seed);
        if (group.size() <= 1) {
            return false;
        }
        activeMoveGroup.clear();
        activeMoveGroup.addAll(group);
        outlinedGroup.clear();
        outlinedGroup.addAll(group);
        buttonAcceptGroupMove.setVisibility(View.VISIBLE);
        updateGroupOutline();
        return true;
    }

    boolean isGroupMoveModeActive() {
        return !activeMoveGroup.isEmpty();
    }

    boolean isInActiveMoveGroup(keyBoardVirtualControllerElement element) {
        return activeMoveGroup.contains(element);
    }

    void beginActiveGroupMove(float rawX, float rawY) {
        groupLastRawX = rawX;
        groupLastRawY = rawY;
    }

    void moveActiveGroup(float rawX, float rawY) {
        if (activeMoveGroup.isEmpty()) {
            return;
        }
        int dx = Math.round(rawX - groupLastRawX);
        int dy = Math.round(rawY - groupLastRawY);
        if (dx == 0 && dy == 0) {
            return;
        }

        Rect bounds = getBounds(activeMoveGroup);
        int parentWidth = frame_layout.getWidth();
        int parentHeight = frame_layout.getHeight();
        if (parentWidth > 0) {
            dx = Math.max(-bounds.left, Math.min(dx, parentWidth - bounds.right));
        }
        if (parentHeight > 0) {
            dy = Math.max(-bounds.top, Math.min(dy, parentHeight - bounds.bottom));
        }

        for (keyBoardVirtualControllerElement element : activeMoveGroup) {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) element.getLayoutParams();
            p.leftMargin += dx;
            p.topMargin += dy;
            p.rightMargin = 0;
            p.bottomMargin = 0;
            element.requestLayout();
        }
        groupLastRawX = rawX;
        groupLastRawY = rawY;
        updateGroupOutline();
    }

    void finishActiveGroupMove() {
        if (!activeMoveGroup.isEmpty()) {
            KeyBoardControllerConfigurationLoader.saveProfile(this, context);
            updateGroupOutline();
        }
    }

    private void exitGroupMoveMode(boolean save) {
        if (save && !activeMoveGroup.isEmpty()) {
            KeyBoardControllerConfigurationLoader.saveProfile(this, context);
        }
        activeMoveGroup.clear();
        outlinedGroup.clear();
        if (buttonAcceptGroupMove != null) {
            buttonAcceptGroupMove.setVisibility(View.GONE);
        }
        if (groupOutline != null) {
            groupOutline.setVisibility(View.GONE);
        }
    }

    private Rect getBounds(List<keyBoardVirtualControllerElement> group) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (keyBoardVirtualControllerElement element : group) {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) element.getLayoutParams();
            left = Math.min(left, p.leftMargin);
            top = Math.min(top, p.topMargin);
            right = Math.max(right, p.leftMargin + p.width);
            bottom = Math.max(bottom, p.topMargin + p.height);
        }
        if (left == Integer.MAX_VALUE) {
            return new Rect(0, 0, 1, 1);
        }
        return new Rect(left, top, right, bottom);
    }

    void showGestureGroupOutline(List<keyBoardVirtualControllerElement> group) {
        if (isGroupMoveModeActive()) {
            outlinedGroup.clear();
            outlinedGroup.addAll(activeMoveGroup);
            updateGroupOutline();
            return;
        }
        outlinedGroup.clear();
        if (group != null) {
            for (keyBoardVirtualControllerElement element : group) {
                if (element != null && !element.hidden && element.getVisibility() == View.VISIBLE) {
                    outlinedGroup.add(element);
                }
            }
        }
        if (outlinedGroup.size() <= 1) {
            clearGestureGroupOutline();
            return;
        }
        updateGroupOutline();
    }

    void showSnapGroupOutline(keyBoardVirtualControllerElement seed) {
        showGestureGroupOutline(getConnectedGroup(seed));
    }

    void clearGestureGroupOutline() {
        if (isGroupMoveModeActive()) {
            outlinedGroup.clear();
            outlinedGroup.addAll(activeMoveGroup);
            updateGroupOutline();
            return;
        }
        outlinedGroup.clear();
        if (groupOutline != null) {
            groupOutline.setVisibility(View.GONE);
        }
    }

    boolean isElementCoveredByGroupOutline(keyBoardVirtualControllerElement element) {
        return groupOutline != null && groupOutline.getVisibility() == View.VISIBLE &&
                outlinedGroup.contains(element);
    }

    private void updateGroupOutline() {
        if (outlinedGroup.size() <= 1 || groupOutline == null || groupOutline.getLayoutParams() == null) {
            if (groupOutline != null) {
                groupOutline.setVisibility(View.GONE);
            }
            return;
        }
        Rect bounds = getBounds(outlinedGroup);
        int pad = Math.max(4,
                Math.round(5 * context.getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) groupOutline.getLayoutParams();
        params.leftMargin = Math.max(0, bounds.left - pad);
        params.topMargin = Math.max(0, bounds.top - pad);
        params.width = Math.max(1, bounds.width() + pad * 2);
        params.height = Math.max(1, bounds.height() + pad * 2);
        groupOutline.setVisibility(View.VISIBLE);
        groupOutline.requestLayout();
        groupOutline.bringToFront();
        for (keyBoardVirtualControllerElement element : outlinedGroup) {
            element.bringToFront();
        }
        if (buttonAcceptGroupMove.getVisibility() == View.VISIBLE) {
            buttonAcceptGroupMove.bringToFront();
        }
    }

    void switchKeyboardProfile(String profileId) {
        KeyBoardControllerConfigurationLoader.saveProfile(this, context);
        exitGroupMoveMode(false);
        if (KeyboardProfilesManager.setActiveProfile(context, profileId)) {
            reloadCurrentProfile();
            vibrate(KeyEvent.ACTION_DOWN);
        }
    }

    void reloadCurrentProfile() {
        exitGroupMoveMode(false);
        refreshLayout();
    }
}
