package com.pitchiq.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.dto.CombinedIntelligenceResult;
import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.VenueIntelligenceDto;
import com.pitchiq.entity.VenueIntelligence;
import com.pitchiq.repository.VenueIntelligenceRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production Gemini AI provider.
 * Makes ONE Gemini call per match open:
 *   - If venue NOT cached: returns {"venue":{...}, "insights":[...]} → caches venue in PostgreSQL
 *   - If venue IS cached:  returns ["insight1", ...] → uses cached venue data
 * Never caches fallback/mock data.
 */
@Service
@ConditionalOnProperty(name = "pitchiq.ai.provider", havingValue = "gemini")
public class GeminiAiCommentaryProvider implements AiCommentaryProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiCommentaryProvider.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
    private static final int MAX_RETRIES = 2;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAiCommentaryProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(15000);   // 15 seconds
        this.restTemplate = new RestTemplate(factory);
    }

    @Autowired
    private VenueIntelligenceRepository venueRepository;

    // ─── Startup Verification ───────────────────────────────────────────────

    @PostConstruct
    public void logProviderActivation() {
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();
        log.info("[PitchIQ] ✅ GeminiAiCommentaryProvider is ACTIVE. API key present: {}", hasKey);
        if (!hasKey) {
            log.warn("[PitchIQ] ⚠ GEMINI_API_KEY is empty — all calls will use fallback responses.");
        }
    }

    // ─── Main Entry Point ───────────────────────────────────────────────────

    @Override
    public CombinedIntelligenceResult generateIntelligence(MatchStateRequest request, SimulationResponse simState) {
        String venueName = nvl(request.getVenueName(), "Unknown Venue");
        boolean venueUnknown = "Unknown Venue".equalsIgnoreCase(venueName.trim());

        if (apiKeyMissing()) {
            log.warn("[PitchIQ] API key missing — returning fallback intelligence.");
            return fallback();
        }

        // Check PostgreSQL cache
        Optional<VenueIntelligence> cached = venueUnknown
                ? Optional.empty()
                : venueRepository.findByVenueNameIgnoreCase(venueName.trim());

        if (cached.isPresent()) {
            // ── CACHE HIT: Venue already in DB — ONE call for insights only ──
            log.info("[PitchIQ] Cache HIT for venue '{}'. Generating fresh insights only.", venueName);
            VenueIntelligenceDto venueDto = mapEntityToDto(cached.get());
            List<String> insights = callGeminiForInsightsOnly(request, simState, venueDto);
            return new CombinedIntelligenceResult(venueDto, insights);
        }

        // ── CACHE MISS: ONE call for venue + insights combined ──
        log.info("[PitchIQ] Cache MISS for venue '{}'. Calling Gemini for combined response.", venueName);
        CombinedIntelligenceResult result = callGeminiForCombined(request, simState);

        // Cache venue ONLY if it's a real Gemini response (not fallback)
        VenueIntelligenceDto venueDto = result.getVenueIntelligence();
        if (!venueUnknown && venueDto != null && isRealVenueData(venueDto)) {
            try {
                VenueIntelligence entity = mapDtoToEntity(venueDto, venueName);
                venueRepository.save(entity);
                log.info("[PitchIQ] Venue '{}' successfully cached in PostgreSQL.", venueName);
            } catch (Exception e) {
                log.warn("[PitchIQ] Failed to cache venue '{}': {}", venueName, e.getMessage());
            }
        } else {
            log.warn("[PitchIQ] Venue data for '{}' is fallback/empty — NOT caching to PostgreSQL.", venueName);
        }

        return result;
    }

    // ─── Gemini Calls ───────────────────────────────────────────────────────

    /**
     * ONE Gemini call when venue is NOT cached.
     * Prompt asks for both venue data and match insights in one JSON object.
     */
    private CombinedIntelligenceResult callGeminiForCombined(MatchStateRequest request, SimulationResponse sim) {
        String prompt = buildCombinedPrompt(request, sim);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String rawJson = callGeminiApi(prompt);
                String text = extractAndCleanText(rawJson);
                log.debug("[PitchIQ] Combined Gemini raw text: {}", text.substring(0, Math.min(200, text.length())));
                return parseCombinedResponse(text);
            } catch (Exception e) {
                log.warn("[PitchIQ] Combined Gemini call attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }
        }
        log.error("[PitchIQ] All combined Gemini attempts failed — using fallback.");
        return fallback();
    }

    /**
     * ONE Gemini call when venue IS cached.
     * Prompt asks for match insights only (venue context provided inline).
     */
    private List<String> callGeminiForInsightsOnly(MatchStateRequest request, SimulationResponse sim, VenueIntelligenceDto venue) {
        String prompt = buildInsightsOnlyPrompt(request, sim, venue);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String rawJson = callGeminiApi(prompt);
                String text = extractAndCleanText(rawJson);
                log.debug("[PitchIQ] Insights-only Gemini raw text: {}", text.substring(0, Math.min(200, text.length())));
                return parseInsightsArray(text);
            } catch (Exception e) {
                log.warn("[PitchIQ] Insights Gemini call attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }
        }
        log.error("[PitchIQ] All insights Gemini attempts failed — using fallback bullets.");
        return fallbackInsights();
    }

    // ─── Prompt Builders ────────────────────────────────────────────────────

    private String buildCombinedPrompt(MatchStateRequest request, SimulationResponse sim) {
        String status = nvl(request.getMatchStatus(), "live").toLowerCase();
        String t1 = nvl(request.getBattingTeamName(), "Team A");
        String t2 = nvl(request.getBowlingTeamName(), "Team B");
        String venue = nvl(request.getVenueName(), "Unknown Venue");
        String format = nvl(request.getMatchFormat(), "t20").toUpperCase();

        StringBuilder sb = new StringBuilder();
        sb.append("You are a cricket intelligence analyst. Return ONLY raw JSON with no markdown, no code fences, no explanation.\n");
        sb.append("Match: ").append(t1).append(" vs ").append(t2)
          .append(" | Venue: ").append(venue)
          .append(" | Format: ").append(format)
          .append(" | Status: ").append(status.toUpperCase()).append("\n");
        sb.append(buildMatchContext(request, sim, status)).append("\n");
        sb.append("Return this exact JSON structure (fill all string fields with real cricket data for ").append(venue).append("):\n");
        sb.append("{\"venue\":{");
        sb.append("\"groundName\":\"\",\"city\":\"\",\"pitchType\":\"\",\"battingRating\":\"\",\"bowlingRating\":\"\",");
        sb.append("\"spinSupport\":\"\",\"paceSupport\":\"\",\"averageFirstInningsScore\":\"\",\"highestSuccessfulChase\":\"\",");
        sb.append("\"boundarySize\":\"\",\"dewFactor\":\"\",\"tossAdvantage\":\"\",\"historicalTrend\":\"\",");
        sb.append("\"recommendedStrategy\":\"\",\"shortSummary\":\"\"");
        sb.append("},\"insights\":[\"insight1\",\"insight2\",\"insight3\",\"insight4\",\"insight5\"]}");
        return sb.toString();
    }

    private String buildInsightsOnlyPrompt(MatchStateRequest request, SimulationResponse sim, VenueIntelligenceDto venue) {
        String status = nvl(request.getMatchStatus(), "live").toLowerCase();
        String t1 = nvl(request.getBattingTeamName(), "Team A");
        String t2 = nvl(request.getBowlingTeamName(), "Team B");
        String format = nvl(request.getMatchFormat(), "t20").toUpperCase();

        StringBuilder sb = new StringBuilder();
        sb.append("You are a cricket intelligence analyst. Return ONLY a raw JSON array of exactly 5 concise insight strings, no markdown.\n");
        sb.append("Match: ").append(t1).append(" vs ").append(t2)
          .append(" | Format: ").append(format)
          .append(" | Status: ").append(status.toUpperCase()).append("\n");
        sb.append("Venue context: ").append(venue.getGroundName()).append(", ").append(venue.getCity())
          .append(". Pitch: ").append(venue.getPitchType())
          .append(". Avg 1st innings: ").append(venue.getAverageFirstInningsScore())
          .append(". ").append(venue.getHistoricalTrend()).append("\n");
        sb.append(buildMatchContext(request, sim, status)).append("\n");
        sb.append("Return: [\"insight1\",\"insight2\",\"insight3\",\"insight4\",\"insight5\"]");
        return sb.toString();
    }

    private String buildMatchContext(MatchStateRequest request, SimulationResponse sim, String status) {
        if ("upcoming".equals(status)) {
            return "Pre-match — no score yet. Generate pre-match predictions and strategy insights.";
        } else if ("completed".equals(status)) {
            return "Final: " + request.getCurrentRuns() + "/" + request.getCurrentWickets()
                   + ". Target was: " + request.getTargetScore() + ". Generate post-match analysis.";
        } else {
            double rrr = (sim != null) ? sim.getRequiredRunRate() : 0;
            double wp = (sim != null) ? sim.getWinProbability() * 100 : 50;
            return String.format("Score: %d/%d in %.1f overs. Target: %d. RRR: %.2f. Win Prob: %.1f%%.",
                    request.getCurrentRuns(), request.getCurrentWickets(),
                    request.getOvers(), request.getTargetScore(), rrr, wp);
        }
    }

    // ─── Gemini API ─────────────────────────────────────────────────────────

    private String callGeminiApi(String prompt) throws Exception {
        String url = GEMINI_API_URL + apiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Use ObjectMapper to safely build the JSON request body (handles escaping correctly)
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));
        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(content));

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForObject(url, entity, String.class);
    }

    private String extractAndCleanText(String geminiJsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(geminiJsonResponse);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini response has no candidates: " + geminiJsonResponse.substring(0, Math.min(300, geminiJsonResponse.length())));
        }
        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        // Strip markdown code fences that Gemini may add despite instructions
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        return text;
    }

    // ─── Response Parsers ───────────────────────────────────────────────────

    private CombinedIntelligenceResult parseCombinedResponse(String cleanText) throws Exception {
        JsonNode root = objectMapper.readTree(cleanText);
        VenueIntelligenceDto venue = objectMapper.treeToValue(root.path("venue"), VenueIntelligenceDto.class);
        List<String> insights = new ArrayList<>();
        JsonNode insightsNode = root.path("insights");
        if (insightsNode.isArray()) {
            for (JsonNode n : insightsNode) {
                String s = n.asText().trim();
                if (!s.isEmpty()) insights.add(s);
            }
        }
        if (insights.isEmpty()) {
            throw new RuntimeException("Gemini combined response has no insights array");
        }
        return new CombinedIntelligenceResult(venue, insights);
    }

    private List<String> parseInsightsArray(String cleanText) throws Exception {
        JsonNode root = objectMapper.readTree(cleanText);
        List<String> insights = new ArrayList<>();
        // Handle both direct array and {"insights": [...]} wrapping
        JsonNode arrayNode = root.isArray() ? root : root.path("insights");
        if (arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                String s = n.asText().trim();
                if (!s.isEmpty()) insights.add(s);
            }
        }
        if (insights.isEmpty()) {
            throw new RuntimeException("Gemini insights-only response yielded empty list");
        }
        return insights;
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private VenueIntelligenceDto mapEntityToDto(VenueIntelligence entity) {
        VenueIntelligenceDto dto = new VenueIntelligenceDto();
        dto.setGroundName(entity.getGroundName());
        dto.setCity(entity.getCity());
        dto.setPitchType(entity.getPitchType());
        dto.setBattingRating(entity.getBattingRating());
        dto.setBowlingRating(entity.getBowlingRating());
        dto.setSpinSupport(entity.getSpinSupport());
        dto.setPaceSupport(entity.getPaceSupport());
        dto.setAverageFirstInningsScore(entity.getAverageFirstInningsScore());
        dto.setHighestSuccessfulChase(entity.getHighestSuccessfulChase());
        dto.setBoundarySize(entity.getBoundarySize());
        dto.setDewFactor(entity.getDewFactor());
        dto.setTossAdvantage(entity.getTossAdvantage());
        dto.setHistoricalTrend(entity.getHistoricalTrend());
        dto.setRecommendedStrategy(entity.getRecommendedStrategy());
        dto.setShortSummary(entity.getShortSummary());
        return dto;
    }

    private VenueIntelligence mapDtoToEntity(VenueIntelligenceDto dto, String venueName) {
        VenueIntelligence entity = new VenueIntelligence();
        entity.setVenueName(venueName.trim());
        entity.setGroundName(dto.getGroundName());
        entity.setCity(dto.getCity());
        entity.setPitchType(dto.getPitchType());
        entity.setBattingRating(dto.getBattingRating());
        entity.setBowlingRating(dto.getBowlingRating());
        entity.setSpinSupport(dto.getSpinSupport());
        entity.setPaceSupport(dto.getPaceSupport());
        entity.setAverageFirstInningsScore(dto.getAverageFirstInningsScore());
        entity.setHighestSuccessfulChase(dto.getHighestSuccessfulChase());
        entity.setBoundarySize(dto.getBoundarySize());
        entity.setDewFactor(dto.getDewFactor());
        entity.setTossAdvantage(dto.getTossAdvantage());
        entity.setHistoricalTrend(dto.getHistoricalTrend());
        entity.setRecommendedStrategy(dto.getRecommendedStrategy());
        entity.setShortSummary(dto.getShortSummary());
        return entity;
    }

    // ─── Guards & Fallbacks ─────────────────────────────────────────────────

    private boolean apiKeyMissing() {
        return apiKey == null || apiKey.trim().isEmpty();
    }

    /**
     * Returns true only if the venue DTO has real data from Gemini.
     * Prevents caching fallback/empty/placeholder responses.
     */
    private boolean isRealVenueData(VenueIntelligenceDto dto) {
        if (dto == null) return false;
        String name = dto.getGroundName();
        return name != null && !name.trim().isEmpty()
                && !"Unknown".equalsIgnoreCase(name.trim())
                && !"N/A".equalsIgnoreCase(name.trim());
    }

    private CombinedIntelligenceResult fallback() {
        VenueIntelligenceDto dto = new VenueIntelligenceDto();
        dto.setGroundName("N/A");
        dto.setCity("N/A");
        dto.setPitchType("Balanced");
        dto.setBattingRating("Average");
        dto.setBowlingRating("Average");
        dto.setSpinSupport("Medium");
        dto.setPaceSupport("Medium");
        dto.setAverageFirstInningsScore("N/A");
        dto.setHighestSuccessfulChase("N/A");
        dto.setBoundarySize("Medium");
        dto.setDewFactor("Moderate");
        dto.setTossAdvantage("No significant advantage");
        dto.setHistoricalTrend("Data temporarily unavailable.");
        dto.setRecommendedStrategy("Adapt to conditions on the day.");
        dto.setShortSummary("Venue intelligence temporarily unavailable.");
        return new CombinedIntelligenceResult(dto, fallbackInsights());
    }

    private List<String> fallbackInsights() {
        return List.of("AI intelligence temporarily unavailable.");
    }

    private String nvl(String s, String def) {
        return (s == null || s.trim().isEmpty()) ? def : s;
    }
}
