package com.pitchiq.engine;

/**
 * DTO representing the final output of the Monte Carlo simulation.
 * Read-only class to guarantee data immutability after simulation completes.
 */
public class SimulationResult {
    private final double winProbability;
    private final int projectedScore;

    public SimulationResult(double winProbability, int projectedScore) {
        this.winProbability = winProbability;
        this.projectedScore = projectedScore;
    }

    public double getWinProbability() { return winProbability; }
    public int getProjectedScore() { return projectedScore; }

    @Override
    public String toString() {
        return String.format("SimulationResult[WinProb=%.2f%%, ProjectedScore=%d]", 
                             winProbability * 100, projectedScore);
    }
}
