package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * User-created keyboard chord button.
 *
 * Runtime presses hold modifiers first, then the selected non-modifier keys. Releases happen in the
 * reverse order so modifier state cannot leak into subsequent input. In the Enable/Disable editor,
 * a normal tap still toggles the element while a long press opens the combo editor instead.
 */
final class KeyComboButton extends KeyBoardDigitalButton {
    private final int touchSlop;
    private final Runnable editorLongPressRunnable;

    private String comboId;
    private String displayName;
    private int[] modifierKeys;
    private int[] regularKeys;
    private boolean longPressToggleEnabled;
    private boolean chordDown;
    private boolean unlockingTogglePress;

    private float editorDownX;
    private float editorDownY;
    private boolean editorMoved;
    private boolean editorLongPressTriggered;

    KeyComboButton(KeyBoardController controller,
                   Context context,
                   KeyComboManager.Definition definition) {
        super(controller, definition.elementId(), 1, context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        editorLongPressRunnable = () -> {
            if (virtualController == null ||
                    virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons ||
                    editorMoved) {
                return;
            }
            editorLongPressTriggered = true;
            setPressed(false);
            invalidate();
            virtualController.vibrate(-1);
            KeyComboManager.showEditDialog(virtualController, getContext(), this);
        };

        setSlideActivationEnabled(false);
        updateDefinition(definition);

        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                if (longPressToggleEnabled && isSticky()) {
                    // A second press is always an unlock gesture. Suppress its later long-click so
                    // holding the finger again cannot accidentally re-lock the chord.
                    setSticky(false);
                    unlockingTogglePress = true;
                    invalidate();
                    return;
                }
                unlockingTogglePress = false;
                pressChord();
            }

            @Override
            public void onLongClick() {
                if (longPressToggleEnabled && chordDown && !unlockingTogglePress) {
                    setSticky(true);
                    invalidate();
                    if (virtualController != null) {
                        virtualController.vibrate(-1);
                    }
                }
            }

            @Override
            public void onRelease() {
                if (isSticky()) {
                    return;
                }
                releaseChord();
                unlockingTogglePress = false;
            }
        });
    }

    void updateDefinition(KeyComboManager.Definition definition) {
        if (chordDown) {
            releaseChord();
        }
        setSticky(false);
        unlockingTogglePress = false;
        comboId = definition.id;
        displayName = definition.name;
        modifierKeys = definition.modifiers.clone();
        regularKeys = definition.keys.clone();
        longPressToggleEnabled = definition.longPressToggle;
        setText(displayName);
        post(() -> {
            if (virtualController != null) {
                virtualController.ensureTextButtonWidth(this, displayName);
            }
        });
    }

    String getComboId() {
        return comboId;
    }

    String getDisplayName() {
        return displayName;
    }

    int[] getModifierKeys() {
        return modifierKeys.clone();
    }

    int[] getRegularKeys() {
        return regularKeys.clone();
    }

    boolean isLongPressToggleEnabled() {
        return longPressToggleEnabled;
    }

    private void pressChord() {
        if (chordDown || virtualController == null) {
            return;
        }
        chordDown = true;
        virtualController.vibrate(KeyEvent.ACTION_DOWN);

        for (int keyCode : modifierKeys) {
            sendKey(keyCode, KeyEvent.ACTION_DOWN);
        }
        for (int keyCode : regularKeys) {
            sendKey(keyCode, KeyEvent.ACTION_DOWN);
        }
    }

    private void releaseChord() {
        if (!chordDown || virtualController == null) {
            return;
        }

        for (int i = regularKeys.length - 1; i >= 0; i--) {
            sendKey(regularKeys[i], KeyEvent.ACTION_UP);
        }
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            sendKey(modifierKeys[i], KeyEvent.ACTION_UP);
        }

        chordDown = false;
        virtualController.vibrate(KeyEvent.ACTION_UP);
    }

    private void sendKey(int keyCode, int action) {
        KeyEvent event = new KeyEvent(action, keyCode);
        // Source 2 still routes through Game.onKey(), but KeyBoardController suppresses per-key
        // vibration for that source. The combo itself provides one haptic on chord-down/up instead.
        event.setSource(2);
        virtualController.sendKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (virtualController == null ||
                virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons) {
            boolean handled = super.onTouchEvent(event);
            if ((event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) && virtualController != null) {
                post(() -> virtualController.ensureTextButtonWidth(this, displayName));
            }
            return handled;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                editorDownX = event.getX();
                editorDownY = event.getY();
                editorMoved = false;
                editorLongPressTriggered = false;
                setPressed(true);
                invalidate();
                virtualController.getHandler().postDelayed(
                        editorLongPressRunnable,
                        ViewConfiguration.getLongPressTimeout());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - editorDownX) > touchSlop ||
                        Math.abs(event.getY() - editorDownY) > touchSlop) {
                    editorMoved = true;
                    virtualController.getHandler().removeCallbacks(editorLongPressRunnable);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                virtualController.getHandler().removeCallbacks(editorLongPressRunnable);
                setPressed(false);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                virtualController.getHandler().removeCallbacks(editorLongPressRunnable);
                setPressed(false);
                if (!editorMoved && !editorLongPressTriggered) {
                    actionDisableEnableButton();
                    virtualController.vibrate(KeyEvent.ACTION_DOWN);
                }
                invalidate();
                return true;

            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (virtualController != null) {
            virtualController.getHandler().removeCallbacks(editorLongPressRunnable);
        }
        super.onDetachedFromWindow();
    }
}
