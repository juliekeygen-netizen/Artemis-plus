package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

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

    private static final int EDIT_MOVE_COLOR = 0xF0FF0000;
    private static final int EDIT_RESIZE_COLOR = 0xF0FF00FF;
    private static final int EDIT_ENABLED_COLOR = 0xF000FF00;
    private static final int EDIT_DISABLED_COLOR = 0xF0AAAAAA;

    private final int primaryIconRes;
    private final int alternateIconRes;
    private final int minimumSizePx;
    private final Paint editorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int displayedIconRes;

    ArtemisActionButton(KeyBoardController controller,
                        String elementId,
                        Context context,
                        int primaryIconRes,
                        int alternateIconRes) {
        super(controller, elementId, 1, context);
        this.primaryIconRes = primaryIconRes;
        this.alternateIconRes = alternateIconRes;
        this.displayedIconRes = primaryIconRes;
        this.minimumSizePx = Math.max(1,
                Math.round(MIN_SIZE_DP * context.getResources().getDisplayMetrics().density));

        // This is the exact shell used by Artemis's native floating Menu and Zoom/Pan buttons:
        // #CC000000 oval, 2dp #AAFFFFFF stroke, and the same white ripple feedback.
        setBackgroundResource(R.drawable.floating_menu_button);
    }

    void setAlternateIcon(boolean alternate) {
        int newIcon = alternate && alternateIconRes != -1 ? alternateIconRes : primaryIconRes;
        if (displayedIconRes != newIcon) {
            displayedIconRes = newIcon;
            invalidate();
        }
    }

    int getDisplayedIconResForTest() {
        return displayedIconRes;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        drawActionIcon(canvas);
        drawEditorStateRing(canvas);
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

    /**
     * The old text-style keyboard buttons changed their outline color while editing. Since action
     * controls now reuse the native floating-button shell, draw the same editor-state cue over it.
     */
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
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        // Existing installs may have rectangular text-action geometry saved from the pre-icon
        // implementation. Migrate that geometry to a square once it is loaded.
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
