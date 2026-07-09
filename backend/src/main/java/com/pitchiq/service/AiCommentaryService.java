package com.pitchiq.service;

import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.CombinedIntelligenceResult;
import com.pitchiq.service.provider.AiCommentaryProvider;
import org.springframework.stereotype.Service;


/**
 * Service to generate AI commentary.
 * Delegates actual generation to the configured AiCommentaryProvider (Mock or Gemini).
 */
@Service
public class AiCommentaryService {

    private final AiCommentaryProvider provider;

    public AiCommentaryService(AiCommentaryProvider provider) {
        this.provider = provider;
    }

    public void enrichWithCommentary(SimulationResponse response, MatchStateRequest request) {
        CombinedIntelligenceResult intelligence = provider.generateIntelligence(request, response);
        response.setAiCommentary(intelligence.getInsights());
        response.setVenueIntelligence(intelligence.getVenueIntelligence());
    }

}
