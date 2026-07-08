package com.pitchiq.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Standalone Main class to prove the Monte Carlo engine's correctness
 * before wiring it into any Spring Boot or database complexity.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting PitchIQ Simulation Engine Test...\n");

        // 1. Create a dummy Probability Distribution (e.g. T20 global average)
        Map<BallOutcome, Double> weights = new HashMap<>();
        weights.put(BallOutcome.DOT, 0.35);
        weights.put(BallOutcome.ONE, 0.40);
        weights.put(BallOutcome.TWO, 0.05);
        weights.put(BallOutcome.THREE, 0.005);
        weights.put(BallOutcome.FOUR, 0.10);
        weights.put(BallOutcome.SIX, 0.045);
        weights.put(BallOutcome.WICKET, 0.05);
        ProbabilityDistribution dist = new ProbabilityDistribution(weights);

        MonteCarloSimulator simulator = new MonteCarloSimulator();

        // Test Case 1: Easy Chase
        // 120/2 in 15 overs (90 balls). Target 150. Need 30 off 30 balls.
        MatchState state1 = new MatchState(120, 2, 90, 150, 120);
        long startTime = System.currentTimeMillis();
        SimulationResult result1 = simulator.simulate(state1, dist);
        long time1 = System.currentTimeMillis() - startTime;
        System.out.println("Test Case 1: Easy Chase (30 runs needed off 30 balls, 8 wickets in hand)");
        System.out.println(result1);
        System.out.println("Time taken: " + time1 + " ms\n");

        // Test Case 2: Impossible Chase
        // 100/9 in 18 overs (108 balls). Target 250. Need 150 off 12 balls.
        MatchState state2 = new MatchState(100, 9, 108, 250, 120);
        SimulationResult result2 = simulator.simulate(state2, dist);
        System.out.println("Test Case 2: Impossible Chase (150 runs needed off 12 balls, 1 wicket in hand)");
        System.out.println(result2 + "\n");

        // Test Case 3: First Innings Projection
        // 80/1 in 10 overs (60 balls). No target (Setting).
        MatchState state3 = new MatchState(80, 1, 60, 0, 120);
        SimulationResult result3 = simulator.simulate(state3, dist);
        System.out.println("Test Case 3: First Innings Projection (80/1 in 10 overs)");
        System.out.println(result3 + "\n");
        
        System.out.println("Engine tests completed.");
    }
}
