package com.h2ai.insights.entity;

import com.h2ai.insights.enums.CytogeneticRisk;
import com.h2ai.insights.enums.Gender;
import com.h2ai.insights.enums.LeukemiaType;
import com.h2ai.insights.enums.TreatmentStatus;
import com.h2ai.insights.enums.TreatmentType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
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
    @Enumerated(EnumType.STRING)
    private LeukemiaType leukemiaType;

    private LocalDate diagnosisDate;

    // Philadelphia chromosome (key for CML/ALL prognosis)
    private Boolean philadelphiaChromosomePositive;

    @Enumerated(EnumType.STRING)
    private CytogeneticRisk cytogeneticRisk;

    // --- Blood / Lab Values ---
    private Double wbcCount;          // White blood cell count (cells/µL)
    private Double hemoglobinLevel;   // g/dL
    private Integer plateletCount;    // cells/µL
    private Double ldh;               // Lactate dehydrogenase (U/L)
    private Double blastPercentage;   // % blasts in bone marrow

    // --- Treatment ---
    @Enumerated(EnumType.STRING)
    private TreatmentType treatmentType;

    @Enumerated(EnumType.STRING)
    private TreatmentStatus treatmentStatus;

    private Integer relapseCount;

    // --- Performance / Spread ---
    private Integer ecogPerformanceStatus; // 0 (fully active) – 4 (completely disabled)
    private Boolean cnsInvolvement;        // cancer spread to brain/spinal cord
}
