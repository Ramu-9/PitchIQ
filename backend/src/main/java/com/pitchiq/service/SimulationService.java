package com.pitchiq.service;

import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.engine.BallOutcome;
import com.pitchiq.engine.MatchState;
import com.pitchiq.engine.MonteCarloSimulator;
import com.pitchiq.engine.ProbabilityDistribution;
import com.pitchiq.engine.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the Spring REST layer with the standalone Monte Carlo engine.
 */
@Service
public class SimulationService {

    private final MonteCarloSimulator simulator;
    private final AiCommentaryService aiCommentaryService;

    public SimulationService(AiCommentaryService aiCommentaryService) {
        this.simulator = new MonteCarloSimulator();
        this.aiCommentaryService = aiCommentaryService;
    }

    public SimulationResponse runSimulation(MatchStateRequest request) {
        // 1. Map DTO to Engine State
        int totalBallsBowled = (request.getOversBowled() * 6) + request.getBallsBowledInOver();
        MatchState initialState = new MatchState(
                request.getCurrentRuns(),
                request.getCurrentWickets(),
                totalBallsBowled,
                request.getTargetScore()
        );

        // 2. Fetch Probability Distribution
        // TODO: In Phase 5, this will be fetched from the Database based on Venue and Phase.
        // For Phase 4, we use a mock baseline distribution.
        ProbabilityDistribution dist = getBaselineDistribution();

        // 3. Run Simulation Engine
        SimulationResult result = simulator.simulate(initialState, dist);

        // 4. Map Result to Response DTO
        SimulationResponse response = new SimulationResponse();
        response.setWinProbability(result.getWinProbability());
        response.setProjectedScore(result.getProjectedScore());

        if (request.getTargetScore() > 0) {
            response.setExpectedRunsRemaining(request.getTargetScore() - request.getCurrentRuns());
            int ballsRemaining = 120 - totalBallsBowled;
            if (ballsRemaining > 0) {
                response.setRequiredRunRate(((double) response.getExpectedRunsRemaining() / ballsRemaining) * 6);
            }
        }
        
        // Mock momentum meter (0 to 1) based on simple heuristic for now
        response.setMomentumMeter(0.5);

        // Enhance with AI
        aiCommentaryService.enrichWithCommentary(response, request);

        return response;
    }

    private ProbabilityDistribution getBaselineDistribution() {
        Map<BallOutcome, Double> weights = new HashMap<>();
        weights.put(BallOutcome.DOT, 0.35);
        weights.put(BallOutcome.ONE, 0.40);
        weights.put(BallOutcome.TWO, 0.05);
        weights.put(BallOutcome.FOUR, 0.10);
        weights.put(BallOutcome.SIX, 0.05);
        weights.put(BallOutcome.WICKET, 0.05);
        return new ProbabilityDistribution(weights);
    }
}
