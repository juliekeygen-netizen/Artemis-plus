/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardDigitalButton extends keyBoardVirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 300;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };
    private boolean callbackPressActive;

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private final Rect textBounds = new Rect();

    /** Minimum bubble width that keeps text readable while never shrinking below a square. */
    static int minimumWidthForText(Context context, String value, int height) {
        int safeHeight = Math.max(20, height);
        if (value == null || value.isEmpty()) {
            return safeHeight;
        }

        Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float textSize = safeHeight * 0.25f;
        measurePaint.setTextSize(textSize);

        float density = context.getResources().getDisplayMetrics().density;
        float horizontalPadding = Math.max(8f * density, safeHeight * 0.20f);

        // Paint.measureText() is the authoritative measurement on a real device. Some test/runtime
        // environments can return zero or otherwise unusable font metrics, though, so keep a
        // conservative deterministic fallback based on code-point count. Using the larger value
        // also avoids clipping unusually narrow-reported fonts while preserving the square minimum
        // for ordinary short labels.
        float measuredWidth = Math.max(0f, measurePaint.measureText(value));
        int codePointCount = value.codePointCount(0, value.length());
        float fallbackWidth = codePointCount * safeHeight * 0.14f;
        float textWidth = Math.max(measuredWidth, fallbackWidth);

        int required = (int) Math.ceil(textWidth + horizontalPadding * 2f);
        return Math.max(safeHeight, required);
    }

    /** Baseline that centers the glyphs that are actually drawn, not the font's full line box. */
    static float visualTextBaseline(Paint paint, Rect reusableBounds, String value, float centerY) {
        if (value == null || value.isEmpty()) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            return centerY - (metrics.ascent + metrics.descent) / 2f;
        }
        reusableBounds.setEmpty();
        paint.getTextBounds(value, 0, value.length(), reusableBounds);
        if (reusableBounds.isEmpty()) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            return centerY - (metrics.ascent + metrics.descent) / 2f;
        }
        return centerY - (reusableBounds.top + reusableBounds.bottom) / 2f;
    }

    private int layer;
    private KeyBoardDigitalButton movingButton = null;
    private boolean sticky = false;
    private boolean slideActivationEnabled = true;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, KeyBoardDigitalButton movingButton) {
        // Some buttons (notably Artemis-local actions) must only fire from a deliberate direct
        // press. Never allow a slide gesture to enter or leave one of those buttons.
        if (!slideActivationEnabled || !movingButton.slideActivationEnabled) {
            return false;
        }

        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        if (!slideActivationEnabled) {
            return;
        }
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof KeyBoardDigitalButton) {
                ((KeyBoardDigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardDigitalButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean isSticky() {
        return this.sticky;
    }

    public void setSlideActivationEnabled(boolean enabled) {
        slideActivationEnabled = enabled;
    }

    public boolean isSlideActivationEnabled() {
        return slideActivationEnabled;
    }

    @Override
    protected int getDefaultStrokeWidth() {
        return Math.max(1, Math.round(2f * getResources().getDisplayMetrics().density));
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        // Text size follows the short edge, so widening a long-label bubble does not recursively
        // make the text larger and wider again.
        paint.setTextSize(getPercent(Math.min(getWidth(), getHeight()), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        boolean shouldSetPressed = isPressed() || isSticky();

        paint.setColor(shouldSetPressed ? pressedColor : getDefaultColor());

        paint.setStyle(shouldSetPressed ? Paint.Style.FILL_AND_STROKE: Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        if(PreferenceConfiguration.readPreferences(getContext()).enableKeyboardSquare){
            canvas.drawRect(rect,paint);
        }else{
            canvas.drawOval(rect, paint);
        }

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            float baseline = visualTextBaseline(paint, textBounds, text, getHeight() / 2f);
            canvas.drawText(text, getWidth() / 2f, baseline, paint);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        callbackPressActive = true;
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // Sticky buttons deliberately keep their host-side press active after finger-up.
        if (!sticky) {
            callbackPressActive = false;
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private boolean switchDown;

    private boolean enableSwitchDown;

    public void setEnableSwitchDown(boolean enableSwitchDown) {
        this.enableSwitchDown = enableSwitchDown;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                onClickCallback();

                invalidate();
                if(enableSwitchDown){
                    switchDown=!switchDown;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if(enableSwitchDown&&switchDown){
                    return true;
                }
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (virtualController != null) {
            virtualController.getHandler().removeCallbacks(longClickRunnable);
        }

        // A profile/layout refresh can detach this View without delivering ACTION_UP/CANCEL. Only
        // synthesize a release if this control actually emitted a listener press, so editor-only
        // touches cannot release an unrelated host key that happens to use the same key code.
        if (callbackPressActive) {
            setPressed(false);
            sticky = false;
            switchDown = false;
            movingButton = null;
            onReleaseCallback();
        }

        super.onDetachedFromWindow();
    }
}