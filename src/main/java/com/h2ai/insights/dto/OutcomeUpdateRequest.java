package com.h2ai.insights.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OutcomeUpdateRequest {

    private String actualOutcome;
    private LocalDate actualOutcomeDate;
    private String actualOutcomeNotes;
    private Double overallSurvivalMonths;
    private Boolean deceased;
    private Double fractionGenomeAltered;
    private Integer mutationCount;
    private Double tmbNonsynonymous;
    private Integer yearOfDiagnosis;
}
