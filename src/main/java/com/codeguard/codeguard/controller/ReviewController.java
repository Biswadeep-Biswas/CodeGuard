package com.codeguard.codeguard.controller;

import com.codeguard.codeguard.model.ReviewRequest;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.service.ReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/review")
    public ReviewResponse review(@RequestBody ReviewRequest request) {
        return reviewService.reviewCode(request.getCode());
    }
}