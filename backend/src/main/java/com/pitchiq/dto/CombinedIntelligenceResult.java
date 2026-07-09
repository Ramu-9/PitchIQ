package com.pitchiq.dto;

import java.util.List;

/**
 * Holds the result of a single combined Gemini call —
 * venue intelligence (cached in PostgreSQL) + match insights (fresh per match).
 */
public class CombinedIntelligenceResult {
    private final VenueIntelligenceDto venueIntelligence;
    private final List<String> insights;

    public CombinedIntelligenceResult(VenueIntelligenceDto venueIntelligence, List<String> insights) {
        this.venueIntelligence = venueIntelligence;
        this.insights = insights;
    }

    public VenueIntelligenceDto getVenueIntelligence() { return venueIntelligence; }
    public List<String> getInsights() { return insights; }
}
