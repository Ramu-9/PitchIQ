package com.pitchiq.dto;

import java.util.Map;

public class SimulationResponse {
    private double winProbability;
    private int projectedScore;
    private int expectedRunsRemaining;
    private double requiredRunRate;
    private double momentumMeter;
    private Map<String, String> aiCommentary;

    // Getters and setters
    public double getWinProbability() { return winProbability; }
    public void setWinProbability(double winProbability) { this.winProbability = winProbability; }

    public int getProjectedScore() { return projectedScore; }
    public void setProjectedScore(int projectedScore) { this.projectedScore = projectedScore; }

    public int getExpectedRunsRemaining() { return expectedRunsRemaining; }
    public void setExpectedRunsRemaining(int expectedRunsRemaining) { this.expectedRunsRemaining = expectedRunsRemaining; }

    public double getRequiredRunRate() { return requiredRunRate; }
    public void setRequiredRunRate(double requiredRunRate) { this.requiredRunRate = requiredRunRate; }

    public double getMomentumMeter() { return momentumMeter; }
    public void setMomentumMeter(double momentumMeter) { this.momentumMeter = momentumMeter; }

    public Map<String, String> getAiCommentary() { return aiCommentary; }
    public void setAiCommentary(Map<String, String> aiCommentary) { this.aiCommentary = aiCommentary; }
}
