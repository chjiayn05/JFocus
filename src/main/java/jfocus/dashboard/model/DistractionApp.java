package jfocus.dashboard.model;

public class DistractionApp {
    private String appName;
    private long durationSeconds;

    public DistractionApp(String appName, long durationSeconds) {
        this.appName = appName;
        this.durationSeconds = durationSeconds;
    }

    public String getAppName() {
        return appName;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
