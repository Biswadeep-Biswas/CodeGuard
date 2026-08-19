package com.codeguard.codeguard.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "repository_owner",
                                "repository_name",
                                "pull_number",
                                "commit_sha"
                        }
                )
        }
)
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "repository_owner",
            nullable = false
    )
    private String repositoryOwner;

    @Column(
            name = "repository_name",
            nullable = false
    )
    private String repositoryName;

    @Column(
            name = "pull_number",
            nullable = false
    )
    private int pullNumber;

    @Column(
            name = "commit_sha",
            nullable = false
    )
    private String commitSha;

    private int score;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewStatus status;

    @Column(length = 4000)
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @OneToMany(
            mappedBy = "review",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<ReviewFindingEntity> findings =
            new ArrayList<>();

    public ReviewEntity() {
    }

    /*
     * Constructor for a review that has
     * just started processing.
     */
    public ReviewEntity(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha) {

        this.repositoryOwner =
                repositoryOwner;

        this.repositoryName =
                repositoryName;

        this.pullNumber =
                pullNumber;

        this.commitSha =
                commitSha;

        this.score = 0;

        this.status =
                ReviewStatus.PROCESSING;

        this.createdAt =
                LocalDateTime.now();

        this.completedAt = null;

        this.errorMessage = null;
    }

    public void markSuccess(
            int score) {

        this.score =
                score;

        this.status =
                ReviewStatus.SUCCESS;

        this.completedAt =
                LocalDateTime.now();

        this.errorMessage = null;
    }

    public void markFailed(
            String errorMessage) {

        this.status =
                ReviewStatus.FAILED;

        this.completedAt =
                LocalDateTime.now();

        this.errorMessage =
                errorMessage;
    }

    public void addFinding(
            ReviewFindingEntity finding) {

        findings.add(finding);

        finding.setReview(this);
    }

    public void clearFindings() {

        findings.clear();
    }

    public Long getId() {
        return id;
    }

    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public int getPullNumber() {
        return pullNumber;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public int getScore() {
        return score;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<ReviewFindingEntity> getFindings() {
        return findings;
    }
}