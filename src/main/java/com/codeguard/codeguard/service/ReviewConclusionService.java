package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ReviewConclusionService {

    public String determineConclusion(
            List<Finding> findings) {

        if (findings == null
                || findings.isEmpty()) {

            return "success";
        }

        boolean hasMedium = false;

        for (Finding finding : findings) {

            if (finding == null
                    || finding.getSeverity() == null) {

                continue;
            }

            String severity =
                    finding.getSeverity()
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            );

            switch (severity) {

                case "CRITICAL":
                case "HIGH":
                    return "failure";

                case "MEDIUM":
                    hasMedium = true;
                    break;

                default:
                    break;
            }
        }

        if (hasMedium) {
            return "neutral";
        }

        return "success";
    }
}