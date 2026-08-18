package com.codeguard.codeguard.controller;

import com.codeguard.codeguard.entity.ReviewEntity;
import com.codeguard.codeguard.repository.ReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewHistoryController {

    private final ReviewRepository reviewRepository;

    public ReviewHistoryController(
            ReviewRepository reviewRepository) {

        this.reviewRepository =
                reviewRepository;
    }

    @GetMapping
    public List<ReviewEntity> getRecentReviews() {

        return reviewRepository
                .findTop20ByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewEntity> getReview(
            @PathVariable Long id) {

        return reviewRepository
                .findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }
}