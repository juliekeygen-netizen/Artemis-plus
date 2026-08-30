package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.limelight.ArtemisAction;
import com.limelight.ArtemisActionStateReader;
import com.limelight.Game;
import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Local Artemis action control styled like the native 36dp floating Menu/Zoom controls.
 *
 * The native ripple/black-circle/white-ring drawable is reused as the View background. The action
 * glyph is drawn with proportional 6/36 padding, and resize is locked to a square so neither the
 * shell nor the icon can be stretched on only one axis.
 */
final class ArtemisActionButton extends KeyBoardDigitalButton {
    static final float DEFAULT_SIZE_DP = 36f;
    private static final float MIN_SIZE_DP = 24f;
    private static final float ICON_PADDING_RATIO = 6f / 36f;
    private static final float EDITOR_RING_RATIO = 2f / 36f;
    private static final long STATE_REFRESH_MS = 400L;

    private static final int EDIT_MOVE_COLOR = 0xF0FF0000;
    private static final int EDIT_RESIZE_COLOR = 0xF0FF00FF;
    private static final int EDIT_ENABLED_COLOR = 0xF000FF00;
    private static final int EDIT_DISABLED_COLOR = 0xF0AAAAAA;
    // Match the active native Zoom/Pan control's #FF4CAF50 outline exactly.
    private static final int ACTIVE_TOGGLE_COLOR = 0xFF4CAF50;

    private final ArtemisAction action;
    private final int primaryIconRes;
    private final int alternateIconRes;
    private final int minimumSizePx;
    private final Paint editorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int displayedIconRes;
    private Boolean explicitToggleState;

    ArtemisActionButton(KeyBoardController controller,
                        String elementId,
                        Context context,
                        ArtemisAction action,
                        int primaryIconRes,
                        int alternateIconRes) {
        super(controller, elementId, 1, context);
        this.action = action;
        this.primaryIconRes = primaryIconRes;
        this.alternateIconRes = alternateIconRes;
        this.displayedIconRes = primaryIconRes;
        this.minimumSizePx = Math.max(1,
                Math.round(MIN_SIZE_DP * context.getResources().getDisplayMetrics().density));

        // Exact shell used by Artemis's native floating Menu and Zoom/Pan buttons.
        setBackgroundResource(R.drawable.floating_menu_button);
    }

    void setAlternateIcon(boolean alternate) {
        int newIcon = alternate && alternateIconRes != -1 ? alternateIconRes : primaryIconRes;
        if (displayedIconRes != newIcon) {
            displayedIconRes = newIcon;
            invalidate();
        }
    }

    void setExplicitToggleState(Boolean state) {
        if (explicitToggleState == null ? state != null : !explicitToggleState.equals(state)) {
            explicitToggleState = state;
            invalidate();
        }
    }

    int getDisplayedIconResForTest() {
        return displayedIconRes;
    }

    Boolean getExplicitToggleStateForTest() {
        return explicitToggleState;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        drawActionIcon(canvas);
        drawRuntimeToggleState(canvas);
        drawEditorStateRing(canvas);

        // Some states can also be changed by the native quick menu/floating controls. A very light
        // periodic redraw keeps these action indicators synchronized without wiring invasive
        // callbacks through the legacy Game class.
        if (virtualController != null &&
                virtualController.getControllerMode() == KeyBoardController.ControllerMode.Active &&
                isToggleCapableAction()) {
            postInvalidateDelayed(STATE_REFRESH_MS);
        }
    }

    private void drawActionIcon(Canvas canvas) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), displayedIconRes);
        if (drawable == null) {
            return;
        }

        int side = Math.min(getWidth(), getHeight());
        int padding = Math.max(1, Math.round(side * ICON_PADDING_RATIO));
        int left = (getWidth() - side) / 2 + padding;
        int top = (getHeight() - side) / 2 + padding;
        int right = (getWidth() + side) / 2 - padding;
        int bottom = (getHeight() + side) / 2 - padding;

        drawable = drawable.mutate();
        drawable.setBounds(left, top, right, bottom);
        drawable.draw(canvas);
    }

    private void drawRuntimeToggleState(Canvas canvas) {
        if (virtualController == null ||
                virtualController.getControllerMode() != KeyBoardController.ControllerMode.Active) {
            return;
        }

        Boolean active = explicitToggleState != null
                ? explicitToggleState
                : ArtemisActionStateReader.getToggleState(action, Game.instance);
        if (!Boolean.TRUE.equals(active)) {
            return;
        }

        float side = Math.min(getWidth(), getHeight());
        float stroke = Math.max(1f, side * EDITOR_RING_RATIO);
        float inset = stroke / 2f;
        editorPaint.setStyle(Paint.Style.STROKE);
        editorPaint.setStrokeWidth(stroke);
        editorPaint.setColor(ACTIVE_TOGGLE_COLOR);
        canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, editorPaint);
    }

    /** Editor colors always win over the runtime green on/off indicator. */
    private void drawEditorStateRing(Canvas canvas) {
        if (virtualController == null) {
            return;
        }

        int color;
        switch (virtualController.getControllerMode()) {
            case MoveButtons:
                color = EDIT_MOVE_COLOR;
                break;
            case ResizeButtons:
                color = EDIT_RESIZE_COLOR;
                break;
            case DisableEnableButtons:
                color = enabled ? EDIT_ENABLED_COLOR : EDIT_DISABLED_COLOR;
                break;
            case Active:
            default:
                return;
        }

        float side = Math.min(getWidth(), getHeight());
        float stroke = Math.max(1f, side * EDITOR_RING_RATIO);
        float inset = stroke / 2f;

        editorPaint.setStyle(Paint.Style.STROKE);
        editorPaint.setStrokeWidth(stroke);
        editorPaint.setColor(color);
        canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, editorPaint);
    }

    private boolean isToggleCapableAction() {
        switch (action) {
            case FULL_KEYBOARD:
            case TOGGLE_HUD:
            case TOGGLE_STATS_OVERLAY:
            case TOGGLE_FLOATING_MENU:
            case TOGGLE_ZOOM:
            case TOGGLE_VIRTUAL_CONTROLLER:
            case TOGGLE_KEYBOARD_CONTROLLER:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected boolean shouldDrawBaseEditorOutline() {
        // Action buttons already draw their own state-aware 2dp ring.
        return false;
    }

    @Override
    protected int getMinimumResizeSizePx() {
        return minimumSizePx;
    }

    @Override
    protected void resizeElement(int pressedX, int pressedY, int x, int y) {
        int deltaX = x - pressedX;
        int deltaY = y - pressedY;
        int dominantDelta = Math.abs(deltaX) >= Math.abs(deltaY) ? deltaX : deltaY;
        int startSide = Math.max(startSize_x, startSize_y);
        int newSide = Math.max(minimumSizePx, startSide + dominantDelta);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.width = newSide;
        layoutParams.height = newSide;
        requestLayout();
    }

    @Override
    protected void checkAndApplyResize() {
        super.checkAndApplyResize();
        enforceSquareGeometry();
    }

    @Override
    public void resetSizeToDefault() {
        super.resetSizeToDefault();
        enforceSquareGeometry();
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        enforceSquareGeometry();
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        enforceSquareGeometry();
        return super.getConfiguration();
    }

    private void enforceSquareGeometry() {
        if (!(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        int side = Math.max(minimumSizePx, Math.max(layoutParams.width, layoutParams.height));
        if (layoutParams.width != side || layoutParams.height != side) {
            layoutParams.width = side;
            layoutParams.height = side;
            requestLayout();
        }
    }
}
