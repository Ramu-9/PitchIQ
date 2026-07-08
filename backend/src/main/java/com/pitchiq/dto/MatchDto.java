package com.pitchiq.dto;

import java.util.List;

public class MatchDto {
    private String id;
    private String name;
    private String status;
    private String venue;
    private String battingTeam;
    private String bowlingTeam;
    private List<ScoreDto> scores;
    private boolean matchStarted;
    private boolean matchEnded;
    private String matchType;
    private String dateTimeGMT;

    // Getters and Setters
    public String getDateTimeGMT() { return dateTimeGMT; }
    public void setDateTimeGMT(String dateTimeGMT) { this.dateTimeGMT = dateTimeGMT; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getBattingTeam() { return battingTeam; }
    public void setBattingTeam(String battingTeam) { this.battingTeam = battingTeam; }
    public String getBowlingTeam() { return bowlingTeam; }
    public void setBowlingTeam(String bowlingTeam) { this.bowlingTeam = bowlingTeam; }
    public List<ScoreDto> getScores() { return scores; }
    public void setScores(List<ScoreDto> scores) { this.scores = scores; }
    public boolean isMatchStarted() { return matchStarted; }
    public void setMatchStarted(boolean matchStarted) { this.matchStarted = matchStarted; }
    public boolean isMatchEnded() { return matchEnded; }
    public void setMatchEnded(boolean matchEnded) { this.matchEnded = matchEnded; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
}
