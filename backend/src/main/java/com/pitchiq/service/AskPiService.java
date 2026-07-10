package com.pitchiq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.dto.AskPiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AskPiService {

    private static final Logger log = LoggerFactory.getLogger(AskPiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AskPiService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String askQuestion(AskPiRequest request) {
        try {
            // Build system prompt
            String systemInstruction = "You are Ask PI, the PitchIQ Intelligence Assistant. " +
                    "You are a professional, highly analytical cricket expert. " +
                    "You ONLY answer questions related to the current match, cricket strategy, and the provided PitchIQ telemetry. " +
                    "If the user asks an unrelated question (e.g., 'Who is Elon Musk?', 'Write Java code', etc.), politely refuse by saying: " +
                    "'Ask PI is designed exclusively for cricket analysis and PitchIQ match intelligence.'\n\n" +
                    "Current Match Context:\n" + request.getMatchContext();

            // Construct payload for Gemini API
            Map<String, Object> payload = new HashMap<>();
            
            // System instructions
            Map<String, Object> systemContent = new HashMap<>();
            systemContent.put("parts", List.of(Map.of("text", systemInstruction)));
            payload.put("systemInstruction", systemContent);

            // History & Question
            List<Map<String, Object>> contents = new ArrayList<>();
            if (request.getHistory() != null) {
                for (AskPiRequest.Message msg : request.getHistory()) {
                    Map<String, Object> part = new HashMap<>();
                    part.put("text", msg.getContent());
                    Map<String, Object> contentMap = new HashMap<>();
                    // Gemini uses "user" and "model" roles
                    contentMap.put("role", "assistant".equals(msg.getRole()) ? "model" : "user");
                    contentMap.put("parts", List.of(part));
                    contents.add(contentMap);
                }
            }
            
            // Current Question
            Map<String, Object> currentQPart = new HashMap<>();
            currentQPart.put("text", request.getQuestion());
            Map<String, Object> currentQ = new HashMap<>();
            currentQ.put("role", "user");
            currentQ.put("parts", List.of(currentQPart));
            contents.add(currentQ);

            payload.put("contents", contents);

            // Set generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            payload.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);

            String response = restTemplate.postForObject(GEMINI_API_URL + apiKey, entity, String.class);
            return extractTextFromResponse(response);

        } catch (Exception e) {
            log.error("[Ask PI] API call failed: {}", e.getMessage());
            return "I am currently unable to process your request. Please try again in a moment.";
        }
    }

    private String extractTextFromResponse(String jsonResponse) {
        try {
            var rootNode = objectMapper.readTree(jsonResponse);
            var candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                var parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini response for Ask PI: {}", e.getMessage());
        }
        return "Sorry, I could not understand the response from the intelligence engine.";
    }
}
