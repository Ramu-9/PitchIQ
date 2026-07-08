package com.pitchiq.engine.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.engine.BallOutcome;
import com.pitchiq.engine.MatchState;
import com.pitchiq.engine.MonteCarloSimulator;
import com.pitchiq.engine.ProbabilityDistribution;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ModelValidator {
    
    private final ObjectMapper mapper = new ObjectMapper();
    private final MonteCarloSimulator simulator = new MonteCarloSimulator();

    public void validate(File dataDir, ProbabilityDistribution dist) {
        System.out.println("Starting validation on historical matches...");
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        // Use the first 100 matches as the test set (hold-out)
        int testCount = Math.min(100, files.length);
        List<PredictionRecord> predictions = new ArrayList<>();

        for (int i = 0; i < testCount; i++) {
            try {
                CricsheetMatch match = mapper.readValue(files[i], CricsheetMatch.class);
                if (match.innings == null || match.innings.size() < 2 || match.info.outcome == null || match.info.outcome.winner == null) {
                    continue; // Skip incomplete matches or ties/no result
                }

                String team1 = match.innings.get(0).team;
                String team2 = match.innings.get(1).team;
                String winner = match.info.outcome.winner;
                boolean team2Won = winner.equals(team2);

                int targetScore = calculateFirstInningsScore(match.innings.get(0)) + 1;

                // Process 2nd innings checkpoints
                CricsheetMatch.InningsWrapper secondInnings = match.innings.get(1);
                processCheckpoints(secondInnings, targetScore, dist, team2Won, predictions);

            } catch (Exception e) {
                // skip
            }
        }

        writeCsv(predictions);
    }

    private void processCheckpoints(CricsheetMatch.InningsWrapper innings, int targetScore, ProbabilityDistribution dist, boolean actualWin, List<PredictionRecord> records) {
        int runs = 0;
        int wickets = 0;
        int balls = 0;

        for (CricsheetMatch.Over over : innings.overs) {
            for (CricsheetMatch.Delivery d : over.deliveries) {
                if (d.wickets != null && !d.wickets.isEmpty()) wickets++;
                if (d.runs != null) runs += d.runs.total;
                if (d.extras == null || (!d.extras.containsKey("wides") && !d.extras.containsKey("noballs"))) {
                    balls++;
                }
            }

            // Checkpoints at end of over 6, 12, 16
            if (over.over == 5 || over.over == 11 || over.over == 15) { // 0-indexed overs
                MatchState state = new MatchState(runs, wickets, balls, targetScore, 120);
                double winProb = simulator.simulate(state, dist).getWinProbability();
                records.add(new PredictionRecord(winProb, actualWin));
            }
        }
    }

    private int calculateFirstInningsScore(CricsheetMatch.InningsWrapper innings) {
        int total = 0;
        for (CricsheetMatch.Over over : innings.overs) {
            for (CricsheetMatch.Delivery d : over.deliveries) {
                if (d.runs != null) total += d.runs.total;
            }
        }
        return total;
    }

    private void writeCsv(List<PredictionRecord> records) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("calibration_results.csv"))) {
            writer.println("predicted_prob,actual_win");
            for (PredictionRecord r : records) {
                writer.println(r.predictedProb + "," + (r.actualWin ? 1 : 0));
            }
            System.out.println("Saved " + records.size() + " checkpoints to calibration_results.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class PredictionRecord {
        double predictedProb;
        boolean actualWin;
        PredictionRecord(double p, boolean a) { predictedProb = p; actualWin = a; }
    }
}
