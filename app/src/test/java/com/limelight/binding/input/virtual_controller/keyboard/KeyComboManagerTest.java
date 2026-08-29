package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.json.JSONObject;
import org.junit.Test;

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
}
