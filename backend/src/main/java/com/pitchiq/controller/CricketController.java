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

    @org.springframework.beans.factory.annotation.Value("${cricapi.key:}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Value("${cricapi.base-url:https://api.cricapi.com/}")
    private String baseUrl;

    @GetMapping("/raw")
    public ResponseEntity<String> getRawMatches(@RequestParam(defaultValue = "v1/currentMatches") String endpoint) {
        try {
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + endpoint + "?apikey=" + apiKey;
            return ResponseEntity.ok().header("Content-Type", "application/json").body(rt.getForObject(url, String.class));
        } catch (Exception e) {
            return ResponseEntity.ok("{\"error\":\"" + e.getMessage() + "\"}");
        }
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
