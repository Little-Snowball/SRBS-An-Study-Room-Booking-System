package edu.campus.studyroom.resource.dto;

/**
 * Result of a batch seat generation: the number of seats actually inserted.
 * The batch is strictly all-or-nothing, so a non-zero result always means the
 * full requested rectangle was created.
 */
public record BatchSeatResult(
        String roomId,
        int generated
) {
}
