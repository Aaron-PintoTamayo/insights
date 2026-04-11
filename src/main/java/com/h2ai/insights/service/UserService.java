package com.h2ai.insights.service;

import com.h2ai.insights.entity.User;
import com.h2ai.insights.exception.IncompletePatientInfoException;
import com.h2ai.insights.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ClaudeIntakeService claudeIntakeService;

    // Upload a file, extract structured fields with Claude, and save the User.
    public User extractAndSaveUser(MultipartFile image) throws IOException {
        User user = claudeIntakeService.extractUserFromFile(image);
        List<String> missingFields = findMissingRequiredFields(user);
        if (!missingFields.isEmpty()) {
            throw new IncompletePatientInfoException(missingFields);
        }
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchPatientsByName(String name) {
        if (isBlank(name)) {
            return userRepository.findAll();
        }
        return userRepository.findByNameContainingIgnoreCaseOrderByNameAsc(name.trim());
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private List<String> findMissingRequiredFields(User user) {
        List<String> missing = new ArrayList<>();

        if (isBlank(user.getName())) missing.add("name");
        if (user.getAge() == null) missing.add("age");
        if (user.getGender() == null) missing.add("gender");
        if (user.getDiagnosisDate() == null) missing.add("diagnosisDate");
        if (user.getPriorMalignancy() == null) missing.add("priorMalignancy");
        if (user.getPriorTreatment() == null) missing.add("priorTreatment");
        if (user.getEcogPerformanceStatus() == null) missing.add("ecogPerformanceStatus");

        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
