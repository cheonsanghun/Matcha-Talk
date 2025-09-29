package net.datasa.project01.service.support;

import net.datasa.project01.domain.entity.MatchRequest;

/**
 * Utility helpers for keeping {@link MatchRequest} entities within the database constraints
 * before persisting them.
 */
public final class MatchRequestSanitizer {

    private MatchRequestSanitizer() {
    }

    /**
     * Ensures the stored age range respects the {@code max_age >= min_age} check constraint
     * by swapping the values when they have been captured in reverse order.
     *
     * @param request match request entity to sanitize (ignored when {@code null})
     */
    public static void normalizeAgeRange(MatchRequest request) {
        if (request == null) {
            return;
        }

        Integer minAge = request.getMinAge();
        Integer maxAge = request.getMaxAge();
        if (minAge != null && maxAge != null && minAge > maxAge) {
            request.setMinAge(maxAge);
            request.setMaxAge(minAge);
        }
    }
}
