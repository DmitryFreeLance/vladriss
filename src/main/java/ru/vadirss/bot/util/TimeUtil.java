package ru.vadirss.bot.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private TimeUtil() {}

    public static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static String nowIso(ZoneId zone) {
        return LocalDateTime.now(zone).format(DATETIME);
    }

    public static String todayIso(ZoneId zone) {
        return LocalDate.now(zone).format(DATE);
    }

    public static LocalDate parseDate(String iso) {
        return LocalDate.parse(iso, DATE);
    }

    public static LocalDateTime parseDateTime(String iso) {
        return LocalDateTime.parse(iso, DATETIME);
    }

    public static String fmt(LocalDateTime dt) {
        return dt.format(DATETIME);
    }

    public static String fmt(LocalDate d) {
        return d.format(DATE);
    }

    public static LocalTime parseTime(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    public static String fmt(LocalTime t) {
        return t.toString();
    }
}