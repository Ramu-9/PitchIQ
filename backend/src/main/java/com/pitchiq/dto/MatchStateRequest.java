package com.pitchiq.dto;

public class MatchStateRequest {
    private int venueId;
    private int battingTeamId;
    private int bowlingTeamId;
    private int currentRuns;
    private int currentWickets;
    private int oversBowled;
    private int ballsBowledInOver;
    private int targetScore;
    private String persona = "Analyst"; // Default persona

    // Getters and setters
    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }
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

    public int getOversBowled() { return oversBowled; }
    public void setOversBowled(int oversBowled) { this.oversBowled = oversBowled; }

    public int getBallsBowledInOver() { return ballsBowledInOver; }
    public void setBallsBowledInOver(int ballsBowledInOver) { this.ballsBowledInOver = ballsBowledInOver; }

    public int getTargetScore() { return targetScore; }
    public void setTargetScore(int targetScore) { this.targetScore = targetScore; }
}
