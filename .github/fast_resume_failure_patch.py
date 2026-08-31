from pathlib import Path

path = Path('app/src/main/java/com/limelight/Game.java')
data = path.read_bytes()
newline = '\r\n' if b'\r\n' in data else '\n'
text = data.decode('utf-8').replace('\r\n', '\n')
old = '''    private void handleConnectionTerminatedFinal(final int errorCode) {
        if (controllerHandler != null) {
            controllerHandler.resumeAfterReconnect();
        }
        // Perform a connection test if the failure could be due to a blocked port'''
new = '''    private void handleConnectionTerminatedFinal(final int errorCode) {
        // A failed reconnect has no live transport to receive input. Keep Fast Resume input
        // suspended here; connectionStarted() is the success path that restores controllers.
        // Perform a connection test if the failure could be due to a blocked port'''
if new not in text:
    if old not in text:
        raise SystemExit('Fast Resume final failure-path anchor missing')
    if text.count(old) != 1:
        raise SystemExit(f'Fast Resume final failure-path anchor count={text.count(old)}')
    text = text.replace(old, new, 1)
path.write_bytes(text.replace('\n', newline).encode('utf-8'))
