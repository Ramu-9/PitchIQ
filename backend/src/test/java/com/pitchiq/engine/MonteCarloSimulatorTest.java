package com.pitchiq.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloSimulatorTest {

    private MonteCarloSimulator simulator;
    private ProbabilityDistribution dist;

    @BeforeEach
    void setUp() {
        simulator = new MonteCarloSimulator();
        
        // Simple distribution favoring DOTs and ONEs
        Map<BallOutcome, Double> weights = new HashMap<>();
        weights.put(BallOutcome.DOT, 0.40);
        weights.put(BallOutcome.ONE, 0.40);
        weights.put(BallOutcome.FOUR, 0.10);
        weights.put(BallOutcome.SIX, 0.05);
        weights.put(BallOutcome.WICKET, 0.05);
        dist = new ProbabilityDistribution(weights);
    }

    @Test
    void testImpossibleChase_ZeroWinProbability() {
        // Need 200 off 1 ball (impossible)
        MatchState state = new MatchState(100, 9, 119, 300);
        SimulationResult result = simulator.simulate(state, dist);
        
        assertEquals(0.0, result.getWinProbability(), "Impossible chase should have 0.0 win probability");
    }

    @Test
    void testGuaranteedChase_HighWinProbability() {
        // Need 1 run off 60 balls with 10 wickets in hand
        MatchState state = new MatchState(100, 0, 60, 101);
        SimulationResult result = simulator.simulate(state, dist);
        
        assertTrue(result.getWinProbability() > 0.99, "Guaranteed chase should have ~100% win probability");
    }
    
    @Test
    void testFirstInnings_ZeroWinProbability() {
        // Setting target (targetScore = 0)
        MatchState state = new MatchState(100, 2, 60, 0);
        SimulationResult result = simulator.simulate(state, dist);
        
        assertEquals(0.0, result.getWinProbability(), "First innings should have 0 win probability as target is not set");
        assertTrue(result.getProjectedScore() > 100, "Projected score should be higher than current score");
    }

    @Test
    void testInningsOver_NoStateChange() {
        // Match already over (10 wickets down)
        MatchState state = new MatchState(150, 10, 100, 200);
        SimulationResult result = simulator.simulate(state, dist);
        
        assertEquals(150, result.getProjectedScore(), "Projected score should equal current score if innings is over");
        assertEquals(0.0, result.getWinProbability(), "Win probability should be 0 if all out before target");
    }
}
