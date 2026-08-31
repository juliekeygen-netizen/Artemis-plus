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
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    return text.replace(old, new, 1)


def replace_exact_count(text, old, new, expected, label):
    count = text.count(old)
    if count == 0 and text.count(new) == expected:
        return text
    if count != expected:
        raise SystemExit(f'{label}: expected {expected} anchors, found {count}')
    return text.replace(old, new)


# --- SidewaysStreamMode.java: share/test logical drag clamping policy ---
p, text, nl = load('app/src/main/java/com/limelight/SidewaysStreamMode.java')
text = replace_once(
    text,
    '''    public static int logicalHeight(int physicalWidth, int physicalHeight, String mode) {\n        return isActive(mode) ? physicalWidth : physicalHeight;\n    }\n\n    /**\n     * Converts absolute screen/raw coordinates''',
    '''    public static int logicalHeight(int physicalWidth, int physicalHeight, String mode) {\n        return isActive(mode) ? physicalWidth : physicalHeight;\n    }\n\n    /** Maximum top/left coordinate that keeps a child fully inside its logical parent. */\n    public static float clampChildPosition(float position, int parentSize, int childSize) {\n        int max = Math.max(0, parentSize - childSize);\n        return Math.max(0f, Math.min(position, max));\n    }\n\n    /**\n     * Converts absolute screen/raw coordinates''',
    'Sideways logical clamp helper')
save(p, text, nl)


# --- Game.java: floating controls are in logical-root coordinates, so clamp to that parent ---
p, text, nl = load('app/src/main/java/com/limelight/Game.java')
old_bounds = '''                            // Ensure the button stays within screen bounds\n                            if (newX < 0) newX = 0;\n                            if (newY < 0) newY = 0;\n\n                            int maxOffsetX = getWindow().getDecorView().getWidth() - view.getWidth();\n                            if (newX > maxOffsetX) {\n                                newX = maxOffsetX;\n                            }\n\n                            int maxOffsetY = getWindow().getDecorView().getHeight() - view.getHeight();\n                            if (newY > maxOffsetY) {\n                                newY = maxOffsetY;\n                            }\n\n                            view.setX(newX);\n                            view.setY(newY);\n'''
new_bounds = '''                            // Drag coordinates are logical stream-root coordinates in sideways mode.\n                            // Clamp against the View's actual parent rather than the physical portrait\n                            // DecorView, otherwise the long and short axes are swapped.\n                            ViewParent dragParent = view.getParent();\n                            int parentWidth = dragParent instanceof View\n                                    ? ((View) dragParent).getWidth()\n                                    : getWindow().getDecorView().getWidth();\n                            int parentHeight = dragParent instanceof View\n                                    ? ((View) dragParent).getHeight()\n                                    : getWindow().getDecorView().getHeight();\n                            newX = SidewaysStreamMode.clampChildPosition(\n                                    newX, parentWidth, view.getWidth());\n                            newY = SidewaysStreamMode.clampChildPosition(\n                                    newY, parentHeight, view.getHeight());\n\n                            view.setX(newX);\n                            view.setY(newY);\n'''
text = replace_exact_count(text, old_bounds, new_bounds, 2,
                           'Game floating control logical bounds')
save(p, text, nl)


# --- KeyBoardController.java: expose the same logical dimensions its editor chrome uses ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java')
text = replace_once(
    text,
    '''    private int logicalLayoutHeight() {\n        if (frame_layout.getHeight() > 0) return frame_layout.getHeight();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalHeight(screen.widthPixels, screen.heightPixels, mode);\n    }\n\n    private void resetConfigureButtonPosition() {''',
    '''    private int logicalLayoutHeight() {\n        if (frame_layout.getHeight() > 0) return frame_layout.getHeight();\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n        String mode = Game.instance != null\n                ? Game.instance.getActiveSidewaysStreamMode()\n                : SidewaysStreamMode.MODE_OFF;\n        return SidewaysStreamMode.logicalHeight(screen.widthPixels, screen.heightPixels, mode);\n    }\n\n    int getLayoutWidth() {\n        return logicalLayoutWidth();\n    }\n\n    int getLayoutHeight() {\n        return logicalLayoutHeight();\n    }\n\n    private void resetConfigureButtonPosition() {''',
    'Keyboard controller logical dimension accessors')
save(p, text, nl)


# --- Keyboard default loader: stop mixing physical portrait metrics with logical editor geometry ---
p, text, nl = load('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java')
text = replace_once(
    text,
    '''    public static void createDefaultLayout(final KeyBoardController controller, final Context context, final NvConnection conn) {\n\n        DisplayMetrics screen = context.getResources().getDisplayMetrics();\n\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        int height = screen.heightPixels;\n\n        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;\n\n        int BUTTON_SIZE = 10;\n\n        int w = screenScale(BUTTON_SIZE, height);\n\n        int maxW = screen.widthPixels / 18;\n''',
    '''    public static void createDefaultLayout(final KeyBoardController controller, final Context context, final NvConnection conn) {\n\n        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);\n\n        // This controller lives inside the logical stream root. In sideways mode Android's\n        // Resources metrics are physical portrait dimensions, so using them here would build the\n        // actual keys in a different coordinate space from the editor chrome.\n        int logicalWidth = controller.getLayoutWidth();\n        int height = controller.getLayoutHeight();\n\n        int rightDisplacement = logicalWidth - height * 16 / 9;\n\n        int BUTTON_SIZE = 10;\n\n        int w = screenScale(BUTTON_SIZE, height);\n\n        int maxW = logicalWidth / 18;\n''',
    'Keyboard default logical dimensions')
save(p, text, nl)


# --- SidewaysStreamModeTest.java: permanently cover logical child bounds ---
p, text, nl = load('app/src/test/java/com/limelight/SidewaysStreamModeTest.java')
insert_before = '''    @Test\n    public void floatingPositionSlotsDoNotCollide() {\n'''
new_test = '''    @Test\n    public void logicalChildPositionClampUsesParentCoordinateSpace() {\n        assertEquals(0f, SidewaysStreamMode.clampChildPosition(-20f, 200, 40), 0.001f);\n        assertEquals(75f, SidewaysStreamMode.clampChildPosition(75f, 200, 40), 0.001f);\n        assertEquals(160f, SidewaysStreamMode.clampChildPosition(190f, 200, 40), 0.001f);\n        assertEquals(0f, SidewaysStreamMode.clampChildPosition(20f, 30, 40), 0.001f);\n    }\n\n'''
if 'logicalChildPositionClampUsesParentCoordinateSpace' not in text:
    if insert_before not in text:
        raise SystemExit('SidewaysStreamModeTest clamp insertion anchor missing')
    text = text.replace(insert_before, new_test + insert_before, 1)
save(p, text, nl)

print('Fourth sideways audit patch applied successfully')
