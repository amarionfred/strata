package dev.omalauncher.companion;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class InboxService extends NotificationListenerService {
    private static volatile InboxService connected;
    private static final java.util.Set<Runnable> observers = new java.util.concurrent.CopyOnWriteArraySet<>();
    static void observe(Runnable listener) { observers.add(listener); }
    static void forget(Runnable listener) { observers.remove(listener); }
    private static void changed() { for (Runnable observer : observers) observer.run(); }
    @Override public void onListenerConnected() { connected = this; changed(); }
    @Override public void onListenerDisconnected() { if (connected == this) connected = null; changed(); }
    @Override public void onDestroy() { if (connected == this) connected = null; changed(); super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification item) { changed(); }
    @Override public void onNotificationRemoved(StatusBarNotification item) { changed(); }
    static boolean available() { return connected != null; }
    static StatusBarNotification[] notifications() {
        InboxService instance = connected;
        if (instance == null) return new StatusBarNotification[0];
        try {
            StatusBarNotification[] active = instance.getActiveNotifications();
            return active == null ? new StatusBarNotification[0] : java.util.Arrays.stream(active)
                    .filter(item -> AppCatalog.outsidePrivate(instance, item.getUser()))
                    .filter(item -> !instance.getPackageName().equals(item.getPackageName()))
                    .filter(item -> (item.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) == 0)
                    .sorted(java.util.Comparator.comparingLong(StatusBarNotification::getPostTime).reversed())
                    .toArray(StatusBarNotification[]::new);
        } catch (RuntimeException e) { return new StatusBarNotification[0]; }
    }
    static void dismiss(String key) {
        InboxService instance = connected;
        StatusBarNotification item = find(key);
        if (instance != null && item != null && item.isClearable()) instance.cancelNotification(key);
    }
    static StatusBarNotification find(String key) {
        for (StatusBarNotification item : notifications()) if (item.getKey().equals(key)) return item;
        return null;
    }
    static void open(android.content.Context context, String key) throws android.app.PendingIntent.CanceledException {
        StatusBarNotification item = find(key);
        if (item == null || item.getNotification().contentIntent == null)
            throw new IllegalStateException("This notification is no longer available.");
        android.os.Bundle options = null;
        if (android.os.Build.VERSION.SDK_INT >= 34) options = android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
        item.getNotification().contentIntent.send(context, 0, null, null, null, null, options);
        if ((item.getNotification().flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0) dismiss(key);
    }
}
