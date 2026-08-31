package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.SidewaysStreamMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Verifies the physical-root transform contract without involving TextureView or MediaCodec. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SidewaysStreamLayoutTest {
    @Test
    public void sidewaysModesMeasureCenterAndRotateLogicalCanvas() {
        Context context = ApplicationProvider.getApplicationContext();
        SidewaysStreamLayout root = new SidewaysStreamLayout(context);
        View canvas = new View(context);
        root.addView(canvas, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.setSidewaysMode(SidewaysStreamMode.MODE_CW);
        measureAndLayout(root, 100, 200);

        assertTrue(root.isSidewaysActive());
        assertEquals(200, canvas.getWidth());
        assertEquals(100, canvas.getHeight());
        assertEquals(-50, canvas.getLeft());
        assertEquals(50, canvas.getTop());
        assertEquals(100f, canvas.getPivotX(), 0.001f);
        assertEquals(50f, canvas.getPivotY(), 0.001f);
        assertEquals(90f, canvas.getRotation(), 0.001f);

        root.setSidewaysMode(SidewaysStreamMode.MODE_CCW);
        measureAndLayout(root, 100, 200);
        assertEquals(-90f, canvas.getRotation(), 0.001f);
        assertEquals(-50, canvas.getLeft());
        assertEquals(50, canvas.getTop());
    }

    @Test
    public void disablingOrNormalizingModeRestoresPhysicalCanvas() {
        Context context = ApplicationProvider.getApplicationContext();
        SidewaysStreamLayout root = new SidewaysStreamLayout(context);
        View canvas = new View(context);
        root.addView(canvas, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.setSidewaysMode(SidewaysStreamMode.MODE_CW);
        measureAndLayout(root, 100, 200);

        root.setSidewaysMode("unsupported-mode");
        measureAndLayout(root, 100, 200);

        assertEquals(SidewaysStreamMode.MODE_OFF, root.getSidewaysMode());
        assertFalse(root.isSidewaysActive());
        assertEquals(100, canvas.getWidth());
        assertEquals(200, canvas.getHeight());
        assertEquals(0, canvas.getLeft());
        assertEquals(0, canvas.getTop());
        assertEquals(0f, canvas.getRotation(), 0.001f);
    }

    private static void measureAndLayout(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
}
