package com.codeguard.codeguard.service;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.entity.ReviewFindingEntity;
import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.repository.ReviewRepository;
import org.springframework.stereotype.Service;

@Service
public class ReviewHistoryService {

    private final ReviewRepository reviewRepository;

    public ReviewHistoryService(
            ReviewRepository reviewRepository) {

        this.reviewRepository =
                reviewRepository;
    }

    public boolean alreadyReviewed(
            String owner,
            String repository,
            int pullNumber,
            String commitSha) {

        return reviewRepository
                .existsByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
                        owner,
                        repository,
                        pullNumber,
                        commitSha
                );
    }

    public void saveReview(
            String owner,
            String repository,
            int pullNumber,
            String commitSha,
            ReviewResponse response) {

        ReviewEntity review =
                new ReviewEntity(
                        owner,
                        repository,
                        pullNumber,
                        commitSha,
                        response.getScore()
                );

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

        reviewRepository.save(review);
    }
}