package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tracks host keys currently owned by one virtual control and releases them on detach. */
final class KeyboardInputReleaseTracker {
    interface Sender {
        void send(int keyCode, boolean pressed);
    }

    private final Sender sender;
    private final Set<Integer> pressedKeys = new LinkedHashSet<>();

    KeyboardInputReleaseTracker(Sender sender) {
        this.sender = sender;
    }

    void send(int keyCode, boolean pressed) {
        sender.send(keyCode, pressed);
        if (pressed) {
            pressedKeys.add(keyCode);
        } else {
            pressedKeys.remove(keyCode);
        }
    }

    void bindTo(View view) {
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                releaseAll();
            }
        });
    }

    void releaseAll() {
        if (pressedKeys.isEmpty()) {
            return;
        }

        List<Integer> keys = new ArrayList<>(pressedKeys);
        for (int i = keys.size() - 1; i >= 0; i--) {
            int keyCode = keys.get(i);
            sender.send(keyCode, false);
            pressedKeys.remove(keyCode);
        }
    }

    int pressedKeyCountForTest() {
        return pressedKeys.size();
    }
}
