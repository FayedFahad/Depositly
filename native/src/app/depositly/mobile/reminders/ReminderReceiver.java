package app.depositly.mobile.reminders;

import android.content.*;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (ReminderEngine.FIRE.equals(intent.getAction())) ReminderEngine.fire(context, intent.getStringExtra("date"));
                else ReminderEngine.schedule(context); // Boot, update, clock or time-zone change.
            } finally { pending.finish(); }
        }, "Depositly-reminder").start();
    }
}
