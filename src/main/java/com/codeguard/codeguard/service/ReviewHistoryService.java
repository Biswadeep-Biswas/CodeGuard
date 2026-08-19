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
     * Only SUCCESS means this exact commit
     * has already been completely reviewed.
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
     * Create a PROCESSING review before
     * contacting Gemini.
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
                     * A failed review can be retried.
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
     * Store successful findings and mark
     * the review SUCCESS.
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

        if (response.getFindings() != null) {

            for (Finding finding :
                    response.getFindings()) {

                ReviewFindingEntity entity =
                        createFindingEntity(
                                finding
                        );

                review.addFinding(entity);
            }
        }

        review.markSuccess(
                response.getScore()
        );

        reviewRepository.save(review);
    }

    /*
     * Store the failure reason and mark
     * the review FAILED.
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

        /*
         * Keep safely below the database
         * column's 4000-character limit.
         */
        if (message.length() > 3900) {

            message =
                    message.substring(
                            0,
                            3900
                    );
        }

        review.markFailed(
                message
        );

        reviewRepository.save(
                review
        );
    }

    /*
     * Kept for compatibility with any
     * existing callers.
     *
     * New GitHub reviews should normally
     * use startReview() + markSuccess().
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

        if (response.getFindings() != null) {

            for (Finding finding :
                    response.getFindings()) {

                ReviewFindingEntity entity =
                        createFindingEntity(
                                finding
                        );

                review.addFinding(entity);
            }
        }

        review.markSuccess(
                response.getScore()
        );

        reviewRepository.save(
                review
        );
    }

    /*
     * Convert the API/model Finding into
     * the MySQL/JPA entity.
     */
    private ReviewFindingEntity createFindingEntity(
            Finding finding) {

        return new ReviewFindingEntity(
                finding.getSeverity(),
                finding.getCategory(),
                finding.getTitle(),
                finding.getExplanation(),
                finding.getSuggestion(),
                finding.getFilePath(),
                finding.getLine()
        );
    }
}