package edu.campus.studyroom.auth.dto;

/**
 * Authenticated user view embedded in the login response. IDs are strings in
 * JSON; the credit snapshot comes from credit_account (default 100 when absent)
 * and creditRestricted compares against the active rule's minCredit.
 */
public record UserProfile(String id, String account, String name, String college,
                          String role, int creditScore, boolean creditRestricted,
                          boolean blocked) {
}
