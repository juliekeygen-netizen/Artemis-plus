from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    data = p.read_bytes()
    nl = b'\r\n' if b'\r\n' in data else b'\n'
    text = data.decode('utf-8').replace('\r\n', '\n')
    if new and new in text:
        return
    if old not in text:
        # Empty replacements are idempotent when the old text is already gone.
        if not new:
            return
        raise SystemExit(f'anchor missing: {path}: {old!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique: {path}: count={text.count(old)}')
    text = text.replace(old, new, 1)
    p.write_bytes(text.replace('\n', nl.decode()).encode('utf-8'))


# Background streaming intentionally retains Game while hidden. Android's noHistory contract
# would finish the Activity as soon as the user navigates away, defeating both Fast Resume and
# Keep Connection Alive. Disabled mode still finishes explicitly in Game.onStop(), and Game is
# excluded from Recents, so removing this flag doesn't change normal stream exit behavior.
replace_once(
    'app/src/main/AndroidManifest.xml',
    '''        <activity
            android:name=".Game"
            android:configChanges="mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|screenLayout|fontScale|uiMode|orientation|screenSize|smallestScreenSize|layoutDirection"
            android:noHistory="true"
            android:supportsPictureInPicture="true"''',
    '''        <activity
            android:name=".Game"
            android:configChanges="mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|screenLayout|fontScale|uiMode|orientation|screenSize|smallestScreenSize|layoutDirection"
            android:supportsPictureInPicture="true"''')

game = 'app/src/main/java/com/limelight/Game.java'

# PiP and visible multi-window own their lifecycle transitions and must revoke both speculative
# background-mode arms. Otherwise an onPause() that arrived first can later be misread as Keep Alive.
replace_once(game,
'''            // PiP is still an active stream, not a Fast Resume background stop.
            fastResumeLifecycleArmed = false;''',
'''            // PiP is still an active stream, not a background-streaming stop.
            fastResumeLifecycleArmed = false;
            keepAliveLifecycleArmed = false;''')
replace_once(game,
'''            // PiP owns this transition. Do not let an earlier pause callback reinterpret it as
            // Fast Resume if the platform changes lifecycle ordering on a particular device.
            fastResumeLifecycleArmed = false;''',
'''            // PiP owns this transition. Do not let an earlier pause callback reinterpret it as
            // background streaming if the platform changes lifecycle ordering on a device.
            fastResumeLifecycleArmed = false;
            keepAliveLifecycleArmed = false;''')
replace_once(game,
'''            // A visible multi-window/PiP transition owns this lifecycle change. Revoke any
            // speculative Fast Resume arm created by an earlier onPause() ordering.
            fastResumeLifecycleArmed = false;''',
'''            // A visible multi-window/PiP transition owns this lifecycle change. Revoke any
            // speculative background arm created by an earlier onPause() ordering.
            fastResumeLifecycleArmed = false;
            keepAliveLifecycleArmed = false;''')

# If a transient pause returns directly to onResume() without ever parking, clear both arms.
replace_once(game,
'''        } else {
            fastResumeLifecycleArmed = false;
        }
    }

    @Override
    protected void onPause() {''',
'''        } else {
            fastResumeLifecycleArmed = false;
            keepAliveLifecycleArmed = false;
        }
    }

    @Override
    protected void onPause() {''')

# A timeout is terminal. Quiesce decoder threads before the ImageReader Surface disappears, then
# stop the transport, and only then close the headless consumer.
replace_once(game,
'''    private void finishKeepAliveSession() {
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        closeKeepAliveSurface();
        stopConnection();
        stopKeepAliveService();
        finish();
    }''',
'''    private void finishKeepAliveSession() {
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        if (decoderRenderer != null && (connecting || connected)) {
            decoderRenderer.prepareForStop();
        }
        stopConnection();
        closeKeepAliveSurface();
        stopKeepAliveService();
        finish();
    }''')

# A foreground service raises process importance but does not make the Activity immortal. If Android
# explicitly destroys a retained Game instance, terminate its Activity-owned transport before
# destroying controller/renderer resources. Process death still needs no callback cleanup.
replace_once(game,
'''        timerHandler.removeCallbacksAndMessages(null);
        fastResumeLifecycleArmed = false;''',
'''        timerHandler.removeCallbacksAndMessages(null);
        if ((keepAliveServiceStarted || keepAliveSurface != null || keepAliveBackgrounded ||
                keepAliveLifecycleArmed || keepAliveReturnPending) && (connecting || connected)) {
            if (decoderRenderer != null) {
                decoderRenderer.prepareForStop();
            }
            stopConnection();
        }
        fastResumeLifecycleArmed = false;''')

print('Keep Alive lifecycle audit patch applied')
