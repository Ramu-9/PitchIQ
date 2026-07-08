package com.pitchiq.service.provider;

import com.pitchiq.dto.MatchDto;
import com.pitchiq.dto.ScoreDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "pitchiq.cricket.provider", havingValue = "mock", matchIfMissing = true)
public class MockCricketDataProvider implements CricketDataProvider {

    @Override
    public List<MatchDto> getLiveMatches() {
        System.out.println("[MOCK] Fetching Live Matches...");
        List<MatchDto> matches = new ArrayList<>();
        
        MatchDto match = new MatchDto();
        match.setId("mock-match-1");
        match.setName("Mock RCB vs CSK");
        match.setStatus("RCB need 42 runs in 18 balls");
        match.setVenue("M Chinnaswamy Stadium");
        match.setBattingTeam("RCB");
        match.setBowlingTeam("CSK");
        
        ScoreDto score = new ScoreDto();
        score.setRuns(142);
        score.setWickets(4);
        score.setOvers(17.0);
        score.setInning("RCB Inning 1");
        
        match.setScores(Collections.singletonList(score));
        matches.add(match);
        
        return matches;
    }

    @Override
    public MatchDto getMatchDetails(String matchId) {
        if ("mock-match-1".equals(matchId)) {
            return getLiveMatches().get(0);
        }
        return null;
    }
}
