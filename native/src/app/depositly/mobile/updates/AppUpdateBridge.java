package app.depositly.mobile.updates;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** User-initiated, signed self-updates. Native networking never exposes the WebView to remote pages. */
public final class AppUpdateBridge {
    private static final String ENDPOINT = "https://depositly.vercel.app/api/app-update";
    private static final int SOURCES = 0x1011, INSTALL = 0x1012;
    private final Activity activity;
    private final WebView web;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final long installedCode;
    private final String installedName;
    private final File apk, part;
    private UpdateManifest release;
    private String phase = "idle", message = "Check for the latest Depositly update.";
    private long downloaded, generation;
    private boolean busy, verified, destroyed, awaitingPermission, pageReady;
    private volatile HttpURLConnection connection;

    public AppUpdateBridge(Activity activity, WebView web) {
        this.activity = activity; this.web = web;
        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            installedCode = code(installed); installedName = installed.versionName;
        } catch (Exception e) { throw new IllegalStateException("Could not read the installed version", e); }
        apk = UpdateApkProvider.apk(activity);
        // A destroyed activity's worker may still be closing its stream. Never let its
        // cleanup delete a newly created activity's in-progress download.
        part = new File(apk.getParentFile(), "update-" + UUID.randomUUID() + ".part");
        if (apk.isFile()) {
            busy = true; phase = "verifying"; message = "Checking the saved update…";
            final long ticket = ++generation;
            worker.execute(() -> restore(ticket));
        }
    }

    private static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private synchronized boolean active(long ticket) { return !destroyed && ticket == generation; }
    @JavascriptInterface public synchronized String getStatus() {
        JSONObject status = new JSONObject();
        try {
            status.put("available", true).put("phase", phase).put("message", message)
                .put("installedVersionName", installedName).put("installedVersionCode", installedCode)
                .put("versionName", release == null ? "" : release.versionName).put("versionCode", release == null ? 0 : release.versionCode)
                .put("releaseNotes", release == null ? "" : release.releaseNotes).put("downloadedBytes", downloaded)
                .put("totalBytes", release == null ? 0 : release.apkSize).put("apkSize", release == null ? 0 : release.apkSize).put("canInstall", verified && !busy);
        } catch (Exception ignored) {}
        return status.toString();
    }
    private void emit() {
        final String status = getStatus();
        activity.runOnUiThread(() -> {
            synchronized (this) { if (destroyed || !pageReady || activity.isFinishing() || activity.isDestroyed()) return; }
            web.evaluateJavascript("if(typeof window.onAppUpdateStatus==='function')window.onAppUpdateStatus(" + JSONObject.quote(status) + ")", null);
        });
    }
    private boolean state(long ticket, String next, String text, boolean working) {
        synchronized (this) {
            if (!active(ticket)) return false;
            phase = next; message = text; busy = working;
        }
        emit(); return true;
    }
    public void pageReady() { synchronized (this) { pageReady = true; } emit(); }
    public void resume() { emit(); }

    @JavascriptInterface public void check() {
        final long ticket;
        synchronized (this) { if (destroyed || busy || awaitingPermission || "installing".equals(phase)) return; ticket = ++generation; busy = true; phase = "checking"; message = "Checking for updates…"; }
        emit();
        worker.execute(() -> {
            try {
                UpdateManifest latest = new UpdateManifest(readManifest(ticket));
                synchronized (this) {
                    if (!active(ticket)) return;
                    if (release == null || !release.apkSha256.equals(latest.apkSha256)) verified = false;
                    release = latest; downloaded = verified ? latest.apkSize : 0;
                }
                if (!latest.newerThan(installedCode)) {
                    synchronized (this) { verified = false; }
                    clearSaved();
                    state(ticket, "current", "You have the latest Depositly version.", false);
                } else state(ticket, verified ? "ready" : "available", verified ? "Update downloaded. Ready to install." : "A new Depositly update is available.", false);
            } catch (Exception e) { failure(ticket, "Could not check for updates. Check your internet connection and try again."); }
        });
    }

    @JavascriptInterface public void download() {
        final long ticket; final UpdateManifest selected;
        synchronized (this) {
            if (destroyed || busy || awaitingPermission || "installing".equals(phase) || release == null || !release.newerThan(installedCode)) return;
            ticket = ++generation; selected = release; busy = true; verified = false; downloaded = 0; phase = "downloading"; message = "Downloading update…";
        }
        emit();
        worker.execute(() -> {
            try {
                if (!apk.getParentFile().isDirectory() && !apk.getParentFile().mkdirs()) throw new IOException("No update directory");
                if (apk.getParentFile().getUsableSpace() < selected.apkSize * 2 + 8 * 1024 * 1024) throw new IOException("Not enough storage");
                HttpURLConnection remote = open(selected.apkUrl, ticket, true);
                long length = remote.getContentLengthLong();
                if (length >= 0 && length != selected.apkSize) throw new IOException("Update download size does not match");
                final long[] lastProgress = {0};
                String digest;
                try (InputStream input = remote.getInputStream(); FileOutputStream output = new FileOutputStream(part)) {
                    digest = UpdateManifest.digest(input, output, selected.apkSize, () -> !active(ticket), value -> {
                        long now = SystemClock.elapsedRealtime();
                        synchronized (this) { if (!active(ticket)) return; downloaded = value; }
                        if (now - lastProgress[0] >= 250 || value == selected.apkSize) { lastProgress[0] = now; emit(); }
                    });
                    output.getFD().sync();
                } finally { remote.disconnect(); connection = null; }
                if (!selected.apkSha256.equals(digest)) throw new IOException("Update checksum does not match");
                if (!state(ticket, "verifying", "Verifying the downloaded update…", true)) return;
                verifyPackage(part, selected);
                synchronized (this) {
                    if (!active(ticket)) return;
                    if (apk.exists() && !apk.delete()) throw new IOException("Could not replace the saved update");
                    if (!part.renameTo(apk)) throw new IOException("Could not save the update");
                    if (!activity.getSharedPreferences("app-updates", 0).edit().putString("manifest", selected.toJson()).commit()) throw new IOException("Could not save update metadata");
                    verified = true;
                }
                state(ticket, "ready", "Update downloaded. Ready to install.", false);
            } catch (Exception e) {
                failure(ticket, e.getMessage() != null && e.getMessage().contains("storage") ? "Not enough storage. Free some space and try downloading again."
                    : "The update could not be downloaded or verified. Check your connection and try again.");
            } finally { part.delete(); HttpURLConnection remote = connection; if (remote != null) remote.disconnect(); connection = null; }
        });
    }

    @JavascriptInterface public void install() {
        final long ticket; final UpdateManifest selected;
        synchronized (this) {
            if (destroyed || busy || awaitingPermission || "installing".equals(phase) || !verified || release == null) return;
            ticket = ++generation; selected = release; busy = true; phase = "verifying"; message = "Verifying the update before installation…";
        }
        emit();
        worker.execute(() -> {
            try {
                verifyFile(apk, selected, ticket);
                activity.runOnUiThread(() -> promptInstaller(ticket));
            } catch (Exception e) {
                synchronized (this) { if (active(ticket)) verified = false; }
                failure(ticket, "The saved update could not be verified. Download it again.");
            }
        });
    }

    private void promptInstaller(long ticket) {
        if (!active(ticket) || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                synchronized (this) { awaitingPermission = true; }
                state(ticket, "permission", "Allow Depositly to install updates, then return to continue.", false);
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())), SOURCES);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(UpdateApkProvider.URI, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true);
            intent.setClipData(ClipData.newRawUri("Depositly update", UpdateApkProvider.URI));
            state(ticket, "installing", "Confirm the update in Android’s installer. Your saved data stays on this device.", false);
            activity.startActivityForResult(intent, INSTALL);
        } catch (RuntimeException e) {
            synchronized (this) { awaitingPermission = false; }
            state(ticket, "ready", "Could not open Android’s installer or install settings. Try Install update again.", false);
        }
    }

    public boolean activityResult(int request, int result) {
        if (request != SOURCES && request != INSTALL) return false;
        synchronized (this) { awaitingPermission = false; }
        if (request == SOURCES && activity.getPackageManager().canRequestPackageInstalls()) install();
        else {
            final long ticket; synchronized (this) { ticket = generation; }
            state(ticket, "ready", request == SOURCES ? "Installation permission was not granted. Tap Install update to try again."
                : "Installation was not completed. Tap Install update to try again.", false);
        }
        return true;
    }

    @JavascriptInterface public void cancel() {
        synchronized (this) {
            if (destroyed || awaitingPermission || "installing".equals(phase)) return;
            ++generation; busy = false; downloaded = verified && release != null ? release.apkSize : 0;
            phase = verified ? "ready" : release != null && release.newerThan(installedCode) ? "available" : "idle";
            message = "Update canceled. You can try again when ready.";
        }
        HttpURLConnection remote = connection; if (remote != null) remote.disconnect();
        emit();
    }

    private void restore(long ticket) {
        try {
            String saved = activity.getSharedPreferences("app-updates", 0).getString("manifest", "");
            UpdateManifest selected = new UpdateManifest(saved);
            if (!selected.newerThan(installedCode)) { clearSaved(); state(ticket, "idle", "Check for the latest Depositly update.", false); return; }
            verifyFile(apk, selected, ticket);
            synchronized (this) { if (!active(ticket)) return; release = selected; verified = true; downloaded = selected.apkSize; }
            state(ticket, "ready", "Update downloaded. Ready to install.", false);
        } catch (Exception e) {
            if (active(ticket)) { clearSaved(); state(ticket, "idle", "Check for the latest Depositly update.", false); }
        }
    }
    private void clearSaved() { apk.delete(); activity.getSharedPreferences("app-updates", 0).edit().remove("manifest").apply(); }
    private void verifyFile(File file, UpdateManifest selected, long ticket) throws Exception {
        if (!selected.newerThan(installedCode)) throw new IOException("Update is not newer than this app");
        try (InputStream input = new FileInputStream(file)) {
            if (!selected.apkSha256.equals(UpdateManifest.digest(input, null, selected.apkSize, () -> !active(ticket), null))) throw new IOException("Update checksum does not match");
        }
        if (!active(ticket)) throw new InterruptedIOException();
        verifyPackage(file, selected);
    }
    private void verifyPackage(File file, UpdateManifest selected) throws Exception {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo update = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), flags);
        if (update == null || !UpdateManifest.PACKAGE.equals(update.packageName) || code(update) != selected.versionCode
            || !selected.versionName.equals(update.versionName) || !selected.newerThan(installedCode)) throw new IOException("APK version does not match");
        requireSigner(update); requireSigner(installed);
    }
    private static void requireSigner(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length != 1 || !UpdateManifest.CERTIFICATE.equals(UpdateManifest.hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())))) throw new IOException("APK signer does not match Depositly");
    }
    private String readManifest(long ticket) throws Exception {
        HttpURLConnection remote = open(ENDPOINT, ticket, false);
        try (InputStream input = remote.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                if (!active(ticket)) throw new InterruptedIOException();
                if (output.size() + count > 131072) throw new IOException("Update information is too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally { remote.disconnect(); connection = null; }
    }
    private HttpURLConnection open(String address, long ticket, boolean download) throws Exception {
        URL target = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!active(ticket)) throw new InterruptedIOException();
            String host = target.getHost();
            boolean allowed = download ? "github.com".equals(host) || "release-assets.githubusercontent.com".equals(host) || "objects.githubusercontent.com".equals(host)
                : "depositly.vercel.app".equals(host);
            if (!"https".equals(target.getProtocol()) || !allowed || target.getUserInfo() != null || target.getPort() != -1 && target.getPort() != 443) throw new IOException("Untrusted update server");
            HttpURLConnection remote = (HttpURLConnection) target.openConnection();
            connection = remote;
            remote.setConnectTimeout(15000); remote.setReadTimeout(30000); remote.setInstanceFollowRedirects(false);
            remote.setUseCaches(false); remote.setRequestProperty("User-Agent", "Depositly-Android/" + installedName);
            remote.setRequestProperty("Accept", download ? "application/octet-stream" : "application/json");
            remote.setRequestProperty("Accept-Encoding", "identity");
            int result;
            try { result = remote.getResponseCode(); } catch (Exception e) { remote.disconnect(); throw e; }
            if (result == 200) return remote;
            String location = remote.getHeaderField("Location"); remote.disconnect();
            if ((result == 301 || result == 302 || result == 303 || result == 307 || result == 308) && location != null) target = new URL(target, location);
            else throw new IOException("Update server is unavailable");
        }
        throw new IOException("Too many update redirects");
    }
    private void failure(long ticket, String text) { state(ticket, "error", text, false); }
    public void destroy() {
        synchronized (this) { destroyed = true; ++generation; }
        HttpURLConnection remote = connection; if (remote != null) remote.disconnect();
        worker.shutdownNow();
    }
}
