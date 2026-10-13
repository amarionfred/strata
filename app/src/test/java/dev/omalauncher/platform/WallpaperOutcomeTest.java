package dev.omalauncher.platform;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WallpaperOutcomeTest {
    @Test public void acceptedBothFlagsNeedsNoFallback() {
        List<Integer> calls = new ArrayList<>();
        assertEquals(WallpaperOutcome.BOTH, WallpaperOutcome.apply(flags -> { calls.add(flags); return 10; }));
        assertEquals(Arrays.asList(3), calls);
    }
    @Test public void systemFallbackNeverClaimsTheLockChanged() {
        List<Integer> calls = new ArrayList<>();
        assertEquals(WallpaperOutcome.SYSTEM_ONLY, WallpaperOutcome.apply(flags -> {
            calls.add(flags); if (flags == 3) throw new SecurityException("lock blocked"); return 11;
        }));
        assertEquals(Arrays.asList(3, 1), calls);
    }
    @Test public void rejectedWritesNeverReportSuccess() {
        assertEquals(WallpaperOutcome.FAILED, WallpaperOutcome.apply(flags -> { throw new SecurityException("blocked"); }));
        assertEquals(WallpaperOutcome.FAILED, WallpaperOutcome.apply(flags -> 0));
    }
}
