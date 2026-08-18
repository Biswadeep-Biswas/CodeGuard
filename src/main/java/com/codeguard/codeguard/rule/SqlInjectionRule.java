package com.codeguard.codeguard.rule;

import com.codeguard.codeguard.model.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SqlInjectionRule implements CodeReviewRule {

    private static final Pattern SQL_CONCATENATION = Pattern.compile(
            "(?i)(SELECT|INSERT|UPDATE|DELETE).*\\+"
    );

    @Override
    public List<Finding> analyze(String code) {

        List<Finding> findings = new ArrayList<>();

        String[] lines = code.split("\\R");

        for (int i = 0; i < lines.length; i++) {

            if (SQL_CONCATENATION.matcher(lines[i]).find()) {

                Finding finding = new Finding(
                        "HIGH",
                        "SECURITY",
                        "Potential SQL injection",
                        "A SQL query appears to be constructed using string concatenation.",
                        "Use parameterized queries or prepared statements instead of concatenating input.",
                        i + 1
                );

                findings.add(finding);
            }
        }

        return findings;
    }
}