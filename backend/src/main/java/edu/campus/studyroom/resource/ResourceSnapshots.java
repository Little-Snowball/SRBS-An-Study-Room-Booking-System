package edu.campus.studyroom.resource;

import edu.campus.studyroom.common.BusinessClock;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds after_snapshot JSON for resource_status_event. Plain manual JSON with
 * escaping; snapshots are server-generated and version-checked on read.
 */
public final class ResourceSnapshots {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ResourceSnapshots() {
    }

    public static String room(String status, LocalTime openTime, LocalTime closeTime,
                              int rows, int cols, String name) {
        return "{"
                + "\"status\":\"" + status + "\","
                + "\"openTime\":\"" + openTime.format(TIME) + "\","
                + "\"closeTime\":\"" + closeTime.format(TIME) + "\","
                + "\"rows\":" + rows + ","
                + "\"cols\":" + cols + ","
                + "\"name\":\"" + escape(name) + "\""
                + "}";
    }

    public static String seat(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    /** Business clock is injected only to keep this helper Spring-free and testable. */
    public static String serverNote(BusinessClock clock) {
        return clock.nowOffset().toString();
    }
}
