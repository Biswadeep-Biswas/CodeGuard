package com.codeguard.codeguard.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitHubDiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile(
                    "@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*"
            );

    public String convertToNumberedNewLines(
            String filename,
            String patch) {

        if (filename == null
                || filename.isBlank()
                || patch == null
                || patch.isBlank()) {

            return "";
        }

        StringBuilder output =
                new StringBuilder();

        output.append("FILE: ")
                .append(filename)
                .append("\n");

        String[] lines =
                patch.split("\\R");

        int newLineNumber = -1;
        boolean insideHunk = false;

        for (String line : lines) {

            Matcher matcher =
                    HUNK_HEADER.matcher(
                            line
                    );

            if (matcher.matches()) {

                newLineNumber =
                        Integer.parseInt(
                                matcher.group(1)
                        );

                insideHunk = true;
                continue;
            }

            if (!insideHunk) {
                continue;
            }

            if (line.startsWith(
                    "\\ No newline at end of file"
            )) {

                continue;
            }

            /*
             * Deleted lines do not exist
             * in the new version.
             */
            if (line.startsWith("-")) {
                continue;
            }

            if (line.startsWith("+")) {

                appendLine(
                        output,
                        newLineNumber,
                        line.substring(1)
                );

                newLineNumber++;
                continue;
            }

            if (line.startsWith(" ")) {

                appendLine(
                        output,
                        newLineNumber,
                        line.substring(1)
                );

                newLineNumber++;
                continue;
            }

            appendLine(
                    output,
                    newLineNumber,
                    line
            );

            newLineNumber++;
        }

        return output.toString();
    }

    private void appendLine(
            StringBuilder output,
            int lineNumber,
            String source) {

        output.append(
                "NEW LINE "
        );

        output.append(
                lineNumber
        );

        output.append(
                ": "
        );

        output.append(
                source
        );

        output.append(
                "\n"
        );
    }
}