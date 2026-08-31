from pathlib import Path


def read(path):
    p = Path(path)
    data = p.read_bytes()
    newline = '\r\n' if b'\r\n' in data else '\n'
    return data.decode('utf-8').replace('\r\n', '\n'), newline


def write(path, text, newline='\n'):
    Path(path).write_bytes(text.replace('\n', newline).encode('utf-8'))


def replace_once(path, old, new):
    text, newline = read(path)
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:180]!r}')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'anchor not unique: {path}: count={count}')
    write(path, text.replace(old, new, 1), newline)


game = 'app/src/main/java/com/limelight/Game.java'
replace_once(
    game,
    '''    private boolean shouldUseFastResumeForBackgroundStop() {
        return prefConfig != null && BackgroundStreamingPolicy.shouldUseFastResume(
                prefConfig.backgroundStreamingMode,
                isFinishing(),
                isChangingConfigurations(),
                isCurrentlyInPip(),
                isOnExternalDisplay());
    }''',
    '''    private boolean shouldUseFastResumeForBackgroundStop() {
        // onPause() is the authoritative transition gate because Surface loss may happen before
        // onStop(). If PiP/multi-window revoked the arm, onStop() must not reinterpret the same
        // transition as Fast Resume after a terminal Surface path has already begun.
        return fastResumeLifecycleArmed && prefConfig != null &&
                BackgroundStreamingPolicy.shouldUseFastResume(
                        prefConfig.backgroundStreamingMode,
                        isFinishing(),
                        isChangingConfigurations(),
                        isCurrentlyInPip(),
                        isOnExternalDisplay());
    }''')
replace_once(
    game,
    '''        if (isInMultiWindowMode) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoBackground();''',
    '''        if (isInMultiWindowMode) {
            // A visible multi-window/PiP transition owns this lifecycle change. Revoke any
            // speculative Fast Resume arm created by an earlier onPause() ordering.
            fastResumeLifecycleArmed = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoBackground();''')
