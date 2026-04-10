package com.h2ai.insights.service;

import com.h2ai.insights.entity.User;
import com.h2ai.insights.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TextractClient textractClient;

    // Upload an image, extract text via Textract, parse into a User, and save
    public User extractAndSaveUser(MultipartFile image) throws IOException {
        String extractedText = extractTextFromImage(image);
        User user = parseTextToUser(extractedText);
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    // --- Private helpers ---

    private String extractTextFromImage(MultipartFile image) throws IOException {
        SdkBytes imageBytes = SdkBytes.fromByteArray(image.getBytes());

        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(Document.builder().bytes(imageBytes).build())
                .build();

        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

        // TODO: join all detected lines into one string
        return null;
    }

    private User parseTextToUser(String text) {
        // TODO: parse the extracted text and map fields onto a User object
        User user = new User();
        return user;
    }
}
