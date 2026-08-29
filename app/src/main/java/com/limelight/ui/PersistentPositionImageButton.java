package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageButton;

/**
 * Drop-in ImageButton that preserves its dragged position between Game sessions.
 *
 * Game owns the actual drag/click listener. This class only wraps that listener so
 * the final position is saved on pointer release, then restores a normalized position
 * once the next layout is ready. Normalized coordinates survive resolution changes
 * much better than raw pixels.
 */
public class PersistentPositionImageButton extends ImageButton {
    private static final String PREFS = "ArtemisPlusFloatingControlPositions";

    public PersistentPositionImageButton(Context context) {
        super(context);
    }

    public PersistentPositionImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PersistentPositionImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::restorePosition);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == View.VISIBLE) {
            // These controls start as GONE in XML, so restoring again when they become visible
            // ensures their real measured width/height is available for accurate clamping.
            post(this::restorePosition);
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
                savePosition();
            }
            return handled;
        });
    }

    private void savePosition() {
        ViewParent parent = getParent();
        if (!(parent instanceof View) || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - getWidth());
        int maxY = Math.max(0, parentView.getHeight() - getHeight());
        if (maxX == 0 || maxY == 0) {
            return;
        }

        float normalizedX = clamp(getX() / maxX);
        float normalizedY = clamp(getY() / maxY);

        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key("saved"), true)
                .putFloat(key("x"), normalizedX)
                .putFloat(key("y"), normalizedY)
                .apply();
    }

    private void restorePosition() {
        ViewParent parent = getParent();
        if (!(parent instanceof View) || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        SharedPreferences preferences = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(key("saved"), false)) {
            return;
        }

        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - getWidth());
        int maxY = Math.max(0, parentView.getHeight() - getHeight());
        if (maxX == 0 || maxY == 0) {
            return;
        }

        setX(clamp(preferences.getFloat(key("x"), 0f)) * maxX);
        setY(clamp(preferences.getFloat(key("y"), 0f)) * maxY);
    }

    private String key(String axis) {
        String idName;
        try {
            idName = getResources().getResourceEntryName(getId());
        } catch (Exception ignored) {
            idName = Integer.toString(getId());
        }

        int orientation = getResources().getConfiguration().orientation;
        String orientationName = orientation == Configuration.ORIENTATION_PORTRAIT
                ? "portrait"
                : "landscape";
        return idName + "_" + orientationName + "_" + axis;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
