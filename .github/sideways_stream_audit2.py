from pathlib import Path
import re


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
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    return text.replace(old, new, 1)


# --- SidewaysStreamMode.java: make TextureView/SDR policy explicit and testable ---
p, text, nl = load('app/src/main/java/com/limelight/SidewaysStreamMode.java')
text = replace_once(
    text,
    '''    public static boolean isActive(String mode) {\n        return MODE_CW.equals(mode) || MODE_CCW.equals(mode);\n    }\n\n    public static float rotationDegrees(String mode) {\n''',
    '''    public static boolean isActive(String mode) {\n        return MODE_CW.equals(mode) || MODE_CCW.equals(mode);\n    }\n\n    /** Sideways 2D uses TextureView for rotation; keep HDR on the normal SurfaceView path. */\n    public static boolean shouldForceSdr(String mode) {\n        return isActive(mode);\n    }\n\n    public static float rotationDegrees(String mode) {\n''',
    'Sideways force-SDR policy')
save(p, text, nl)


# --- Game.java: resolve/lock the physical orientation before inflating TextureView ---
p, text, nl = load('app/src/main/java/com/limelight/Game.java')

prefs_anchor = '''        // Read the stream preferences\n        prefConfig = PreferenceConfiguration.readPreferences(this);\n        bottomEdgeStartGestureDetector = new BottomEdgeStartGestureDetector(\n                getResources().getDisplayMetrics().density);\n'''
early_block = prefs_anchor + '''\n        // Resolve the target display and experimental sideways policy before inflating the render\n        // tree. A TextureView can become available immediately after inflation, so requesting the\n        // physical portrait Activity here prevents a transient landscape Surface from racing stream\n        // startup before Android applies the sideways mode's fixed portrait window.\n        Display currentDisplay = null;\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {\n            int displayId = getIntent().getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY);\n            currentDisplay = getSystemService(DisplayManager.class).getDisplay(displayId);\n        }\n        if (currentDisplay == null) {\n            currentDisplay = getWindowManager().getDefaultDisplay();\n        }\n        onExternelDisplay = currentDisplay.getDisplayId() != Display.DEFAULT_DISPLAY;\n\n        activeSidewaysStreamMode = SidewaysStreamMode.resolveSessionMode(\n                prefConfig.sidewaysStreamMode, prefConfig.renderMode, onExternelDisplay);\n        // StreamContainer reads the resolved value, so unsupported renderers/displays retain the\n        // established SurfaceView path.\n        prefConfig.sidewaysStreamMode = activeSidewaysStreamMode;\n        if (isSidewaysStreamActive()) {\n            prefConfig.enablePip = false;\n            if (SidewaysStreamMode.shouldForceSdr(activeSidewaysStreamMode)) {\n                // Rotated TextureView composition is not a reliable HDR presentation path across\n                // Android/vendor stacks. Preserve HDR on the normal SurfaceView path instead.\n                prefConfig.enableHdr = false;\n            }\n            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);\n        }\n'''
text = replace_once(text, prefs_anchor, early_block, 'Game early sideways resolution')

# Remove the now-duplicated display lookup that used to run after setContentView/spinner creation.
display_lookup = re.compile(
    r'''\n        Display currentDisplay = null;\n        if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.M\) \{\n            int displayId = getIntent\(\)\.getIntExtra\(EXTRA_DISPLAY_ID, Display\.DEFAULT_DISPLAY\);\n            currentDisplay = getSystemService\(DisplayManager\.class\)\.getDisplay\(displayId\);\n        \}\n\n        if \(currentDisplay == null\) \{\n            currentDisplay = getWindowManager\(\)\.getDefaultDisplay\(\);\n        \}\n\n        onExternelDisplay = currentDisplay\.getDisplayId\(\) != Display\.DEFAULT_DISPLAY;\n''')
text, count = display_lookup.subn('', text, count=1)
if count != 1:
    raise SystemExit(f'Game late display lookup: expected one block, found {count}')

# Session mode is resolved above before inflation now. Keep only applying it to the inflated root.
late_resolve = '''        activeSidewaysStreamMode = SidewaysStreamMode.resolveSessionMode(\n                prefConfig.sidewaysStreamMode, prefConfig.renderMode, onExternelDisplay);\n        // StreamContainer reads the session-resolved value so unsupported 3D/external-display\n        // sessions never switch away from the existing SurfaceView path.\n        prefConfig.sidewaysStreamMode = activeSidewaysStreamMode;\n'''
if late_resolve not in text:
    raise SystemExit('Game late sideways resolution block not found')
text = text.replace(late_resolve, '', 1)

# Physical portrait/PiP/HDR policy was already established before inflation. The later block now only
# defines Artemis's logical stream orientation and decoder dimensions.
text = replace_once(
    text,
    '''        if (isSidewaysStreamActive()) {\n            // Android stays physically portrait; the logical stream canvas remains landscape.\n            prefConfig.enablePip = false;\n            currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            displayWidth = prefConfig.width;\n            displayHeight = prefConfig.height;\n            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M\n''',
    '''        if (isSidewaysStreamActive()) {\n            // Android is already physically portrait; Artemis's logical stream stays landscape.\n            currentOrientation = Configuration.ORIENTATION_LANDSCAPE;\n            displayWidth = prefConfig.width;\n            displayHeight = prefConfig.height;\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M\n''',
    'Game late sideways logical-orientation block')

# Root application still belongs after setContentView, but it must use the already-resolved mode.
if '''        sidewaysStreamLayout = findViewById(R.id.gamePhysicalRoot);\n''' not in text:
    raise SystemExit('Game sideways root lookup missing')
if '''            sidewaysStreamLayout.setSidewaysMode(activeSidewaysStreamMode);\n''' not in text:
    raise SystemExit('Game sideways root mode application missing')

save(p, text, nl)


# --- SidewaysStreamModeTest.java: cover force-SDR policy and all physical corners ---
p, text, nl = load('app/src/test/java/com/limelight/SidewaysStreamModeTest.java')
insert_before = '''    @Test\n    public void floatingPositionSlotsDoNotCollide() {\n'''
new_tests = '''    @Test\n    public void sidewaysTextureViewSessionsForceSdrOnlyWhenActive() {\n        assertFalse(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_OFF));\n        assertTrue(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_CW));\n        assertTrue(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_CCW));\n    }\n\n    @Test\n    public void physicalCornersMapToLogicalLandscapeCorners() {\n        assertPoint(0f, 100f, SidewaysStreamMode.physicalRawToLogical(\n                0f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));\n        assertPoint(0f, 0f, SidewaysStreamMode.physicalRawToLogical(\n                100f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));\n        assertPoint(200f, 100f, SidewaysStreamMode.physicalRawToLogical(\n                0f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));\n        assertPoint(200f, 0f, SidewaysStreamMode.physicalRawToLogical(\n                100f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));\n\n        assertPoint(200f, 0f, SidewaysStreamMode.physicalRawToLogical(\n                0f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));\n        assertPoint(200f, 100f, SidewaysStreamMode.physicalRawToLogical(\n                100f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));\n        assertPoint(0f, 0f, SidewaysStreamMode.physicalRawToLogical(\n                0f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));\n        assertPoint(0f, 100f, SidewaysStreamMode.physicalRawToLogical(\n                100f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));\n    }\n\n    private static void assertPoint(float x, float y, SidewaysStreamMode.LogicalPoint point) {\n        assertEquals(x, point.x, 0.001f);\n        assertEquals(y, point.y, 0.001f);\n    }\n\n'''
if 'sidewaysTextureViewSessionsForceSdrOnlyWhenActive' not in text:
    if insert_before not in text:
        raise SystemExit('SidewaysStreamModeTest insertion anchor missing')
    text = text.replace(insert_before, new_tests + insert_before, 1)
save(p, text, nl)


# --- preferences.xml: disclose SDR and composition cost for the experimental path ---
p, text, nl = load('app/src/main/res/xml/preferences.xml')
old_summary = 'android:summary="Keeps the Android stream Activity physically portrait so system bars stay on a short edge, while rotating the 2D stream and in-stream controls into landscape. Ignored for 3D and external displays; PiP and manual stream rotation are disabled while active."'
new_summary = 'android:summary="Experimental 2D/internal-display mode. Keeps Android physically portrait so system bars stay on a short edge while rotating the stream and controls into landscape. Uses TextureView, disables HDR and PiP, and may use slightly more GPU/battery. Ignored for 3D and external displays."'
text = replace_once(text, old_summary, new_summary, 'Sideways preference summary')
save(p, text, nl)

print('Second sideways audit patch applied successfully')
