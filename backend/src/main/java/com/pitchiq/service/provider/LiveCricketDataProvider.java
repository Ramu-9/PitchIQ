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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // Fetch live/current matches (only 1 page needed to avoid wasting API)
            List<MatchDto> current = fetchAndParseList("v1/currentMatches", 0);
            for (MatchDto match : current) {
                matchMap.put(match.getId(), match);
            }
            
            // Fetch priority matches from cricScore (1 API hit, returns international priority matches)
            List<MatchDto> upcoming = fetchAndParseCricScoreList();
            for (MatchDto match : upcoming) {
                matchMap.putIfAbsent(match.getId(), match);
            }
            
            
            List<MatchDto> allMatches = new ArrayList<>(matchMap.values());
            
            List<String> priorityTeams = List.of("India", "Australia", "England", "South Africa", "New Zealand", "Pakistan", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan", "Zimbabwe", "Ireland", "IND", "AUS", "ENG", "SA", "NZ", "PAK", "SL", "WI", "BAN", "AFG", "ZIM", "IRE");
            
            allMatches.sort((m1, m2) -> {
                boolean m1Priority = priorityTeams.stream().anyMatch(t -> (m1.getBattingTeam() != null && m1.getBattingTeam().contains(t)) || (m1.getBowlingTeam() != null && m1.getBowlingTeam().contains(t)) || (m1.getName() != null && m1.getName().contains(t)));
                boolean m2Priority = priorityTeams.stream().anyMatch(t -> (m2.getBattingTeam() != null && m2.getBattingTeam().contains(t)) || (m2.getBowlingTeam() != null && m2.getBowlingTeam().contains(t)) || (m2.getName() != null && m2.getName().contains(t)));
                
                if (m1Priority && !m2Priority) return -1;
                if (!m1Priority && m2Priority) return 1;
                
                // Chronological sort
                if (m1.getDateTimeGMT() != null && m2.getDateTimeGMT() != null) {
                    try {
                        LocalDateTime t1 = LocalDateTime.parse(m1.getDateTimeGMT());
                        LocalDateTime t2 = LocalDateTime.parse(m2.getDateTimeGMT());
                        return t1.compareTo(t2);
                    } catch (Exception e) {
                        return 0;
                    }
                }
                
                return 0; // Stable fallback
            });
            
            liveMatchesCache.put("LIVE_LIST", new CacheEntry<>(allMatches));
            return allMatches;
        } catch (Exception e) {
            log.error("Failed to fetch matches from CricAPI: {}", e.getMessage());
            // Fallback to cached data if API fails
            return entry != null ? entry.data : new ArrayList<>();
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
            return entry != null ? entry.data : null;
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
                dto.setVenue("TBC, TBC"); 
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
        
        dto.setVenue(matchNode.path("venue").asText(""));
        
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
