package dev.omalauncher.companion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Private, bounded job history survives UI recreation and late PendingIntent results. */
final class JobStore {
    static final int LIMIT = 16;
    static final int OUTPUT_LIMIT = 100000;
    private JobStore() {}
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("runtime-jobs", Context.MODE_PRIVATE);
    }
    static synchronized JSONArray all(Context context) {
        try { return new JSONArray(prefs(context).getString("jobs", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    static synchronized String create(Context context, String kind, String label) {
        try {
            JSONArray current = all(context), next = new JSONArray();
            int pending = 0;
            for (int i = 0; i < current.length(); i++)
                if ("waiting".equals(current.getJSONObject(i).optString("state"))) pending++;
            if (pending >= LIMIT) throw new IllegalStateException("16 jobs are still awaiting results. Check Termux before starting more.");
            String id = UUID.randomUUID().toString();
            next.put(new JSONObject().put("id", id).put("kind", kind).put("label", label)
                    .put("time", System.currentTimeMillis()).put("state", "waiting"));
            for (int i = 0; i < current.length(); i++) {
                JSONObject job = current.getJSONObject(i);
                if ("waiting".equals(job.optString("state")) || next.length() < LIMIT) next.put(job);
            }
            if (!prefs(context).edit().putString("jobs", next.toString()).commit())
                throw new IllegalStateException("Unable to save job before dispatch.");
            return id;
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    static synchronized boolean finish(Context context, String id, String stdout, String stderr,
                                       Integer exitCode, String error, boolean truncated) {
        JSONArray jobs = all(context);
        try {
            for (int i = 0; i < jobs.length(); i++) {
                JSONObject job = jobs.getJSONObject(i);
                if (!id.equals(job.optString("id")) || !"waiting".equals(job.optString("state"))) continue;
                job.put("state", error.isEmpty() ? "finished" : "error")
                        .put("stdout", tail(stdout)).put("stderr", tail(stderr))
                        .put("error", tail(error)).put("truncated", truncated);
                if (exitCode != null) job.put("exit", exitCode);
                return prefs(context).edit().putString("jobs", jobs.toString()).commit();
            }
        } catch (org.json.JSONException ignored) { /* Do not accept a malformed result. */ }
        return false;
    }
    static String tail(String text) {
        return text.length() <= OUTPUT_LIMIT ? text : text.substring(text.length() - OUTPUT_LIMIT);
    }
}

