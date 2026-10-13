package dev.omalauncher.companion;

import dev.omalauncher.theme.ThemeColors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Validated Omarchy colors.toml using the same resolution rules as the launcher. */
final class OmarchyPalette {
    final String name, source, digest;
    final Map<String, String> colors;

    OmarchyPalette(String name, String source) {
        if (name == null || !name.matches("[a-z0-9][a-z0-9._+-]{0,63}")) throw new IllegalArgumentException("Invalid theme name");
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > 32000)
            throw new IllegalArgumentException("Choose a colors.toml file smaller than 32 KB.");
        Map<String, String> raw = new LinkedHashMap<>(ThemeColors.INSTANCE.parse(source));
        // Several upstream palettes include desktop border gradients, not colors.
        // Preserve them in the imported data but never interpret them on Android.
        raw.keySet().removeIf(key -> key.startsWith("hyprland_"));
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getKey().equals("mode")) {
                if (!Arrays.asList("dark", "light").contains(entry.getValue())) throw new IllegalArgumentException("Invalid theme mode");
            } else if (!entry.getValue().matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Theme value " + entry.getKey() + " must be a #rrggbb color.");
            }
        }
        if ((!raw.containsKey("background") && !raw.containsKey("color0"))
                || (!raw.containsKey("foreground") && !raw.containsKey("color7")))
            throw new IllegalArgumentException("This file needs Omarchy background and foreground colors.");
        Map<String, String> resolved = new LinkedHashMap<>(ThemeColors.INSTANCE.resolve(raw));
        for (String key : new String[]{"background", "foreground", "blue", "color0", "color1", "color2", "color3", "color4", "color5", "color6", "color7",
                "color8", "color9", "color10", "color11", "color12", "color13", "color14", "color15"})
            if (!resolved.containsKey(key)) throw new IllegalArgumentException("Missing theme color: " + key);
        resolved.putIfAbsent("accent", resolved.get("blue"));
        this.name = name;
        this.source = source;
        this.colors = Collections.unmodifiableMap(resolved);
        this.digest = sha256(source);
    }
    String key() { return name + ":" + digest; }
    int color(String key) { return 0xff000000 | Integer.parseInt(colors.get(key).substring(1), 16); }
    boolean light() { return "light".equals(colors.get("mode")); }
    static String sha256(String contents) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(contents.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
