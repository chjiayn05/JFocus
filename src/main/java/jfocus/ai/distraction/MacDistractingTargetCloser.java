package jfocus.ai.distraction;

import java.io.IOException;

import jfocus.monitor.WindowSession;

public class MacDistractingTargetCloser implements DistractingTargetCloser {
    private static final String OSASCRIPT_BINARY = "osascript";
    private static final String OSASCRIPT_EXECUTE_FLAG = "-e";
    private static final String TAB_CLOSE_KEY = "w";
    private static final String COMMAND_KEY = "command down";
    private static final int APPLESCRIPT_SUCCESS_EXIT_CODE = 0;

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
        String script = closeTabOnly
                ? CLOSE_TAB_SCRIPT_TEMPLATE.formatted(escapedProcessName, TAB_CLOSE_KEY, COMMAND_KEY)
                : CLOSE_WINDOW_SCRIPT_TEMPLATE.formatted(escapedProcessName, TAB_CLOSE_KEY, COMMAND_KEY);

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
