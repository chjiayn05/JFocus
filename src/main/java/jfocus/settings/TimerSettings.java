package jfocus.settings;

public record TimerSettings(int workMinutes, int breakMinutes) {
    public static final int DEFAULT_WORK_MINUTES = 25;
    public static final int DEFAULT_BREAK_MINUTES = 5;

    public static TimerSettings defaults() {
        return new TimerSettings(DEFAULT_WORK_MINUTES, DEFAULT_BREAK_MINUTES);
    }

    public TimerSettings {
        if (workMinutes <= 0) {
            workMinutes = DEFAULT_WORK_MINUTES;
        }
        if (breakMinutes <= 0) {
            breakMinutes = DEFAULT_BREAK_MINUTES;
        }
    }
}
