package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private final GitHubAuthService authService;
    private final ReviewService reviewService;
    private final ReviewHistoryService reviewHistoryService;

    public GitHubService(
            GitHubAuthService authService,
            ReviewService reviewService,
            ReviewHistoryService reviewHistoryService) {

        this.authService = authService;
        this.reviewService = reviewService;
        this.reviewHistoryService = reviewHistoryService;
    }

    public void reviewPullRequest(
            String owner,
            String repository,
            int pullNumber,
            long installationId,
            String commitSha) {

        if (reviewHistoryService.alreadyReviewed(
                owner,
                repository,
                pullNumber,
                commitSha)) {

            System.out.println(
                    "Skipping PR #" + pullNumber
                            + " because commit "
                            + commitSha
                            + " was already reviewed."
            );

            return;
        }

        String token =
                authService.createInstallationToken(
                        installationId
                );

        RestClient client = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(
                        "Authorization",
                        "Bearer " + token
                )
                .defaultHeader(
                        "Accept",
                        "application/vnd.github+json"
                )
                .build();

        JsonNode files = client
                .get()
                .uri(
                        "/repos/{owner}/{repo}/pulls/{pull}/files",
                        owner,
                        repository,
                        pullNumber
                )
                .retrieve()
                .body(JsonNode.class);

        if (files == null || !files.isArray()) {

            System.err.println(
                    "GitHub returned no PR files."
            );

            return;
        }

        StringBuilder reviewInput =
                new StringBuilder();

        for (JsonNode file : files) {

            String filename =
                    file.path("filename").asText();

            String patch =
                    file.path("patch").asText();

            if (patch == null || patch.isBlank()) {
                continue;
            }

            reviewInput
                    .append("\n\nFILE: ")
                    .append(filename)
                    .append("\n")
                    .append(patch);
        }

        if (reviewInput.isEmpty()) {

            System.out.println(
                    "No reviewable code changes."
            );

            return;
        }

        System.out.println(
                "Sending PR #" + pullNumber
                        + " to CodeGuard review engine..."
        );

        ReviewResponse review =
                reviewService.reviewCode(
                        reviewInput.toString()
                );

        String comment =
                buildComment(review);

        client.post()
                .uri(
                        "/repos/{owner}/{repo}/issues/{pull}/comments",
                        owner,
                        repository,
                        pullNumber
                )
                .body(
                        Map.of(
                                "body",
                                comment
                        )
                )
                .retrieve()
                .toBodilessEntity();

        reviewHistoryService.saveReview(
                owner,
                repository,
                pullNumber,
                commitSha,
                review
        );

        System.out.println(
                "CodeGuard comment posted successfully to PR #"
                        + pullNumber
        );

        System.out.println(
                "Review saved to MySQL."
        );
    }

    private String buildComment(
            ReviewResponse review) {

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                "## 🤖 CodeGuard AI Review\n\n"
        );

        builder.append("**Score: ")
                .append(review.getScore())
                .append("/10**\n\n");

        List<Finding> findings =
                review.getFindings();

        if (findings == null || findings.isEmpty()) {

            builder.append(
                    "✅ No meaningful issues detected."
            );

            return builder.toString();
        }

        for (Finding finding : findings) {

            builder.append("---\n\n");

            builder.append("### ")
                    .append(
                            severityEmoji(
                                    finding.getSeverity()
                            )
                    )
                    .append(" ")
                    .append(
                            finding.getSeverity()
                    )
                    .append(" — ")
                    .append(
                            finding.getTitle()
                    )
                    .append("\n\n");

            builder.append("**Category:** ")
                    .append(
                            finding.getCategory()
                    )
                    .append("\n\n");

            builder.append("**Line:** ")
                    .append(
                            finding.getLine()
                    )
                    .append("\n\n");

            builder.append(
                    finding.getExplanation()
            );

            builder.append("\n\n");

            builder.append(
                    "**Suggested fix:** "
            );

            builder.append(
                    finding.getSuggestion()
            );

            builder.append("\n\n");
        }

        return builder.toString();
    }

    private String severityEmoji(
            String severity) {

        if (severity == null) {
            return "ℹ️";
        }

        return switch (severity) {
            case "CRITICAL" -> "🔴";
            case "HIGH" -> "🟠";
            case "MEDIUM" -> "🟡";
            case "LOW" -> "🔵";
            default -> "ℹ️";
        };
    }
}