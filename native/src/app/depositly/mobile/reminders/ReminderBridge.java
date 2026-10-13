package app.depositly.mobile.reminders;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.time.LocalTime;

public final class ReminderBridge {
    public static final int PERMISSION_REQUEST = 4103;
    private final Activity activity;
    private final WebView web;
    public ReminderBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        ReminderEngine.channel(activity);
    }
    @JavascriptInterface public String getStatus() { return ReminderEngine.status(activity); }
    @JavascriptInterface public boolean setPlan(String data) {
        try { ReminderEngine.setPlan(activity, data); return true; }
        catch (Exception e) { ReminderEngine.enabled(activity, false); return false; }
    }
    @JavascriptInterface public void setEnabled(final boolean on) {
        activity.runOnUiThread(() -> {
            ReminderEngine.enabled(activity, on);
            if (on && Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
            else if (on && !ReminderEngine.allowed(activity)) openSystemSettings();
            dispatchStatus(activity, web);
        });
    }
    @JavascriptInterface public boolean setTime(String time) {
        try {
            LocalTime parsed = LocalTime.parse(time);
            if (time.length() != 5 || parsed.getSecond() != 0) return false;
            if (!ReminderEngine.prefs(activity).edit().putString("time", time).commit()) return false;
            ReminderEngine.schedule(activity);
            return true;
        } catch (Exception e) { return false; }
    }
    @JavascriptInterface public void testNotification() {
        activity.runOnUiThread(() -> {
            if (ReminderEngine.allowed(activity)) ReminderEngine.test(activity);
            else Toast.makeText(activity, "Allow notifications in Android settings first.", Toast.LENGTH_SHORT).show();
        });
    }
    @JavascriptInterface public void openSystemSettings() {
        activity.runOnUiThread(() -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            activity.startActivity(i);
        });
    }
    public static void dispatchStatus(Activity activity, WebView web) {
        if (web == null) return;
        String value = JSONObject.quote(ReminderEngine.status(activity));
        activity.runOnUiThread(() -> web.evaluateJavascript("window.onReminderStatus&&window.onReminderStatus(" + value + ")", null));
    }
    public static void permissionResult(Activity activity, WebView web) {
        if (!ReminderEngine.allowed(activity)) ReminderEngine.enabled(activity, false);
        else ReminderEngine.schedule(activity);
        dispatchStatus(activity, web);
    }
    public static void onResume(Activity activity, WebView web) {
        ReminderEngine.schedule(activity);
        dispatchStatus(activity, web);
    }
    public static void pageReady(Activity activity, WebView web) {
        dispatchStatus(activity, web);
        Intent intent = activity.getIntent();
        String day = intent.getStringExtra(ReminderEngine.OPEN_DATE);
        if (day == null || !day.matches("\\d{4}-\\d{2}-\\d{2}")) return;
        intent.removeExtra(ReminderEngine.OPEN_DATE);
        web.evaluateJavascript("window.openReminderDate&&window.openReminderDate(" + JSONObject.quote(day) + ")", null);
    }
}
