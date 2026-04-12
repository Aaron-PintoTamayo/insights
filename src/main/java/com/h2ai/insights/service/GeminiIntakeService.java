package com.h2ai.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.h2ai.insights.entity.User;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiIntakeService {

    private static final String SYSTEM_PROMPT = "You are a clinical intake extraction engine. " +
            "Extract only the requested fields and return strict JSON only.";

        private static final String USER_PROMPT = "Extract these fields from the uploaded clinical file. " +
            "Return ONLY valid JSON with this exact shape and exact key names: " +
            "{\"name\": string|null, \"age\": number|null, \"mutation_count\": number|null, " +
            "\"TMB\": number|null, \"fga\": number|null, \"sex\": \"Male\"|\"Female\"|\"Unknown\"|null, " +
            "\"race\": \"WHITE\"|\"BLACK OR AFRICAN AMERICAN\"|\"ASIAN\"|" +
            "\"AMERICAN INDIAN OR ALASKA NATIVE\"|\"NATIVE HAWAIIAN OR OTHER PACIFIC ISLANDER\"|\"Unknown\"|null, " +
            "\"ethnicity\": \"NOT HISPANIC OR LATINO\"|\"HISPANIC OR LATINO\"|\"Unknown\"|null}. " +
            "Do not include markdown or extra keys. " +
            "Map aliases to these exact keys (for example mutationCount->mutation_count, mutation count->mutation_count, " +
            "tmbNonsynonymous->TMB, fractionGenomeAltered->fga, gender->sex). " +
            "Normalize sex values to exactly Male, Female, or Unknown; preserve race and ethnicity labels exactly from the allowed values. " +
            "If a numeric value has units or percent symbols, strip units and return only the numeric value.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    public User extractUserFromFile(MultipartFile file) throws IOException {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> requestBody = contentType.equals(MediaType.TEXT_PLAIN_VALUE)
                ? buildGeminiTextRequest(new String(bytes, StandardCharsets.UTF_8))
                : buildGeminiRequest(contentType, base64);

        String rawResponse = callGemini(requestBody);
        JsonNode extracted = extractJsonPayload(rawResponse);

        return toUser(extracted);
    }

    private Map<String, Object> buildGeminiTextRequest(String clinicalText) {
        Map<String, Object> promptPart = new LinkedHashMap<>();
        promptPart.put("text", USER_PROMPT + "\n\nClinical text:\n" + clinicalText);

        Map<String, Object> systemPart = new LinkedHashMap<>();
        systemPart.put("text", SYSTEM_PROMPT);

        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", List.of(promptPart));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("system_instruction", Map.of("parts", List.of(systemPart)));
        request.put("contents", List.of(userContent));
        return request;
    }

    private Map<String, Object> buildGeminiRequest(String contentType, String base64Data) {
        Map<String, Object> filePart = new LinkedHashMap<>();
        filePart.put("inline_data", Map.of("mime_type", contentType, "data", base64Data));

        Map<String, Object> promptPart = new LinkedHashMap<>();
        promptPart.put("text", USER_PROMPT);

        Map<String, Object> systemPart = new LinkedHashMap<>();
        systemPart.put("text", SYSTEM_PROMPT);

        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", List.of(promptPart, filePart));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("system_instruction", Map.of("parts", List.of(systemPart)));
        request.put("contents", List.of(userContent));
        return request;
    }

    private String callGemini(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    resolveGeminiEndpoint(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            String suffix = (body == null || body.isBlank()) ? "" : ": " + body;
            throw new IllegalStateException("Gemini intake request failed with status " + ex.getRawStatusCode() + suffix);
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Gemini intake request failed with status " + response.getStatusCode());
        }

        return response.getBody();
    }

    private JsonNode extractJsonPayload(String rawGeminiResponse) throws IOException {
        JsonNode root = objectMapper.readTree(rawGeminiResponse);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini response did not contain candidates");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new RuntimeException("Gemini response did not contain content parts");
        }

        String text = null;
        for (JsonNode block : parts) {
            String candidate = block.path("text").asText(null);
            if (candidate != null && !candidate.isBlank()) {
                text = candidate;
                break;
            }
        }

        if (text == null) {
            throw new IllegalStateException("Gemini response did not contain a text block");
        }

        text = text.replace("```json", "").replace("```", "").trim();

        return objectMapper.readTree(text);
    }

    private String resolveGeminiEndpoint() {
        String base = geminiApiUrl == null || geminiApiUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : geminiApiUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;
    }

    private User toUser(JsonNode node) {
        User user = new User();

        user.setName(textOrNull(node, "name"));
        user.setAge(intOrNull(node, "age"));
        user.setMutationCount(intOrNull(node, "mutation_count"));
        user.setTmb(doubleOrNull(node, "TMB"));
        user.setFga(doubleOrNull(node, "fga"));
        user.setSex(normalizeUnknown(textOrNull(node, "sex")));
        user.setRace(normalizeUnknown(textOrNull(node, "race")));
        user.setEthnicity(normalizeUnknown(textOrNull(node, "ethnicity")));

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

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asDouble();
    }

    private String normalizeUnknown(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value;
    }
}
