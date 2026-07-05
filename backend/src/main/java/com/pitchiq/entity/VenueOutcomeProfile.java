package com.pitchiq.entity;

import com.pitchiq.engine.BallOutcome;
import jakarta.persistence.*;

@Entity
@Table(name = "venue_outcome_profile")
public class VenueOutcomeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;
    
    @Column(name = "match_phase", nullable = false)
    private String matchPhase; // POWERPLAY, MIDDLE, DEATH
    
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_type", nullable = false)
    private BallOutcome outcomeType;
    
    @Column(name = "probability_weight", nullable = false)
    private Double probabilityWeight;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Venue getVenue() { return venue; }
    public void setVenue(Venue venue) { this.venue = venue; }
    
    public String getMatchPhase() { return matchPhase; }
    public void setMatchPhase(String matchPhase) { this.matchPhase = matchPhase; }
    
    public BallOutcome getOutcomeType() { return outcomeType; }
    public void setOutcomeType(BallOutcome outcomeType) { this.outcomeType = outcomeType; }
    
    public Double getProbabilityWeight() { return probabilityWeight; }
    public void setProbabilityWeight(Double probabilityWeight) { this.probabilityWeight = probabilityWeight; }
}
