package com.pitchiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VenueIntelligenceDto {
    private String groundName = "";
    private String city = "";
    private String pitchType = "";
    private String battingRating = "";
    private String bowlingRating = "";
    private String spinSupport = "";
    private String paceSupport = "";
    private String averageFirstInningsScore = "";
    private String highestSuccessfulChase = "";
    private String boundarySize = "";
    private String dewFactor = "";
    private String tossAdvantage = "";
    private String historicalTrend = "";
    private String recommendedStrategy = "";
    private String shortSummary = "";

    public String getGroundName() { return groundName; }
    public void setGroundName(String groundName) { this.groundName = groundName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPitchType() { return pitchType; }
    public void setPitchType(String pitchType) { this.pitchType = pitchType; }
    public String getBattingRating() { return battingRating; }
    public void setBattingRating(String battingRating) { this.battingRating = battingRating; }
    public String getBowlingRating() { return bowlingRating; }
    public void setBowlingRating(String bowlingRating) { this.bowlingRating = bowlingRating; }
    public String getSpinSupport() { return spinSupport; }
    public void setSpinSupport(String spinSupport) { this.spinSupport = spinSupport; }
    public String getPaceSupport() { return paceSupport; }
    public void setPaceSupport(String paceSupport) { this.paceSupport = paceSupport; }
    public String getAverageFirstInningsScore() { return averageFirstInningsScore; }
    public void setAverageFirstInningsScore(String averageFirstInningsScore) { this.averageFirstInningsScore = averageFirstInningsScore; }
    public String getHighestSuccessfulChase() { return highestSuccessfulChase; }
    public void setHighestSuccessfulChase(String highestSuccessfulChase) { this.highestSuccessfulChase = highestSuccessfulChase; }
    public String getBoundarySize() { return boundarySize; }
    public void setBoundarySize(String boundarySize) { this.boundarySize = boundarySize; }
    public String getDewFactor() { return dewFactor; }
    public void setDewFactor(String dewFactor) { this.dewFactor = dewFactor; }
    public String getTossAdvantage() { return tossAdvantage; }
    public void setTossAdvantage(String tossAdvantage) { this.tossAdvantage = tossAdvantage; }
    public String getHistoricalTrend() { return historicalTrend; }
    public void setHistoricalTrend(String historicalTrend) { this.historicalTrend = historicalTrend; }
    public String getRecommendedStrategy() { return recommendedStrategy; }
    public void setRecommendedStrategy(String recommendedStrategy) { this.recommendedStrategy = recommendedStrategy; }
    public String getShortSummary() { return shortSummary; }
    public void setShortSummary(String shortSummary) { this.shortSummary = shortSummary; }
}
