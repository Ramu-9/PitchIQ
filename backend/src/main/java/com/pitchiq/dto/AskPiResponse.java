package com.pitchiq.dto;

public class AskPiResponse {
    private String answer;

    public AskPiResponse() {}

    public AskPiResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
}
