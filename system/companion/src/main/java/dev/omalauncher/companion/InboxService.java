package dev.omalauncher.companion;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class InboxService extends NotificationListenerService {
    private static volatile InboxService connected;
    @Override public void onListenerConnected() { connected = this; }
    @Override public void onListenerDisconnected() { if (connected == this) connected = null; }
    @Override public void onDestroy() { if (connected == this) connected = null; super.onDestroy(); }
    static boolean available() { return connected != null; }
    static StatusBarNotification[] notifications() {
        InboxService instance = connected;
        if (instance == null) return new StatusBarNotification[0];
        try {
            StatusBarNotification[] active = instance.getActiveNotifications();
            return active == null ? new StatusBarNotification[0] : active;
        } catch (RuntimeException e) { return new StatusBarNotification[0]; }
    }
    static void dismiss(String key) {
        InboxService instance = connected;
        if (instance != null) instance.cancelNotification(key);
    }
}

