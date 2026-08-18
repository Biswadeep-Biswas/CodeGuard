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

    @Column(name = "repository_owner", nullable = false)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "pull_number", nullable = false)
    private int pullNumber;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    private int score;

    private LocalDateTime createdAt;

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

    public ReviewEntity(
            String repositoryOwner,
            String repositoryName,
            int pullNumber,
            String commitSha,
            int score) {

        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
        this.pullNumber = pullNumber;
        this.commitSha = commitSha;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }

    public void addFinding(
            ReviewFindingEntity finding) {

        findings.add(finding);
        finding.setReview(this);
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ReviewFindingEntity> getFindings() {
        return findings;
    }
}