package dev.omalauncher.companion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class CommandResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!TermuxBridge.ACTION_RESULT.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null || !"oma-job".equals(data.getScheme()) || !"result".equals(data.getHost())) return;
        String id = data.getLastPathSegment();
        if (id == null) return;
        Bundle result = intent.getBundleExtra("result");
        if (result == null) {
            JobStore.finish(context, id, "", "", null, "Termux returned no result bundle.", false);
            return;
        }
        String stdout = result.getString("stdout", ""), stderr = result.getString("stderr", "");
        if (stdout == null) stdout = "";
        if (stderr == null) stderr = "";
        int errorCode = result.getInt("err", 0);
        Integer exit = result.containsKey("exitCode") ? result.getInt("exitCode") : null;
        String error = errorCode == ActivityResult.OK ? "" : "Termux error " + errorCode + ": " + result.getString("errmsg", "No details");
        if (exit == null && error.isEmpty()) error = "Termux returned no process exit code.";
        boolean truncated = originalLength(result, "stdout_original_length") > stdout.length()
                || originalLength(result, "stderr_original_length") > stderr.length()
                || stdout.length() > JobStore.OUTPUT_LIMIT || stderr.length() > JobStore.OUTPUT_LIMIT;
        if (JobStore.finish(context, id, stdout, stderr, exit, error, truncated)) OmarchyThemes.acceptResult(context, id);
    }
    private static long originalLength(Bundle result, String key) {
        try { return Long.parseLong(String.valueOf(result.get(key))); }
        catch (NumberFormatException e) { return 0; }
    }
    private static final class ActivityResult { static final int OK = -1; }
}
