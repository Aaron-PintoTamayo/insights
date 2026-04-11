package com.h2ai.insights.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurvivalPredictionResponse {

    @JsonProperty("survival_6mo")
    private Double survival6mo;

    @JsonProperty("survival_12mo")
    private Double survival12mo;

    @JsonProperty("survival_24mo")
    private Double survival24mo;

    @JsonProperty("partial_hazard")
    private Double partialHazard;

    @JsonProperty("risk_group")
    private String riskGroup;

    private String interpretation;

    @JsonProperty("survival_curve_png")
    private String survivalCurvePng;
}
