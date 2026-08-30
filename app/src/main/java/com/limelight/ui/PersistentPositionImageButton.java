package com.limelight.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/** ImageButton that preserves its dragged position across Game sessions. */
public class PersistentPositionImageButton extends ImageButton {
    public PersistentPositionImageButton(Context context) { super(context); }
    public PersistentPositionImageButton(Context context, AttributeSet attrs) { super(context, attrs); }
    public PersistentPositionImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Session-wide reset is performed once by Game.onCreate(). A View attach is not a
        // session boundary and may happen repeatedly during one stream.
        post(() -> FloatingControlPositionStore.restore(this, null));
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The stream Activity can survive an orientation change. Re-apply the separately saved
        // portrait/landscape position after the parent has been laid out in the new orientation.
        post(() -> FloatingControlPositionStore.restore(this, null));
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == View.VISIBLE) {
            post(() -> FloatingControlPositionStore.restore(this, null));
        }
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        if (listener == null) {
            super.setOnTouchListener(null);
            return;
        }
        super.setOnTouchListener((view, event) -> {
            boolean handled = listener.onTouch(view, event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                FloatingControlPositionStore.save(this, null);
            }
            return handled;
        });
    }
}
