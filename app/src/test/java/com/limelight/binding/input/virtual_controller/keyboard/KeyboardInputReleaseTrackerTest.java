package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class KeyboardInputReleaseTrackerTest {

    @Test
    public void releaseAllReleasesOnlyCurrentlyOwnedKeysInReverseOrderAndIsIdempotent() {
        List<String> events = new ArrayList<>();
        KeyboardInputReleaseTracker tracker = new KeyboardInputReleaseTracker(
                (keyCode, pressed) -> events.add((pressed ? "D" : "U") + keyCode));

        tracker.send(10, true);
        tracker.send(11, true);
        tracker.send(10, false);

        assertEquals(1, tracker.pressedKeyCountForTest());
        tracker.releaseAll();
        tracker.releaseAll();

        assertEquals(Arrays.asList("D10", "D11", "U10", "U11"), events);
        assertEquals(0, tracker.pressedKeyCountForTest());
    }

    @Test
    public void viewDetachReleasesOwnedInput() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        View control = new View(activity);

        List<String> events = new ArrayList<>();
        KeyboardInputReleaseTracker tracker = new KeyboardInputReleaseTracker(
                (keyCode, pressed) -> events.add((pressed ? "D" : "U") + keyCode));
        tracker.bindTo(control);

        root.addView(control);
        tracker.send(42, true);
        root.removeView(control);

        assertEquals(Arrays.asList("D42", "U42"), events);
        assertEquals(0, tracker.pressedKeyCountForTest());
    }
}
