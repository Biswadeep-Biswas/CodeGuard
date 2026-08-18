package com.codeguard.codeguard.controller;

import com.codeguard.codeguard.service.GitHubService;
import com.codeguard.codeguard.service.GitHubWebhookValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/github")
public class GitHubWebhookController {

    private final GitHubWebhookValidator validator;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(
            GitHubWebhookValidator validator,
            GitHubService gitHubService,
            ObjectMapper objectMapper) {

        this.validator = validator;
        this.gitHubService = gitHubService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(

            @RequestHeader(
                    value = "X-GitHub-Event",
                    required = false
            )
            String event,

            @RequestHeader(
                    value = "X-Hub-Signature-256",
                    required = false
            )
            String signature,

            @RequestBody
            String payload) {

        if (!validator.isValid(
                payload,
                signature
        )) {

            return ResponseEntity
                    .status(401)
                    .body(
                            "Invalid webhook signature"
                    );
        }

        if (!"pull_request".equals(event)) {

            return ResponseEntity.ok(
                    "Event ignored"
            );
        }

        try {

            JsonNode json =
                    objectMapper.readTree(
                            payload
                    );

            String action =
                    json.path("action")
                            .asText();

            if (!action.equals("opened")
                    && !action.equals("synchronize")
                    && !action.equals("reopened")) {

                return ResponseEntity.ok(
                        "Pull request action ignored"
                );
            }

            String owner =
                    json.path("repository")
                            .path("owner")
                            .path("login")
                            .asText();

            String repository =
                    json.path("repository")
                            .path("name")
                            .asText();

            int pullNumber =
                    json.path("pull_request")
                            .path("number")
                            .asInt();

            long installationId =
                    json.path("installation")
                            .path("id")
                            .asLong();

            String commitSha =
                    json.path("pull_request")
                            .path("head")
                            .path("sha")
                            .asText();

            System.out.println(
                    "Webhook received for PR #"
                            + pullNumber
            );

            Thread.startVirtualThread(() -> {

                try {

                    System.out.println(
                            "Background review started for PR #"
                                    + pullNumber
                    );

                    gitHubService.reviewPullRequest(
                            owner,
                            repository,
                            pullNumber,
                            installationId,
                            commitSha
                    );

                    System.out.println(
                            "Background review finished for PR #"
                                    + pullNumber
                    );

                } catch (Exception e) {

                    System.err.println(
                            "Background review failed: "
                                    + e.getMessage()
                    );

                    e.printStackTrace();
                }
            });

            return ResponseEntity.ok(
                    "CodeGuard review accepted"
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .body(
                            "Webhook processing failed"
                    );
        }
    }
}