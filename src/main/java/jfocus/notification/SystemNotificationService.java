package jfocus.notification;

import java.awt.SystemTray;
import java.util.Locale;
import java.util.Objects;

public class SystemNotificationService implements NotificationService {
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String WINDOWS_TOKEN = "win";
    private static final String MAC_TOKEN = "mac";

    private final NotificationService delegate;

    public SystemNotificationService() {
        this(buildPlatformService());
    }

    SystemNotificationService(NotificationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    private static NotificationService buildPlatformService() {
        String osName = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        boolean isTraySupported = SystemTray.isSupported();
        System.out.println("[NotificationService] OS: " + osName + ", SystemTray.isSupported(): " + isTraySupported);
        
        // macOS: use AppleScript notifications via Java Process.
        if (osName.contains(MAC_TOKEN)) {
            System.out.println("[NotificationService] Using MacOSNotificationService.");
            return new MacOSNotificationService();
        }
        
        // Windows: use Java AWT SystemTray.
        if (osName.contains(WINDOWS_TOKEN) && isTraySupported) {
            System.out.println("[NotificationService] Using TrayNotificationService for Windows.");
            return new TrayNotificationService();
        }

        System.out.println("[NotificationService] SystemTray not available, using NoOp fallback.");
        return new NoOpNotificationService();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public void notify(NotificationPayload payload) {
        delegate.notify(payload);
    }

    @Override
    public String scheduleDailyNotification(DailyNotificationRequest request) {
        return delegate.scheduleDailyNotification(request);
    }

    @Override
    public boolean cancelScheduledNotification(String notificationId) {
        return delegate.cancelScheduledNotification(notificationId);
    }

    @Override
    public void setEnabled(boolean enabled) {
        delegate.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public String getBackendName() {
        return delegate.getBackendName();
    }

    @Override
    public String getLastFailure() {
        return delegate.getLastFailure();
    }

    @Override
    public String getUserGuidance() {
        return delegate.getUserGuidance();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
