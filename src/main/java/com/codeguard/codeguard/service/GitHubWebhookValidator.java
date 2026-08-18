package com.codeguard.codeguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class GitHubWebhookValidator {

    private final String webhookSecret;

    public GitHubWebhookValidator(
            @Value("${github.webhook-secret}") String webhookSecret) {

        this.webhookSecret = webhookSecret;
    }

    public boolean isValid(
            String payload,
            String signatureHeader) {

        if (signatureHeader == null ||
                !signatureHeader.startsWith("sha256=")) {

            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec key =
                    new SecretKeySpec(
                            webhookSecret.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                    );

            mac.init(key);

            byte[] digest =
                    mac.doFinal(
                            payload.getBytes(StandardCharsets.UTF_8)
                    );

            String expected =
                    "sha256=" + HexFormat.of().formatHex(digest);

            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            return false;
        }
    }
}