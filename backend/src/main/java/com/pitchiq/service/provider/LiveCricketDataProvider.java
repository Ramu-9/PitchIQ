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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "pitchiq.cricket.provider", havingValue = "live")
public class LiveCricketDataProvider implements CricketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveCricketDataProvider.class);

    // ─── Big-Team Visibility Set ────────────────────────────────────────────
    // Matches involving these teams get a visibility boost so they survive
    // Phase A truncation. This has ZERO effect on display order (Phase B).
    // Format (Test/ODI/T20/T20I/franchise) has ZERO priority influence.
    private static final Set<String> BIG_TEAM_NAMES = new HashSet<>();
    private static final Set<String> BIG_TEAM_ABBRS = new HashSet<>();
    static {
        // Major international teams
        for (String t : new String[]{"india","australia","england","pakistan","south africa",
                "new zealand","sri lanka","bangladesh","west indies","afghanistan"}) {
            BIG_TEAM_NAMES.add(t);
        }
        // Major IPL franchises
        for (String t : new String[]{"chennai super kings","royal challengers bengaluru",
                "royal challengers bangalore","mumbai indians","kolkata knight riders",
                "sunrisers hyderabad","delhi capitals","rajasthan royals",
                "gujarat titans","punjab kings","lucknow super giants"}) {
            BIG_TEAM_NAMES.add(t);
        }
        // Abbreviations
        for (String a : new String[]{"ind","aus","eng","pak","sa","nz","sl","ban","wi","afg",
                "csk","rcb","mi","kkr","srh","dc","rr","gt","pbks","lsg"}) {
            BIG_TEAM_ABBRS.add(a);
        }
    }

    /** Check if a team is a major team. Women's equivalents inherit priority. */
    private static boolean isBigTeam(String fullName, String shortName) {
        if (fullName != null) {
            String base = fullName.replaceAll("(?i)\\b(women|woman|w)\\b", "").trim().toLowerCase();
            if (BIG_TEAM_NAMES.contains(base)) return true;
        }
        if (shortName != null) {
            String base = shortName.replaceAll("(?i)\\s*w$", "").trim().toLowerCase();
            if (BIG_TEAM_ABBRS.contains(base)) return true;
        }
        return false;
    }

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
    private final Map<String, CacheEntry<List<MatchDto>>> scheduleCache = new ConcurrentHashMap<>();
    
    private static final long SCHEDULE_CACHE_TTL_MS = 600000; // 10 minutes
    
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
            // 1. Fetch current matches dynamically (bounded to 6 pages = 150 matches)
            List<MatchDto> current = fetchEndpointDynamically("v1/currentMatches", 6);
            
            // 2. Fetch upcoming schedule dynamically (bounded to 10 pages = 250 matches) and cache for 10m
            List<MatchDto> historical = getScheduleMatchesDynamically("v1/matches", 10);

            // 1. Current matches take priority
            for (MatchDto match : current) {
                matchMap.put(match.getId(), match);
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
                    // Scheduled start time has passed, but give it 24h before declaring it stale 
                    // (accounts for rain delays, abandoned tosses, bad light, etc.)
                    if (matchTime != null && matchTime.isBefore(nowUtc.minusHours(24))) {
                        log.warn("[PitchIQ] Filtering stale upcoming match whose start time passed >24h ago: ID={}, Name={}, Time={}", 
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
            
            // --- Phase A: Visibility Selection ---
            List<MatchDto> liveMatches = new ArrayList<>();
            List<MatchDto> recentMatches = new ArrayList<>();
            List<MatchDto> upcomingMatches = new ArrayList<>();
            
            for (MatchDto m : allMatches) {
                int state = calculateStateScore(m);
                if (state == 3) liveMatches.add(m);
                else if (state == 2) recentMatches.add(m);
                else upcomingMatches.add(m);
            }
            
            // Sort each bucket by visibilityScore DESC
            liveMatches.sort((m1, m2) -> Integer.compare(m2.getVisibilityScore(), m1.getVisibilityScore()));
            recentMatches.sort((m1, m2) -> Integer.compare(m2.getVisibilityScore(), m1.getVisibilityScore()));
            upcomingMatches.sort((m1, m2) -> Integer.compare(m2.getVisibilityScore(), m1.getVisibilityScore()));
            
            // Truncate based on visibility limits
            if (liveMatches.size() > 15) liveMatches = liveMatches.subList(0, 15);
            if (recentMatches.size() > 15) recentMatches = recentMatches.subList(0, 15);
            if (upcomingMatches.size() > 20) upcomingMatches = upcomingMatches.subList(0, 20);
            
            // --- Phase B: Strict Chronological Ordering ---
            // Discard visibility score and sort strictly by actual date/time
            
            liveMatches.sort((m1, m2) -> {
                LocalDateTime t1 = parseMatchTime(m1.getDateTimeGMT());
                LocalDateTime t2 = parseMatchTime(m2.getDateTimeGMT());
                if (t1 != null && t2 != null && !t1.equals(t2)) return t2.compareTo(t1); // newest first
                if (t1 != null) return -1;
                if (t2 != null) return 1;
                return m1.getId().compareTo(m2.getId());
            });
            
            recentMatches.sort((m1, m2) -> {
                LocalDateTime t1 = parseMatchTime(m1.getDateTimeGMT());
                LocalDateTime t2 = parseMatchTime(m2.getDateTimeGMT());
                if (t1 != null && t2 != null && !t1.equals(t2)) return t2.compareTo(t1); // newest first
                if (t1 != null) return -1;
                if (t2 != null) return 1;
                return m1.getId().compareTo(m2.getId());
            });
            
            upcomingMatches.sort((m1, m2) -> {
                LocalDateTime t1 = parseMatchTime(m1.getDateTimeGMT());
                LocalDateTime t2 = parseMatchTime(m2.getDateTimeGMT());
                if (t1 != null && t2 != null && !t1.equals(t2)) return t1.compareTo(t2); // earliest first
                if (t1 != null) return -1;
                if (t2 != null) return 1;
                return m1.getId().compareTo(m2.getId());
            });
            
            // Recombine
            allMatches = new ArrayList<>();
            allMatches.addAll(liveMatches);
            allMatches.addAll(recentMatches);
            allMatches.addAll(upcomingMatches);
            
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

    private List<MatchDto> fetchEndpointDynamically(String endpoint, int maxPages) {
        List<MatchDto> allMatches = new ArrayList<>();
        try {
            // Fetch first page to get totalRows
            String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + endpoint + "?apikey=" + apiKey + "&offset=0";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("status") && !"success".equalsIgnoreCase(root.path("status").asText())) {
                log.error("CricAPI Error: {}", root.path("info").asText("Unknown"));
                return allMatches;
            }
            
            com.fasterxml.jackson.databind.JsonNode data = root.path("data");
            if (data.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode matchNode : data) {
                    MatchDto dto = parseSingleMatch(matchNode);
                    if (dto != null) allMatches.add(dto);
                }
            }
            
            int totalRows = root.path("info").path("totalRows").asInt(0);
            int pageSize = 25; // standard CricAPI page size
            
            if (totalRows > pageSize) {
                int totalPagesNeeded = (int) Math.ceil((double) totalRows / pageSize);
                int pagesToFetch = Math.min(totalPagesNeeded, maxPages) - 1; // -1 because we fetched page 1
                
                if (pagesToFetch > 0) {
                    List<CompletableFuture<List<MatchDto>>> futures = new ArrayList<>();
                    for (int i = 1; i <= pagesToFetch; i++) {
                        int currentOffset = i * pageSize;
                        futures.add(CompletableFuture.supplyAsync(() -> fetchAndParseListSafely(endpoint, currentOffset)));
                    }
                    
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    
                    for (CompletableFuture<List<MatchDto>> future : futures) {
                        List<MatchDto> pageMatches = future.join();
                        if (pageMatches != null) {
                            allMatches.addAll(pageMatches);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error dynamically fetching {}: {}", endpoint, e.getMessage());
        }
        return allMatches;
    }

    private List<MatchDto> getScheduleMatchesDynamically(String endpoint, int maxPages) {
        CacheEntry<List<MatchDto>> entry = scheduleCache.get("SCHEDULE_LIST");
        if (entry != null && (System.currentTimeMillis() - entry.timestamp) < SCHEDULE_CACHE_TTL_MS) {
            return entry.data;
        }
        List<MatchDto> schedule = fetchEndpointDynamically(endpoint, maxPages);
        if (schedule != null && !schedule.isEmpty()) {
            scheduleCache.put("SCHEDULE_LIST", new CacheEntry<>(schedule));
        }
        return schedule != null ? schedule : new ArrayList<>();
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
        int visibilityScore = 0;
        
        if (matchNode.path("bbbEnabled").asBoolean(false)) {
            visibilityScore += 10;
        }
        if (matchNode.path("fantasyEnabled").asBoolean(false)) {
            visibilityScore += 10;
        }
        if (matchNode.path("hasSquad").asBoolean(false)) {
            visibilityScore += 5;
        }

        if (teamInfo.isArray() && teamInfo.size() >= 2) {
            t1Short = teamInfo.get(0).path("shortname").asText("").trim();
            t2Short = teamInfo.get(1).path("shortname").asText("").trim();
            if (!t1Short.isEmpty() && !t2Short.isEmpty()) {
                visibilityScore += 5;
            }
            String t1Img = teamInfo.get(0).path("img").asText("").trim();
            String t2Img = teamInfo.get(1).path("img").asText("").trim();
            if (!t1Img.isEmpty() && !t2Img.isEmpty()) {
                visibilityScore += 5;
            }
        }
        
        // Big-team visibility boost (+20 per big team, max +40)
        // This ensures major matches survive Phase A truncation.
        if (isBigTeam(dto.getBattingTeam(), t1Short)) visibilityScore += 20;
        if (isBigTeam(dto.getBowlingTeam(), t2Short)) visibilityScore += 20;
        
        dto.setVisibilityScore(visibilityScore);
        
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
        
        if (abbr == null && providedShortName != null && !providedShortName.trim().isEmpty()) {
            abbr = getIccAbbreviation(providedShortName);
        }
        
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
            if (!abbr.endsWith(" w")) {
                abbr += " w";
            }
        }
        
        return abbr;
    }

    private String getIccAbbreviation(String teamName) {
        if (teamName == null || teamName.isEmpty()) return null;
        String baseName = teamName.replaceAll("(?i)\\b(Women|W)\\b", "").trim().toLowerCase();
        
        switch (baseName) {
            case "india": case "ind": case "in": return "IND";
            case "australia": case "aus": case "au": return "AUS";
            case "england": case "eng": case "en": return "ENG";
            case "new zealand": case "nz": case "nzl": return "NZ";
            case "south africa": case "sa": case "rsa": return "SA";
            case "pakistan": case "pak": case "pk": return "PAK";
            case "bangladesh": case "ban": case "bd": return "BAN";
            case "sri lanka": case "sl": case "slk": return "SL";
            case "west indies": case "wi": case "win": return "WI";
            case "afghanistan": case "afg": return "AFG";
            case "ireland": case "ire": case "irl": return "IRE";
            case "zimbabwe": case "zim": case "zw": return "ZIM";
            case "scotland": case "sco": return "SCO";
            case "netherlands": case "ned": case "nl": return "NED";
            case "united arab emirates": case "uae": return "UAE";
            case "namibia": case "nam": return "NAM";
            case "nepal": case "nep": return "NEP";
            case "oman": case "oma": return "OMA";
            case "papua new guinea": case "png": return "PNG";
            case "united states": case "usa": case "us": case "united states of america": return "USA";
            default: return null;
        }
    }
}
