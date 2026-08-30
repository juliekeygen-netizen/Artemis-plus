from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8', newline='\n')

def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f'anchor not found in {path}: {old[:120]!r}')
    text = text.replace(old, new, 1)
    write(path, text)

def regex_once(path, pattern, repl, flags=re.S):
    text = read(path)
    new, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f'pattern matched {count} times in {path}: {pattern[:120]!r}')
    write(path, new)

# ---------------------------------------------------------------------------
# Shared Artemis Plus dialog/menu UI primitives
# ---------------------------------------------------------------------------
write('app/src/main/java/com/limelight/ui/ArtemisEditorUi.java', r'''package com.limelight.ui;

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
''')

# ---------------------------------------------------------------------------
# One shared normalized position store for native Menu/Zoom and keyboard gear
# ---------------------------------------------------------------------------
write('app/src/main/java/com/limelight/ui/FloatingControlPositionStore.java', r'''package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;

import androidx.preference.PreferenceManager;

/** Shared portrait/landscape normalized-position persistence for Artemis floating controls. */
public final class FloatingControlPositionStore {
    public static final String PREFS = "ArtemisPlusFloatingControlPositions";
    public static final String RESET_BETWEEN_SESSIONS_KEY =
            "checkbox_reset_floating_controls_between_sessions";

    private FloatingControlPositionStore() {}

    public static boolean shouldResetBetweenSessions(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(RESET_BETWEEN_SESSIONS_KEY, false);
    }

    public static void save(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
        int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
        if (maxX <= 0 || maxY <= 0) return;
        String id = identity == null ? identityForView(view) : identity;
        prefs(view.getContext()).edit()
                .putBoolean(key(view.getContext(), id, "saved"), true)
                .putFloat(key(view.getContext(), id, "x"), clamp(view.getX() / maxX))
                .putFloat(key(view.getContext(), id, "y"), clamp(view.getY() / maxY))
                .apply();
    }

    public static boolean restore(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        String id = identity == null ? identityForView(view) : identity;
        SharedPreferences preferences = prefs(view.getContext());
        if (!preferences.getBoolean(key(view.getContext(), id, "saved"), false)) return false;
        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
        int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
        if (maxX <= 0 || maxY <= 0) return false;
        view.setX(clamp(preferences.getFloat(key(view.getContext(), id, "x"), 0f)) * maxX);
        view.setY(clamp(preferences.getFloat(key(view.getContext(), id, "y"), 0f)) * maxY);
        return true;
    }

    public static void clearCurrentOrientation(Context context, String identity) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.remove(key(context, identity, "saved"));
        editor.remove(key(context, identity, "x"));
        editor.remove(key(context, identity, "y"));
        editor.apply();
    }

    public static void clearAllOrientations(Context context, String identity) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String orientation : new String[]{"portrait", "landscape"}) {
            editor.remove(identity + "_" + orientation + "_saved");
            editor.remove(identity + "_" + orientation + "_x");
            editor.remove(identity + "_" + orientation + "_y");
        }
        editor.apply();
    }

    public static String identityForView(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Exception ignored) {
            return Integer.toString(view.getId());
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(Context context, String identity, String axis) {
        int orientation = context.getResources().getConfiguration().orientation;
        String orientationName = orientation == Configuration.ORIENTATION_PORTRAIT
                ? "portrait" : "landscape";
        return identity + "_" + orientationName + "_" + axis;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
''')

write('app/src/main/java/com/limelight/ui/PersistentPositionImageButton.java', r'''package com.limelight.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/** ImageButton that preserves its dragged position across Game sessions. */
public class PersistentPositionImageButton extends ImageButton {
    private boolean sessionResetApplied;

    public PersistentPositionImageButton(Context context) { super(context); }
    public PersistentPositionImageButton(Context context, AttributeSet attrs) { super(context, attrs); }
    public PersistentPositionImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!sessionResetApplied) {
            sessionResetApplied = true;
            if (FloatingControlPositionStore.shouldResetBetweenSessions(getContext())) {
                FloatingControlPositionStore.clearCurrentOrientation(
                        getContext(), FloatingControlPositionStore.identityForView(this));
            }
        }
        post(() -> FloatingControlPositionStore.restore(this, null));
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == View.VISIBLE) {
            post(() -> FloatingControlPositionStore.restore(this, null));
        }
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        if (listener == null) {
            super.setOnTouchListener(null);
            return;
        }
        super.setOnTouchListener((view, event) -> {
            boolean handled = listener.onTouch(view, event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                FloatingControlPositionStore.save(this, null);
            }
            return handled;
        });
    }
}
''')

# ---------------------------------------------------------------------------
# Remove the incorrect blank-managed-profile workaround.
# ---------------------------------------------------------------------------
loader = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java'
text = read(loader)
text = re.sub(r'\n\s*static boolean shouldTreatManagedProfileAsBlank\(String storageName, boolean hasSavedGeometry\) \{.*?\n\s*\}\n\n\s*public static void loadFromPreferences',
              '\n\n    public static void loadFromPreferences', text, count=1, flags=re.S)
text = text.replace('        boolean blankManagedProfile = shouldTreatManagedProfileAsBlank(name, !pref.getAll().isEmpty());\n\n', '')
text = re.sub(r'\n\s*\} else if \(blankManagedProfile\) \{\n\s*// New Artemis Plus profiles are intentionally blank\..*?\n\s*element\.enabled = true;\n', '\n            }\n', text, count=1, flags=re.S)
write(loader, text)

# ---------------------------------------------------------------------------
# Key/action geometry consistency and eliminate Action double editor outline.
# ---------------------------------------------------------------------------
controller = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java'
text = read(controller)
text = text.replace('import android.view.View;\n', 'import android.view.View;\nimport android.view.ViewConfiguration;\n')
text = text.replace('import com.limelight.Game;\n', 'import com.limelight.Game;\nimport com.limelight.ui.FloatingControlPositionStore;\n')
text = text.replace('    private View groupOutline;\n', '    private View groupOutline;\n\n    private static final String SETTINGS_POSITION_ID = "keyboardSettingsButton";\n    private boolean configureSessionPositionPrepared;\n')

old_touch = re.compile(r'''\n\s*buttonConfigure\.setOnLongClickListener\(v -> \{.*?\n\s*buttonConfigure\.setOnClickListener\(v -> cycleEditorMode\(\)\);''', re.S)
new_touch = r'''

        final int configureTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final long configureLongPressMs = ViewConfiguration.getLongPressTimeout();
        final long configureResetHoldMs = Math.max(1300L, configureLongPressMs + 700L);
        buttonConfigure.setOnClickListener(v -> cycleEditorMode());
        buttonConfigure.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startViewX;
            private float startViewY;
            private boolean moveArmed;
            private boolean moved;
            private boolean resetPromptShown;

            private final Runnable armMove = () -> {
                if (moved || resetPromptShown) return;
                moveArmed = true;
                Toast.makeText(context,
                        context.getString(R.string.keyboard_configure_movable),
                        Toast.LENGTH_SHORT).show();
                vibrate(KeyEvent.ACTION_DOWN);
            };

            private final Runnable offerReset = () -> {
                if (moved || resetPromptShown) return;
                resetPromptShown = true;
                moveArmed = false;
                new AlertDialog.Builder(context)
                        .setTitle("Reset position?")
                        .setMessage("Reset the on-screen settings button to its default position?")
                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            };

            private void cancelTimers() {
                handler.removeCallbacks(armMove);
                handler.removeCallbacks(offerReset);
            }

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startViewX = view.getX();
                        startViewY = view.getY();
                        moveArmed = false;
                        moved = false;
                        resetPromptShown = false;
                        handler.postDelayed(armMove, configureLongPressMs);
                        handler.postDelayed(offerReset, configureResetHoldMs);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        boolean beyondSlop = Math.hypot(dx, dy) > configureTouchSlop;
                        if (beyondSlop) {
                            handler.removeCallbacks(offerReset);
                        }
                        if (moveArmed && beyondSlop) {
                            moved = true;
                            float maxX = Math.max(0, frame_layout.getWidth() - view.getWidth());
                            float maxY = Math.max(0, frame_layout.getHeight() - view.getHeight());
                            view.setX(Math.max(0, Math.min(startViewX + dx, maxX)));
                            view.setY(Math.max(0, Math.min(startViewY + dy, maxY)));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelTimers();
                        if (moved) {
                            FloatingControlPositionStore.save(view, SETTINGS_POSITION_ID);
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                                !moveArmed && !resetPromptShown) {
                            view.performClick();
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });'''
text, count = old_touch.subn(new_touch, text, count=1)
if count != 1:
    raise RuntimeError('configure touch block not found')

# Replace screen-relative custom-key default with native Action size.
text = re.sub(r'''    int getDefaultKeyButtonSize\(\) \{.*?\n    \}\n\n    /\*\*\n     \* Spawn new user controls''',
'''    int getDefaultKeyButtonSize() {
        return Math.max(1, Math.round(
                ArtemisActionButton.DEFAULT_SIZE_DP * context.getResources().getDisplayMetrics().density));
    }

    /**
     * Spawn new user controls''', text, count=1, flags=re.S)

# Clean explicit default anchor and restore saved gear position after each layout rebuild.
text = text.replace('        configParams.leftMargin = Math.max(0, 20 + oldButtonSize - 50);\n        configParams.topMargin = 15;\n        frame_layout.addView(buttonConfigure, configParams);',
'''        configParams.leftMargin = 0; // old 20px anchor shifted 50 physical px left, clamped to edge
        configParams.topMargin = 15;
        frame_layout.addView(buttonConfigure, configParams);
        prepareConfigureButtonSessionPosition();
        buttonConfigure.post(() -> FloatingControlPositionStore.restore(
                buttonConfigure, SETTINGS_POSITION_ID));''')

# Add gear persistence/reset helpers before refreshLayout.
marker = '    public void refreshLayout() {\n'
helpers = r'''    private void prepareConfigureButtonSessionPosition() {
        if (configureSessionPositionPrepared) return;
        configureSessionPositionPrepared = true;
        if (FloatingControlPositionStore.shouldResetBetweenSessions(context)) {
            FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        }
    }

    private void resetConfigureButtonPosition() {
        FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        if (buttonConfigure == null) return;
        buttonConfigure.setX(0f);
        buttonConfigure.setY(15f);
    }

'''
if marker not in text:
    raise RuntimeError('refreshLayout marker missing')
text = text.replace(marker, helpers + marker, 1)
write(controller, text)

# Normal text keys: exactly 2dp bubble/editor stroke.
digital = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardDigitalButton.java'
text = read(digital)
insert = '''\n    @Override\n    protected int getDefaultStrokeWidth() {\n        return Math.max(1, Math.round(2f * getResources().getDisplayMetrics().density));\n    }\n'''
text = text.replace('\n    @Override\n    protected void onElementDraw(Canvas canvas) {', insert + '\n    @Override\n    protected void onElementDraw(Canvas canvas) {', 1)
write(digital, text)

base_element = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java'
text = read(base_element)
text = text.replace('        if (currentMode != Mode.Normal &&\n                (virtualController == null || !virtualController.isElementCoveredByGroupOutline(this))) {',
'''        if (currentMode != Mode.Normal && shouldDrawBaseEditorOutline() &&
                (virtualController == null || !virtualController.isElementCoveredByGroupOutline(this))) {''', 1)
text = text.replace('    protected int getDefaultStrokeWidth() {\n', '    protected boolean shouldDrawBaseEditorOutline() {\n        return true;\n    }\n\n    protected int getDefaultStrokeWidth() {\n', 1)
write(base_element, text)

action_button = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButton.java'
text = read(action_button)
text = text.replace('    @Override\n    protected int getMinimumResizeSizePx() {',
'''    @Override
    protected boolean shouldDrawBaseEditorOutline() {
        // Action buttons already draw their own state-aware 2dp ring.
        return false;
    }

    @Override
    protected int getMinimumResizeSizePx() {''', 1)
text = text.replace('            case TOUCH_SENSITIVITY:\n', '')
write(action_button, text)

# ---------------------------------------------------------------------------
# Deterministic mixed-size snapping: best candidate wins; no overlap resizing.
# ---------------------------------------------------------------------------
write('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelper.java', r'''package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.View;
import android.widget.FrameLayout;

public class LayoutSnappingHelper {
    private static final int SNAP_THRESHOLD = 10;
    private static final int SPACING_MIN = 4;
    private static final int SPACING_THRESHOLD = 30;
    private static final float GROUP_SIZE_TOLERANCE_RATIO = 0.12f;
    private static final float GROUP_PARALLEL_OVERLAP = 0.40f;

    public static class SnapResult {
        public int newX;
        public int newY;
        public int newWidth;
        public int newHeight;
        public boolean didSnap;
        public boolean didResize;
        public boolean didAdjustSpacing;
        public boolean lockX;
        public boolean lockY;

        public SnapResult(int x, int y, int width, int height,
                          boolean snapped, boolean resized, boolean adjustedSpacing) {
            newX = x; newY = y; newWidth = width; newHeight = height;
            didSnap = snapped; didResize = resized; didAdjustSpacing = adjustedSpacing;
        }
    }

    private static final class Candidate {
        final int value;
        final int distance;
        final int priority;
        final boolean spacing;
        Candidate(int value, int distance, int priority, boolean spacing) {
            this.value = value; this.distance = distance; this.priority = priority; this.spacing = spacing;
        }
        boolean betterThan(Candidate other) {
            if (other == null) return true;
            if (distance != other.distance) return distance < other.distance;
            return priority < other.priority;
        }
    }

    public static boolean areGrouped(View first, View second) {
        if (first == null || second == null || first == second ||
                first.getVisibility() != View.VISIBLE || second.getVisibility() != View.VISIBLE ||
                !(first.getLayoutParams() instanceof FrameLayout.LayoutParams) ||
                !(second.getLayoutParams() instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) first.getLayoutParams();
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) second.getLayoutParams();
        return areGrouped(a.leftMargin, a.topMargin, a.width, a.height,
                b.leftMargin, b.topMargin, b.width, b.height);
    }

    static boolean areGrouped(int aLeft, int aTop, int aWidth, int aHeight,
                              int bLeft, int bTop, int bWidth, int bHeight) {
        aWidth = Math.max(1, aWidth); aHeight = Math.max(1, aHeight);
        bWidth = Math.max(1, bWidth); bHeight = Math.max(1, bHeight);
        int aRight = aLeft + aWidth, aBottom = aTop + aHeight;
        int bRight = bLeft + bWidth, bBottom = bTop + bHeight;
        int verticalOverlap = Math.min(aBottom, bBottom) - Math.max(aTop, bTop);
        int horizontalOverlap = Math.min(aRight, bRight) - Math.max(aLeft, bLeft);
        int minHeight = Math.min(aHeight, bHeight), minWidth = Math.min(aWidth, bWidth);
        int horizontalTolerance = Math.max(8, Math.round(minWidth * GROUP_SIZE_TOLERANCE_RATIO));
        int verticalTolerance = Math.max(8, Math.round(minHeight * GROUP_SIZE_TOLERANCE_RATIO));
        boolean sideBySide = verticalOverlap >= minHeight * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aRight - bLeft) <= horizontalTolerance ||
                        Math.abs(bRight - aLeft) <= horizontalTolerance);
        boolean stacked = horizontalOverlap >= minWidth * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aBottom - bTop) <= verticalTolerance ||
                        Math.abs(bBottom - aTop) <= verticalTolerance);
        return sideBySide || stacked;
    }

    public static SnapResult calculateSnappedPosition(View movingView, View[] otherViews,
                                                       int proposedX, int proposedY) {
        FrameLayout.LayoutParams movingParams = (FrameLayout.LayoutParams) movingView.getLayoutParams();
        int movingWidth = Math.max(1, movingParams.width);
        int movingHeight = Math.max(1, movingParams.height);
        Candidate bestX = null, bestY = null;

        for (View other : otherViews) {
            if (other == null || other == movingView || other.getVisibility() != View.VISIBLE ||
                    !(other.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) other.getLayoutParams();
            int otherWidth = Math.max(1, p.width), otherHeight = Math.max(1, p.height);
            int otherRight = p.leftMargin + otherWidth;
            int otherBottom = p.topMargin + otherHeight;

            int verticalOverlap = overlap(proposedY, proposedY + movingHeight,
                    p.topMargin, otherBottom);
            int horizontalOverlap = overlap(proposedX, proposedX + movingWidth,
                    p.leftMargin, otherRight);
            boolean parallelY = verticalOverlap >= Math.min(movingHeight, otherHeight) * 0.35f;
            boolean parallelX = horizontalOverlap >= Math.min(movingWidth, otherWidth) * 0.35f;

            if (parallelY) {
                bestX = consider(bestX, proposedX, otherRight + SPACING_MIN,
                        SPACING_THRESHOLD, 0, true);
                bestX = consider(bestX, proposedX, p.leftMargin - SPACING_MIN - movingWidth,
                        SPACING_THRESHOLD, 0, true);
            }
            bestX = consider(bestX, proposedX, p.leftMargin, SNAP_THRESHOLD, 1, false);
            bestX = consider(bestX, proposedX, otherRight - movingWidth, SNAP_THRESHOLD, 1, false);
            bestX = consider(bestX, proposedX,
                    p.leftMargin + otherWidth / 2 - movingWidth / 2,
                    SNAP_THRESHOLD, 2, false);

            if (parallelX) {
                bestY = consider(bestY, proposedY, otherBottom + SPACING_MIN,
                        SPACING_THRESHOLD, 0, true);
                bestY = consider(bestY, proposedY, p.topMargin - SPACING_MIN - movingHeight,
                        SPACING_THRESHOLD, 0, true);
            }
            bestY = consider(bestY, proposedY, p.topMargin, SNAP_THRESHOLD, 1, false);
            bestY = consider(bestY, proposedY, otherBottom - movingHeight, SNAP_THRESHOLD, 1, false);
            bestY = consider(bestY, proposedY,
                    p.topMargin + otherHeight / 2 - movingHeight / 2,
                    SNAP_THRESHOLD, 2, false);
        }

        int x = bestX == null ? proposedX : bestX.value;
        int y = bestY == null ? proposedY : bestY.value;
        boolean spacing = (bestX != null && bestX.spacing) || (bestY != null && bestY.spacing);
        boolean snap = (bestX != null && !bestX.spacing) || (bestY != null && !bestY.spacing);
        SnapResult result = new SnapResult(x, y, movingWidth, movingHeight, snap, false, spacing);
        result.lockX = bestX != null;
        result.lockY = bestY != null;
        return result;
    }

    private static Candidate consider(Candidate current, int proposed, int target,
                                      int threshold, int priority, boolean spacing) {
        int distance = Math.abs(proposed - target);
        if (distance > threshold) return current;
        Candidate candidate = new Candidate(target, distance, priority, spacing);
        return candidate.betterThan(current) ? candidate : current;
    }

    private static int overlap(int aStart, int aEnd, int bStart, int bEnd) {
        return Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
    }
}
''')

# ---------------------------------------------------------------------------
# Key dialog: shared shell, clear chord preview, no Android selection toolbar,
# compact dropdown height.
# ---------------------------------------------------------------------------
combo = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManager.java'
text = read(combo)
text = text.replace('import com.limelight.R;\n', 'import com.limelight.R;\nimport com.limelight.ui.ArtemisEditorUi;\n')
text = text.replace('        Context dialogContext = new ContextThemeWrapper(context, R.style.ArtemisEditorDialogTheme);',
                    '        Context dialogContext = ArtemisEditorUi.context(context);')
text = text.replace('                "Add one normal key, or build an ordered chord. Modifiers are held while the key rows are pressed from top to bottom.",\n                14f, 0xFFBDBDBD);',
                    '                "Add one key, or build a chord. Modifiers stay held while every selected key is pressed.",\n                12.5f, ArtemisEditorUi.TEXT_SECONDARY);')
# Add secondary press-order text and update summary runnable.
text = text.replace('''        TextView summary = label(dialogContext, "", 13f, 0xFFE0E0E0);
        summary.setPadding(0, Math.round(8 * density), 0, 0);
        content.addView(summary);

        Runnable updateSummary = () -> summary.setText(buildSummary(
                ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(), selectedOptions(keyRows)));''',
'''        TextView summary = label(dialogContext, "", 13f, 0xFFE0E0E0);
        summary.setPadding(0, Math.round(8 * density), 0, 0);
        content.addView(summary);
        TextView pressOrder = label(dialogContext, "", 11.5f, ArtemisEditorUi.TEXT_SECONDARY);
        pressOrder.setPadding(0, Math.round(2 * density), 0, 0);
        content.addView(pressOrder);

        Runnable updateSummary = () -> {
            List<KeyOption> selected = selectedOptions(keyRows);
            summary.setText(buildSummary(
                    ctrl.isChecked(), alt.isChecked(), shift.isChecked(), meta.isChecked(), selected));
            String order = buildPressOrder(selected);
            pressOrder.setText(order);
            pressOrder.setVisibility(order.isEmpty() ? View.GONE : View.VISIBLE);
        };''')
# Shared custom title builder and size.
text = text.replace('''        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext)
                .setTitle(existing == null ? "Add Keys" : "Edit Key")
                .setView(scrollView)''',
'''        AlertDialog.Builder builder = ArtemisEditorUi.builder(
                        dialogContext, existing == null ? "Add Keys" : "Edit Key")
                .setView(scrollView)''')
old_size = re.compile(r'''            if \(dialog\.getWindow\(\) != null\) \{.*?\n            \}\n\n            dialog\.getButton\(AlertDialog\.BUTTON_POSITIVE\)''', re.S)
text, n = old_size.subn('''            ArtemisEditorUi.styleDialog(dialog, context, 520, 620, true);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)''', text, count=1)
if n != 1: raise RuntimeError('combo dialog size block missing')
# Key field UX: cap dropdown and suppress text action toolbar.
text = text.replace('''            field.setSingleLine(true);
            field.setThreshold(0);''',
'''            field.setSingleLine(true);
            field.setThreshold(0);
            field.setDropDownHeight(Math.round(264 * density));
            ArtemisEditorUi.suppressTextActionMenu(field);''')
# Replace summary method with chord-held semantics + separate order.
text = re.sub(r'''    private static String buildSummary\(boolean ctrl,.*?\n    \}\n\n    private static TextView label''', r'''    static String buildSummary(boolean ctrl,
                               boolean alt,
                               boolean shift,
                               boolean meta,
                               List<KeyOption> keys) {
        StringBuilder result = new StringBuilder("Sends: ");
        boolean wrote = false;
        if (ctrl) { result.append("Ctrl"); wrote = true; }
        if (alt) { if (wrote) result.append(" + "); result.append("Alt"); wrote = true; }
        if (shift) { if (wrote) result.append(" + "); result.append("Shift"); wrote = true; }
        if (meta) { if (wrote) result.append(" + "); result.append("Win"); wrote = true; }
        for (KeyOption key : keys) {
            if (wrote) result.append(" + ");
            result.append(key.selectionLabel().replace("   ", " "));
            wrote = true;
        }
        if (!wrote) result.append("nothing selected yet");
        return result.toString();
    }

    static String buildPressOrder(List<KeyOption> keys) {
        if (keys.size() <= 1) return "";
        StringBuilder result = new StringBuilder("Press order: ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) result.append("  →  ");
            result.append(keys.get(i).selectionLabel().replace("   ", " "));
        }
        return result.toString();
    }

    private static TextView label''', text, count=1, flags=re.S)
write(combo, text)

# ---------------------------------------------------------------------------
# Compact, consistent Keyboard Profiles dialog.
# ---------------------------------------------------------------------------
write('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesDialog.java', r'''package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.ui.ArtemisEditorUi;

import java.util.ArrayList;
import java.util.List;

/** Shared keyboard-profile editor used in-stream and from Settings. */
public final class KeyboardProfilesDialog {
    private KeyboardProfilesDialog() {}

    public static void show(Context context, KeyBoardController controller) {
        KeyboardProfilesManager.ensureInitialized(context);
        Context ui = ArtemisEditorUi.context(context);
        int padding = ArtemisEditorUi.dp(ui, 12);

        LinearLayout root = new LinearLayout(ui);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, ArtemisEditorUi.dp(ui, 8), padding, ArtemisEditorUi.dp(ui, 6));
        root.setBackgroundColor(ArtemisEditorUi.SURFACE);

        RecyclerView list = new RecyclerView(ui);
        list.setLayoutManager(new LinearLayoutManager(ui));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, ArtemisEditorUi.dp(ui, 4));
        int listHeight = Math.min(ArtemisEditorUi.dp(ui, 330),
                Math.max(ArtemisEditorUi.dp(ui, 120),
                        context.getResources().getDisplayMetrics().heightPixels / 2));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight));

        ProfileAdapter adapter = new ProfileAdapter(
                ui, context, controller, new ArrayList<>(KeyboardProfilesManager.getProfiles(context)));
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                int from = vh.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
                return from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION &&
                        from != to && adapter.moveProfile(from, to);
            }
            @Override public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override public boolean isLongPressDragEnabled() { return true; }
        }).attachToRecyclerView(list);

        AlertDialog dialog = ArtemisEditorUi.builder(ui, "Keyboard Profiles")
                .setView(root)
                .setNeutralButton("+ Add profile", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(dialog, context, 520);
            Button add = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            ArtemisEditorUi.styleFooterButton(add, ArtemisEditorUi.ACCENT);
            add.setOnClickListener(v -> promptForName(ui, "Add Profile", "Profile name", "", name -> {
                KeyboardProfilesManager.Profile profile = KeyboardProfilesManager.createProfile(context, name);
                if (profile != null) {
                    switchProfile(context, controller, profile.id);
                    adapter.replaceProfiles(KeyboardProfilesManager.getProfiles(context));
                    list.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                }
            }));
        });
        dialog.show();
    }

    private static final class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.Holder> {
        private final Context ui;
        private final Context app;
        private final KeyBoardController controller;
        private final List<KeyboardProfilesManager.Profile> profiles = new ArrayList<>();
        private String activeId;

        ProfileAdapter(Context ui, Context app, KeyBoardController controller,
                       List<KeyboardProfilesManager.Profile> initial) {
            this.ui = ui; this.app = app; this.controller = controller; replaceProfiles(initial);
        }

        void replaceProfiles(List<KeyboardProfilesManager.Profile> updated) {
            profiles.clear(); profiles.addAll(updated);
            KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(app);
            activeId = active == null ? "" : active.id;
            notifyDataSetChanged();
        }

        boolean moveProfile(int from, int to) {
            if (from < 0 || to < 0 || from >= profiles.size() || to >= profiles.size()) return false;
            KeyboardProfilesManager.Profile moving = profiles.get(from);
            int direction = to > from ? 1 : -1;
            for (int i = 0; i < Math.abs(to - from); i++) {
                if (!KeyboardProfilesManager.moveProfile(app, moving.id, direction)) {
                    replaceProfiles(KeyboardProfilesManager.getProfiles(app));
                    return false;
                }
            }
            profiles.remove(from); profiles.add(to, moving); notifyItemMoved(from, to); return true;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardView card = new CardView(ui);
            card.setRadius(ArtemisEditorUi.dp(ui, 10));
            card.setCardElevation(0);
            card.setUseCompatPadding(false);
            LinearLayout row = new LinearLayout(ui);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ArtemisEditorUi.dp(ui, 6), 0, ArtemisEditorUi.dp(ui, 4), 0);
            card.addView(row, new CardView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 54)));

            TextView drag = new TextView(ui);
            drag.setText("≡"); drag.setTextColor(0xFF94949B); drag.setTextSize(21f);
            drag.setGravity(Gravity.CENTER); drag.setContentDescription("Hold and drag profile to reorder");
            row.addView(drag, new LinearLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 48), ArtemisEditorUi.dp(ui, 48)));

            TextView name = new TextView(ui);
            name.setTextColor(Color.WHITE); name.setTextSize(15.5f); name.setMaxLines(1);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            FrameLayout moreTouch = new FrameLayout(ui);
            TextView moreIcon = new TextView(ui);
            moreIcon.setText("⋮"); moreIcon.setTextColor(0xFFE8E8EA); moreIcon.setTextSize(21f);
            moreIcon.setGravity(Gravity.CENTER);
            moreIcon.setBackground(ArtemisEditorUi.rounded(ui, 0x22FFFFFF, 8, 0, 0));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 32), ArtemisEditorUi.dp(ui, 32), Gravity.CENTER);
            moreTouch.addView(moreIcon, iconParams);
            moreTouch.setContentDescription("Profile options");
            row.addView(moreTouch, new LinearLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 48), ArtemisEditorUi.dp(ui, 48)));

            RecyclerView.LayoutParams outer = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 54));
            outer.setMargins(0, ArtemisEditorUi.dp(ui, 3), 0, ArtemisEditorUi.dp(ui, 3));
            card.setLayoutParams(outer);
            return new Holder(card, name, moreTouch);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            KeyboardProfilesManager.Profile profile = profiles.get(position);
            boolean active = profile.id.equals(activeId);
            holder.name.setText(profile.name);
            ((CardView) holder.itemView).setCardBackgroundColor(
                    active ? ArtemisEditorUi.SURFACE_SELECTED : ArtemisEditorUi.SURFACE_RAISED);
            holder.itemView.setOnClickListener(v -> {
                switchProfile(app, controller, profile.id);
                KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(app);
                activeId = current == null ? "" : current.id;
                notifyDataSetChanged();
            });
            holder.more.setOnClickListener(v -> showProfileMenu(
                    ui, app, controller, profile, profiles.size(), holder.more,
                    () -> replaceProfiles(KeyboardProfilesManager.getProfiles(app))));
        }

        @Override public int getItemCount() { return profiles.size(); }
        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name; final View more;
            Holder(View item, TextView name, View more) { super(item); this.name = name; this.more = more; }
        }
    }

    private static void showProfileMenu(Context ui, Context app, KeyBoardController controller,
                                        KeyboardProfilesManager.Profile profile, int count,
                                        View anchor, Runnable rebuild) {
        int popupWidth = ArtemisEditorUi.dp(ui, 132);
        int itemHeight = ArtemisEditorUi.dp(ui, 38);
        int popupHeight = itemHeight * 3 + ArtemisEditorUi.dp(ui, 8);
        LinearLayout content = new LinearLayout(ui);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, ArtemisEditorUi.dp(ui, 4), 0, ArtemisEditorUi.dp(ui, 4));
        content.setBackground(ArtemisEditorUi.rounded(ui, ArtemisEditorUi.SURFACE_RAISED,
                10, 1, ArtemisEditorUi.BORDER));
        PopupWindow popup = new PopupWindow(content, popupWidth, popupHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(ArtemisEditorUi.dp(ui, 10));

        content.addView(menuItem(ui, "Rename", Color.WHITE, true, itemHeight, () -> {
            popup.dismiss();
            promptForName(ui, "Rename Profile", "Profile name", profile.name, name -> {
                KeyboardProfilesManager.renameProfile(app, profile.id, name); rebuild.run();
            });
        }));
        content.addView(menuItem(ui, "Duplicate", Color.WHITE, true, itemHeight, () -> {
            popup.dismiss(); KeyboardProfilesManager.duplicateProfile(app, profile.id); rebuild.run();
        }));
        content.addView(menuItem(ui, "Delete", count > 1 ? ArtemisEditorUi.DANGER : 0xFF77777D,
                count > 1, itemHeight, () -> {
                    popup.dismiss();
                    new AlertDialog.Builder(ui).setTitle("Delete Profile?")
                            .setMessage("Delete “" + profile.name + "”? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) -> {
                                KeyboardProfilesManager.Profile before = KeyboardProfilesManager.getActiveProfile(app);
                                if (KeyboardProfilesManager.deleteProfile(app, profile.id)) {
                                    if (controller != null && before != null && before.id.equals(profile.id))
                                        controller.reloadCurrentProfile();
                                    rebuild.run();
                                }
                            }).setNegativeButton(android.R.string.cancel, null).show();
                }));

        int xOffset = -popupWidth + anchor.getWidth();
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int screenHeight = ui.getResources().getDisplayMetrics().heightPixels;
        int spaceBelow = screenHeight - (location[1] + anchor.getHeight());
        int yOffset = spaceBelow >= popupHeight + ArtemisEditorUi.dp(ui, 6)
                ? -ArtemisEditorUi.dp(ui, 2)
                : -(anchor.getHeight() + popupHeight + ArtemisEditorUi.dp(ui, 4));
        popup.showAsDropDown(anchor, xOffset, yOffset);
    }

    private static TextView menuItem(Context context, String text, int color, boolean enabled,
                                     int height, Runnable action) {
        TextView item = new TextView(context);
        item.setText(text); item.setTextColor(color); item.setTextSize(13f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ArtemisEditorUi.dp(context, 12), 0, ArtemisEditorUi.dp(context, 8), 0);
        item.setEnabled(enabled); item.setAlpha(enabled ? 1f : .55f);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
        item.setOnClickListener(v -> { if (enabled) action.run(); });
        return item;
    }

    private static void switchProfile(Context context, KeyBoardController controller, String id) {
        KeyboardProfilesManager.Profile current = KeyboardProfilesManager.getActiveProfile(context);
        if (current != null && current.id.equals(id)) return;
        if (controller != null) controller.switchKeyboardProfile(id);
        else {
            KeyboardProfilesManager.setActiveProfile(context, id);
            Toast.makeText(context, "Keyboard profile selected", Toast.LENGTH_SHORT).show();
        }
    }

    private interface NameCallback { void onName(String name); }
    private static void promptForName(Context context, String title, String hint, String initial,
                                      NameCallback callback) {
        EditText input = new EditText(context);
        input.setSingleLine(true); input.setHint(hint); input.setText(initial);
        input.setTextColor(Color.WHITE); input.setHintTextColor(0xFF8E8E93);
        input.setPadding(ArtemisEditorUi.dp(context, 14), input.getPaddingTop(),
                ArtemisEditorUi.dp(context, 14), input.getPaddingBottom());
        if (!initial.isEmpty()) input.setSelection(initial.length());
        AlertDialog dialog = ArtemisEditorUi.builder(context, title)
                .setView(input).setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> {
            ArtemisEditorUi.styleDialog(dialog, context, 420);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) { input.setError("Enter a profile name"); return; }
                callback.onName(value); dialog.dismiss();
            });
        });
        dialog.show();
    }
}
''')

# ---------------------------------------------------------------------------
# Add Artemis Actions picker joins the same compact shell.
# ---------------------------------------------------------------------------
action_factory = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButtonFactory.java'
text = read(action_factory)
text = text.replace('import android.widget.Toast;\n', 'import android.widget.Toast;\nimport android.widget.CheckBox;\nimport android.widget.LinearLayout;\nimport android.widget.ScrollView;\n')
text = text.replace('import com.limelight.R;\n', 'import com.limelight.R;\nimport com.limelight.ui.ArtemisEditorUi;\n')
method_pattern = re.compile(r'''    public static void showPicker\(KeyBoardController controller, Context context\) \{.*?\n    \}\n\n    public static void restoreSelectedActions''', re.S)
method_repl = r'''    public static void showPicker(KeyBoardController controller, Context context) {
        ArtemisAction[] actions = ArtemisAction.values();
        Set<String> selected = new HashSet<>(getSelectedActionIds(context));
        Context ui = ArtemisEditorUi.context(context);
        LinearLayout list = new LinearLayout(ui);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ArtemisEditorUi.dp(ui, 14), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 14), ArtemisEditorUi.dp(ui, 6));
        CheckBox[] boxes = new CheckBox[actions.length];
        for (int i = 0; i < actions.length; i++) {
            CheckBox box = new CheckBox(ui);
            box.setText(actions[i].getLabel());
            box.setTextSize(15f);
            box.setTextColor(ArtemisEditorUi.TEXT_PRIMARY);
            box.setChecked(selected.contains(actions[i].getId()));
            box.setMinHeight(ArtemisEditorUi.dp(ui, 44));
            boxes[i] = box;
            list.addView(box, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 44)));
        }
        ScrollView scroll = new ScrollView(ui);
        scroll.addView(list);
        AlertDialog dialog = ArtemisEditorUi.builder(ui, "Add Artemis Actions")
                .setView(scroll)
                .setPositiveButton("Apply", (d, which) -> {
                    HashSet<String> requested = new HashSet<>();
                    for (int i = 0; i < actions.length; i++) {
                        if (boxes[i].isChecked()) {
                            requested.add(actions[i].getId());
                            ensureActionPresent(controller, context, actions[i], true);
                        } else hideExistingAction(controller, actions[i]);
                    }
                    if (!requested.contains(ArtemisAction.TOGGLE_KEYBOARD_CONTROLLER.getId())) {
                        COLLAPSED_CONTROLLERS.remove(controller);
                        controller.showEnabledElements();
                    } else applyCollapsedState(controller);
                    saveSelectedActionIds(context, requested);
                    KeyBoardControllerConfigurationLoader.saveProfile(controller, context);
                    Toast.makeText(context, "Artemis action buttons updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> ArtemisEditorUi.styleDialog(dialog, context, 520, 600, false));
        dialog.show();
    }

    public static void restoreSelectedActions'''
text, n = method_pattern.subn(method_repl, text, count=1)
if n != 1: raise RuntimeError('action picker method not found')
write(action_factory, text)

# ---------------------------------------------------------------------------
# Touch Sensitivity dialog joins shared shell without touching input logic.
# ---------------------------------------------------------------------------
game = 'app/src/main/java/com/limelight/Game.java'
text = read(game)
text = text.replace('import com.limelight.ui.GameGestures;\n', 'import com.limelight.ui.GameGestures;\nimport com.limelight.ui.ArtemisEditorUi;\n')
text = text.replace('''        android.view.ContextThemeWrapper themedContext =
                new android.view.ContextThemeWrapper(this, R.style.ArtemisEditorDialogTheme);''',
                    '        Context themedContext = ArtemisEditorUi.context(this);')
text = text.replace('''        new AlertDialog.Builder(themedContext)
                .setTitle("Touch Sensitivity")
                .setView(root)''',
'''        AlertDialog touchDialog = ArtemisEditorUi.builder(themedContext, "Touch Sensitivity")
                .setView(root)''')
text = text.replace('''                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void disconnect()''',
'''                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        touchDialog.setOnShowListener(ignored -> ArtemisEditorUi.styleDialog(touchDialog, this, 520));
        touchDialog.show();
    }

    public void disconnect()''', 1)
write(game, text)

# ---------------------------------------------------------------------------
# Quick/Advanced/Send Keys/Server Commands: one compact shared menu shell.
# ---------------------------------------------------------------------------
game_menu = 'app/src/main/java/com/limelight/GameMenu.java'
text = read(game_menu)
text = text.replace('import android.widget.ArrayAdapter;\n', 'import android.widget.ArrayAdapter;\nimport android.widget.LinearLayout;\nimport android.widget.ScrollView;\nimport android.widget.TextView;\n')
text = text.replace('import com.limelight.binding.input.GameInputDevice;\n',
                    'import com.limelight.binding.input.GameInputDevice;\nimport com.limelight.ui.ArtemisEditorUi;\n')
menu_pattern = re.compile(r'''    private void showMenuDialog\(String title, MenuOption\[] options\) \{.*?\n    \}\n\n    private void showSpecialKeysMenu''', re.S)
menu_repl = r'''    private void showMenuDialog(String title, MenuOption[] options) {
        Context ui = ArtemisEditorUi.context(dialogScreenContext);
        LinearLayout rows = new LinearLayout(ui);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6));
        String cancelLabel = getString(R.string.game_menu_cancel);
        for (MenuOption option : options) {
            if (option.runnable == null) {
                cancelLabel = option.label;
                continue;
            }
            TextView row = ArtemisEditorUi.menuRow(ui, option.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
            lp.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0, ArtemisEditorUi.dp(ui, 2));
            rows.addView(row, lp);
            row.setOnClickListener(v -> {
                hideMenu();
                run(option);
            });
        }
        ScrollView scroll = new ScrollView(ui);
        scroll.addView(rows);
        AlertDialog.Builder builder = ArtemisEditorUi.builder(ui, title)
                .setView(scroll)
                .setNegativeButton(cancelLabel, (dialog, which) -> currentDialog = null)
                .setOnCancelListener(dialog -> hideMenu());
        if (currentDialog != null) currentDialog.dismiss();
        currentDialog = builder.create();
        currentDialog.setOnShowListener(ignored ->
                ArtemisEditorUi.styleDialog(currentDialog, dialogScreenContext, 460));
        currentDialog.show();
    }

    private void showSpecialKeysMenu'''
text, n = menu_pattern.subn(menu_repl, text, count=1)
if n != 1: raise RuntimeError('GameMenu showMenuDialog not found')
# Style the server-command-empty dialog too.
text = text.replace('''                        new AlertDialog.Builder(themedContext)
                                .setTitle(R.string.game_dialog_title_server_cmd_empty)
                                .setMessage(R.string.game_dialog_message_server_cmd_empty)
                                .show();''',
'''                        AlertDialog emptyDialog = ArtemisEditorUi.builder(
                                        themedContext, getString(R.string.game_dialog_title_server_cmd_empty))
                                .setMessage(R.string.game_dialog_message_server_cmd_empty)
                                .setNegativeButton(android.R.string.ok, null)
                                .create();
                        emptyDialog.setOnShowListener(ignored ->
                                ArtemisEditorUi.styleDialog(emptyDialog, themedContext, 440));
                        emptyDialog.show();''')
write(game_menu, text)

# ---------------------------------------------------------------------------
# Shared dialog theme surface follows the app, not a separate #18181B island.
# ---------------------------------------------------------------------------
styles = 'app/src/main/res/values/styles.xml'
text = read(styles)
text = text.replace('<item name="android:windowBackground">#18181B</item>', '<item name="android:windowBackground">#1A1A1A</item>')
text = text.replace('<item name="android:colorBackground">#18181B</item>', '<item name="android:colorBackground">#1A1A1A</item>')
text = text.replace('<item name="colorSurface">#FF18181B</item>', '<item name="colorSurface">#FF1A1A1A</item>')
write(styles, text)

# ---------------------------------------------------------------------------
# New setting under the existing native floating-button preference.
# ---------------------------------------------------------------------------
prefs = 'app/src/main/res/xml/preferences.xml'
text = read(prefs)
anchor = '''        <CheckBoxPreference
            android:dependency="checkbox_enable_quit_dialog"
            android:defaultValue="false"
            android:key="checkbox_enable_floating_button"
            android:summary="@string/summary_floating_button"
            android:title="@string/title_floating_button"
            app:iconSpaceReserved="false" />'''
addition = anchor + '''
        <CheckBoxPreference
            android:defaultValue="false"
            android:key="checkbox_reset_floating_controls_between_sessions"
            android:title="Reset floating control positions between sessions"
            android:summary="Reset the on-screen settings button, native floating menu button, and Zoom/Pan button to their default positions when a new stream starts"
            app:iconSpaceReserved="false" />'''
if anchor not in text: raise RuntimeError('floating preference anchor missing')
text = text.replace(anchor, addition, 1)
write(prefs, text)

# ---------------------------------------------------------------------------
# Regression tests: updated semantics + deterministic mixed-size snapping.
# ---------------------------------------------------------------------------
combo_test = 'app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboManagerTest.java'
text = read(combo_test)
text = re.sub(r'''    @Test\n    public void emptyManagedProfileIsBlankButLegacyProfileIsNot\(\) \{.*?\n    \}\n\n''', '', text, count=1, flags=re.S)
# Can't directly instantiate private KeyOption; assert source-visible behavior through package helper later not needed.
# Add a pure summary helper test by exposing key labels through a new package helper would overcouple UI; persistence/order tests remain.
write(combo_test, text)

snap_test = 'app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelperTest.java'
text = read(snap_test)
# Update old proportional gap case to a geometry that remains intentionally connected at 10x (40px gap / 400px = 10%).
# It already is exactly that and remains true with 12% ratio.
extra = r'''
    @Test
    public void mixedSizeAdjacentControlsSnapWithoutResizing() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 90, 40);
        View anchor = sizedView(context, 120, 100, 40, 60);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 27, 108);
        assertTrue(result.lockX);
        assertFalse(result.didResize);
        assertEquals(26, result.newX);
        assertEquals(90, result.newWidth);
        assertEquals(40, result.newHeight);
    }

    @Test
    public void nearestCandidateWinsInsteadOfLaterViewOrder() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View near = sizedView(context, 100, 100, 40, 40);
        View farther = sizedView(context, 107, 100, 40, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{near, farther}, 102, 100);
        assertEquals(100, result.newX);
    }

    @Test
    public void centerAlignmentWorksForDifferentWidths() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 80, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 121, 100);
        assertEquals(120, result.newX);
        assertTrue(result.lockX);
    }
'''
text = text.replace('\n    private static View sizedView', extra + '\n    private static View sizedView', 1)
text = text.replace('import static org.junit.Assert.assertFalse;\n', 'import static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\n')
write(snap_test, text)

# ---------------------------------------------------------------------------
# Self-cleaning: final feature tree must not contain one-shot machinery.
# ---------------------------------------------------------------------------
for temp in [Path('tools/one_shot_ui_editor_v4.py'), Path('.github/workflows/one-shot-ui-editor-v4.yml')]:
    if temp.exists():
        temp.unlink()

print('Artemis Plus UI/editor system v4 patch applied successfully.')
