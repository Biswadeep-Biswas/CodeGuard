package com.codeguard.codeguard.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "review_findings")
public class ReviewFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String severity;

    private String category;

    private String title;

    @Column(length = 4000)
    private String explanation;

    @Column(length = 4000)
    private String suggestion;

    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private ReviewEntity review;

    public ReviewFindingEntity() {
    }

    public ReviewFindingEntity(
            String severity,
            String category,
            String title,
            String explanation,
            String suggestion,
            int lineNumber) {

        this.severity = severity;
        this.category = category;
        this.title = title;
        this.explanation = explanation;
        this.suggestion = suggestion;
        this.lineNumber = lineNumber;
    }

    public void setReview(
            ReviewEntity review) {

        this.review = review;
    }

    public Long getId() {
        return id;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}