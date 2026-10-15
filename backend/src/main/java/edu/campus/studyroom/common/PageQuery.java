package edu.campus.studyroom.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Common page query parameters: page from 1, size default 20 max 100.
 * Sorting uses whitelists only; request strings never reach ORDER BY.
 */
public record PageQuery(
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer size,
        @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$") String sort
) {
    public PageQuery {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
    }

    public int offset() {
        return (page - 1) * size;
    }
}
