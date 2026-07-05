package com.pitchiq.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_history")
public class SimulationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batting_team_id")
    private Team battingTeam;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bowling_team_id")
    private Team bowlingTeam;
    
    private Integer currentRuns;
    private Integer currentWickets;
    private Integer currentBalls;
    private Integer targetScore;
    
    private Double winProbability;
    private Integer projectedScore;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and setters omitted for brevity in demo, but assumed present
    // ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Venue getVenue() { return venue; }
    public void setVenue(Venue venue) { this.venue = venue; }

    public Team getBattingTeam() { return battingTeam; }
    public void setBattingTeam(Team battingTeam) { this.battingTeam = battingTeam; }

    public Team getBowlingTeam() { return bowlingTeam; }
    public void setBowlingTeam(Team bowlingTeam) { this.bowlingTeam = bowlingTeam; }

    public Integer getCurrentRuns() { return currentRuns; }
    public void setCurrentRuns(Integer currentRuns) { this.currentRuns = currentRuns; }

    public Integer getCurrentWickets() { return currentWickets; }
    public void setCurrentWickets(Integer currentWickets) { this.currentWickets = currentWickets; }

    public Integer getCurrentBalls() { return currentBalls; }
    public void setCurrentBalls(Integer currentBalls) { this.currentBalls = currentBalls; }

    public Integer getTargetScore() { return targetScore; }
    public void setTargetScore(Integer targetScore) { this.targetScore = targetScore; }

    public Double getWinProbability() { return winProbability; }
    public void setWinProbability(Double winProbability) { this.winProbability = winProbability; }

    public Integer getProjectedScore() { return projectedScore; }
    public void setProjectedScore(Integer projectedScore) { this.projectedScore = projectedScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
