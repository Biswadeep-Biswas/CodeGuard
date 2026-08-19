package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
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
            List<Finding> findings) {

        String conclusion =
                determineConclusion(
                        findings
                );

        Map<String, Object> output =
                new LinkedHashMap<>();

        output.put(
                "title",
                buildTitle(
                        conclusion
                )
        );

        output.put(
                "summary",
                buildSummary(
                        score,
                        findings
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
                conclusion
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
                "CodeGuard check marked "
                        + conclusion.toUpperCase()
                        + "."
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
                "CodeGuard check marked FAILURE."
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

    /*
     * Decide whether CodeGuard should
     * pass, warn, or fail the PR check.
     */
    private String determineConclusion(
            List<Finding> findings) {

        if (findings == null
                || findings.isEmpty()) {

            return "success";
        }

        boolean hasMedium = false;

        for (Finding finding : findings) {

            String severity =
                    finding.getSeverity();

            if (severity == null) {
                continue;
            }

            switch (
                    severity.toUpperCase()
            ) {

                case "CRITICAL":
                case "HIGH":

                    return "failure";

                case "MEDIUM":

                    hasMedium = true;
                    break;

                default:
                    break;
            }
        }

        if (hasMedium) {
            return "neutral";
        }

        /*
         * Only LOW / INFO findings.
         */
        return "success";
    }

    private String buildTitle(
            String conclusion) {

        return switch (conclusion) {

            case "failure" ->
                    "CodeGuard found blocking issues";

            case "neutral" ->
                    "CodeGuard found issues to review";

            default ->
                    "CodeGuard review passed";
        };
    }

    private String buildSummary(
            int score,
            List<Finding> findings) {

        int findingCount =
                findings == null
                        ? 0
                        : findings.size();

        if (findingCount == 0) {

            return """
                    CodeGuard completed the AI review.

                    Score: %d/10

                    No meaningful issues were detected.
                    """.formatted(
                    score
            );
        }

        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        int info = 0;

        for (Finding finding : findings) {

            if (finding.getSeverity() == null) {
                continue;
            }

            switch (
                    finding.getSeverity()
                            .toUpperCase()
            ) {

                case "CRITICAL":
                    critical++;
                    break;

                case "HIGH":
                    high++;
                    break;

                case "MEDIUM":
                    medium++;
                    break;

                case "LOW":
                    low++;
                    break;

                default:
                    info++;
                    break;
            }
        }

        return """
                CodeGuard completed the AI review.

                Score: %d/10

                Total findings: %d

                CRITICAL: %d
                HIGH: %d
                MEDIUM: %d
                LOW: %d
                INFO: %d

                See the inline review comments for details.
                """.formatted(
                score,
                findingCount,
                critical,
                high,
                medium,
                low,
                info
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