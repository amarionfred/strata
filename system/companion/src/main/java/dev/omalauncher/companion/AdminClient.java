package dev.omalauncher.companion;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import rikka.shizuku.Shizuku;

final class AdminClient implements AutoCloseable {
    volatile IAdminService service;
    private final Runnable changed;
    private final Shizuku.UserServiceArgs args;
    private boolean binding;
    private String failure;
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener dead = () -> { service = null; binding = false; notifyChanged(); };
    private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
        if (code == 730) connect();
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IAdminService.Stub.asInterface(binder);
            binding = false;
            failure = null;
            notifyChanged();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            notifyChanged();
        }
    };
    AdminClient(Context context, Runnable changed) {
        this.changed = changed;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, AdminService.class))
                .tag("oma-admin-preview").version(1).daemon(false).debuggable(false).processNameSuffix("oma_admin");
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(permission);
    }
    private void notifyChanged() { if (changed != null) changed.run(); }
    String status() {
        if (!Shizuku.pingBinder()) return "Android controls disconnected · start Shizuku";
        if (failure != null) return failure;
        try {
            if (Shizuku.getVersion() < 13) return "Shizuku 13 or newer is required";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "Android controls need Shizuku permission";
            return service == null ? "Connecting Android controls…" : "Android controls connected · backend UID " + Shizuku.getUid();
        } catch (RuntimeException e) { return e.toString(); }
    }
    void request() {
        if (!Shizuku.pingBinder()) throw new IllegalStateException("Open Shizuku and start it first. USB activation is available from a computer.");
        if (Shizuku.getVersion() < 13) throw new IllegalStateException("Update Shizuku to version 13 or newer.");
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) connect();
        else Shizuku.requestPermission(730);
    }
    void connect() {
        try {
            if (Shizuku.pingBinder() && Shizuku.getVersion() >= 13
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && !binding && service == null) {
                binding = true;
                failure = null;
                Shizuku.bindUserService(args, connection);
            }
        } catch (RuntimeException e) { failure = e.toString(); binding = false; }
        notifyChanged();
    }
    @Override public void close() {
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        try { if (Shizuku.pingBinder() && (binding || service != null)) Shizuku.unbindUserService(args, connection, true); }
        catch (RuntimeException ignored) { }
        service = null;
    }
}

