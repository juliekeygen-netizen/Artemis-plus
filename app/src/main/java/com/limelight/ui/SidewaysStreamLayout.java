package com.limelight.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.SidewaysStreamMode;

/**
 * Physical portrait root for the experimental sideways stream mode.
 *
 * The single child is measured as a landscape canvas (physical H x physical W), centered beyond
 * the portrait root's unrotated bounds, then rotated back into the physical viewport. Keeping this
 * in a ViewGroup makes the transform follow every real window/inset size change without Game having
 * to race Android configuration/layout callbacks.
 */
public class SidewaysStreamLayout extends FrameLayout {
    private String sidewaysMode = SidewaysStreamMode.MODE_OFF;

    public SidewaysStreamLayout(Context context) {
        super(context);
        init();
    }

    public SidewaysStreamLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SidewaysStreamLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setSidewaysMode(String mode) {
        String normalized = SidewaysStreamMode.normalize(mode);
        if (normalized.equals(sidewaysMode)) {
            return;
        }
        sidewaysMode = normalized;
        requestLayout();
        invalidate();
    }

    public String getSidewaysMode() {
        return sidewaysMode;
    }

    public boolean isSidewaysActive() {
        return SidewaysStreamMode.isActive(sidewaysMode);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int physicalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int physicalHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSize(physicalWidth, widthMeasureSpec),
                resolveSize(physicalHeight, heightMeasureSpec));

        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int childWidth = SidewaysStreamMode.logicalWidth(measuredWidth, measuredHeight, sidewaysMode);
        int childHeight = SidewaysStreamMode.logicalHeight(measuredWidth, measuredHeight, sidewaysMode);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, childWidth), MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(0, childHeight), MeasureSpec.EXACTLY);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.measure(childWidthSpec, childHeightSpec);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int physicalWidth = right - left;
        int physicalHeight = bottom - top;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int childLeft = (physicalWidth - childWidth) / 2;
            int childTop = (physicalHeight - childHeight) / 2;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            child.setPivotX(childWidth / 2f);
            child.setPivotY(childHeight / 2f);
            child.setRotation(SidewaysStreamMode.rotationDegrees(sidewaysMode));
        }
    }

    public SidewaysStreamMode.LogicalPoint mapRawToLogical(float rawX, float rawY) {
        if (!isSidewaysActive()) {
            return new SidewaysStreamMode.LogicalPoint(rawX, rawY);
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        return SidewaysStreamMode.physicalRawToLogical(
                rawX,
                rawY,
                location[0],
                location[1],
                getWidth(),
                getHeight(),
                sidewaysMode);
    }
}
