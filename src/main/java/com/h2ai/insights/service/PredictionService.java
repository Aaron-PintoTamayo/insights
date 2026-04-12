package com.h2ai.insights.service;

import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
import com.h2ai.insights.dto.SurvivalPredictionResponse;
import com.h2ai.insights.entity.PredictionRecord;
import com.h2ai.insights.entity.User;
import com.h2ai.insights.repository.PredictionRecordRepository;
import com.h2ai.insights.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PredictionService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PredictionRecordRepository predictionRecordRepository;

    @Value("${modal.api.url}")
    private String modalApiUrl;

    public PredictionService(
            RestTemplate restTemplate,
            UserRepository userRepository,
            PredictionRecordRepository predictionRecordRepository
    ) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.predictionRecordRepository = predictionRecordRepository;
    }

    public PredictionRecordResponse predict(Long userId) {
        User patient = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + userId));

        PredictionRequest request = toPredictionRequest(patient);

        ResponseEntity<SurvivalPredictionResponse> response = restTemplate.postForEntity(
                resolvePredictEndpoint(),
                request,
                SurvivalPredictionResponse.class
        );

        if (response.getBody() == null) {
            throw new IllegalStateException("Modal API returned an empty response body");
        }

        PredictionRecord saved = savePredictionRecord(patient, response.getBody());
        return toResponse(saved);
    }

    public List<PredictionRecordResponse> search(String name) {
        return predictionRecordRepository.findAll().stream()
                .filter(record -> {
                    if (name == null || name.isBlank()) {
                        return true;
                    }
                    String patientName = record.getPatient().getName();
                    return patientName != null && patientName.toLowerCase().contains(name.trim().toLowerCase());
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PredictionRecordResponse updateActualOutcome(Long predictionId, OutcomeUpdateRequest request) {
        PredictionRecord record = predictionRecordRepository.findById(predictionId)
                .orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + predictionId));

        if (request.getActualOutcome() == null || request.getActualOutcome().isBlank()) {
            throw new IllegalArgumentException("actualOutcome is required");
        }

        record.setActualOutcome(request.getActualOutcome().trim());
        record.setActualOutcomeDate(request.getActualOutcomeDate());
        record.setActualOutcomeNotes(request.getActualOutcomeNotes());

        PredictionRecord saved = predictionRecordRepository.save(record);
        return toResponse(saved);
    }

    private PredictionRequest toPredictionRequest(User patient) {
        return new PredictionRequest(
                patient.getAge(),
                patient.getMutationCount(),
                patient.getTmb(),
                patient.getFga(),
                defaultUnknown(patient.getSex()),
                defaultUnknown(patient.getRace()),
                defaultUnknown(patient.getEthnicity())
        );
    }

    private PredictionRecord savePredictionRecord(User patient, SurvivalPredictionResponse prediction) {
        SurvivalPredictionResponse.ClinicianOutput clinician = prediction.getClinicianOutput();
        SurvivalPredictionResponse.SurvivalProbabilities probs = clinician != null
                ? clinician.getSurvivalProbabilities()
                : null;

        PredictionRecord record = new PredictionRecord();
        record.setPatient(patient);
        record.setSurvival6mo(probs != null ? probs.getSixMonths() : null);
        record.setSurvival12mo(probs != null ? probs.getTwelveMonths() : null);
        record.setSurvival24mo(probs != null ? probs.getTwentyFourMonths() : null);
        record.setRiskScore(clinician != null ? clinician.getRiskScore() : null);
        record.setRiskGroup(clinician != null ? clinician.getRiskGroup() : null);
        record.setEstimatedMedianSurvivalMonths(clinician != null ? clinician.getEstimatedMedianSurvivalMonths() : null);
        record.setPlainLanguageSummary(clinician != null ? clinician.getPlainLanguageSummary() : null);
        record.setKeyDrivers(clinician != null && clinician.getKeyDrivers() != null
                ? String.join(", ", clinician.getKeyDrivers())
                : null);
        record.setTechnicalOutput(prediction.getTechnicalOutput() != null ? prediction.getTechnicalOutput().toString() : null);
        record.setExpectedOutcome(buildExpectedOutcome(record));
        return predictionRecordRepository.save(record);
    }

    private String buildExpectedOutcome(PredictionRecord record) {
        String risk = record.getRiskGroup() == null ? "Unknown" : record.getRiskGroup();
        Double twelveMonth = record.getSurvival12mo();
        if (twelveMonth == null) {
            return "Risk Group: " + risk;
        }
        int pct12 = (int) Math.round(twelveMonth * 100.0);
        return "Risk Group: " + risk + ", Expected 12-month survival: " + pct12 + "%";
    }

    private PredictionRecordResponse toResponse(PredictionRecord record) {
        PredictionRecordResponse response = new PredictionRecordResponse();
        response.setPredictionId(record.getId());
        response.setPatientId(record.getPatient().getId());
        response.setPatientName(record.getPatient().getName());
        response.setPatientAge(record.getPatient().getAge());
        response.setSurvival6mo(record.getSurvival6mo());
        response.setSurvival12mo(record.getSurvival12mo());
        response.setSurvival24mo(record.getSurvival24mo());
        response.setRiskScore(record.getRiskScore());
        response.setRiskGroup(record.getRiskGroup());
        response.setEstimatedMedianSurvivalMonths(record.getEstimatedMedianSurvivalMonths());
        response.setPlainLanguageSummary(record.getPlainLanguageSummary());
        response.setKeyDrivers(record.getKeyDrivers());
        response.setTechnicalOutput(record.getTechnicalOutput());
        response.setExpectedOutcome(record.getExpectedOutcome());
        response.setActualOutcome(record.getActualOutcome());
        response.setActualOutcomeDate(record.getActualOutcomeDate());
        response.setActualOutcomeNotes(record.getActualOutcomeNotes());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private String resolvePredictEndpoint() {
        if (modalApiUrl == null || modalApiUrl.isBlank()) {
            throw new IllegalStateException("MODAL_API_URL is not configured");
        }
        return modalApiUrl.trim();
    }

    private String defaultUnknown(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value;
    }
}
