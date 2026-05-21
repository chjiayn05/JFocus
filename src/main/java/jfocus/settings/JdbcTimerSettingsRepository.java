package jfocus.settings;

import java.util.Objects;

import jfocus.db.DatabaseCore;

public class JdbcTimerSettingsRepository {
    private static final String WORK_MINUTES_KEY = "timer_work_minutes";
    private static final String BREAK_MINUTES_KEY = "timer_break_minutes";

    private final AppSettingsRepository appSettingsRepository;

    public JdbcTimerSettingsRepository(DatabaseCore databaseCore) {
        this(new JdbcAppSettingsRepository(databaseCore));
    }

    public JdbcTimerSettingsRepository(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = Objects.requireNonNull(appSettingsRepository, "appSettingsRepository cannot be null");
    }

    public TimerSettings loadSettings() {
        TimerSettings defaults = TimerSettings.defaults();
        int workMinutes = loadPositiveInt(WORK_MINUTES_KEY, defaults.workMinutes());
        int breakMinutes = loadPositiveInt(BREAK_MINUTES_KEY, defaults.breakMinutes());
        return new TimerSettings(workMinutes, breakMinutes);
    }

    public void saveSettings(TimerSettings settings) {
        TimerSettings validatedSettings = Objects.requireNonNull(settings, "settings cannot be null");

        appSettingsRepository.saveValue(WORK_MINUTES_KEY, Integer.toString(validatedSettings.workMinutes()));
        appSettingsRepository.saveValue(BREAK_MINUTES_KEY, Integer.toString(validatedSettings.breakMinutes()));
    }

    private int loadPositiveInt(String key, int fallback) {
        return appSettingsRepository.findValue(key)
                .map(rawValue -> parsePositiveInt(rawValue, fallback))
                .orElse(fallback);
    }

    private int parsePositiveInt(String rawValue, int fallback) {
        try {
            int value = Integer.parseInt(rawValue);
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
