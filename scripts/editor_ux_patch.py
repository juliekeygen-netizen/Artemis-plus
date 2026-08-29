from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected patch anchor not found in {path}: {old[:120]!r}")
    text = text.replace(old, new, 1)
    p.write_text(text, encoding="utf-8")


# -----------------------------------------------------------------------------
# 1. True visual text centering + content-aware minimum width.
# -----------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardDigitalButton.java"
replace_once(path,
'''import android.graphics.Paint;
import android.graphics.RectF;
''',
'''import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
''')

replace_once(path,
'''    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
''',
'''    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private final Rect textBounds = new Rect();

    /** Minimum bubble width that keeps text readable while never shrinking below a square. */
    static int minimumWidthForText(Context context, String value, int height) {
        int safeHeight = Math.max(20, height);
        if (value == null || value.isEmpty()) {
            return safeHeight;
        }
        Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        measurePaint.setTextSize(safeHeight * 0.25f);
        float density = context.getResources().getDisplayMetrics().density;
        float horizontalPadding = Math.max(8f * density, safeHeight * 0.20f);
        int required = (int) Math.ceil(measurePaint.measureText(value) + horizontalPadding * 2f);
        return Math.max(safeHeight, required);
    }

    /** Baseline that centers the glyphs that are actually drawn, not the font's full line box. */
    static float visualTextBaseline(Paint paint, Rect reusableBounds, String value, float centerY) {
        if (value == null || value.isEmpty()) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            return centerY - (metrics.ascent + metrics.descent) / 2f;
        }
        reusableBounds.setEmpty();
        paint.getTextBounds(value, 0, value.length(), reusableBounds);
        if (reusableBounds.isEmpty()) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            return centerY - (metrics.ascent + metrics.descent) / 2f;
        }
        return centerY - (reusableBounds.top + reusableBounds.bottom) / 2f;
    }
''')

replace_once(path,
'''        paint.setTextSize(getPercent(getWidth(), 25));
''',
'''        // Text size follows the short edge, so widening a long-label bubble does not recursively
        // make the text larger and wider again.
        paint.setTextSize(getPercent(Math.min(getWidth(), getHeight()), 25));
''')

replace_once(path,
'''            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
''',
'''            float baseline = visualTextBaseline(paint, textBounds, text, getHeight() / 2f);
            canvas.drawText(text, getWidth() / 2f, baseline, paint);
''')


# -----------------------------------------------------------------------------
# 2. Key-combo bubbles auto-grow after edits/manual resize and reflow their group.
# -----------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyComboButton.java"
replace_once(path,
'''        setText(displayName);
    }
''',
'''        setText(displayName);
        post(() -> {
            if (virtualController != null) {
                virtualController.ensureTextButtonWidth(this, displayName);
            }
        });
    }
''')

replace_once(path,
'''        if (virtualController == null ||
                virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons) {
            return super.onTouchEvent(event);
        }
''',
'''        if (virtualController == null ||
                virtualController.getControllerMode() != KeyBoardController.ControllerMode.DisableEnableButtons) {
            boolean handled = super.onTouchEvent(event);
            if ((event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) && virtualController != null) {
                post(() -> virtualController.ensureTextButtonWidth(this, displayName));
            }
            return handled;
        }
''')


# -----------------------------------------------------------------------------
# 3. Group outline state, content-width reflow, and deletion helper in controller.
# -----------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java"
replace_once(path,
'''    private final List<keyBoardVirtualControllerElement> activeMoveGroup = new ArrayList<>();
    private float groupLastRawX;
''',
'''    private final List<keyBoardVirtualControllerElement> activeMoveGroup = new ArrayList<>();
    private final List<keyBoardVirtualControllerElement> outlinedGroup = new ArrayList<>();
    private float groupLastRawX;
''')

replace_once(path,
'''    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }
''',
'''    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    void removeElement(keyBoardVirtualControllerElement element) {
        if (element == null) {
            return;
        }
        elements.remove(element);
        activeMoveGroup.remove(element);
        outlinedGroup.remove(element);
        frame_layout.removeView(element);
        context.getSharedPreferences(
                KeyboardProfilesManager.getActiveStorageName(context),
                Context.MODE_PRIVATE)
                .edit()
                .remove(element.elementId)
                .apply();
        if (outlinedGroup.size() <= 1) {
            clearGestureGroupOutline();
        } else {
            updateGroupOutline();
        }
    }

    /**
     * Grow a text button horizontally when its label no longer fits. Controls connected to its
     * right edge are shifted as a unit so the wider bubble cannot overlap the rest of the group.
     */
    void ensureTextButtonWidth(KeyBoardDigitalButton element, String text) {
        if (element == null || !(element.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
        int targetWidth = KeyBoardDigitalButton.minimumWidthForText(context, text, params.height);
        if (targetWidth <= params.width) {
            return;
        }

        List<keyBoardVirtualControllerElement> group = getConnectedGroup(element);
        if (group.isEmpty()) {
            group.add(element);
        }
        int oldRight = params.leftMargin + params.width;
        int delta = targetWidth - params.width;
        int elementTop = params.topMargin;
        int elementBottom = params.topMargin + params.height;
        params.width = targetWidth;
        element.requestLayout();

        for (keyBoardVirtualControllerElement other : group) {
            if (other == element || !(other.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams otherParams = (FrameLayout.LayoutParams) other.getLayoutParams();
            int overlap = Math.min(elementBottom, otherParams.topMargin + otherParams.height) -
                    Math.max(elementTop, otherParams.topMargin);
            int minHeight = Math.min(params.height, otherParams.height);
            if (otherParams.leftMargin >= oldRight - 6 && overlap >= minHeight * 0.35f) {
                otherParams.leftMargin += delta;
                other.requestLayout();
            }
        }

        int parentWidth = frame_layout.getWidth() > 0
                ? frame_layout.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        Rect bounds = getBounds(group);
        int shiftLeft = Math.max(0, bounds.right - parentWidth);
        if (shiftLeft > 0) {
            int available = Math.min(shiftLeft, bounds.left);
            for (keyBoardVirtualControllerElement member : group) {
                FrameLayout.LayoutParams memberParams =
                        (FrameLayout.LayoutParams) member.getLayoutParams();
                memberParams.leftMargin = Math.max(0, memberParams.leftMargin - available);
                member.requestLayout();
            }
        }

        element.invalidate();
        KeyBoardControllerConfigurationLoader.saveProfile(this, context);
    }
''')

replace_once(path,
'''        activeMoveGroup.clear();
        activeMoveGroup.addAll(group);
        buttonAcceptGroupMove.setVisibility(View.VISIBLE);
        updateGroupOutline();
''',
'''        activeMoveGroup.clear();
        activeMoveGroup.addAll(group);
        outlinedGroup.clear();
        outlinedGroup.addAll(group);
        buttonAcceptGroupMove.setVisibility(View.VISIBLE);
        updateGroupOutline();
''')

replace_once(path,
'''        activeMoveGroup.clear();
        if (buttonAcceptGroupMove != null) {
            buttonAcceptGroupMove.setVisibility(View.GONE);
        }
        if (groupOutline != null) {
            groupOutline.setVisibility(View.GONE);
        }
''',
'''        activeMoveGroup.clear();
        outlinedGroup.clear();
        if (buttonAcceptGroupMove != null) {
            buttonAcceptGroupMove.setVisibility(View.GONE);
        }
        if (groupOutline != null) {
            groupOutline.setVisibility(View.GONE);
        }
''')

replace_once(path,
'''    private void updateGroupOutline() {
        if (activeMoveGroup.isEmpty() || groupOutline == null) {
            return;
        }
        Rect bounds = getBounds(activeMoveGroup);
''',
'''    void showGestureGroupOutline(List<keyBoardVirtualControllerElement> group) {
        if (isGroupMoveModeActive()) {
            outlinedGroup.clear();
            outlinedGroup.addAll(activeMoveGroup);
            updateGroupOutline();
            return;
        }
        outlinedGroup.clear();
        if (group != null) {
            for (keyBoardVirtualControllerElement element : group) {
                if (element != null && !element.hidden && element.getVisibility() == View.VISIBLE) {
                    outlinedGroup.add(element);
                }
            }
        }
        if (outlinedGroup.size() <= 1) {
            clearGestureGroupOutline();
            return;
        }
        updateGroupOutline();
    }

    void showSnapGroupOutline(keyBoardVirtualControllerElement seed) {
        showGestureGroupOutline(getConnectedGroup(seed));
    }

    void clearGestureGroupOutline() {
        if (isGroupMoveModeActive()) {
            outlinedGroup.clear();
            outlinedGroup.addAll(activeMoveGroup);
            updateGroupOutline();
            return;
        }
        outlinedGroup.clear();
        if (groupOutline != null) {
            groupOutline.setVisibility(View.GONE);
        }
    }

    boolean isElementCoveredByGroupOutline(keyBoardVirtualControllerElement element) {
        return groupOutline != null && groupOutline.getVisibility() == View.VISIBLE &&
                outlinedGroup.contains(element);
    }

    private void updateGroupOutline() {
        if (outlinedGroup.size() <= 1 || groupOutline == null || groupOutline.getLayoutParams() == null) {
            if (groupOutline != null) {
                groupOutline.setVisibility(View.GONE);
            }
            return;
        }
        Rect bounds = getBounds(outlinedGroup);
''')

replace_once(path,
'''        for (keyBoardVirtualControllerElement element : activeMoveGroup) {
            element.bringToFront();
        }
        buttonAcceptGroupMove.bringToFront();
''',
'''        for (keyBoardVirtualControllerElement element : outlinedGroup) {
            element.bringToFront();
        }
        if (buttonAcceptGroupMove.getVisibility() == View.VISIBLE) {
            buttonAcceptGroupMove.bringToFront();
        }
''')


# -----------------------------------------------------------------------------
# 4. Snap hysteresis and transient group feedback while moving/resizing.
# -----------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelper.java"
replace_once(path,
'''        public boolean didAdjustSpacing;

        public SnapResult(int x, int y, int width, int height, boolean snapped, boolean resized, boolean adjustedSpacing) {
''',
'''        public boolean didAdjustSpacing;
        /** Axis locks let the editor keep a snapped relationship until the user deliberately pulls away. */
        public boolean lockX;
        public boolean lockY;

        public SnapResult(int x, int y, int width, int height, boolean snapped, boolean resized, boolean adjustedSpacing) {
''')

replace_once(path,
'''        boolean didAdjustSpacing = false;

        FrameLayout.LayoutParams movingParams = (FrameLayout.LayoutParams) movingView.getLayoutParams();
''',
'''        boolean didAdjustSpacing = false;
        boolean lockX = false;
        boolean lockY = false;

        FrameLayout.LayoutParams movingParams = (FrameLayout.LayoutParams) movingView.getLayoutParams();
''')

replace_once(path,
'''                    snappedX = otherParams.leftMargin + otherView.getWidth() + SPACING_MIN;
                    didAdjustSpacing = true;
''',
'''                    snappedX = otherParams.leftMargin + otherView.getWidth() + SPACING_MIN;
                    didAdjustSpacing = true;
                    lockX = true;
''')
replace_once(path,
'''                    snappedX = otherParams.leftMargin - SPACING_MIN - movingWidth;
                    didAdjustSpacing = true;
''',
'''                    snappedX = otherParams.leftMargin - SPACING_MIN - movingWidth;
                    didAdjustSpacing = true;
                    lockX = true;
''')
replace_once(path,
'''                    snappedY = otherParams.topMargin + otherView.getHeight() + SPACING_MIN;
                    didAdjustSpacing = true;
''',
'''                    snappedY = otherParams.topMargin + otherView.getHeight() + SPACING_MIN;
                    didAdjustSpacing = true;
                    lockY = true;
''')
replace_once(path,
'''                    snappedY = otherParams.topMargin - SPACING_MIN - movingHeight;
                    didAdjustSpacing = true;
''',
'''                    snappedY = otherParams.topMargin - SPACING_MIN - movingHeight;
                    didAdjustSpacing = true;
                    lockY = true;
''')
replace_once(path,
'''                snappedX = otherParams.leftMargin;
                didSnap = true;
''',
'''                snappedX = otherParams.leftMargin;
                didSnap = true;
                lockX = true;
''')
replace_once(path,
'''                snappedX = otherParams.leftMargin + otherView.getWidth() - movingWidth;
                didSnap = true;
''',
'''                snappedX = otherParams.leftMargin + otherView.getWidth() - movingWidth;
                didSnap = true;
                lockX = true;
''')
replace_once(path,
'''                snappedY = otherParams.topMargin;
                didSnap = true;
''',
'''                snappedY = otherParams.topMargin;
                didSnap = true;
                lockY = true;
''')
replace_once(path,
'''                snappedY = otherParams.topMargin + otherView.getHeight() - movingHeight;
                didSnap = true;
''',
'''                snappedY = otherParams.topMargin + otherView.getHeight() - movingHeight;
                didSnap = true;
                lockY = true;
''')
replace_once(path,
'''        return new SnapResult(snappedX, snappedY, newWidth, newHeight, didSnap, didResize, didAdjustSpacing);
''',
'''        SnapResult result = new SnapResult(
                snappedX, snappedY, newWidth, newHeight, didSnap, didResize, didAdjustSpacing);
        result.lockX = lockX;
        result.lockY = lockY;
        return result;
''')

path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java"
replace_once(path,
'''    private long lastMoveTapUpTime;

    private static final class GroupResizeSnapshot {
''',
'''    private long lastMoveTapUpTime;
    private boolean stickyMoveX;
    private boolean stickyMoveY;
    private int stickyMoveAnchorX;
    private int stickyMoveAnchorY;

    private static final class GroupResizeSnapshot {
''')

old_move = '''    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        lastMoveX = newPos_x;
        lastMoveY = newPos_y;

        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons) {
            View[] otherViews = new View[virtualController.getElements().size() - 1];
            int index = 0;
            for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
                if (element != this) {
                    otherViews[index++] = element;
                }
            }

            LayoutSnappingHelper.SnapResult snapResult = LayoutSnappingHelper.calculateSnappedPosition(
                    this, otherViews, newPos_x, newPos_y
            );

            newPos_x = snapResult.newX;
            newPos_y = snapResult.newY;

            if (snapResult.didSnap || snapResult.didAdjustSpacing) {
                virtualController.vibrate(KeyEvent.ACTION_DOWN);
            }
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.leftMargin = Math.max(0, newPos_x);
        layoutParams.topMargin = Math.max(0, newPos_y);
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;
        requestLayout();
    }
'''
new_move = '''    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        // Once an axis snaps, keep it attached for a larger release distance than the initial snap
        // threshold. This hysteresis prevents tiny finger wobble from immediately tearing a control
        // back out of its group while still allowing deliberate movement parallel to the joined edge.
        int releaseThreshold = Math.max(18,
                Math.round(28 * getResources().getDisplayMetrics().density));
        if (stickyMoveX) {
            if (Math.abs(newPos_x - stickyMoveAnchorX) < releaseThreshold) {
                newPos_x = stickyMoveAnchorX;
            } else {
                stickyMoveX = false;
            }
        }
        if (stickyMoveY) {
            if (Math.abs(newPos_y - stickyMoveAnchorY) < releaseThreshold) {
                newPos_y = stickyMoveAnchorY;
            } else {
                stickyMoveY = false;
            }
        }

        lastMoveX = newPos_x;
        lastMoveY = newPos_y;
        boolean newlyLocked = false;

        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons) {
            View[] otherViews = new View[virtualController.getElements().size() - 1];
            int index = 0;
            for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
                if (element != this) {
                    otherViews[index++] = element;
                }
            }

            boolean wasStickyX = stickyMoveX;
            boolean wasStickyY = stickyMoveY;
            LayoutSnappingHelper.SnapResult snapResult = LayoutSnappingHelper.calculateSnappedPosition(
                    this, otherViews, newPos_x, newPos_y
            );

            newPos_x = snapResult.newX;
            newPos_y = snapResult.newY;
            if (snapResult.lockX) {
                stickyMoveX = true;
                stickyMoveAnchorX = newPos_x;
            }
            if (snapResult.lockY) {
                stickyMoveY = true;
                stickyMoveAnchorY = newPos_y;
            }
            newlyLocked = (snapResult.lockX && !wasStickyX) || (snapResult.lockY && !wasStickyY);
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.leftMargin = Math.max(0, newPos_x);
        layoutParams.topMargin = Math.max(0, newPos_y);
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;
        requestLayout();

        if (stickyMoveX || stickyMoveY) {
            virtualController.showSnapGroupOutline(this);
            if (newlyLocked) {
                virtualController.vibrate(KeyEvent.ACTION_DOWN);
            }
        } else {
            virtualController.clearGestureGroupOutline();
        }
    }
'''
replace_once(path, old_move, new_move)

replace_once(path,
'''        resizeGroup = snapshots;
        resizeGroupOriginX = minLeft;
''',
'''        resizeGroup = snapshots;
        virtualController.showGestureGroupOutline(connected);
        resizeGroupOriginX = minLeft;
''')

replace_once(path,
'''            snapshot.element.requestLayout();
            snapshot.element.invalidate();
        }
    }
''',
'''            snapshot.element.requestLayout();
            snapshot.element.invalidate();
        }
        List<keyBoardVirtualControllerElement> outlined = new ArrayList<>(resizeGroup.size());
        for (GroupResizeSnapshot snapshot : resizeGroup) {
            outlined.add(snapshot.element);
        }
        virtualController.showGestureGroupOutline(outlined);
    }
''')

replace_once(path,
'''    private void clearGroupedResize() {
        resizeGroup = null;
    }
''',
'''    private void clearGroupedResize() {
        resizeGroup = null;
        if (virtualController != null) {
            virtualController.clearGestureGroupOutline();
        }
    }
''')

replace_once(path,
'''        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
''',
'''        if (currentMode != Mode.Normal &&
                (virtualController == null || !virtualController.isElementCoveredByGroupOutline(this))) {
            paint.setColor(configSelectedColor);
''')

replace_once(path,
'''                    moveDownRawX = event.getRawX();
                    moveDownRawY = event.getRawY();
                    moveGestureMoved = false;
''',
'''                    moveDownRawX = event.getRawX();
                    moveDownRawY = event.getRawY();
                    moveGestureMoved = false;
                    stickyMoveX = false;
                    stickyMoveY = false;
''')

replace_once(path,
'''                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || moveGestureMoved) {
                            lastMoveTapUpTime = 0;
''',
'''                        stickyMoveX = false;
                        stickyMoveY = false;
                        virtualController.clearGestureGroupOutline();
                        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || moveGestureMoved) {
                            lastMoveTapUpTime = 0;
''')


# -----------------------------------------------------------------------------
# 5. Empty managed profiles must be truly blank instead of resurrecting every legacy control.
# -----------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java"
replace_once(path,
'''    public static void loadFromPreferences(final KeyBoardController controller, final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
''',
'''    static boolean shouldTreatManagedProfileAsBlank(String storageName, boolean hasSavedGeometry) {
        return storageName != null &&
                storageName.startsWith("ArtemisKeyboardProfile_") &&
                !hasSavedGeometry;
    }

    public static void loadFromPreferences(final KeyBoardController controller, final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
        boolean blankManagedProfile = shouldTreatManagedProfileAsBlank(name, !pref.getAll().isEmpty());

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            } else if (blankManagedProfile) {
                // New Artemis Plus profiles are intentionally blank. Previously an empty profile
                // fell through to every legacy default control's enabled/visible state, layering a
                // dense set of touch-consuming Views over the stream and making input appear dead.
                element.hidden = true;
                element.enabled = true;
                element.setVisibility(View.GONE);
            }
        }
    }
''')

# add View import for GONE
replace_once(path,
'''import android.view.KeyEvent;
import android.widget.Toast;
''',
'''import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
''')

print("Editor UX/input patch applied successfully")
