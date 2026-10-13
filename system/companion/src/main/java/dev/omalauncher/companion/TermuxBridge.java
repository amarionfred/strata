package dev.omalauncher.companion;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

final class TermuxBridge {
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String ACTION_RESULT = "dev.omalauncher.companion.COMMAND_RESULT";
    private TermuxBridge() {}

    static boolean installed(Context context) {
        try { context.getPackageManager().getPackageInfo("com.termux", 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    static boolean permitted(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }
    static String run(Activity context, String kind, String label, String script, boolean interactive) {
        return dispatch(context, kind, label, interactive ? Commands.interactive() : script, interactive);
    }
    static String rootSession(Activity context, boolean linux) {
        if (context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())
            throw new IllegalStateException("Unlock the phone before starting a root session.");
        return dispatch(context, "interactive", linux ? "Linux root terminal" : "Android root terminal", RootCommands.interactive(linux), true);
    }
    private static String dispatch(Activity context, String kind, String label, String script, boolean interactive) {
        return dispatch(context, kind, label, script, interactive, null);
    }
    static String workspace(Activity context, String script, String input) {
        return dispatch(context, "workspace", "Native workspace runtime", script, false, input);
    }
    private static String dispatch(Activity context, String kind, String label, String script, boolean interactive, String input) {
        if (!installed(context)) throw new IllegalStateException("Install and open Termux first, then connect it in Setup.");
        if (!permitted(context)) throw new IllegalStateException("Grant the Termux command permission in Setup.");
        String id = JobStore.create(context, kind, label);
        Intent reply = new Intent(context, CommandResultReceiver.class).setAction(ACTION_RESULT)
                .setData(Uri.parse("oma-job://result/" + id));
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        // Explicit component + fixed data/action; only Termux receives this one-use capability.
        PendingIntent callback = PendingIntent.getBroadcast(context, 0, reply, flags);
        Intent command = new Intent("com.termux.RUN_COMMAND")
                .setComponent(new ComponentName("com.termux", "com.termux.app.RunCommandService"))
                .putExtra("com.termux.RUN_COMMAND_PATH", Commands.PREFIX + "/bin/bash")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"--noprofile", "--norc", "-c", script})
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", Commands.HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", !interactive)
                .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback);
        if (input != null) command.putExtra("com.termux.RUN_COMMAND_STDIN", input);
        try {
            if (context.startService(command) == null) throw new IllegalStateException("Termux command service is unavailable.");
        } catch (RuntimeException e) {
            callback.cancel();
            JobStore.finish(context, id, "", "", null, e.toString(), false);
            throw e;
        }
        if (interactive) {
            // Opening from this visible activity avoids relying on Termux's background launch permission.
            Intent terminal = context.getPackageManager().getLaunchIntentForPackage("com.termux");
            if (terminal != null) context.startActivity(terminal);
        }
        return id;
    }
}
