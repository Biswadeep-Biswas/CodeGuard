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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubService {

    private static final Pattern HUNK_HEADER =
            Pattern.compile(
                    "@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*"
            );

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

        ReviewEntity reviewEntity = null;

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

                String numberedPatch =
                        convertPatchToNumberedNewLines(
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

            /*
             * Post inline comments first.
             *
             * If GitHub rejects a particular line,
             * we log it and continue instead of
             * failing the whole CodeGuard review.
             */
            int inlineCommentsPosted =
                    postInlineComments(
                            client,
                            owner,
                            repository,
                            pullNumber,
                            commitSha,
                            review
                    );

            /*
             * Always post one overall summary
             * comment to the PR conversation too.
             */
            String summaryComment =
                    buildSummaryComment(
                            review,
                            inlineCommentsPosted
                    );

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

                System.err.println(
                        "Skipping inline comment because filePath is missing: "
                                + finding.getTitle()
                );

                continue;
            }

            if (finding.getLine() <= 0) {

                System.err.println(
                        "Skipping inline comment because line is invalid: "
                                + finding.getTitle()
                );

                continue;
            }

            String body =
                    buildInlineComment(
                            finding
                    );

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
                                        body,

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

                /*
                 * GitHub can reject a line if it is
                 * not commentable in the current diff.
                 *
                 * Do NOT fail the entire review.
                 * The summary comment will still contain
                 * this finding.
                 */
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

        StringBuilder builder =
                new StringBuilder();

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
                " — "
        );

        builder.append(
                finding.getTitle()
        );

        builder.append(
                "**\n\n"
        );

        builder.append(
                finding.getExplanation()
        );

        builder.append(
                "\n\n"
        );

        builder.append(
                "**Suggested fix:** "
        );

        builder.append(
                finding.getSuggestion()
        );

        builder.append(
                "\n\n"
        );

        builder.append(
                "_Category: "
        );

        builder.append(
                finding.getCategory()
        );

        builder.append(
                "_"
        );

        return builder.toString();
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

        builder.append(
                "\n"
        );

        builder.append(
                "_Detailed findings are posted inline where GitHub allows it._"
        );

        return builder.toString();
    }

    private String convertPatchToNumberedNewLines(
            String filename,
            String patch) {

        StringBuilder output =
                new StringBuilder();

        output.append(
                "FILE: "
        );

        output.append(
                filename
        );

        output.append(
                "\n"
        );

        String[] lines =
                patch.split(
                        "\\R"
                );

        int newLineNumber = -1;

        boolean insideHunk = false;

        for (String line : lines) {

            Matcher matcher =
                    HUNK_HEADER.matcher(
                            line
                    );

            if (matcher.matches()) {

                newLineNumber =
                        Integer.parseInt(
                                matcher.group(1)
                        );

                insideHunk = true;

                continue;
            }

            if (!insideHunk) {
                continue;
            }

            if (line.startsWith(
                    "\\ No newline at end of file"
            )) {

                continue;
            }

            if (line.startsWith("-")) {

                continue;
            }

            if (line.startsWith("+")) {

                output.append(
                        "NEW LINE "
                );

                output.append(
                        newLineNumber
                );

                output.append(
                        ": "
                );

                output.append(
                        line.substring(1)
                );

                output.append(
                        "\n"
                );

                newLineNumber++;

                continue;
            }

            if (line.startsWith(" ")) {

                output.append(
                        "NEW LINE "
                );

                output.append(
                        newLineNumber
                );

                output.append(
                        ": "
                );

                output.append(
                        line.substring(1)
                );

                output.append(
                        "\n"
                );

                newLineNumber++;

                continue;
            }

            output.append(
                    "NEW LINE "
            );

            output.append(
                    newLineNumber
            );

            output.append(
                    ": "
            );

            output.append(
                    line
            );

            output.append(
                    "\n"
            );

            newLineNumber++;
        }

        return output.toString();
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