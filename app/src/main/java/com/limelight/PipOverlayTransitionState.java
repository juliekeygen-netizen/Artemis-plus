package com.limelight;

/** Stores one PiP transition snapshot so repeated configuration callbacks cannot overwrite it. */
final class PipOverlayTransitionState {
    boolean inPip;
    boolean floatingButtonShown;
    boolean zoomButtonShown;
    boolean virtualControllerShown;
    boolean keyboardControllerShown;
    boolean keyboardLayoutShown;
    boolean performanceOverlayShown;
    boolean statsOverlayShown;
    int notificationVisibility;

    boolean enter(boolean floatingButtonShown,
                  boolean zoomButtonShown,
                  boolean virtualControllerShown,
                  boolean keyboardControllerShown,
                  boolean keyboardLayoutShown,
                  boolean performanceOverlayShown,
                  int notificationVisibility,
                  boolean statsOverlayShown) {
        if (inPip) {
            return false;
        }
        this.floatingButtonShown = floatingButtonShown;
        this.zoomButtonShown = zoomButtonShown;
        this.virtualControllerShown = virtualControllerShown;
        this.keyboardControllerShown = keyboardControllerShown;
        this.keyboardLayoutShown = keyboardLayoutShown;
        this.performanceOverlayShown = performanceOverlayShown;
        this.notificationVisibility = notificationVisibility;
        this.statsOverlayShown = statsOverlayShown;
        inPip = true;
        return true;
    }

    boolean exit() {
        if (!inPip) {
            return false;
        }
        inPip = false;
        return true;
    }
}
