package jfocus.notification;

public class NoOpNotificationService implements NotificationService {
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
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void shutdown() {
    }
}
