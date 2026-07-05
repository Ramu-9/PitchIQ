package com.pitchiq.engine;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the weighted probabilities of each ball outcome for a specific venue/phase.
 * We use a NavigableMap (TreeMap) to implement O(log N) random weighted selection.
 * This is a great algorithm to discuss in interviews as an alternative to iterating arrays.
 */
public class ProbabilityDistribution {
    private final NavigableMap<Double, BallOutcome> distribution = new TreeMap<>();
    private double totalWeight = 0;

    public ProbabilityDistribution(Map<BallOutcome, Double> weights) {
        for (Map.Entry<BallOutcome, Double> entry : weights.entrySet()) {
            if (entry.getValue() > 0) {
                totalWeight += entry.getValue();
                distribution.put(totalWeight, entry.getKey());
            }
        }
    }

    /**
     * Generates a random outcome based on the configured weights.
     * Uses ThreadLocalRandom for high performance in multithreaded environments.
     */
    public BallOutcome nextOutcome() {
        double value = ThreadLocalRandom.current().nextDouble() * totalWeight;
        return distribution.higherEntry(value).getValue();
    }
}
