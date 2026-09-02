package com.limelight;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@Config(sdk = 33)
@RunWith(RobolectricTestRunner.class)
public class ProfileEditorPreferenceValuesTest {
    @Test
    public void gsonNumericAndListValuesRemainReadable() {
        assertEquals(1.5f, ProfileEditorPreferenceValues.getFloat(1.5d, -1f), 0.0001f);

        Set<String> expected = new HashSet<>(Arrays.asList("alpha", "beta"));
        assertEquals(expected, ProfileEditorPreferenceValues.getStringSet(
                Arrays.asList("alpha", "beta"), null));
    }

    @Test
    public void rotationStatePreservesDoubleAndListBackedStringSet() {
        Map<String, Object> values = new HashMap<>();
        values.put("zoom", 1.75d);
        values.put("items", Arrays.asList("alpha", "beta"));

        Bundle encoded = ProfileEditorPreferenceValues.encodeState(values);
        Map<String, Object> decoded = ProfileEditorPreferenceValues.decodeState(encoded);

        assertEquals(1.75d, (Double) decoded.get("zoom"), 0.0001d);
        assertEquals(new HashSet<>(Arrays.asList("alpha", "beta")), decoded.get("items"));
    }

    @Test
    public void invalidMixedIterableFallsBackAndIsNotEncodedAsStringSet() {
        Set<String> fallback = new HashSet<>(Arrays.asList("fallback"));
        assertEquals(fallback, ProfileEditorPreferenceValues.getStringSet(
                Arrays.asList("alpha", 7), fallback));

        Map<String, Object> values = new HashMap<>();
        values.put("mixed", Arrays.asList("alpha", 7));
        Map<String, Object> decoded = ProfileEditorPreferenceValues.decodeState(
                ProfileEditorPreferenceValues.encodeState(values));
        assertFalse(decoded.containsKey("mixed"));
    }
}
