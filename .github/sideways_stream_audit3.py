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


# --- Game.java: apply visual rotation before spinner and expose the logical root to in-stream UI ---
p, text, nl = load('app/src/main/java/com/limelight/Game.java')
text = replace_once(
    text,
    '''        // Inflate the content\n        setContentView(R.layout.activity_game);\n        sidewaysStreamLayout = findViewById(R.id.gamePhysicalRoot);\n\n        clipboardManager''',
    '''        // Inflate the content\n        setContentView(R.layout.activity_game);\n        sidewaysStreamLayout = findViewById(R.id.gamePhysicalRoot);\n        if (sidewaysStreamLayout != null) {\n            // Apply the logical landscape transform before any stream-session UI (including the\n            // connection spinner) can become visible.\n            sidewaysStreamLayout.setSidewaysMode(activeSidewaysStreamMode);\n        }\n\n        clipboardManager''',
    'Game early visual-root mode')

late = '''        if (sidewaysStreamLayout != null) {\n            sidewaysStreamLayout.setSidewaysMode(activeSidewaysStreamMode);\n        }\n\n        if (isSidewaysStreamActive()) {\n'''
text = replace_once(text, late, '''        if (isSidewaysStreamActive()) {\n''', 'Game remove duplicate visual-root mode')

getter_anchor = '''    public String getActiveSidewaysStreamMode() {\n        return activeSidewaysStreamMode;\n    }\n\n    public SidewaysStreamMode.LogicalPoint mapRawToStreamCoordinates'''
getter_replacement = '''    public String getActiveSidewaysStreamMode() {\n        return activeSidewaysStreamMode;\n    }\n\n    /** Logical in-stream root. Sideways-safe menus/overlays must attach here, not to a new Window. */\n    public FrameLayout getStreamVisualRoot() {\n        return rootView instanceof FrameLayout ? (FrameLayout) rootView : null;\n    }\n\n    public SidewaysStreamMode.LogicalPoint mapRawToStreamCoordinates'''
text = replace_once(text, getter_anchor, getter_replacement, 'Game visual-root getter')
save(p, text, nl)


# --- GameMenu.java: use an in-stream overlay when the Activity is physically portrait/sideways ---
p, text, nl = load('app/src/main/java/com/limelight/GameMenu.java')
text = replace_once(text,
    'import android.view.ContextThemeWrapper;\n',
    'import android.view.ContextThemeWrapper;\nimport android.view.Gravity;\nimport android.view.View;\nimport android.view.ViewGroup;\n',
    'GameMenu View imports')
text = replace_once(text,
    'import android.widget.LinearLayout;\n',
    'import android.widget.FrameLayout;\nimport android.widget.LinearLayout;\n',
    'GameMenu FrameLayout import')
text = replace_once(text,
    '''    private final Context dialogScreenContext;\n    private AlertDialog currentDialog;\n''',
    '''    private final Context dialogScreenContext;\n    private AlertDialog currentDialog;\n    private View currentOverlay;\n''',
    'GameMenu overlay field')

show_anchor = '''    private void showMenuDialog(String title, MenuOption[] options) {\n        Context ui = ArtemisEditorUi.context(dialogScreenContext);\n'''
show_replacement = '''    private boolean shouldUseSidewaysOverlay() {\n        return dialogScreenContext == game && game.isSidewaysStreamActive() &&\n                game.getStreamVisualRoot() != null;\n    }\n\n    private void showMenuDialog(String title, MenuOption[] options) {\n        if (shouldUseSidewaysOverlay()) {\n            showSidewaysOverlay(title, null, options, getString(R.string.game_menu_cancel));\n            return;\n        }\n\n        Context ui = ArtemisEditorUi.context(dialogScreenContext);\n'''
text = replace_once(text, show_anchor, show_replacement, 'GameMenu sideways dispatch')

# Insert overlay renderer after showMenuDialog and before special keys.
insert_anchor = '''    private void showSpecialKeysMenu() {\n'''
overlay_methods = r'''    private void showSidewaysOverlay(String title,
                                     String message,
                                     MenuOption[] options,
                                     String fallbackCancelLabel) {
        FrameLayout root = game.getStreamVisualRoot();
        if (root == null) {
            return;
        }

        hideMenu();

        Context ui = ArtemisEditorUi.context(game);
        FrameLayout overlay = new FrameLayout(ui);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0xB8000000);
        overlay.setOnClickListener(v -> hideMenu());

        int logicalWidth = root.getWidth() > 0
                ? root.getWidth() : game.getResources().getDisplayMetrics().heightPixels;
        int logicalHeight = root.getHeight() > 0
                ? root.getHeight() : game.getResources().getDisplayMetrics().widthPixels;
        int widthCap = Math.max(ArtemisEditorUi.dp(ui, 280),
                Math.round(logicalWidth * 0.90f));
        int panelWidth = Math.min(ArtemisEditorUi.dp(ui, 460), widthCap);
        panelWidth = Math.min(panelWidth, Math.max(1, logicalWidth - ArtemisEditorUi.dp(ui, 24)));
        int panelHeight = Math.min(ArtemisEditorUi.dp(ui, 620),
                Math.max(ArtemisEditorUi.dp(ui, 220), Math.round(logicalHeight * 0.86f)));
        panelHeight = Math.min(panelHeight, Math.max(1, logicalHeight - ArtemisEditorUi.dp(ui, 24)));

        LinearLayout panel = new LinearLayout(ui);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true); // Do not let taps inside the card hit the dismissing scrim.
        panel.setBackground(ArtemisEditorUi.rounded(
                ui, ArtemisEditorUi.SURFACE, 14, 1, ArtemisEditorUi.BORDER));

        TextView header = ArtemisEditorUi.header(ui, title);
        panel.addView(header);

        if (message != null && !message.isEmpty()) {
            TextView body = ArtemisEditorUi.label(
                    ui, message, 14f, ArtemisEditorUi.TEXT_SECONDARY);
            body.setPadding(ArtemisEditorUi.dp(ui, 20), ArtemisEditorUi.dp(ui, 14),
                    ArtemisEditorUi.dp(ui, 20), ArtemisEditorUi.dp(ui, 14));
            panel.addView(body, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout rows = new LinearLayout(ui);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6));
        String cancelLabel = fallbackCancelLabel;
        if (options != null) {
            for (MenuOption option : options) {
                if (option == null) continue;
                if (option.runnable == null) {
                    cancelLabel = option.label;
                    continue;
                }
                TextView row = ArtemisEditorUi.menuRow(ui, option.label);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
                rowParams.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0,
                        ArtemisEditorUi.dp(ui, 2));
                rows.addView(row, rowParams);
                row.setOnClickListener(v -> {
                    hideMenu();
                    run(option);
                });
            }
        }

        ScrollView scroll = new ScrollView(ui);
        scroll.setFillViewport(false);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView cancel = ArtemisEditorUi.menuRow(ui, cancelLabel);
        cancel.setTextColor(ArtemisEditorUi.TEXT_SECONDARY);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
        cancelParams.setMargins(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 4),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 10));
        panel.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> hideMenu());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth, panelHeight);
        panelParams.gravity = Gravity.CENTER;
        overlay.addView(panel, panelParams);

        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
        currentOverlay = overlay;
    }

    private void showSidewaysMessage(String title, String message) {
        showSidewaysOverlay(title, message, new MenuOption[0],
                getString(android.R.string.ok));
    }

'''
text = replace_once(text, insert_anchor, overlay_methods + insert_anchor,
                    'GameMenu overlay methods')

empty_anchor = '''        if (serverCmds.isEmpty()) {\n            int themeResId = game.getApplicationInfo().theme;\n'''
empty_replacement = '''        if (serverCmds.isEmpty()) {\n            if (shouldUseSidewaysOverlay()) {\n                showSidewaysMessage(\n                        getString(R.string.game_dialog_title_server_cmd_empty),\n                        getString(R.string.game_dialog_message_server_cmd_empty));\n                return;\n            }\n            int themeResId = game.getApplicationInfo().theme;\n'''
text = replace_once(text, empty_anchor, empty_replacement,
                    'GameMenu empty server-command overlay')

hide_anchor = '''    @Override\n    public void hideMenu() {\n        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();\n        currentDialog = null;\n    }\n\n    @Override\n    public boolean isMenuOpen() {\n        return currentDialog != null && currentDialog.isShowing();\n    }\n'''
hide_replacement = '''    @Override\n    public void hideMenu() {\n        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();\n        currentDialog = null;\n        if (currentOverlay != null) {\n            ViewParent parent = currentOverlay.getParent();\n            if (parent instanceof ViewGroup) {\n                ((ViewGroup) parent).removeView(currentOverlay);\n            }\n            currentOverlay = null;\n        }\n    }\n\n    @Override\n    public boolean isMenuOpen() {\n        return (currentDialog != null && currentDialog.isShowing()) ||\n                (currentOverlay != null && currentOverlay.getParent() != null);\n    }\n'''
text = replace_once(text, hide_anchor, hide_replacement, 'GameMenu overlay cleanup')

# ViewParent is used by cleanup.
text = replace_once(text,
    'import android.view.ViewGroup;\n',
    'import android.view.ViewGroup;\nimport android.view.ViewParent;\n',
    'GameMenu ViewParent import')
save(p, text, nl)


# --- BackgroundStreamingPolicy.java: correct stale SurfaceView-only comment ---
p, text, nl = load('app/src/main/java/com/limelight/preferences/BackgroundStreamingPolicy.java')
old = '''        // MediaCodec.setOutputSurface() was added in Android 6.0. The first POC is intentionally\n        // limited to the normal 2D SurfaceView path; Artemis' stereo modes own a separate GL\n        // Surface lifecycle and transparently fall back to Fast Resume for now.\n'''
new = '''        // MediaCodec.setOutputSurface() was added in Android 6.0. Keep Alive is intentionally\n        // limited to the normal 2D MediaCodec path (SurfaceView or sideways TextureView); Artemis'\n        // stereo modes own a separate GL Surface lifecycle and transparently fall back to Fast Resume.\n'''
text = replace_once(text, old, new, 'BackgroundStreamingPolicy renderer comment')
save(p, text, nl)

print('Third sideways audit patch applied successfully')
