package com.h2ai.insights.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class PredictionRecordResponse {

    private Long predictionId;
    private Long patientId;
    private String patientName;
    private Integer patientAge;

    private Double survival6mo;
    private Double survival12mo;
    private Double survival24mo;
    private Double riskScore;
    private String riskGroup;
    private Double estimatedMedianSurvivalMonths;
    private String plainLanguageSummary;
    private String keyDrivers;
    private String technicalOutput;

    private String expectedOutcome;
    private String actualOutcome;
    private LocalDate actualOutcomeDate;
    private String actualOutcomeNotes;
    private LocalDateTime createdAt;
}
