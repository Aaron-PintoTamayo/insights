package com.h2ai.insights.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
import com.h2ai.insights.dto.TrainingSyncResponse;
import com.h2ai.insights.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PredictionService predictionService;

    @Test
    void predictSingle_shouldReturnPredictionRecord() throws Exception {
        when(predictionService.predict(10L)).thenReturn(sampleRecord(101L, 10L, "Jane Doe"));

        mockMvc.perform(post("/api/predictions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").value(101))
                .andExpect(jsonPath("$.patientName").value("Jane Doe"));
    }

    @Test
    void predictSingle_aliasRoute_shouldReturnPredictionRecord() throws Exception {
        when(predictionService.predict(11L)).thenReturn(sampleRecord(102L, 11L, "Alex Park"));

        mockMvc.perform(post("/api/predict/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionId").value(102));
    }

    @Test
    void predictAll_shouldReturnBulkRecords() throws Exception {
        when(predictionService.predictAll(eq("smith")))
                .thenReturn(List.of(sampleRecord(201L, 21L, "Bob Smith"), sampleRecord(202L, 22L, "Ana Smith")));

        mockMvc.perform(post("/api/predictions/predict-all").param("name", "smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predictionId").value(201))
                .andExpect(jsonPath("$[1].predictionId").value(202));
    }

    @Test
    void searchPredictions_shouldReturnRecords() throws Exception {
        when(predictionService.search("jane", 30, 80, "High", "Alive"))
                .thenReturn(List.of(sampleRecord(301L, 31L, "Jane Roe")));

        mockMvc.perform(get("/api/predictions/search")
                        .param("name", "jane")
                        .param("minAge", "30")
                        .param("maxAge", "80")
                        .param("riskGroup", "High")
                        .param("actualOutcome", "Alive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientName").value("Jane Roe"));
    }

    @Test
    void updateOutcome_shouldReturnUpdatedRecord() throws Exception {
        PredictionRecordResponse updated = sampleRecord(401L, 41L, "Nina Cole");
        updated.setActualOutcome("Alive at 12 months");

        when(predictionService.updateActualOutcome(eq(401L), any(OutcomeUpdateRequest.class))).thenReturn(updated);

        OutcomeUpdateRequest request = new OutcomeUpdateRequest();
        request.setActualOutcome("Alive at 12 months");
        request.setActualOutcomeDate(LocalDate.of(2026, 1, 10));
        request.setOverallSurvivalMonths(12.0);
        request.setDeceased(false);

        mockMvc.perform(patch("/api/predictions/401/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualOutcome").value("Alive at 12 months"));
    }

    @Test
    void syncTraining_shouldReturnSummary() throws Exception {
        when(predictionService.syncLabeledOutcomesToRetrainingApi())
                .thenReturn(new TrainingSyncResponse(3, 5, "Synced labeled outcomes to retraining API"));

        mockMvc.perform(post("/api/predictions/sync-training"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentRecords").value(3))
                .andExpect(jsonPath("$.totalLabeledRecords").value(5));
    }

    private PredictionRecordResponse sampleRecord(Long predictionId, Long patientId, String patientName) {
        PredictionRecordResponse response = new PredictionRecordResponse();
        response.setPredictionId(predictionId);
        response.setPatientId(patientId);
        response.setPatientName(patientName);
        response.setPatientAge(61);
        response.setSurvival6mo(0.82);
        response.setSurvival12mo(0.64);
        response.setSurvival24mo(0.41);
        response.setPartialHazard(1.12);
        response.setRiskGroup("High Risk");
        response.setInterpretation("Pseudo interpretation");
        response.setSurvivalCurvePng("base64_png");
        response.setExpectedOutcome("Risk Group: High Risk, Expected 12-month survival: 64%");
        response.setCreatedAt(LocalDateTime.of(2026, 4, 11, 10, 0));
        return response;
    }
}
