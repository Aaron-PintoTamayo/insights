package com.h2ai.insights.entity;

import com.h2ai.insights.enums.Gender;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "patients")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Demographics ---
    private String name;
    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    // --- Diagnosis ---
    private LocalDate diagnosisDate;

    // --- Cox Model Features ---
    private Boolean priorMalignancy;   // required by Cox model
    private Boolean priorTreatment;    // was in original feature set

    // --- Performance (context for Claude explanation) ---
    private Integer ecogPerformanceStatus; // 0 (fully active) – 4 (completely disabled)
}
