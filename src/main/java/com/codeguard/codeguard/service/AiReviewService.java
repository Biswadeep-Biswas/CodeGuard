package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiReviewService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AiReviewService(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public List<Finding> reviewCode(String code) {

        List<Finding> findings = new ArrayList<>();

        try {

            Map<String, Object> findingSchema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "severity", Map.of(
                                    "type", "string",
                                    "enum", List.of(
                                            "CRITICAL",
                                            "HIGH",
                                            "MEDIUM",
                                            "LOW",
                                            "INFO"
                                    )
                            ),
                            "category", Map.of(
                                    "type", "string"
                            ),
                            "title", Map.of(
                                    "type", "string"
                            ),
                            "explanation", Map.of(
                                    "type", "string"
                            ),
                            "suggestion", Map.of(
                                    "type", "string"
                            ),
                            "line", Map.of(
                                    "type", "integer"
                            )
                    ),
                    "required", List.of(
                            "severity",
                            "category",
                            "title",
                            "explanation",
                            "suggestion",
                            "line"
                    )
            );

            Map<String, Object> responseSchema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "findings", Map.of(
                                    "type", "array",
                                    "items", findingSchema
                            )
                    ),
                    "required", List.of("findings")
            );

            String prompt = """
                    You are a senior software engineer performing a code review.

                    Analyze the source code below.

                    Find real and actionable issues involving:

                    - functional bugs
                    - security vulnerabilities
                    - incorrect exception handling
                    - performance problems
                    - maintainability problems
                    - dangerous programming practices

                    Do not report purely stylistic preferences.

                    Severity must be one of:
                    CRITICAL, HIGH, MEDIUM, LOW, INFO.

                    Line numbers start at 1.

                    If there are no meaningful problems, return an empty findings array.

                    SOURCE CODE:

                    """ + code;

            Map<String, Object> requestBody = Map.of(

                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(
                                            Map.of(
                                                    "text",
                                                    prompt
                                            )
                                    )
                            )
                    ),

                    "generationConfig", Map.of(
                            "responseMimeType",
                            "application/json",
                            "responseSchema",
                            responseSchema
                    )
            );

            JsonNode response = restClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/gemini-3.5-flash:generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                System.err.println("Gemini returned null response.");
                return findings;
            }

            JsonNode candidates = response.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                System.err.println("Gemini returned no candidates:");
                System.err.println(response);
                return findings;
            }

            String outputText = candidates
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            if (outputText == null || outputText.isBlank()) {
                System.err.println("Gemini returned empty output.");
                System.err.println(response);
                return findings;
            }

            System.out.println("Gemini raw response:");
            System.out.println(outputText);

            JsonNode result =
                    objectMapper.readTree(outputText);

            JsonNode aiFindings =
                    result.path("findings");

            if (!aiFindings.isArray()) {
                System.err.println(
                        "Gemini JSON did not contain a findings array."
                );
                return findings;
            }

            for (JsonNode item : aiFindings) {

                Finding finding = new Finding(
                        item.path("severity").asText(),
                        item.path("category").asText(),
                        item.path("title").asText(),
                        item.path("explanation").asText(),
                        item.path("suggestion").asText(),
                        item.path("line").asInt()
                );

                findings.add(finding);
            }

        } catch (Exception e) {

            System.err.println(
                    "Gemini AI review failed: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }

        return findings;
    }
}