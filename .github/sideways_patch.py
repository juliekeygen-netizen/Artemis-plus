from pathlib import Path


def read_text(path):
    raw = Path(path).read_bytes()
    newline = '\r\n' if b'\r\n' in raw else '\n'
    return raw.decode('utf-8').replace('\r\n', '\n'), newline


def write_text(path, text, newline):
    Path(path).write_bytes(text.replace('\n', newline).encode('utf-8'))


def replace_once(text, old, new, label):
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f'{label}: anchor not found')
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# PreferenceConfiguration: persisted default-off POC mode.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java'
text, nl = read_text(path)
text = replace_once(
    text,
    'import com.limelight.nvstream.jni.MoonBridge;\n',
    'import com.limelight.SidewaysStreamPolicy;\nimport com.limelight.nvstream.jni.MoonBridge;\n',
    'PreferenceConfiguration import')
text = replace_once(
    text,
    '    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";\n',
    '    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";\n'
    '    private static final String SIDEWAYS_STREAM_MODE_PREF_STRING = SidewaysStreamPolicy.PREF_KEY;\n',
    'PreferenceConfiguration pref key')
text = replace_once(
    text,
    '    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";\n',
    '    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";\n'
    '    private static final String DEFAULT_SIDEWAYS_STREAM_MODE = SidewaysStreamPolicy.MODE_OFF;\n',
    'PreferenceConfiguration default')
text = replace_once(
    text,
    '    public String bottomEdgeStartGestureMode;\n    public String backgroundStreamingMode;\n',
    '    public String bottomEdgeStartGestureMode;\n'
    '    public String sidewaysStreamMode;\n'
    '    public String backgroundStreamingMode;\n',
    'PreferenceConfiguration field')
text = replace_once(
    text,
    '        config.bottomEdgeStartGestureMode = prefs.getString(\n'
    '                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);\n'
    '        config.backgroundStreamingMode = prefs.getString(\n',
    '        config.bottomEdgeStartGestureMode = prefs.getString(\n'
    '                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);\n'
    '        config.sidewaysStreamMode = SidewaysStreamPolicy.sanitizeMode(prefs.getString(\n'
    '                SIDEWAYS_STREAM_MODE_PREF_STRING, DEFAULT_SIDEWAYS_STREAM_MODE));\n'
    '        config.backgroundStreamingMode = prefs.getString(\n',
    'PreferenceConfiguration read')
write_text(path, text, nl)


# ---------------------------------------------------------------------------
# Settings arrays and UI.
# ---------------------------------------------------------------------------
path = 'app/src/main/res/values/arrays.xml'
text, nl = read_text(path)
if 'name="sideways_stream_mode_names"' not in text:
    anchor = '    <string-array name="background_streaming_mode_names">\n'
    block = (
        '    <string-array name="sideways_stream_mode_names">\n'
        '        <item>Off</item>\n'
        '        <item>Sideways clockwise (experimental)</item>\n'
        '        <item>Sideways counter-clockwise (experimental)</item>\n'
        '    </string-array>\n'
        '    <string-array name="sideways_stream_mode_values" translatable="false">\n'
        '        <item>off</item>\n'
        '        <item>cw</item>\n'
        '        <item>ccw</item>\n'
        '    </string-array>\n\n')
    if anchor not in text:
        raise SystemExit('arrays sideways insertion anchor not found')
    text = text.replace(anchor, block + anchor, 1)
write_text(path, text, nl)

path = 'app/src/main/res/xml/preferences.xml'
text, nl = read_text(path)
if 'android:key="list_sideways_stream_mode"' not in text:
    anchor = '''        <CheckBoxPreference\n            android:defaultValue="false"\n            android:key="checkbox_auto_orientation"\n            android:summary="@string/summary_auto_orientation"\n            android:title="@string/title_auto_orientation"\n            app:iconSpaceReserved="false" />\n'''
    block = anchor + '''\n        <ListPreference\n            android:defaultValue="off"\n            android:entries="@array/sideways_stream_mode_names"\n            android:entryValues="@array/sideways_stream_mode_values"\n            android:key="list_sideways_stream_mode"\n            android:title="Sideways stream / short-edge system bar (experimental)"\n            android:summary="Keeps the Android stream window physically portrait, then rotates the stream and in-layout Artemis controls 90°. This POC is 2D-only, disables PiP while active, and is ignored on external displays."\n            app:iconSpaceReserved="false" />\n'''
    if anchor not in text:
        raise SystemExit('preferences sideways insertion anchor not found')
    text = text.replace(anchor, block, 1)
write_text(path, text, nl)


# ---------------------------------------------------------------------------
# Game: physical portrait + rotated logical visual root, PiP guard, logical drags.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/limelight/Game.java'
text, nl = read_text(path)
text = replace_once(
    text,
    '    private ViewParent rootView;\n    private ClipboardManager clipboardManager;\n',
    '    private ViewParent rootView;\n'
    '    private FrameLayout gamePhysicalRoot;\n'
    '    private FrameLayout gameVisualRoot;\n'
    '    private boolean sidewaysStreamActive;\n'
    '    private ClipboardManager clipboardManager;\n',
    'Game transform fields')
text = replace_once(
    text,
    '        // Inflate the content\n        setContentView(R.layout.activity_game);\n\n        clipboardManager =',
    '        // Inflate the content\n        setContentView(R.layout.activity_game);\n'
    '        gamePhysicalRoot = findViewById(R.id.gamePhysicalRoot);\n'
    '        gameVisualRoot = findViewById(R.id.gameVisualRoot);\n\n'
    '        clipboardManager =',
    'Game visual roots')
text = replace_once(
    text,
    '        onExternelDisplay = currentDisplay.getDisplayId() != Display.DEFAULT_DISPLAY;\n\n'
    '        boolean shouldInvertDecoderResolution = false;\n\n'
    '        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M\n',
    '        onExternelDisplay = currentDisplay.getDisplayId() != Display.DEFAULT_DISPLAY;\n'
    '        sidewaysStreamActive = SidewaysStreamPolicy.shouldApply(\n'
    '                prefConfig.sidewaysStreamMode, onExternelDisplay, prefConfig.renderMode);\n'
    '        if (sidewaysStreamActive) {\n'
    '            // PiP is intentionally disabled for the first sideways POC because PiP is sized in\n'
    '            // physical-window coordinates rather than the rotated logical game hierarchy.\n'
    '            prefConfig.enablePip = false;\n'
    '            FloatingControlPositionStore.setSessionLayoutSlot(\n'
    '                    this, SidewaysStreamPolicy.positionSlot(prefConfig.sidewaysStreamMode));\n'
    '        } else {\n'
    '            FloatingControlPositionStore.clearSessionLayoutSlot(this);\n'
    '        }\n\n'
    '        boolean shouldInvertDecoderResolution = false;\n\n'
    '        if (sidewaysStreamActive) {\n'
    '            // Logical content stays in the configured stream orientation. Only the Android\n'
    '            // window is portrait, so never invert the decoder just because the physical\n'
    '            // Configuration reports portrait.\n'
    '            currentOrientation = Configuration.ORIENTATION_PORTRAIT;\n'
    '            displayWidth = prefConfig.width;\n'
    '            displayHeight = prefConfig.height;\n'
    '            setPreferredOrientationForActivity();\n'
    '        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M\n',
    'Game mode compatibility and resolution')
text = replace_once(
    text,
    '        rootView = streamContainer.getParent();\n\n        //串流画面 顶部居中显示\n',
    '        rootView = streamContainer.getParent();\n'
    '        applySidewaysVisualTransform();\n\n'
    '        //串流画面 顶部居中显示\n',
    'Game transform application')
text = replace_once(
    text,
    '    private void setPreferredOrientationForActivity() {\n        Display display = getActiveDisplay(Game.this, prefConfig);\n',
    '    private void setPreferredOrientationForActivity() {\n'
    '        if (sidewaysStreamActive) {\n'
    '            currentOrientation = Configuration.ORIENTATION_PORTRAIT;\n'
    '            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);\n'
    '            return;\n'
    '        }\n\n'
    '        Display display = getActiveDisplay(Game.this, prefConfig);\n',
    'Game physical portrait orientation')
# Insert transform helpers immediately before the normal orientation policy.
marker = '    private void setPreferredOrientationForActivity() {\n'
if 'private void applySidewaysVisualTransform()' not in text:
    helper = '''    public boolean isSidewaysStreamActive() {\n        return sidewaysStreamActive;\n    }\n\n    private void refreshSidewaysDependentLayouts() {\n        if (!sidewaysStreamActive) return;\n        if (virtualController != null) virtualController.refreshLayout();\n        if (keyBoardController != null) keyBoardController.refreshLayout();\n        if (keyBoardLayoutController != null) keyBoardLayoutController.refreshLayout();\n    }\n\n    private void applySidewaysVisualTransform() {\n        if (gamePhysicalRoot == null || gameVisualRoot == null) return;\n        gamePhysicalRoot.post(() -> {\n            if (isFinishing() || gamePhysicalRoot == null || gameVisualRoot == null) return;\n            int physicalWidth = gamePhysicalRoot.getWidth();\n            int physicalHeight = gamePhysicalRoot.getHeight();\n            if (physicalWidth <= 0 || physicalHeight <= 0) return;\n\n            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) gameVisualRoot.getLayoutParams();\n            params.gravity = Gravity.CENTER;\n            if (sidewaysStreamActive) {\n                params.width = SidewaysStreamPolicy.logicalWidth(\n                        physicalWidth, physicalHeight, true);\n                params.height = SidewaysStreamPolicy.logicalHeight(\n                        physicalWidth, physicalHeight, true);\n            } else {\n                params.width = ViewGroup.LayoutParams.MATCH_PARENT;\n                params.height = ViewGroup.LayoutParams.MATCH_PARENT;\n            }\n            gameVisualRoot.setLayoutParams(params);\n            gameVisualRoot.setRotation(sidewaysStreamActive\n                    ? SidewaysStreamPolicy.rotationDegrees(prefConfig.sidewaysStreamMode)\n                    : 0f);\n            gameVisualRoot.post(() -> {\n                if (gameVisualRoot == null) return;\n                gameVisualRoot.setPivotX(gameVisualRoot.getWidth() / 2f);\n                gameVisualRoot.setPivotY(gameVisualRoot.getHeight() / 2f);\n                refreshSidewaysDependentLayouts();\n            });\n        });\n    }\n\n'''
    if marker not in text:
        raise SystemExit('Game orientation method marker missing')
    text = text.replace(marker, helper + marker, 1)
# Configuration changes must re-fit the swapped logical root after Android settles the physical size.
text = replace_once(
    text,
    '        // Set requested orientation for possible new screen size\n        setPreferredOrientationForActivity();\n',
    '        // Set requested orientation for possible new screen size\n'
    '        setPreferredOrientationForActivity();\n'
    '        applySidewaysVisualTransform();\n',
    'Game config transform refresh')
# Sideways uses logical stream width for right-half touch-sensitivity decisions.
text = text.replace(
    'if(!prefConfig.touchSensitivityGlobal&&normalizedX<getResources().getDisplayMetrics().widthPixels/2)',
    'if(!prefConfig.touchSensitivityGlobal&&normalizedX<streamContainer.getWidth()/2.0f)')
# Rotate Screen would fight the forced physical portrait policy.
text = replace_once(
    text,
    '    public void rotateScreen() {\n        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {\n',
    '    public void rotateScreen() {\n'
    '        if (sidewaysStreamActive) return;\n'
    '        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {\n',
    'Game rotate guard')
# Explicit Android PiP request guard for OEM paths that may invoke this callback directly.
text = replace_once(
    text,
    '    public boolean onPictureInPictureRequested() {\n        // Enter PiP when requested unless we\'re on Android 12 which supports auto-enter.\n',
    '    public boolean onPictureInPictureRequested() {\n'
    '        if (sidewaysStreamActive) return false;\n'
    '        // Enter PiP when requested unless we\'re on Android 12 which supports auto-enter.\n',
    'Game PiP callback guard')
# Remove session slot explicitly rather than waiting for the weak Context key to be collected.
text = replace_once(
    text,
    '    protected void onDestroy() {\n        super.onDestroy();\n\n        instance = null;\n',
    '    protected void onDestroy() {\n'
    '        super.onDestroy();\n\n'
    '        FloatingControlPositionStore.clearSessionLayoutSlot(this);\n'
    '        instance = null;\n',
    'Game position slot cleanup')
# Native floating controls: use stable logical-parent coordinates, not physical raw window coords.
text = text.replace(
    'floatingButtonStartX = event.getRawX();\n                        floatingButtonStartY = event.getRawY();\n                        floatingButtonDX = view.getX() - event.getRawX();\n                        floatingButtonDY = view.getY() - event.getRawY();',
    'floatingButtonStartX = view.getX() + event.getX();\n'
    '                        floatingButtonStartY = view.getY() + event.getY();\n'
    '                        floatingButtonDX = -event.getX();\n'
    '                        floatingButtonDY = -event.getY();')
text = text.replace(
    'float newX = event.getRawX() + floatingButtonDX;\n                        float newY = event.getRawY() + floatingButtonDY;\n\n'
    '                        // Check if it\'s a move or just a tap\n'
    '                        if (Math.abs(event.getRawX() - floatingButtonStartX) > CLICK_ACTION_THRESHOLD ||\n'
    '                                Math.abs(event.getRawY() - floatingButtonStartY) > CLICK_ACTION_THRESHOLD) {',
    'float pointerX = view.getX() + event.getX();\n'
    '                        float pointerY = view.getY() + event.getY();\n'
    '                        float newX = pointerX + floatingButtonDX;\n'
    '                        float newY = pointerY + floatingButtonDY;\n\n'
    '                        // Check if it\'s a move or just a tap\n'
    '                        if (Math.abs(pointerX - floatingButtonStartX) > CLICK_ACTION_THRESHOLD ||\n'
    '                                Math.abs(pointerY - floatingButtonStartY) > CLICK_ACTION_THRESHOLD) {')
text = text.replace(
    'int maxOffsetX = getWindow().getDecorView().getWidth() - view.getWidth();',
    'int maxOffsetX = ((View) view.getParent()).getWidth() - view.getWidth();', 2)
text = text.replace(
    'int maxOffsetY = getWindow().getDecorView().getHeight() - view.getHeight();',
    'int maxOffsetY = ((View) view.getParent()).getHeight() - view.getHeight();', 2)
text = text.replace(
    'zoomButtonStartX = event.getRawX();\n                            zoomButtonStartY = event.getRawY();\n                            zoomButtonDX = view.getX() - event.getRawX();\n                            zoomButtonDY = view.getY() - event.getRawY();',
    'zoomButtonStartX = view.getX() + event.getX();\n'
    '                            zoomButtonStartY = view.getY() + event.getY();\n'
    '                            zoomButtonDX = -event.getX();\n'
    '                            zoomButtonDY = -event.getY();')
text = text.replace(
    'float newX = event.getRawX() + zoomButtonDX;\n                            float newY = event.getRawY() + zoomButtonDY;\n\n'
    '                            // Check if it\'s a move or just a tap\n'
    '                            if (Math.abs(event.getRawX() - zoomButtonStartX) > CLICK_ACTION_THRESHOLD ||\n'
    '                                    Math.abs(event.getRawY() - zoomButtonStartY) > CLICK_ACTION_THRESHOLD) {',
    'float zoomPointerX = view.getX() + event.getX();\n'
    '                            float zoomPointerY = view.getY() + event.getY();\n'
    '                            float newX = zoomPointerX + zoomButtonDX;\n'
    '                            float newY = zoomPointerY + zoomButtonDY;\n\n'
    '                            // Check if it\'s a move or just a tap\n'
    '                            if (Math.abs(zoomPointerX - zoomButtonStartX) > CLICK_ACTION_THRESHOLD ||\n'
    '                                    Math.abs(zoomPointerY - zoomButtonStartY) > CLICK_ACTION_THRESHOLD) {')
if 'event.getRawX()' in text or 'event.getRawY()' in text:
    raise SystemExit('Game still contains raw pointer coordinates after sideways patch')
write_text(path, text, nl)


# ---------------------------------------------------------------------------
# Quick Menu: hide Rotate Screen when sideways mode owns physical orientation.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/limelight/GameMenu.java'
text, nl = read_text(path)
text = replace_once(
    text,
    '            case StreamActionRegistry.ROTATE_SCREEN:\n                if (dialogScreenContext != game) return null;\n',
    '            case StreamActionRegistry.ROTATE_SCREEN:\n'
    '                if (dialogScreenContext != game || game.isSidewaysStreamActive()) return null;\n',
    'GameMenu sideways rotate availability')
write_text(path, text, nl)


# ---------------------------------------------------------------------------
# Virtual controller sizing: actual logical parent first, transformed fallback second.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualController.java'
text, nl = read_text(path)
text = replace_once(text, 'import com.limelight.LimeLog;\n', 'import com.limelight.Game;\nimport com.limelight.LimeLog;\n', 'VirtualController Game import')
text = replace_once(
    text,
    '    public int getLayoutWidth() {\n        return frame_layout != null ? frame_layout.getWidth() : 0;\n    }\n\n'
    '    public int getLayoutHeight() {\n        return frame_layout != null ? frame_layout.getHeight() : 0;\n    }\n',
    '    public int getLayoutWidth() {\n'
    '        if (frame_layout != null && frame_layout.getWidth() > 0) return frame_layout.getWidth();\n'
    '        DisplayMetrics metrics = context.getResources().getDisplayMetrics();\n'
    '        return context instanceof Game && ((Game) context).isSidewaysStreamActive()\n'
    '                ? metrics.heightPixels : metrics.widthPixels;\n'
    '    }\n\n'
    '    public int getLayoutHeight() {\n'
    '        if (frame_layout != null && frame_layout.getHeight() > 0) return frame_layout.getHeight();\n'
    '        DisplayMetrics metrics = context.getResources().getDisplayMetrics();\n'
    '        return context instanceof Game && ((Game) context).isSidewaysStreamActive()\n'
    '                ? metrics.widthPixels : metrics.heightPixels;\n'
    '    }\n',
    'VirtualController logical dimensions')
text = text.replace(
    '        int buttonSize = (int)(screen.heightPixels*0.06f);',
    '        int buttonSize = (int)(getLayoutHeight()*0.06f);')
write_text(path, text, nl)

path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerConfigurationLoader.java'
text, nl = read_text(path)
old = '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        // Displace controls on the right by this amount of pixels to account for different aspect ratios\n        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;\n\n        int height = screen.heightPixels;\n'''
new = '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n        int layoutWidth = controller.getLayoutWidth() > 0 ? controller.getLayoutWidth() : screen.widthPixels;\n        int height = controller.getLayoutHeight() > 0 ? controller.getLayoutHeight() : screen.heightPixels;\n\n        // Displace controls on the right by this amount of pixels to account for different aspect ratios\n        int rightDisplacement = layoutWidth - height * 16 / 9;\n'''
text = replace_once(text, old, new, 'VirtualControllerConfigurationLoader logical dimensions')
write_text(path, text, nl)


# ---------------------------------------------------------------------------
# Custom keyboard editor: logical parent sizing and stable parent-space gestures.
# ---------------------------------------------------------------------------
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java'
text, nl = read_text(path)
# Add logical layout accessors before getElements().
anchor = '    public List<keyBoardVirtualControllerElement> getElements() {\n'
if 'public int getLayoutWidth()' not in text:
    methods = '''    public int getLayoutWidth() {\n        if (frame_layout != null && frame_layout.getWidth() > 0) return frame_layout.getWidth();\n        DisplayMetrics metrics = context.getResources().getDisplayMetrics();\n        return context instanceof Game && ((Game) context).isSidewaysStreamActive()\n                ? metrics.heightPixels : metrics.widthPixels;\n    }\n\n    public int getLayoutHeight() {\n        if (frame_layout != null && frame_layout.getHeight() > 0) return frame_layout.getHeight();\n        DisplayMetrics metrics = context.getResources().getDisplayMetrics();\n        return context instanceof Game && ((Game) context).isSidewaysStreamActive()\n                ? metrics.widthPixels : metrics.heightPixels;\n    }\n\n'''
    if anchor not in text:
        raise SystemExit('KeyBoardController accessor marker missing')
    text = text.replace(anchor, methods + anchor, 1)
text = text.replace(
    '        int oldButtonSize = (int) (screen.heightPixels * 0.06f);',
    '        int oldButtonSize = (int) (getLayoutHeight() * 0.06f);')
# Settings gear long-press/drag: stable logical parent coords.
text = text.replace('            private float downRawX;\n            private float downRawY;\n',
                    '            private float downParentX;\n            private float downParentY;\n')
text = text.replace(
    '                        downRawX = event.getRawX();\n                        downRawY = event.getRawY();',
    '                        downParentX = view.getX() + event.getX();\n'
    '                        downParentY = view.getY() + event.getY();')
text = text.replace(
    '                        float dx = event.getRawX() - downRawX;\n                        float dy = event.getRawY() - downRawY;',
    '                        float currentParentX = view.getX() + event.getX();\n'
    '                        float currentParentY = view.getY() + event.getY();\n'
    '                        float dx = currentParentX - downParentX;\n'
    '                        float dy = currentParentY - downParentY;')
# Explicit group movement is also parent-space rather than physical-window raw-space.
text = text.replace('    private float groupLastRawX;\n    private float groupLastRawY;\n',
                    '    private float groupLastPointerX;\n    private float groupLastPointerY;\n')
text = text.replace(
    '    void beginActiveGroupMove(float rawX, float rawY) {\n        groupLastRawX = rawX;\n        groupLastRawY = rawY;\n    }\n\n'
    '    void moveActiveGroup(float rawX, float rawY) {',
    '    void beginActiveGroupMove(float pointerX, float pointerY) {\n'
    '        groupLastPointerX = pointerX;\n'
    '        groupLastPointerY = pointerY;\n'
    '    }\n\n'
    '    void moveActiveGroup(float pointerX, float pointerY) {')
text = text.replace(
    '        int dx = Math.round(rawX - groupLastRawX);\n        int dy = Math.round(rawY - groupLastRawY);',
    '        int dx = Math.round(pointerX - groupLastPointerX);\n'
    '        int dy = Math.round(pointerY - groupLastPointerY);')
text = text.replace('        groupLastRawX = rawX;\n        groupLastRawY = rawY;',
                    '        groupLastPointerX = pointerX;\n        groupLastPointerY = pointerY;')
if 'getRawX()' in text or 'getRawY()' in text:
    raise SystemExit('KeyBoardController still contains raw pointer coordinates')
write_text(path, text, nl)

path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java'
text, nl = read_text(path)
old = '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        int height = screen.heightPixels;\n\n        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;\n'''
new = '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        int layoutWidth = controller.getLayoutWidth() > 0 ? controller.getLayoutWidth() : screen.widthPixels;\n        int height = controller.getLayoutHeight() > 0 ? controller.getLayoutHeight() : screen.heightPixels;\n\n        int rightDisplacement = layoutWidth - height * 16 / 9;\n'''
text = replace_once(text, old, new, 'KeyBoardControllerConfigurationLoader logical dimensions')
text = text.replace('        int maxW = screen.widthPixels / 18;', '        int maxW = layoutWidth / 18;')
write_text(path, text, nl)

path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java'
text, nl = read_text(path)
text = text.replace('resizeDownRawX', 'resizeDownParentX').replace('resizeDownRawY', 'resizeDownParentY')
text = text.replace('moveDownRawX', 'moveDownParentX').replace('moveDownRawY', 'moveDownParentY')
text = text.replace('private void resizeConnectedGroup(float rawX, float rawY, int localX, int localY)',
                    'private void resizeConnectedGroup(float parentX, float parentY, int localX, int localY)')
text = text.replace('(rawX - resizeDownParentX)', '(parentX - resizeDownParentX)')
text = text.replace('(rawY - resizeDownParentY)', '(parentY - resizeDownParentY)')
# ACTION_DOWN and MOVE parent-space values. Each expression remains stable while this View moves/resizes.
text = text.replace('moveDownParentX = event.getRawX();\n                    moveDownParentY = event.getRawY();',
                    'moveDownParentX = getX() + event.getX();\n'
                    '                    moveDownParentY = getY() + event.getY();')
text = text.replace('virtualController.beginActiveGroupMove(event.getRawX(), event.getRawY());',
                    'virtualController.beginActiveGroupMove(\n'
                    '                                    getX() + event.getX(), getY() + event.getY());')
text = text.replace('resizeDownParentX = event.getRawX();\n                    resizeDownParentY = event.getRawY();',
                    'resizeDownParentX = getX() + event.getX();\n'
                    '                    resizeDownParentY = getY() + event.getY();')
text = text.replace('virtualController.moveActiveGroup(event.getRawX(), event.getRawY());',
                    'virtualController.moveActiveGroup(\n'
                    '                                        getX() + event.getX(), getY() + event.getY());')
text = text.replace(
    'if (Math.abs(event.getRawX() - moveDownParentX) > touchSlop ||\n'
    '                                    Math.abs(event.getRawY() - moveDownParentY) > touchSlop) {',
    'float parentX = getX() + event.getX();\n'
    '                            float parentY = getY() + event.getY();\n'
    '                            if (Math.abs(parentX - moveDownParentX) > touchSlop ||\n'
    '                                    Math.abs(parentY - moveDownParentY) > touchSlop) {')
text = text.replace(
    'if (Math.abs(event.getRawX() - resizeDownParentX) > touchSlop ||\n'
    '                                Math.abs(event.getRawY() - resizeDownParentY) > touchSlop) {',
    'float resizeParentX = getX() + event.getX();\n'
    '                        float resizeParentY = getY() + event.getY();\n'
    '                        if (Math.abs(resizeParentX - resizeDownParentX) > touchSlop ||\n'
    '                                Math.abs(resizeParentY - resizeDownParentY) > touchSlop) {')
text = text.replace(
    'resizeConnectedGroup(\n                                event.getRawX(),\n                                event.getRawY(),',
    'resizeConnectedGroup(\n                                resizeParentX,\n                                resizeParentY,')
if 'getRawX()' in text or 'getRawY()' in text:
    raise SystemExit('keyBoardVirtualControllerElement still contains raw pointer coordinates')
write_text(path, text, nl)

# Full keyboard auto-fit should follow its logical parent rather than the physical portrait metrics.
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardLayoutController.java'
text, nl = read_text(path)
old = '''        } else {\n            DisplayMetrics screen = context.getResources().getDisplayMetrics();\n            width = screen.widthPixels;\n            height = (int) (screen.heightPixels * 0.5);\n        }\n'''
new = '''        } else {\n            DisplayMetrics screen = context.getResources().getDisplayMetrics();\n            int layoutWidth = frame_layout.getWidth() > 0 ? frame_layout.getWidth()\n                    : (context instanceof Game && ((Game) context).isSidewaysStreamActive()\n                    ? screen.heightPixels : screen.widthPixels);\n            int layoutHeight = frame_layout.getHeight() > 0 ? frame_layout.getHeight()\n                    : (context instanceof Game && ((Game) context).isSidewaysStreamActive()\n                    ? screen.widthPixels : screen.heightPixels);\n            width = layoutWidth;\n            height = (int) (layoutHeight * 0.5);\n        }\n'''
text = replace_once(text, old, new, 'KeyBoardLayoutController logical dimensions')
write_text(path, text, nl)

print('Applied sideways stream POC integration patch')
