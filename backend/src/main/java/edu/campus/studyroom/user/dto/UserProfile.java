package edu.campus.studyroom.user.dto;

import edu.campus.studyroom.user.domain.UserRole;

/**
 * Personal-center profile. creditRestricted is derived from the active rule's
 * minCredit (never hard-coded) and tells the student UI to prompt a top-up
 * warning; blocked reflects the manual black-list flag only.
 */
public record UserProfile(
        String id,
        String account,
        String name,
        String college,
        UserRole role,
        int creditScore,
        boolean creditRestricted,
        boolean blocked
) {
}
