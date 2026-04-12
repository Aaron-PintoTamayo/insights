package com.h2ai.insights.controller;

import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
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

    @GetMapping("/search")
    public ResponseEntity<List<PredictionRecordResponse>> searchPredictions(
            @RequestParam(value = "name", required = false) String name
    ) {
        return ResponseEntity.ok(predictionService.search(name));
    }

    @PatchMapping("/{predictionId}/outcome")
    public ResponseEntity<PredictionRecordResponse> updateOutcome(
            @PathVariable Long predictionId,
            @RequestBody OutcomeUpdateRequest request
    ) {
        return ResponseEntity.ok(predictionService.updateActualOutcome(predictionId, request));
    }
}
