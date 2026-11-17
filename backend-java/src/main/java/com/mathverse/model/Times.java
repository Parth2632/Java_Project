package com.mathverse.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
}
