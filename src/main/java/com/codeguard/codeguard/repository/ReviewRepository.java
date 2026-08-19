package com.codeguard.codeguard.repository;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository
        extends JpaRepository<ReviewEntity, Long> {

    boolean existsByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha
    );

    boolean existsByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitShaAndStatus(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha,
            ReviewStatus status
    );

    Optional<ReviewEntity>
    findByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha
    );

    List<ReviewEntity>
    findTop20ByOrderByCreatedAtDesc();
}