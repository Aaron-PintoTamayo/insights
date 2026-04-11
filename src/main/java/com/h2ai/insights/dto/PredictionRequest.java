package com.h2ai.insights.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PredictionRequest {

    private Integer age;

    private String gender;

    @JsonProperty("prior_malignancy")
    private Boolean priorMalignancy;

    @JsonProperty("prior_treatment")
    private Boolean priorTreatment;

    @JsonProperty("ecog_performance_status")
    private Integer ecogPerformanceStatus;
}
