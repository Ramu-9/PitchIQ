package com.pitchiq.repository;

import com.pitchiq.entity.VenueIntelligence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VenueIntelligenceRepository extends JpaRepository<VenueIntelligence, Long> {
    Optional<VenueIntelligence> findByVenueNameIgnoreCase(String venueName);
}
