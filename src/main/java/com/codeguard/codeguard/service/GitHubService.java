package com.codeguard.codeguard.service;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import org.springframework.stereotype.Service;
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

            System.out.println(
                    "CodeGuard comment posted successfully to PR #"
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

    /*
     * Converts GitHub's unified diff into explicit
     * new-file line numbers.
     *
     * Example:
     *
     * FILE: LoginService.java
     * NEW LINE 5: public void login(...) {
     * NEW LINE 6:     System.out.println(password);
     *
     * Deleted lines are ignored because they do not
     * exist in the new version of the file.
     */
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

            /*
             * Ignore metadata marker.
             */
            if (line.startsWith(
                    "\\ No newline at end of file"
            )) {

                continue;
            }

            /*
             * Deleted line.
             *
             * It existed only in the old file,
             * so it does not consume a new-file
             * line number.
             */
            if (line.startsWith("-")) {

                continue;
            }

            /*
             * Added line.
             */
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

            /*
             * Context line.
             *
             * Context lines exist in both old
             * and new versions, so they consume
             * a new-file line number too.
             */
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

            /*
             * Defensive fallback for unusual
             * patch content.
             */
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

    private String buildComment(
            ReviewResponse review) {

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

        for (Finding finding : findings) {

            builder.append(
                    "---\n\n"
            );

            builder.append(
                    "### "
            );

            builder.append(
                    severityEmoji(
                            finding.getSeverity()
                    )
            );

            builder.append(
                    " "
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
                    "\n\n"
            );

            builder.append(
                    "**Category:** "
            );

            builder.append(
                    finding.getCategory()
            );

            builder.append(
                    "\n\n"
            );

            if (finding.getFilePath() != null
                    && !finding.getFilePath().isBlank()) {

                builder.append(
                        "**File:** `"
                );

                builder.append(
                        finding.getFilePath()
                );

                builder.append(
                        "`\n\n"
                );
            }

            builder.append(
                    "**Line:** "
            );

            builder.append(
                    finding.getLine()
            );

            builder.append(
                    "\n\n"
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