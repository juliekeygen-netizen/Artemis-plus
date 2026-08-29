package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class KeyComboManagerTest {
    @Test
    public void singleKeyWithoutModifiersRoundTrips() throws Exception {
        KeyComboManager.Definition original = new KeyComboManager.Definition(
                "plain-f5",
                "F5",
                new int[0],
                new int[]{KeyEvent.KEYCODE_F5});

        JSONObject serialized = original.toJson();
        KeyComboManager.Definition restored = KeyComboManager.Definition.fromJson(serialized);

        assertEquals("F5", restored.name);
        assertArrayEquals(new int[0], restored.modifiers);
        assertArrayEquals(new int[]{KeyEvent.KEYCODE_F5}, restored.keys);
    }

    @Test
    public void regularKeyOrderAndOptionalModifiersArePreserved() throws Exception {
        KeyComboManager.Definition original = new KeyComboManager.Definition(
                "ordered",
                "Forward sequence",
                new int[]{KeyEvent.KEYCODE_ALT_LEFT},
                new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER});

        KeyComboManager.Definition restored =
                KeyComboManager.Definition.fromJson(original.toJson());

        assertArrayEquals(new int[]{KeyEvent.KEYCODE_ALT_LEFT}, restored.modifiers);
        assertArrayEquals(
                new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER},
                restored.keys);
    }

    @Test
    public void semanticSearchFindsSymbolOnlyArrowKeys() {
        assertTrue(KeyComboManager.keySearchMatches("←", KeyEvent.KEYCODE_DPAD_LEFT, "left"));
        assertTrue(KeyComboManager.keySearchMatches("→", KeyEvent.KEYCODE_DPAD_RIGHT, "right arrow"));
        assertTrue(KeyComboManager.keySearchMatches("↑", KeyEvent.KEYCODE_DPAD_UP, "up"));
        assertTrue(KeyComboManager.keySearchMatches("↓", KeyEvent.KEYCODE_DPAD_DOWN, "down arrow"));
        assertFalse(KeyComboManager.keySearchMatches("←", KeyEvent.KEYCODE_DPAD_LEFT, "right"));
    }

    @Test
    public void semanticSearchFindsBackspaceAndCommonAliases() {
        assertTrue(KeyComboManager.keySearchMatches("⌫", KeyEvent.KEYCODE_DEL, "backspace"));
        assertTrue(KeyComboManager.keySearchMatches("⌫", KeyEvent.KEYCODE_DEL, "bksp"));
        assertTrue(KeyComboManager.keySearchMatches("Esc", KeyEvent.KEYCODE_ESCAPE, "escape"));
        assertTrue(KeyComboManager.keySearchMatches("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, "page down"));
    }

    @Test
    public void emptyManagedProfileIsBlankButLegacyProfileIsNot() {
        assertTrue(KeyBoardControllerConfigurationLoader.shouldTreatManagedProfileAsBlank(
                "ArtemisKeyboardProfile_abc123", false));
        assertFalse(KeyBoardControllerConfigurationLoader.shouldTreatManagedProfileAsBlank(
                "ArtemisKeyboardProfile_abc123", true));
        assertFalse(KeyBoardControllerConfigurationLoader.shouldTreatManagedProfileAsBlank(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE, false));
        assertFalse(KeyBoardControllerConfigurationLoader.shouldTreatManagedProfileAsBlank(null, false));
    }

    @Test
    public void longLabelsGrowBubbleWithoutShrinkingShortLabelsBelowSquare() {
        Context context = ApplicationProvider.getApplicationContext();
        int baseSize = 80;

        assertEquals(baseSize, KeyBoardDigitalButton.minimumWidthForText(context, "A", baseSize));
        assertTrue(KeyBoardDigitalButton.minimumWidthForText(
                context,
                "A deliberately long display label",
                baseSize) > baseSize);
    }
}
