package jfocus.notification;

public interface NotificationService {
    boolean isAvailable();

    void notify(NotificationPayload payload);

    void setEnabled(boolean enabled);

    boolean isEnabled();

    void shutdown();
}
