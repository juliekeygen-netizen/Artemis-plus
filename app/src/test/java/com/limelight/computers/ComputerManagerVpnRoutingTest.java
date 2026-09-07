package com.limelight.computers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComputerManagerVpnRoutingTest {
    @Test
    public void vpnRouteIsPreservedInsteadOfPerformingExternalAddressDiscovery() {
        assertFalse(ComputerManagerService.shouldDiscoverExternalAddress(true));
    }

    @Test
    public void externalAddressDiscoveryStillRunsWithoutVpn() {
        assertTrue(ComputerManagerService.shouldDiscoverExternalAddress(false));
    }
}
