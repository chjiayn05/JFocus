package jfocus.notification;

import java.time.LocalTime;
import java.util.Objects;

public final class DailyNotificationRequest {
    private final String title;
    private final String message;
    private final LocalTime triggerTime;
    private final NotificationSeverity severity;
    private final String source;

    public DailyNotificationRequest(String title,
            String message,
            LocalTime triggerTime,
            NotificationSeverity severity,
            String source) {
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.triggerTime = Objects.requireNonNull(triggerTime, "triggerTime cannot be null");
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public LocalTime getTriggerTime() {
        return triggerTime;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }
}
