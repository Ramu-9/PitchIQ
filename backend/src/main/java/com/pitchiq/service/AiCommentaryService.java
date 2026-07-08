package com.pitchiq.service;

import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.service.provider.AiCommentaryProvider;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public void enrichWithCommentary(SimulationResponse response) {
        response.setAiCommentary(provider.generateCommentary(response));
    }

}
