package jfocus.settings;

public record DistractionSettings(boolean systemNotificationsEnabled) {

    public static final boolean DEFAULT_SYSTEM_NOTIFICATIONS_ENABLED = true;

    public static DistractionSettings defaults() {
        return new DistractionSettings(DEFAULT_SYSTEM_NOTIFICATIONS_ENABLED);
    }
}
