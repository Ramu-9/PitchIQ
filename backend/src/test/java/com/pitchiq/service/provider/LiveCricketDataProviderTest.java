package com.pitchiq.service.provider;

import com.pitchiq.dto.MatchDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveCricketDataProviderTest {

    private LiveCricketDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LiveCricketDataProvider();
    }

    @Test
    void testIsTerminalStatus_AwardedAndRefusedToPlay() {
        String status = "Tanzania Women awarded the match (opposition refused to play)";
        assertTrue(LiveCricketDataProvider.isTerminalStatus(status), 
            "Status with 'awarded' and 'refused to play' must be identified as terminal");
    }

    @Test
    void testIsTerminalStatus_CommonTerminalKeywords() {
        assertTrue(LiveCricketDataProvider.isTerminalStatus("India won by 6 wickets"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Australia lost by 12 runs"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match drawn"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match tied"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("No result (rain)"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match abandoned without a ball bowled"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match cancelled due to wet outfield"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match ended"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("England conceded the match"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Walkover to team B"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match concluded"));
        assertTrue(LiveCricketDataProvider.isTerminalStatus("Match postponed"));
    }

    @Test
    void testIsTerminalStatus_NonTerminalLiveAndUpcomingStatuses() {
        assertFalse(LiveCricketDataProvider.isTerminalStatus("Match starts at 3:30 PM IST"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("India need 24 runs in 18 balls"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("Innings break"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("Stumps - Day 2"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("Rain delay - play to resume shortly"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("Toss won by Australia and elected to bat"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus("toss won by india"));
        assertFalse(LiveCricketDataProvider.isTerminalStatus(null));
        assertFalse(LiveCricketDataProvider.isTerminalStatus(""));
    }

    @Test
    void testCalculateStateScore_TerminalMatchTreatedAsRecentNotLive() {
        MatchDto match = new MatchDto();
        match.setId("tan-uga-test");
        match.setName("TAN w vs UGA w");
        match.setStatus("Tanzania Women awarded the match (opposition refused to play)");
        match.setMatchStarted(true);
        match.setMatchEnded(false); // Even if provider returned matchEnded false!

        int score = provider.calculateStateScore(match);
        assertEquals(2, score, "Terminal match must score as 2 (RECENT), never 3 (LIVE)");
    }

    @Test
    void testCalculateStateScore_LiveMatch() {
        MatchDto match = new MatchDto();
        match.setId("live-match-1");
        match.setName("IND vs PAK");
        match.setStatus("IND need 12 runs in 8 balls");
        match.setMatchStarted(true);
        match.setMatchEnded(false);

        int score = provider.calculateStateScore(match);
        assertEquals(3, score, "Active match in progress must score as 3 (LIVE)");
    }

    @Test
    void testCalculateStateScore_UpcomingMatch() {
        MatchDto match = new MatchDto();
        match.setId("upcoming-match-1");
        match.setName("ENG vs AUS");
        match.setStatus("Match starts at Aug 20, 7:00 PM IST");
        match.setMatchStarted(false);
        match.setMatchEnded(false);

        int score = provider.calculateStateScore(match);
        assertEquals(1, score, "Upcoming match must score as 1 (UPCOMING)");
    }



    @Test
    void testTeamAbbreviations() throws Exception {
        java.lang.reflect.Method method = LiveCricketDataProvider.class.getDeclaredMethod("sanitizeAbbreviation", String.class, String.class);
        method.setAccessible(true);
        
        // Men's teams
        assertEquals("IND", method.invoke(provider, "IND", "India"));
        assertEquals("AUS", method.invoke(provider, "AU", "Australia"));
        assertEquals("ENG", method.invoke(provider, "", "England"));
        assertEquals("PAK", method.invoke(provider, "PK", "Pakistan"));
        
        // Women's teams
        assertEquals("IND w", method.invoke(provider, "IND-W", "India Women"));
        assertEquals("AUS w", method.invoke(provider, "AU-W", "Australia Women"));
        assertEquals("ENG w", method.invoke(provider, "", "England W"));
        assertEquals("PAK w", method.invoke(provider, "PK W", "Pakistan Women"));
    }
}
