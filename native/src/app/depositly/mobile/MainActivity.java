package app.depositly.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.*;
import app.depositly.mobile.cloud.DriveBackupBridge;
import app.depositly.mobile.reminders.ReminderBridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Keeps the original vault preferences and document picker behavior across updates. */
public class MainActivity extends Activity {
    private WebView web;
    private DriveBackupBridge cloud;
    private boolean pageReady;
    private String pendingImport;
    private static final int EXPORT = 10, IMPORT = 11;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);
        applySystemTheme(savedDark());
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // The interface is self-contained. Never expose native bridges to remote content.
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverImport();
                ReminderBridge.pageReady(MainActivity.this, web);
                cloud.pageReady();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView v, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("Continue", (d, w) -> result.confirm())
                    .setNegativeButton("Cancel", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        cloud = new DriveBackupBridge(this, web, saved);
        web.addJavascriptInterface(new Store(), "Vault");
        web.addJavascriptInterface(new ReminderBridge(this, web), "Reminders");
        web.addJavascriptInterface(cloud, "DriveBackup");
        try (InputStream input = getAssets().open("index.html")) {
            web.loadDataWithBaseURL("https://depositly.local/", read(input), "text/html", "UTF-8", null);
        } catch (IOException e) { throw new IllegalStateException("Could not load Depositly", e); }
    }
    private boolean savedDark() {
        try { return new JSONObject(getSharedPreferences("vault", 0).getString("data", "{}")).optBoolean("dark"); }
        catch (Exception e) { return false; }
    }
    public void applySystemTheme(boolean dark) {
        int color = Color.parseColor(dark ? "#101b18" : "#f6f7f3");
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        getWindow().getDecorView().setBackgroundColor(color);
        int flags = getWindow().getDecorView().getSystemUiVisibility() & ~0x2010;
        if (!dark) flags |= 0x2010;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        web.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }
    private File exportCache() { return new File(getCacheDir(), "pending-export.txt"); }
    public static String read(InputStream in) throws IOException {
        if (in == null) throw new IOException("No file selected");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) {
            if (out.size() + n > 20000000) throw new IOException("Backup is too large");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
    private void message(String value) { runOnUiThread(() -> web.evaluateJavascript("toast(" + JSONObject.quote(value) + ")", null)); }
    private void deliverImport() {
        if (!pageReady || pendingImport == null) return;
        web.evaluateJavascript("restoreData(" + JSONObject.quote(pendingImport) + ")", null);
        pendingImport = null;
    }
    public class Store {
        @JavascriptInterface public String load() { return getSharedPreferences("vault", 0).getString("data", ""); }
        @JavascriptInterface public boolean save(String json) { return getSharedPreferences("vault", 0).edit().putString("data", json).commit(); }
        @JavascriptInterface public void setTheme(boolean dark) { runOnUiThread(() -> applySystemTheme(dark)); }
        @JavascriptInterface public void backup(String json) { exportFile(json, "depositly-entries.json", "application/json"); }
        @JavascriptInterface public void exportFile(String data, String name, String mime) {
            try (OutputStream out = new FileOutputStream(exportCache())) { out.write(data.getBytes(StandardCharsets.UTF_8)); }
            catch (IOException e) { message("Could not prepare export. Please try again."); return; }
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name), EXPORT));
        }
        @JavascriptInterface public void restore() {
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT));
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (cloud.activityResult(request, result, data)) return;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == EXPORT) {
                try (InputStream in = new FileInputStream(exportCache()); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("No output stream");
                    byte[] buffer = new byte[8192];
                    for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
                }
                exportCache().delete();
                message("Export saved");
            } else if (request == IMPORT) {
                try (InputStream in = getContentResolver().openInputStream(uri)) { pendingImport = read(in); }
                deliverImport();
            }
        } catch (Exception e) { message("Could not read or save the file. Please try again."); }
    }
    @Override public void onBackPressed() { web.evaluateJavascript("handleBack()", null); }
    @Override protected void onResume() {
        super.onResume();
        ReminderBridge.onResume(this, web);
        if (cloud != null) cloud.resume();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (pageReady) ReminderBridge.pageReady(this, web);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 0x1007) ReminderBridge.permissionResult(this, web);
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); cloud.saveInstanceState(state); }
    @Override protected void onDestroy() {
        if (cloud != null) cloud.destroy();
        if (web != null) { web.removeJavascriptInterface("DriveBackup"); web.destroy(); }
        super.onDestroy();
    }
}
