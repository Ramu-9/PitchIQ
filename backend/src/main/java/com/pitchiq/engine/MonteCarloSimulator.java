package com.pitchiq.engine;

/**
 * The core simulation engine.
 * Completely independent of Spring Boot to ensure clean architecture and testability.
 * This class applies the Law of Large Numbers (Monte Carlo method).
 */
public class MonteCarloSimulator {
    
    // 10,000 iterations: standard error of estimated probability is ~1%
    // Runs in < 100ms on modern CPUs, balancing precision and performance.
    private static final int SIMULATION_COUNT = 10000;

    /**
     * Simulates the remainder of the innings 10,000 times.
     * @param initialState The current state of the match.
     * @param distribution The probability distribution of ball outcomes.
     * @return SimulationResult containing probability and projected score.
     */
    public SimulationResult simulate(MatchState initialState, ProbabilityDistribution distribution) {
        int wins = 0;
        long totalScore = 0;
        
        for (int i = 0; i < SIMULATION_COUNT; i++) {
            MatchState simState = new MatchState(initialState);
            
            while (!simState.isInningsOver()) {
                BallOutcome outcome = distribution.nextOutcome();
                simState.update(outcome);
            }
            
            totalScore += simState.getCurrentRuns();
            
            // Standard chase logic: if target is set and reached, count as a win.
            if (simState.getTargetScore() > 0 && simState.getCurrentRuns() >= simState.getTargetScore()) {
                wins++;
            }
        }
        
        double winProbability = (initialState.getTargetScore() > 0) ? (double) wins / SIMULATION_COUNT : 0.0;
        int projectedScore = (int) (totalScore / SIMULATION_COUNT);
        
        return new SimulationResult(winProbability, projectedScore);
    }
}
