package com.pitchiq.controller;

import com.pitchiq.dto.MatchDto;
import com.pitchiq.service.CricketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matches")
public class CricketController {

    private final CricketService cricketService;

    public CricketController(CricketService cricketService) {
        this.cricketService = cricketService;
    }

    @GetMapping("/live")
    public ResponseEntity<List<MatchDto>> getLiveMatches() {
        return ResponseEntity.ok(cricketService.getLiveMatches());
    }

    @GetMapping("/raw")
    public ResponseEntity<String> getRawMatches(@RequestParam(defaultValue = "v1/currentMatches") String endpoint) {
        return ResponseEntity.ok().header("Content-Type", "application/json").body(cricketService.getRawMatches(endpoint));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatchDetails(@PathVariable String id) {
        MatchDto match = cricketService.getMatchDetails(id);
        if (match != null) {
            return ResponseEntity.ok(match);
        }
        return ResponseEntity.notFound().build();
    }
}
