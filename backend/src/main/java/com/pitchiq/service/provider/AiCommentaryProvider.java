package com.pitchiq.service.provider;

import com.pitchiq.dto.CombinedIntelligenceResult;
import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.SimulationResponse;

/**
 * Single-method contract for AI intelligence.
 * Implementations make ONE external call and return both venue intelligence
 * (eligible for caching) and match insights (always fresh) together.
 */
public interface AiCommentaryProvider {

    /**
     * Generates venue intelligence (from cache or Gemini) AND match insights
     * (always fresh) in a single operation.
     *
     * @param request  Full match state context.
     * @param simState Simulation output (win probability, RRR, projected score).
     * @return Combined result containing venueIntelligence and insights list.
     */
    CombinedIntelligenceResult generateIntelligence(MatchStateRequest request, SimulationResponse simState);
}
