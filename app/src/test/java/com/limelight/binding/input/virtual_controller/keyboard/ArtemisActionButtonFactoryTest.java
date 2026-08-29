package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.ArtemisAction;
import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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
}
