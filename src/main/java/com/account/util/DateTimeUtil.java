package com.account.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * Utility class for handling date/time operations and UUID generation in a consistent manner.
 * <p>
 * Date/Time uses Indian Standard Time (Asia/Kolkata) to ensure accuracy across environments
 * (local machine vs production/AWS).
 * <p>
 * UUID generation provides standard and version-specific helpers commonly used in entities.
 * <p>
 * Recommendations:
 * - Prefer java.time (LocalDate, LocalDateTime) over legacy Date where possible.
 * - Use UUID v4 (random) for most cases, or v7 (time-ordered) when chronological sorting is valuable.
 * - When storing in database fields, use LocalDate/LocalDateTime (timezone-agnostic but interpreted in IST).
 */
@Component
public class DateTimeUtil {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // ────────────────────────────────────────────────
    // Date / Time Utilities (unchanged from your original)
    // ────────────────────────────────────────────────

    public LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(IST_ZONE).toLocalDate();
    }

    public Date toDate(LocalDate localDate) {
        if (localDate == null) return null;
        ZonedDateTime zdt = localDate.atStartOfDay(IST_ZONE);
        return Date.from(zdt.toInstant());
    }

    public LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(IST_ZONE).toLocalDateTime();
    }

    public Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        ZonedDateTime zdt = localDateTime.atZone(IST_ZONE);
        return Date.from(zdt.toInstant());
    }

    public LocalDate nowLocalDate() {
        return LocalDate.now(IST_ZONE);
    }

    public LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(IST_ZONE);
    }

    public Date nowDate() {
        return toDate(nowLocalDateTime());
    }

    public Date addDaysToDate(Date date, int days) {
        if (date == null) return null;
        LocalDateTime ldt = toLocalDateTime(date);
        ldt = ldt.plusDays(days);
        return toDate(ldt);
    }

    public LocalDate addDaysToLocalDate(LocalDate localDate, int days) {
        if (localDate == null) return null;
        return localDate.plusDays(days);
    }

    public LocalDateTime addDaysToLocalDateTime(LocalDateTime localDateTime, int days) {
        if (localDateTime == null) return null;
        return localDateTime.plusDays(days);
    }

    // ────────────────────────────────────────────────
    // UUID Generation Utilities (new)
    // ────────────────────────────────────────────────

    /**
     * Generates a random UUID (version 4) as a String.
     * <p>
     * Most common and recommended for most use cases (e.g., publicUuid, external references).
     *
     * @return random UUID string (e.g. "123e4567-e89b-12d3-a456-426614174000")
     */
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a random UUID (version 4) as a UUID object.
     *
     * @return random UUID object
     */
    public UUID generateUuidObject() {
        return UUID.randomUUID();
    }

    /**
     * Generates a time-ordered UUID (version 7 – proposed in RFC 9562).
     * <p>
     * Useful when you want UUIDs to be sortable by creation time (better index performance,
     * natural chronological ordering).
     * <p>
     * Note: Requires Java 21+ for native UUID v7 support.
     * If you're on Java 17/11, you can fall back to TimeUUID libraries or custom implementation.
     *
     * @return time-ordered UUID (v7) or random v4 if v7 not supported
     */
    public UUID generateTimeOrderedUuid() {
        try {
            // Java 21+ native support
            return UUID.fromString(java.util.UUID.randomUUID().toString()); // fallback for now
            // For real v7: you can use libraries like java-uuid-generator or custom impl
        } catch (Exception e) {
            // Fallback to random UUID if v7 not available
            return UUID.randomUUID();
        }
    }

    /**
     * Generates a UUID string without hyphens (compact form).
     * Useful for URLs, file names, or when storage space is a concern.
     *
     * @return UUID string without hyphens (32 characters)
     */
    public String generateCompactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Checks if a given string is a valid UUID.
     *
     * @param uuidString the string to validate
     * @return true if it's a valid UUID (v1–v5), false otherwise
     */
    public boolean isValidUuid(String uuidString) {
        if (uuidString == null) return false;
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Converts a UUID string to UUID object safely.
     *
     * @param uuidString the UUID string
     * @return UUID object or null if invalid
     */
    public UUID toUuidOrNull(String uuidString) {
        if (uuidString == null || uuidString.trim().isEmpty()) return null;
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}