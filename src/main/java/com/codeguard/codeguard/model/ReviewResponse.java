package com.codeguard.codeguard.model;

import java.util.List;

public class ReviewResponse {

    private int score;
    private List<Finding> findings;

    public ReviewResponse(int score, List<Finding> findings) {
        this.score = score;
        this.findings = findings;
    }

    public int getScore() {
        return score;
    }

    public List<Finding> getFindings() {
        return findings;
    }
}