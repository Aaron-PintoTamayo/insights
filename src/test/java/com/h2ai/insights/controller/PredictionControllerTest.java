package com.h2ai.insights.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.h2ai.insights.dto.OutcomeUpdateRequest;
import com.h2ai.insights.dto.PredictionRecordResponse;
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
    void searchPredictions_shouldReturnRecords() throws Exception {
        when(predictionService.search("jane"))
                .thenReturn(List.of(sampleRecord(301L, 31L, "Jane Roe")));

        mockMvc.perform(get("/api/predictions/search")
                        .param("name", "jane"))
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
        request.setActualOutcomeNotes("Follow-up confirms no progression");

        mockMvc.perform(patch("/api/predictions/401/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualOutcome").value("Alive at 12 months"));
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
        response.setRiskScore(1.12);
        response.setRiskGroup("High Risk");
        response.setPlainLanguageSummary("Patient has elevated risk compared to baseline.");
        response.setExpectedOutcome("Risk Group: High Risk, Expected 12-month survival: 64%");
        response.setCreatedAt(LocalDateTime.of(2026, 4, 11, 10, 0));
        return response;
    }
}
