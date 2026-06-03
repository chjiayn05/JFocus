package jfocus.settings;

import java.util.Objects;

import jfocus.db.DatabaseCore;

public class JdbcDistractionSettingsRepository {
    private static final String SYSTEM_NOTIFICATIONS_ENABLED_KEY = "system_notifications_enabled";

    private final AppSettingsRepository appSettingsRepository;

    public JdbcDistractionSettingsRepository(DatabaseCore databaseCore) {
        this(new JdbcAppSettingsRepository(databaseCore));
    }

    public JdbcDistractionSettingsRepository(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = Objects.requireNonNull(appSettingsRepository,
                "appSettingsRepository cannot be null");
    }

    public DistractionSettings loadSettings() {
        DistractionSettings defaults = DistractionSettings.defaults();
        boolean notificationsEnabled = appSettingsRepository.findValue(SYSTEM_NOTIFICATIONS_ENABLED_KEY)
                .map(this::parseBoolean)
                .orElse(defaults.systemNotificationsEnabled());

        return new DistractionSettings(notificationsEnabled);
    }

    public void saveSystemNotificationsEnabled(boolean enabled) {
        appSettingsRepository.saveValue(SYSTEM_NOTIFICATIONS_ENABLED_KEY, Boolean.toString(enabled));
    }

    private boolean parseBoolean(String rawValue) {
        return Boolean.parseBoolean(rawValue);
    }
}
