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
    void testCompetitionScore_TierGaps() {
        MatchDto t1 = new MatchDto();
        t1.setName("India vs Pakistan, Group A, ICC Men's T20 World Cup, 2026");
        assertEquals(5000, provider.calculateCompetitionScore(t1), "Global ICC Events must be Tier 1 (5000)");

        MatchDto t2 = new MatchDto();
        t2.setName("Royal Challengers Bengaluru vs Chennai Super Kings, Indian Premier League 2026");
        assertEquals(4000, provider.calculateCompetitionScore(t2), "Premier Franchise / Continental must be Tier 2 (4000)");

        MatchDto t3 = new MatchDto();
        t3.setName("England vs Australia, 1st Test, The Ashes 2026");
        assertEquals(3000, provider.calculateCompetitionScore(t3), "Bilateral International must be Tier 3 (3000)");

        MatchDto t4 = new MatchDto();
        t4.setName("Somerset vs Essex, County Championship Division One 2026");
        assertEquals(2000, provider.calculateCompetitionScore(t4), "Premier Domestic must be Tier 4 (2000)");

        MatchDto t5 = new MatchDto();
        t5.setName("Shivamogga Yodhas vs Coastal Kings Mangaluru, Qualifier 2, Maharaja Trophy KSCA T20 2026");
        assertEquals(1000, provider.calculateCompetitionScore(t5), "Regional / State must be Tier 5 (1000)");
    }

    @Test
    void testTeamPopularityDominatesCompetitionContext() {
        // World Cup match with two Associate teams (NED vs NEP)
        MatchDto wcAssociate = new MatchDto();
        wcAssociate.setName("Netherlands vs Nepal, Group D, ICC Men's T20 World Cup, 2026");
        wcAssociate.setBattingTeam("Netherlands");
        wcAssociate.setBattingTeamShort("NED");
        wcAssociate.setBowlingTeam("Nepal");
        wcAssociate.setBowlingTeamShort("NEP");
        wcAssociate.setMatchType("T20");

        // Bilateral tour match with two Full Members (IND vs ZIM)
        MatchDto bilateralFull = new MatchDto();
        bilateralFull.setName("India vs Zimbabwe, 5th T20I, India tour of Zimbabwe, 2026");
        bilateralFull.setBattingTeam("India");
        bilateralFull.setBattingTeamShort("IND");
        bilateralFull.setBowlingTeam("Zimbabwe");
        bilateralFull.setBowlingTeamShort("ZIM");
        bilateralFull.setMatchType("T20");

        int qWc = provider.calculateTeamScore(wcAssociate);
        int qBilateral = provider.calculateTeamScore(bilateralFull);

        assertTrue(qBilateral > qWc, 
            "Bilateral match (IND vs ZIM: " + qBilateral + ") must outrank World Cup match (NED vs NEP: " + qWc + ")");
    }

    @Test
    void testMajorFranchise_DominatesBilateralAssociate() {
        // Premier Franchise match (CSK vs RCB) - Tier 3 vs Tier 3 = 60,000 pts
        MatchDto iplMatch = new MatchDto();
        iplMatch.setName("Chennai Super Kings vs Royal Challengers Bengaluru, Qualifier 1, Indian Premier League 2026");
        iplMatch.setBattingTeam("Chennai Super Kings");
        iplMatch.setBattingTeamShort("CSK");
        iplMatch.setBowlingTeam("Royal Challengers Bengaluru");
        iplMatch.setBowlingTeamShort("RCB");
        iplMatch.setMatchType("T20");

        // Bilateral Associate match (SCO vs NAM) - Tier 1 vs Tier 1 = 20,000 pts
        MatchDto associateBilateral = new MatchDto();
        associateBilateral.setName("Scotland vs Namibia, 2nd T20I, Namibia tour of Scotland, 2026");
        associateBilateral.setBattingTeam("Scotland");
        associateBilateral.setBattingTeamShort("SCO");
        associateBilateral.setBowlingTeam("Namibia");
        associateBilateral.setBowlingTeamShort("NAM");
        associateBilateral.setMatchType("T20");

        int qIpl = provider.calculateTeamScore(iplMatch);
        int qAssociate = provider.calculateTeamScore(associateBilateral);

        assertTrue(qIpl > qAssociate, 
            "IPL match (" + qIpl + ") must outrank Bilateral Associate match (" + qAssociate + ")");
    }

    @Test
    void testIntraCompetitionOrdering_FullMembersVsAssociates() {
        // IND vs AUS in World Cup
        MatchDto indAus = new MatchDto();
        indAus.setName("India vs Australia, Group Stage, ICC Men's T20 World Cup, 2026");
        indAus.setBattingTeam("India");
        indAus.setBattingTeamShort("IND");
        indAus.setBowlingTeam("Australia");
        indAus.setBowlingTeamShort("AUS");
        indAus.setMatchType("T20");

        // USA vs CAN in World Cup
        MatchDto usaCan = new MatchDto();
        usaCan.setName("USA vs Canada, Group Stage, ICC Men's T20 World Cup, 2026");
        usaCan.setBattingTeam("United States");
        usaCan.setBattingTeamShort("USA");
        usaCan.setBowlingTeam("Canada");
        usaCan.setBowlingTeamShort("CAN");
        usaCan.setMatchType("T20");

        int qIndAus = provider.calculateQualityScore(indAus);
        int qUsaCan = provider.calculateQualityScore(usaCan);

        assertTrue(qIndAus > qUsaCan, 
            "Within World Cup, IND vs AUS (" + qIndAus + ") must rank higher than USA vs CAN (" + qUsaCan + ")");
        assertEquals(5000, provider.calculateCompetitionScore(indAus));
        assertEquals(5000, provider.calculateCompetitionScore(usaCan));
    }

    @Test
    void testStageBonus_FinalRanksAboveLeague() {
        MatchDto finalMatch = new MatchDto();
        finalMatch.setName("Sydney Sixers vs Brisbane Heat, Final, Big Bash League 2026");
        finalMatch.setMatchType("T20");

        MatchDto leagueMatch = new MatchDto();
        leagueMatch.setName("Sydney Sixers vs Brisbane Heat, Match 12, Big Bash League 2026");
        leagueMatch.setMatchType("T20");

        int qFinal = provider.calculateQualityScore(finalMatch);
        int qLeague = provider.calculateQualityScore(leagueMatch);

        assertTrue(qFinal > qLeague, "Final (" + qFinal + ") must outrank league match (" + qLeague + ")");
        assertEquals(50, provider.calculateStageScore(finalMatch));
        assertEquals(0, provider.calculateStageScore(leagueMatch));
    }

    @Test
    void testUpcomingOrdering_StartTimeProximityFirst() {
        // Match A: Regional match (Tier 5), starting in 1 hour
        MatchDto m1 = new MatchDto();
        m1.setId("m1");
        m1.setName("Shivamogga vs Coastal, Maharaja Trophy KSCA T20 2026");
        m1.setDateTimeGMT("2026-08-15T13:00:00");
        m1.setMatchStarted(false);
        m1.setMatchEnded(false);

        // Match B: County Match (Tier 4), starting in 1 hour (same time slot as m1)
        MatchDto m2 = new MatchDto();
        m2.setId("m2");
        m2.setName("Somerset vs Essex, County Championship 2026");
        m2.setDateTimeGMT("2026-08-15T13:00:00");
        m2.setMatchStarted(false);
        m2.setMatchEnded(false);

        // Match C: Premier Franchise IPL Final (Tier 2), starting in 3 days
        MatchDto m3 = new MatchDto();
        m3.setId("m3");
        m3.setName("Chennai Super Kings vs Mumbai Indians, Final, Indian Premier League 2026");
        m3.setDateTimeGMT("2026-08-18T19:30:00");
        m3.setMatchStarted(false);
        m3.setMatchEnded(false);

        // Match D: World Cup Final (Tier 1), starting in 5 days
        MatchDto m4 = new MatchDto();
        m4.setId("m4");
        m4.setName("India vs Australia, Final, ICC Men's T20 World Cup, 2026");
        m4.setDateTimeGMT("2026-08-20T19:30:00");
        m4.setMatchStarted(false);
        m4.setMatchEnded(false);

        List<MatchDto> list = new ArrayList<>(List.of(m4, m3, m1, m2));
        
        // Sort using the same comparator logic
        list.sort((a, b) -> {
            int state1 = provider.calculateStateScore(a);
            int state2 = provider.calculateStateScore(b);
            if (state1 != state2) return Integer.compare(state2, state1);

            java.time.LocalDateTime t1 = a.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(a.getDateTimeGMT()) : null;
            java.time.LocalDateTime t2 = b.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(b.getDateTimeGMT()) : null;
            int q1 = provider.calculateQualityScore(a);
            int q2 = provider.calculateQualityScore(b);

            if (t1 != null && t2 != null && !t1.equals(t2)) {
                return t1.compareTo(t2); // Ascending time
            }
            if (q1 != q2) return Integer.compare(q2, q1);
            return a.getId().compareTo(b.getId());
        });

        // Expected order:
        // 1. m2 (County Championship starting today at 13:00) -> Higher quality than m1 for same start time
        // 2. m1 (Maharaja Trophy starting today at 13:00) -> Imminent start
        // 3. m3 (IPL Final starting Aug 18)
        // 4. m4 (World Cup Final starting Aug 20)
        assertEquals("m2", list.get(0).getId(), "Earliest time slot with higher quality should be first");
        assertEquals("m1", list.get(1).getId(), "Earliest time slot with lower quality should be second");
        assertEquals("m3", list.get(2).getId(), "Later match (Aug 18) should be third");
        assertEquals("m4", list.get(3).getId(), "Furthest match (Aug 20) should be fourth");
    }

    @Test
    void testLiveOrdering_CompetitionImportanceDominates() {
        // LIVE Match 1: NED vs NEP in World Cup (Tier 1)
        MatchDto m1 = new MatchDto();
        m1.setId("live-wc");
        m1.setName("Netherlands vs Nepal, Group D, ICC Men's T20 World Cup, 2026");
        m1.setDateTimeGMT("2026-08-15T09:00:00");
        m1.setMatchStarted(true);
        m1.setMatchEnded(false);

        // LIVE Match 2: IND vs AUS in Bilateral (Tier 3)
        MatchDto m2 = new MatchDto();
        m2.setId("live-bilateral");
        m2.setName("India vs Australia, 3rd T20I, Australia tour of India, 2026");
        m2.setDateTimeGMT("2026-08-15T11:00:00");
        m2.setMatchStarted(true);
        m2.setMatchEnded(false);

        List<MatchDto> list = new ArrayList<>(List.of(m2, m1));
        list.sort((a, b) -> {
            int state1 = provider.calculateStateScore(a);
            int state2 = provider.calculateStateScore(b);
            if (state1 != state2) return Integer.compare(state2, state1);

            java.time.LocalDateTime t1 = a.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(a.getDateTimeGMT()) : null;
            java.time.LocalDateTime t2 = b.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(b.getDateTimeGMT()) : null;
            int q1 = provider.calculateQualityScore(a);
            int q2 = provider.calculateQualityScore(b);

            if (q1 != q2) return Integer.compare(q2, q1); // Descending quality
            if (t1 != null && t2 != null && !t1.equals(t2)) return t2.compareTo(t1);
            return a.getId().compareTo(b.getId());
        });

        assertEquals("live-wc", list.get(0).getId(), "In LIVE, Tier 1 World Cup must outrank Tier 3 Bilateral");
        assertEquals("live-bilateral", list.get(1).getId());
    }

    @Test
    void testRecentOrdering_QualityThenNewestCompletion() {
        // RECENT Match 1: IPL Final (Tier 2), ended yesterday
        MatchDto m1 = new MatchDto();
        m1.setId("rec-ipl-final");
        m1.setName("CSK vs RCB, Final, Indian Premier League 2026");
        m1.setStatus("CSK won by 5 wickets");
        m1.setDateTimeGMT("2026-08-14T19:30:00");
        m1.setMatchStarted(true);
        m1.setMatchEnded(true);

        // RECENT Match 2: Bilateral Match (Tier 3), ended 1 hour ago
        MatchDto m2 = new MatchDto();
        m2.setId("rec-bilateral");
        m2.setName("England vs South Africa, 3rd ODI 2026");
        m2.setStatus("England won by 20 runs");
        m2.setDateTimeGMT("2026-08-15T10:00:00");
        m2.setMatchStarted(true);
        m2.setMatchEnded(true);

        // RECENT Match 3: IPL Group Match (Tier 2), ended 2 hours ago
        MatchDto m3 = new MatchDto();
        m3.setId("rec-ipl-group");
        m3.setName("MI vs KKR, Match 45, Indian Premier League 2026");
        m3.setStatus("MI won by 10 runs");
        m3.setDateTimeGMT("2026-08-15T09:00:00");
        m3.setMatchStarted(true);
        m3.setMatchEnded(true);

        List<MatchDto> list = new ArrayList<>(List.of(m2, m3, m1));
        list.sort((a, b) -> {
            int state1 = provider.calculateStateScore(a);
            int state2 = provider.calculateStateScore(b);
            if (state1 != state2) return Integer.compare(state2, state1);

            java.time.LocalDateTime t1 = a.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(a.getDateTimeGMT()) : null;
            java.time.LocalDateTime t2 = b.getDateTimeGMT() != null ? java.time.LocalDateTime.parse(b.getDateTimeGMT()) : null;
            int q1 = provider.calculateQualityScore(a);
            int q2 = provider.calculateQualityScore(b);

            if (q1 != q2) return Integer.compare(q2, q1); // Descending quality
            if (t1 != null && t2 != null && !t1.equals(t2)) return t2.compareTo(t1);
            return a.getId().compareTo(b.getId());
        });

        // Expected order:
        // 1. rec-ipl-final (Tier 2 + Final bonus = 4050 pts)
        // 2. rec-ipl-group (Tier 2 + Group = 4000 pts)
        // 3. rec-bilateral (Tier 3 = 3000 pts)
        assertEquals("rec-ipl-final", list.get(0).getId(), "IPL Final (Tier 2 + Final) should rank first in recent");
        assertEquals("rec-ipl-group", list.get(1).getId(), "IPL Group (Tier 2) should rank second in recent");
        assertEquals("rec-bilateral", list.get(2).getId(), "Bilateral (Tier 3) should rank third in recent");
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
