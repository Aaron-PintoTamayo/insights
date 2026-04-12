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
    private Double riskScore;
    private String riskGroup;
    private Double estimatedMedianSurvivalMonths;

    @Column(columnDefinition = "TEXT")
    private String plainLanguageSummary;

    @Column(columnDefinition = "TEXT")
    private String keyDrivers;

    @Column(columnDefinition = "TEXT")
    private String technicalOutput;

    @Column(columnDefinition = "TEXT")
    private String expectedOutcome;

    @Column(columnDefinition = "TEXT")
    private String actualOutcome;

    private LocalDate actualOutcomeDate;

    @Column(columnDefinition = "TEXT")
    private String actualOutcomeNotes;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
