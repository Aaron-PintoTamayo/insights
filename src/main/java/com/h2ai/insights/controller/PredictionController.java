package com.h2ai.insights.controller;

import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
import com.h2ai.insights.dto.TrainingSyncResponse;
import com.h2ai.insights.service.PredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/predictions", "/api/predict"})
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<PredictionRecordResponse> predict(@PathVariable Long userId) {
        return ResponseEntity.ok(predictionService.predict(userId));
    }

    @PostMapping("/predict-all")
    public ResponseEntity<List<PredictionRecordResponse>> predictAll(
            @RequestParam(value = "name", required = false) String name
    ) {
        return ResponseEntity.ok(predictionService.predictAll(name));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PredictionRecordResponse>> searchPredictions(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "minAge", required = false) Integer minAge,
            @RequestParam(value = "maxAge", required = false) Integer maxAge,
            @RequestParam(value = "riskGroup", required = false) String riskGroup,
            @RequestParam(value = "actualOutcome", required = false) String actualOutcome
    ) {
        return ResponseEntity.ok(predictionService.search(name, minAge, maxAge, riskGroup, actualOutcome));
    }

    @PatchMapping("/{predictionId}/outcome")
    public ResponseEntity<PredictionRecordResponse> updateOutcome(
            @PathVariable Long predictionId,
            @RequestBody OutcomeUpdateRequest request
    ) {
        return ResponseEntity.ok(predictionService.updateActualOutcome(predictionId, request));
    }

    @PostMapping("/sync-training")
    public ResponseEntity<TrainingSyncResponse> syncTrainingData() {
        return ResponseEntity.ok(predictionService.syncLabeledOutcomesToRetrainingApi());
    }
}
