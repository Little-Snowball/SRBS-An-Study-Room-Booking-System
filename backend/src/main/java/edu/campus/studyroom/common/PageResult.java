package edu.campus.studyroom.common;

import java.util.List;

/**
 * Page result. page starts at 1, size defaults to 20 and is capped at 100.
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }
}
