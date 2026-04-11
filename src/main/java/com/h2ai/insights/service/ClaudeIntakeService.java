package com.h2ai.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h2ai.insights.entity.User;
import com.h2ai.insights.enums.Gender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaudeIntakeService {

    private static final String SYSTEM_PROMPT = "You are a clinical intake extraction engine. " +
            "Extract only the requested fields and return strict JSON only.";

    private static final String USER_PROMPT = "Extract these fields from the uploaded clinical file. " +
            "Return ONLY valid JSON with this exact shape: " +
            "{\"name\": string|null, \"age\": number|null, \"gender\": \"MALE\"|\"FEMALE\"|null, " +
            "\"diagnosisDate\": \"YYYY-MM-DD\"|null, \"priorMalignancy\": boolean|null, " +
            "\"priorTreatment\": boolean|null, \"ecogPerformanceStatus\": number|null}. " +
            "Do not include markdown or extra keys.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${anthropic.api.url}")
    private String anthropicApiUrl;

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Value("${anthropic.model}")
    private String anthropicModel;

    public User extractUserFromFile(MultipartFile file) throws IOException {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }

        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> requestBody = contentType.equals(MediaType.TEXT_PLAIN_VALUE)
                ? buildClaudeTextRequest(new String(bytes, StandardCharsets.UTF_8))
                : buildClaudeRequest(contentType, base64);

        String rawResponse = callClaude(requestBody);
        JsonNode extracted = extractJsonPayload(rawResponse);

        return toUser(extracted);
    }

    private Map<String, Object> buildClaudeTextRequest(String clinicalText) {
        Map<String, Object> promptPart = new LinkedHashMap<>();
        promptPart.put("type", "text");
        promptPart.put("text", USER_PROMPT + "\n\nClinical text:\n" + clinicalText);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(promptPart));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", anthropicModel);
        request.put("max_tokens", 1024);
        request.put("system", SYSTEM_PROMPT);
        request.put("messages", List.of(message));
        return request;
    }

    private Map<String, Object> buildClaudeRequest(String contentType, String base64Data) {
        Map<String, Object> mediaSource = new LinkedHashMap<>();
        mediaSource.put("type", "base64");
        mediaSource.put("media_type", contentType);
        mediaSource.put("data", base64Data);

        Map<String, Object> filePart = new LinkedHashMap<>();
        if (contentType.startsWith("image/")) {
            filePart.put("type", "image");
        } else {
            filePart.put("type", "document");
        }
        filePart.put("source", mediaSource);

        Map<String, Object> promptPart = new LinkedHashMap<>();
        promptPart.put("type", "text");
        promptPart.put("text", USER_PROMPT);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(promptPart, filePart));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", anthropicModel);
        request.put("max_tokens", 1024);
        request.put("system", SYSTEM_PROMPT);
        request.put("messages", List.of(message));
        return request;
    }

    private String callClaude(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    anthropicApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            String suffix = (body == null || body.isBlank()) ? "" : ": " + body;
            throw new IllegalStateException("Claude intake request failed with status " + ex.getRawStatusCode() + suffix);
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Claude intake request failed with status " + response.getStatusCode());
        }

        return response.getBody();
    }

    private JsonNode extractJsonPayload(String rawClaudeResponse) throws IOException {
        JsonNode root = objectMapper.readTree(rawClaudeResponse);
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new RuntimeException("Claude response did not contain content blocks");
        }

        String text = null;
        for (JsonNode block : content) {
            String candidate = block.path("text").asText(null);
            if (candidate != null && !candidate.isBlank()) {
                text = candidate;
                break;
            }
        }

        if (text == null) {
            throw new IllegalStateException("Claude response did not contain a text block");
        }

        text = text.replace("```json", "").replace("```", "").trim();

        return objectMapper.readTree(text);
    }

    private User toUser(JsonNode node) {
        User user = new User();

        user.setName(textOrNull(node, "name"));
        user.setAge(intOrNull(node, "age"));
        user.setGender(genderOrNull(node, "gender"));
        user.setDiagnosisDate(dateOrNull(node, "diagnosisDate"));
        user.setPriorMalignancy(boolOrNull(node, "priorMalignancy"));
        user.setPriorTreatment(boolOrNull(node, "priorTreatment"));
        user.setEcogPerformanceStatus(intOrNull(node, "ecogPerformanceStatus"));

        return user;
    }

    private String normalizeContentType(String provided, String filename) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }

        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asInt();
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asBoolean();
    }

    private LocalDate dateOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        try {
            return LocalDate.parse(child.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Gender genderOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        try {
            return Gender.valueOf(child.asText().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }
}
