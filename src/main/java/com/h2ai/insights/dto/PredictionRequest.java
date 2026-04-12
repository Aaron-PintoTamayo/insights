package com.h2ai.insights.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PredictionRequest {

    private Integer age;

    @JsonProperty("mutation_count")
    private Integer mutationCount;

    @JsonProperty("TMB")
    private Double tmb;

    private Double fga;

    private String sex;
    private String race;
    private String ethnicity;
}
