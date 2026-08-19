package com.codeguard.codeguard.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GitHubCheckService {

    public Long createCheckRun(
            RestClient client,
            String owner,
            String repository,
            String commitSha) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "name",
                "CodeGuard AI Review"
        );

        body.put(
                "head_sha",
                commitSha
        );

        body.put(
                "status",
                "in_progress"
        );

        JsonNode response =
                client.post()
                        .uri(
                                "/repos/{owner}/{repo}/check-runs",
                                owner,
                                repository
                        )
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);

        if (response == null) {

            throw new RuntimeException(
                    "GitHub returned no check-run response."
            );
        }

        long checkRunId =
                response.path("id")
                        .asLong();

        if (checkRunId <= 0) {

            throw new RuntimeException(
                    "GitHub check-run ID missing."
            );
        }

        System.out.println(
                "Created CodeGuard check run #"
                        + checkRunId
        );

        return checkRunId;
    }

    public void markSuccess(
            RestClient client,
            String owner,
            String repository,
            Long checkRunId,
            int score,
            int findingCount) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "title",
                "CodeGuard review completed"
        );

        output.put(
                "summary",
                buildSuccessSummary(
                        score,
                        findingCount
                )
        );

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "status",
                "completed"
        );

        body.put(
                "conclusion",
                determineConclusion(
                        findingCount
                )
        );

        body.put(
                "output",
                output
        );

        updateCheckRun(
                client,
                owner,
                repository,
                checkRunId,
                body
        );

        System.out.println(
                "CodeGuard check marked complete."
        );
    }

    public void markFailure(
            RestClient client,
            String owner,
            String repository,
            Long checkRunId,
            String errorMessage) {

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "title",
                "CodeGuard review failed"
        );

        output.put(
                "summary",
                sanitizeError(
                        errorMessage
                )
        );

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "status",
                "completed"
        );

        body.put(
                "conclusion",
                "failure"
        );

        body.put(
                "output",
                output
        );

        updateCheckRun(
                client,
                owner,
                repository,
                checkRunId,
                body
        );

        System.out.println(
                "CodeGuard check marked FAILED."
        );
    }

    private void updateCheckRun(
            RestClient client,
            String owner,
            String repository,
            Long checkRunId,
            Map<String, Object> body) {

        client.patch()
                .uri(
                        "/repos/{owner}/{repo}/check-runs/{checkRunId}",
                        owner,
                        repository,
                        checkRunId
                )
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String determineConclusion(
            int findingCount) {

        /*
         * For now:
         *
         * no findings  -> success
         * findings     -> neutral
         *
         * This avoids blocking a PR merely because
         * CodeGuard found advisory issues.
         *
         * Later we can fail only for HIGH/CRITICAL.
         */
        if (findingCount == 0) {
            return "success";
        }

        return "neutral";
    }

    private String buildSuccessSummary(
            int score,
            int findingCount) {

        if (findingCount == 0) {

            return """
                    CodeGuard completed the AI review.

                    Score: %d/10

                    No meaningful issues were detected.
                    """.formatted(score);
        }

        return """
                CodeGuard completed the AI review.

                Score: %d/10

                Findings detected: %d

                See the pull request conversation and inline review comments for details.
                """.formatted(
                score,
                findingCount
        );
    }

    private String sanitizeError(
            String errorMessage) {

        if (errorMessage == null
                || errorMessage.isBlank()) {

            return "CodeGuard review failed for an unknown reason.";
        }

        if (errorMessage.length() > 2000) {

            return errorMessage.substring(
                    0,
                    2000
            );
        }

        return errorMessage;
    }
}