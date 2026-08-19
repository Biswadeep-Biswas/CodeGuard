package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.rule.CodeReviewRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ReviewService {

    /*
     * Severity penalties.
     *
     * The internal score uses points out of 20
     * so that we can support 0.5-point penalties
     * while still returning an integer 0-10 score.
     *
     * CRITICAL = -4.0
     * HIGH     = -3.0
     * MEDIUM   = -1.5
     * LOW      = -0.5
     * INFO     =  0
     */
    private static final int MAX_SCORE_POINTS = 20;

    private static final int CRITICAL_PENALTY = 8;
    private static final int HIGH_PENALTY = 6;
    private static final int MEDIUM_PENALTY = 3;
    private static final int LOW_PENALTY = 1;

    private final List<CodeReviewRule> rules;
    private final AiReviewService aiReviewService;

    public ReviewService(
            List<CodeReviewRule> rules,
            AiReviewService aiReviewService) {

        this.rules = rules;
        this.aiReviewService = aiReviewService;
    }

    public ReviewResponse reviewCode(
            String code) {

        List<Finding> findings =
                new ArrayList<>();

        /*
         * 1. Run CodeGuard's deterministic
         * static-analysis rules.
         */
        for (CodeReviewRule rule : rules) {

            List<Finding> ruleFindings =
                    rule.analyze(code);

            if (ruleFindings != null) {

                findings.addAll(
                        ruleFindings
                );
            }
        }

        /*
         * 2. Run the Gemini AI review.
         */
        List<Finding> aiFindings =
                aiReviewService.reviewCode(
                        code
                );

        if (aiFindings != null) {

            findings.addAll(
                    aiFindings
            );
        }

        /*
         * 3. Remove duplicate findings.
         *
         * A finding is considered a duplicate
         * when it refers to the same file,
         * source line and normalized title.
         */
        List<Finding> uniqueFindings =
                removeDuplicates(
                        findings
                );

        /*
         * 4. Calculate the final deterministic
         * CodeGuard score.
         */
        int score =
                calculateScore(
                        uniqueFindings
                );

        System.out.println(
                "CodeGuard score: "
                        + score
                        + "/10 from "
                        + uniqueFindings.size()
                        + " unique finding(s)."
        );

        return new ReviewResponse(
                score,
                uniqueFindings
        );
    }

    private int calculateScore(
            List<Finding> findings) {

        int points =
                MAX_SCORE_POINTS;

        for (Finding finding : findings) {

            String severity =
                    normalize(
                            finding.getSeverity()
                    );

            switch (severity) {

                case "CRITICAL":
                    points -=
                            CRITICAL_PENALTY;
                    break;

                case "HIGH":
                    points -=
                            HIGH_PENALTY;
                    break;

                case "MEDIUM":
                    points -=
                            MEDIUM_PENALTY;
                    break;

                case "LOW":
                    points -=
                            LOW_PENALTY;
                    break;

                case "INFO":
                default:
                    break;
            }
        }

        /*
         * Never allow a negative score.
         */
        points =
                Math.max(
                        points,
                        0
                );

        /*
         * Convert our 0-20 internal scale
         * back to the existing 0-10 integer
         * API expected by the rest of CodeGuard.
         *
         * Example:
         *
         * 20 points -> 10
         * 17 points -> 9
         * 14 points -> 7
         *  9 points -> 5
         *  0 points -> 0
         */
        return (int) Math.round(
                points / 2.0
        );
    }

    private List<Finding> removeDuplicates(
            List<Finding> findings) {

        List<Finding> unique =
                new ArrayList<>();

        Set<String> seen =
                new HashSet<>();

        for (Finding finding : findings) {

            if (finding == null) {
                continue;
            }

            String key =
                    buildDuplicateKey(
                            finding
                    );

            if (seen.add(key)) {

                unique.add(
                        finding
                );
            }
        }

        return unique;
    }

    private String buildDuplicateKey(
            Finding finding) {

        String filePath =
                normalize(
                        finding.getFilePath()
                );

        String title =
                normalize(
                        finding.getTitle()
                );

        return filePath
                + "|"
                + finding.getLine()
                + "|"
                + title;
    }

    private String normalize(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }
}