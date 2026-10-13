package dev.omalauncher.companion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class OmarchyThemes {
    private OmarchyThemes() {}
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("omarchy", Context.MODE_PRIVATE); }
    static String text(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("The selected file cannot be opened");
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > 32000) throw new IOException("Configuration file exceeds 32 KB");
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    static String asset(Context context, String path) throws IOException { return text(context.getAssets().open(path)); }
    static OmarchyPalette current(Context context) {
        try {
            String saved = prefs(context).getString("colors", null);
            if (saved != null) return new OmarchyPalette(prefs(context).getString("name", "imported"), saved);
        } catch (IllegalArgumentException ignored) { /* Fall back if an older preference is invalid. */ }
        try { return new OmarchyPalette("tokyo-night", asset(context, "themes/tokyo-night.toml")); }
        catch (IOException e) { throw new IllegalStateException("Bundled Omarchy theme is missing", e); }
    }
    static List<OmarchyPalette> bundled(Context context) throws IOException {
        String[] names = context.getAssets().list("themes");
        List<OmarchyPalette> themes = new ArrayList<>();
        if (names != null) for (String name : names) if (name.endsWith(".toml"))
            themes.add(new OmarchyPalette(name.substring(0, name.length() - 5), asset(context, "themes/" + name)));
        themes.sort(Comparator.comparing(p -> p.name));
        return themes;
    }
    static void select(Context context, OmarchyPalette palette) {
        if (!prefs(context).edit().putString("name", palette.name).putString("colors", palette.source).commit())
            throw new IllegalStateException("Could not save the theme selection");
    }
    static String title(OmarchyPalette palette) {
        StringBuilder title = new StringBuilder();
        for (String part : palette.name.split("-")) {
            if (part.isEmpty()) continue;
            if (title.length() > 0) title.append(' ');
            title.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return title.toString();
    }
    static String nativeStatus(Context context) {
        String selected = current(context).key();
        JSONArray jobs = JobStore.all(context);
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && job.optString("kind").startsWith("omarchy-apply:") && "waiting".equals(job.optString("state")))
                return "Waiting for Termux to return the config-install result.";
        }
        return selected.equals(prefs(context).getString("native-key", ""))
                ? "Termux confirmed this theme and its config files. Open a new interactive session to load the shell defaults."
                : "Apply to terminal to synchronize colors and native app configs.";
    }
    static boolean pending(Context context) {
        JSONArray jobs = JobStore.all(context);
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job != null && job.optString("kind").startsWith("omarchy-apply:") && "waiting".equals(job.optString("state"))) return true;
        }
        return false;
    }
    static void acceptResult(Context context, String id) {
        JSONArray jobs = JobStore.all(context);
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null || !id.equals(job.optString("id"))) continue;
            String key = OmarchyConfigs.acknowledged(job.optString("kind"), job.optString("stdout"),
                    job.optInt("exit", -1), job.optString("error"), job.optBoolean("truncated"));
            if (key != null) prefs(context).edit().putString("native-key", key).apply();
            else if (job.optString("kind").startsWith("omarchy-apply:")) prefs(context).edit().remove("native-key").apply();
        }
    }
}
