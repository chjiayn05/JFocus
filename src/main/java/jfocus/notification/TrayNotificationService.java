package jfocus.notification;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TrayNotificationService implements NotificationService {
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private TrayIcon trayIcon;
    private volatile boolean enabled = true;
    private volatile String lastFailure = "";

    public TrayNotificationService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.trayIcon = createTrayIcon();
    }

    @Override
    public boolean isAvailable() {
        return trayIcon != null;
    }

    @Override
    public void notify(NotificationPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        System.out.println("[TrayNotificationService] notify() called: " + payload.getTitle());
        
        if (!enabled) {
            System.out.println("[TrayNotificationService] Service disabled, skipping notification.");
            return;
        }
        
        if (trayIcon == null) {
            lastFailure = "AWT tray icon is unavailable.";
            System.out.println("[TrayNotificationService] TrayIcon is null, notification skipped.");
            return;
        }

        try {
            trayIcon.setImage(createCircleImage(payload.getSeverity()));
            trayIcon.setToolTip("JFocus Notification: " + payload.getSeverity().name());
            trayIcon.displayMessage("[" + payload.getSeverity().name() + "] " + payload.getTitle(),
                    payload.getMessage(),
                    mapMessageType(payload.getSeverity()));
            lastFailure = "";
            System.out.println("[TrayNotificationService] Notification displayed successfully.");
        } catch (Exception ex) {
            lastFailure = "AWT tray notification failed: " + ex.getMessage();
            System.err.println("[TrayNotificationService] Error displaying notification: " + ex.getMessage());
            ex.printStackTrace();
        }
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
        return id;
    }

    @Override
    public boolean cancelScheduledNotification(String notificationId) {
        ScheduledFuture<?> future = scheduledTasks.remove(notificationId);
        if (future == null) {
            return false;
        }
        return future.cancel(false);
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
        return "awt-tray";
    }

    @Override
    public String getLastFailure() {
        return lastFailure;
    }

    @Override
    public String getUserGuidance() {
        return "Windows: Check Settings > System > Notifications, Do not disturb, and the taskbar notification area.";
    }

    @Override
    public void shutdown() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        scheduler.shutdownNow();

        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private static long secondsUntilNext(LocalTime triggerTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDate.now().atTime(triggerTime);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Math.max(1L, Duration.between(now, nextRun).getSeconds());
    }

    private static MessageType mapMessageType(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> MessageType.INFO;
            case WARN -> MessageType.WARNING;
            case URGENT -> MessageType.ERROR;
        };
    }

    private TrayIcon createTrayIcon() {
        if (!SystemTray.isSupported()) {
            lastFailure = "SystemTray.isSupported() returned false.";
            System.out.println("[TrayNotificationService] SystemTray.isSupported() returned false.");
            return null;
        }

        try {
            System.out.println("[TrayNotificationService] Attempting to create TrayIcon...");
            TrayIcon icon = new TrayIcon(createCircleImage(NotificationSeverity.INFO), "JFocus Notification");
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            lastFailure = "";
            System.out.println("[TrayNotificationService] TrayIcon created successfully.");
            return icon;
        } catch (AWTException ex) {
            lastFailure = "Failed to create AWT tray icon: " + ex.getMessage();
            System.err.println("[TrayNotificationService] Failed to create TrayIcon: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        } catch (Exception ex) {
            lastFailure = "Unexpected AWT tray error: " + ex.getMessage();
            System.err.println("[TrayNotificationService] Unexpected error: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    private static Image createCircleImage(NotificationSeverity severity) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.decode(severity.getColorHex()));
            graphics.fillOval(1, 1, 14, 14);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
