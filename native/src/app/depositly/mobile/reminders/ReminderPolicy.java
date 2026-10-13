package app.depositly.mobile.reminders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.TreeSet;

/** Shared scheduling policy; dates refer to the phone's local calendar. */
public final class ReminderPolicy {
    public static final class Next {
        public final String date;
        public final long time;
        Next(String date, long time) { this.date = date; this.time = time; }
    }
    public static Next next(Collection<String> dates, String delivered, String clock, ZonedDateTime now) {
        LocalTime at;
        try { at = LocalTime.parse(clock); } catch (Exception e) { at = LocalTime.of(9, 0); }
        for (String value : new TreeSet<>(dates)) {
            LocalDate day;
            try { day = LocalDate.parse(value); } catch (Exception e) { continue; }
            if (day.isBefore(now.toLocalDate()) || value.compareTo(delivered) <= 0) continue;
            long trigger = day.atTime(at).atZone(now.getZone()).toInstant().toEpochMilli();
            // Enabling after today's selected time still gives one reminder today.
            return new Next(value, Math.max(trigger, now.toInstant().toEpochMilli() + 1000));
        }
        return null;
    }
}
