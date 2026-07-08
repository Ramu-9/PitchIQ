package com.pitchiq.dto;

public class MatchStateRequest {
    private int venueId;
    private int battingTeamId;
    private int bowlingTeamId;
    private int currentRuns;
    private int currentWickets;
    private double overs;
    private int targetScore;
    private int maxOvers = 20; // Default to T20
    private String venueName;

    // Getters and Setters
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    
    public int getVenueId() { return venueId; }
    public void setVenueId(int venueId) { this.venueId = venueId; }
    public int getBattingTeamId() { return battingTeamId; }
    public void setBattingTeamId(int battingTeamId) { this.battingTeamId = battingTeamId; }
    public int getBowlingTeamId() { return bowlingTeamId; }
    public void setBowlingTeamId(int bowlingTeamId) { this.bowlingTeamId = bowlingTeamId; }
    public int getCurrentRuns() { return currentRuns; }
    public void setCurrentRuns(int currentRuns) { this.currentRuns = currentRuns; }
    public int getCurrentWickets() { return currentWickets; }
    public void setCurrentWickets(int currentWickets) { this.currentWickets = currentWickets; }
    public double getOvers() { return overs; }
    public void setOvers(double overs) { this.overs = overs; }
    public int getTargetScore() { return targetScore; }
    public void setTargetScore(int targetScore) { this.targetScore = targetScore; }
    public int getMaxOvers() { return maxOvers; }
    public void setMaxOvers(int maxOvers) { this.maxOvers = maxOvers; }
}
