package dev.omalauncher.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.widget.Toast;

/** The system delivers a one-use pin request; accepting always requires a user tap. */
public final class PinShortcutActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LauncherApps launcher = getSystemService(LauncherApps.class);
        LauncherApps.PinItemRequest request = launcher.getPinItemRequest(getIntent());
        if (request == null || request.getRequestType() != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || !request.isValid()) { finish(); return; }
        ShortcutInfo info = request.getShortcutInfo();
        if (info == null || !allowed(info)) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Add shortcut?")
                .setMessage(String.valueOf(info.getShortLabel()) + "\n" + info.getPackage())
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .setPositiveButton("Add", (dialog, which) -> {
                    boolean accepted = false;
                    try { accepted = allowed(info) && request.isValid() && request.accept(); }
                    catch (RuntimeException ignored) { }
                    Toast.makeText(this, accepted ? "Added to Applications" : "Shortcut is no longer available", Toast.LENGTH_LONG).show();
                    finish();
                }).show();
    }
    private boolean allowed(ShortcutInfo info) {
        return getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME)
                && AppCatalog.outsidePrivate(this, info.getUserHandle());
    }
}
