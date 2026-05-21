package jfocus.settings;

import java.util.Optional;

public interface AppSettingsRepository {
    Optional<String> findValue(String key);

    void saveValue(String key, String value);
}
