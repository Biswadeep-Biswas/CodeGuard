package com.codeguard.codeguard.service;

import com.codeguard.codeguard.exception.AiReviewException;
import com.codeguard.codeguard.model.Finding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiReviewService {

    private static final int MAX_ATTEMPTS = 3;

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

        Map<String, Object> requestBody =
                buildRequestBody(code);

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                System.out.println(
                        "Gemini request attempt "
                                + attempt
                                + "/"
                                + MAX_ATTEMPTS
                );

                JsonNode response =
                        callGemini(requestBody);

                List<Finding> findings =
                        parseResponse(response);

                System.out.println(
                        "Gemini review successful on attempt "
                                + attempt
                );

                return findings;

            } catch (RestClientResponseException e) {

                int status =
                        e.getStatusCode().value();

                System.err.println(
                        "Gemini HTTP error "
                                + status
                                + " on attempt "
                                + attempt
                );

                System.err.println(
                        e.getResponseBodyAsString()
                );

                boolean retryable =
                        status == 429 ||
                        status == 503;

                if (!retryable) {

                    throw new AiReviewException(
                            "Gemini returned HTTP "
                                    + status,
                            e
                    );
                }

                if (attempt == MAX_ATTEMPTS) {

                    throw new AiReviewException(
                            "Gemini unavailable after "
                                    + MAX_ATTEMPTS
                                    + " attempts.",
                            e
                    );
                }

                waitBeforeRetry(attempt);

            } catch (AiReviewException e) {

                throw e;

            } catch (Exception e) {

                throw new AiReviewException(
                        "Gemini review failed unexpectedly.",
                        e
                );
            }
        }

        throw new AiReviewException(
                "Gemini review failed."
        );
    }

    private JsonNode callGemini(
            Map<String, Object> requestBody) {

        JsonNode response = restClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/v1beta/models/gemini-3.5-flash:generateContent"
                        )
                        .queryParam(
                                "key",
                                apiKey
                        )
                        .build())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {

            throw new AiReviewException(
                    "Gemini returned a null response."
            );
        }

        return response;
    }

    private List<Finding> parseResponse(
            JsonNode response) {

        JsonNode candidates =
                response.path("candidates");

        if (!candidates.isArray()
                || candidates.isEmpty()) {

            throw new AiReviewException(
                    "Gemini returned no candidates."
            );
        }

        String outputText =
                candidates
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText();

        if (outputText == null
                || outputText.isBlank()) {

            throw new AiReviewException(
                    "Gemini returned empty output."
            );
        }

        System.out.println(
                "Gemini raw response:"
        );

        System.out.println(
                outputText
        );

        try {

            JsonNode result =
                    objectMapper.readTree(
                            outputText
                    );

            JsonNode aiFindings =
                    result.path("findings");

            if (!aiFindings.isArray()) {

                throw new AiReviewException(
                        "Gemini response did not contain a findings array."
                );
            }

            List<Finding> findings =
                    new ArrayList<>();

            for (JsonNode item : aiFindings) {

                Finding finding =
                        new Finding(
                                item.path(
                                        "severity"
                                ).asText(),

                                item.path(
                                        "category"
                                ).asText(),

                                item.path(
                                        "title"
                                ).asText(),

                                item.path(
                                        "explanation"
                                ).asText(),

                                item.path(
                                        "suggestion"
                                ).asText(),

                                item.path(
                                        "filePath"
                                ).asText(),

                                item.path(
                                        "line"
                                ).asInt()
                        );

                findings.add(finding);
            }

            return findings;

        } catch (AiReviewException e) {

            throw e;

        } catch (Exception e) {

            throw new AiReviewException(
                    "Could not parse Gemini response.",
                    e
            );
        }
    }

    private void waitBeforeRetry(
            int attempt) {

        long delayMilliseconds =
                attempt == 1
                        ? 2000
                        : 4000;

        System.out.println(
                "Retrying Gemini in "
                        + delayMilliseconds / 1000
                        + " seconds..."
        );

        try {

            Thread.sleep(
                    delayMilliseconds
            );

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            throw new AiReviewException(
                    "Gemini retry interrupted.",
                    e
            );
        }
    }

    private Map<String, Object> buildRequestBody(
            String code) {

        Map<String, Object> findingSchema =
                Map.of(
                        "type",
                        "object",

                        "properties",
                        Map.of(
                                "severity",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "CRITICAL",
                                                "HIGH",
                                                "MEDIUM",
                                                "LOW",
                                                "INFO"
                                        )
                                ),

                                "category",
                                Map.of(
                                        "type",
                                        "string"
                                ),

                                "title",
                                Map.of(
                                        "type",
                                        "string"
                                ),

                                "filePath",
                                Map.of(
                                        "type",
                                        "string"
                                ),

                                "explanation",
                                Map.of(
                                        "type",
                                        "string"
                                ),

                                "suggestion",
                                Map.of(
                                        "type",
                                        "string"
                                ),

                                "line",
                                Map.of(
                                        "type",
                                        "integer"
                                )
                        ),

                        "required",
                        List.of(
                                "severity",
                                "category",
                                "title",
                                "filePath",
                                "explanation",
                                "suggestion",
                                "line"
                        )
                );

        Map<String, Object> responseSchema =
                Map.of(
                        "type",
                        "object",

                        "properties",
                        Map.of(
                                "findings",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        findingSchema
                                )
                        ),

                        "required",
                        List.of(
                                "findings"
                        )
                );

        String prompt = """
                You are a senior software engineer performing a code review.

                Analyze the source code changes below.

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

                IMPORTANT FILE AND LINE NUMBER RULES:

                The input is divided into files.

                Each file begins with a line formatted exactly like:

                FILE: <repository-relative-file-path>

                Source lines are then formatted exactly like:

                NEW LINE <number>: <source code>

                When reporting a finding:

                1. filePath must exactly match the value after FILE:.

                2. line must be exactly the number shown after NEW LINE
                   for the problematic source line.

                3. Do not calculate line numbers yourself.

                4. Do not count lines in this prompt.

                5. Do not use the position of a line inside the combined input.

                6. Do not invent a line number.

                7. Only report findings for source lines that are actually
                   present as NEW LINE entries in the supplied input.

                Example input:

                FILE: src/main/java/com/example/LoginService.java
                NEW LINE 10: public void login(String password) {
                NEW LINE 11:     System.out.println(password);
                NEW LINE 12: }

                A finding for the println line must use:

                filePath:
                src/main/java/com/example/LoginService.java

                line:
                11

                If there are no meaningful problems,
                return an empty findings array.

                SOURCE CODE CHANGES:

                """ + code;

        return Map.of(

                "contents",
                List.of(
                        Map.of(
                                "role",
                                "user",

                                "parts",
                                List.of(
                                        Map.of(
                                                "text",
                                                prompt
                                        )
                                )
                        )
                ),

                "generationConfig",
                Map.of(
                        "responseMimeType",
                        "application/json",

                        "responseSchema",
                        responseSchema
                )
        );
    }
}