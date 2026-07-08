package com.pitchiq.engine.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pitchiq.engine.BallOutcome;
import com.pitchiq.entity.Venue;
import com.pitchiq.entity.VenueOutcomeProfile;
import com.pitchiq.repository.VenueOutcomeProfileRepository;
import com.pitchiq.repository.VenueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * One-time ETL script to process Cricsheet JSON files and seed the MySQL database.
 * Annotated with @Profile("seed-data") so it does NOT run on every startup.
 * To execute, run the application with: --spring.profiles.active=seed-data
 */
@Component
@Profile("seed-data")
public class DatabaseSeeder implements CommandLineRunner {

    private final VenueRepository venueRepository;
    private final VenueOutcomeProfileRepository profileRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public DatabaseSeeder(VenueRepository venueRepository, VenueOutcomeProfileRepository profileRepository) {
        this.venueRepository = venueRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting one-time ETL database seeding...");

        File dataDir = new File("cricsheet_data");
        if (!dataDir.exists()) {
            dataDir = new File("../cricsheet_data");
        }
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            System.err.println("cricsheet_data directory not found. Please download Cricsheet JSON files first.");
            return;
        }

        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        Map<String, Map<String, Map<BallOutcome, Integer>>> venuePhaseCounts = new HashMap<>();

        for (File file : files) {
            try {
                CricsheetMatch match = mapper.readValue(file, CricsheetMatch.class);
                if (match.info == null || match.info.venue == null || match.innings == null) continue;
                
                String venueName = match.info.venue;
                venuePhaseCounts.putIfAbsent(venueName, new HashMap<>());

                for (CricsheetMatch.InningsWrapper innings : match.innings) {
                    if (innings.overs == null) continue;
                    for (CricsheetMatch.Over over : innings.overs) {
                        String phase = determinePhase(over.over);
                        venuePhaseCounts.get(venueName).putIfAbsent(phase, new HashMap<>());
                        
                        if (over.deliveries == null) continue;
                        for (CricsheetMatch.Delivery d : over.deliveries) {
                            BallOutcome outcome = mapDeliveryToOutcome(d);
                            Map<BallOutcome, Integer> counts = venuePhaseCounts.get(venueName).get(phase);
                            counts.put(outcome, counts.getOrDefault(outcome, 0) + 1);
                        }
                    }
                }
            } catch (Exception e) {
                // skip malformed
            }
        }

        saveToDatabase(venuePhaseCounts);
        System.out.println("Database seeding completed.");
    }

    private void saveToDatabase(Map<String, Map<String, Map<BallOutcome, Integer>>> venuePhaseCounts) {
        for (Map.Entry<String, Map<String, Map<BallOutcome, Integer>>> venueEntry : venuePhaseCounts.entrySet()) {
            String venueName = venueEntry.getKey();
            
            Venue venue = new Venue();
            venue.setName(venueName);
            venue.setCity("Unknown"); // City parsing can be added later
            venue = venueRepository.save(venue);

            for (Map.Entry<String, Map<BallOutcome, Integer>> phaseEntry : venueEntry.getValue().entrySet()) {
                String phase = phaseEntry.getKey();
                Map<BallOutcome, Integer> counts = phaseEntry.getValue();
                
                double total = counts.values().stream().mapToDouble(Integer::doubleValue).sum();
                if (total == 0) continue;

                for (Map.Entry<BallOutcome, Integer> outcomeEntry : counts.entrySet()) {
                    VenueOutcomeProfile profile = new VenueOutcomeProfile();
                    profile.setVenue(venue);
                    profile.setMatchPhase(phase);
                    profile.setOutcomeType(outcomeEntry.getKey());
                    profile.setProbabilityWeight(outcomeEntry.getValue() / total);
                    profileRepository.save(profile);
                }
            }
        }
    }

    private String determinePhase(int overIndex) {
        if (overIndex < 6) return "POWERPLAY";
        if (overIndex < 15) return "MIDDLE";
        return "DEATH";
    }

    private BallOutcome mapDeliveryToOutcome(CricsheetMatch.Delivery d) {
        if (d.wickets != null && !d.wickets.isEmpty()) return BallOutcome.WICKET;
        if (d.extras != null) {
            if (d.extras.containsKey("wides")) return BallOutcome.WIDE;
            if (d.extras.containsKey("noballs")) return BallOutcome.NO_BALL;
        }
        int runs = (d.runs != null) ? d.runs.batter : 0;
        switch (runs) {
            case 0: return BallOutcome.DOT;
            case 1: return BallOutcome.ONE;
            case 2: return BallOutcome.TWO;
            case 3: return BallOutcome.THREE;
            case 4: return BallOutcome.FOUR;
            case 6: return BallOutcome.SIX;
            default: return BallOutcome.DOT;
        }
    }
}
