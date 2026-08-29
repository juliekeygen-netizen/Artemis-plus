package com.limelight;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import java.lang.reflect.Field;

/** Manual stream-orientation helper used by Artemis Plus actions and the native game menu. */
public final class ArtemisOrientationHelper {
    private ArtemisOrientationHelper() {
    }

    public static boolean rotate(Game game) {
        if (game == null || game.isFinishing() || game.isOnExternalDisplay()) {
            return false;
        }

        int current = game.getResources().getConfiguration().orientation;
        if (current != Configuration.ORIENTATION_LANDSCAPE &&
                current != Configuration.ORIENTATION_PORTRAIT) {
            current = game.getResources().getDisplayMetrics().widthPixels >=
                    game.getResources().getDisplayMetrics().heightPixels
                    ? Configuration.ORIENTATION_LANDSCAPE
                    : Configuration.ORIENTATION_PORTRAIT;
        }

        int target = current == Configuration.ORIENTATION_LANDSCAPE
                ? Configuration.ORIENTATION_PORTRAIT
                : Configuration.ORIENTATION_LANDSCAPE;

        // Game.onConfigurationChanged() reapplies its preferred orientation based on this field.
        // Keep it synchronized so the configuration callback doesn't immediately undo our request.
        try {
            Field orientationField = Game.class.getDeclaredField("currentOrientation");
            orientationField.setAccessible(true);
            orientationField.setInt(game, target);
        } catch (ReflectiveOperationException e) {
            LimeLog.warning("Unable to synchronize manual orientation state: " + e.getMessage());
        }

        // Use an explicit orientation rather than USER_* here. USER_* may follow the user's
        // rotation preference/lock, which made the existing manual Rotate command appear inert
        // on some devices even though it was intended to be a direct command.
        game.setRequestedOrientation(target == Configuration.ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        return true;
    }
}
