package dev.omalauncher.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.concurrent.*;

/** User-started root jobs survive activity recreation. No exported command endpoint. */
public final class RootJobsService extends Service {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int pending;

    static String start(Activity activity, String kind, String label, String script) {
        if (activity.getSystemService(KeyguardManager.class).isKeyguardLocked())
            throw new IllegalStateException("Unlock the phone before starting a root command.");
        RootCommands.command(script);
        String id = JobStore.create(activity, "root:" + kind, label);
        Intent intent = new Intent(activity, RootJobsService.class)
                .putExtra("id", id).putExtra("script", script).putExtra("label", label);
        try { activity.startForegroundService(intent); }
        catch (RuntimeException e) {
            JobStore.finish(activity, id, "", "", null, e.toString(), false);
            throw e;
        }
        return id;
    }

    static String status(Context context) {
        return context.getSharedPreferences("root-access", MODE_PRIVATE)
                .getString("status", "Root has not been checked");
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getStringExtra("id") == null || intent.getStringExtra("script") == null) {
            if (pending == 0) stopSelf(startId);
            return START_NOT_STICKY;
        }
        String id = intent.getStringExtra("id"), script = intent.getStringExtra("script");
        try {
            RootCommands.command(script);
            foreground(intent.getStringExtra("label"));
        } catch (RuntimeException e) {
            JobStore.finish(this, id, "", "", null, "Root command was not started: " + e, false);
            if (pending == 0) stopSelf(startId);
            return START_NOT_STICKY;
        }
        pending++;
        executor.execute(() -> {
            try { execute(id, script); }
            finally { handler.post(() -> { if (--pending == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); } }); }
        });
        return START_NOT_STICKY;
    }

    private void foreground(String label) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("root-jobs", "Root commands", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 30,
                new Intent(this, MainActivity.class).putExtra("tab", "System"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "root-jobs").setSmallIcon(R.drawable.ic_oma)
                .setContentTitle("Strata root command").setContentText(label)
                .setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(92, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(92, notification);
    }

    private void execute(String id, String script) {
        String output = "", error = "", state = "Root unavailable";
        Integer exit = null;
        boolean truncated = false;
        try {
            ProcessRunner.Result result = ProcessRunner.run(600000, "su", "-c", RootCommands.guarded(script));
            output = result.output;
            exit = result.exit;
            String marker = RootCommands.VERIFIED + "\n";
            if (output.startsWith(marker)) {
                state = "Last command verified UID 0";
                output = output.substring(marker.length());
            } else error = "Root was not granted. Strata needs an already rooted phone and permission from its root manager.";
            truncated = output.endsWith("[output truncated]");
        } catch (java.io.IOException e) {
            error = "Cannot start su. Root is unavailable or the root manager could not start a shell. " + e.getMessage();
        } catch (Exception e) {
            state = "Root command interrupted; check its result";
            error = e + "\nProcess state is unknown. Inspect running processes before repeating a modifying command.";
        }
        getSharedPreferences("root-access", MODE_PRIVATE).edit().putString("status", state)
                .putLong("checked", System.currentTimeMillis()).commit();
        JobStore.finish(this, id, output, "", exit, error, truncated);
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
