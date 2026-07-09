package com.pitchiq.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "venue_intelligence")
public class VenueIntelligence {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String venueName;
    
    @Column(columnDefinition = "TEXT")
    private String groundName;
    
    @Column(columnDefinition = "TEXT")
    private String city;
    
    @Column(columnDefinition = "TEXT")
    private String pitchType;
    
    @Column(columnDefinition = "TEXT")
    private String battingRating;
    
    @Column(columnDefinition = "TEXT")
    private String bowlingRating;
    
    @Column(columnDefinition = "TEXT")
    private String spinSupport;
    
    @Column(columnDefinition = "TEXT")
    private String paceSupport;
    
    @Column(columnDefinition = "TEXT")
    private String averageFirstInningsScore;
    
    @Column(columnDefinition = "TEXT")
    private String highestSuccessfulChase;
    
    @Column(columnDefinition = "TEXT")
    private String boundarySize;
    
    @Column(columnDefinition = "TEXT")
    private String dewFactor;
    
    @Column(columnDefinition = "TEXT")
    private String tossAdvantage;
    
    @Column(columnDefinition = "TEXT")
    private String historicalTrend;
    
    @Column(columnDefinition = "TEXT")
    private String recommendedStrategy;
    
    @Column(columnDefinition = "TEXT")
    private String shortSummary;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void setTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
