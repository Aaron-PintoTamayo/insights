package com.h2ai.insights.controller;

import com.h2ai.insights.entity.User;
import com.h2ai.insights.enums.Gender;
import com.h2ai.insights.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void uploadPatientFile_shouldCreatePatient() throws Exception {
        User patient = samplePatient(1L, "John Smith");
        when(userService.extractAndSaveUser(any())).thenReturn(patient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "clinical pseudo note".getBytes()
        );

        mockMvc.perform(multipart("/api/users/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Smith"));
    }

    @Test
    void getAllUsers_shouldReturnList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(samplePatient(1L, "Alice Jones")));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice Jones"));
    }

    @Test
    void searchPatients_shouldReturnMatchesByName() throws Exception {
        when(userService.searchPatientsByName(eq("smith")))
                .thenReturn(List.of(samplePatient(2L, "Bob Smith")));

        mockMvc.perform(get("/api/users/search").param("name", "smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bob Smith"));
    }

    @Test
    void getUserById_shouldReturnPatient() throws Exception {
        when(userService.getUserById(3L)).thenReturn(samplePatient(3L, "Maria Lee"));

        mockMvc.perform(get("/api/users/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Maria Lee"));
    }

    private User samplePatient(Long id, String name) {
        User patient = new User();
        patient.setId(id);
        patient.setName(name);
        patient.setAge(61);
        patient.setGender(Gender.MALE);
        patient.setDiagnosisDate(LocalDate.of(2023, 1, 5));
        patient.setPriorMalignancy(false);
        patient.setPriorTreatment(true);
        patient.setEcogPerformanceStatus(1);
        return patient;
    }
}
