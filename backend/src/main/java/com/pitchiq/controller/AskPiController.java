package com.pitchiq.controller;

import com.pitchiq.dto.AskPiRequest;
import com.pitchiq.dto.AskPiResponse;
import com.pitchiq.service.AskPiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AskPiController {

    private final AskPiService askPiService;

    public AskPiController(AskPiService askPiService) {
        this.askPiService = askPiService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskPiResponse> askQuestion(@RequestBody AskPiRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new AskPiResponse("Please ask a valid question."));
        }
        String answer = askPiService.askQuestion(request);
        return ResponseEntity.ok(new AskPiResponse(answer));
    }
}
