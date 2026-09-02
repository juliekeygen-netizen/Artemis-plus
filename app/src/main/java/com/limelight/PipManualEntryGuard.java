package com.limelight;

final class PipManualEntryGuard {
    interface EntryAction {
        void enter();
    }

    private PipManualEntryGuard() {
    }

    static boolean tryEnter(EntryAction action) {
        try {
            action.enter();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
