package edu.campus.studyroom.seed;

/**
 * CLI seed result counters: created rows, idempotently skipped entities and a
 * human-readable detail line for the run log.
 */
public record SeedReport(int created, int skipped, String detail) {
}
