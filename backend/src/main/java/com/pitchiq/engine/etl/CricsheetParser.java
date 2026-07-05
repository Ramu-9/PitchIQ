package com.pitchiq.engine.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.engine.BallOutcome;
import com.pitchiq.engine.MatchState;
import com.pitchiq.engine.ProbabilityDistribution;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Cricsheet JSON files to generate ProbabilityDistributions and extract MatchStates.
 */
public class CricsheetParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public ProbabilityDistribution parseGlobalDistribution(File directory) {
        System.out.println("Parsing Cricsheet data from " + directory.getAbsolutePath());
        Map<BallOutcome, Double> counts = new HashMap<>();
        for (BallOutcome outcome : BallOutcome.values()) {
            counts.put(outcome, 0.0);
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("No JSON files found in " + directory.getAbsolutePath());
            return new ProbabilityDistribution(counts); // Empty
        }

        int matchesProcessed = 0;
        for (File file : files) {
            try {
                CricsheetMatch match = mapper.readValue(file, CricsheetMatch.class);
                if (match.innings != null) {
                    for (CricsheetMatch.InningsWrapper innings : match.innings) {
                        if (innings.overs != null) {
                            for (CricsheetMatch.Over over : innings.overs) {
                                if (over.deliveries != null) {
                                    for (CricsheetMatch.Delivery d : over.deliveries) {
                                        BallOutcome outcome = mapDeliveryToOutcome(d);
                                        counts.put(outcome, counts.get(outcome) + 1);
                                    }
                                }
                            }
                        }
                    }
                }
                matchesProcessed++;
                if (matchesProcessed % 100 == 0) {
                    System.out.println("Processed " + matchesProcessed + " matches...");
                }
            } catch (Exception e) {
                // Skip malformed files
            }
        }

        System.out.println("Total matches processed: " + matchesProcessed);
        return new ProbabilityDistribution(counts);
    }

    private BallOutcome mapDeliveryToOutcome(CricsheetMatch.Delivery d) {
        if (d.wickets != null && !d.wickets.isEmpty()) {
            return BallOutcome.WICKET;
        }
        if (d.extras != null) {
            if (d.extras.containsKey("wides")) {
                return BallOutcome.WIDE;
            }
            if (d.extras.containsKey("noballs")) {
                return BallOutcome.NO_BALL;
            }
        }
        int runs = (d.runs != null) ? d.runs.batter : 0;
        switch (runs) {
            case 0: return BallOutcome.DOT;
            case 1: return BallOutcome.ONE;
            case 2: return BallOutcome.TWO;
            case 3: return BallOutcome.THREE;
            case 4: return BallOutcome.FOUR;
            case 6: return BallOutcome.SIX;
            default: return BallOutcome.DOT; // Fallback for 5 or 7 runs
        }
    }
}
