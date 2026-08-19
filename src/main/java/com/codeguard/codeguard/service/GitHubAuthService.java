package com.codeguard.codeguard.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class GitHubAuthService {

    private final String appId;
    private final String privateKeyPath;
    private final String privateKeyValue;

    public GitHubAuthService(
            @Value("${github.app.id}") String appId,
            @Value("${github.private-key-path:}") String privateKeyPath,
            @Value("${github.private-key:}") String privateKeyValue) {

        this.appId = appId;
        this.privateKeyPath = privateKeyPath;
        this.privateKeyValue = privateKeyValue;
    }

    public String createInstallationToken(long installationId) {

        try {
            String jwt = createAppJwt();

            RestClient client = RestClient.builder()
                    .baseUrl("https://api.github.com")
                    .defaultHeader(
                            "Accept",
                            "application/vnd.github+json"
                    )
                    .defaultHeader(
                            "Authorization",
                            "Bearer " + jwt
                    )
                    .build();

            JsonNode response = client
                    .post()
                    .uri(
                            "/app/installations/{id}/access_tokens",
                            installationId
                    )
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new RuntimeException(
                        "Empty installation token response"
                );
            }

            String token =
                    response.path("token").asText();

            if (token.isBlank()) {
                throw new RuntimeException(
                        "GitHub installation token missing"
                );
            }

            return token;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to create GitHub installation token",
                    e
            );
        }
    }

    private String createAppJwt()
            throws Exception {

        RSAPrivateKey privateKey =
                readPrivateKey();

        Algorithm algorithm =
                Algorithm.RSA256(
                        null,
                        privateKey
                );

        Instant now =
                Instant.now();

        return JWT.create()
                .withIssuer(appId)
                .withIssuedAt(
                        Date.from(
                                now.minusSeconds(60)
                        )
                )
                .withExpiresAt(
                        Date.from(
                                now.plusSeconds(9 * 60)
                        )
                )
                .sign(algorithm);
    }

    private RSAPrivateKey readPrivateKey()
            throws Exception {

        String pem;

        if (
                privateKeyValue != null &&
                !privateKeyValue.isBlank()
        ) {

            pem = privateKeyValue
                    .replace("\\n", "\n")
                    .trim();

        } else {

            if (
                    privateKeyPath == null ||
                    privateKeyPath.isBlank()
            ) {

                throw new RuntimeException(
                        "GitHub private key is not configured"
                );
            }

            pem = Files.readString(
                    Path.of(privateKeyPath)
            );
        }

        if (
                pem.contains(
                        "BEGIN RSA PRIVATE KEY"
                )
        ) {

            pem =
                    convertPkcs1ToPkcs8(pem);
        }

        pem = pem
                .replace(
                        "-----BEGIN PRIVATE KEY-----",
                        ""
                )
                .replace(
                        "-----END PRIVATE KEY-----",
                        ""
                )
                .replaceAll(
                        "\\s",
                        ""
                );

        byte[] decoded =
                Base64.getDecoder()
                        .decode(pem);

        PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(
                        decoded
                );

        KeyFactory keyFactory =
                KeyFactory.getInstance(
                        "RSA"
                );

        return (RSAPrivateKey)
                keyFactory.generatePrivate(
                        keySpec
                );
    }

    private String convertPkcs1ToPkcs8(
            String pkcs1Pem)
            throws Exception {

        String cleaned =
                pkcs1Pem
                        .replace(
                                "-----BEGIN RSA PRIVATE KEY-----",
                                ""
                        )
                        .replace(
                                "-----END RSA PRIVATE KEY-----",
                                ""
                        )
                        .replaceAll(
                                "\\s",
                                ""
                        );

        byte[] pkcs1Bytes =
                Base64.getDecoder()
                        .decode(cleaned);

        byte[] pkcs8Bytes =
                wrapPkcs1InPkcs8(
                        pkcs1Bytes
                );

        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(
                        64,
                        new byte[]{'\n'}
                ).encodeToString(
                        pkcs8Bytes
                )
                + "\n-----END PRIVATE KEY-----";
    }

    private byte[] wrapPkcs1InPkcs8(
            byte[] pkcs1Bytes) {

        byte[] rsaAlgorithmIdentifier =
                new byte[]{
                        0x30, 0x0D,
                        0x06, 0x09,
                        0x2A,
                        (byte) 0x86,
                        0x48,
                        (byte) 0x86,
                        (byte) 0xF7,
                        0x0D,
                        0x01,
                        0x01,
                        0x01,
                        0x05,
                        0x00
                };

        byte[] octetString =
                encodeDerOctetString(
                        pkcs1Bytes
                );

        byte[] body =
                concatenate(
                        new byte[]{
                                0x02,
                                0x01,
                                0x00
                        },
                        rsaAlgorithmIdentifier,
                        octetString
                );

        return encodeDerSequence(body);
    }

    private byte[] encodeDerSequence(
            byte[] data) {

        return concatenate(
                new byte[]{0x30},
                encodeLength(data.length),
                data
        );
    }

    private byte[] encodeDerOctetString(
            byte[] data) {

        return concatenate(
                new byte[]{0x04},
                encodeLength(data.length),
                data
        );
    }

    private byte[] encodeLength(
            int length) {

        if (length < 128) {
            return new byte[]{
                    (byte) length
            };
        }

        int temp = length;
        int bytesNeeded = 0;

        while (temp > 0) {
            temp >>= 8;
            bytesNeeded++;
        }

        byte[] result =
                new byte[
                        bytesNeeded + 1
                ];

        result[0] =
                (byte) (
                        0x80 |
                        bytesNeeded
                );

        for (
                int i = bytesNeeded;
                i > 0;
                i--
        ) {

            result[i] =
                    (byte) (
                            length & 0xFF
                    );

            length >>= 8;
        }

        return result;
    }

    private byte[] concatenate(
            byte[]... arrays) {

        int totalLength = 0;

        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        byte[] result =
                new byte[totalLength];

        int position = 0;

        for (byte[] array : arrays) {

            System.arraycopy(
                    array,
                    0,
                    result,
                    position,
                    array.length
            );

            position += array.length;
        }

        return result;
    }
}