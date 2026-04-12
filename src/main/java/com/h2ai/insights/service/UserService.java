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
    private final GeminiIntakeService geminiIntakeService;

    // Upload a file, extract structured fields with Gemini, and save the User.
    public User extractAndSaveUser(MultipartFile image) throws IOException {
        User user = geminiIntakeService.extractUserFromFile(image);
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

        if (user.getAge() == null) missing.add("age");
        if (user.getMutationCount() == null) missing.add("mutation_count");
        if (user.getTmb() == null) missing.add("TMB");
        if (user.getFga() == null) missing.add("fga");

        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
