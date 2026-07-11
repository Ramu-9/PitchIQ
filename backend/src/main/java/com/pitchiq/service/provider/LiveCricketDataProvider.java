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
                    if (m1.isMatchEnded() || m1.isMatchStarted()) {
                        // Completed or Live: Newest first (Descending)
                        return t2.compareTo(t1);
                    } else {
                        // Upcoming: Earliest first (Ascending)
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
        
        String t1Short = match.getBattingTeamShort() != null ? match.getBattingTeamShort().toUpperCase() : "";
        String t2Short = match.getBowlingTeamShort() != null ? match.getBowlingTeamShort().toUpperCase() : "";
        
        java.util.Set<String> bigTeams = java.util.Set.of("IND", "AUS", "ENG", "NZ", "SA", "PAK", "SL", "BAN", "WI", "AFG", "IRE", "ZIM", 
                                                          "IND-W", "AUS-W", "ENG-W", "NZ-W", "SA-W", "PAK-W", "SL-W", "BAN-W", "WI-W", "AFG-W", "IRE-W", "ZIM-W");
        
        if (bigTeams.contains(t1Short) || bigTeams.contains(t2Short)) {
            return 4; // Major International Teams
        }

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
            full.contains("ilt20") || full.contains("mlc") || full.contains("wpl")) {
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
        
        // Debug raw payload
        dto.setRawVenueJson("venue: " + matchNode.path("venue").asText("") + ", venueInfo: " + venueInfo.toString());

        if (!venueInfo.isMissingNode() && !venueInfo.isNull()) {
            String ground = venueInfo.path("ground").asText("").trim();
            String city = venueInfo.path("city").asText("").trim();
            String country = venueInfo.path("country").asText("").trim();
            
            StringBuilder vBuilder = new StringBuilder();
            if (!ground.isEmpty()) vBuilder.append(ground);
            if (!city.isEmpty()) {
                if (vBuilder.length() > 0) vBuilder.append(", ");
                vBuilder.append(city);
            }
            if (!country.isEmpty()) {
                if (vBuilder.length() > 0) vBuilder.append(", ");
                vBuilder.append(country);
            }
            venue = vBuilder.toString();
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
