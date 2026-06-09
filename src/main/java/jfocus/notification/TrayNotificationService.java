package jfocus.notification;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class TrayNotificationService implements NotificationService {

    private TrayIcon trayIcon;
    private volatile boolean enabled = true;

    public TrayNotificationService() {
        this.trayIcon = createTrayIcon();
    }

    @Override
    public boolean isAvailable() {
        return trayIcon != null;
    }

    @Override
    public void notify(NotificationPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");

        if (!enabled || trayIcon == null) {
            return;
        }

        try {
            trayIcon.setImage(createCircleImage(payload.getSeverity()));
            trayIcon.setToolTip("JFocus Notification: " + payload.getSeverity().name());
            trayIcon.displayMessage("[" + payload.getSeverity().name() + "] " + payload.getTitle(),
                    payload.getMessage(),
                    mapMessageType(payload.getSeverity()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
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
            return null;
        }

        try {
            TrayIcon icon = new TrayIcon(createCircleImage(NotificationSeverity.INFO), "JFocus Notification");
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            return icon;
        } catch (AWTException ex) {
            System.err.println("[TrayNotificationService] Failed to create TrayIcon: " + ex.getMessage());
            return null;
        } catch (Exception ex) {
            System.err.println("[TrayNotificationService] Unexpected error: " + ex.getMessage());
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
