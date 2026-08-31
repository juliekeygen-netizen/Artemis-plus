from pathlib import Path


def load(path):
    p = Path(path)
    raw = p.read_bytes()
    newline = '\r\n' if b'\r\n' in raw else '\n'
    return p, raw.decode('utf-8').replace('\r\n', '\n'), newline


def save(p, text, newline):
    p.write_bytes(text.replace('\n', newline).encode('utf-8'))


def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f'{label}: anchor not found')
    return text.replace(old, new, 1)


# --- arrays.xml ---
p, text, nl = load('app/src/main/res/values/arrays.xml')
if 'name="sideways_stream_mode_names"' not in text:
    block = '''    <string-array name="sideways_stream_mode_names">\n        <item>Off</item>\n        <item>Sideways clockwise (experimental)</item>\n        <item>Sideways counter-clockwise (experimental)</item>\n    </string-array>\n    <string-array name="sideways_stream_mode_values" translatable="false">\n        <item>off</item>\n        <item>sideways_cw</item>\n        <item>sideways_ccw</item>\n    </string-array>\n\n'''
    text = text.replace('<resources>\n', '<resources>\n' + block, 1)
save(p, text, nl)

# --- preferences.xml ---
p, text, nl = load('app/src/main/res/xml/preferences.xml')
if 'android:key="list_sideways_stream_mode"' not in text:
    marker = '''        <ListPreference\n            android:defaultValue="follow_system"\n            android:entries="@array/outside_stream_orientation_names"\n            android:entryValues="@array/outside_stream_orientation_values"\n            android:key="list_outside_stream_orientation"\n            android:title="Outside stream orientation"\n            android:summary="Controls Artemis screens when no stream is open. Follow system respects Android rotation lock; Portrait keeps Artemis menus upright."\n            app:iconSpaceReserved="false" />\n'''
    addition = marker + '''        <ListPreference\n            android:defaultValue="off"\n            android:entries="@array/sideways_stream_mode_names"\n            android:entryValues="@array/sideways_stream_mode_values"\n            android:key="list_sideways_stream_mode"\n            android:title="Sideways stream orientation (experimental)"\n            android:summary="Keeps the Android stream Activity physically portrait so system bars stay on a short edge, while rotating the 2D stream and in-stream controls into landscape. Ignored for 3D and external displays; PiP and manual stream rotation are disabled while active."\n            app:iconSpaceReserved="false" />\n'''
    if marker not in text:
        raise SystemExit('preferences.xml: outside-orientation marker not found')
    text = text.replace(marker, addition, 1)
save(p, text, nl)

# --- activity_game.xml: physical portrait root + one logical visual root ---
p, text, nl = load('app/src/main/res/layout/activity_game.xml')
if 'android:id="@+id/gamePhysicalRoot"' not in text:
    old_open = '''<merge xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"\n    xmlns:app="http://schemas.android.com/apk/res-auto"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    tools:context=".Game" >\n'''
    new_open = '''<com.limelight.ui.SidewaysStreamLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"\n    xmlns:app="http://schemas.android.com/apk/res-auto"\n    android:id="@+id/gamePhysicalRoot"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    android:clipChildren="false"\n    android:clipToPadding="false"\n    tools:context=".Game">\n\n    <FrameLayout\n        android:id="@+id/streamVisualRoot"\n        android:layout_width="match_parent"\n        android:layout_height="match_parent"\n        android:clipChildren="false"\n        android:clipToPadding="false">\n'''
    if old_open not in text or not text.rstrip().endswith('</merge>'):
        raise SystemExit('activity_game.xml: merge root shape changed')
    text = text.replace(old_open, new_open, 1)
    text = text.rstrip()[:-len('</merge>')].rstrip() + '\n\n    </FrameLayout>\n</com.limelight.ui.SidewaysStreamLayout>\n'
save(p, text, nl)

# --- PreferenceConfiguration.java ---
p, text, nl = load('app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java')
text = replace_once(text,
    '    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";\n',
    '    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";\n    private static final String SIDEWAYS_STREAM_MODE_PREF_STRING = "list_sideways_stream_mode";\n',
    'PreferenceConfiguration sideways key')
text = replace_once(text,
    '    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";\n',
    '    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";\n    private static final String DEFAULT_SIDEWAYS_STREAM_MODE = "off";\n',
    'PreferenceConfiguration sideways default')
text = replace_once(text,
    '    public String bottomEdgeStartGestureMode;\n    public String backgroundStreamingMode;\n',
    '    public String bottomEdgeStartGestureMode;\n    public String sidewaysStreamMode;\n    public String backgroundStreamingMode;\n',
    'PreferenceConfiguration sideways field')
text = replace_once(text,
    '''        config.bottomEdgeStartGestureMode = prefs.getString(\n                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);\n        config.backgroundStreamingMode = prefs.getString(\n''',
    '''        config.bottomEdgeStartGestureMode = prefs.getString(\n                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);\n        config.sidewaysStreamMode = prefs.getString(\n                SIDEWAYS_STREAM_MODE_PREF_STRING, DEFAULT_SIDEWAYS_STREAM_MODE);\n        config.backgroundStreamingMode = prefs.getString(\n''',
    'PreferenceConfiguration sideways read')
save(p, text, nl)

# --- Game.java ---
p, text, nl = load('app/src/main/java/com/limelight/Game.java')
text = replace_once(text,
    'import com.limelight.ui.StreamContainer;\n',
    'import com.limelight.ui.StreamContainer;\nimport com.limelight.ui.SidewaysStreamLayout;\n',
    'Game sideways layout import')
text = replace_once(text,
    '    private ViewParent rootView;\n    private ClipboardManager clipboardManager;\n',
    '    private ViewParent rootView;\n    private SidewaysStreamLayout sidewaysStreamLayout;\n    private String activeSidewaysStreamMode = SidewaysStreamMode.MODE_OFF;\n    private ClipboardManager clipboardManager;\n',
    'Game sideways fields')
text = replace_once(text,
    '        // Inflate the content\n        setContentView(R.layout.activity_game);\n\n        clipboardManager',
    '        // Inflate the content\n        setContentView(R.layout.activity_game);\n        sidewaysStreamLayout = findViewById(R.id.gamePhysicalRoot);\n\n        clipboardManager',
    'Game physical root lookup')

if 'activeSidewaysStreamMode = SidewaysStreamMode.resolveSessionMode' not in text:
    start = text.find('        boolean shouldInvertDecoderResolution = false;')
    end_marker = '\n\n\n        if (\n                prefConfig.videoScaleMode == PreferenceConfiguration.ScaleMode.STRETCH'
    end = text.find(end_marker, start)
    if start < 0 or end < 0:
        raise SystemExit('Game orientation block not found')
    new_block = '''        boolean shouldInvertDecoderResolution = false;\n\n        activeSidewaysStreamMode = SidewaysStreamMode.resolveSessionMode(\n                prefConfig.sidewaysStreamMode, prefConfig.renderMode, onExternelDisplay);\n        // StreamContainer reads the session-resolved value so unsupported 3D/external-display\n        // sessions never switch away from the existing SurfaceView path.\n        prefConfig.sidewaysStreamMode = activeSidewaysStreamMode;\n        if (sidewaysStreamLayout != null) {\n            sidewaysStreamLayout.setSidewaysMode(activeSidewaysStreamMode);\n        }\n\n        if (isSidewaysStreamActive()) {\n            // Android stays physically portrait; the logical stream canvas remains landscape.\n            prefConfig.enablePip = false;\n            currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            displayWidth = prefConfig.width;\n            displayHeight = prefConfig.height;\n            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M\n                && onExternelDisplay\n                && prefConfig.renderMode == 0 // For 3D we want to maintain configured resolution\n        ) {\n            Display.Mode currentMode = currentDisplay.getMode();\n            displayWidth = currentMode.getPhysicalWidth();\n            displayHeight = currentMode.getPhysicalHeight();\n            prefConfig.width = displayWidth;\n            prefConfig.height = displayHeight;\n            prefConfig.fps = currentMode.getRefreshRate();\n            prefConfig.videoScaleMode = PreferenceConfiguration.ScaleMode.STRETCH;\n            prefConfig.enableFloatingButton = false;\n            prefConfig.showOverlayZoomToggleButton = false;\n            prefConfig.enablePip = false;\n            currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);\n        } else {\n            if (prefConfig.renderMode != 0) {\n                prefConfig.videoScaleMode = PreferenceConfiguration.ScaleMode.STRETCH;\n            }\n\n            if (prefConfig.autoOrientation) {\n                currentOrientation = getResources().getConfiguration().orientation;\n            } else {\n                currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            }\n\n            boolean portraitMode = currentOrientation == Configuration.ORIENTATION_PORTRAIT;\n            shouldInvertDecoderResolution = portraitMode && prefConfig.autoInvertVideoResolution;\n\n            displayWidth = shouldInvertDecoderResolution ? prefConfig.height : prefConfig.width;\n            displayHeight = shouldInvertDecoderResolution ? prefConfig.width : prefConfig.height;\n\n            // Enter landscape unless we're on a square screen\n            setPreferredOrientationForActivity();\n        }'''
    text = text[:start] + new_block + text[end:]

text = text.replace('streamContainer.getSurfaceView(),', 'streamContainer.getRenderView(),')

text = replace_once(text,
    '    private void setPreferredOrientationForActivity() {\n        Display display = getActiveDisplay(Game.this, prefConfig);\n',
    '''    private void setPreferredOrientationForActivity() {\n        if (isSidewaysStreamActive()) {\n            currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {\n                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);\n            }\n            return;\n        }\n\n        Display display = getActiveDisplay(Game.this, prefConfig);\n''',
    'Game preferred orientation guard')

text = replace_once(text,
    '''    public boolean isOnExternalDisplay() {\n        return onExternelDisplay;\n    }\n\n    private float prepareDisplayForRendering''',
    '''    public boolean isOnExternalDisplay() {\n        return onExternelDisplay;\n    }\n\n    public boolean isSidewaysStreamActive() {\n        return SidewaysStreamMode.isActive(activeSidewaysStreamMode);\n    }\n\n    public String getActiveSidewaysStreamMode() {\n        return activeSidewaysStreamMode;\n    }\n\n    public SidewaysStreamMode.LogicalPoint mapRawToStreamCoordinates(float rawX, float rawY) {\n        if (sidewaysStreamLayout == null || !isSidewaysStreamActive()) {\n            return new SidewaysStreamMode.LogicalPoint(rawX, rawY);\n        }\n        return sidewaysStreamLayout.mapRawToLogical(rawX, rawY);\n    }\n\n    private float streamRawX(MotionEvent event) {\n        return mapRawToStreamCoordinates(event.getRawX(), event.getRawY()).x;\n    }\n\n    private float streamRawY(MotionEvent event) {\n        return mapRawToStreamCoordinates(event.getRawX(), event.getRawY()).y;\n    }\n\n    private float prepareDisplayForRendering''',
    'Game sideways accessors')

# Only Game's two floating-control drag listeners use raw coordinates. Route them through the
# logical-canvas mapper while leaving all local View event coordinates untouched.
text = text.replace('event.getRawX()', 'streamRawX(event)')
text = text.replace('event.getRawY()', 'streamRawY(event)')
# The helper methods above must call the real MotionEvent raw accessors, not recursively call self.
text = text.replace('mapRawToStreamCoordinates(streamRawX(event), streamRawY(event)).x',
                    'mapRawToStreamCoordinates(event.getRawX(), event.getRawY()).x')
text = text.replace('mapRawToStreamCoordinates(streamRawX(event), streamRawY(event)).y',
                    'mapRawToStreamCoordinates(event.getRawX(), event.getRawY()).y')

text = text.replace(
    '        if (!prefConfig.enablePip || isOnExternalDisplay()) {\n            return;\n        }',
    '        if (!prefConfig.enablePip || isOnExternalDisplay() || isSidewaysStreamActive()) {\n            return;\n        }',
    1)
text = replace_once(text,
    '    public void rotateScreen() {\n        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {\n',
    '    public void rotateScreen() {\n        if (isSidewaysStreamActive()) {\n            return;\n        }\n        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {\n',
    'Game rotateScreen guard')

# SurfaceHolder callbacks delegate to Surface-based equivalents used by TextureView.
if 'public void streamSurfaceChanged(int format, int width, int height)' not in text:
    old = '''    @Override\n    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {\n        if (!surfaceCreated) {'''
    new = '''    @Override\n    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {\n        streamSurfaceChanged(format, width, height);\n    }\n\n    public void streamSurfaceChanged(int format, int width, int height) {\n        if (!surfaceCreated) {'''
    text = replace_once(text, old, new, 'Game Surface changed bridge')

if 'public void streamSurfaceCreated(Surface surface)' not in text:
    old = '''    @Override\n    public void surfaceCreated(SurfaceHolder holder) {\n        float desiredFrameRate;'''
    new = '''    @Override\n    public void surfaceCreated(SurfaceHolder holder) {\n        streamSurfaceCreated(holder.getSurface());\n    }\n\n    public void streamSurfaceCreated(Surface surface) {\n        float desiredFrameRate;'''
    text = replace_once(text, old, new, 'Game Surface created bridge')
    # Restrict replacement to the surface-created method's frame-rate calls; this source currently
    # has exactly these two holder Surface uses in that method.
    surface_created_start = text.find('    public void streamSurfaceCreated(Surface surface)')
    surface_destroyed_start = text.find('    @Override\n    public void surfaceDestroyed', surface_created_start)
    segment = text[surface_created_start:surface_destroyed_start]
    segment = segment.replace('holder.getSurface().setFrameRate(', 'surface.setFrameRate(')
    text = text[:surface_created_start] + segment + text[surface_destroyed_start:]

if 'public void streamSurfaceDestroyed()' not in text:
    old = '''    @Override\n    public void surfaceDestroyed(SurfaceHolder holder) {\n        if (!surfaceCreated) {'''
    new = '''    @Override\n    public void surfaceDestroyed(SurfaceHolder holder) {\n        streamSurfaceDestroyed();\n    }\n\n    public void streamSurfaceDestroyed() {\n        if (!surfaceCreated) {'''
    text = replace_once(text, old, new, 'Game Surface destroyed bridge')

if 'streamRawX(event)' not in text or 'streamRawY(event)' not in text:
    raise SystemExit('Game raw-coordinate routing missing')
save(p, text, nl)

# --- ArtemisOrientationHelper: manual rotate must never break the physical-portrait invariant. ---
p, text, nl = load('app/src/main/java/com/limelight/ArtemisOrientationHelper.java')
text = text.replace(
    'if (game == null || game.isFinishing() || game.isDestroyed() || game.isOnExternalDisplay()) {',
    'if (game == null || game.isFinishing() || game.isDestroyed() || game.isOnExternalDisplay() ||\n                game.isSidewaysStreamActive()) {',
    1)
save(p, text, nl)

# --- GameMenu: hide Rotate Screen in sideways mode. ---
p, text, nl = load('app/src/main/java/com/limelight/GameMenu.java')
text = text.replace(
    '                if (dialogScreenContext != game) return null;\n',
    '                if (dialogScreenContext != game || game.isSidewaysStreamActive()) return null;\n',
    1)
save(p, text, nl)

# --- FloatingControlPositionStore: sideways CW/CCW have distinct persistence slots. ---
p, text, nl = load('app/src/main/java/com/limelight/ui/FloatingControlPositionStore.java')
text = replace_once(text,
    'import android.view.ViewParent;\n\nimport androidx.preference.PreferenceManager;\n',
    'import android.view.ViewParent;\n\nimport androidx.preference.PreferenceManager;\n\nimport com.limelight.Game;\nimport com.limelight.SidewaysStreamMode;\n',
    'Floating store sideways imports')
text = text.replace(
    'for (String orientation : new String[]{"portrait", "landscape"}) {',
    'for (String orientation : new String[]{"portrait", "landscape",\n                SidewaysStreamMode.MODE_CW, SidewaysStreamMode.MODE_CCW}) {')
old_key = '''    private static String key(Context context, String identity, String axis) {\n        int orientation = context.getResources().getConfiguration().orientation;\n        String orientationName = orientation == Configuration.ORIENTATION_PORTRAIT\n                ? "portrait" : "landscape";\n        return identity + "_" + orientationName + "_" + axis;\n    }'''
new_key = '''    private static String key(Context context, String identity, String axis) {\n        int orientation = context.getResources().getConfiguration().orientation;\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        String orientationName = SidewaysStreamMode.positionSlot(mode, orientation);\n        return identity + "_" + orientationName + "_" + axis;\n    }'''
text = replace_once(text, old_key, new_key, 'Floating store key mode')
save(p, text, nl)

# --- VirtualController: use logical parent geometry, not physical portrait display metrics. ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualController.java')
text = replace_once(text,
    'import com.limelight.LimeLog;\nimport com.limelight.R;\n',
    'import com.limelight.Game;\nimport com.limelight.LimeLog;\nimport com.limelight.R;\nimport com.limelight.SidewaysStreamMode;\n',
    'VirtualController sideways imports')
old_dims = '''    public int getLayoutWidth() {\n        return frame_layout != null ? frame_layout.getWidth() : 0;\n    }\n\n    public int getLayoutHeight() {\n        return frame_layout != null ? frame_layout.getHeight() : 0;\n    }'''
new_dims = '''    public int getLayoutWidth() {\n        if (frame_layout != null && frame_layout.getWidth() > 0) {\n            return frame_layout.getWidth();\n        }\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalWidth(screen.widthPixels, screen.heightPixels, mode);\n    }\n\n    public int getLayoutHeight() {\n        if (frame_layout != null && frame_layout.getHeight() > 0) {\n            return frame_layout.getHeight();\n        }\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalHeight(screen.widthPixels, screen.heightPixels, mode);\n    }'''
text = replace_once(text, old_dims, new_dims, 'VirtualController logical dimensions')
text = text.replace(
    '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n\n        int buttonSize = (int)(screen.heightPixels*0.06f);''',
    '''        int buttonSize = (int)(getLayoutHeight()*0.06f);''',
    1)
save(p, text, nl)

# --- VirtualControllerConfigurationLoader: default OSC layout uses logical root dimensions. ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/VirtualControllerConfigurationLoader.java')
old = '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        // Displace controls on the right by this amount of pixels to account for different aspect ratios\n        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;\n\n        int height = screen.heightPixels;'''
new = '''        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n        int logicalWidth = controller.getLayoutWidth();\n        int height = controller.getLayoutHeight();\n\n        // Displace controls on the right by this amount of pixels to account for different aspect ratios\n        int rightDisplacement = logicalWidth - height * 16 / 9;'''
text = replace_once(text, old, new, 'OSC logical default geometry')
save(p, text, nl)

# --- KeyBoardController: logical sizing + mapped raw editor drags. ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java')
text = replace_once(text,
    'import com.limelight.Game;\n',
    'import com.limelight.Game;\nimport com.limelight.SidewaysStreamMode;\n',
    'Keyboard controller sideways import')
# Stable configure-button long-press drag: convert raw physical coordinates once per event.
text = replace_once(text,
    '''                    case MotionEvent.ACTION_DOWN:\n                        downRawX = event.getRawX();\n                        downRawY = event.getRawY();\n                        startViewX = view.getX();''',
    '''                    case MotionEvent.ACTION_DOWN:\n                        SidewaysStreamMode.LogicalPoint downPoint = mapRaw(event);\n                        downRawX = downPoint.x;\n                        downRawY = downPoint.y;\n                        startViewX = view.getX();''',
    'Keyboard configure drag down mapping')
text = replace_once(text,
    '''                    case MotionEvent.ACTION_MOVE:\n                        float dx = event.getRawX() - downRawX;\n                        float dy = event.getRawY() - downRawY;''',
    '''                    case MotionEvent.ACTION_MOVE:\n                        SidewaysStreamMode.LogicalPoint movePoint = mapRaw(event);\n                        float dx = movePoint.x - downRawX;\n                        float dy = movePoint.y - downRawY;''',
    'Keyboard configure drag move mapping')
text = text.replace('Math.abs(event.getRawX() - downRawX)', 'Math.abs(movePoint.x - downRawX)', 1)
text = text.replace('Math.abs(event.getRawY() - downRawY)', 'Math.abs(movePoint.y - downRawY)', 1)
text = replace_once(text,
    '    private void resetConfigureButtonPosition() {\n',
    '''    private SidewaysStreamMode.LogicalPoint mapRaw(MotionEvent event) {\n        Game game = Game.instance;\n        if (game != null) {\n            return game.mapRawToStreamCoordinates(event.getRawX(), event.getRawY());\n        }\n        return new SidewaysStreamMode.LogicalPoint(event.getRawX(), event.getRawY());\n    }\n\n    private int logicalLayoutWidth() {\n        if (frame_layout.getWidth() > 0) return frame_layout.getWidth();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalWidth(screen.widthPixels, screen.heightPixels, mode);\n    }\n\n    private int logicalLayoutHeight() {\n        if (frame_layout.getHeight() > 0) return frame_layout.getHeight();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalHeight(screen.widthPixels, screen.heightPixels, mode);\n    }\n\n    private void resetConfigureButtonPosition() {\n''',
    'Keyboard logical geometry helpers')
text = text.replace(
    '''        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        int oldButtonSize = (int) (screen.heightPixels * 0.06f);''',
    '''        int logicalWidth = logicalLayoutWidth();\n        int logicalHeight = logicalLayoutHeight();\n        int oldButtonSize = (int) (logicalHeight * 0.06f);''',
    1)
text = text.replace('screen.widthPixels / 2 - totalWidth / 2', 'logicalWidth / 2 - totalWidth / 2', 1)
text = text.replace('screen.widthPixels / 2 - buttonProfiles.getMeasuredWidth() / 2',
                    'logicalWidth / 2 - buttonProfiles.getMeasuredWidth() / 2', 1)
text = text.replace('screen.heightPixels - buttonProfiles.getMeasuredHeight() - 20',
                    'logicalHeight - buttonProfiles.getMeasuredHeight() - 20', 1)
text = text.replace('screen.widthPixels / 2 - buttonAcceptGroupMove.getMeasuredWidth() / 2',
                    'logicalWidth / 2 - buttonAcceptGroupMove.getMeasuredWidth() / 2', 1)
save(p, text, nl)

# --- custom keyboard element: map the raw-only group move/resize bookkeeping. ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/keyBoardVirtualControllerElement.java')
if 'import com.limelight.Game;' not in text:
    marker = 'import android.widget.FrameLayout;\n\n'
    text = text.replace(marker,
                        'import android.widget.FrameLayout;\n\nimport com.limelight.Game;\nimport com.limelight.SidewaysStreamMode;\n\n', 1)
text = replace_once(text,
    '    @Override\n    public boolean onTouchEvent(MotionEvent event) {\n',
    '''    private SidewaysStreamMode.LogicalPoint mapRaw(MotionEvent event) {\n        Game game = Game.instance;\n        if (game != null) {\n            return game.mapRawToStreamCoordinates(event.getRawX(), event.getRawY());\n        }\n        return new SidewaysStreamMode.LogicalPoint(event.getRawX(), event.getRawY());\n    }\n\n    @Override\n    public boolean onTouchEvent(MotionEvent event) {\n''',
    'Keyboard element raw mapper')
text = replace_once(text,
    '''            case MotionEvent.ACTION_DOWN: {\n                position_pressed_x = event.getX();''',
    '''            case MotionEvent.ACTION_DOWN: {\n                SidewaysStreamMode.LogicalPoint rawPoint = mapRaw(event);\n                position_pressed_x = event.getX();''',
    'Keyboard element down point')
text = text.replace('moveDownRawX = event.getRawX();', 'moveDownRawX = rawPoint.x;', 1)
text = text.replace('moveDownRawY = event.getRawY();', 'moveDownRawY = rawPoint.y;', 1)
text = text.replace('virtualController.beginActiveGroupMove(event.getRawX(), event.getRawY());',
                    'virtualController.beginActiveGroupMove(rawPoint.x, rawPoint.y);', 1)
text = text.replace('resizeDownRawX = event.getRawX();', 'resizeDownRawX = rawPoint.x;', 1)
text = text.replace('resizeDownRawY = event.getRawY();', 'resizeDownRawY = rawPoint.y;', 1)
text = replace_once(text,
    '''            case MotionEvent.ACTION_MOVE: {\n                switch (currentMode) {''',
    '''            case MotionEvent.ACTION_MOVE: {\n                SidewaysStreamMode.LogicalPoint rawPoint = mapRaw(event);\n                switch (currentMode) {''',
    'Keyboard element move point')
text = text.replace('virtualController.moveActiveGroup(event.getRawX(), event.getRawY());',
                    'virtualController.moveActiveGroup(rawPoint.x, rawPoint.y);', 1)
text = text.replace('Math.abs(event.getRawX() - moveDownRawX)', 'Math.abs(rawPoint.x - moveDownRawX)', 1)
text = text.replace('Math.abs(event.getRawY() - moveDownRawY)', 'Math.abs(rawPoint.y - moveDownRawY)', 1)
text = text.replace('Math.abs(event.getRawX() - resizeDownRawX)', 'Math.abs(rawPoint.x - resizeDownRawX)', 1)
text = text.replace('Math.abs(event.getRawY() - resizeDownRawY)', 'Math.abs(rawPoint.y - resizeDownRawY)', 1)
text = text.replace(
    '''                        resizeConnectedGroup(\n                                event.getRawX(),\n                                event.getRawY(),''',
    '''                        resizeConnectedGroup(\n                                rawPoint.x,\n                                rawPoint.y,''',
    1)
save(p, text, nl)

# --- full keyboard: logical autofit geometry and no wrongly-oriented separate PopupWindow preview. ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardLayoutController.java')
text = text.replace(
    'if (!TextUtils.equals("hide", tag) && !_isSpecialKey) {',
    'if (!TextUtils.equals("hide", tag) && !_isSpecialKey &&\n                            (Game.instance == null || !Game.instance.isSidewaysStreamActive())) {',
    1)
old = '''        } else {\n            DisplayMetrics screen = context.getResources().getDisplayMetrics();\n            width = screen.widthPixels;\n            height = (int) (screen.heightPixels * 0.5);\n        }'''
new = '''        } else {\n            width = logicalLayoutWidth();\n            height = (int) (logicalLayoutHeight() * 0.5f);\n        }'''
text = replace_once(text, old, new, 'Full keyboard logical autofit')
text = replace_once(text,
    '    public int dip2px(Context context, float dpValue) {\n',
    '''    private int logicalLayoutWidth() {\n        if (frame_layout != null && frame_layout.getWidth() > 0) return frame_layout.getWidth();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        if (Game.instance != null && Game.instance.isSidewaysStreamActive()) {\n            return screen.heightPixels;\n        }\n        return screen.widthPixels;\n    }\n\n    private int logicalLayoutHeight() {\n        if (frame_layout != null && frame_layout.getHeight() > 0) return frame_layout.getHeight();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        if (Game.instance != null && Game.instance.isSidewaysStreamActive()) {\n            return screen.widthPixels;\n        }\n        return screen.heightPixels;\n    }\n\n    public int dip2px(Context context, float dpValue) {\n''',
    'Full keyboard logical dimension helpers')
save(p, text, nl)

print('Applied sideways stream POC integration patch')
