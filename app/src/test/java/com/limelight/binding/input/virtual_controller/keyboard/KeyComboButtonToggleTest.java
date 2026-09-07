package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class KeyComboButtonToggleTest {
    @Test
    public void enabledLongPressLatchesThenSecondHoldReleasesWithoutRelatching() {
        KeyBoardController controller = controller();
        KeyComboButton button = button(controller, true);

        touch(button, MotionEvent.ACTION_DOWN);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS);
        touch(button, MotionEvent.ACTION_UP);

        assertTrue(button.isSticky());

        touch(button, MotionEvent.ACTION_DOWN);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS);
        touch(button, MotionEvent.ACTION_UP);

        assertFalse(button.isSticky());
        ArgumentCaptor<KeyEvent> events = ArgumentCaptor.forClass(KeyEvent.class);
        verify(controller, times(2)).sendKeyEvent(events.capture());
        List<KeyEvent> values = events.getAllValues();
        assertTrue(values.get(0).getAction() == KeyEvent.ACTION_DOWN);
        assertTrue(values.get(1).getAction() == KeyEvent.ACTION_UP);
    }

    @Test
    public void disabledLongPressRetainsOrdinaryPressAndReleaseBehavior() {
        KeyBoardController controller = controller();
        KeyComboButton button = button(controller, false);

        touch(button, MotionEvent.ACTION_DOWN);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS);
        touch(button, MotionEvent.ACTION_UP);

        assertFalse(button.isSticky());
        verify(controller, times(2)).sendKeyEvent(org.mockito.ArgumentMatchers.any(KeyEvent.class));
    }

    private static KeyBoardController controller() {
        KeyBoardController controller = mock(KeyBoardController.class);
        when(controller.getControllerMode()).thenReturn(KeyBoardController.ControllerMode.Active);
        when(controller.getHandler()).thenReturn(new Handler(Looper.getMainLooper()));
        return controller;
    }

    private static KeyComboButton button(KeyBoardController controller, boolean enabled) {
        Context context = ApplicationProvider.getApplicationContext();
        return new KeyComboButton(controller, context, new KeyComboManager.Definition(
                "test", "F5", new int[0], new int[]{KeyEvent.KEYCODE_F5}, enabled));
    }

    private static void touch(KeyComboButton button, int action) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, 5f, 5f, 0);
        button.onTouchEvent(event);
        event.recycle();
    }
}
