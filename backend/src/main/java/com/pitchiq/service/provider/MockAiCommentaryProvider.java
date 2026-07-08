package com.pitchiq.service.provider;

import com.pitchiq.dto.MatchDto;
import com.pitchiq.dto.SimulationResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Mock provider for AI commentary.
 * Used during local development and testing to prevent consuming real API quotas.
 */
@Service
@ConditionalOnProperty(name = "pitchiq.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiCommentaryProvider implements AiCommentaryProvider {

    @Override
    public List<String> generateCommentary(SimulationResponse response) {
        return Arrays.asList(
            "Matches at this venue are historically won in the middle overs.",
            "The pitch is slowing down, favoring spin bowlers.",
            String.format("Required Run Rate stands at %.2f.", response.getRequiredRunRate()),
            "Focus on strike rotation instead of risky boundaries.",
            String.format("Based on 10,000 simulations, the win probability is %.1f%%.", response.getWinProbability() * 100)
        );
    }
}
