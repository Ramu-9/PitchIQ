package com.pitchiq.service;

import com.pitchiq.dto.MatchDto;
import com.pitchiq.service.provider.CricketDataProvider;
import com.pitchiq.service.provider.MockCricketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CricketService {

    private static final Logger log = LoggerFactory.getLogger(CricketService.class);

    private final CricketDataProvider primaryProvider;
    private final MockCricketDataProvider mockFallback = new MockCricketDataProvider();

    public CricketService(CricketDataProvider primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    public List<MatchDto> getLiveMatches() {
        try {
            return primaryProvider.getLiveMatches();
        } catch (Exception e) {
            log.warn("[CricketService] Primary provider failed: {}. Falling back to Mock.", e.getMessage());
            return mockFallback.getLiveMatches();
        }
    }

    public MatchDto getMatchDetails(String matchId) {
        try {
            return primaryProvider.getMatchDetails(matchId);
        } catch (Exception e) {
            log.warn("[CricketService] Primary provider failed: {}. Falling back to Mock.", e.getMessage());
            return mockFallback.getMatchDetails(matchId);
        }
    }
}
