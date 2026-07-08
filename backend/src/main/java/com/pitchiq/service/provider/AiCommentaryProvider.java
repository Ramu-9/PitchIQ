package com.pitchiq.service.provider;

import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.MatchDto;
import java.util.List;

/**
 * Interface defining the contract for AI Commentary generation.
 * This ensures the business logic remains decoupled from specific AI providers (Gemini, Mock, etc).
 */
public interface AiCommentaryProvider {
    
    /**
     * Generates a 5-part PitchIQ intelligence list.
     *
     * @param response The simulation response containing analytical data.
     * @return List of 5 intelligence bullets.
     */
    List<String> generateCommentary(SimulationResponse response);

    /**
     * Generates a single pre-match insight string based on match context.
     *
     * @param match The match details.
     * @return A pre-match insight string.
     */}
