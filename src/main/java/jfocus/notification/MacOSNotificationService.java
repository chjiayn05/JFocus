package jfocus.notification;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * macOS native notification service using AppleScript and osascript.
 * Falls back to System.out logging if osascript is unavailable.
 */
public class MacOSNotificationService implements NotificationService {

    private final ExecutorService executor;
    private volatile boolean enabled = true;

    public MacOSNotificationService() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available on macOS
    }

    @Override
    public void notify(NotificationPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");

        if (!enabled) {
            return;
        }

        // Run on background thread to avoid blocking
        executor.execute(() -> displayNotification(payload));
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    private void displayNotification(NotificationPayload payload) {
        try {
            String title = escapeAppleScript(withSeverityEmoji(payload));
            String message = escapeAppleScript(payload.getMessage());

            String script = String.format(
                    "display notification \"%s\" with title \"%s\"",
                    message, title);

            Process process = Runtime.getRuntime().exec(new String[]{
                    "/usr/bin/osascript", "-e", script
            });

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("[MacOSNotificationService] osascript exited with code: " + exitCode);
                logError(process);
            }
        } catch (Exception ex) {
            System.err.println("[MacOSNotificationService] Error: " + ex.getMessage());
            System.out.println("[NOTIFICATION] " + payload.getTitle() + ": " + payload.getMessage());
        }
    }

    private void logError(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println("[osascript] " + line);
            }
        } catch (Exception ignored) {
        }
    }

    private static String escapeAppleScript(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String withSeverityEmoji(NotificationPayload payload) {
        String emoji = switch (payload.getSeverity()) {
            case INFO -> "ℹ️";
            case WARN -> "⚠️";
            case URGENT -> "🔴";
        };
        return emoji + " " + payload.getTitle();
    }
}
