package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeyBoardEditorChromeLayoutTest {
    @Test
    public void configureButtonUsesShortEdgeAndDoesNotGrowAfterRotation() {
        int landscape = KeyBoardController.editorChromeButtonSize(2376, 1080);
        int portrait = KeyBoardController.editorChromeButtonSize(1080, 2376);

        assertEquals(landscape, portrait);
        assertEquals(75, landscape);
    }

    @Test
    public void toolbarCentersAgainstCurrentContainerWidth() {
        assertEquals(688, KeyBoardController.centeredEditorStart(2376, 1000));
        assertEquals(40, KeyBoardController.centeredEditorStart(1080, 1000));
        assertEquals(0, KeyBoardController.centeredEditorStart(800, 1000));
    }
}
