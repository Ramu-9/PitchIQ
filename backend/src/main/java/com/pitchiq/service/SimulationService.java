package com.pitchiq.service;

import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.engine.BallOutcome;
import com.pitchiq.engine.MatchState;
import com.pitchiq.engine.MonteCarloSimulator;
import com.pitchiq.engine.ProbabilityDistribution;
import com.pitchiq.engine.SimulationResult;
import com.pitchiq.entity.VenueOutcomeProfile;
import com.pitchiq.repository.VenueOutcomeProfileRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges the Spring REST layer with the standalone Monte Carlo engine.
 */
@Service
public class SimulationService {

    private final MonteCarloSimulator simulator;
    private final AiCommentaryService aiCommentaryService;
    private final VenueOutcomeProfileRepository profileRepository;

    public SimulationService(AiCommentaryService aiCommentaryService, VenueOutcomeProfileRepository profileRepository) {
        this.simulator = new MonteCarloSimulator();
        this.aiCommentaryService = aiCommentaryService;
        this.profileRepository = profileRepository;
    }

    public SimulationResponse runSimulation(MatchStateRequest request) {
        // 1. Map DTO to Engine State
        int fullOvers = (int) request.getOvers();
        int ballsInPartialOver = (int) Math.round((request.getOvers() - fullOvers) * 10);
        int totalBallsBowled = (fullOvers * 6) + ballsInPartialOver;
        int maxBalls = request.getMaxOvers() * 6;
        MatchState initialState = new MatchState(
                request.getCurrentRuns(),
                request.getCurrentWickets(),
                totalBallsBowled,
                request.getTargetScore(),
                maxBalls
        );

        // 2. Fetch Probability Distribution
        ProbabilityDistribution dist = getDistributionFromDatabase((long) request.getVenueId(), totalBallsBowled);

        // 3. Run Simulation Engine
        SimulationResult result = simulator.simulate(initialState, dist);

        // 4. Map Result to Response DTO
        SimulationResponse response = new SimulationResponse();
        response.setWinProbability(result.getWinProbability());
        response.setProjectedScore(result.getProjectedScore());
        response.setVenueName(request.getVenueName());

        if (request.getTargetScore() > 0) {
            response.setExpectedRunsRemaining(request.getTargetScore() - request.getCurrentRuns());
            int ballsRemaining = maxBalls - totalBallsBowled;
            if (ballsRemaining > 0) {
                response.setRequiredRunRate(((double) response.getExpectedRunsRemaining() / ballsRemaining) * 6);
            }
        }
        
        // Calculate momentum meter (0 to 1) based on current win probability
        response.setMomentumMeter(result.getWinProbability());

        // 5. Enrich with Commentary
        aiCommentaryService.enrichWithCommentary(response, request);

        return response;
    }

    private ProbabilityDistribution getDistributionFromDatabase(Long venueId, int ballsBowled) {
        String phase = getMatchPhase(ballsBowled);
        List<VenueOutcomeProfile> profiles = profileRepository.findByVenueIdAndMatchPhase(venueId, phase);
        
        if (profiles.isEmpty()) {
            return getBaselineDistribution(); // Fallback if no DB data
        }
        
        Map<BallOutcome, Double> weights = new HashMap<>();
        for (VenueOutcomeProfile profile : profiles) {
            weights.put(profile.getOutcomeType(), profile.getProbabilityWeight());
        }
        
        return new ProbabilityDistribution(weights);
    }
    
    private String getMatchPhase(int ballsBowled) {
        if (ballsBowled < 36) return "POWERPLAY";
        if (ballsBowled < 90) return "MIDDLE";
        return "DEATH";
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
