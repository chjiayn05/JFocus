package jfocus.notification;

public interface NotificationService {
    boolean isAvailable();

    void notify(NotificationPayload payload);

    String scheduleDailyNotification(DailyNotificationRequest request);

    boolean cancelScheduledNotification(String notificationId);

    void setEnabled(boolean enabled);

    boolean isEnabled();

    default String getBackendName() {
        return getClass().getSimpleName();
    }

    default String getLastFailure() {
        return "";
    }

    default String getUserGuidance() {
        return "";
    }

    void shutdown();
}
