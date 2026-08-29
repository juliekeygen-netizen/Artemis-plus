package com.limelight;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manual stream-orientation helper used by Artemis Plus actions and the native game menu.
 *
 * Android/OEM window-management compatibility policies may delay or ignore an otherwise valid
 * setRequestedOrientation() call. Keep the command deterministic from Artemis's point of view,
 * verify the app window actually changed shape, and retry conservatively rather than issuing a
 * burst of orientation requests that Android may classify as an orientation loop.
 */
public final class ArtemisOrientationHelper {
    private static final long VERIFY_DELAY_MS = 1250L;
    private static final long MIN_REQUEST_INTERVAL_MS = 1050L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Game, RotationRequest> PENDING_REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Field CURRENT_ORIENTATION_FIELD = findCurrentOrientationField();

    private ArtemisOrientationHelper() {
    }

    public static boolean rotate(Game game) {
        if (game == null || game.isFinishing() || game.isDestroyed() || game.isOnExternalDisplay()) {
            return false;
        }

        // If the previous command is still pending, treat its target as the logical current state.
        // This makes a rapid second tap cancel/reverse the pending command instead of requesting the
        // same direction twice just because Android hasn't updated the window yet.
        RotationRequest previous = PENDING_REQUESTS.get(game);
        int logicalCurrent;
        if (previous != null && !isTargetApplied(game, previous.targetOrientation)) {
            logicalCurrent = previous.targetOrientation;
            removeFocusRetry(previous);
        } else {
            logicalCurrent = getDisplayedOrientation(game);
        }

        int target = oppositeOrientation(logicalCurrent);
        RotationRequest request = new RotationRequest(target);
        PENDING_REQUESTS.put(game, request);

        syncGameOrientationState(game, target);
        installFocusRetry(game, request);

        // First attempt: an explicit fixed orientation. Unlike USER_* this is a direct command and
        // does not depend on the user's system auto-rotate preference.
        issueRequest(game, request, fixedRequestFor(target), "fixed");

        // Do not immediately spam another orientation constant. Android has an OEM-configurable
        // compatibility treatment that can ignore apps which request multiple new orientations in
        // a short interval. Wait beyond that window, verify the *actual app window*, then use
        // sensorPortrait/sensorLandscape as an axis-constrained fallback. Those modes still use the
        // sensor even when the user has system rotation locked.
        MAIN_HANDLER.postDelayed(() -> verifyOrUseSensorFallback(game, request), VERIFY_DELAY_MS);
        return true;
    }

    private static void verifyOrUseSensorFallback(Game game, RotationRequest request) {
        if (!isCurrentRequest(game, request) || !isUsable(game)) {
            return;
        }
        if (isTargetApplied(game, request.targetOrientation)) {
            completeRequest(game, request, "fixed request applied");
            return;
        }

        issueRequest(game, request, sensorRequestFor(request.targetOrientation), "sensor-axis fallback");
        MAIN_HANDLER.postDelayed(() -> verifyOrUseFinalFixedRetry(game, request), VERIFY_DELAY_MS);
    }

    private static void verifyOrUseFinalFixedRetry(Game game, RotationRequest request) {
        if (!isCurrentRequest(game, request) || !isUsable(game)) {
            return;
        }
        if (isTargetApplied(game, request.targetOrientation)) {
            completeRequest(game, request, "sensor-axis fallback applied");
            return;
        }

        // One final fixed request, again outside Android's orientation-loop detection window.
        // If the OEM still defers it, leave the requested orientation in place. Android can then
        // honor the pending request on the next visibility/focus transition (the same transition
        // that can occur when leaving Recents and returning to the stream).
        issueRequest(game, request, fixedRequestFor(request.targetOrientation), "final fixed retry");
        MAIN_HANDLER.postDelayed(() -> finalVerification(game, request), VERIFY_DELAY_MS);
    }

    private static void finalVerification(Game game, RotationRequest request) {
        if (!isCurrentRequest(game, request) || !isUsable(game)) {
            return;
        }
        if (isTargetApplied(game, request.targetOrientation)) {
            completeRequest(game, request, "final fixed retry applied");
            return;
        }

        LimeLog.warning("Manual rotation request remains pending after retries. " +
                "target=" + orientationName(request.targetOrientation) +
                ", requestedOrientation=" + game.getRequestedOrientation() +
                ", displayed=" + orientationName(getDisplayedOrientation(game)) +
                ". Android/OEM window management may be deferring the request until the next " +
                "visibility transition.");
    }

    private static boolean issueRequest(Game game,
                                        RotationRequest request,
                                        int requestedOrientation,
                                        String reason) {
        if (!isCurrentRequest(game, request) || !isUsable(game)) {
            return false;
        }

        long now = SystemClock.uptimeMillis();
        if (request.lastRequestUptime != 0L &&
                now - request.lastRequestUptime < MIN_REQUEST_INTERVAL_MS) {
            return false;
        }

        request.lastRequestUptime = now;
        syncGameOrientationState(game, request.targetOrientation);

        LimeLog.info("Manual rotation: " + reason +
                ", target=" + orientationName(request.targetOrientation) +
                ", request=" + requestedOrientation +
                ", displayed=" + orientationName(getDisplayedOrientation(game)));

        try {
            game.setRequestedOrientation(requestedOrientation);
            nudgeWindowTraversal(game);
            return true;
        } catch (RuntimeException e) {
            LimeLog.warning("Manual rotation request failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Re-run a pending request when the stream window regains focus. This intentionally mirrors the
     * lifecycle transition that can make an OEM finally honor a previously deferred orientation
     * request after the user briefly visits Recents.
     */
    private static void installFocusRetry(Game game, RotationRequest request) {
        View decor = game.getWindow().getDecorView();
        if (decor == null) {
            return;
        }

        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (!observer.isAlive()) {
            return;
        }

        FocusRetryListener listener = new FocusRetryListener(game, observer, request);
        request.focusRetryListener = listener;
        observer.addOnWindowFocusChangeListener(listener);
    }

    private static final class FocusRetryListener implements ViewTreeObserver.OnWindowFocusChangeListener {
        private final WeakReference<Game> gameRef;
        private final WeakReference<ViewTreeObserver> observerRef;
        private final RotationRequest request;

        FocusRetryListener(Game game, ViewTreeObserver observer, RotationRequest request) {
            this.gameRef = new WeakReference<>(game);
            this.observerRef = new WeakReference<>(observer);
            this.request = request;
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus) {
                return;
            }

            Game game = gameRef.get();
            if (game == null || !isCurrentRequest(game, request) || !isUsable(game)) {
                removeSelf();
                return;
            }

            if (isTargetApplied(game, request.targetOrientation)) {
                completeRequest(game, request, "applied on focus return");
                return;
            }

            issueRequest(game, request, fixedRequestFor(request.targetOrientation), "focus-return retry");
        }

        void removeSelf() {
            ViewTreeObserver observer = observerRef.get();
            if (observer != null && observer.isAlive()) {
                observer.removeOnWindowFocusChangeListener(this);
            }
        }
    }

    private static void nudgeWindowTraversal(Game game) {
        View decor = game.getWindow().getDecorView();
        if (decor == null) {
            return;
        }

        decor.postOnAnimation(() -> {
            if (!isUsable(game)) {
                return;
            }
            decor.requestLayout();
            decor.requestApplyInsets();
            decor.invalidate();
        });
    }

    private static boolean isTargetApplied(Game game, int targetOrientation) {
        return getDisplayedOrientation(game) == targetOrientation;
    }

    /** Resolve the orientation of the app window, not just the physical display rotation. */
    static int getDisplayedOrientation(Game game) {
        int configOrientation = game.getResources().getConfiguration().orientation;
        View decor = game.getWindow().getDecorView();
        if (decor != null) {
            int fromBounds = orientationFromDimensions(
                    decor.getWidth(),
                    decor.getHeight(),
                    configOrientation);
            if (fromBounds == Configuration.ORIENTATION_LANDSCAPE ||
                    fromBounds == Configuration.ORIENTATION_PORTRAIT) {
                return fromBounds;
            }
        }

        if (configOrientation == Configuration.ORIENTATION_LANDSCAPE ||
                configOrientation == Configuration.ORIENTATION_PORTRAIT) {
            return configOrientation;
        }

        return orientationFromDimensions(
                game.getResources().getDisplayMetrics().widthPixels,
                game.getResources().getDisplayMetrics().heightPixels,
                Configuration.ORIENTATION_LANDSCAPE);
    }

    static int orientationFromDimensions(int width, int height, int fallback) {
        if (width > 0 && height > 0 && width != height) {
            return width > height
                    ? Configuration.ORIENTATION_LANDSCAPE
                    : Configuration.ORIENTATION_PORTRAIT;
        }
        if (fallback == Configuration.ORIENTATION_PORTRAIT ||
                fallback == Configuration.ORIENTATION_LANDSCAPE) {
            return fallback;
        }
        return Configuration.ORIENTATION_LANDSCAPE;
    }

    static int oppositeOrientation(int orientation) {
        return orientation == Configuration.ORIENTATION_PORTRAIT
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;
    }

    static int fixedRequestFor(int targetOrientation) {
        return targetOrientation == Configuration.ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    static int sensorRequestFor(int targetOrientation) {
        return targetOrientation == Configuration.ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private static void syncGameOrientationState(Game game, int targetOrientation) {
        if (CURRENT_ORIENTATION_FIELD == null) {
            return;
        }
        try {
            CURRENT_ORIENTATION_FIELD.setInt(game, targetOrientation);
        } catch (IllegalAccessException e) {
            LimeLog.warning("Unable to synchronize manual orientation state: " + e.getMessage());
        }
    }

    private static Field findCurrentOrientationField() {
        try {
            Field field = Game.class.getDeclaredField("currentOrientation");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LimeLog.warning("Unable to access Game orientation state: " + e.getMessage());
            return null;
        }
    }

    private static boolean isCurrentRequest(Game game, RotationRequest request) {
        return PENDING_REQUESTS.get(game) == request;
    }

    private static boolean isUsable(Game game) {
        return game != null && !game.isFinishing() && !game.isDestroyed() && !game.isOnExternalDisplay();
    }

    private static void completeRequest(Game game, RotationRequest request, String reason) {
        synchronized (PENDING_REQUESTS) {
            if (PENDING_REQUESTS.get(game) != request) {
                return;
            }
            PENDING_REQUESTS.remove(game);
        }
        removeFocusRetry(request);
        LimeLog.info("Manual rotation complete: " + reason +
                ", orientation=" + orientationName(request.targetOrientation));
    }

    private static void removeFocusRetry(RotationRequest request) {
        if (request == null || request.focusRetryListener == null) {
            return;
        }
        request.focusRetryListener.removeSelf();
        request.focusRetryListener = null;
    }

    private static String orientationName(int orientation) {
        return orientation == Configuration.ORIENTATION_PORTRAIT ? "portrait" : "landscape";
    }

    private static final class RotationRequest {
        final int targetOrientation;
        long lastRequestUptime;
        FocusRetryListener focusRetryListener;

        RotationRequest(int targetOrientation) {
            this.targetOrientation = targetOrientation;
        }
    }
}
