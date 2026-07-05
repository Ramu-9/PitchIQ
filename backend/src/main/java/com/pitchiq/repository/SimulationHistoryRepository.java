package com.pitchiq.repository;

import com.pitchiq.entity.SimulationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SimulationHistoryRepository extends JpaRepository<SimulationHistory, Long> {
    List<SimulationHistory> findTop10ByOrderByCreatedAtDesc();
}
