package com.h2ai.insights.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_records")
@Getter
@Setter
public class PredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    private Double survival6mo;
    private Double survival12mo;
    private Double survival24mo;
    private Double partialHazard;
    private String riskGroup;

    @Column(columnDefinition = "TEXT")
    private String interpretation;

    @Column(columnDefinition = "TEXT")
    private String survivalCurvePng;

    @Column(columnDefinition = "TEXT")
    private String expectedOutcome;

    @Column(columnDefinition = "TEXT")
    private String actualOutcome;

    private LocalDate actualOutcomeDate;

    private Double overallSurvivalMonths;
    private Boolean deceased;
    private Double fractionGenomeAltered;
    private Integer mutationCount;
    private Double tmbNonsynonymous;
    private Integer yearOfDiagnosis;

    @Column(columnDefinition = "TEXT")
    private String actualOutcomeNotes;

    private LocalDateTime createdAt;
    private LocalDateTime retrainSyncedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
