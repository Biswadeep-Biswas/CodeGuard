package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.rule.CodeReviewRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewService {

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
         * 1. Run our own static rules.
         */
        for (CodeReviewRule rule : rules) {

            List<Finding> ruleFindings =
                    rule.analyze(code);

            findings.addAll(
                    ruleFindings
            );
        }

        /*
         * 2. Run Gemini AI review.
         */
        List<Finding> aiFindings =
                aiReviewService.reviewCode(code);

        findings.addAll(
                aiFindings
        );

        /*
         * 3. Remove basic duplicates.
         */
        List<Finding> uniqueFindings =
                removeDuplicates(findings);

        /*
         * 4. Calculate final score.
         */
        int score =
                calculateScore(
                        uniqueFindings
                );

        return new ReviewResponse(
                score,
                uniqueFindings
        );
    }

    private int calculateScore(
            List<Finding> findings) {

        int score = 10;

        for (Finding finding : findings) {

            switch (
                    finding.getSeverity()) {

                case "CRITICAL":
                    score -= 4;
                    break;

                case "HIGH":
                    score -= 2;
                    break;

                case "MEDIUM":
                    score -= 1;
                    break;

                case "LOW":
                    break;

                case "INFO":
                    break;

                default:
                    break;
            }
        }

        return Math.max(
                score,
                0
        );
    }

    private List<Finding> removeDuplicates(
            List<Finding> findings) {

        List<Finding> unique =
                new ArrayList<>();

        for (Finding finding : findings) {

            boolean duplicate = false;

            for (Finding existing : unique) {

                boolean sameTitle =
                        existing
                                .getTitle()
                                .equalsIgnoreCase(
                                        finding.getTitle()
                                );

                boolean sameLine =
                        existing.getLine()
                                == finding.getLine();

                if (sameTitle
                        && sameLine) {

                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {

                unique.add(
                        finding
                );
            }
        }

        return unique;
    }
}