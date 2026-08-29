package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.ArtemisAction;

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
    }
}
