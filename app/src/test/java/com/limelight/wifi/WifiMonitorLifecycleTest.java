package com.limelight.wifi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class WifiMonitorLifecycleTest {

    @Test
    public void unavailableWifiServiceDoesNotWedgeFutureStart() {
        Context context = mock(Context.class);
        WifiManager wifiManager = mock(WifiManager.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSystemService(Context.WIFI_SERVICE))
                .thenReturn(null, wifiManager);

        WifiMonitor monitor = new WifiMonitor();
        WifiMonitor.WifiCallback callback = (quality, rssi, linkSpeed) -> { };

        monitor.start(context, callback);
        assertFalse(monitor.isRunning());

        monitor.start(context, callback);
        assertTrue(monitor.isRunning());
        verify(context, times(2)).getSystemService(Context.WIFI_SERVICE);

        monitor.stop();
        assertFalse(monitor.isRunning());
    }

    @Test
    public void stopReleasesLifecycleAndAllowsRestart() {
        Context context = mock(Context.class);
        WifiManager wifiManager = mock(WifiManager.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager);

        WifiMonitor monitor = new WifiMonitor();
        WifiMonitor.WifiCallback callback = (quality, rssi, linkSpeed) -> { };

        monitor.start(context, callback);
        assertTrue(monitor.isRunning());
        monitor.stop();
        assertFalse(monitor.isRunning());

        monitor.start(context, callback);
        assertTrue(monitor.isRunning());
        verify(context, times(2)).getSystemService(Context.WIFI_SERVICE);
        monitor.stop();
    }
}
