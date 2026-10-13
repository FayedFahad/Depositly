package app.depositly.mobile.cloud;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Scope;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** OAuth tokens stay in native memory; only backup data and display status cross the bridge. */
public final class DriveBackupBridge {
    private static final int AUTHORIZE = 2301;
    private static final List<Scope> SCOPES = Arrays.asList(new Scope("openid"), new Scope("https://www.googleapis.com/auth/userinfo.email"),
        new Scope("https://www.googleapis.com/auth/userinfo.profile"), new Scope("https://www.googleapis.com/auth/drive.appdata"));
    private final Activity activity;
    private final WebView web;
    private final AuthorizationClient client;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile String status = "{}";
    private boolean busy, resolving, pageReady, dead;
    private String operation = "", message = "", phase = "idle";
    private int generation;
    private long retryDelay = 30000;
    private final Runnable upload = () -> start("backup", false);

    public DriveBackupBridge(Activity activity, WebView web, Bundle saved) {
        this.activity = activity; this.web = web;
        prefs = activity.getSharedPreferences("google-drive-backup", 0);
        client = Identity.getAuthorizationClient(activity);
        if (saved != null && saved.getBoolean("cloudResolving")) {
            operation = saved.getString("cloudOperation", "connect"); busy = resolving = true; phase = "authorizing";
        }
        refresh();
    }
    private boolean signedIn() { return !prefs.getString("sub", "").isEmpty(); }
    private boolean ready() { return signedIn() && prefs.getString("sub", "").equals(prefs.getString("readySub", "")); }
    private boolean available() { return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == 0; }
    private void refresh() {
        try {
            status = new JSONObject().put("available", available()).put("signedIn", signedIn()).put("email", prefs.getString("email", ""))
                .put("busy", busy).put("ready", ready()).put("phase", phase).put("message", message)
                .put("automatic", prefs.getBoolean("automatic", true)).put("dirty", prefs.getBoolean("dirty", false))
                .put("lastBackup", prefs.getLong("lastBackup", 0)).toString();
            emit("onCloudStatus", status);
        } catch (Exception ignored) { }
    }
    private void emit(String function, String value) {
        if (!pageReady || dead) return;
        web.evaluateJavascript("window." + function + "&&window." + function + "(" + JSONObject.quote(value) + ")", null);
    }
    @JavascriptInterface public String getStatus() { return status; }
    @JavascriptInterface public void signIn() { main.post(() -> start("connect", true)); }
    @JavascriptInterface public void restore() { main.post(() -> start(signedIn() ? "restore" : "connect", true)); }
    @JavascriptInterface public void reviewRestore() { main.post(() -> { String pending = prefs.getString("pendingRestore", ""); if (!pending.isEmpty()) emit("onCloudRestore", pending); else start("restore", true); }); }
    @JavascriptInterface public void backupNow(String payload) { main.post(() -> { if (storePayload(payload)) start("backup", true); }); }
    @JavascriptInterface public void queueBackup(String payload) {
        main.post(() -> {
            if (!signedIn() || !ready() || !prefs.getBoolean("automatic", true)) return;
            if (storePayload(payload)) schedule(1500);
        });
    }
    private boolean storePayload(String payload) {
        if (!ready()) { message = "Review your Google Drive backup before saving this phone's data."; refresh(); return false; }
        try {
            DriveRepository.validate(payload);
            if (payload.equals(prefs.getString("payload", ""))) return true;
            if (!prefs.edit().putString("payload", payload).putBoolean("dirty", true).commit()) throw new Exception();
            message = "Changes saved on this phone. Google Drive backup is pending."; refresh(); return true;
        } catch (Exception e) { message = "Could not prepare a Drive backup. Export a file backup and try again."; refresh(); return false; }
    }
    @JavascriptInterface public void setAutomatic(boolean enabled) {
        main.post(() -> {
            prefs.edit().putBoolean("automatic", enabled).apply();
            main.removeCallbacks(upload); refresh();
            if (enabled) emit("onCloudReady", "");
        });
    }
    @JavascriptInterface public void acceptRestore() {
        main.post(() -> {
            if (!signedIn() || busy) return;
            if (!prefs.edit().putString("readySub", prefs.getString("sub", "")).remove("pendingRestore")
                .remove("payload").putBoolean("dirty", false).commit()) { fail("Could not save the backup connection. Try again."); return; }
            phase = "idle"; message = "Connected to Google Drive."; refresh(); emit("onCloudReady", "");
        });
    }
    @JavascriptInterface public void signOut() {
        main.post(() -> {
            if (busy) return;
            generation++; main.removeCallbacks(upload);
            prefs.edit().clear().commit(); phase = "idle"; message = "Signed out. Your phone's data and Drive backups are kept."; refresh();
        });
    }
    @JavascriptInterface public void disconnect() {
        main.post(() -> {
            if (busy || !signedIn()) return;
            busy = true; phase = "authorizing"; message = "Removing Google access…"; refresh();
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(new Account(prefs.getString("email", ""), "com.google")).setScopes(SCOPES).build())
                .addOnSuccessListener(activity, unused -> {
                    busy = false; generation++; main.removeCallbacks(upload); prefs.edit().clear().commit();
                    phase = "idle"; message = "Google access removed. Your phone's records are kept."; refresh();
                }).addOnFailureListener(activity, e -> fail("Could not remove Google access. Check your connection and try again."));
        });
    }
    private void schedule(long delay) { main.removeCallbacks(upload); if (!dead) main.postDelayed(upload, delay); }
    private void start(String action, boolean interactive) {
        if (dead || busy) return;
        if (!available()) { fail("Update Google Play services to use Google Drive backup."); return; }
        if (action.equals("backup") && (!ready() || prefs.getString("payload", "").isEmpty())) return;
        if (!interactive && (!prefs.getBoolean("automatic", true) || !prefs.getBoolean("dirty", false))) return;
        busy = true; operation = action; phase = "authorizing"; message = action.equals("backup") ? "Saving to Google Drive…" : "Checking your Google Drive backup…";
        refresh();
        final int run = generation;
        AuthorizationRequest.Builder request = AuthorizationRequest.builder().setRequestedScopes(SCOPES);
        if (action.equals("connect")) request.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else request.setAccount(new Account(prefs.getString("email", ""), "com.google"));
        client.authorize(request.build()).addOnSuccessListener(activity, result -> {
            if (dead || run != generation) return;
            if (result.hasResolution()) {
                if (!interactive) { fail("Sign in again to allow Google Drive backup. Changes are safe on this phone."); return; }
                try { resolving = true; activity.startIntentSenderForResult(result.getPendingIntent().getIntentSender(), AUTHORIZE, null, 0, 0, 0); }
                catch (Exception e) { resolving = false; fail("Could not open Google sign-in. Please try again."); }
            } else authorized(result);
        }).addOnFailureListener(activity, e -> {
            if (dead || run != generation) return;
            fail("Google sign-in failed. Check your connection, OAuth test-user access and the app's signing certificate, then try again.");
            if (!interactive) retry();
        });
    }
    public boolean activityResult(int request, int result, Intent data) {
        if (request != AUTHORIZE) return false;
        resolving = false;
        if (result != Activity.RESULT_OK || data == null) { fail("Google sign-in was canceled. Your phone's data is unchanged."); return true; }
        try { authorized(client.getAuthorizationResultFromIntent(data)); }
        catch (Exception e) { fail("Google sign-in did not complete. Please try again."); }
        return true;
    }
    private void authorized(AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || !result.getGrantedScopes().contains("https://www.googleapis.com/auth/drive.appdata")) { fail("Allow Google Drive backup access to continue."); return; }
        final String action = operation, expectedSub = action.equals("connect") ? "" : prefs.getString("sub", "");
        final String payload = prefs.getString("payload", "");
        final int run = generation;
        phase = action.equals("backup") ? "uploading" : "restoring"; refresh();
        worker.execute(() -> {
            try {
                DriveRepository drive = new DriveRepository(DriveRepository.authenticated(token));
                JSONObject user = drive.user();
                String sub = user.getString("sub"), email = user.getString("email");
                if (!user.optBoolean("email_verified") || sub.isEmpty() || email.isEmpty() || (!expectedSub.isEmpty() && !expectedSub.equals(sub)))
                    throw new Exception("Google returned a different account. Sign in with the linked account before backing up.");
                if (action.equals("backup")) {
                    if (payload.isEmpty()) throw new Exception("No backup data is ready.");
                    drive.save(payload);
                    main.post(() -> {
                        if (dead || run != generation) return;
                        boolean changed = !payload.equals(prefs.getString("payload", ""));
                        prefs.edit().putBoolean("dirty", changed).putLong("lastBackup", System.currentTimeMillis()).apply();
                        busy = false; retryDelay = 30000; phase = "idle"; message = "Backup saved to Google Drive."; refresh();
                        if (changed && prefs.getBoolean("automatic", true)) schedule(1500);
                    });
                } else {
                    String backup = drive.latest();
                    JSONObject envelope = new JSONObject().put("email", email).put("backup", backup == null ? JSONObject.NULL : new JSONObject(backup));
                    main.post(() -> {
                        if (dead || run != generation) return;
                        if (!prefs.edit().putString("sub", sub).putString("email", email).remove("readySub").remove("payload")
                            .putBoolean("dirty", false).putString("pendingRestore", envelope.toString()).commit()) {
                            fail("Could not save the Google account connection. Free some storage and try again."); return;
                        }
                        busy = false; phase = "review"; message = "Review your backup to finish connecting."; refresh(); emit("onCloudRestore", envelope.toString());
                    });
                }
            } catch (Exception e) {
                main.post(() -> {
                    if (dead || run != generation) return;
                    if (e instanceof DriveRepository.HttpFailure && ((DriveRepository.HttpFailure)e).code == 401)
                        client.clearToken(ClearTokenRequest.builder().setToken(token).build());
                    String text = e instanceof DriveRepository.HttpFailure ? e.getMessage() : e instanceof java.io.IOException ?
                        "Google Drive could not be reached or its backup could not be read. Your data is safe on this phone. Try again." :
                        "Could not complete Google Drive backup. Your phone's data and previous backups are safe. Try again.";
                    fail(text);
                    if (action.equals("backup")) retry();
                });
            }
        });
    }
    private void retry() { if (ready() && prefs.getBoolean("automatic", true)) { schedule(retryDelay); retryDelay = Math.min(retryDelay * 2, 300000); } }
    private void fail(String text) { busy = resolving = false; phase = "error"; message = text; refresh(); }
    public void pageReady() {
        pageReady = true; refresh();
        String pending = prefs.getString("pendingRestore", "");
        if (!pending.isEmpty()) emit("onCloudRestore", pending);
        else if (ready()) emit("onCloudReady", "");
    }
    public void resume() { if (!busy && ready() && prefs.getBoolean("dirty", false)) schedule(1500); }
    public void saveInstanceState(Bundle state) { state.putBoolean("cloudResolving", resolving); state.putString("cloudOperation", operation); }
    public void destroy() { dead = true; generation++; main.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
