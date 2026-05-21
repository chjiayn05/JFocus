package jfocus.ai.distraction;

import java.util.Objects;

import jfocus.db.DatabaseCore;
import jfocus.settings.AppSettingsRepository;
import jfocus.settings.JdbcAppSettingsRepository;

public class JdbcDistractionModeRepository implements DistractionModeRepository {
    private static final String MODE_SETTING_KEY = "distraction_handling_mode";

    private final AppSettingsRepository appSettingsRepository;

    public JdbcDistractionModeRepository(DatabaseCore databaseCore) {
        this(new JdbcAppSettingsRepository(databaseCore));
    }

    public JdbcDistractionModeRepository(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = Objects.requireNonNull(appSettingsRepository, "appSettingsRepository cannot be null");
    }

    @Override
    public DistractionHandlingMode loadMode(DistractionHandlingMode defaultMode) {
        DistractionHandlingMode fallbackMode = Objects.requireNonNull(defaultMode, "defaultMode cannot be null");

        String rawValue = appSettingsRepository.findValue(MODE_SETTING_KEY).orElse(null);
        if (rawValue == null || rawValue.isBlank()) {
            saveMode(fallbackMode);
            return fallbackMode;
        }

        try {
            return DistractionHandlingMode.valueOf(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            saveMode(fallbackMode);
            return fallbackMode;
        }
    }

    @Override
    public void saveMode(DistractionHandlingMode mode) {
        DistractionHandlingMode validatedMode = Objects.requireNonNull(mode, "mode cannot be null");
        appSettingsRepository.saveValue(MODE_SETTING_KEY, validatedMode.name());
    }
}
