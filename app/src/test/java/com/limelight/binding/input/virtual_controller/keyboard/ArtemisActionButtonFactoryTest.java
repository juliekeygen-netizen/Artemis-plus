package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.ArtemisAction;
import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class ArtemisActionButtonFactoryTest {

    @Test
    public void normalDigitalButtonsKeepSlideActivationEnabledByDefault() {
        Context context = ApplicationProvider.getApplicationContext();
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(null, "normal", 1, context);

        assertTrue(button.isSlideActivationEnabled());
    }

    @Test
    public void localArtemisActionsRequireDirectPresses() {
        Context context = ApplicationProvider.getApplicationContext();
        KeyBoardDigitalButton button = ArtemisActionButtonFactory.createButton(
                ArtemisAction.ROTATE_SCREEN,
                null,
                context);

        assertFalse(button.isSlideActivationEnabled());
        assertTrue(button instanceof ArtemisActionButton);
    }

    @Test
    public void customButtonsToggleUsesStateAwareEyeIcons() {
        Context context = ApplicationProvider.getApplicationContext();
        ArtemisActionButton button = (ArtemisActionButton) ArtemisActionButtonFactory.createButton(
                ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER,
                null,
                context);

        assertEquals(R.drawable.ic_artemis_action_eye_closed, button.getDisplayedIconResForTest());
        button.setAlternateIcon(true);
        assertEquals(R.drawable.ic_artemis_action_eye, button.getDisplayedIconResForTest());
        button.setAlternateIcon(false);
        assertEquals(R.drawable.ic_artemis_action_eye_closed, button.getDisplayedIconResForTest());
    }

    @Test
    public void detachingHeldStickyButtonReleasesPressAndCancelsLongClick() {
        Context context = ApplicationProvider.getApplicationContext();
        KeyBoardController controller = mock(KeyBoardController.class);
        Handler handler = new Handler(Looper.getMainLooper());
        when(controller.getHandler()).thenReturn(handler);

        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, "held", 1, context);
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger longClicks = new AtomicInteger();
        button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
            }

            @Override
            public void onLongClick() {
                longClicks.incrementAndGet();
            }

            @Override
            public void onRelease() {
                releases.incrementAndGet();
            }
        });

        MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0);
        button.onElementTouchEvent(down);
        down.recycle();
        button.setSticky(true);

        button.onDetachedFromWindow();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500L, TimeUnit.MILLISECONDS);

        assertEquals(1, releases.get());
        assertEquals(0, longClicks.get());
        assertFalse(button.isPressed());
        assertFalse(button.isSticky());
    }
}