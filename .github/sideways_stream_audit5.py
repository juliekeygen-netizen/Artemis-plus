from pathlib import Path

p = Path('app/src/main/java/com/limelight/Game.java')
raw = p.read_bytes()
newline = '\r\n' if b'\r\n' in raw else '\n'
text = raw.decode('utf-8').replace('\r\n', '\n')

old = '''    @Override\n    public void toggleKeyboard() {\n        if (isOnExternalDisplay()) {\n            ExternalDisplayControlActivity.toggleKeyboard();\n        } else {\n            LimeLog.info("Toggling keyboard overlay");\n            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);\n            inputManager.toggleSoftInput(0, 0);\n        }\n    }\n'''
new = '''    @Override\n    public void toggleKeyboard() {\n        if (isOnExternalDisplay()) {\n            ExternalDisplayControlActivity.toggleKeyboard();\n        } else if (isSidewaysStreamActive()) {\n            // The Android IME belongs to the physically portrait Activity window and therefore\n            // cannot inherit streamVisualRoot's 90-degree transform. Use Artemis's in-stream\n            // keyboard instead so the default keyboard action remains upright in sideways mode.\n            LimeLog.info("Using in-stream keyboard for sideways mode");\n            toggleFullKeyboard();\n        } else {\n            LimeLog.info("Toggling keyboard overlay");\n            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);\n            inputManager.toggleSoftInput(0, 0);\n        }\n    }\n'''
if new not in text:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'toggleKeyboard anchor count={count}')
    text = text.replace(old, new, 1)

# Keep the startup comment accurate: the root transform is applied before in-root stream UI,
# while separate Android windows (spinner/dialogs) intentionally remain physical portrait.
text = text.replace(
    '''            // Apply the logical landscape transform before any stream-session UI (including the\n            // connection spinner) can become visible.\n''',
    '''            // Apply the logical landscape transform before in-root stream UI can become visible.\n            // Separate Android windows (for example the connection spinner) remain physical portrait.\n''')

p.write_bytes(text.replace('\n', newline).encode('utf-8'))
print('Fifth sideways audit patch applied successfully')
