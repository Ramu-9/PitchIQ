package com.pitchiq.dto;

public class ScoreDto {
    private int runs;
    private int wickets;
    private double overs;
    private String inning;

    // Getters and Setters
    public int getRuns() { return runs; }
    public void setRuns(int runs) { this.runs = runs; }
    public int getWickets() { return wickets; }
    public void setWickets(int wickets) { this.wickets = wickets; }
    public double getOvers() { return overs; }
    public void setOvers(double overs) { this.overs = overs; }
    public String getInning() { return inning; }
    public void setInning(String inning) { this.inning = inning; }
}
