package com.codeguard.codeguard.service;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private final GitHubAuthService authService;
    private final ReviewService reviewService;
    private final ReviewHistoryService reviewHistoryService;
    private final GitHubCheckService githubCheckService;
    private final GitHubDiffParser githubDiffParser;

    public GitHubService(
            GitHubAuthService authService,
            ReviewService reviewService,
            ReviewHistoryService reviewHistoryService,
            GitHubCheckService githubCheckService,
            GitHubDiffParser githubDiffParser) {

        this.authService = authService;
        this.reviewService = reviewService;
        this.reviewHistoryService = reviewHistoryService;
        this.githubCheckService = githubCheckService;
        this.githubDiffParser = githubDiffParser;
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

        ReviewEntity reviewEntity = null;
        RestClient githubClient = null;
        Long checkRunId = null;

        try {

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

            String token =
                    authService.createInstallationToken(
                            installationId
                    );

            githubClient =
                    RestClient.builder()
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

            checkRunId =
                    githubCheckService.createCheckRun(
                            githubClient,
                            owner,
                            repository,
                            commitSha
                    );

            JsonNode files =
                    githubClient.get()
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

            StringBuilder reviewInput =
                    new StringBuilder();

            for (JsonNode file : files) {

                String filename =
                        file.path("filename")
                                .asText();

                String patch =
                        file.path("patch")
                                .asText();

                if (patch == null
                        || patch.isBlank()) {

                    continue;
                }

                String numberedPatch =
                        githubDiffParser
                                .convertToNumberedNewLines(
                                        filename,
                                        patch
                                );

                if (numberedPatch.isBlank()) {
                    continue;
                }

                reviewInput
                        .append("\n\n")
                        .append(numberedPatch);
            }

            if (reviewInput.isEmpty()) {

                ReviewResponse emptyReview =
                        new ReviewResponse(
                                10,
                                List.of()
                        );

                reviewHistoryService.markSuccess(
                        reviewEntity.getId(),
                        emptyReview
                );

                githubCheckService.markSuccess(
                        githubClient,
                        owner,
                        repository,
                        checkRunId,
                        10,
                        List.of()
                );

                System.out.println(
                        "No reviewable code changes."
                );

                return;
            }

            System.out.println(
                    "Sending PR #"
                            + pullNumber
                            + " to CodeGuard review engine..."
            );

            System.out.println(
                    "Normalized review input:"
            );

            System.out.println(
                    reviewInput
            );

            ReviewResponse review =
                    reviewService.reviewCode(
                            reviewInput.toString()
                    );

            int inlineCommentsPosted =
                    postInlineComments(
                            githubClient,
                            owner,
                            repository,
                            pullNumber,
                            commitSha,
                            review
                    );

            String summaryComment =
                    buildSummaryComment(
                            review,
                            inlineCommentsPosted
                    );

            githubClient.post()
                    .uri(
                            "/repos/{owner}/{repo}/issues/{pull}/comments",
                            owner,
                            repository,
                            pullNumber
                    )
                    .body(
                            Map.of(
                                    "body",
                                    summaryComment
                            )
                    )
                    .retrieve()
                    .toBodilessEntity();

            System.out.println(
                    "CodeGuard summary comment posted successfully to PR #"
                            + pullNumber
            );

            reviewHistoryService.markSuccess(
                    reviewEntity.getId(),
                    review
            );

            githubCheckService.markSuccess(
                    githubClient,
                    owner,
                    repository,
                    checkRunId,
                    review.getScore(),
                    review.getFindings()
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

            String usefulError =
                    getUsefulErrorMessage(
                            e
                    );

            if (reviewEntity != null
                    && reviewEntity.getId() != null) {

                try {

                    reviewHistoryService.markFailed(
                            reviewEntity.getId(),
                            usefulError
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
                }
            }

            if (githubClient != null
                    && checkRunId != null) {

                try {

                    githubCheckService.markFailure(
                            githubClient,
                            owner,
                            repository,
                            checkRunId,
                            usefulError
                    );

                } catch (Exception checkException) {

                    System.err.println(
                            "Could not update GitHub check: "
                                    + checkException.getMessage()
                    );
                }
            }

            throw new RuntimeException(
                    "Pull request review failed.",
                    e
            );
        }
    }

    private int postInlineComments(
            RestClient client,
            String owner,
            String repository,
            int pullNumber,
            String commitSha,
            ReviewResponse review) {

        List<Finding> findings =
                review.getFindings();

        if (findings == null
                || findings.isEmpty()) {

            return 0;
        }

        int posted = 0;

        for (Finding finding : findings) {

            if (finding.getFilePath() == null
                    || finding.getFilePath().isBlank()) {

                continue;
            }

            if (finding.getLine() <= 0) {
                continue;
            }

            try {

                client.post()
                        .uri(
                                "/repos/{owner}/{repo}/pulls/{pull}/comments",
                                owner,
                                repository,
                                pullNumber
                        )
                        .body(
                                Map.of(
                                        "body",
                                        buildInlineComment(
                                                finding
                                        ),
                                        "commit_id",
                                        commitSha,
                                        "path",
                                        finding.getFilePath(),
                                        "line",
                                        finding.getLine(),
                                        "side",
                                        "RIGHT"
                                )
                        )
                        .retrieve()
                        .toBodilessEntity();

                posted++;

                System.out.println(
                        "Inline comment posted: "
                                + finding.getFilePath()
                                + ":"
                                + finding.getLine()
                );

            } catch (HttpClientErrorException e) {

                System.err.println(
                        "Inline comment failed for "
                                + finding.getFilePath()
                                + ":"
                                + finding.getLine()
                                + " - HTTP "
                                + e.getStatusCode()
                );

                System.err.println(
                        e.getResponseBodyAsString()
                );

            } catch (Exception e) {

                System.err.println(
                        "Inline comment failed for "
                                + finding.getFilePath()
                                + ":"
                                + finding.getLine()
                                + " - "
                                + e.getMessage()
                );
            }
        }

        return posted;
    }

    private String buildInlineComment(
            Finding finding) {

        return severityEmoji(
                finding.getSeverity()
        )
                + " **"
                + finding.getSeverity()
                + " — "
                + finding.getTitle()
                + "**\n\n"
                + finding.getExplanation()
                + "\n\n"
                + "**Suggested fix:** "
                + finding.getSuggestion()
                + "\n\n"
                + "_Category: "
                + finding.getCategory()
                + "_";
    }

    private String buildSummaryComment(
            ReviewResponse review,
            int inlineCommentsPosted) {

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                "## 🤖 CodeGuard AI Review\n\n"
        );

        builder.append(
                "**Score: "
        );

        builder.append(
                review.getScore()
        );

        builder.append(
                "/10**\n\n"
        );

        List<Finding> findings =
                review.getFindings();

        if (findings == null
                || findings.isEmpty()) {

            builder.append(
                    "✅ No meaningful issues detected."
            );

            return builder.toString();
        }

        builder.append(
                "**Findings:** "
        );

        builder.append(
                findings.size()
        );

        builder.append(
                "\n\n"
        );

        builder.append(
                "**Inline comments posted:** "
        );

        builder.append(
                inlineCommentsPosted
        );

        builder.append(
                "/"
        );

        builder.append(
                findings.size()
        );

        builder.append(
                "\n\n"
        );

        for (Finding finding : findings) {

            builder.append(
                    "- "
            );

            builder.append(
                    severityEmoji(
                            finding.getSeverity()
                    )
            );

            builder.append(
                    " **"
            );

            builder.append(
                    finding.getSeverity()
            );

            builder.append(
                    "** — "
            );

            builder.append(
                    finding.getTitle()
            );

            if (finding.getFilePath() != null
                    && !finding.getFilePath().isBlank()) {

                builder.append(
                        " (`"
                );

                builder.append(
                        finding.getFilePath()
                );

                builder.append(
                        "`:"
                );

                builder.append(
                        finding.getLine()
                );

                builder.append(
                        ")"
                );
            }

            builder.append(
                    "\n"
            );
        }

        return builder.toString();
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

                    message.append(
                            " -> "
                    );
                }

                message.append(
                        current.getClass()
                                .getSimpleName()
                );

                message.append(
                        ": "
                );

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