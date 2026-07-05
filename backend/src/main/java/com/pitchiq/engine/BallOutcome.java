package com.pitchiq.engine;

public enum BallOutcome {
    DOT(0, 0),
    ONE(1, 0),
    TWO(2, 0),
    THREE(3, 0),
    FOUR(4, 0),
    SIX(6, 0),
    WICKET(0, 1),
    WIDE(1, 0, false),    // 1 run, not a legal delivery
    NO_BALL(1, 0, false); // 1 run, not a legal delivery

    private final int runs;
    private final int wickets;
    private final boolean isLegalDelivery;

    BallOutcome(int runs, int wickets) {
        this(runs, wickets, true);
    }

    BallOutcome(int runs, int wickets, boolean isLegalDelivery) {
        this.runs = runs;
        this.wickets = wickets;
        this.isLegalDelivery = isLegalDelivery;
    }

    public int getRuns() { return runs; }
    public int getWickets() { return wickets; }
    public boolean isLegalDelivery() { return isLegalDelivery; }
}
