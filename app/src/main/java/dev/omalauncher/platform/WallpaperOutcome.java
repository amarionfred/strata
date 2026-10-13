package dev.omalauncher.platform;

/** A result describes what Android accepted, rather than whether a task finished. */
public enum WallpaperOutcome {
    BOTH("Applied to system and lock wallpaper"),
    SYSTEM_ONLY("Applied to system wallpaper; Android rejected the lock wallpaper"),
    LIVE_ACTIVE("A live wallpaper is active; static wallpaper was not changed"),
    BLOCKED("Android does not allow wallpaper changes for this user"),
    INVALID_IMAGE("The selected background could not be decoded"),
    FAILED("Android did not apply the wallpaper");

    public final String message;
    WallpaperOutcome(String message) { this.message = message; }
    public interface Setter { int set(int flags) throws Exception; }
    public static WallpaperOutcome apply(Setter setter) {
        try { if (setter.set(3) > 0) return BOTH; } catch (Exception ignored) { }
        try { if (setter.set(1) > 0) return SYSTEM_ONLY; } catch (Exception ignored) { }
        return FAILED;
    }
}
