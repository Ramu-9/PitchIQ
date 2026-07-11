package com.pitchiq.service.provider;

import com.pitchiq.dto.MatchDto;
import java.util.List;

public interface CricketDataProvider {
    List<MatchDto> getLiveMatches();
    default String getRawMatches(String endpoint) { return "{}"; }
    MatchDto getMatchDetails(String matchId);
}
