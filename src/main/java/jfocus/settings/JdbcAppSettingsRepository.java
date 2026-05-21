package jfocus.settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

public class JdbcAppSettingsRepository implements AppSettingsRepository {
    private final DatabaseCore databaseCore;

    public JdbcAppSettingsRepository(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    @Override
    public Optional<String> findValue(String key) {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ? LIMIT 1";

        try (Connection conn = databaseCore.getConnection()) {
            ensureSettingsTable(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, requireKey(key));

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(rs.getString("setting_value"));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("讀取應用設定失敗", e);
        }
    }

    @Override
    public void saveValue(String key, String value) {
        String sql = "INSERT INTO app_settings(setting_key, setting_value, updated_at) VALUES(?, ?, datetime('now')) "
                + "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, updated_at = datetime('now')";

        try (Connection conn = databaseCore.getConnection()) {
            ensureSettingsTable(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, requireKey(key));
                pstmt.setString(2, Objects.requireNonNull(value, "value cannot be null"));
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("儲存應用設定失敗", e);
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        return key.trim();
    }

    private void ensureSettingsTable(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS app_settings (
                    setting_key TEXT PRIMARY KEY,
                    setting_value TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
