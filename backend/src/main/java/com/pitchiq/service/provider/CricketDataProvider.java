package com.pitchiq.service.provider;

import com.pitchiq.dto.MatchDto;
import java.util.List;

public interface CricketDataProvider {
    List<MatchDto> getLiveMatches();
    MatchDto getMatchDetails(String matchId);
}
