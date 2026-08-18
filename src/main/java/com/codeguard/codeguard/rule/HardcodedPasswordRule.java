package com.codeguard.codeguard.rule;

import com.codeguard.codeguard.model.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HardcodedPasswordRule implements CodeReviewRule {

    @Override
    public List<Finding> analyze(String code) {

        List<Finding> findings = new ArrayList<>();

        String[] lines = code.split("\\R");

        for (int i = 0; i < lines.length; i++) {

            if (lines[i].contains("password = \"")) {

                Finding finding = new Finding(
                        "HIGH",
                        "SECURITY",
                        "Hard-coded password",
                        "A password appears to be directly embedded in the source code.",
                        "Use an environment variable or a secure secret manager.",
                        i + 1
                );

                findings.add(finding);
            }
        }

        return findings;
    }
}