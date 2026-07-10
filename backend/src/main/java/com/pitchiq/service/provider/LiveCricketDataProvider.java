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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static class CacheEntry<T> {
        final T data;
        final long timestamp;
        CacheEntry(T data) { this.data = data; this.timestamp = System.currentTimeMillis(); }
    }

    private final Map<String, CacheEntry<MatchDto>> matchDetailsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<MatchDto>>> liveMatchesCache = new ConcurrentHashMap<>();
    
    private static final long LIST_CACHE_TTL_MS = 60000; // 60 seconds
    private static final long DETAILS_CACHE_TTL_MS = 45000; // 45 seconds

    @Override
    public List<MatchDto> getLiveMatches() {
        CacheEntry<List<MatchDto>> entry = liveMatchesCache.get("LIVE_LIST");
        if (entry != null && (System.currentTimeMillis() - entry.timestamp) < LIST_CACHE_TTL_MS) {
            return entry.data;
        }

        Map<String, MatchDto> matchMap = new java.util.LinkedHashMap<>();
        try {
            // 1. Fetch live/current matches (Live and very recent)
            List<MatchDto> current = fetchAndParseList("v1/currentMatches", 0);
            for (MatchDto match : current) {
                matchMap.put(match.getId(), match);
            }
            
            // 2. Fetch priority upcoming matches from cricScore
            List<MatchDto> upcoming = fetchAndParseCricScoreList();
            for (MatchDto match : upcoming) {
                matchMap.putIfAbsent(match.getId(), match);
            }

            // 3. Fetch general matches to ensure we have recently completed results that dropped off currentMatches
            List<MatchDto> historical = fetchAndParseList("v1/matches", 0);
            for (MatchDto match : historical) {
                matchMap.putIfAbsent(match.getId(), match);
            }
            
            List<MatchDto> allMatches = new ArrayList<>(matchMap.values());
            
            // Filter stale upcoming matches
            LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
            allMatches.removeIf(match -> {
                if (!match.isMatchStarted() && !match.isMatchEnded()) {
                    LocalDateTime matchTime = parseMatchTime(match.getDateTimeGMT());
                    if (matchTime != null && matchTime.isBefore(nowUtc.minusHours(24))) {
                        log.warn("[PitchIQ] Filtering stale upcoming match: ID={}, Name={}, Time={}, Started={}, Ended={}", 
                            match.getId(), match.getName(), match.getDateTimeGMT(), match.isMatchStarted(), match.isMatchEnded());
                        return true;
                    }
                }
                return false;
            });
            
            // Deterministic Sorting:
            // 1. State Priority: Live > Recent (Completed) > Upcoming
            // 2. Quality Priority: International > Major Franchise > Domestic
            // 3. Time Priority: 
            //    - Recent: Newest first
            //    - Upcoming: Earliest first
            allMatches.sort((m1, m2) -> {
                int score1 = calculateStateScore(m1);
                int score2 = calculateStateScore(m2);
                
                if (score1 != score2) {
                    return Integer.compare(score2, score1); // Descending (higher score first)
                }
                
                int q1 = calculateQualityScore(m1);
                int q2 = calculateQualityScore(m2);
                
                if (q1 != q2) {
                    return Integer.compare(q2, q1); // Descending
                }
                
                // Chronological fallback based on state
                LocalDateTime t1 = parseMatchTime(m1.getDateTimeGMT());
                LocalDateTime t2 = parseMatchTime(m2.getDateTimeGMT());
                
                if (t1 != null && t2 != null) {
                    if (m1.isMatchEnded()) {
                        // Completed: Newest first (Descending)
                        return t2.compareTo(t1);
                    } else {
                        // Upcoming or Live: Earliest first (Ascending)
                        return t1.compareTo(t2);
                    }
                }
                return 0;
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

    private int calculateStateScore(MatchDto match) {
        boolean isStumps = match.getStatus() != null && 
            (match.getStatus().toLowerCase().contains("stump") || match.getStatus().toLowerCase().contains("day "));
            
        if (!match.isMatchEnded() && (match.isMatchStarted() || isStumps)) {
            return 3; // LIVE
        } else if (match.isMatchEnded()) {
            return 2; // RECENT
        } else {
            return 1; // UPCOMING
        }
    }

    private int calculateQualityScore(MatchDto match) {
        String name = (match.getName() != null ? match.getName() : "").toLowerCase();
        String t1 = (match.getBattingTeam() != null ? match.getBattingTeam() : "").toLowerCase();
        String t2 = (match.getBowlingTeam() != null ? match.getBowlingTeam() : "").toLowerCase();
        String full = name + " " + t1 + " " + t2;

        // International Formats / Major Tournaments
        if (full.contains("odi") || full.contains("test") || full.contains("t20i") || 
            full.contains("world cup") || full.contains("champions trophy") || full.contains("asia cup")) {
            return 3; 
        }
        
        // Major Franchise Leagues
        if (full.contains("ipl") || full.contains("indian premier league") || 
            full.contains("bbl") || full.contains("big bash") || 
            full.contains("psl") || full.contains("super league") ||
            full.contains("sa20") || full.contains("the hundred") || 
            full.contains("cpl") || full.contains("caribbean") ||
            full.contains("ilt20") || full.contains("mlc")) {
            return 2;
        }

        return 1; // Domestic / Other
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
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        
        List<MatchDto> matchList = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response.getBody());
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
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        
        List<MatchDto> matchList = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response.getBody());
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
                
                if (status.startsWith("Match starts at ")) {
                    dto.setMatchStarted(false);
                    dto.setMatchEnded(false);
                } else if (status.toLowerCase().contains("won by") || status.toLowerCase().contains("drawn") || 
                           status.toLowerCase().contains("abandoned") || status.toLowerCase().contains("no result")) {
                    dto.setMatchStarted(true);
                    dto.setMatchEnded(true);
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
        
        String venue = "";
        JsonNode venueInfo = matchNode.path("venueInfo");
        if (!venueInfo.isMissingNode() && !venueInfo.isNull()) {
            String ground = venueInfo.path("ground").asText("").trim();
            String city = venueInfo.path("city").asText("").trim();
            if (!ground.isEmpty() && !city.isEmpty()) {
                venue = ground + ", " + city;
            } else if (!ground.isEmpty()) {
                venue = ground;
            } else if (!city.isEmpty()) {
                venue = city;
            }
        }
        
        if (venue.isEmpty()) {
            venue = matchNode.path("venue").asText("").trim();
        }
        
        if (venue.isEmpty() || venue.equalsIgnoreCase("TBC, TBC") || venue.equalsIgnoreCase("TBC") || venue.equalsIgnoreCase("TBA")) {
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
        
        dto.setStatus(status);
        dto.setMatchStarted(matchNode.path("matchStarted").asBoolean());
        dto.setMatchEnded(matchNode.path("matchEnded").asBoolean());
        
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
}
