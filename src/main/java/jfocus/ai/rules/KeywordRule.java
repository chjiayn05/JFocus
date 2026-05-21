package jfocus.ai.rules;

import java.util.Locale;

/**
 * A persisted keyword matching rule.
 */
public record KeywordRule(String keyword) {

    public KeywordRule {
        keyword = normalize(keyword);
        if (keyword.isBlank()) {
            throw new IllegalArgumentException("keyword cannot be blank");
        }
    }

    public KeywordRule(String appName, String windowTitle) {
        this(toKeyword(appName, windowTitle));
    }

    private static String toKeyword(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(windowTitle);
        if (app.isBlank()) {
            return title;
        }
        if (title.isBlank()) {
            return app;
        }
        return app + " " + title;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
