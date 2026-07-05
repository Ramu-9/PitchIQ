package com.pitchiq.engine.etl;

import com.pitchiq.engine.ProbabilityDistribution;
import java.io.File;

public class EtlMain {
    public static void main(String[] args) {
        File dataDir = new File("cricsheet_data");
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            System.err.println("cricsheet_data directory not found. Skipping validation.");
            return;
        }

        CricsheetParser parser = new CricsheetParser();
        ProbabilityDistribution dist = parser.parseGlobalDistribution(dataDir);
        
        ModelValidator validator = new ModelValidator();
        validator.validate(dataDir, dist);
    }
}
