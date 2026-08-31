package com.limelight;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GameActivityManifestTest {
    @Test
    public void gameActivityIsRetainedWhenUserNavigatesAway() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ActivityInfo info = context.getPackageManager().getActivityInfo(
                new ComponentName(context, Game.class), 0);

        assertEquals("Game must not use noHistory because background streaming retains it",
                0, info.flags & ActivityInfo.FLAG_NO_HISTORY);
    }
}
