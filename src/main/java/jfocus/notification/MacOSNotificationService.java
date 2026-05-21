package jfocus.notification;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * macOS native notification service using AppleScript and osascript.
 * Falls back to System.out logging if osascript is unavailable.
 */
public class MacOSNotificationService implements NotificationService {
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private volatile boolean enabled = true;

    public MacOSNotificationService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduledTasks = new ConcurrentHashMap<>();
        System.out.println("[MacOSNotificationService] Initialized with AppleScript backend.");
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available on macOS
    }

    @Override
    public void notify(NotificationPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        System.out.println("[MacOSNotificationService] notify() called: " + payload.getTitle());

        if (!enabled) {
            System.out.println("[MacOSNotificationService] Service disabled, skipping notification.");
            return;
        }

        // Run on background thread to avoid blocking
        scheduler.execute(() -> displayNotification(payload));
    }

    @Override
    public String scheduleDailyNotification(DailyNotificationRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        String id = UUID.randomUUID().toString();

        long initialDelaySeconds = secondsUntilNext(request.getTriggerTime());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            NotificationPayload payload = new NotificationPayload(
                    request.getTitle(),
                    request.getMessage(),
                    request.getSeverity(),
                    request.getSource());
            notify(payload);
        }, initialDelaySeconds, SECONDS_PER_DAY, TimeUnit.SECONDS);

        scheduledTasks.put(id, future);
        System.out.println("[MacOSNotificationService] Scheduled daily notification: " + id);
        return id;
    }

    @Override
    public boolean cancelScheduledNotification(String notificationId) {
        ScheduledFuture<?> future = scheduledTasks.remove(notificationId);
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(false);
        System.out.println("[MacOSNotificationService] Cancelled notification: " + notificationId + " - " + cancelled);
        return cancelled;
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
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        scheduler.shutdownNow();
        System.out.println("[MacOSNotificationService] Shutdown complete.");
    }

    private void displayNotification(NotificationPayload payload) {
        try {
            // macOS notification via AppleScript
            String title = escapeAppleScript(withSeverityEmoji(payload));
            String message = escapeAppleScript(payload.getMessage());

            String script = String.format(
                    "display notification \"%s\" with title \"%s\"",
                    message, title);

            Process process = Runtime.getRuntime().exec(new String[]{
                    "/usr/bin/osascript", "-e", script
            });

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("[MacOSNotificationService] Notification displayed: " + title);
            } else {
                System.err.println("[MacOSNotificationService] osascript exited with code: " + exitCode);
                logError(process);
            }
        } catch (Exception ex) {
            System.err.println("[MacOSNotificationService] Error: " + ex.getMessage());
            // Graceful fallback: just log to stdout
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
        // Escape backslashes and quotes for AppleScript
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

    private static long secondsUntilNext(LocalTime triggerTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDate.now().atTime(triggerTime);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Math.max(1L, Duration.between(now, nextRun).getSeconds());
    }
}
