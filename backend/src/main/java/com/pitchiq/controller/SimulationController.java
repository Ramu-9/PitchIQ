package com.pitchiq.controller;

import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.service.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "${pitchiq.cors.origins:*}") // For frontend integration
public class SimulationController {

    private final SimulationService simulationService;

    @Autowired
    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<SimulationResponse> analyzeMatchState(@RequestBody MatchStateRequest request) {
        if (request.getCurrentWickets() < 0 || request.getCurrentWickets() > 10) {
            throw new IllegalArgumentException("Wickets must be between 0 and 10");
        }
        
        SimulationResponse response = simulationService.runSimulation(request);
        return ResponseEntity.ok(response);
    }
}
