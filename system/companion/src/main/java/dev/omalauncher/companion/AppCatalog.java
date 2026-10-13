package dev.omalauncher.companion;

import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;

/** Activity-scoped catalog. Labels are loaded off the UI thread and never persisted. */
final class AppCatalog implements AutoCloseable {
    static final class Entry {
        final String label, packageName, key, shortcut;
        final ComponentName component;
        final long serial;
        final AppPolicy.Kind kind;
        Entry(String label, ComponentName component, String packageName, String shortcut, long serial, AppPolicy.Kind kind) {
            this.label = label; this.component = component; this.packageName = packageName;
            this.shortcut = shortcut; this.serial = serial; this.kind = kind;
            key = serial + ":" + packageName + ":" + (shortcut == null ? component.flattenToString() : "shortcut:" + shortcut);
        }
    }
    static final class Profile {
        final long serial;
        final AppPolicy.Kind kind;
        final boolean quiet;
        Profile(long serial, AppPolicy.Kind kind, boolean quiet) { this.serial = serial; this.kind = kind; this.quiet = quiet; }
    }
    private final Context context;
    private final LauncherApps launcher;
    private final UserManager users;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable changed;
    private final Set<Long> locking = new HashSet<>();
    private Future<?> loading;
    private int generation;
    private boolean closed;
    private List<Entry> entries = Collections.emptyList();
    private List<Profile> profiles = Collections.emptyList();
    String error = "";
    private final LauncherApps.Callback packages = new LauncherApps.Callback() {
        public void onPackageRemoved(String name, UserHandle user) { refresh(); }
        public void onPackageAdded(String name, UserHandle user) { refresh(); }
        public void onPackageChanged(String name, UserHandle user) { refresh(); }
        public void onPackagesAvailable(String[] names, UserHandle user, boolean replacing) { refresh(); }
        public void onPackagesUnavailable(String[] names, UserHandle user, boolean replacing) { refresh(); }
        public void onShortcutsChanged(String name, List<ShortcutInfo> shortcuts, UserHandle user) { refresh(); }
    };
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        public void onReceive(Context ignored, Intent intent) { refresh(); }
    };
    AppCatalog(Context context, Runnable changed) {
        this.context = context; this.changed = changed;
        launcher = context.getSystemService(LauncherApps.class); users = context.getSystemService(UserManager.class);
        launcher.registerCallback(packages, main);
        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        for (String action : new String[]{Intent.ACTION_MANAGED_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                "android.intent.action.PROFILE_ADDED", "android.intent.action.PROFILE_REMOVED",
                "android.intent.action.PROFILE_AVAILABLE", "android.intent.action.PROFILE_UNAVAILABLE",
                Intent.ACTION_USER_UNLOCKED}) filter.addAction(action);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(changes, filter);
    }
    boolean homeRole() { return context.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME); }
    boolean privateShown() { return context.getSharedPreferences("workspace", 0).getBoolean("private-shown", true); }
    void privateShown(boolean show) { context.getSharedPreferences("workspace", 0).edit().putBoolean("private-shown", show).apply(); refresh(); }
    List<Entry> entries() { return entries; }
    List<Profile> profiles() { return profiles; }
    private AppPolicy.Kind kind(UserHandle user) { return kind(context, user); }
    static AppPolicy.Kind kind(Context context, UserHandle user) {
        if (android.os.Process.myUserHandle().equals(user)) return AppPolicy.Kind.PERSONAL;
        String type = null;
        if (Build.VERSION.SDK_INT >= 35) try {
            LauncherUserInfo info = context.getSystemService(LauncherApps.class).getLauncherUserInfo(user);
            if (info != null) type = info.getUserType();
        } catch (RuntimeException ignored) { }
        return AppPolicy.kind(Build.VERSION.SDK_INT, false, type);
    }
    static boolean outsidePrivate(Context context, UserHandle user) {
        AppPolicy.Kind kind = kind(context, user);
        return kind != AppPolicy.Kind.PRIVATE && kind != AppPolicy.Kind.UNKNOWN;
    }
    private boolean quiet(UserHandle user) {
        if (android.os.Process.myUserHandle().equals(user)) return false;
        try { return users.isQuietModeEnabled(user); } catch (RuntimeException e) { return true; }
    }
    private UserHandle user(long serial) {
        try {
            UserHandle user = users.getUserForSerialNumber(serial);
            return user != null && AppPolicy.sameProfile(serial, users.getSerialNumberForUser(user)) ? user : null;
        } catch (RuntimeException e) { return null; }
    }
    void hidePrivateNow() {
        generation++;
        if (loading != null) loading.cancel(true);
        entries = entries.stream().filter(e -> e.kind != AppPolicy.Kind.PRIVATE).collect(java.util.stream.Collectors.toList());
        changed.run();
    }
    void refresh() {
        if (closed) return;
        // Clear cached rows before any async work, including locale, role and lock transitions.
        int scan = ++generation;
        if (loading != null) loading.cancel(true);
        entries = Collections.emptyList(); error = ""; changed.run();
        boolean home = homeRole(), shown = privateShown();
        Set<Long> pending = new HashSet<>(locking);
        loading = worker.submit(() -> {
            List<Entry> next = new ArrayList<>(); List<Profile> nextProfiles = new ArrayList<>();
            String failure = "";
            try {
                Set<UserHandle> handles = new LinkedHashSet<>();
                handles.add(android.os.Process.myUserHandle()); handles.addAll(launcher.getProfiles());
                for (UserHandle handle : handles) {
                    if (Thread.currentThread().isInterrupted()) return;
                    long serial = users.getSerialNumberForUser(handle);
                    AppPolicy.Kind type = kind(handle);
                    if (serial < 0 || type == AppPolicy.Kind.UNKNOWN) continue;
                    boolean paused = quiet(handle);
                    if (type == AppPolicy.Kind.PRIVATE && !home) continue;
                    nextProfiles.add(new Profile(serial, type, paused || pending.contains(serial)));
                    if (!AppPolicy.visible(type, paused, home, shown, pending.contains(serial))) continue;
                    try {
                        for (LauncherActivityInfo info : launcher.getActivityList(null, handle)) {
                            if (info.getComponentName().getPackageName().equals(context.getPackageName())) continue;
                            next.add(new Entry(info.getLabel().toString(), info.getComponentName(), info.getComponentName().getPackageName(), null, serial, type));
                        }
                        // Private shortcuts stay within the private profile; they never enter home/search.
                        if (type != AppPolicy.Kind.PRIVATE && launcher.hasShortcutHostPermission()) {
                            List<ShortcutInfo> pinned = launcher.getShortcuts(new LauncherApps.ShortcutQuery()
                                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), handle);
                            if (pinned != null) for (ShortcutInfo info : pinned) if (info.isEnabled())
                                next.add(new Entry(String.valueOf(info.getShortLabel()), info.getActivity(), info.getPackage(), info.getId(), serial, type));
                        }
                    } catch (RuntimeException unavailable) { /* A profile can lock or disappear during a scan. */ }
                }
                Collator collator = Collator.getInstance(Locale.getDefault());
                next.sort((a, b) -> collator.compare(a.label, b.label));
            } catch (RuntimeException e) { failure = "Cannot load applications: " + e.getMessage(); }
            String resultError = failure;
            main.post(() -> {
                if (closed || scan != generation) return;
                // Recheck privacy after binder calls; a quiet profile may have changed during label loading.
                next.removeIf(e -> !canUse(e));
                List<Profile> currentProfiles = new ArrayList<>();
                for (Profile profile : nextProfiles) {
                    UserHandle handle = user(profile.serial);
                    if (handle == null || kind(handle) != profile.kind) continue;
                    if (profile.kind == AppPolicy.Kind.PRIVATE && !homeRole()) continue;
                    boolean paused = quiet(handle);
                    if (paused) locking.remove(profile.serial);
                    currentProfiles.add(new Profile(profile.serial, profile.kind, paused || locking.contains(profile.serial)));
                }
                entries = next; profiles = currentProfiles; error = resultError; changed.run();
            });
        });
    }
    private boolean canUse(Entry entry) {
        UserHandle handle = user(entry.serial);
        if (handle == null || kind(handle) != entry.kind) return false;
        return AppPolicy.visible(entry.kind, quiet(handle), homeRole(), privateShown(), locking.contains(entry.serial));
    }
    void open(Entry entry) {
        if (!canUse(entry) || entries.stream().noneMatch(e -> e.key.equals(entry.key)))
            throw new IllegalStateException("This app or profile is no longer available.");
        UserHandle handle = user(entry.serial);
        if (handle == null) throw new IllegalStateException("This profile was removed.");
        if (quiet(handle)) { users.requestQuietModeEnabled(false, handle); refresh(); return; }
        if (entry.shortcut != null) launcher.startShortcut(entry.packageName, entry.shortcut, null, null, handle);
        else launcher.startMainActivity(entry.component, handle, null, null);
    }
    void quiet(Profile profile, boolean pause) {
        UserHandle handle = user(profile.serial);
        if (handle == null || kind(handle) != profile.kind) throw new IllegalStateException("This profile was removed.");
        if (profile.kind == AppPolicy.Kind.PRIVATE && pause) { locking.add(profile.serial); hidePrivateNow(); }
        if (profile.kind == AppPolicy.Kind.PRIVATE && !pause && locking.contains(profile.serial) && !quiet(handle)) return;
        try {
            if (!users.requestQuietModeEnabled(pause, handle) && pause) locking.remove(profile.serial);
        } catch (RuntimeException e) { locking.remove(profile.serial); throw e; }
        finally { refresh(); }
    }
    public void close() {
        closed = true; generation++; launcher.unregisterCallback(packages); context.unregisterReceiver(changes);
        worker.shutdownNow(); main.removeCallbacksAndMessages(null); entries = Collections.emptyList(); profiles = Collections.emptyList();
    }
}
