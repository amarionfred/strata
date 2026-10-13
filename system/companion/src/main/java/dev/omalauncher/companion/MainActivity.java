package dev.omalauncher.companion;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** A small native UI for validating runtime, management, and phone-wide access together. */
public final class MainActivity extends Activity {
    private int BG, SURFACE, TEXT, MUTED, ACCENT;
    private OmarchyPalette palette;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SharedPreferences.OnSharedPreferenceChangeListener jobsChanged = (prefs, key) -> handler.post(this::updateJobs);
    private LinearLayout root, content, history;
    private TextView status, adminStatus, adminOutput, themeStatus;
    private EditText configEditor;
    private AdminClient admin;
    private String tab = "Home", lastAdminOutput = "Read the device state to check the connected service.";
    private boolean adminBusy;
    private SharedPreferences ui;
    private AppCatalog catalog;
    private LinearLayout appRows, appTabs;
    private EditText appQuery;
    private AppPolicy.Kind appTab = AppPolicy.Kind.PERSONAL;
    private boolean wallpaperBusy;
    private final Runnable renderInbox = () -> { if (!isDestroyed() && "Inbox".equals(tab)) render(); };
    private final Runnable inboxChanged = () -> {
        handler.removeCallbacks(renderInbox); handler.postDelayed(renderInbox, 200);
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = getSharedPreferences("workspace", MODE_PRIVATE);
        if (saved != null) tab = saved.getString("tab", "Home");
        else if (getIntent().hasExtra("tab")) tab = getIntent().getStringExtra("tab");
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        else root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        admin = new AdminClient(this, () -> handler.post(this::updateStatus));
        JobStore.prefs(this).registerOnSharedPreferenceChangeListener(jobsChanged);
        catalog = new AppCatalog(this, this::populateApps);
        render();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("tab")) { tab = intent.getStringExtra("tab"); render(); }
    }
    @Override protected void onResume() {
        super.onResume();
        InboxService.observe(inboxChanged);
        if (admin != null) admin.connect();
        if (catalog != null) catalog.refresh();
        updateStatus();
        updateJobs();
        if ("Inbox".equals(tab)) render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("tab", tab); super.onSaveInstanceState(out); }
    @Override protected void onPause() {
        InboxService.forget(inboxChanged);
        handler.removeCallbacks(renderInbox);
        if (appTab == AppPolicy.Kind.PRIVATE && appQuery != null) appQuery.setText("");
        if (catalog != null) catalog.hidePrivateNow();
        super.onPause();
    }
    @Override protected void onDestroy() {
        InboxService.forget(inboxChanged);
        JobStore.prefs(this).unregisterOnSharedPreferenceChangeListener(jobsChanged);
        if (admin != null) admin.close();
        if (catalog != null) catalog.close();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    private void render() {
        if (tab == null) tab = "Home";
        palette = OmarchyThemes.current(this);
        BG = palette.color("background"); SURFACE = palette.color("lighter_background");
        TEXT = palette.color("foreground"); MUTED = palette.color("light_foreground"); ACCENT = palette.color("accent");
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(palette.light()
                    ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (palette.light()) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        root.removeAllViews();
        history = null; configEditor = null; adminStatus = null; adminOutput = null; themeStatus = null;
        appRows = null; appTabs = null; appQuery = null;
        LinearLayout heading = column();
        heading.setPadding(dp(20), dp(14), dp(20), dp(8));
        text(heading, "strata", 14, ACCENT, true);
        status = text(heading, "", 12, MUTED, false);
        root.addView(heading);
        ScrollView scroll = new ScrollView(this);
        content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        text(content, tab, 30, TEXT, false);
        switch (tab) {
            case "Terminal": terminal(); break;
            case "Software": software(); break;
            case "Files": files(); break;
            case "Themes": themes(); break;
            case "Controls": controls(); break;
            case "Inbox": inbox(); break;
            case "Setup": setup(); break;
            default: tab = "Home"; home();
        }
        HorizontalScrollView nav = new HorizontalScrollView(this);
        nav.setHorizontalScrollBarEnabled(false);
        LinearLayout items = new LinearLayout(this);
        items.setPadding(dp(8), 0, dp(8), dp(4));
        for (String name : new String[]{"Home", "Terminal", "Software", "Files", "Themes", "Controls", "Inbox", "Setup"}) {
            Button button = button(items, name, () -> { tab = name; render(); });
            button.setTextColor(name.equals(tab) ? ACCENT : MUTED);
            button.setMinWidth(0);
        }
        nav.addView(items);
        root.addView(nav);
        updateStatus();
        updateJobs();
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean mono) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5));
        if (mono) view.setTypeface(Typeface.MONOSPACE);
        parent.addView(view);
        return view;
    }
    private LinearLayout card(String title, String subtitle) {
        LinearLayout box = column();
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE); background.setCornerRadius(dp(16));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        content.addView(box, params);
        text(box, title, 20, TEXT, false);
        if (!subtitle.isEmpty()) text(box, subtitle, 14, MUTED, false);
        return box;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setTextColor(ACCENT);
        button.setBackgroundTintList(ColorStateList.valueOf(SURFACE));
        button.setOnClickListener(view -> safely(action));
        parent.addView(button);
        return button;
    }
    private EditText edit(LinearLayout parent, String hint, String value, boolean multiline) {
        EditText input = new EditText(this);
        input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        input.setHint(hint); input.setText(value); input.setTextSize(14);
        input.setTypeface(Typeface.MONOSPACE); input.setSingleLine(!multiline);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16000)});
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
    private void remember(EditText edit, String key) {
        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { ui.edit().putString(key, s.toString()).apply(); }
            public void afterTextChanged(Editable s) { }
        });
    }
    private void safely(Runnable action) {
        try { action.run(); }
        catch (Exception e) { new AlertDialog.Builder(this).setTitle("Action unavailable").setMessage(e.getMessage()).setPositiveButton("OK", null).show(); }
    }
    private void updateStatus() {
        if (status == null || isDestroyed()) return;
        status.setText((TermuxBridge.installed(this) ? (TermuxBridge.permitted(this) ? "Native runtime permission granted" : "Native runtime needs permission") : "Connect native runtime in Setup")
                + "  ·  " + (admin != null && admin.service != null ? "Android controls connected" : "Android controls offline"));
        if (adminStatus != null && admin != null) adminStatus.setText(admin.status());
        if (themeStatus != null) themeStatus.setText(OmarchyThemes.nativeStatus(this));
    }
    private void home() {
        LinearLayout workspace = card("Your workspace", "Native tools, Android apps, and phone controls in one place.");
        button(workspace, "Open terminal", () -> { tab = "Terminal"; render(); });
        button(workspace, "Omarchy themes · " + OmarchyThemes.title(palette), () -> { tab = "Themes"; render(); });
        button(workspace, "Connect runtime and controls", () -> { tab = "Setup"; render(); });
        LinearLayout apps = card("Applications", "Search names or packages. Work and private apps keep their own profiles.");
        appTabs = new LinearLayout(this); apps.addView(appTabs);
        appQuery = edit(apps, "Find an app", "", false);
        appQuery.setImeOptions(appQuery.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        // Browsing never forces the keyboard open; tapping the search field does.
        apps.setFocusableInTouchMode(true); apps.requestFocus();
        appRows = column(); apps.addView(appRows);
        appQuery.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { populateApps(); }
            public void afterTextChanged(Editable s) { }
        });
        populateApps();
    }
    private void populateApps() {
        if (catalog == null || appRows == null || appTabs == null || appQuery == null || isDestroyed()) return;
        boolean work = catalog.profiles().stream().anyMatch(p -> p.kind == AppPolicy.Kind.WORK);
        boolean privateSpace = catalog.homeRole() && catalog.privateShown()
                && catalog.profiles().stream().anyMatch(p -> p.kind == AppPolicy.Kind.PRIVATE);
        if ((appTab == AppPolicy.Kind.WORK && !work) || (appTab == AppPolicy.Kind.PRIVATE && !privateSpace)) {
            appTab = AppPolicy.Kind.PERSONAL;
            appQuery.setText("");
        }
        // Private labels and query text are never left in Recents screenshots.
        if (appTab == AppPolicy.Kind.PRIVATE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        appTabs.removeAllViews(); appRows.removeAllViews();
        List<AppPolicy.Kind> tabs = new ArrayList<>(); tabs.add(AppPolicy.Kind.PERSONAL);
        if (work) tabs.add(AppPolicy.Kind.WORK); if (privateSpace) tabs.add(AppPolicy.Kind.PRIVATE);
        for (AppPolicy.Kind kind : tabs) button(appTabs, (kind == appTab ? "✓ " : "") + kind.name().toLowerCase(Locale.ROOT), () -> {
            appTab = kind; appQuery.setText(""); populateApps();
        });
        for (AppCatalog.Profile profile : catalog.profiles()) if (profile.kind == appTab && (appTab == AppPolicy.Kind.WORK || appTab == AppPolicy.Kind.PRIVATE)) {
            String action = appTab == AppPolicy.Kind.PRIVATE ? (profile.quiet ? "Unlock private space" : "Lock private space")
                    : (profile.quiet ? "Resume work apps" : "Pause work apps");
            button(appRows, action, () -> catalog.quiet(profile, !profile.quiet));
        }
        if (!catalog.error.isEmpty()) text(appRows, catalog.error, 14, MUTED, false);
        String filter = appQuery.getText().toString();
        int count = 0;
        for (AppCatalog.Entry entry : catalog.entries()) {
            boolean inTab = entry.kind == appTab || (appTab == AppPolicy.Kind.PERSONAL && entry.kind == AppPolicy.Kind.CLONE);
            if (!inTab || !AppPolicy.matches(entry.label, entry.packageName, filter)) continue;
            String label = entry.label + (entry.kind == AppPolicy.Kind.CLONE ? " · clone" : "") + (entry.shortcut != null ? " · shortcut" : "");
            button(appRows, label, () -> catalog.open(entry)); count++;
        }
        if (count == 0) text(appRows, appTab == AppPolicy.Kind.PRIVATE ? "Unlock private space to view its apps." : "No matching apps", 14, MUTED, false);
    }
    private void terminal() {
        LinearLayout box = card("Run a command", "Each job loads installed Omarchy shell defaults, then your session.sh overrides. Output arrives when the process exits.");
        EditText command = edit(box, "id; uname -a", ui.getString("command", "id; uname -a"), true);
        remember(command, "command");
        button(box, "Run", () -> run("terminal", command.getText().toString(), Commands.terminal(command.getText().toString())));
        button(box, "Open interactive Termux session", () -> TermuxBridge.run(this, "interactive", "Interactive shell", "", true));
        text(box, "Use an interactive session for editors, persistent cd, prompts, and long-running programs. Close it with exit to return its transcript.", 13, MUTED, false);
        button(box, "Check runtime identity", () -> run("inspect", "Runtime identity", Commands.inspect()));
        history = card("Command history", "Output is stored privately on this phone. Tap a job for its result.");
    }
    private String run(String kind, String label, String script) {
        String id = TermuxBridge.run(this, kind, label, script, false);
        Toast.makeText(this, "Sent to Termux", Toast.LENGTH_SHORT).show();
        updateJobs();
        return id;
    }
    private void software() {
        LinearLayout box = card("Native packages", "Install tools in your Termux environment. Android apps stay available through their usual installers.");
        EditText name = edit(box, "python, git, nano…", "", false);
        button(box, "Install package", () -> {
            String packageName = name.getText().toString().trim();
            String script = Commands.install(packageName);
            new AlertDialog.Builder(this).setTitle("Install " + packageName + "?")
                    .setMessage("Termux will download this package and its dependencies into your native environment.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Install", (dialog, which) -> safely(() -> run("install", "Install " + packageName, script))).show();
        });
        button(box, "Refresh repositories", () -> run("packages", "Refresh repositories", "exec " + Commands.PREFIX + "/bin/pkg update -y"));
        button(box, "List installed packages", () -> run("packages", "Installed packages", "exec " + Commands.PREFIX + "/bin/dpkg-query -W"));
        history = card("Package jobs", "Repository failures and process exit codes appear in the job result.");
    }
    private void themes() {
        LinearLayout active = card(OmarchyThemes.title(palette), "Omarchy colors are shared across home, files, controls, notifications, and the floating panel.");
        themeStatus = text(active, OmarchyThemes.nativeStatus(this), 14, MUTED, false);
        LinearLayout swatches = new LinearLayout(this);
        for (String color : new String[]{"background", "foreground", "accent", "red", "green", "blue"}) {
            View swatch = new View(this);
            swatch.setBackgroundColor(palette.color(color)); swatch.setContentDescription(color);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(24), 1);
            size.setMargins(dp(2), dp(6), dp(2), dp(6));
            swatches.addView(swatch, size);
        }
        active.addView(swatches);
        button(active, "Apply to terminal", () -> {
            if (OmarchyThemes.pending(this)) throw new IllegalStateException("A theme install is awaiting a result. Check its job in the list below.");
            new AlertDialog.Builder(this).setTitle("Connect Omarchy configs?")
                    .setMessage("Apply " + OmarchyThemes.title(palette) + " to Termux colors, Bash defaults, and btop. Add Omarchy's Starship and tmux configs where no config exists. Replaced files are backed up; existing app configs and .bashrc are kept.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> safely(() -> {
                        if (OmarchyThemes.pending(this)) throw new IllegalStateException("An install is already pending.");
                        try {
                            String script = new OmarchyConfigs(path -> OmarchyThemes.asset(this, "omarchy/" + path)).install(palette, Commands.HOME);
                            OmarchyThemes.prefs(this).edit().remove("native-key").apply();
                            run("omarchy-apply:" + palette.key(), "Connect Omarchy · " + OmarchyThemes.title(palette), script);
                        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read bundled Omarchy configs", e); }
                    })).show();
        });
        button(active, "Install terminal tools", () -> new AlertDialog.Builder(this).setTitle("Install native tools?")
                .setMessage("Download Starship, tmux, eza, fzf, bat, zoxide, git, and nano through Termux. Package downloads require internet access.")
                .setNegativeButton("Cancel", null).setPositiveButton("Install", (dialog, which) -> safely(() -> run("install", "Omarchy terminal tools",
                        "exec " + Commands.PREFIX + "/bin/pkg install -y starship tmux eza fzf bat zoxide git nano"))).show());
        text(active, "btop theme files are included for compatible installations. Termux supplies btop through its root repository, so this setup does not install it on a stock phone.", 13, MUTED, false);
        button(active, "Open themed terminal", () -> TermuxBridge.run(this, "interactive", "Omarchy interactive shell", "", true));
        LinearLayout choices = card("Omarchy palettes", "The same 22 bundled palettes and color-resolution rules as the original launcher. Select one, then apply it to the terminal.");
        try {
            for (OmarchyPalette candidate : OmarchyThemes.bundled(this)) {
                Button choose = button(choices, (candidate.key().equals(palette.key()) ? "✓ " : "") + OmarchyThemes.title(candidate), () -> {
                    OmarchyThemes.select(this, candidate); render();
                });
                choose.setTextColor(candidate.color("accent"));
                choose.setBackgroundTintList(ColorStateList.valueOf(candidate.color("background")));
            }
        } catch (java.io.IOException e) { text(choices, "Cannot load bundled themes: " + e.getMessage(), 14, MUTED, false); }
        button(choices, "Import an Omarchy colors.toml", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 811));
        text(choices, "Imported palettes are color data only. Desktop Hyprland/Waybar settings do not control Android; launcher widgets and wallpaper files are not imported in this build.", 13, MUTED, false);
        LinearLayout wallpaper = card("Android wallpaper", "Set a photo or the current palette's background color on the home and lock screens. An active live wallpaper is kept.");
        text(wallpaper, ui.getString("wallpaper-result", "No wallpaper applied yet."), 14, MUTED, false);
        button(wallpaper, "Choose a wallpaper image", () -> {
            if (wallpaperBusy) throw new IllegalStateException("Wait for the current wallpaper operation.");
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 812);
        });
        button(wallpaper, "Use palette background color", () -> applyWallpaper(null, BG));
        history = card("Theme and config jobs", "A terminal sync is confirmed only after Termux returns a successful result.");
    }
    private void applyWallpaper(Uri image, int color) {
        if (wallpaperBusy) throw new IllegalStateException("Wait for the current wallpaper operation.");
        wallpaperBusy = true;
        worker.execute(() -> {
            java.io.File temporary = null;
            String outcome;
            try {
                temporary = java.io.File.createTempFile("wallpaper-", ".image", getCacheDir());
                try (java.io.OutputStream output = new java.io.FileOutputStream(temporary)) {
                    if (image == null) {
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888);
                        try { bitmap.eraseColor(color); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); }
                        finally { bitmap.recycle(); }
                    } else try (java.io.InputStream input = getContentResolver().openInputStream(image)) {
                        if (input == null) throw new java.io.IOException("Cannot open this image.");
                        byte[] buffer = new byte[8192]; int count, total = 0;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > 20 * 1024 * 1024) throw new java.io.IOException("Choose an image smaller than 20 MB.");
                            output.write(buffer, 0, count);
                        }
                    }
                }
                outcome = dev.omalauncher.platform.StaticWallpaper.apply(getApplicationContext(), temporary).message;
            } catch (Exception e) { outcome = "Wallpaper was not applied: " + e.getMessage(); }
            finally { if (temporary != null) temporary.delete(); }
            String message = outcome;
            ui.edit().putString("wallpaper-result", message).apply();
            handler.post(() -> {
                wallpaperBusy = false;
                if (isDestroyed()) return;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if ("Themes".equals(tab)) render();
            });
        });
    }
    private void files() {
        LinearLayout box = card("Native home", "Persistent files in your Termux home directory.");
        button(box, "List home files", () -> run("files", "Home files", "ls -la -- \"$HOME\""));
        button(box, "Browse and open a file", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 810));
        text(box, "Android's file picker can expose Termux files when its document provider is available.", 13, MUTED, false);
        LinearLayout config = card("Session configuration", "~/.config/oma/session.sh · shell syntax is checked before saving. The previous version is kept as session.sh.previous.");
        configEditor = edit(config, "Shell configuration", ui.getString("config-draft", ""), true);
        configEditor.setMinLines(6);
        remember(configEditor, "config-draft");
        button(config, "Load current configuration", () -> new AlertDialog.Builder(this)
                .setTitle("Replace the editor draft?").setMessage("Load the current configuration from your native home.")
                .setNegativeButton("Cancel", null).setPositiveButton("Load", (dialog, which) -> safely(() -> {
                    String id = run("config-read", "Read session configuration", Commands.readConfig());
                    ui.edit().putString("config-read-job", id).putString("config-draft-at-read", configEditor.getText().toString()).apply();
                })).show());
        button(config, "Save configuration", () -> run("config-write", "Save session configuration", Commands.saveConfig(configEditor.getText().toString())));
        history = card("File operations", "Select a job to view its result.");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 812 && result == RESULT_OK && data != null && data.getData() != null)
            safely(() -> applyWallpaper(data.getData(), BG));
        if (request == 811 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            worker.execute(() -> {
                try {
                    String colors = OmarchyThemes.text(getContentResolver().openInputStream(uri));
                    OmarchyPalette imported = new OmarchyPalette("imported-" + OmarchyPalette.sha256(colors).substring(0, 8), colors);
                    handler.post(() -> { if (!isDestroyed()) safely(() -> { OmarchyThemes.select(this, imported); tab = "Themes"; render(); }); });
                } catch (Exception e) {
                    handler.post(() -> { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("Theme import failed").setMessage(e.getMessage()).setPositiveButton("OK", null).show(); });
                }
            });
        }
        if (request == 810 && result == RESULT_OK && data != null && data.getData() != null) safely(() -> {
            Uri uri = data.getData();
            String type = getContentResolver().getType(uri);
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type == null ? "application/octet-stream" : type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open, "Open file"));
        });
    }
    private void controls() {
        LinearLayout box = card("Android administration", "Shizuku authorizes these controls. The result includes the actual service identity and Android's response.");
        adminStatus = text(box, "", 14, ACCENT, false);
        button(box, "Connect controls", () -> admin.request());
        button(box, "Read device state", () -> adminCall(0));
        button(box, "Wi-Fi on", () -> adminCall(1));
        button(box, "Wi-Fi off", () -> new AlertDialog.Builder(this).setTitle("Turn Wi-Fi off?")
                .setMessage("Connections using Wi-Fi will disconnect.").setNegativeButton("Cancel", null)
                .setPositiveButton("Turn off", (dialog, which) -> safely(() -> adminCall(2))).show());
        button(box, "Lock rotation", () -> adminCall(3));
        button(box, "Enable auto-rotate", () -> adminCall(4));
        adminOutput = text(box, lastAdminOutput, 12, MUTED, true);
        adminOutput.setTextIsSelectable(true);
        LinearLayout everyday = card("Everyday settings", "");
        AudioManager audio = getSystemService(AudioManager.class);
        text(everyday, "Media volume", 14, TEXT, false);
        SeekBar volume = new SeekBar(this);
        volume.setProgressTintList(ColorStateList.valueOf(ACCENT));
        volume.setThumbTintList(ColorStateList.valueOf(ACCENT));
        volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        volume.setContentDescription("Media volume");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) safely(() -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0));
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        everyday.addView(volume);
        button(everyday, "Internet settings", () -> startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)));
        button(everyday, "Display settings", () -> startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        button(everyday, "Android settings", () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
    }
    private void adminCall(int operation) {
        IAdminService service = admin.service;
        if (service == null) throw new IllegalStateException(admin.status());
        if (adminBusy) throw new IllegalStateException("Wait for the current Android operation to finish.");
        adminBusy = true;
        lastAdminOutput = "Waiting for Android…";
        if (adminOutput != null) adminOutput.setText(lastAdminOutput);
        worker.execute(() -> {
            String output;
            try {
                if (operation == 0) output = service.inspect();
                else if (operation <= 2) output = service.setWifi(operation == 1);
                else output = service.setRotationLocked(operation == 3);
            } catch (Exception e) { output = "Operation failed: " + e; }
            String result = output;
            handler.post(() -> {
                if (isDestroyed()) return;
                adminBusy = false;
                lastAdminOutput = result;
                if (adminOutput != null) adminOutput.setText(result);
            });
        });
    }
    private void inbox() {
        LinearLayout box = card("Notification center", InboxService.available() ? "Current notifications from connected apps." : "Grant notification access to show your notifications here.");
        button(box, "Notification access", () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(box, "Refresh", this::render);
        StatusBarNotification[] active = InboxService.notifications();
        if (active.length == 0 && InboxService.available()) text(box, "No active notifications", 15, MUTED, false);
        for (StatusBarNotification item : active) {
            if (getPackageName().equals(item.getPackageName())) continue;
            Notification notification = item.getNotification();
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE, item.getPackageName());
            CharSequence message = notification.extras.getCharSequence(Notification.EXTRA_TEXT, "");
            LinearLayout row = card(String.valueOf(title), item.getPackageName());
            text(row, String.valueOf(message), 15, TEXT, false);
            if (notification.contentIntent != null) button(row, "Open", () -> {
                try { InboxService.open(this, item.getKey()); } catch (PendingIntent.CanceledException e) { throw new IllegalStateException("This notification is no longer available."); }
            });
            if (item.isClearable()) button(row, "Dismiss", () -> { InboxService.dismiss(item.getKey()); handler.postDelayed(this::render, 250); });
        }
    }
    private void setup() {
        LinearLayout runtime = card("1 / Native runtime", "Install Termux, open it once to initialize its packages, then enable command access.");
        button(runtime, "Termux downloads", () -> openUrl("https://github.com/termux/termux-app#installation"));
        button(runtime, "Open Termux", () -> openPackage("com.termux"));
        String setupCommand = "mkdir -p ~/.termux && printf '\\nallow-external-apps = true\\n' >> ~/.termux/termux.properties && termux-reload-settings";
        text(runtime, "Run this once in Termux:", 14, MUTED, false);
        TextView setupText = text(runtime, setupCommand, 12, TEXT, true); setupText.setTextIsSelectable(true);
        button(runtime, "Copy setup command", () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Termux setup", setupCommand));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        button(runtime, "Grant command permission", () -> {
            if (!TermuxBridge.installed(this)) throw new IllegalStateException("Install Termux first.");
            requestPermissions(new String[]{TermuxBridge.PERMISSION}, 720);
        });
        button(runtime, "Runtime permission settings", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        button(runtime, "Verify native runtime", () -> run("inspect", "Runtime identity", Commands.inspect()));
        button(runtime, "Connect Omarchy themes and configs", () -> { tab = "Themes"; render(); });
        LinearLayout management = card("2 / Android controls", "Start Shizuku and allow Strata to use it. Restart Shizuku after rebooting your phone.");
        button(management, "Shizuku downloads and setup", () -> openUrl("https://shizuku.rikka.app/guide/setup/"));
        button(management, "Open Shizuku", () -> openPackage("moe.shizuku.privileged.api"));
        button(management, "Authorize controls", () -> admin.request());
        adminStatus = text(management, "", 14, ACCENT, false);
        LinearLayout interfaceBox = card("3 / Your interface", "Use Strata as home and open its panels from other apps. Android's secure lock screen, system shade, and other apps keep their own interface in this preview.");
        if (Build.VERSION.SDK_INT >= 35) button(interfaceBox, catalog.privateShown() ? "Hide private-space tab" : "Show private-space tab", () -> {
            catalog.privateShown(!catalog.privateShown()); appTab = AppPolicy.Kind.PERSONAL; render();
        });
        button(interfaceBox, "Choose default home", () -> {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 820);
            else startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        button(interfaceBox, "Grant notification access", () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(interfaceBox, "Allow floating controls", () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        button(interfaceBox, "Start floating controls", () -> {
            if (!Settings.canDrawOverlays(this)) throw new IllegalStateException("Allow floating controls first.");
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 740);
                return;
            }
            startForegroundService(new Intent(this, PanelService.class));
        });
        button(interfaceBox, "Stop floating controls", () -> stopService(new Intent(this, PanelService.class)));
        button(interfaceBox, "Choose another home", () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
        history = card("Setup checks", "A granted permission becomes a verified runtime connection only after a command returns.");
    }
    private void openUrl(String url) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private void openPackage(String name) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(name);
        if (intent == null) throw new IllegalStateException("This component is not installed.");
        startActivity(intent);
    }
    private void updateJobs() {
        if (isDestroyed()) return;
        if (themeStatus != null) themeStatus.setText(OmarchyThemes.nativeStatus(this));
        JSONArray jobs = JobStore.all(this);
        String configRead = ui.getString("config-read-job", "");
        if (history != null) {
            // Preserve card title and description.
            while (history.getChildCount() > 2) history.removeViewAt(2);
        }
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null) continue;
            String state = job.optString("state");
            if (job.optString("id").equals(configRead) && !"waiting".equals(state)) {
                ui.edit().remove("config-read-job").apply();
                if (!ui.getString("config-draft-at-read", "").equals(ui.getString("config-draft", ""))) {
                    Toast.makeText(this, "Your newer draft was kept. The loaded config is in the job result.", Toast.LENGTH_LONG).show();
                } else if (job.optInt("exit", -1) == 0 && job.optString("error").isEmpty() && !job.optBoolean("truncated")) {
                    String contents = job.optString("stdout");
                    ui.edit().putString("config-draft", contents).apply();
                    if (configEditor != null) configEditor.setText(contents);
                } else Toast.makeText(this, "Config load failed. Open the job result for details.", Toast.LENGTH_LONG).show();
            }
            if (history == null || i >= 12) continue;
            String result = "waiting".equals(state) ? "awaiting result" : !job.optString("error").isEmpty() ? "runtime error" : "exit " + job.optInt("exit", -1);
            String label = job.optString("label");
            if (label.length() > 65) label = label.substring(0, 65) + "…";
            button(history, label + "\n" + result, () -> showJob(job));
        }
    }
    private void showJob(JSONObject job) {
        String text = DateFormat.getDateTimeInstance().format(new Date(job.optLong("time"))) + "\n" + job.optString("label") + "\n\n";
        if ("waiting".equals(job.optString("state")))
            text += "Awaiting a Termux result. This does not confirm that the process is running. Check Termux if it does not return.\n";
        else text += (job.has("exit") ? "Process exit: " + job.optInt("exit") + "\n" : "")
                + job.optString("error") + "\n" + job.optString("stdout")
                + (job.optString("stderr").isEmpty() ? "" : "\n[stderr]\n" + job.optString("stderr"))
                + (job.optBoolean("truncated") ? "\n[Termux or Strata truncated this output]" : "");
        ScrollView scroll = new ScrollView(this);
        TextView output = new TextView(this); output.setPadding(dp(20), dp(12), dp(20), dp(12));
        output.setTypeface(Typeface.MONOSPACE); output.setTextSize(12); output.setTextIsSelectable(true); output.setText(text);
        scroll.addView(output);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Job result").setView(scroll).setPositiveButton("Close", null);
        if ("waiting".equals(job.optString("state"))) dialog.setNeutralButton("Stop tracking", (prompt, choice) ->
                new AlertDialog.Builder(this).setTitle("Stop waiting for this result?")
                        .setMessage("This does not stop the native process. Check or stop it in Termux before running the same operation again.")
                        .setNegativeButton("Keep waiting", null).setPositiveButton("Stop tracking", (confirmation, which) ->
                                JobStore.finish(this, job.optString("id"), "", "", null,
                                        "Tracking stopped by user. Native process state is unknown; it may still be running in Termux.", false)).show());
        dialog.show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        updateStatus();
        if (request == 740 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && Settings.canDrawOverlays(this))
            startForegroundService(new Intent(this, PanelService.class));
    }
}
