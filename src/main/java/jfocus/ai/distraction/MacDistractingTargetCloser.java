package jfocus.ai.distraction;

import java.io.IOException;

import jfocus.monitor.WindowSession;

public class MacDistractingTargetCloser implements DistractingTargetCloser {
    private static final String OSASCRIPT_BINARY = "osascript";
    private static final String OSASCRIPT_EXECUTE_FLAG = "-e";
    private static final String TAB_CLOSE_KEY = "w";
    private static final String COMMAND_KEY = "command down";
    private static final int APPLESCRIPT_SUCCESS_EXIT_CODE = 0;

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
