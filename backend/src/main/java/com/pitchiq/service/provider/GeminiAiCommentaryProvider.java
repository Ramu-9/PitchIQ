package com.pitchiq.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.MatchStateRequest;
import com.pitchiq.dto.VenueIntelligenceDto;
import com.pitchiq.entity.VenueIntelligence;
import com.pitchiq.repository.VenueIntelligenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "pitchiq.ai.provider", havingValue = "gemini")
public class GeminiAiCommentaryProvider implements AiCommentaryProvider {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RETRIES = 2;

    @Autowired
    private VenueIntelligenceRepository venueRepository;

    @Override
    public List<String> generateCommentary(SimulationResponse response, MatchStateRequest request) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("MOCK_KEY_FOR_LOCAL_TESTING")) {
            return getFallbackCommentary();
        }

        String prompt = buildMatchPrompt(response, request);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String apiResponse = callGeminiApi(prompt);
                List<String> commentary = parseMatchResponse(apiResponse);
                if (commentary.size() >= 5) {
                    return commentary.subList(0, 5);
                }
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    return getFallbackCommentary();
                }
            }
        }
        return getFallbackCommentary();
    }

    @Override
    public VenueIntelligenceDto getVenueIntelligence(MatchStateRequest request) {
        String venueName = request.getVenueName();
        if (venueName == null || venueName.trim().isEmpty() || "Unknown Venue".equalsIgnoreCase(venueName)) {
            return getFallbackVenueIntelligence();
        }

        // 1. Check DB Cache
        Optional<VenueIntelligence> cached = venueRepository.findByVenueNameIgnoreCase(venueName.trim());
        if (cached.isPresent()) {
            return mapEntityToDto(cached.get());
        }

        // 2. Call Gemini if not cached
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("MOCK_KEY_FOR_LOCAL_TESTING")) {
            return getFallbackVenueIntelligence();
        }

        String prompt = buildVenuePrompt(venueName);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String apiResponse = callGeminiApi(prompt);
                VenueIntelligenceDto dto = parseVenueResponse(apiResponse);
                dto.setGroundName(venueName); // Ensure it has a name
                
                // 3. Save to DB Cache
                VenueIntelligence entity = mapDtoToEntity(dto, venueName);
                venueRepository.save(entity);
                
                return dto;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    return getFallbackVenueIntelligence();
                }
            }
        }
        return getFallbackVenueIntelligence();
    }

    private String buildMatchPrompt(SimulationResponse response, MatchStateRequest request) {
        String status = request.getMatchStatus() != null ? request.getMatchStatus().toLowerCase() : "live";
        String t1 = request.getBattingTeamName() != null ? request.getBattingTeamName() : "Batting Team";
        String t2 = request.getBowlingTeamName() != null ? request.getBowlingTeamName() : "Bowling Team";
        
        String baseContext = String.format("Teams: %s vs %s\nVenue: %s\nFormat: %s\n", t1, t2, request.getVenueName(), request.getMatchFormat());
        
        if ("upcoming".equals(status)) {
            return "You are an expert cricket analyst providing pre-match intelligence.\n" +
                   "State:\n" + baseContext +
                   "Generate 5 concise professional pre-match insights (Pitch behaviour, Toss importance, Historical trends, Predicted Strategy).\n" +
                   "RULES:\n1. 5 newline-separated plain-text lines.\n2. Max 20 words per line.\n3. NO markdown or numbering.";
        } else if ("completed".equals(status)) {
            return "You are an expert cricket analyst providing post-match intelligence.\n" +
                   "State:\n" + baseContext + "Target: " + request.getTargetScore() + "\nFinal Score: " + request.getCurrentRuns() + "/" + request.getCurrentWickets() + "\n" +
                   "Generate 5 concise professional post-match insights explaining how the match was won (turning points, partnerships, bowling discipline).\n" +
                   "RULES:\n1. 5 newline-separated plain-text lines.\n2. Max 20 words per line.\n3. NO markdown or numbering.";
        } else {
            // LIVE
            return String.format(
                "You are an expert cricket analyst providing live match intelligence.\n" +
                "State:\n%s" +
                "Score: %d/%d in %.1f overs. Target: %d\n" +
                "Req Run Rate: %.2f\n" +
                "Win Prob: %.1f%%\n" +
                "Generate 5 concise professional live insights (Run rate context, tactical suggestions, partnership importance, phase analysis).\n" +
                "RULES:\n1. 5 newline-separated plain-text lines.\n2. Max 20 words per line.\n3. NO markdown or numbering.",
                baseContext, request.getCurrentRuns(), request.getCurrentWickets(), request.getOvers(), request.getTargetScore(), response.getRequiredRunRate(), response.getWinProbability() * 100
            );
        }
    }

    private String buildVenuePrompt(String venueName) {
        return "You are an expert cricket venue analyst. Generate detailed intelligence for the following stadium: " + venueName + "\n" +
               "Return ONLY a valid JSON object with the exact following string keys and no extra formatting or markdown code blocks:\n" +
               "groundName, city, pitchType, battingRating, bowlingRating, spinSupport, paceSupport, averageFirstInningsScore, highestSuccessfulChase, boundarySize, dewFactor, tossAdvantage, historicalTrend, recommendedStrategy, shortSummary.";
    }

    private String callGeminiApi(String prompt) throws Exception {
        String url = GEMINI_API_URL + apiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = "{ \"contents\": [{ \"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\"}] }] }";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForObject(url, request, String.class);
    }

    private List<String> parseMatchResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            return Arrays.stream(text.split("\\n"))
                    .map(s -> s.replaceAll("^\\d+\\.\\s*", "").replaceAll("^[\\*\\-•]\\s*", "").replaceAll("\\[.*?\\]", "").replace("**", "").trim())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        throw new RuntimeException("Invalid JSON from Gemini API");
    }

    private VenueIntelligenceDto parseVenueResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            // Clean markdown json blocks if gemini adds them despite instructions
            text = text.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "").trim();
            return objectMapper.readValue(text, VenueIntelligenceDto.class);
        }
        throw new RuntimeException("Invalid JSON from Gemini API");
    }

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

    private List<String> getFallbackCommentary() {
        return Arrays.asList(
            "Live intelligence is temporarily unavailable.",
            "Historical venue data remains a strong indicator of match outcomes.",
            "Statistical insights remain available through the PitchIQ engine.",
            "Continue to monitor required run rates and projected scores.",
            "The Monte Carlo simulation engine continues to process live telemetry."
        );
    }

    private VenueIntelligenceDto getFallbackVenueIntelligence() {
        VenueIntelligenceDto dto = new VenueIntelligenceDto();
        dto.setGroundName("Unknown");
        dto.setCity("Unknown");
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
        dto.setHistoricalTrend("Matches evenly split between chasing and defending.");
        dto.setRecommendedStrategy("Adapt to conditions on the day.");
        dto.setShortSummary("Venue intelligence is currently unavailable.");
        return dto;
    }
}
