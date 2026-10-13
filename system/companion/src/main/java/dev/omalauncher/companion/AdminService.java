package dev.omalauncher.companion;

import android.content.Context;
import android.os.Binder;
import android.os.Process;
import org.json.JSONObject;

/** Started by Shizuku app_process, never by an exported Android service. */
public final class AdminService extends IAdminService.Stub {
    private final int ownerUid;
    private final String user;

    // Shizuku 13+ supplies this package context. UID is the APK's, not app_process's.
    public AdminService(Context context) {
        ownerUid = context.getApplicationInfo().uid;
        user = Integer.toString(ownerUid / 100000);
        if (ownerUid < 10000) throw new SecurityException("Invalid companion identity");
    }
    private void authorize() {
        if (Binder.getCallingUid() != ownerUid) throw new SecurityException("Only the companion may call this service");
    }
    private String result(String operation, String... arguments) {
        try {
            ProcessRunner.Result result = ProcessRunner.run(10000, arguments);
            return new JSONObject().put("operation", operation).put("uid", Process.myUid())
                    .put("exit", result.exit).put("output", result.output).toString();
        } catch (Exception e) {
            return error(operation, e.toString());
        }
    }
    private String error(String operation, String error) {
        try { return new JSONObject().put("operation", operation).put("uid", Process.myUid()).put("error", error).toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Override public synchronized String inspect() {
        authorize();
        try {
            JSONObject report = new JSONObject().put("uid", Process.myUid()).put("pid", Process.myPid()).put("user", user);
            report.put("identity", new JSONObject(result("identity", "/system/bin/id")));
            report.put("wifi", new JSONObject(result("wifi", "/system/bin/cmd", "wifi", "status")));
            report.put("rotation", new JSONObject(result("rotation", "/system/bin/settings", "--user", user,
                    "get", "system", "accelerometer_rotation")));
            return report.toString(2);
        } catch (Exception e) { return error("inspect", e.toString()); }
    }
    @Override public synchronized String setWifi(boolean enabled) {
        authorize();
        try {
            JSONObject write = new JSONObject(result("wifi", "/system/bin/cmd", "wifi", "set-wifi-enabled", enabled ? "enabled" : "disabled"));
            // A command exit is not proof that the radio has finished changing state.
            write.put("observed_after_request", new JSONObject(result("wifi status", "/system/bin/cmd", "wifi", "status")));
            return write.toString(2);
        } catch (Exception e) { return error("wifi", e.toString()); }
    }
    @Override public synchronized String setRotationLocked(boolean locked) {
        authorize();
        try {
            String value = locked ? "0" : "1";
            JSONObject write = new JSONObject(result("rotation", "/system/bin/settings", "--user", user,
                    "put", "system", "accelerometer_rotation", value));
            JSONObject read = new JSONObject(result("rotation readback", "/system/bin/settings", "--user", user,
                    "get", "system", "accelerometer_rotation"));
            write.put("readback", read);
            write.put("verified", write.optInt("exit", -1) == 0 && read.optInt("exit", -1) == 0
                    && value.equals(read.optString("output").trim()));
            return write.toString(2);
        } catch (Exception e) { return error("rotation", e.toString()); }
    }
    @Override public void destroy() {
        int caller = Binder.getCallingUid();
        if (caller != ownerUid && caller != 2000 && caller != 0) throw new SecurityException("Unauthorized teardown");
        System.exit(0);
    }
}

