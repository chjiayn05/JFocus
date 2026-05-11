package jfocus.notification;

public enum NotificationSeverity {
    INFO("#3A86FF"),
    WARN("#FB8500"),
    URGENT("#D90429");

    private final String colorHex;

    NotificationSeverity(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getColorHex() {
        return colorHex;
    }
}