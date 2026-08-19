package com.codeguard.codeguard.model;

public class Finding {

    private String severity;
    private String category;
    private String title;
    private String explanation;
    private String suggestion;
    private String filePath;
    private int line;

    public Finding(
            String severity,
            String category,
            String title,
            String explanation,
            String suggestion,
            int line) {

        this(
                severity,
                category,
                title,
                explanation,
                suggestion,
                null,
                line
        );
    }

    public Finding(
            String severity,
            String category,
            String title,
            String explanation,
            String suggestion,
            String filePath,
            int line) {

        this.severity = severity;
        this.category = category;
        this.title = title;
        this.explanation = explanation;
        this.suggestion = suggestion;
        this.filePath = filePath;
        this.line = line;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLine() {
        return line;
    }
}