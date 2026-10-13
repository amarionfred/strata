package dev.omalauncher.companion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.*;

/** One embedded workspace; its native sessions outlive the view and Activity. */
@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
final class WorkspaceView extends WebView {
    private static final String ORIGIN = "https://strata.invalid/";
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final ExecutorService input = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private final String initialPage;
    private volatile WorkspaceConnection terminal;
    private volatile int generation;
    private volatile boolean disposed;
    private volatile CountDownLatch outputReady;
    private boolean starting;
    private boolean ready;
    private final java.util.List<String> queued = new java.util.ArrayList<>();

    WorkspaceView(Activity activity, String page) {
        super(activity);
        this.activity = activity;
        initialPage = page;
        preferences = activity.getSharedPreferences("native-workspace", Context.MODE_PRIVATE);
        if (!preferences.contains("token")) preferences.edit()
                .putString("token", WorkspaceConnection.randomToken())
                .putInt("port", 40000 + new SecureRandom().nextInt(20000)).commit();
        setBackgroundColor(OmarchyThemes.current(activity).color("background"));
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowContentAccess(false);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        addJavascriptInterface(new Bridge(), "Strata");
        setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(ORIGIN)) return blocked();
                String path = url.substring(ORIGIN.length());
                if (!path.matches("[a-zA-Z0-9_/.-]+") || path.contains("..")) return blocked();
                try {
                    String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                    return new WebResourceResponse(mime, "UTF-8", activity.getAssets().open("workspace/" + path));
                } catch (IOException e) { return blocked(); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(ORIGIN + "index.html")) return;
                try {
                    JSONObject options = new JSONObject().put("page", initialPage)
                            .put("theme", new JSONObject(OmarchyThemes.current(activity).colors))
                            .put("session", preferences.getString("session", ""))
                            .put("draft", new JSONObject(preferences.getString("draft", "{}")));
                    evaluateJavascript("workspace.init(" + options + ")", ignored -> {
                        ready = true;
                        for (String source : queued) evaluateJavascript(source, null);
                        queued.clear();
                    });
                } catch (JSONException ignored) { }
            }
        });
        loadUrl(ORIGIN + "index.html");
    }
    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }
    private WorkspaceConnection connect() throws Exception {
        return new WorkspaceConnection(preferences.getInt("port", 0), preferences.getString("token", ""));
    }
    private void js(String source) { main.post(() -> {
        if (disposed) return;
        if (ready) evaluateJavascript(source, null); else if (queued.size() < 32) queued.add(source);
    }); }
    private void error(String message) { js("workspace.error(" + JSONObject.quote(message) + ")"); }
    private JSONObject rpc(JSONObject request) throws Exception {
        try (WorkspaceConnection connection = connect()) { connection.send(request); return connection.read(); }
    }
    private void launch() {
        if (starting) return;
        starting = true;
        try {
            String source;
            try (InputStream stream = activity.getAssets().open("runtime/runtime.py")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                source = bytes.toString("UTF-8");
            }
            // Source and credential travel through Termux's stdin capability, never
            // argv, a shared file, a URL, a WebView bridge argument or a job label.
            JSONObject payload = new JSONObject().put("source", source)
                    .put("token", preferences.getString("token", "")).put("port", preferences.getInt("port", 0));
            String loader = "import sys,json,io; c=json.load(sys.stdin); s=c.pop('source'); sys.stdin=io.StringIO(json.dumps(c)); exec(compile(s,'strata-runtime','exec'))";
            String command = "for tool in python tmux; do command -v \"$tool\" >/dev/null || { printf 'Missing %s. Use Prepare workspace in Strata.\\n' \"$tool\" >&2; exit 1; }; done\nexec python -u -c " + Commands.quote(loader);
            TermuxBridge.workspace(activity, command, payload.toString());
            requests.execute(() -> {
                Exception last = null;
                for (int attempt = 0; attempt < 20 && !disposed; attempt++) {
                    try { JSONObject status = rpc(new JSONObject().put("op", "status"));
                        js("workspace.connected(" + status + ")");
                        main.post(() -> starting = false); return;
                    } catch (Exception e) { last = e; }
                    try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                }
                error("Workspace did not connect. Prepare its tools, then retry. Setup shows the runtime job result. " + (last == null ? "" : last.getMessage()));
                main.post(() -> starting = false);
            });
        } catch (Exception e) { starting = false; error(e.getMessage()); }
    }
    private void detach() {
        generation++;
        CountDownLatch pending = outputReady; outputReady = null;
        if (pending != null) pending.countDown();
        WorkspaceConnection previous = terminal; terminal = null;
        if (previous != null) try { previous.close(); } catch (IOException ignored) { }
    }
    private void attach(JSONObject request) {
        detach();
        int current = generation;
        Thread reader = new Thread(() -> {
            try (WorkspaceConnection connection = connect()) {
                connection.send(request); JSONObject attached = connection.read();
                if (disposed || current != generation) return;
                terminal = connection; connection.streaming();
                if (request.optString("op").equals("display")) js("workspace.displayAttached()");
                else {
                    preferences.edit().putString("session", request.optString("name")).apply();
                    js("workspace.attached(" + attached + ")");
                }
                while (!disposed && current == generation) {
                    JSONObject event = connection.read();
                    // Backpressure bounds pending WebView output to one chunk.
                    CountDownLatch rendered = new CountDownLatch(1);
                    outputReady = rendered;
                    main.post(() -> {
                        if (disposed || current != generation) { rendered.countDown(); return; }
                        evaluateJavascript("workspace.output(" + event + "," + current + ")", null);
                    });
                    if (!rendered.await(15, TimeUnit.SECONDS)) break;
                    if (event.optBoolean("detached")) break;
                }
            } catch (Exception e) { if (!disposed && current == generation) error("Terminal disconnected: " + e.getMessage()); }
            finally { if (current == generation) terminal = null; }
        }, "strata-terminal");
        reader.start();
    }
    private final class Bridge {
        @JavascriptInterface public void request(String id, String payload) {
            if (disposed || id.length() > 40 || payload.length() > WorkspaceConnection.MAX_FRAME) return;
            requests.execute(() -> {
                try {
                    JSONObject request = new JSONObject(payload), response = rpc(request);
                    js("workspace.reply(" + JSONObject.quote(id) + "," + response + ")");
                } catch (Exception e) {
                    js("workspace.reply(" + JSONObject.quote(id) + ",{\"ok\":false,\"error\":" + JSONObject.quote(e.getMessage()) + "})");
                }
            });
        }
        @JavascriptInterface public void start() { main.post(() -> {
            requests.execute(() -> {
                try { JSONObject status = rpc(new JSONObject().put("op", "status")); js("workspace.connected(" + status + ")"); }
                catch (Exception e) { main.post(() -> { if (!disposed) launch(); }); }
            });
        }); }
        @JavascriptInterface public void prepare() { main.post(() -> {
            try {
                TermuxBridge.run(activity, "install", "Prepare native workspace", "exec pkg install -y python tmux git nano curl", false);
                js("workspace.message('Installing workspace tools. Reconnect when the Setup job finishes.')");
            } catch (Exception e) { error(e.getMessage()); }
        }); }
        @JavascriptInterface public void attach(String payload) {
            try { JSONObject request = new JSONObject(payload);
                if (!"attach".equals(request.optString("op")) && !"display".equals(request.optString("op"))) return;
                main.post(() -> { if (!disposed) WorkspaceView.this.attach(request); });
            } catch (Exception e) { error(e.getMessage()); }
        }
        @JavascriptInterface public void input(String payload) {
            if (disposed || payload.length() > 65536) return;
            WorkspaceConnection target = terminal;
            input.execute(() -> {
                try { if (target != null && target == terminal) target.send(new JSONObject(payload)); }
                catch (Exception e) { error(e.getMessage()); }
            });
        }
        @JavascriptInterface public void detach() { main.post(WorkspaceView.this::detach); }
        @JavascriptInterface public void copy(String value) { main.post(() -> activity.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("Terminal selection", value))); }
        @JavascriptInterface public void paste() { main.post(() -> {
            ClipboardManager manager = activity.getSystemService(ClipboardManager.class);
            ClipData clip = manager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(activity);
                if (text != null && text.length() <= 16000) js("workspace.paste(" + JSONObject.quote(text.toString()) + ")");
            }
        }); }
        @JavascriptInterface public void draft(String value) {
            if (value.length() <= WorkspaceConnection.MAX_FRAME) preferences.edit().putString("draft", value).apply();
        }
        @JavascriptInterface public void rendered(int stream) {
            CountDownLatch pending = outputReady;
            if (stream == generation && pending != null) pending.countDown();
        }
        @JavascriptInterface public void importFile() { main.post(() -> {
            try { activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 830); }
            catch (Exception e) { error(e.getMessage()); }
        }); }
        @JavascriptInterface public void exportFile(String name, String text) { main.post(() -> {
            if (text.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) { error("Choose a text file smaller than 256 KiB"); return; }
            preferences.edit().putString("export-text", text).apply();
            try { activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, new java.io.File(name).getName()), 831); }
            catch (Exception e) { preferences.edit().remove("export-text").apply(); error(e.getMessage()); }
        }); }
    }
    void documentResult(int request, Uri uri) {
        if (uri == null) { preferences.edit().remove("export-text").apply(); return; }
        requests.execute(() -> {
            try {
                if (request == 831) {
                    String value = preferences.getString("export-text", null);
                    if (value == null) throw new IOException("The export draft is no longer available");
                    try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("Cannot open export destination");
                        output.write(value.getBytes(StandardCharsets.UTF_8));
                    }
                    preferences.edit().remove("export-text").apply();
                    js("workspace.message('Exported file to Android')");
                } else {
                    String name = "imported.txt";
                    try (android.database.Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                    }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream source = activity.getContentResolver().openInputStream(uri)) {
                        if (source == null) throw new IOException("Cannot read this document");
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = source.read(buffer)) != -1) {
                            if (bytes.size() + count > 256 * 1024) throw new IOException("Choose a text file smaller than 256 KiB");
                            bytes.write(buffer, 0, count);
                        }
                    }
                    String value = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
                    if (value.indexOf('\0') >= 0) throw new IOException("Choose a UTF-8 text file");
                    js("workspace.imported(" + JSONObject.quote(name) + "," + JSONObject.quote(value) + ")");
                }
            } catch (Exception e) { error(e.getMessage()); }
        });
    }
    void suspendWorkspace() { detach(); js("workspace.suspend()"); onPause(); }
    void resumeWorkspace() { onResume(); js("if (typeof workspace !== 'undefined') workspace.resume()"); }
    void close() {
        disposed = true; detach(); requests.shutdownNow(); input.shutdownNow(); main.removeCallbacksAndMessages(null);
        removeJavascriptInterface("Strata"); stopLoading(); destroy();
    }
}
