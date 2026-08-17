package com.pitchiq.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.dto.MatchDto;
import com.pitchiq.dto.ScoreDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "pitchiq.cricket.provider", havingValue = "live")
public class LiveCricketDataProvider implements CricketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveCricketDataProvider.class);

    @Value("${cricapi.key}")
    private String apiKey;

    @Value("${cricapi.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LiveCricketDataProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        this.restTemplate = new RestTemplate(factory);
    }

    public static boolean isTerminalStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return false;
        }
        String s = status.toLowerCase();
        return (s.contains("won by") && !s.contains("toss won by")) ||
               s.contains("lost by") ||
               s.contains("draw") ||
               s.contains("drawn") ||
               s.contains("tie") ||
               s.contains("tied") ||
               s.contains("no result") ||
               s.contains("abandoned") ||
               s.contains("cancelled") ||
               s.contains("canceled") ||
               s.contains("awarded") ||
               s.contains("match ended") ||
               s.contains("refused to play") ||
               s.contains("conceded") ||
               s.contains("walkover") ||
               s.contains("concluded") ||
               s.contains("postponed");
    }

    private static class CacheEntry<T> {
        final T data;
        final long timestamp;
        CacheEntry(T data) { this.data = data; this.timestamp = System.currentTimeMillis(); }
    }

    private final Map<String, CacheEntry<MatchDto>> matchDetailsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<MatchDto>>> liveMatchesCache = new ConcurrentHashMap<>();
    
    private static final long LIST_CACHE_TTL_MS = 60000; // 60 seconds
    private static final long DETAILS_CACHE_TTL_MS = 45000; // 45 seconds

    public String getRawMatches(String endpoint) {
        String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + endpoint + "?apikey=" + apiKey;
        try {
            return restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public List<MatchDto> getLiveMatches() {
        CacheEntry<List<MatchDto>> entry = liveMatchesCache.get("LIVE_LIST");
        if (entry != null && (System.currentTimeMillis() - entry.timestamp) < LIST_CACHE_TTL_MS) {
            return entry.data;
        }

        Map<String, MatchDto> matchMap = new java.util.LinkedHashMap<>();
        try {
            // Step 1: Parallelize the CricAPI requests concurrently via CompletableFuture
            CompletableFuture<List<MatchDto>> currentFuture0 = CompletableFuture.supplyAsync(() -> fetchAndParseListSafely("v1/currentMatches", 0));
            CompletableFuture<List<MatchDto>> currentFuture1 = CompletableFuture.supplyAsync(() -> fetchAndParseListSafely("v1/currentMatches", 25));

            CompletableFuture<List<MatchDto>> upcomingFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchAndParseCricScoreList();
                } catch (Exception e) {
                    log.warn("[PitchIQ] Failed to fetch cricScore: {}", e.getMessage());
                    return Collections.emptyList();
                }
            });

            CompletableFuture<List<MatchDto>> historicalFuture0 = CompletableFuture.supplyAsync(() -> fetchAndParseListSafely("v1/matches", 0));
            CompletableFuture<List<MatchDto>> historicalFuture1 = CompletableFuture.supplyAsync(() -> fetchAndParseListSafely("v1/matches", 25));

            CompletableFuture.allOf(currentFuture0, currentFuture1, upcomingFuture, historicalFuture0, historicalFuture1).join();

            List<MatchDto> current = new ArrayList<>(currentFuture0.join());
            current.addAll(currentFuture1.join());
            
            List<MatchDto> upcoming = upcomingFuture.join();
            
            List<MatchDto> historical = new ArrayList<>(historicalFuture0.join());
            historical.addAll(historicalFuture1.join());

            // 1. Current matches take priority
            for (MatchDto match : current) {
                matchMap.put(match.getId(), match);
            }
            
            // 2. CricScore matches
            for (MatchDto match : upcoming) {
                matchMap.putIfAbsent(match.getId(), match);
            }

            // 3. Historical matches
            for (MatchDto match : historical) {
                matchMap.putIfAbsent(match.getId(), match);
            }
            
            List<MatchDto> allMatches = new ArrayList<>(matchMap.values());
            
            // Filter stale upcoming matches (start time has passed and match has not started or completed)
            LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
            allMatches.removeIf(match -> {
                // If match is already completed or terminal, do not remove (it belongs in Recent)
                if (match.isMatchEnded() || isTerminalStatus(match.getStatus())) {
                    return false;
                }
                // If match is not started (upcoming candidate) but has no score data
                if (!match.isMatchStarted() && (match.getScores() == null || match.getScores().isEmpty())) {
                    LocalDateTime matchTime = parseMatchTime(match.getDateTimeGMT());
                    // Scheduled start time has already passed without match starting
                    if (matchTime != null && matchTime.isBefore(nowUtc)) {
                        log.warn("[PitchIQ] Filtering stale upcoming match whose start time has passed: ID={}, Name={}, Time={}", 
                            match.getId(), match.getName(), match.getDateTimeGMT());
                        return true;
                    }
                }
                return false;
            });
            
            // Deduplicate matches (CricAPI sometimes returns multiple entries for the same match with different statuses)
            Map<String, MatchDto> uniqueMatches = new HashMap<>();
            for (MatchDto m : allMatches) {
                if (m.getName() == null) continue;
                String datePart = m.getDateTimeGMT() != null && m.getDateTimeGMT().contains("T") 
                                  ? m.getDateTimeGMT().substring(0, m.getDateTimeGMT().indexOf('T')) 
                                  : "";
                String dedupeKey = m.getName().trim() + "_" + datePart;
                
                // If a duplicate exists, keep the one that has scores or is already marked as started
                if (uniqueMatches.containsKey(dedupeKey)) {
                    MatchDto existing = uniqueMatches.get(dedupeKey);
                    boolean currentHasScores = m.getScores() != null && !m.getScores().isEmpty();
                    boolean existingHasScores = existing.getScores() != null && !existing.getScores().isEmpty();
                    
                    if (currentHasScores && !existingHasScores) {
                        uniqueMatches.put(dedupeKey, m);
                    } else if (m.isMatchStarted() && !existing.isMatchStarted()) {
                        uniqueMatches.put(dedupeKey, m);
                    }
                } else {
                    uniqueMatches.put(dedupeKey, m);
                }
            }
            allMatches = new ArrayList<>(uniqueMatches.values());
            
            // Deterministic Sorting:
            // 1. Strict State Separation: Live (3) > Recent (2) > Upcoming (1)
            // 2. State-specific intra-state ranking rules:
            //    - LIVE: Competition & Quality first, then newest start time
            //    - RECENT: Competition & Quality first, then newest completion time
            //    - UPCOMING: Start-time proximity first (earliest start first), then Quality for equal slots
            allMatches.sort((m1, m2) -> {
                int state1 = calculateStateScore(m1);
                int state2 = calculateStateScore(m2);
                
                if (state1 != state2) {
                    return Integer.compare(state2, state1); // Descending (higher score first: LIVE(3) > RECENT(2) > UPCOMING(1))
                }
                
                LocalDateTime t1 = parseMatchTime(m1.getDateTimeGMT());
                LocalDateTime t2 = parseMatchTime(m2.getDateTimeGMT());
                int q1 = calculateQualityScore(m1);
                int q2 = calculateQualityScore(m2);
                
                if (state1 == 3) {
                    // LIVE: Competition importance as primary intra-state factor, followed by team, stage, format, then time
                    if (q1 != q2) {
                        return Integer.compare(q2, q1); // Descending quality
                    }
                    if (t1 != null && t2 != null && !t1.equals(t2)) {
                        return t2.compareTo(t1); // Descending time (most recently started)
                    }
                } else if (state1 == 2) {
                    // RECENT: Prioritize competition importance and then newest completion time
                    if (q1 != q2) {
                        return Integer.compare(q2, q1); // Descending quality
                    }
                    if (t1 != null && t2 != null && !t1.equals(t2)) {
                        return t2.compareTo(t1); // Descending time (newest result first)
                    }
                } else {
                    // UPCOMING: Prioritize quality first, then start-time proximity
                    if (q1 != q2) {
                        return Integer.compare(q2, q1); // Descending quality
                    }
                    if (t1 != null && t2 != null && !t1.equals(t2)) {
                        return t1.compareTo(t2); // Ascending time (earliest / most imminent start first)
                    } else if (t1 != null && t2 == null) {
                        return -1;
                    } else if (t1 == null && t2 != null) {
                        return 1;
                    }
                }
                
                // Deterministic fallback (guarantees stable sorting)
                String id1 = m1.getId() != null ? m1.getId() : "";
                String id2 = m2.getId() != null ? m2.getId() : "";
                return id1.compareTo(id2);
            });
            
            liveMatchesCache.put("LIVE_LIST", new CacheEntry<>(allMatches));
            return allMatches;
        } catch (Exception e) {
            log.error("Failed to fetch matches from CricAPI: {}", e.getMessage());
            if (entry != null) {
                return entry.data;
            }
            return new ArrayList<>();
        }
    }

    private List<MatchDto> fetchAndParseListSafely(String endpoint, int offset) {
        try {
            return fetchAndParseList(endpoint, offset);
        } catch (Exception e) {
            log.warn("[PitchIQ] Failed to fetch {} at offset {}: {}", endpoint, offset, e.getMessage());
            return Collections.emptyList();
        }
    }

    public int calculateStateScore(MatchDto match) {
        boolean isTerminal = isTerminalStatus(match.getStatus());
        if (isTerminal || match.isMatchEnded()) {
            return 2; // RECENT / COMPLETED
        }

        boolean isStumps = match.getStatus() != null && 
            (match.getStatus().toLowerCase().contains("stump") || match.getStatus().toLowerCase().contains("day "));
            
        if (match.isMatchStarted() || isStumps) {
            return 3; // LIVE
        } else {
            return 1; // UPCOMING
        }
    }

    private static final java.util.Set<String> FULL_MEMBERS = java.util.Set.of(
        "IND", "AUS", "ENG", "SA", "NZ", "PAK", "SL", "BAN", "WI", "AFG", "IRE", "ZIM",
        "IND-W", "AUS-W", "ENG-W", "SA-W", "NZ-W", "PAK-W", "SL-W", "BAN-W", "WI-W", "AFG-W", "IRE-W", "ZIM-W"
    );

    private static final java.util.Set<String> WC_ASSOCIATES = java.util.Set.of(
        "USA", "SCO", "NED", "NAM", "NEP", "OMA", "UAE", "PNG", "CAN", "UGA", "KEN", "THA",
        "USA-W", "SCO-W", "NED-W", "NAM-W", "NEP-W", "OMA-W", "UAE-W", "PNG-W", "CAN-W", "UGA-W", "KEN-W", "THA-W"
    );

    public int calculateQualityScore(MatchDto match) {
        int compScore = calculateCompetitionScore(match);
        int teamScore = calculateTeamScore(match);
        int stageScore = calculateStageScore(match);
        int formatScore = calculateFormatScore(match);
        return compScore + teamScore + stageScore + formatScore;
    }

    public int calculateCompetitionScore(MatchDto match) {
        String name = (match.getName() != null ? match.getName() : "").toLowerCase();

        // Tier 1 (5,000 pts): Global ICC Events
        if (name.contains("world cup") || name.contains("t20 world cup") || name.contains("cricket world cup") ||
            name.contains("icc world cup") || name.contains("champions trophy") || 
            name.contains("world test championship") || name.contains("wtc final") || 
            name.contains("under-19 world cup") || name.contains("u19 world cup") ||
            name.contains("icc men") || name.contains("icc women")) {
            return 5000;
        }

        // Tier 2 (4,000 pts): Premier Global Franchise Leagues & Continental Cups
        if (name.contains("indian premier league") || name.matches(".*\\bipl\\b.*") ||
            name.contains("women's premier league") || name.matches(".*\\bwpl\\b.*") ||
            name.contains("big bash") || name.matches(".*\\b(bbl|wbbl)\\b.*") ||
            name.contains("the hundred") || name.contains("sa20") ||
            name.contains("pakistan super league") || name.matches(".*\\bpsl\\b.*") ||
            name.contains("caribbean premier league") || name.matches(".*\\bcpl\\b.*") ||
            name.contains("major league cricket") || name.matches(".*\\bmlc\\b.*") ||
            name.contains("international league t20") || name.matches(".*\\bilt20\\b.*") ||
            name.contains("super smash") || name.contains("bangladesh premier league") || name.matches(".*\\bbpl\\b.*") ||
            name.contains("asia cup")) {
            return 4000;
        }

        // Tier 5 (1,000 pts): Regional / State Tournaments (checked before bilateral fallback)
        if (name.contains("maharaja trophy") || name.contains("ksca") ||
            name.contains("tamil nadu premier league") || name.contains("tnpl") ||
            name.contains("duleep trophy") || name.contains("deodhar trophy") ||
            name.contains("irani cup") || name.contains("andhra premier league") || name.contains("apl") ||
            name.contains("lanka premier league") || name.contains("lpl") ||
            name.contains("kpl")) {
            return 1000;
        }

        // Tier 4 (2,000 pts): Premier National Domestic Competitions
        if (name.contains("ranji trophy") || name.contains("county championship") ||
            name.contains("sheffield shield") || name.contains("vitality blast") || name.contains("t20 blast") ||
            name.contains("syed mushtaq ali") || name.contains("smat") || name.contains("vijay hazare") ||
            name.contains("marsh one-day") || name.contains("marsh cup") || name.contains("super50") ||
            name.contains("ford trophy") || name.contains("plunket shield")) {
            return 2000;
        }

        // Tier 3 (3,000 pts): Bilateral International Series
        if (name.matches(".*\\b(t20i|odi|test|t20 international|one day international|ashes|border-gavaskar)\\b.*") ||
            name.contains("tour of") || isInternationalMatch(match)) {
            return 3000;
        }

        return 0; // Tier 6: Minor / Unclassified
    }

    private boolean isInternationalMatch(MatchDto match) {
        String t1 = match.getBattingTeamShort() != null ? match.getBattingTeamShort() : "";
        String t2 = match.getBowlingTeamShort() != null ? match.getBowlingTeamShort() : "";
        return getTeamTier(t1, match.getBattingTeam()) > 0 || getTeamTier(t2, match.getBowlingTeam()) > 0;
    }

    public int getTeamTier(String shortName, String fullName) {
        String s = (shortName != null ? shortName : "").toUpperCase().trim();
        if (FULL_MEMBERS.contains(s)) {
            return 2; // Full Member
        }
        if (WC_ASSOCIATES.contains(s)) {
            return 1; // World Cup Associate
        }

        String f = (fullName != null ? fullName : "").toLowerCase().trim();
        if (f.contains("india") || f.contains("australia") || f.contains("england") ||
            f.contains("south africa") || f.contains("new zealand") || f.contains("pakistan") ||
            f.contains("sri lanka") || f.contains("bangladesh") || f.contains("west indies") ||
            f.contains("afghanistan") || f.contains("ireland") || f.contains("zimbabwe")) {
            return 2;
        }
        if (f.contains("united states") || f.contains("scotland") || f.contains("netherlands") ||
            f.contains("namibia") || f.contains("nepal") || f.contains("oman") ||
            f.contains("emirates") || f.contains("papua new guinea") || f.contains("canada") ||
            f.contains("uganda") || f.contains("kenya") || f.contains("thailand")) {
            return 1;
        }
        return 0;
    }

    public int calculateTeamScore(MatchDto match) {
        int t1Tier = getTeamTier(match.getBattingTeamShort(), match.getBattingTeam());
        int t2Tier = getTeamTier(match.getBowlingTeamShort(), match.getBowlingTeam());

        if (t1Tier == 2 && t2Tier == 2) {
            return 80; // Two Full Members
        } else if ((t1Tier == 2 && t2Tier == 1) || (t1Tier == 1 && t2Tier == 2)) {
            return 50; // Full Member vs WC Associate
        } else if (t1Tier == 1 && t2Tier == 1) {
            return 35; // Two WC Associates
        } else if (t1Tier > 0 || t2Tier > 0) {
            return 20; // One Full Member / Associate vs other
        }
        return 0;
    }

    public int calculateStageScore(MatchDto match) {
        String name = (match.getName() != null ? match.getName() : "").toLowerCase();
        if (name.contains("final") && !name.contains("semi-final") && !name.contains("semifinal") && !name.contains("semi final") && !name.contains("quarter-final")) {
            return 50;
        }
        if (name.contains("semi-final") || name.contains("semifinal") || name.contains("semi final") ||
            name.contains("qualifier") || name.contains("eliminator") || name.contains("super 8") ||
            name.contains("super 4") || name.contains("playoff") || name.contains("quarter-final")) {
            return 30;
        }
        return 0;
    }

    public int calculateFormatScore(MatchDto match) {
        String format = (match.getMatchType() != null ? match.getMatchType() : "").toUpperCase();
        String name = (match.getName() != null ? match.getName() : "").toLowerCase();

        if ("TEST".equals(format) || name.contains("test")) {
            return 20;
        }
        if ("ODI".equals(format) || name.contains("odi")) {
            return 15;
        }
        if ("T20".equals(format) || "T20I".equals(format) || name.contains("t20")) {
            return 10;
        }
        return 0;
    }

    private LocalDateTime parseMatchTime(String timeGMT) {
        if (timeGMT == null || timeGMT.isEmpty()) return null;
        try {
            return LocalDateTime.parse(timeGMT);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public MatchDto getMatchDetails(String matchId) {
        CacheEntry<MatchDto> entry = matchDetailsCache.get(matchId);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp) < DETAILS_CACHE_TTL_MS) {
            return entry.data;
        }

        String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "v1/match_info?apikey=" + apiKey + "&id=" + matchId;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            MatchDto match = parseSingleMatch(objectMapper.readTree(response.getBody()).path("data"));
            
            if (match != null) {
                matchDetailsCache.put(matchId, new CacheEntry<>(match));
            }
            return match;
        } catch (Exception e) {
            log.error("Failed to fetch match details from CricAPI: {}", e.getMessage());
            if (entry != null) {
                return entry.data;
            }
            return null; // Return null gracefully
        }
    }

    private List<MatchDto> fetchAndParseList(String endpoint, int offset) throws Exception {
        String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + endpoint + "?apikey=" + apiKey + "&offset=" + offset;
        
        String maskedUrl = url.replace(apiKey != null && !apiKey.isEmpty() ? apiKey : "empty", "***");
        log.info("[PitchIQ-Trace] Executing CricAPI Request to: {}", maskedUrl);
        
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            log.info("[PitchIQ-Trace] Response Code: {}", response.getStatusCode());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("[PitchIQ-Trace] HTTP Error from CricAPI: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("[PitchIQ-Trace] Exception before/during request: {}", e.getMessage(), e);
            throw e;
        }
        
        List<MatchDto> matchList = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response.getBody());
        
        if (root.has("status") && !"success".equalsIgnoreCase(root.path("status").asText())) {
            throw new Exception("CricAPI Error: " + root.path("info").asText("Unknown"));
        }
        
        JsonNode data = root.path("data");
        
        if (data.isArray()) {
            for (JsonNode matchNode : data) {
                MatchDto dto = parseSingleMatch(matchNode);
                if (dto != null) {
                    matchList.add(dto);
                }
            }
        }
        return matchList;
    }

    private List<MatchDto> fetchAndParseCricScoreList() throws Exception {
        String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "v1/cricScore?apikey=" + apiKey;
        
        String maskedUrl = url.replace(apiKey != null && !apiKey.isEmpty() ? apiKey : "empty", "***");
        log.info("[PitchIQ-Trace] Executing CricAPI CricScore Request to: {}", maskedUrl);
        
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            log.info("[PitchIQ-Trace] CricScore Response Code: {}", response.getStatusCode());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("[PitchIQ-Trace] HTTP Error from CricScore: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("[PitchIQ-Trace] Exception before/during CricScore request: {}", e.getMessage(), e);
            throw e;
        }
        
        List<MatchDto> matchList = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response.getBody());
        
        if (root.has("status") && !"success".equalsIgnoreCase(root.path("status").asText())) {
            throw new Exception("CricAPI Error: " + root.path("info").asText("Unknown"));
        }
        
        JsonNode data = root.path("data");
        
        if (data.isArray()) {
            for (JsonNode matchNode : data) {
                if (matchNode == null || matchNode.isMissingNode() || !matchNode.has("id")) continue;
                MatchDto dto = new MatchDto();
                dto.setId(matchNode.path("id").asText());
                String t1 = matchNode.path("t1").asText("Team A").replaceAll("\\s*\\[.*?\\]", "");
                String t2 = matchNode.path("t2").asText("Team B").replaceAll("\\s*\\[.*?\\]", "");
                dto.setBattingTeam(t1);
                dto.setBowlingTeam(t2);
                dto.setName(t1 + " vs " + t2);
                
                String t1s = matchNode.path("t1s").asText("").trim();
                String t2s = matchNode.path("t2s").asText("").trim();
                dto.setBattingTeamShort(sanitizeAbbreviation(t1s, t1));
                dto.setBowlingTeamShort(sanitizeAbbreviation(t2s, t2));
                
                String dateGMT = matchNode.path("dateTimeGMT").asText("");
                dto.setDateTimeGMT(dateGMT);
                
                String status = matchNode.path("status").asText("");
                if (status.startsWith("Match starts at ") && !dateGMT.isEmpty()) {
                    try {
                        LocalDateTime gmtTime = LocalDateTime.parse(dateGMT);
                        java.time.ZonedDateTime zonedDateTime = gmtTime.atZone(java.time.ZoneId.of("UTC"));
                        java.time.ZonedDateTime istTime = zonedDateTime.withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"));
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a 'IST'");
                        status = "Match starts at " + istTime.format(formatter);
                    } catch (Exception e) {}
                }
                dto.setStatus(status);
                dto.setVenue("Venue unavailable"); 
                dto.setMatchType(matchNode.path("matchType").asText("T20").toUpperCase());
                
                if (isTerminalStatus(status)) {
                    dto.setMatchStarted(true);
                    dto.setMatchEnded(true);
                } else if (status.startsWith("Match starts at ") || status.toLowerCase().contains("not started")) {
                    dto.setMatchStarted(false);
                    dto.setMatchEnded(false);
                } else {
                    dto.setMatchStarted(true);
                    dto.setMatchEnded(false);
                }
                
                dto.setScores(new ArrayList<>());
                matchList.add(dto);
            }
        }
        return matchList;
    }

    private MatchDto parseSingleMatch(JsonNode matchNode) {
        if (matchNode == null || matchNode.isMissingNode() || !matchNode.has("id")) return null;
        
        MatchDto dto = new MatchDto();
        dto.setId(matchNode.path("id").asText());
        dto.setName(matchNode.path("name").asText("Unknown Match"));
        
        String dateGMT = matchNode.path("dateTimeGMT").asText("");
        dto.setDateTimeGMT(dateGMT);
        
        // Handle premium date format mapping for status if it's upcoming
        String status = matchNode.path("status").asText("");
        if (status.startsWith("Match starts at ")) {
            if (!dateGMT.isEmpty()) {
                try {
                    LocalDateTime gmtTime = LocalDateTime.parse(dateGMT);
                    java.time.ZonedDateTime zonedDateTime = gmtTime.atZone(java.time.ZoneId.of("UTC"));
                    java.time.ZonedDateTime istTime = zonedDateTime.withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"));
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a 'IST'");
                    status = "Match starts at " + istTime.format(formatter);
                } catch (Exception e) {
                    // fallback
                }
            }
        }
        dto.setStatus(status);
        
        String rawVenue = matchNode.path("venue").asText("").trim();
        JsonNode venueInfo = matchNode.path("venueInfo");
        
        List<String> venueParts = new ArrayList<>();
        
        // 1. Add stadium/ground if available
        String ground = !venueInfo.isMissingNode() ? venueInfo.path("ground").asText("").trim() : "";
        if (!ground.isEmpty()) {
            venueParts.add(ground);
        } else if (!rawVenue.isEmpty()) {
            // If raw venue exists but ground doesn't, split rawVenue by comma and take the first part as ground
            String[] parts = rawVenue.split(",");
            venueParts.add(parts[0].trim());
        }
        
        // 2. Add city if available
        String city = !venueInfo.isMissingNode() ? venueInfo.path("city").asText("").trim() : "";
        if (!city.isEmpty() && venueParts.stream().noneMatch(p -> p.equalsIgnoreCase(city))) {
            venueParts.add(city);
        } else if (!rawVenue.isEmpty()) {
            // Fallback: check if city is in rawVenue
            String[] parts = rawVenue.split(",");
            if (parts.length > 1 && venueParts.stream().noneMatch(p -> p.equalsIgnoreCase(parts[1].trim()))) {
                venueParts.add(parts[1].trim());
            }
        }
        
        // 3. Add country if available
        String country = !venueInfo.isMissingNode() ? venueInfo.path("country").asText("").trim() : "";
        if (!country.isEmpty() && venueParts.stream().noneMatch(p -> p.equalsIgnoreCase(country))) {
            venueParts.add(country);
        }

        String venue = String.join(", ", venueParts);
        
        if (venue.isEmpty() || venue.equalsIgnoreCase("TBC, TBC") || venue.equalsIgnoreCase("TBC") || venue.equalsIgnoreCase("TBA") || venue.equalsIgnoreCase("Unknown") || venue.equalsIgnoreCase("null") || venue.equalsIgnoreCase("null, null")) {
            venue = "Venue unavailable";
        }
        
        dto.setVenue(venue);
        
        String matchType = matchNode.path("matchType").asText("");
        if (matchType.isEmpty()) {
            String nameLower = dto.getName().toLowerCase();
            if (nameLower.contains("odi")) {
                matchType = "ODI";
            } else if (nameLower.contains("test")) {
                matchType = "TEST";
            } else if (nameLower.contains("t10")) {
                matchType = "T10";
            } else {
                matchType = "T20"; // default fallback
            }
        }
        dto.setMatchType(matchType.toUpperCase());
        
        JsonNode teams = matchNode.path("teams");
        if (teams.isArray() && teams.size() >= 2) {
            dto.setBattingTeam(teams.get(0).asText());
            dto.setBowlingTeam(teams.get(1).asText());
        } else {
            dto.setBattingTeam("Team A");
            dto.setBowlingTeam("Team B");
        }
        
        JsonNode teamInfo = matchNode.path("teamInfo");
        String t1Short = "";
        String t2Short = "";
        if (teamInfo.isArray() && teamInfo.size() >= 2) {
            t1Short = teamInfo.get(0).path("shortname").asText("").trim();
            t2Short = teamInfo.get(1).path("shortname").asText("").trim();
        }
        
        dto.setBattingTeamShort(sanitizeAbbreviation(t1Short, dto.getBattingTeam()));
        dto.setBowlingTeamShort(sanitizeAbbreviation(t2Short, dto.getBowlingTeam()));
        
        dto.setStatus(status);
        boolean started = matchNode.path("matchStarted").asBoolean();
        boolean ended = matchNode.path("matchEnded").asBoolean();

        if (isTerminalStatus(status)) {
            started = true;
            ended = true;
        } else if (status.toLowerCase().contains("not started") || status.startsWith("Match starts at ")) {
            started = false;
            ended = false;
        }

        dto.setMatchStarted(started);
        dto.setMatchEnded(ended);
        
        List<ScoreDto> scores = new ArrayList<>();
        JsonNode scoreArray = matchNode.path("score");
        
        if (scoreArray.isArray()) {
            for (JsonNode scoreNode : scoreArray) {
                ScoreDto s = new ScoreDto();
                s.setRuns(scoreNode.path("r").asInt(0));
                s.setWickets(scoreNode.path("w").asInt(0));
                s.setOvers(scoreNode.path("o").asDouble(0.0));
                s.setInning(scoreNode.path("inning").asText(""));
                scores.add(s);
            }
            
            // Try to set accurate batting team based on the last inning's team name
            if (scores.size() > 0) {
                String inningStr = scores.get(scores.size() - 1).getInning();
                if (teams.isArray() && teams.size() >= 2) {
                    String t1 = teams.get(0).asText();
                    String t2 = teams.get(1).asText();
                    if (inningStr.toLowerCase().contains(t1.toLowerCase())) {
                        dto.setBattingTeam(t1);
                        dto.setBowlingTeam(t2);
                    } else if (inningStr.toLowerCase().contains(t2.toLowerCase())) {
                        dto.setBattingTeam(t2);
                        dto.setBowlingTeam(t1);
                    }
                }
            }
        }
        
        dto.setScores(scores);
        return dto;
    }

    private String sanitizeAbbreviation(String providedShortName, String fullTeamName) {
        if (fullTeamName == null) fullTeamName = "";
        String nameLower = fullTeamName.trim().toLowerCase();
        boolean isWomen = nameLower.matches(".*\\bwomen\\b.*") || nameLower.endsWith(" w");
        
        String abbr = getIccAbbreviation(fullTeamName);
        
        if (abbr == null) {
            if (providedShortName != null && !providedShortName.trim().isEmpty()) {
                abbr = providedShortName.trim().toUpperCase();
            } else {
                String baseName = fullTeamName.replaceAll("(?i)\\b(Women|W)\\b", "").trim();
                if (baseName.isEmpty()) {
                    abbr = "UNK";
                } else {
                    String[] parts = baseName.split("\\s+");
                    if (parts.length > 1 && parts[0].length() > 0 && parts[1].length() > 0) {
                        abbr = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
                    } else {
                        abbr = baseName.length() >= 3 ? baseName.substring(0, 3).toUpperCase() : baseName.toUpperCase();
                    }
                }
            }
        }
        
        if (isWomen) {
            abbr = abbr.replaceAll("(?i)\\s*-\\s*W$", "").replaceAll("(?i)\\s+W$", "");
            if (abbr.length() == 4 && abbr.toUpperCase().endsWith("W")) {
                abbr = abbr.substring(0, 3);
            }
            if (!abbr.endsWith("-W")) {
                abbr += "-W";
            }
        }
        
        return abbr;
    }

    private String getIccAbbreviation(String teamName) {
        if (teamName == null || teamName.isEmpty()) return null;
        String baseName = teamName.replaceAll("(?i)\\b(Women|W)\\b", "").trim().toLowerCase();
        
        switch (baseName) {
            case "india": return "IND";
            case "australia": return "AUS";
            case "england": return "ENG";
            case "new zealand": return "NZ";
            case "south africa": return "SA";
            case "pakistan": return "PAK";
            case "bangladesh": return "BAN";
            case "sri lanka": return "SL";
            case "west indies": return "WI";
            case "afghanistan": return "AFG";
            case "ireland": return "IRE";
            case "zimbabwe": return "ZIM";
            case "scotland": return "SCO";
            case "netherlands": return "NED";
            case "united arab emirates": case "uae": return "UAE";
            case "namibia": return "NAM";
            case "nepal": return "NEP";
            case "oman": return "OMA";
            case "papua new guinea": case "png": return "PNG";
            case "united states": case "usa": case "united states of america": return "USA";
            default: return null;
        }
    }
}
