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

        // 2. Fetch Probability Distribution (format-aware)
        String format = request.getMatchFormat() != null ? request.getMatchFormat() : "t20";
        ProbabilityDistribution dist = getDistributionFromDatabase((long) request.getVenueId(), totalBallsBowled, format);

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

    private ProbabilityDistribution getDistributionFromDatabase(Long venueId, int ballsBowled, String matchFormat) {
        String phase = getMatchPhase(ballsBowled, matchFormat);
        List<VenueOutcomeProfile> profiles = profileRepository.findByVenueIdAndMatchPhase(venueId, phase);
        
        if (profiles.isEmpty()) {
            return getBaselineDistribution(matchFormat, phase);
        }
        
        Map<BallOutcome, Double> weights = new HashMap<>();
        for (VenueOutcomeProfile profile : profiles) {
            weights.put(profile.getOutcomeType(), profile.getProbabilityWeight());
        }
        
        return new ProbabilityDistribution(weights);
    }
    
    private String getMatchPhase(int ballsBowled, String matchFormat) {
        if ("odi".equalsIgnoreCase(matchFormat)) {
            if (ballsBowled < 60) return "POWERPLAY";   // 0-10 overs
            if (ballsBowled < 240) return "MIDDLE";      // 10-40 overs
            return "DEATH";                               // 40-50 overs
        } else if ("test".equalsIgnoreCase(matchFormat)) {
            if (ballsBowled < 150) return "POWERPLAY";   // First session
            if (ballsBowled < 450) return "MIDDLE";       // Middle sessions
            return "DEATH";                               // Late innings
        }
        // T20 default
        if (ballsBowled < 36) return "POWERPLAY";
        if (ballsBowled < 90) return "MIDDLE";
        return "DEATH";
    }

    /**
     * Format-aware baseline distributions based on real T20/ODI/Test scoring patterns.
     * Different formats have meaningfully different run rates and risk profiles.
     */
    private ProbabilityDistribution getBaselineDistribution(String matchFormat, String phase) {
        Map<BallOutcome, Double> weights = new HashMap<>();
        
        if ("test".equalsIgnoreCase(matchFormat)) {
            // Test: defensive, low boundary rate, ~3 RPO
            weights.put(BallOutcome.DOT, 0.45);
            weights.put(BallOutcome.ONE, 0.35);
            weights.put(BallOutcome.TWO, 0.05);
            weights.put(BallOutcome.FOUR, 0.07);
            weights.put(BallOutcome.SIX, 0.01);
            weights.put(BallOutcome.WICKET, 0.07);
        } else if ("odi".equalsIgnoreCase(matchFormat)) {
            // ODI: moderate aggression, ~5.5 RPO overall
            if ("POWERPLAY".equals(phase)) {
                weights.put(BallOutcome.DOT, 0.30);
                weights.put(BallOutcome.ONE, 0.38);
                weights.put(BallOutcome.TWO, 0.06);
                weights.put(BallOutcome.FOUR, 0.14);
                weights.put(BallOutcome.SIX, 0.04);
                weights.put(BallOutcome.WICKET, 0.08);
            } else if ("DEATH".equals(phase)) {
                weights.put(BallOutcome.DOT, 0.28);
                weights.put(BallOutcome.ONE, 0.32);
                weights.put(BallOutcome.TWO, 0.06);
                weights.put(BallOutcome.FOUR, 0.16);
                weights.put(BallOutcome.SIX, 0.10);
                weights.put(BallOutcome.WICKET, 0.08);
            } else {
                weights.put(BallOutcome.DOT, 0.35);
                weights.put(BallOutcome.ONE, 0.38);
                weights.put(BallOutcome.TWO, 0.06);
                weights.put(BallOutcome.FOUR, 0.10);
                weights.put(BallOutcome.SIX, 0.04);
                weights.put(BallOutcome.WICKET, 0.07);
            }
        } else {
            // T20: aggressive, ~8+ RPO
            if ("POWERPLAY".equals(phase)) {
                weights.put(BallOutcome.DOT, 0.32);
                weights.put(BallOutcome.ONE, 0.36);
                weights.put(BallOutcome.TWO, 0.05);
                weights.put(BallOutcome.FOUR, 0.15);
                weights.put(BallOutcome.SIX, 0.06);
                weights.put(BallOutcome.WICKET, 0.06);
            } else if ("DEATH".equals(phase)) {
                weights.put(BallOutcome.DOT, 0.28);
                weights.put(BallOutcome.ONE, 0.30);
                weights.put(BallOutcome.TWO, 0.05);
                weights.put(BallOutcome.FOUR, 0.16);
                weights.put(BallOutcome.SIX, 0.12);
                weights.put(BallOutcome.WICKET, 0.09);
            } else {
                weights.put(BallOutcome.DOT, 0.35);
                weights.put(BallOutcome.ONE, 0.38);
                weights.put(BallOutcome.TWO, 0.05);
                weights.put(BallOutcome.FOUR, 0.11);
                weights.put(BallOutcome.SIX, 0.05);
                weights.put(BallOutcome.WICKET, 0.06);
            }
        }
        
        return new ProbabilityDistribution(weights);
    }
}
