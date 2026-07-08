package com.pitchiq.engine;

/**
 * Represents the current state of a cricket match innings.
 * This class is mutable by design. The Monte Carlo simulator updates state rapidly in a tight loop.
 * Mutating this object instead of creating millions of immutable copies significantly reduces 
 * garbage collection overhead, which is an important talking point for backend engineering interviews.
 */
public class MatchState {
    private int currentRuns;
    private int currentWickets;
    private int ballsBowled;
    private int targetScore; // 0 if setting a target (1st innings)
    private int maxBalls;

    public MatchState(int currentRuns, int currentWickets, int ballsBowled, int targetScore, int maxBalls) {
        this.currentRuns = currentRuns;
        this.currentWickets = currentWickets;
        this.ballsBowled = ballsBowled;
        this.targetScore = targetScore;
        this.maxBalls = maxBalls;
    }

    // Copy constructor for resetting simulation iterations
    public MatchState(MatchState other) {
        this.currentRuns = other.currentRuns;
        this.currentWickets = other.currentWickets;
        this.ballsBowled = other.ballsBowled;
        this.targetScore = other.targetScore;
        this.maxBalls = other.maxBalls;
    }

    public void update(BallOutcome outcome) {
        this.currentRuns += outcome.getRuns();
        this.currentWickets += outcome.getWickets();
        if (outcome.isLegalDelivery()) {
            this.ballsBowled++;
        }
    }

    public boolean isInningsOver() {
        return currentWickets >= 10 || ballsBowled >= maxBalls || hasTargetBeenChased();
    }
    
    public boolean hasTargetBeenChased() {
        return targetScore > 0 && currentRuns >= targetScore;
    }

    // Getters
    public int getCurrentRuns() { return currentRuns; }
    public int getCurrentWickets() { return currentWickets; }
    public int getBallsBowled() { return ballsBowled; }
    public int getTargetScore() { return targetScore; }
}
