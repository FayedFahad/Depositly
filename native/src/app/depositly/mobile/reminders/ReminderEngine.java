package app.depositly.mobile.reminders;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ReminderEngine {
    static final String CHANNEL = "depositly_payouts";
    static final String FIRE = "app.depositly.mobile.PAYOUT_REMINDER";
    static final String OPEN_DATE = "depositlyReminderDate";
    static final int NOTICE_ID = 4101, TEST_ID = 4102;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("depositly-reminders", Context.MODE_PRIVATE); }
    static NotificationManager manager(Context c) { return c.getSystemService(NotificationManager.class); }
    public static void channel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Payout reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Your scheduled interest payments and principal maturity dates.");
        manager(c).createNotificationChannel(channel);
    }
    public static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager m = manager(c);
        NotificationChannel ch = m.getNotificationChannel(CHANNEL);
        return m.areNotificationsEnabled() && (ch == null || ch.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }
    static SortedMap<String, JSONObject> plan(Context c) {
        SortedMap<String, JSONObject> result = new TreeMap<>();
        try {
            JSONArray rows = new JSONArray(prefs(c).getString("plan", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String d = row.getString("date");
                LocalDate.parse(d);
                result.put(d, row);
            }
        } catch (Exception ignored) { }
        return result;
    }
    static PendingIntent alarmIntent(Context c, String day) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(FIRE).putExtra("date", day);
        return PendingIntent.getBroadcast(c, 4100, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static synchronized void setPlan(Context c, String text) throws JSONException {
        if (text.length() > 8 * 1024 * 1024) throw new JSONException("Reminder plan is too large.");
        JSONArray rows = new JSONArray(text);
        if (rows.length() > 100000) throw new JSONException("Too many reminder dates.");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String d = row.getString("date");
            try { LocalDate.parse(d); } catch (Exception e) { throw new JSONException("Invalid reminder date."); }
            if (d.length() != 10 || row.getInt("count") < 1 || row.getString("total").length() > 100 || row.getString("details").length() > 3000) throw new JSONException("Invalid reminder details.");
        }
        if (!prefs(c).edit().putString("plan", text).commit()) throw new JSONException("Could not save reminder plan.");
        // A recorded receipt/deletion removes stale visible due-payment reminders.
        String shown = prefs(c).getString("shownDate", "");
        if (!shown.isEmpty() && !plan(c).containsKey(shown)) manager(c).cancel(NOTICE_ID);
        else if (!shown.isEmpty() && allowed(c)) {
            // Refresh a notification that is still visible after a partial receipt.
            // Do not recreate it if the user has already dismissed it.
            for (android.service.notification.StatusBarNotification active : manager(c).getActiveNotifications()) {
                if (active.getId() == NOTICE_ID) { post(c, plan(c).get(shown), shown); break; }
            }
        }
        schedule(c);
    }
    public static synchronized void schedule(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent alarm = alarmIntent(c, "");
        alarms.cancel(alarm);
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("enabled", false) || !allowed(c)) {
            p.edit().remove("nextDate").apply();
            return;
        }
        ReminderPolicy.Next next = ReminderPolicy.next(plan(c).keySet(), p.getString("delivered", ""), p.getString("time", "09:00"), ZonedDateTime.now());
        if (next == null) { p.edit().remove("nextDate").apply(); return; }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time, alarmIntent(c, next.date));
        p.edit().putString("nextDate", next.date).apply();
    }
    public static synchronized void enabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).commit();
        if (!value) manager(c).cancel(NOTICE_ID);
        schedule(c);
    }
    public static String status(Context c) {
        JSONObject result = new JSONObject();
        try {
            SharedPreferences p = prefs(c);
            result.put("available", true).put("enabled", p.getBoolean("enabled", false)).put("permissionGranted", allowed(c))
                .put("time", p.getString("time", "09:00")).put("nextDate", p.getString("nextDate", ""));
        } catch (JSONException ignored) { }
        return result.toString();
    }
    public static synchronized void restoreSettings(Context c,JSONObject value)throws JSONException {
        String time=value.getString("time");if(!time.matches("([01]\\d|2[0-3]):[0-5]\\d"))throw new JSONException("Invalid reminder time.");
        if(!prefs(c).edit().putBoolean("enabled",value.getBoolean("enabled")).putString("time",time).commit())throw new JSONException("Could not save reminders.");
        if(!value.getBoolean("enabled"))manager(c).cancel(NOTICE_ID);
        schedule(c);
    }
    static PendingIntent open(Context c, String day, int id) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) return null;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(OPEN_DATE, day);
        return PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static Notification.Builder builder(Context c, String day, int id) {
        int icon = c.getResources().getIdentifier("ic_payout_reminder", "drawable", c.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_dialog_info;
        return new Notification.Builder(c, CHANNEL).setSmallIcon(icon).setColor(0xff124c3b)
            .setContentIntent(open(c, day, id)).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE);
    }
    static void post(Context c, JSONObject row, String day) throws JSONException {
        int count = row.getInt("count");
        String label = LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM, uuuu", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
        String title = count + (count == 1 ? " payment due" : " payments due") + " · " + row.getString("total");
        String body = label + "\n" + row.getString("details") + "\nOpen Depositly to record what you receive.";
        Notification publicVersion = builder(c, day, NOTICE_ID).setContentTitle("Depositly payout reminder").setContentText("Open Depositly to view scheduled payments.").build();
        Notification n = builder(c, day, NOTICE_ID).setContentTitle(title).setContentText(label + " · Tap to view your calendar")
            .setStyle(new Notification.BigTextStyle().bigText(body)).setPublicVersion(publicVersion).build();
        manager(c).notify(NOTICE_ID, n);
    }
    public static synchronized void fire(Context c, String day) {
        try {
            SharedPreferences p = prefs(c);
            JSONObject row = plan(c).get(day);
            if (row != null && p.getBoolean("enabled", false) && allowed(c) && day.compareTo(p.getString("delivered", "")) > 0 && !LocalDate.parse(day).isAfter(LocalDate.now())) {
                post(c, row, day);
                p.edit().putString("delivered", day).putString("shownDate", day).commit();
            }
        } catch (Exception ignored) { } finally { schedule(c); }
    }
    public static void test(Context c) {
        if (!allowed(c)) return;
        String day = LocalDate.now().toString();
        manager(c).notify(TEST_ID, builder(c, day, TEST_ID).setContentTitle("Depositly reminders are ready")
            .setContentText("You will receive reminders for your unpaid Self payouts and maturity dates.").build());
    }
}
