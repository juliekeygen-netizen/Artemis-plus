/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.SidewaysStreamMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public abstract class keyBoardVirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;

    protected KeyBoardController virtualController;
    protected final String elementId;

    private final Paint paint = new Paint();

    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xA3DCDCDE;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;
    private int configDisabledColor = 0xF0AAAAAA;

    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    public boolean enabled = true;
    public boolean hidden = false;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    private int lastMoveX;
    private int lastMoveY;

    // Canonical geometry from controller creation. Saved profile geometry is loaded afterwards.
    private int defaultX = -1;
    private int defaultY = -1;
    private int defaultWidth = -1;
    private int defaultHeight = -1;

    private final int touchSlop;

    // Stable raw-coordinate resize gesture tracking. Raw coordinates do not change when this View's
    // bounds change, unlike MotionEvent.getX()/getY(), which caused the old grouped scaling to flash.
    private boolean resizeGestureMoved;
    private float resizeDownRawX;
    private float resizeDownRawY;
    private long lastResizeTapUpTime;
    private List<GroupResizeSnapshot> resizeGroup;
    private int resizeGroupOriginX;
    private int resizeGroupOriginY;
    private int resizeGroupRight;
    private int resizeGroupBottom;

    // Move-mode double tap enters explicit group-position mode.
    private boolean moveGestureMoved;
    private float moveDownRawX;
    private float moveDownRawY;
    private long lastMoveTapUpTime;
    private boolean stickyMoveX;
    private boolean stickyMoveY;
    private int stickyMoveAnchorX;
    private int stickyMoveAnchorY;

    private static final class GroupResizeSnapshot {
        final keyBoardVirtualControllerElement element;
        final int left;
        final int top;
        final int width;
        final int height;

        GroupResizeSnapshot(keyBoardVirtualControllerElement element,
                            int left,
                            int top,
                            int width,
                            int height) {
            this.element = element;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    protected keyBoardVirtualControllerElement(KeyBoardController controller, Context context, String elementId) {
        super(context);
        this.virtualController = controller;
        this.elementId = elementId;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** Called exactly once by the controller when this element is initially attached. */
    void setDefaultGeometry(int x, int y, int width, int height) {
        if (defaultWidth <= 0 || defaultHeight <= 0) {
            defaultX = Math.max(0, x);
            defaultY = Math.max(0, y);
            defaultWidth = Math.max(getMinimumResizeSizePx(), width);
            defaultHeight = Math.max(getMinimumResizeSizePx(), height);
        }
    }

    /** Compatibility helper for older controller code. */
    void setDefaultSize(int width, int height) {
        setDefaultGeometry(0, 0, width, height);
    }

    int getDefaultWidth() {
        return defaultWidth > 0 ? defaultWidth : Math.max(getMinimumResizeSizePx(), getWidth());
    }

    int getDefaultHeight() {
        return defaultHeight > 0 ? defaultHeight : Math.max(getMinimumResizeSizePx(), getHeight());
    }

    int getDefaultX() {
        return Math.max(0, defaultX);
    }

    int getDefaultY() {
        return Math.max(0, defaultY);
    }

    public void resetSizeToDefault() {
        if (defaultWidth <= 0 || defaultHeight <= 0 ||
                !(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.width = defaultWidth;
        layoutParams.height = defaultHeight;
        requestLayout();
        invalidate();
    }

    void setGeometry(int left, int top, int width, int height) {
        if (!(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        params.leftMargin = Math.max(0, left);
        params.topMargin = Math.max(0, top);
        params.width = Math.max(getMinimumResizeSizePx(), width);
        params.height = Math.max(getMinimumResizeSizePx(), height);
        params.rightMargin = 0;
        params.bottomMargin = 0;
        requestLayout();
        invalidate();
    }

    protected int getMinimumResizeSizePx() {
        return 20;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        // Once an axis snaps, keep it attached for a larger release distance than the initial snap
        // threshold. This hysteresis prevents tiny finger wobble from immediately tearing a control
        // back out of its group while still allowing deliberate movement parallel to the joined edge.
        int releaseThreshold = Math.max(18,
                Math.round(28 * getResources().getDisplayMetrics().density));
        if (stickyMoveX) {
            if (LayoutSnappingHelper.shouldRetainAxisLock(
                    newPos_x, stickyMoveAnchorX, releaseThreshold)) {
                newPos_x = stickyMoveAnchorX;
            } else {
                stickyMoveX = false;
            }
        }
        if (stickyMoveY) {
            if (LayoutSnappingHelper.shouldRetainAxisLock(
                    newPos_y, stickyMoveAnchorY, releaseThreshold)) {
                newPos_y = stickyMoveAnchorY;
            } else {
                stickyMoveY = false;
            }
        }

        lastMoveX = newPos_x;
        lastMoveY = newPos_y;
        boolean newlyLocked = false;

        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons) {
            View[] otherViews = new View[virtualController.getElements().size() - 1];
            int index = 0;
            for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
                if (element != this) {
                    otherViews[index++] = element;
                }
            }

            boolean wasStickyX = stickyMoveX;
            boolean wasStickyY = stickyMoveY;
            LayoutSnappingHelper.SnapResult snapResult = LayoutSnappingHelper.calculateSnappedPosition(
                    this, otherViews, newPos_x, newPos_y
            );

            newPos_x = snapResult.newX;
            newPos_y = snapResult.newY;
            if (snapResult.lockX) {
                stickyMoveX = true;
                stickyMoveAnchorX = newPos_x;
            }
            if (snapResult.lockY) {
                stickyMoveY = true;
                stickyMoveAnchorY = newPos_y;
            }
            newlyLocked = (snapResult.lockX && !wasStickyX) || (snapResult.lockY && !wasStickyY);
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.leftMargin = Math.max(0, newPos_x);
        layoutParams.topMargin = Math.max(0, newPos_y);
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;
        requestLayout();

        if (stickyMoveX || stickyMoveY) {
            virtualController.showSnapGroupOutline(this);
            if (newlyLocked) {
                virtualController.vibrate(KeyEvent.ACTION_DOWN);
            }
        } else {
            virtualController.clearGestureGroupOutline();
        }
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = Math.max(getMinimumResizeSizePx(), newHeight);
        layoutParams.width = Math.max(getMinimumResizeSizePx(), newWidth);
        requestLayout();
    }

    private void prepareGroupedResize() {
        resizeGroup = null;
        if (virtualController == null) {
            return;
        }

        List<keyBoardVirtualControllerElement> connected = virtualController.getConnectedGroup(this);
        if (connected.size() <= 1) {
            return;
        }

        List<GroupResizeSnapshot> snapshots = new ArrayList<>(connected.size());
        int minLeft = Integer.MAX_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int maxBottom = Integer.MIN_VALUE;

        for (keyBoardVirtualControllerElement element : connected) {
            if (!(element.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            int width = Math.max(1, params.width);
            int height = Math.max(1, params.height);
            snapshots.add(new GroupResizeSnapshot(
                    element, params.leftMargin, params.topMargin, width, height));
            minLeft = Math.min(minLeft, params.leftMargin);
            minTop = Math.min(minTop, params.topMargin);
            maxRight = Math.max(maxRight, params.leftMargin + width);
            maxBottom = Math.max(maxBottom, params.topMargin + height);
        }

        if (snapshots.size() <= 1) {
            return;
        }

        resizeGroup = snapshots;
        virtualController.showGestureGroupOutline(connected);
        resizeGroupOriginX = minLeft;
        resizeGroupOriginY = minTop;
        resizeGroupRight = maxRight;
        resizeGroupBottom = maxBottom;
    }

    private void resizeConnectedGroup(float rawX, float rawY, int localX, int localY) {
        if (resizeGroup == null || resizeGroup.size() <= 1) {
            resizeElement(
                    (int) position_pressed_x,
                    (int) position_pressed_y,
                    localX,
                    localY);
            return;
        }

        int groupWidth = Math.max(1, resizeGroupRight - resizeGroupOriginX);
        int groupHeight = Math.max(1, resizeGroupBottom - resizeGroupOriginY);
        float normalizedX = (rawX - resizeDownRawX) / groupWidth;
        float normalizedY = (rawY - resizeDownRawY) / groupHeight;
        float normalizedDelta = Math.abs(normalizedX) >= Math.abs(normalizedY)
                ? normalizedX : normalizedY;
        float scale = 1f + normalizedDelta;

        float minScale = 0.05f;
        for (GroupResizeSnapshot snapshot : resizeGroup) {
            int minimum = snapshot.element.getMinimumResizeSizePx();
            minScale = Math.max(minScale, minimum / (float) Math.max(1, snapshot.width));
            minScale = Math.max(minScale, minimum / (float) Math.max(1, snapshot.height));
        }

        float maxScale = Float.MAX_VALUE;
        if (getParent() instanceof View) {
            View parent = (View) getParent();
            if (parent.getWidth() > resizeGroupOriginX) {
                maxScale = Math.min(maxScale,
                        (parent.getWidth() - resizeGroupOriginX) / (float) groupWidth);
            }
            if (parent.getHeight() > resizeGroupOriginY) {
                maxScale = Math.min(maxScale,
                        (parent.getHeight() - resizeGroupOriginY) / (float) groupHeight);
            }
        }
        if (maxScale == Float.MAX_VALUE) {
            maxScale = Math.max(1f, scale);
        }
        scale = Math.max(minScale, Math.min(scale, maxScale));

        for (GroupResizeSnapshot snapshot : resizeGroup) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snapshot.element.getLayoutParams();
            int minimum = snapshot.element.getMinimumResizeSizePx();
            params.leftMargin = resizeGroupOriginX +
                    Math.round((snapshot.left - resizeGroupOriginX) * scale);
            params.topMargin = resizeGroupOriginY +
                    Math.round((snapshot.top - resizeGroupOriginY) * scale);
            params.width = Math.max(minimum, Math.round(snapshot.width * scale));
            params.height = Math.max(minimum, Math.round(snapshot.height * scale));
            params.rightMargin = 0;
            params.bottomMargin = 0;
            snapshot.element.requestLayout();
            snapshot.element.invalidate();
        }
        List<keyBoardVirtualControllerElement> outlined = new ArrayList<>(resizeGroup.size());
        for (GroupResizeSnapshot snapshot : resizeGroup) {
            outlined.add(snapshot.element);
        }
        virtualController.showGestureGroupOutline(outlined);
    }

    private void resetConnectedGroupSizeToDefault() {
        List<keyBoardVirtualControllerElement> group = virtualController.getConnectedGroup(this);
        if (group.size() <= 1) {
            resetSizeToDefault();
            return;
        }

        int originX = Integer.MAX_VALUE;
        int originY = Integer.MAX_VALUE;
        float scaleSum = 0f;
        int scaleSamples = 0;
        for (keyBoardVirtualControllerElement element : group) {
            if (!(element.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            originX = Math.min(originX, params.leftMargin);
            originY = Math.min(originY, params.topMargin);
            if (element.getDefaultWidth() > 0) {
                scaleSum += params.width / (float) element.getDefaultWidth();
                scaleSamples++;
            }
            if (element.getDefaultHeight() > 0) {
                scaleSum += params.height / (float) element.getDefaultHeight();
                scaleSamples++;
            }
        }
        if (originX == Integer.MAX_VALUE || originY == Integer.MAX_VALUE) {
            return;
        }

        float scale = scaleSamples == 0 ? 1f : scaleSum / scaleSamples;
        if (scale <= 0.05f) {
            scale = 1f;
        }

        for (keyBoardVirtualControllerElement element : group) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            int newLeft = originX + Math.round((params.leftMargin - originX) / scale);
            int newTop = originY + Math.round((params.topMargin - originY) / scale);
            element.setGeometry(
                    newLeft,
                    newTop,
                    element.getDefaultWidth(),
                    element.getDefaultHeight());
        }
    }

    private void clearGroupedResize() {
        resizeGroup = null;
        if (virtualController != null) {
            virtualController.clearGestureGroupOutline();
        }
    }

    protected void checkAndApplyResize() {
        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons &&
                !virtualController.isGroupMoveModeActive()) {
            View[] otherViews = new View[virtualController.getElements().size() - 1];
            int index = 0;
            for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
                if (element != this) {
                    otherViews[index++] = element;
                }
            }

            LayoutSnappingHelper.SnapResult snapResult = LayoutSnappingHelper.calculateSnappedPosition(
                    this, otherViews, lastMoveX, lastMoveY
            );

            if (snapResult.didResize) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
                layoutParams.width = snapResult.newWidth;
                layoutParams.height = snapResult.newHeight;
                virtualController.vibrate(KeyEvent.ACTION_DOWN);
                requestLayout();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);

        if (currentMode != Mode.Normal && shouldDrawBaseEditorOutline() &&
                (virtualController == null || !virtualController.isElementCoveredByGroupOutline(this))) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        super.onDraw(canvas);
    }

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.ResizeButtons)
            return configResizeColor;
        else if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.DisableEnableButtons)
            return enabled ? configSelectedColor: configDisabledColor;
        else
            return normalColor;
    }

    protected boolean shouldDrawBaseEditorOutline() {
        return true;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
        alertBuilder.setTitle("Configuration");
        CharSequence functions[] = new CharSequence[]{"Move", "Resize", "Cancel"};
        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        actionEnableMove();
                        break;
                    case 1:
                        actionEnableResize();
                        break;
                    default:
                        actionCancel();
                        break;
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private SidewaysStreamMode.LogicalPoint mapRaw(MotionEvent event) {
        Game game = Game.instance;
        if (game != null) {
            return game.mapRawToStreamCoordinates(event.getRawX(), event.getRawY());
        }
        return new SidewaysStreamMode.LogicalPoint(event.getRawX(), event.getRawY());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                SidewaysStreamMode.LogicalPoint rawPoint = mapRaw(event);
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons) {
                    moveDownRawX = rawPoint.x;
                    moveDownRawY = rawPoint.y;
                    moveGestureMoved = false;
                    stickyMoveX = false;
                    stickyMoveY = false;
                    if (virtualController.isGroupMoveModeActive()) {
                        if (virtualController.isInActiveMoveGroup(this)) {
                            virtualController.beginActiveGroupMove(rawPoint.x, rawPoint.y);
                            actionEnableMove();
                        }
                    } else {
                        actionEnableMove();
                    }
                } else if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.ResizeButtons) {
                    resizeDownRawX = rawPoint.x;
                    resizeDownRawY = rawPoint.y;
                    resizeGestureMoved = false;
                    prepareGroupedResize();
                    actionEnableResize();
                } else if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.DisableEnableButtons) {
                    actionDisableEnableButton();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                SidewaysStreamMode.LogicalPoint rawPoint = mapRaw(event);
                switch (currentMode) {
                    case Move:
                        if (virtualController.isGroupMoveModeActive()) {
                            if (virtualController.isInActiveMoveGroup(this)) {
                                virtualController.moveActiveGroup(rawPoint.x, rawPoint.y);
                            }
                        } else {
                            if (Math.abs(rawPoint.x - moveDownRawX) > touchSlop ||
                                    Math.abs(rawPoint.y - moveDownRawY) > touchSlop) {
                                moveGestureMoved = true;
                            }
                            moveElement(
                                    (int) position_pressed_x,
                                    (int) position_pressed_y,
                                    (int) event.getX(),
                                    (int) event.getY());
                        }
                        break;
                    case Resize:
                        if (Math.abs(rawPoint.x - resizeDownRawX) > touchSlop ||
                                Math.abs(rawPoint.y - resizeDownRawY) > touchSlop) {
                            resizeGestureMoved = true;
                        }
                        resizeConnectedGroup(
                                rawPoint.x,
                                rawPoint.y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    case Normal:
                        break;
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (currentMode == Mode.Move) {
                    if (virtualController.isGroupMoveModeActive()) {
                        virtualController.finishActiveGroupMove();
                    } else {
                        if (moveGestureMoved) {
                            checkAndApplyResize();
                        }
                        stickyMoveX = false;
                        stickyMoveY = false;
                        virtualController.clearGestureGroupOutline();
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || moveGestureMoved) {
                            lastMoveTapUpTime = 0;
                        } else {
                            long now = event.getEventTime();
                            if (lastMoveTapUpTime != 0 &&
                                    now - lastMoveTapUpTime <= ViewConfiguration.getDoubleTapTimeout()) {
                                if (virtualController.enterGroupMoveMode(this)) {
                                    virtualController.vibrate(KeyEvent.ACTION_DOWN);
                                }
                                lastMoveTapUpTime = 0;
                            } else {
                                lastMoveTapUpTime = now;
                            }
                        }
                    }
                } else if (currentMode == Mode.Resize) {
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || resizeGestureMoved) {
                        lastResizeTapUpTime = 0;
                    } else {
                        long now = event.getEventTime();
                        if (lastResizeTapUpTime != 0 &&
                                now - lastResizeTapUpTime <= ViewConfiguration.getDoubleTapTimeout()) {
                            resetConnectedGroupSizeToDefault();
                            lastResizeTapUpTime = 0;
                            virtualController.vibrate(KeyEvent.ACTION_DOWN);
                            KeyBoardControllerConfigurationLoader.saveProfile(virtualController, getContext());
                        } else {
                            lastResizeTapUpTime = now;
                        }
                    }
                }
                clearGroupedResize();
                actionCancel();
                return true;
            }
            default:
                break;
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            // Intentionally quiet unless debug logging is re-enabled.
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;
        invalidate();
    }

    public void setOpacity(int opacity) {
        int hexOpacity = opacity * 255 / 100;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);
        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }

    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("ENABLED", enabled);
        configuration.put("HIDDEN", hidden);
        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = Math.max(0, configuration.getInt("LEFT"));
        layoutParams.topMargin = Math.max(0, configuration.getInt("TOP"));
        layoutParams.width = Math.max(getMinimumResizeSizePx(), configuration.getInt("WIDTH"));
        layoutParams.height = Math.max(getMinimumResizeSizePx(), configuration.getInt("HEIGHT"));
        enabled = configuration.getBoolean("ENABLED");
        hidden = configuration.optBoolean("HIDDEN", false);

        if (virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons) {
            setVisibility(!hidden && enabled ? VISIBLE : GONE);
        } else {
            setVisibility(!hidden ? VISIBLE : GONE);
        }
        requestLayout();
    }

    protected void actionDisableEnableButton() {
        enabled = !enabled;
        if (!hidden && virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons) {
            setVisibility(enabled ? VISIBLE : GONE);
        }
        invalidate();
    }
}
