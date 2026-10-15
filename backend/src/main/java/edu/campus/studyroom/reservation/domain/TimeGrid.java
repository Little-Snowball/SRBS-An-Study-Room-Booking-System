package edu.campus.studyroom.reservation.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Time grid helpers (manual chapter 3). Slots are 30 minutes; start/end must be
 * on-grid with zero seconds/millis. Ceiling never pushes an exact grid point forward.
 */
public final class TimeGrid {

    private TimeGrid() {
    }

    public static boolean isOnGrid(LocalDateTime value, int slotMinutes) {
        return value.getSecond() == 0 && value.getNano() == 0
                && value.getMinute() % slotMinutes == 0;
    }

    /** Ceils to the next grid point; an exact grid point stays unchanged. */
    public static LocalDateTime ceil(LocalDateTime value, int slotMinutes) {
        if (isOnGrid(value, slotMinutes)) {
            return value;
        }
        int minute = value.getMinute();
        int add = slotMinutes - (minute % slotMinutes);
        return value.plusMinutes(add).withSecond(0).withNano(0);
    }

    /**
     * Earliest legal start today: max(now, openTime) ceiled to the grid.
     * An exact grid point is not pushed into the next slot.
     */
    public static LocalDateTime earliestStart(LocalDateTime now, LocalTime openTime, int slotMinutes) {
        LocalDateTime floor = LocalDateTime.of(now.toLocalDate(), openTime);
        LocalDateTime base = now.isAfter(floor) ? now : floor;
        return ceil(base, slotMinutes);
    }

    /** Last legal end today: the room close time. */
    public static LocalDateTime latestEnd(LocalDate date, LocalTime closeTime) {
        return LocalDateTime.of(date, closeTime);
    }
}
