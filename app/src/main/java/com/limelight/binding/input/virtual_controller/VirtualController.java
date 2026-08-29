/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
//        public short inputMap = 0x0000;
        public int inputMap = 0;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;
    private static final String OSC_EDITING_PREFERENCES = "ArtemisPlusOscEditing";
    private static final String KEY_SNAPPING_ENABLED = "snapping_enabled";
    private static final String KEY_PAIRED_SIZING_ENABLED = "paired_sizing_enabled";

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private final List<VirtualControllerElement> elements = new ArrayList<>();

    private Vibrator vibrator;

    private final VibrationEffect defaultVibrationEffect;

    private boolean snappingEnabled;
    private boolean pairedSizingEnabled;

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        SharedPreferences editingPreferences = context.getSharedPreferences(
                OSC_EDITING_PREFERENCES,
                Context.MODE_PRIVATE);
        this.snappingEnabled = editingPreferences.getBoolean(KEY_SNAPPING_ENABLED, true);
        this.pairedSizingEnabled = editingPreferences.getBoolean(KEY_PAIRED_SIZING_ENABLED, true);

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultVibrationEffect = VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE);
        } else {
            defaultVibrationEffect = null;
        }

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;
                ControllerMode nextMode;

                if (currentMode == ControllerMode.Active) {
                    nextMode = ControllerMode.DisableEnableButtons;
                    message = context.getString(R.string.configuration_mode_disable_enable_buttons);
                } else if (currentMode == ControllerMode.DisableEnableButtons){
                    nextMode = ControllerMode.MoveButtons;
                    message = context.getString(R.string.configuration_mode_move_buttons);
                } else if (currentMode == ControllerMode.MoveButtons) {
                    nextMode = ControllerMode.ResizeButtons;
                    message = context.getString(R.string.configuration_mode_resize_buttons);
                } else {
                    nextMode = ControllerMode.Active;
                    message = context.getString(R.string.configuration_mode_exiting);
                }

                setControllerMode(nextMode);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
        buttonConfigure.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                OscProfileDialog.show(VirtualController.this, context);
                return true;
            }
        });

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
    }

    public void show() {
        showEnabledElements();

        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public int switchShowHide() {
        if (buttonConfigure.getVisibility() == View.VISIBLE) {
            hide();
            return 0;
        } else {
            show();
            return 1;
        }
    }

    public void showElements(){
        for(VirtualControllerElement element : elements){
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements(){
        for(VirtualControllerElement element: elements){
            element.setVisibility(element.enabled ? View.VISIBLE : View.GONE);
        }
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    public DisplayMetrics getDisplayMetrics() {
        return context.getResources().getDisplayMetrics();
    }

    public int getLayoutWidth() {
        return frame_layout != null ? frame_layout.getWidth() : 0;
    }

    public int getLayoutHeight() {
        return frame_layout != null ? frame_layout.getHeight() : 0;
    }

    public boolean isSnappingEnabled() {
        return snappingEnabled;
    }

    public boolean toggleSnapping() {
        setSnappingEnabled(!snappingEnabled);
        return snappingEnabled;
    }

    public void setSnappingEnabled(boolean enabled) {
        snappingEnabled = enabled;
        persistEditingPreference(KEY_SNAPPING_ENABLED, enabled);
    }

    public boolean isPairedSizingEnabled() {
        return pairedSizingEnabled;
    }

    public boolean togglePairedSizing() {
        setPairedSizingEnabled(!pairedSizingEnabled);
        return pairedSizingEnabled;
    }

    public void setPairedSizingEnabled(boolean enabled) {
        pairedSizingEnabled = enabled;
        persistEditingPreference(KEY_PAIRED_SIZING_ENABLED, enabled);
    }

    private void persistEditingPreference(String key, boolean value) {
        context.getSharedPreferences(OSC_EDITING_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, value)
                .apply();
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    /**
     * Sets a configuration mode directly. This is used by the normal configure button today and
     * gives the in-game OSC menu/profile UI a clean entry point without simulating button taps.
     */
    public void setControllerMode(ControllerMode mode) {
        if (mode == null) {
            return;
        }

        currentMode = mode;
        if (currentMode == ControllerMode.DisableEnableButtons) {
            showElements();
        } else {
            showEnabledElements();
        }

        if (currentMode == ControllerMode.Active) {
            VirtualControllerConfigurationLoader.saveProfile(this, context);
        }

        if (buttonConfigure != null) {
            buttonConfigure.invalidate();
        }
        for (VirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    public void sendControllerInputContext(long vibrationDuration, int vibrationAmplitude) {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();
        if (frame_layout != null && PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect;
                if (vibrationDuration == 0) {
                    effect = defaultVibrationEffect;
                } else {
                    effect = VibrationEffect.createOneShot(vibrationDuration, vibrationAmplitude);
                }
                vibrator.vibrate(effect);
            } else {
                if (vibrationDuration == 0) {
                    vibrationDuration = 10;
                }
                vibrator.vibrate(vibrationDuration);
            }
        }
        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }

    public void sendControllerInputContext() {
        sendControllerInputContext(0, 0);
    }
}
