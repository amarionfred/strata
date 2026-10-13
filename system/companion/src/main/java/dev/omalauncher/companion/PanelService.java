package dev.omalauncher.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Explicitly enabled, dismissible navigation overlay; hidden when the phone locks. */
public final class PanelService extends Service {
    private WindowManager windows;
    private LinearLayout panel;
    private boolean expanded;
    private OmarchyPalette palette;
    private final SharedPreferences.OnSharedPreferenceChangeListener themeChanged = (prefs, key) -> {
        if (panel != null) { palette = OmarchyThemes.current(this); redraw(); }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (panel != null) panel.setVisibility(Intent.ACTION_USER_PRESENT.equals(intent.getAction()) ? View.VISIBLE : View.GONE);
        }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        palette = OmarchyThemes.current(this);
        OmarchyThemes.prefs(this).registerOnSharedPreferenceChangeListener(themeChanged);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("panel", "Floating controls", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, PanelService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "panel").setSmallIcon(R.drawable.ic_oma)
                .setContentTitle("Strata controls are available").setContentText("Tap the floating button to open your workspace.")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "Stop controls", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(91, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(91, notification);
        if (panel == null) {
            windows = getSystemService(WindowManager.class);
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(8, 8, 8, 8);
            panel.setBackgroundColor(palette.color("background"));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            try { windows.addView(panel, params); redraw(); }
            catch (RuntimeException e) { panel = null; stopSelf(); }
        }
        if (panel != null && getSystemService(KeyguardManager.class).isKeyguardLocked()) panel.setVisibility(View.GONE);
        return START_NOT_STICKY;
    }
    private void redraw() {
        panel.removeAllViews();
        panel.setBackgroundColor(palette.color("background"));
        add(expanded ? "Close" : "›_", () -> { expanded = !expanded; redraw(); });
        if (!expanded) return;
        for (String tab : new String[]{"Home", "System", "Terminal", "Files", "Themes", "Controls", "Inbox"}) {
            add(tab, () -> {
                if (!getSystemService(KeyguardManager.class).isKeyguardLocked())
                    startActivity(new Intent(this, MainActivity.class).putExtra("tab", tab)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                expanded = false;
                redraw();
            });
        }
        add("Stop panel", this::stopSelf);
    }
    private void add(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(palette.color("accent"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.color("lighter_background")));
        button.setAllCaps(false);
        button.setContentDescription(label.equals("›_") ? "Open Strata controls" : label);
        button.setOnClickListener(v -> action.run());
        panel.addView(button);
    }
    @Override public void onDestroy() {
        OmarchyThemes.prefs(this).unregisterOnSharedPreferenceChangeListener(themeChanged);
        if (windows != null && panel != null) windows.removeView(panel);
        unregisterReceiver(screen);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
