package jfocus.ai;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 清理瀏覽器視窗標題，只保留頁面本身較有辨識度的資訊。
 */
public final class BrowserTitleCleaner {
    private static final Pattern NOTIFICATION_BADGE_PATTERN = Pattern.compile("^\\(\\d+\\)\\s*");
    private static final Pattern BROWSER_SUFFIX_PATTERN = Pattern.compile(
            "\\s*(?:[-|｜·•—–_:：]+)\\s*(?:google chrome|chrome|safari|microsoft edge|edge)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_SEPARATOR_PATTERN = Pattern.compile("\\s*(?:[-|｜·•—–_:：]+)\\s*");

    private static final Set<String> BROWSER_APPS = Set.of(
            "google chrome",
            "chrome",
            "safari",
            "microsoft edge",
            "edge");

    private static final Set<String> LOW_VALUE_TITLE_PARTS = Set.of(
            "google chrome",
            "chrome",
            "safari",
            "microsoft edge",
            "edge",
            "google 搜尋",
            "google search",
            "搜尋",
            "search",
            "新分頁",
            "new tab",
            "首頁",
            "home",
            "起始頁面");

    private BrowserTitleCleaner() {
    }

    public static boolean isBrowserApp(String appName) {
        String app = normalize(appName);
        if (app.isBlank()) {
            return false;
        }

        for (String browser : BROWSER_APPS) {
            if (app.contains(browser)) {
                return true;
            }
        }
        return false;
    }

    public static String extractImportantTitle(String appName, String windowTitle) {
        String title = stripBrowserSuffix(windowTitle);
        if (!isBrowserApp(appName)) {
            return title;
        }

        for (String part : TITLE_SEPARATOR_PATTERN.split(title)) {
            String candidate = part.trim();
            if (isUsefulTitlePart(candidate)) {
                return candidate;
            }
        }

        return isUsefulTitlePart(title) ? title : "";
    }

    public static String stripBrowserSuffix(String title) {
        String normalizedTitle = normalize(title);
        normalizedTitle = NOTIFICATION_BADGE_PATTERN.matcher(normalizedTitle).replaceFirst("").trim();
        return BROWSER_SUFFIX_PATTERN.matcher(normalizedTitle).replaceFirst("").trim();
    }

    private static boolean isUsefulTitlePart(String value) {
        String normalized = normalize(value);
        return !normalized.isBlank()
                && !LOW_VALUE_TITLE_PARTS.contains(normalized)
                && normalized.chars().anyMatch(Character::isLetterOrDigit);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
