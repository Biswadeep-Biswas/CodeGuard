package com.codeguard.codeguard.service;

import com.codeguard.codeguard.entity.ReviewEntity;
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

        /*
         * Don't review the same successful commit twice.
         */
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

        ReviewEntity reviewEntity = null;

        try {

            /*
             * Create the database record BEFORE
             * beginning the actual review.
             */
            reviewEntity =
                    reviewHistoryService.startReview(
                            owner,
                            repository,
                            pullNumber,
                            commitSha
                    );

            System.out.println(
                    "Review #" + reviewEntity.getId()
                            + " marked PROCESSING."
            );

            /*
             * Authenticate as the GitHub App.
             */
            String token =
                    authService.createInstallationToken(
                            installationId
                    );

            RestClient client =
                    RestClient.builder()
                            .baseUrl(
                                    "https://api.github.com"
                            )
                            .defaultHeader(
                                    "Authorization",
                                    "Bearer " + token
                            )
                            .defaultHeader(
                                    "Accept",
                                    "application/vnd.github+json"
                            )
                            .build();

            /*
             * Fetch changed files from the PR.
             */
            JsonNode files =
                    client.get()
                            .uri(
                                    "/repos/{owner}/{repo}/pulls/{pull}/files",
                                    owner,
                                    repository,
                                    pullNumber
                            )
                            .retrieve()
                            .body(JsonNode.class);

            if (files == null
                    || !files.isArray()) {

                throw new RuntimeException(
                        "GitHub returned no PR files."
                );
            }

            /*
             * Build the source-code input that
             * will be sent to CodeGuard/Gemini.
             */
            StringBuilder reviewInput =
                    new StringBuilder();

            for (JsonNode file : files) {

                String filename =
                        file.path(
                                "filename"
                        ).asText();

                String patch =
                        file.path(
                                "patch"
                        ).asText();

                if (patch == null
                        || patch.isBlank()) {

                    continue;
                }

                reviewInput
                        .append("\n\nFILE: ")
                        .append(filename)
                        .append("\n")
                        .append(patch);
            }

            if (reviewInput.isEmpty()) {

                /*
                 * This is not technically a system
                 * failure. There simply wasn't
                 * anything useful to review.
                 */
                ReviewResponse emptyReview =
                        new ReviewResponse(
                                10,
                                List.of()
                        );

                reviewHistoryService.markSuccess(
                        reviewEntity.getId(),
                        emptyReview
                );

                System.out.println(
                        "No reviewable code changes."
                );

                System.out.println(
                        "Review marked SUCCESS."
                );

                return;
            }

            System.out.println(
                    "Sending PR #"
                            + pullNumber
                            + " to CodeGuard review engine..."
            );

            /*
             * AiReviewService now retries Gemini
             * automatically for temporary
             * 429/503 failures.
             */
            ReviewResponse review =
                    reviewService.reviewCode(
                            reviewInput.toString()
                    );

            /*
             * Build the GitHub summary comment.
             */
            String comment =
                    buildComment(review);

            /*
             * Post the CodeGuard review to GitHub.
             */
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

            System.out.println(
                    "CodeGuard comment posted successfully to PR #"
                            + pullNumber
            );

            /*
             * Only mark SUCCESS after the AI
             * review AND GitHub comment succeeded.
             */
            reviewHistoryService.markSuccess(
                    reviewEntity.getId(),
                    review
            );

            System.out.println(
                    "Review saved to MySQL and marked SUCCESS."
            );

        } catch (Exception e) {

            System.err.println(
                    "CodeGuard review failed for PR #"
                            + pullNumber
                            + ": "
                            + e.getMessage()
            );

            /*
             * If a PROCESSING database record was
             * successfully created, convert it to
             * FAILED.
             */
            if (reviewEntity != null
                    && reviewEntity.getId() != null) {

                try {

                    reviewHistoryService.markFailed(
                            reviewEntity.getId(),
                            getUsefulErrorMessage(e)
                    );

                    System.err.println(
                            "Review #"
                                    + reviewEntity.getId()
                                    + " marked FAILED."
                    );

                } catch (Exception databaseException) {

                    System.err.println(
                            "Could not mark review FAILED: "
                                    + databaseException.getMessage()
                    );

                    databaseException.printStackTrace();
                }
            }

            /*
             * Re-throw so the webhook background
             * process still knows something failed.
             */
            throw new RuntimeException(
                    "Pull request review failed.",
                    e
            );
        }
    }

    private String getUsefulErrorMessage(
            Exception exception) {

        StringBuilder message =
                new StringBuilder();

        Throwable current =
                exception;

        int depth = 0;

        while (current != null
                && depth < 5) {

            if (current.getMessage() != null
                    && !current.getMessage().isBlank()) {

                if (!message.isEmpty()) {
                    message.append(" -> ");
                }

                message.append(
                        current.getClass()
                                .getSimpleName()
                );

                message.append(": ");

                message.append(
                        current.getMessage()
                );
            }

            current =
                    current.getCause();

            depth++;
        }

        if (message.isEmpty()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message.toString();
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

        if (findings == null
                || findings.isEmpty()) {

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

            builder.append(
                    "**Category:** "
            );

            builder.append(
                    finding.getCategory()
            );

            builder.append("\n\n");

            builder.append(
                    "**Line:** "
            );

            builder.append(
                    finding.getLine()
            );

            builder.append("\n\n");

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

            case "CRITICAL" ->
                    "🔴";

            case "HIGH" ->
                    "🟠";

            case "MEDIUM" ->
                    "🟡";

            case "LOW" ->
                    "🔵";

            default ->
                    "ℹ️";
        };
    }
}