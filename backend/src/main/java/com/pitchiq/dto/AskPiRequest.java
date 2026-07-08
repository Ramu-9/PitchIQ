package com.pitchiq.dto;

import java.util.List;

public class AskPiRequest {
    private String question;
    private String matchContext;
    private List<Message> history;

    public static class Message {
        private String role;
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getMatchContext() { return matchContext; }
    public void setMatchContext(String matchContext) { this.matchContext = matchContext; }
    public List<Message> getHistory() { return history; }
    public void setHistory(List<Message> history) { this.history = history; }
}
