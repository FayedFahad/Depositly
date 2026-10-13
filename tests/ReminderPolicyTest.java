import app.depositly.mobile.reminders.ReminderPolicy;
import java.time.*;
import java.util.*;

public class ReminderPolicyTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        ZonedDateTime morning = ZonedDateTime.of(2026, 10, 13, 8, 0, 0, 0, ZoneId.of("Asia/Dhaka"));
        List<String> days = Arrays.asList("2026-10-14", "2026-10-12", "2026-10-13", "bad");
        ReminderPolicy.Next next = ReminderPolicy.next(days, "", "09:00", morning);
        check(next.date.equals("2026-10-13"), "Earliest unpaid current date wins, regardless of input order");
        check(next.time == morning.withHour(9).toInstant().toEpochMilli(), "Time is local, not UTC");
        ZonedDateTime afternoon = morning.withHour(15);
        next = ReminderPolicy.next(days, "", "09:00", afternoon);
        check(next.time == afternoon.toInstant().toEpochMilli() + 1000, "Enabling after today's time catches up once");
        next = ReminderPolicy.next(days, "2026-10-13", "09:00", afternoon);
        check(next.date.equals("2026-10-14"), "Delivered date is not repeated");
        check(ReminderPolicy.next(Arrays.asList("2026-10-12"), "", "09:00", morning) == null, "Old overdue dates do not trigger retrospective alerts");
        check(ReminderPolicy.next(Collections.emptyList(), "", "09:00", morning) == null, "Empty plan leaves no alarm");
        next = ReminderPolicy.next(days, "", "99:99", morning);
        check(next.time == morning.withHour(9).toInstant().toEpochMilli(), "Invalid stored clock safely defaults to 09:00");
        ZonedDateTime beforeDst = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneId.of("America/New_York"));
        next = ReminderPolicy.next(Arrays.asList("2026-11-01"), "", "09:00", beforeDst);
        check(next.time == Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(), "Time-zone/DST changes preserve the selected local hour");
        ZonedDateTime yearEnd = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, ZoneId.of("Asia/Dhaka"));
        check(ReminderPolicy.next(Arrays.asList("2027-01-01"), "", "09:00", yearEnd).date.equals("2027-01-01"), "Year boundary schedules correctly");
        check(ReminderPolicy.next(days, "2026-10-14", "09:00", morning) == null, "A delayed older alarm cannot reset delivery and repeat a date");
        System.out.println("PASS: local-hour scheduling, one-day catch-up, deduplication, empty/past plans, invalid clocks, DST and year boundaries.");
    }
}
