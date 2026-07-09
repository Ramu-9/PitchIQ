package com.pitchiq.dto;

import java.util.List;

public class SimulationResponse {
    private double winProbability;
    private int projectedScore;
    private int expectedRunsRemaining;
    private double requiredRunRate;
    private double momentumMeter;
    private List<String> aiCommentary;
    private String venueName;
    private VenueIntelligenceDto venueIntelligence;

    // Getters and setters
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
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

    public List<String> getAiCommentary() { return aiCommentary; }
    public void setAiCommentary(List<String> aiCommentary) { this.aiCommentary = aiCommentary; }

    public VenueIntelligenceDto getVenueIntelligence() { return venueIntelligence; }
    public void setVenueIntelligence(VenueIntelligenceDto venueIntelligence) { this.venueIntelligence = venueIntelligence; }
}
