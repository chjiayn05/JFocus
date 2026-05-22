package jfocus.ai.distraction;

import java.io.IOException;

import jfocus.monitor.WindowSession;

public class MacDistractingTargetCloser implements DistractingTargetCloser {
    private static final String OSASCRIPT_BINARY = "osascript";
    private static final String OSASCRIPT_EXECUTE_FLAG = "-e";
    private static final String TAB_CLOSE_KEY = "w";
    private static final String COMMAND_KEY = "command down";
    private static final int APPLESCRIPT_SUCCESS_EXIT_CODE = 0;

    private static final String CHROME_TAB_CLOSE_BY_TITLE_SCRIPT_TEMPLATE = """
            tell application "%s"
                set targetTitle to "%s"
                repeat with browserWindow in windows
                    repeat with browserTab in tabs of browserWindow
                        if targetTitle is "" or targetTitle contains (title of browserTab) or (title of browserTab) contains targetTitle then
                            close browserTab
                            return "closed"
                        end if
                    end repeat
                end repeat
            end tell
            error "tab not found"
            """;

    private static final String SAFARI_TAB_CLOSE_BY_TITLE_SCRIPT_TEMPLATE = """
            tell application "Safari"
                set targetTitle to "%s"
                repeat with browserWindow in windows
                    repeat with browserTab in tabs of browserWindow
                        if targetTitle is "" or targetTitle contains (name of browserTab) or (name of browserTab) contains targetTitle then
                            close browserTab
                            return "closed"
                        end if
                    end repeat
                end repeat
            end tell
            error "tab not found"
            """;

    private static final String TARGETED_CLOSE_SCRIPT_TEMPLATE = """
            tell application "System Events"
                tell process "%s"
                    set frontmost to true
                    try
                        perform action "AXRaise" of window "%s"
                        click button 1 of window "%s"
                        return "closed"
                    on error
                        keystroke "%s" using {%s}
                        return "fallback"
                    end try
                end tell
            end tell
            """;

    private static final String TARGETED_TAB_CLOSE_SCRIPT_TEMPLATE = """
            tell application "%s" to activate
            delay 0.2
            tell application "System Events"
                tell process "%s"
                    set frontmost to true
                    try
                        perform action "AXRaise" of window "%s"
                    end try
                end tell
                keystroke "%s" using {%s}
            end tell
            """;

    private static final String CLOSE_TAB_SCRIPT_TEMPLATE = """
            tell application "%s"
                activate
            end tell
            tell application "System Events"
                keystroke "%s" using {%s}
            end tell
            """;

    private static final String CLOSE_WINDOW_SCRIPT_TEMPLATE = """
            tell application "%s"
                activate
                try
                    close front window
                on error
                    tell application "System Events"
                        keystroke "%s" using {%s}
                    end tell
                end try
            end tell
            """;

    @Override
    public boolean closeDistractingTarget(WindowSession session, boolean closeTabOnly) {
        if (session == null || session.processName == null || session.processName.isBlank()) {
            return false;
        }

        String escapedProcessName = escapeForAppleScript(session.processName.trim());
        String escapedTitle = session.title == null ? "" : escapeForAppleScript(session.title.trim());
        if (closeTabOnly && closeBrowserTabByTitle(session.processName.trim(), session.title)) {
            return true;
        }

        String script;
        if (!escapedTitle.isBlank()) {
            script = closeTabOnly
                    ? TARGETED_TAB_CLOSE_SCRIPT_TEMPLATE.formatted(escapedProcessName, escapedProcessName,
                            escapedTitle, TAB_CLOSE_KEY, COMMAND_KEY)
                    : TARGETED_CLOSE_SCRIPT_TEMPLATE.formatted(escapedProcessName, escapedTitle, escapedTitle,
                            TAB_CLOSE_KEY, COMMAND_KEY);
        } else {
            script = closeTabOnly
                    ? CLOSE_TAB_SCRIPT_TEMPLATE.formatted(escapedProcessName, TAB_CLOSE_KEY, COMMAND_KEY)
                    : CLOSE_WINDOW_SCRIPT_TEMPLATE.formatted(escapedProcessName, TAB_CLOSE_KEY, COMMAND_KEY);
        }

        return executeAppleScript(script);
    }

    private boolean closeBrowserTabByTitle(String appName, String windowTitle) {
        String targetTitle = extractLikelyTabTitle(windowTitle);
        if (isChromeFamily(appName)) {
            return executeAppleScript(CHROME_TAB_CLOSE_BY_TITLE_SCRIPT_TEMPLATE.formatted(
                    escapeForAppleScript(resolveChromiumApplicationTarget(appName)),
                    targetTitle));
        }

        if (isSafari(appName)) {
            return executeAppleScript(SAFARI_TAB_CLOSE_BY_TITLE_SCRIPT_TEMPLATE.formatted(targetTitle));
        }

        return false;
    }

    private boolean isChromeFamily(String appName) {
        String normalizedName = appName.toLowerCase();
        return normalizedName.contains("chrome")
                || normalizedName.contains("edge")
                || normalizedName.contains("brave");
    }

    private boolean isSafari(String appName) {
        return appName.toLowerCase().contains("safari");
    }

    private String resolveChromiumApplicationTarget(String appName) {
        String normalizedName = appName.toLowerCase();
        if (normalizedName.contains("chrome")) {
            return "/Applications/Google Chrome.app";
        }
        if (normalizedName.contains("edge")) {
            return "/Applications/Microsoft Edge.app";
        }
        if (normalizedName.contains("brave")) {
            return "/Applications/Brave Browser.app";
        }
        return appName;
    }

    private String extractLikelyTabTitle(String windowTitle) {
        String title = windowTitle == null ? "" : windowTitle.trim();
        title = title.replaceFirst("^\\(\\d+\\)\\s*", "");
        title = title.replace(" - 音訊播放中", "");
        title = title.replace(" - Google Chrome", "");
        title = title.replace(" - Microsoft Edge", "");
        title = title.replace(" - Brave Browser", "");
        title = title.replace(" - Safari", "");
        return escapeForAppleScript(title.trim());
    }

    private boolean executeAppleScript(String script) {
        ProcessBuilder builder = new ProcessBuilder(OSASCRIPT_BINARY, OSASCRIPT_EXECUTE_FLAG, script);
        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            return exitCode == APPLESCRIPT_SUCCESS_EXIT_CODE;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String escapeForAppleScript(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
