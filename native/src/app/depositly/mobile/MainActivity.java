package app.depositly.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.webkit.*;
import android.widget.Toast;
import app.depositly.mobile.cloud.DriveBackupBridge;
import app.depositly.mobile.cloud.RegistryStorage;
import app.depositly.mobile.reminders.ReminderBridge;
import app.depositly.mobile.updates.AppUpdateBridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Keeps the original vault preferences and document picker behavior across updates. */
public class MainActivity extends Activity {
    private WebView web;
    private DriveBackupBridge cloud;
    private AppUpdateBridge updates;
    private boolean pageReady;
    private String pendingImport;
    private String pendingImportOwner;
    private final DocumentTransfer documents = new DocumentTransfer(command -> runOnUiThread(command));
    private DocumentRequest documentRequest;
    private boolean backCheckPending;
    private long backRequestGeneration;
    private long queuedBackAt = -1;
    private long lastRootBackAt = -1;
    private Toast exitToast;
    private static final long EXIT_BACK_WINDOW_MS = 2000;
    private static final int EXPORT = 10, IMPORT = 11;

    private static final class DocumentRequest {
        final int type;
        final String owner;
        final CancellationSignal cancellation = new CancellationSignal();
        volatile boolean canceled;
        volatile File file;
        DocumentRequest(int type, String owner) { this.type = type; this.owner = owner; }
        void clear() {
            canceled = true;
            cancellation.cancel();
            File temporary = file;
            if (temporary != null) temporary.delete();
        }
    }

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
                updates.pageReady();
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
        updates = new AppUpdateBridge(this, web);
        web.addJavascriptInterface(new Store(), "Vault");
        web.addJavascriptInterface(new ReminderBridge(this, web), "Reminders");
        web.addJavascriptInterface(cloud, "DriveBackup");
        web.addJavascriptInterface(updates, "AppUpdates");
        try {
            String document = AppDocument.load(name -> {
                try (InputStream input = getAssets().open(name)) { return read(input); }
            });
            web.loadDataWithBaseURL("https://depositly.local/", document, "text/html", "UTF-8", null);
        } catch (IOException e) { throw new IllegalStateException("Could not load Depositly", e); }
    }
    private boolean savedDark() {
        try { return new JSONObject(RegistryStorage.load(this)).optBoolean("dark"); }
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
    public static String read(InputStream in) throws IOException {
        return DocumentTransfer.read(in, () -> false);
    }
    private boolean live() { return !isFinishing() && !isDestroyed(); }
    private void message(String value) { runOnUiThread(() -> {
        if (live()) web.evaluateJavascript("toast(" + JSONObject.quote(value) + ")", null);
    }); }
    private void deliverImport() {
        if (!pageReady || pendingImport == null) return;
        String owner = pendingImportOwner, imported = pendingImport;
        pendingImport = null;
        pendingImportOwner = null;
        if (!live()) return;
        if (!owner.equals(RegistryStorage.owner(this))) { accountChanged(); return; }
        // Account changes may reach JavaScript after this callback was queued. Check both views.
        web.evaluateJavascript("if(typeof activeVaultSub!=='undefined'&&activeVaultSub===" + JSONObject.quote(owner)
            + "&&Vault.owner()===" + JSONObject.quote(owner) + "){restoreData(" + JSONObject.quote(imported)
            + ")}else{toast('Your account changed. Choose the backup again in the correct account.');}", null);
    }
    private void accountChanged() { message("Your account changed. Choose the backup again in the correct account."); }
    private DocumentRequest beginDocument(int type, String owner) {
        if (!live()) return null;
        if (owner == null || !owner.equals(RegistryStorage.owner(this))) { accountChanged(); return null; }
        if (documentRequest != null) { message("Finish the current file selection or transfer first."); return null; }
        documentRequest = new DocumentRequest(type, owner);
        return documentRequest;
    }
    private boolean currentDocument(DocumentRequest request) {
        return live() && documentRequest == request && !request.canceled;
    }
    private void finishDocument(DocumentRequest request) {
        if (documentRequest == request) documentRequest = null;
        request.clear();
    }
    private void requireDocumentOwner(DocumentRequest request) throws IOException {
        if (request.canceled || !request.owner.equals(RegistryStorage.owner(this))) throw new IOException("The account changed");
    }
    public class Store {
        @JavascriptInterface public String load() { return RegistryStorage.load(MainActivity.this); }
        @JavascriptInterface public String owner() { return RegistryStorage.owner(MainActivity.this); }
        @JavascriptInterface public boolean save(String json) { return RegistryStorage.save(MainActivity.this,json,owner(),load()); }
        @JavascriptInterface public boolean saveForAccount(String json,String sub,String baseline) { return RegistryStorage.save(MainActivity.this,json,sub,baseline); }
        @JavascriptInterface public void setTheme(boolean dark) { runOnUiThread(() -> applySystemTheme(dark)); }
        @JavascriptInterface public void copyText(String text) {
            runOnUiThread(() -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard == null || text == null || text.isEmpty()) {
                        message("Could not copy. Please try again.");
                        return;
                    }
                    clipboard.setPrimaryClip(ClipData.newPlainText("Bank Information", text));
                    message("Copied to clipboard");
                } catch (RuntimeException e) { message("Could not copy. Please try again."); }
            });
        }
        @JavascriptInterface public void shareText(String text) {
            if (text == null || text.trim().isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(share, "Share Bank Information"));
                } catch (RuntimeException e) { message("Could not open sharing. Please try again."); }
            });
        }
        @JavascriptInterface public void exportFile(String data, String name, String mime, String owner) {
            runOnUiThread(() -> {
                DocumentRequest request = beginDocument(EXPORT, owner);
                if (request == null) return;
                message("Preparing export…");
                documents.execute(() -> {
                    try {
                        requireDocumentOwner(request);
                        request.file = File.createTempFile("depositly-export-", ".csv", getCacheDir());
                        try (InputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                             OutputStream out = new FileOutputStream(request.file)) {
                            DocumentTransfer.copy(in, out, () -> request.canceled);
                        }
                        requireDocumentOwner(request);
                        return request.file;
                    } catch (Exception e) {
                        File temporary = request.file;
                        if (temporary != null) temporary.delete();
                        throw e;
                    }
                }, (file, error) -> {
                    if (!currentDocument(request)) { finishDocument(request); return; }
                    if (!owner.equals(RegistryStorage.owner(MainActivity.this))) {
                        finishDocument(request); accountChanged(); return;
                    }
                    if (error != null) {
                        finishDocument(request); message("Could not prepare export. Please try again."); return;
                    }
                    try {
                        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name), EXPORT);
                    } catch (RuntimeException e) {
                        finishDocument(request); message("Could not open the file picker. Please try again.");
                    }
                });
            });
        }
        @JavascriptInterface public void restore(String owner) {
            runOnUiThread(() -> {
                DocumentRequest request = beginDocument(IMPORT, owner);
                if (request == null) return;
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE, "Choose CSV backup"), IMPORT);
                } catch (RuntimeException e) {
                    finishDocument(request); message("Could not open the file picker. Please try again.");
                }
            });
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (updates != null && updates.activityResult(request, result)) return;
        if (cloud.activityResult(request, result, data)) return;
        if (request != EXPORT && request != IMPORT) return;
        final DocumentRequest transfer = documentRequest;
        if (transfer == null || transfer.type != request) {
            if (result == RESULT_OK) message("The file selection expired. Please choose the file again.");
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) { finishDocument(transfer); return; }
        if (!transfer.owner.equals(RegistryStorage.owner(this))) { finishDocument(transfer); accountChanged(); return; }
        final Uri uri = data.getData();
        final ContentResolver resolver = getContentResolver();
        message(request == EXPORT ? "Saving backup…" : "Reading backup…");
        documents.execute(() -> {
            try {
                requireDocumentOwner(transfer);
                if (request == EXPORT) {
                    try (InputStream in = new FileInputStream(transfer.file);
                         ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "w", transfer.cancellation)) {
                        if (descriptor == null) throw new IOException("No output stream");
                        try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                            DocumentTransfer.copy(in, out, () -> transfer.canceled);
                        }
                    }
                    requireDocumentOwner(transfer);
                    return (String) null;
                }
                try (android.content.res.AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r", transfer.cancellation)) {
                    if (descriptor == null) throw new IOException("No input stream");
                    try (InputStream in = descriptor.createInputStream()) {
                        String imported = DocumentTransfer.read(in, () -> transfer.canceled);
                        requireDocumentOwner(transfer);
                        return imported;
                    }
                }
            } finally {
                File temporary = transfer.file;
                if (temporary != null) temporary.delete();
            }
        }, (imported, error) -> {
            if (!currentDocument(transfer)) { finishDocument(transfer); return; }
            boolean sameOwner = transfer.owner.equals(RegistryStorage.owner(this));
            finishDocument(transfer);
            if (!sameOwner) { accountChanged(); return; }
            if (error != null) { message("Could not read or save the file. Please try again."); return; }
            if (request == EXPORT) message("Export saved");
            else { pendingImport = imported; pendingImportOwner = transfer.owner; deliverImport(); }
        });
    }
    @Override public void onBackPressed() {
        if (isFinishing() || isDestroyed()) return;
        long pressedAt = SystemClock.elapsedRealtime();
        if (backCheckPending) { queuedBackAt = pressedAt; return; }
        checkBack(pressedAt);
    }
    private void checkBack(long pressedAt) {
        if (!pageReady) { handleRootBack(pressedAt); return; }
        backCheckPending = true;
        long generation = ++backRequestGeneration;
        web.evaluateJavascript("typeof handleBack === 'function' ? handleBack() : false", result -> {
            if (generation != backRequestGeneration || isFinishing() || isDestroyed()) return;
            backCheckPending = false;
            if ("false".equals(result)) handleRootBack(pressedAt);
            else resetExitBack();
            long queuedAt = queuedBackAt;
            queuedBackAt = -1;
            if (queuedAt >= 0 && !isFinishing() && !isDestroyed()) checkBack(queuedAt);
        });
    }
    private void handleRootBack(long pressedAt) {
        if (lastRootBackAt >= 0 && pressedAt - lastRootBackAt <= EXIT_BACK_WINDOW_MS) {
            resetExitBack();
            finish();
            return;
        }
        lastRootBackAt = pressedAt;
        if (exitToast != null) exitToast.cancel();
        exitToast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT);
        exitToast.show();
    }
    private void resetExitBack() {
        lastRootBackAt = -1;
        if (exitToast != null) { exitToast.cancel(); exitToast = null; }
    }
    @Override protected void onResume() {
        super.onResume();
        ReminderBridge.onResume(this, web);
        if (cloud != null) cloud.resume();
        if (updates != null) updates.resume();
    }
    @Override protected void onPause() {
        ++backRequestGeneration;
        backCheckPending = false;
        queuedBackAt = -1;
        resetExitBack();
        if (cloud != null) cloud.pause();
        super.onPause();
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
        if (documentRequest != null) { documentRequest.clear(); documentRequest = null; }
        documents.close();
        pendingImport = pendingImportOwner = null;
        if (cloud != null) cloud.destroy();
        if (updates != null) updates.destroy();
        if (web != null) web.removeJavascriptInterface("AppUpdates");
        if (web != null) { web.removeJavascriptInterface("DriveBackup"); web.destroy(); }
        super.onDestroy();
    }
}
