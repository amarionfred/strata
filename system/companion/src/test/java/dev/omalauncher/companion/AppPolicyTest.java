package dev.omalauncher.companion;

import org.junit.Test;
import static org.junit.Assert.*;
import static dev.omalauncher.companion.AppPolicy.Kind.*;

public class AppPolicyTest {
    @Test public void unknownModernProfilesNeverBecomeWorkApps() {
        assertEquals(UNKNOWN, AppPolicy.kind(35, false, null));
        assertEquals(UNKNOWN, AppPolicy.kind(35, false, "vendor.unknown"));
        assertEquals(PRIVATE, AppPolicy.kind(35, false, "android.os.usertype.profile.PRIVATE"));
        assertEquals(CLONE, AppPolicy.kind(35, false, "android.os.usertype.profile.CLONE"));
        assertEquals(WORK, AppPolicy.kind(34, false, null));
        assertEquals(PERSONAL, AppPolicy.kind(35, true, null));
    }
    @Test public void privateRowsDisappearOnLockHideOrLossOfHomeRole() {
        assertTrue(AppPolicy.visible(PRIVATE, false, true, true, false));
        assertFalse(AppPolicy.visible(PRIVATE, true, true, true, false));
        assertFalse(AppPolicy.visible(PRIVATE, false, false, true, false));
        assertFalse(AppPolicy.visible(PRIVATE, false, true, false, false));
        assertFalse(AppPolicy.visible(PRIVATE, false, true, true, true));
        assertFalse(AppPolicy.visible(UNKNOWN, false, true, true, false));
        assertTrue(AppPolicy.visible(WORK, true, false, false, false));
    }
    @Test public void staleSerialCannotLaunchReusedProfile() {
        assertTrue(AppPolicy.sameProfile(0, 0)); assertTrue(AppPolicy.sameProfile(20, 20));
        assertFalse(AppPolicy.sameProfile(20, 21)); assertFalse(AppPolicy.sameProfile(-1, -1));
    }
    @Test public void searchHandlesAccentsCombiningMarksAndPackageNames() {
        assertTrue(AppPolicy.matches("Café", "app.coffee", "cafe"));
        assertTrue(AppPolicy.matches("Cafe\u0301", "app.coffee", "CAFÉ"));
        assertTrue(AppPolicy.matches("Settings", "com.android.settings", "android"));
        assertTrue(AppPolicy.matches("الضبط", "com.android.settings", "الضبط"));
        assertFalse(AppPolicy.matches("Café", "app.coffee", "calendar"));
    }
}
