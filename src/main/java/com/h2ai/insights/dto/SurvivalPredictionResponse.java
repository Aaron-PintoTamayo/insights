package com.h2ai.insights.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SurvivalPredictionResponse {

    @JsonProperty("clinician_output")
    private ClinicianOutput clinicianOutput;

    @JsonProperty("technical_output")
    private JsonNode technicalOutput;

    @Getter
    @Setter
    public static class ClinicianOutput {
        @JsonProperty("risk_group")
        private String riskGroup;

        @JsonProperty("risk_score")
        private Double riskScore;

        @JsonProperty("survival_probabilities")
        private SurvivalProbabilities survivalProbabilities;

        @JsonProperty("estimated_median_survival_months")
        private Double estimatedMedianSurvivalMonths;

        @JsonProperty("plain_language_summary")
        private String plainLanguageSummary;

        @JsonProperty("key_drivers")
        private List<KeyDriver> keyDrivers;
    }

    @Getter
    @Setter
    public static class KeyDriver {
        private String feature;
        private Double coefficient;
        private Double value;
        private Double contribution;
        private String direction;

        public String toSummary() {
            String featureText = feature == null ? "unknown_feature" : feature;
            String directionText = direction == null ? "has mixed impact" : direction;
            return featureText + " " + directionText;
        }
    }

    @Getter
    @Setter
    public static class SurvivalProbabilities {
        @JsonProperty("6_months")
        private Double sixMonths;

        @JsonProperty("12_months")
        private Double twelveMonths;

        @JsonProperty("24_months")
        private Double twentyFourMonths;
    }
}
