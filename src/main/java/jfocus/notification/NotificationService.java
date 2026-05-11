package jfocus.notification;

public interface NotificationService {
    boolean isAvailable();

    void notify(NotificationPayload payload);

    String scheduleDailyNotification(DailyNotificationRequest request);

    boolean cancelScheduledNotification(String notificationId);

    void setEnabled(boolean enabled);

    boolean isEnabled();

    void shutdown();
}
