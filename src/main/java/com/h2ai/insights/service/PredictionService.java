package com.h2ai.insights.service;

import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
import com.h2ai.insights.dto.SurvivalPredictionResponse;
import com.h2ai.insights.dto.TrainingSyncResponse;
import com.h2ai.insights.entity.PredictionRecord;
import com.h2ai.insights.entity.User;
import com.h2ai.insights.repository.PredictionRecordRepository;
import com.h2ai.insights.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public List<PredictionRecordResponse> predictAll(String name) {
        List<User> patients = (name == null || name.isBlank())
                ? userRepository.findAll()
                : userRepository.findByNameContainingIgnoreCaseOrderByNameAsc(name.trim());

        if (patients.isEmpty()) {
            return List.of();
        }

        List<PredictionRequest> requestPatients = patients.stream().map(this::toPredictionRequest).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patients", requestPatients);

        ResponseEntity<SurvivalPredictionResponse[]> response = restTemplate.postForEntity(
                resolveBulkPredictEndpoint(),
                payload,
                SurvivalPredictionResponse[].class
        );

        SurvivalPredictionResponse[] predictions = response.getBody();
        if (predictions == null) {
            throw new IllegalStateException("Modal bulk API returned an empty response body");
        }
        if (predictions.length != patients.size()) {
            throw new IllegalStateException("Modal bulk response size does not match patient count");
        }

        List<PredictionRecordResponse> records = new ArrayList<>();
        for (int i = 0; i < patients.size(); i++) {
            PredictionRecord saved = savePredictionRecord(patients.get(i), predictions[i]);
            records.add(toResponse(saved));
        }
        return records;
    }

    public List<PredictionRecordResponse> search(
            String name,
            Integer minAge,
            Integer maxAge,
            String riskGroup,
            String actualOutcome
    ) {
        Specification<PredictionRecord> spec = (root, query, cb) -> cb.conjunction();

        if (name != null && !name.isBlank()) {
            String namePattern = "%" + name.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(
                    cb.lower(root.join("patient").get("name")),
                    namePattern
            ));
        }

        if (minAge != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(
                    root.join("patient").get("age"),
                    minAge
            ));
        }

        if (maxAge != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(
                    root.join("patient").get("age"),
                    maxAge
            ));
        }

        if (riskGroup != null && !riskGroup.isBlank()) {
            String riskPattern = "%" + riskGroup.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("riskGroup")), riskPattern));
        }

        if (actualOutcome != null && !actualOutcome.isBlank()) {
            String outcomePattern = "%" + actualOutcome.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("actualOutcome")), outcomePattern));
        }

        return predictionRecordRepository.findAll(spec).stream().map(this::toResponse).toList();
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
        record.setOverallSurvivalMonths(request.getOverallSurvivalMonths());
        record.setDeceased(request.getDeceased());
        record.setFractionGenomeAltered(request.getFractionGenomeAltered());
        record.setMutationCount(request.getMutationCount());
        record.setTmbNonsynonymous(request.getTmbNonsynonymous());
        record.setYearOfDiagnosis(request.getYearOfDiagnosis());

        PredictionRecord saved = predictionRecordRepository.save(record);
        return toResponse(saved);
    }

    public TrainingSyncResponse syncLabeledOutcomesToRetrainingApi() {
        List<PredictionRecord> labeled = predictionRecordRepository.findByActualOutcomeIsNotNull();
        if (labeled.isEmpty()) {
            return new TrainingSyncResponse(0, 0, "No labeled outcomes available to sync");
        }

        int sent = 0;
        for (PredictionRecord record : labeled) {
            if (!isReadyForRetrain(record)) {
                continue;
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    resolveRetrainEndpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(toRetrainPayload(record)),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Retraining API sync failed with status " + response.getStatusCode());
            }

            record.setRetrainSyncedAt(LocalDateTime.now());
            predictionRecordRepository.save(record);
            sent++;
        }

        return new TrainingSyncResponse(sent, labeled.size(), "Synced labeled outcomes to retraining API");
    }

    private PredictionRequest toPredictionRequest(User patient) {
        return new PredictionRequest(
                patient.getAge(),
                patient.getGender() != null ? patient.getGender().name() : null,
                patient.getPriorMalignancy(),
                patient.getPriorTreatment(),
                patient.getEcogPerformanceStatus()
        );
    }

    private PredictionRecord savePredictionRecord(User patient, SurvivalPredictionResponse prediction) {
        PredictionRecord record = new PredictionRecord();
        record.setPatient(patient);
        record.setSurvival6mo(prediction.getSurvival6mo());
        record.setSurvival12mo(prediction.getSurvival12mo());
        record.setSurvival24mo(prediction.getSurvival24mo());
        record.setPartialHazard(prediction.getPartialHazard());
        record.setRiskGroup(prediction.getRiskGroup());
        record.setInterpretation(prediction.getInterpretation());
        record.setSurvivalCurvePng(prediction.getSurvivalCurvePng());
        record.setExpectedOutcome(buildExpectedOutcome(prediction));
        return predictionRecordRepository.save(record);
    }

    private String buildExpectedOutcome(SurvivalPredictionResponse prediction) {
        String risk = prediction.getRiskGroup() == null ? "Unknown" : prediction.getRiskGroup();
        Double twelveMonth = prediction.getSurvival12mo();
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
        response.setPartialHazard(record.getPartialHazard());
        response.setRiskGroup(record.getRiskGroup());
        response.setInterpretation(record.getInterpretation());
        response.setSurvivalCurvePng(record.getSurvivalCurvePng());
        response.setExpectedOutcome(record.getExpectedOutcome());
        response.setActualOutcome(record.getActualOutcome());
        response.setActualOutcomeDate(record.getActualOutcomeDate());
        response.setActualOutcomeNotes(record.getActualOutcomeNotes());
        response.setOverallSurvivalMonths(record.getOverallSurvivalMonths());
        response.setDeceased(record.getDeceased());
        response.setFractionGenomeAltered(record.getFractionGenomeAltered());
        response.setMutationCount(record.getMutationCount());
        response.setTmbNonsynonymous(record.getTmbNonsynonymous());
        response.setYearOfDiagnosis(record.getYearOfDiagnosis());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private boolean isReadyForRetrain(PredictionRecord record) {
        return record.getOverallSurvivalMonths() != null
                && record.getDeceased() != null
                && record.getFractionGenomeAltered() != null
                && record.getMutationCount() != null
                && record.getTmbNonsynonymous() != null
                && record.getYearOfDiagnosis() != null;
    }

    private Map<String, Object> toRetrainPayload(PredictionRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("age", record.getPatient().getAge());
        data.put("gender", record.getPatient().getGender() != null ? record.getPatient().getGender().name() : null);
        data.put("prior_malignancy", record.getPatient().getPriorMalignancy());
        data.put("prior_treatment", record.getPatient().getPriorTreatment());
        data.put("overall_survival_months", record.getOverallSurvivalMonths());
        data.put("deceased", record.getDeceased());
        data.put("fraction_genome_altered", record.getFractionGenomeAltered());
        data.put("mutation_count", record.getMutationCount());
        data.put("tmb_nonsynonymous", record.getTmbNonsynonymous());
        data.put("year_of_diagnosis", record.getYearOfDiagnosis());
        return data;
    }

    private String resolvePredictEndpoint() {
        return resolveBaseModalUrl() + "/predict";
    }

    private String resolveBulkPredictEndpoint() {
        return resolveBaseModalUrl() + "/predict/bulk";
    }

    private String resolveRetrainEndpoint() {
        return resolveBaseModalUrl() + "/outcomes/retrain";
    }

    private String resolveBaseModalUrl() {
        if (modalApiUrl == null || modalApiUrl.isBlank()) {
            throw new IllegalStateException("MODAL_API_URL is not configured");
        }

        String base = modalApiUrl.trim();
        if (base.endsWith("/predict")) {
            base = base.substring(0, base.length() - "/predict".length());
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
