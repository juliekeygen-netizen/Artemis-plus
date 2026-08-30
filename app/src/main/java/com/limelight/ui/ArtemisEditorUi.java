package com.limelight.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.limelight.R;

/** Shared visual language for Artemis Plus in-stream editors and compact menus. */
public final class ArtemisEditorUi {
    public static final int SURFACE = 0xFF1A1A1A;
    public static final int SURFACE_RAISED = 0xFF242428;
    public static final int SURFACE_SELECTED = 0xFF30363A;
    public static final int TEXT_PRIMARY = Color.WHITE;
    public static final int TEXT_SECONDARY = 0xFFB5B5BA;
    public static final int ACCENT = 0xFF8BE9A8;
    public static final int DANGER = 0xFFFF7777;
    public static final int BORDER = 0xFF3B3B40;

    private ArtemisEditorUi() {}

    public static Context context(Context base) {
        return new ContextThemeWrapper(base, R.style.ArtemisEditorDialogTheme);
    }

    public static int dp(Context context, float dp) {
        return Math.max(1, Math.round(dp * context.getResources().getDisplayMetrics().density));
    }

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp,
                                           float strokeDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    public static TextView header(Context context, String title) {
        TextView text = new TextView(context);
        text.setText(title);
        text.setTextColor(TEXT_PRIMARY);
        text.setTextSize(20f);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        text.setBackgroundColor(SURFACE);
        text.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56)));
        return text;
    }

    public static AlertDialog.Builder builder(Context base, String title) {
        Context themed = context(base);
        return new AlertDialog.Builder(themed).setCustomTitle(header(themed, title));
    }

    /** Apply a compact, stable width and shared footer typography after dialog.show(). */
    public static void styleDialog(AlertDialog dialog, Context context, int maxWidthDp) {
        styleDialog(dialog, context, maxWidthDp, 0, false);
    }

    public static void styleDialog(AlertDialog dialog, Context context, int maxWidthDp,
                                   int maxHeightDp, boolean fixedHeight) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(SURFACE));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int width = Math.min(dp(context, maxWidthDp), Math.round(screenWidth * 0.92f));
            int height = WindowManager.LayoutParams.WRAP_CONTENT;
            if (maxHeightDp > 0) {
                int capped = Math.min(dp(context, maxHeightDp), Math.round(screenHeight * 0.86f));
                if (fixedHeight) height = capped;
            }
            window.setLayout(Math.max(dp(context, 320), width), height);
        }
        styleFooterButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), ACCENT);
        styleFooterButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), TEXT_SECONDARY);
        styleFooterButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), ACCENT);
    }

    public static void styleFooterButton(Button button, int color) {
        if (button == null) return;
        button.setTextSize(13f);
        button.setTextColor(color);
        button.setMinHeight(dp(button.getContext(), 48));
        button.setAllCaps(true);
    }

    public static TextView label(Context context, String text, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    public static TextView menuRow(Context context, String text) {
        TextView row = new TextView(context);
        row.setText(text);
        row.setTextColor(TEXT_PRIMARY);
        row.setTextSize(14.5f);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        row.setBackground(rounded(context, SURFACE_RAISED, 9, 0, 0));
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46)));
        return row;
    }

    /** Disable Android's copy/paste/autofill action toolbar without disabling typing/focus. */
    public static void suppressTextActionMenu(EditText field) {
        if (field == null) return;
        ActionMode.Callback blocker = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        };
        field.setCustomSelectionActionModeCallback(blocker);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            field.setCustomInsertionActionModeCallback(blocker);
        }
        field.setLongClickable(false);
    }
}
