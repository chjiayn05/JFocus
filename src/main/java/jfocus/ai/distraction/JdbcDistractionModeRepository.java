package jfocus.ai.distraction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

public class JdbcDistractionModeRepository implements DistractionModeRepository {
    private static final String MODE_SETTING_KEY = "distraction_handling_mode";

    private final DatabaseCore databaseCore;

    public JdbcDistractionModeRepository(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    @Override
    public DistractionHandlingMode loadMode(DistractionHandlingMode defaultMode) {
        DistractionHandlingMode fallbackMode = Objects.requireNonNull(defaultMode, "defaultMode cannot be null");

        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ? LIMIT 1";
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MODE_SETTING_KEY);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    saveMode(fallbackMode);
                    return fallbackMode;
                }

                String rawValue = rs.getString("setting_value");
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
        } catch (SQLException e) {
            throw new StorageException("讀取分心模式失敗", e);
        }
    }

    @Override
    public void saveMode(DistractionHandlingMode mode) {
        DistractionHandlingMode validatedMode = Objects.requireNonNull(mode, "mode cannot be null");

        String sql = "INSERT INTO app_settings(setting_key, setting_value, updated_at) VALUES(?, ?, datetime('now')) "
                + "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, updated_at = datetime('now')";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MODE_SETTING_KEY);
            pstmt.setString(2, validatedMode.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("儲存分心模式失敗", e);
        }
    }
}
