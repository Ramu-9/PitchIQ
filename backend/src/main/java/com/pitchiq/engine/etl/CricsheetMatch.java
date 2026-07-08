package com.pitchiq.engine.etl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CricsheetMatch {
    public Info info;
    public List<InningsWrapper> innings;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        public String venue;
        public String city;
        public Outcome outcome;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Outcome {
        public String winner;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InningsWrapper {
        public String team;
        public List<Over> overs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Over {
        public int over;
        public List<Delivery> deliveries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delivery {
        public String batter;
        public String bowler;
        public Runs runs;
        public List<Wicket> wickets;
        public Map<String, Integer> extras;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Runs {
        public int batter;
        public int extras;
        public int total;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wicket {
        public String player_out;
        public String kind;
    }
}
