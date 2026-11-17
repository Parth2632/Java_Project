package com.mathverse.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Clock helper (Python: models.utcnow). */
public final class Times {

    private Times() {
    }

    /**
     * The current time in UTC, without a time zone attached, to microseconds.
     * Stored like this because SQLite has no time-zone type; the Python backend
     * stores and sends times the same way.
     */
    public static LocalDateTime utcnow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * A time as the Python API writes it (datetime.isoformat): "2026-11-17T10:05:00"
     * or, when there are fractions of a second, "2026-11-17T10:05:00.123456".
     * Null stays null.
     */
    public static String iso(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        String text = time.withNano(0).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"));
        int micros = time.getNano() / 1000;
        return micros == 0 ? text : text + String.format(".%06d", micros);
    }
}
