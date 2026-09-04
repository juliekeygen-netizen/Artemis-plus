package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.KeyEvent;

/** Wires non-digital virtual controls to host input with detach-safe key ownership tracking. */
final class KeyboardControlInputBinding {
    private KeyboardControlInputBinding() {
    }

    static void bindDigitalPad(KeyboardDigitalPadButton button,
                               final KeyBoardController controller,
                               final int keyCodeLeft,
                               final int keyCodeRight,
                               final int keyCodeUp,
                               final int keyCodeDown) {
        final KeyboardInputReleaseTracker tracker = createTracker(controller, 3);
        button.addDigitalPadListener(new KeyboardDigitalPadButton.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                tracker.send(keyCodeLeft,
                        (direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_LEFT) != 0);
                tracker.send(keyCodeRight,
                        (direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT) != 0);
                tracker.send(keyCodeUp,
                        (direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP) != 0);
                tracker.send(keyCodeDown,
                        (direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_DOWN) != 0);
            }
        });
        tracker.bindTo(button);
    }

    static void bindAnalogStick(KeyBoardAnalogStickButton analogStick,
                                final KeyBoardController controller) {
        final KeyboardInputReleaseTracker tracker = createTracker(controller, 2);
        analogStick.setListener(new KeyBoardAnalogStickButton.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                tracker.send(code, isPress);
            }
        });
        tracker.bindTo(analogStick);
    }

    static void bindFreeAnalogStick(KeyBoardAnalogStickButtonFree analogStick,
                                    final KeyBoardController controller) {
        final KeyboardInputReleaseTracker tracker = createTracker(controller, 2);
        analogStick.setListener(new KeyBoardAnalogStickButtonFree.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                tracker.send(code, isPress);
            }
        });
        tracker.bindTo(analogStick);
    }

    static void bindTouchButton(KeyBoardTouchPadButton button,
                                final int keyShort,
                                final int type,
                                final KeyBoardController controller) {
        final KeyboardInputReleaseTracker tracker = createTracker(controller, type);
        button.addDigitalButtonListener(new KeyBoardTouchPadButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                tracker.send(keyShort == 9 ? 3 : 1, true);
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onMove(int x, int y) {
                controller.sendMouseMove(x, y);
            }

            @Override
            public void onRelease() {
                tracker.send(keyShort == 9 ? 3 : 1, false);
            }
        });
        tracker.bindTo(button);
    }

    private static KeyboardInputReleaseTracker createTracker(final KeyBoardController controller,
                                                              final int source) {
        return new KeyboardInputReleaseTracker(new KeyboardInputReleaseTracker.Sender() {
            @Override
            public void send(int keyCode, boolean pressed) {
                KeyEvent keyEvent = new KeyEvent(
                        pressed ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                        keyCode);
                keyEvent.setSource(source);
                controller.sendKeyEvent(keyEvent);
            }
        });
    }
}
