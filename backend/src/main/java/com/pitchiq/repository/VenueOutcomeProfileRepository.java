package com.pitchiq.repository;

import com.pitchiq.entity.VenueOutcomeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VenueOutcomeProfileRepository extends JpaRepository<VenueOutcomeProfile, Long> {
    List<VenueOutcomeProfile> findByVenueIdAndMatchPhase(Long venueId, String matchPhase);
}
