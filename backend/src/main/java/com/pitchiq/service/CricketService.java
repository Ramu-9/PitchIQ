package com.pitchiq.service;

import com.pitchiq.dto.MatchDto;
import com.pitchiq.service.provider.CricketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CricketService {

    private static final Logger log = LoggerFactory.getLogger(CricketService.class);

    private final CricketDataProvider primaryProvider;

    public CricketService(CricketDataProvider primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    public List<MatchDto> getLiveMatches() {
        try {
            return primaryProvider.getLiveMatches();
        } catch (Exception e) {
            log.error("[CricketService] Primary provider failed: {}", e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "CricAPI is temporarily unavailable"
            );
        }
    }

    public MatchDto getMatchDetails(String matchId) {
        try {
            return primaryProvider.getMatchDetails(matchId);
        } catch (Exception e) {
            log.error("[CricketService] Primary provider failed: {}", e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, 
                "CricAPI is temporarily unavailable"
            );
        }
    }
}
