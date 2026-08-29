package com.limelight.binding.input.virtual_controller;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

/**
 * Shared layout helpers for OSC configuration.
 *
 * <p>This intentionally has no dependency on Diana's foldable/cover-screen code so the
 * useful controller-layout behaviour can be carried on top of the newer Artemis base.</p>
 */
public final class SnapHelper {
    private static final float SNAP_THRESHOLD_DP = 10.0f;
    private static final float GRID_SIZE_DP = 24.0f;

    public enum ButtonSubset {
        FACE,
        TRIGGERS,
        SHOULDERS,
        STICKS,
        STICK_BUTTONS,
        MENU,
        NONE
    }

    private SnapHelper() {
    }

    public static ButtonSubset getButtonSubset(int elementId) {
        switch (elementId) {
            case VirtualControllerElement.EID_A:
            case VirtualControllerElement.EID_B:
            case VirtualControllerElement.EID_X:
            case VirtualControllerElement.EID_Y:
                return ButtonSubset.FACE;

            case VirtualControllerElement.EID_LT:
            case VirtualControllerElement.EID_RT:
                return ButtonSubset.TRIGGERS;

            case VirtualControllerElement.EID_LB:
            case VirtualControllerElement.EID_RB:
                return ButtonSubset.SHOULDERS;

            case VirtualControllerElement.EID_LS:
            case VirtualControllerElement.EID_RS:
                return ButtonSubset.STICKS;

            case VirtualControllerElement.EID_LSB:
            case VirtualControllerElement.EID_RSB:
                return ButtonSubset.STICK_BUTTONS;

            case VirtualControllerElement.EID_BACK:
            case VirtualControllerElement.EID_START:
                return ButtonSubset.MENU;

            default:
                return ButtonSubset.NONE;
        }
    }

    /**
     * Applies edge, grid, and neighbouring-control snapping to an OSC element.
     * Returns {left, top} in the controller container's coordinate space.
     */
    public static int[] applySnapping(VirtualControllerElement moving,
                                      int proposedLeft,
                                      int proposedTop,
                                      int width,
                                      int height,
                                      VirtualController controller) {
        DisplayMetrics metrics = controller.getDisplayMetrics();
        float density = metrics.density > 0 ? metrics.density : 1.0f;
        int threshold = Math.max(4, Math.round(SNAP_THRESHOLD_DP * density));
        int gridSize = Math.max(8, Math.round(GRID_SIZE_DP * density));

        int containerWidth = controller.getLayoutWidth();
        int containerHeight = controller.getLayoutHeight();
        if (containerWidth <= 0) {
            containerWidth = metrics.widthPixels;
        }
        if (containerHeight <= 0) {
            containerHeight = metrics.heightPixels;
        }

        int left = clamp(proposedLeft, 0, Math.max(0, containerWidth - width));
        int top = clamp(proposedTop, 0, Math.max(0, containerHeight - height));

        // Screen/container edges.
        if (Math.abs(left) <= threshold) {
            left = 0;
        }
        int rightGap = containerWidth - (left + width);
        if (Math.abs(rightGap) <= threshold) {
            left = Math.max(0, containerWidth - width);
        }
        if (Math.abs(top) <= threshold) {
            top = 0;
        }
        int bottomGap = containerHeight - (top + height);
        if (Math.abs(bottomGap) <= threshold) {
            top = Math.max(0, containerHeight - height);
        }

        // Regular layout grid. Only snap when already close, so movement stays natural.
        int gridLeft = Math.round((float) left / gridSize) * gridSize;
        if (Math.abs(gridLeft - left) <= threshold) {
            left = gridLeft;
        }
        int gridTop = Math.round((float) top / gridSize) * gridSize;
        if (Math.abs(gridTop - top) <= threshold) {
            top = gridTop;
        }

        // Nearby OSC controls: align left/right/centres independently on each axis.
        List<VirtualControllerElement> elements = controller.getElements();
        int movingRight = left + width;
        int movingCenterX = left + width / 2;
        int movingBottom = top + height;
        int movingCenterY = top + height / 2;

        for (VirtualControllerElement other : elements) {
            if (other == moving || other.getVisibility() == View.GONE || other.getLayoutParams() == null) {
                continue;
            }

            FrameLayout.LayoutParams otherParams = (FrameLayout.LayoutParams) other.getLayoutParams();
            int otherLeft = otherParams.leftMargin;
            int otherTop = otherParams.topMargin;
            int otherWidth = other.getWidth() > 0 ? other.getWidth() : otherParams.width;
            int otherHeight = other.getHeight() > 0 ? other.getHeight() : otherParams.height;
            int otherRight = otherLeft + otherWidth;
            int otherBottom = otherTop + otherHeight;
            int otherCenterX = otherLeft + otherWidth / 2;
            int otherCenterY = otherTop + otherHeight / 2;

            Integer snappedX = firstCloseAlignment(threshold,
                    new int[]{left, movingRight, movingCenterX},
                    new int[]{otherLeft, otherRight, otherCenterX},
                    width);
            if (snappedX != null) {
                left = snappedX;
                movingRight = left + width;
                movingCenterX = left + width / 2;
            }

            Integer snappedY = firstCloseAlignment(threshold,
                    new int[]{top, movingBottom, movingCenterY},
                    new int[]{otherTop, otherBottom, otherCenterY},
                    height);
            if (snappedY != null) {
                top = snappedY;
                movingBottom = top + height;
                movingCenterY = top + height / 2;
            }
        }

        left = clamp(left, 0, Math.max(0, containerWidth - width));
        top = clamp(top, 0, Math.max(0, containerHeight - height));
        return new int[]{left, top};
    }

    private static Integer firstCloseAlignment(int threshold,
                                               int[] movingAnchors,
                                               int[] targetAnchors,
                                               int movingSize) {
        for (int movingIndex = 0; movingIndex < movingAnchors.length; movingIndex++) {
            for (int target : targetAnchors) {
                if (Math.abs(movingAnchors[movingIndex] - target) <= threshold) {
                    if (movingIndex == 0) {
                        return target;
                    } else if (movingIndex == 1) {
                        return target - movingSize;
                    } else {
                        return target - movingSize / 2;
                    }
                }
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
