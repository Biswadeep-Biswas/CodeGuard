package com.codeguard.codeguard.service;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.entity.ReviewFindingEntity;
import com.codeguard.codeguard.entity.ReviewStatus;
import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewHistoryService {

    private final ReviewRepository reviewRepository;

    public ReviewHistoryService(
            ReviewRepository reviewRepository) {

        this.reviewRepository =
                reviewRepository;
    }

    /*
     * Only a SUCCESS review counts as
     * already fully reviewed.
     *
     * FAILED reviews may be retried later.
     */
    public boolean alreadyReviewed(
            String owner,
            String repository,
            int pullNumber,
            String commitSha) {

        return reviewRepository
                .existsByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitShaAndStatus(
                        owner,
                        repository,
                        pullNumber,
                        commitSha,
                        ReviewStatus.SUCCESS
                );
    }

    /*
     * Create a PROCESSING database row
     * before Gemini starts.
     */
    @Transactional
    public ReviewEntity startReview(
            String owner,
            String repository,
            int pullNumber,
            String commitSha) {

        return reviewRepository
                .findByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
                        owner,
                        repository,
                        pullNumber,
                        commitSha
                )
                .map(existing -> {

                    /*
                     * If the previous attempt failed,
                     * reuse the same row.
                     */
                    if (existing.getStatus()
                            == ReviewStatus.FAILED) {

                        existing.clearFindings();

                        return reviewRepository.save(
                                existing
                        );
                    }

                    return existing;
                })
                .orElseGet(() -> {

                    ReviewEntity review =
                            new ReviewEntity(
                                    owner,
                                    repository,
                                    pullNumber,
                                    commitSha
                            );

                    return reviewRepository.save(
                            review
                    );
                });
    }

    /*
     * Called only when Gemini + GitHub
     * review processing succeeds.
     */
    @Transactional
    public void markSuccess(
            Long reviewId,
            ReviewResponse response) {

        ReviewEntity review =
                reviewRepository
                        .findById(reviewId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Review not found: "
                                                + reviewId
                                )
                        );

        review.clearFindings();

        for (Finding finding :
                response.getFindings()) {

            ReviewFindingEntity entity =
                    new ReviewFindingEntity(
                            finding.getSeverity(),
                            finding.getCategory(),
                            finding.getTitle(),
                            finding.getExplanation(),
                            finding.getSuggestion(),
                            finding.getLine()
                    );

            review.addFinding(entity);
        }

        review.markSuccess(
                response.getScore()
        );

        reviewRepository.save(review);
    }

    /*
     * Called if Gemini/GitHub processing
     * fails.
     */
    @Transactional
    public void markFailed(
            Long reviewId,
            String errorMessage) {

        ReviewEntity review =
                reviewRepository
                        .findById(reviewId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Review not found: "
                                                + reviewId
                                )
                        );

        String message =
                errorMessage;

        if (message == null
                || message.isBlank()) {

            message =
                    "Unknown review failure";
        }

        if (message.length() > 3900) {

            message =
                    message.substring(
                            0,
                            3900
                    );
        }

        review.markFailed(message);

        reviewRepository.save(review);
    }

    /*
     * Keep this temporarily so existing
     * GitHubService code still compiles.
     *
     * We will remove/use the new lifecycle
     * methods in the next step.
     */
    @Transactional
    public void saveReview(
            String owner,
            String repository,
            int pullNumber,
            String commitSha,
            ReviewResponse response) {

        ReviewEntity review =
                reviewRepository
                        .findByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
                                owner,
                                repository,
                                pullNumber,
                                commitSha
                        )
                        .orElseGet(
                                () -> new ReviewEntity(
                                        owner,
                                        repository,
                                        pullNumber,
                                        commitSha
                                )
                        );

        review.clearFindings();

        for (Finding finding :
                response.getFindings()) {

            ReviewFindingEntity entity =
                    new ReviewFindingEntity(
                            finding.getSeverity(),
                            finding.getCategory(),
                            finding.getTitle(),
                            finding.getExplanation(),
                            finding.getSuggestion(),
                            finding.getLine()
                    );

            review.addFinding(entity);
        }

        review.markSuccess(
                response.getScore()
        );

        reviewRepository.save(review);
    }
}