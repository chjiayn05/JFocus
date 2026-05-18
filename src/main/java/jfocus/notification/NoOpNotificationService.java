package jfocus.notification;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoOpNotificationService implements NotificationService {
    private final Set<String> scheduledNotificationIds = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void notify(NotificationPayload payload) {
        // No-op fallback keeps interface stable in unsupported environments.
    }

    @Override
    public String scheduleDailyNotification(DailyNotificationRequest request) {
        String id = UUID.randomUUID().toString();
        scheduledNotificationIds.add(id);
        return id;
    }

    @Override
    public boolean cancelScheduledNotification(String notificationId) {
        return scheduledNotificationIds.remove(notificationId);
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
    public String getBackendName() {
        return "noop";
    }

    @Override
    public String getLastFailure() {
        return "No notification backend is available.";
    }

    @Override
    public void shutdown() {
        scheduledNotificationIds.clear();
    }
}
