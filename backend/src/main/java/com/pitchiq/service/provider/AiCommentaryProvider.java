package com.pitchiq.service.provider;

import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.VenueIntelligenceDto;
import java.util.List;

/**
 * Interface defining the contract for AI Commentary generation.
 * This ensures the business logic remains decoupled from specific AI providers (Gemini, Mock, etc).
 */
public interface AiCommentaryProvider {
    
    /**
     * Generates a 5-part PitchIQ intelligence list based on match context.
     *
     * @param response The simulation response containing analytical data.
     * @param request The match state request with context like teams and match status.
     * @return List of 5 intelligence bullets.
     */
    List<String> generateCommentary(SimulationResponse response, MatchStateRequest request);

    /**
     * Generates structured venue intelligence based on the ground and format.
     *
     * @param request The match state request containing venue details.
     * @return VenueIntelligenceDto structured data.
     */
    VenueIntelligenceDto getVenueIntelligence(MatchStateRequest request);
}
