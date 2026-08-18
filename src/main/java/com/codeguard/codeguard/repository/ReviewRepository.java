package com.codeguard.codeguard.repository;

import com.codeguard.codeguard.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository
        extends JpaRepository<ReviewEntity, Long> {

    boolean existsByRepositoryOwnerAndRepositoryNameAndPullNumberAndCommitSha(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha
    );

    List<ReviewEntity>
    findTop20ByOrderByCreatedAtDesc();
}