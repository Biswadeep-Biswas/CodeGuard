package com.codeguard.codeguard.model;

public class Finding {

    private String severity;
    private String category;
    private String title;
    private String explanation;
    private String suggestion;
    private int line;

    public Finding(
            String severity,
            String category,
            String title,
            String explanation,
            String suggestion,
            int line) {

        this.severity = severity;
        this.category = category;
        this.title = title;
        this.explanation = explanation;
        this.suggestion = suggestion;
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

    public int getLine() {
        return line;
    }
}