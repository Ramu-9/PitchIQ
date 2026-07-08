package com.pitchiq.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.dto.SimulationResponse;
import com.pitchiq.dto.MatchDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "pitchiq.ai.provider", havingValue = "gemini")
public class GeminiAiCommentaryProvider implements AiCommentaryProvider {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RETRIES = 3;

    @Override
    public List<String> generateCommentary(SimulationResponse response) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("MOCK_KEY_FOR_LOCAL_TESTING")) {
            return getFallbackCommentary();
        }

        String prompt = buildPrompt(response);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<String> commentary = callGeminiApi(prompt);
                return commentary;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    return getFallbackCommentary();
                }
            }
        }
        return getFallbackCommentary();
    }

    private String buildPrompt(SimulationResponse response) {
        String venueStr = response.getVenueName() != null ? response.getVenueName() : "Unknown Venue";
        return String.format(
            "You are a professional cricket analytics engine called PitchIQ. " +
            "Based on the current match state, provide exactly 5 intelligence bullets.\n" +
            "State:\n" +
            "- Venue: %s\n" +
            "- Win Prob: %.1f%%\n" +
            "- Projected Score: %d\n" +
            "- Required Run Rate: %.2f\n" +
            "- Momentum Meter: %.2f\n\n" +
            "RULES:\n" +
            "1. Output exactly 5 newline-separated plain-text lines.\n" +
            "2. Fixed order: [Match Statistic] \\n [Venue Insight] \\n [Run Rate Context] \\n [Tactical Suggestion] \\n [Engine Verdict]\n" +
            "3. MAXIMUM 20 words per line.\n" +
            "4. Professional tone.\n" +
            "5. NO markdown, NO numbering, NO headings, NO emojis.\n" +
            "6. Never predict different probabilities than what is provided.",
            venueStr,
            response.getWinProbability() * 100,
            response.getProjectedScore(),
            response.getRequiredRunRate(),
            response.getMomentumMeter()
        );
    }

    private List<String> callGeminiApi(String prompt) throws Exception {
        String url = GEMINI_API_URL + apiKey;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{ \"contents\": [{ \"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\"}] }] }";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        String apiResponse = restTemplate.postForObject(url, request, String.class);
        return parseGeminiResponse(apiResponse);
    }

    private List<String> parseGeminiResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray() && parts.size() > 0) {
                String text = parts.get(0).path("text").asText();
                return parseResponseText(text);
            }
        }
        throw new RuntimeException("Invalid JSON structure from Gemini API");
    }

    private List<String> parseResponseText(String text) throws Exception {
        List<String> lines = Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        if (lines.size() >= 5) {
            return lines.subList(0, 5).stream().map(this::cleanText).collect(Collectors.toList());
        } else {
            throw new RuntimeException("Gemini returned incorrectly formatted text with " + lines.size() + " lines: " + text);
        }
    }
    
    private String cleanText(String input) {
        return input.replaceAll("^\\d+\\.\\s*", "").replaceAll("^[\\*\\-•]\\s*", "").replaceAll("\\[.*?\\]", "").replace("**", "").trim();
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

}
