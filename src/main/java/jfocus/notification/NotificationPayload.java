package jfocus.notification;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class NotificationPayload {
    private final String id;
    private final String title;
    private final String message;
    private final NotificationSeverity severity;
    private final String source;
    private final LocalDateTime timestamp;

    public NotificationPayload(String title, String message, NotificationSeverity severity, String source) {
        this(UUID.randomUUID().toString(), title, message, severity, source, LocalDateTime.now());
    }

    public NotificationPayload(String id,
            String title,
            String message,
            NotificationSeverity severity,
            String source,
            LocalDateTime timestamp) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
