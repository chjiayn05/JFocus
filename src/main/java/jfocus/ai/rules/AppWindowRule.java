package jfocus.ai.rules;

import java.util.Locale;

/**
 * A persisted app/title matching rule.
 */
public record AppWindowRule(String appName, String windowTitle) {

    public AppWindowRule {
        appName = normalize(appName);
        windowTitle = normalize(windowTitle);
        if (appName.isBlank() && windowTitle.isBlank()) {
            throw new IllegalArgumentException("appName and windowTitle cannot both be blank");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
