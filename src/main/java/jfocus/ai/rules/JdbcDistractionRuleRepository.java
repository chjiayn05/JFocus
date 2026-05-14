package jfocus.ai.rules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * JDBC implementation of distraction rules storage.
 */
public class JdbcDistractionRuleRepository implements DistractionRuleRepository {
    private final DatabaseCore databaseCore;

    public JdbcDistractionRuleRepository(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    @Override
    public void saveRule(RuleListType listType, AppWindowRule rule) {
        Objects.requireNonNull(listType, "listType cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");

        String sql = "INSERT INTO distraction_rules(list_type, app_name, window_title) VALUES(?,?,?) "
                + "ON CONFLICT(list_type, app_name, window_title) DO NOTHING";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, listType.name());
            pstmt.setString(2, normalize(rule.appName()));
            pstmt.setString(3, normalize(rule.windowTitle()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("寫入分心規則失敗", e);
        }
    }

    @Override
    public void deleteRule(RuleListType listType, AppWindowRule rule) {
        Objects.requireNonNull(listType, "listType cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");

        String sql = "DELETE FROM distraction_rules WHERE list_type = ? AND app_name = ? AND window_title = ?";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, listType.name());
            pstmt.setString(2, normalize(rule.appName()));
            pstmt.setString(3, normalize(rule.windowTitle()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("刪除分心規則失敗", e);
        }
    }

    @Override
    public boolean matches(RuleListType listType, String appName, String windowTitle) {
        Objects.requireNonNull(listType, "listType cannot be null");

        String normalizedApp = normalize(appName);
        String normalizedTitle = normalize(windowTitle);

        String sql = "SELECT 1 FROM distraction_rules "
                + "WHERE list_type = ? "
                + "AND (app_name = '' OR instr(?, app_name) > 0) "
                + "AND (window_title = '' OR instr(?, window_title) > 0) "
                + "LIMIT 1";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, listType.name());
            pstmt.setString(2, normalizedApp);
            pstmt.setString(3, normalizedTitle);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("查詢分心規則失敗", e);
        }
    }

    @Override
    public List<AppWindowRule> getRules(RuleListType listType) {
        Objects.requireNonNull(listType, "listType cannot be null");

        String sql = "SELECT app_name, window_title FROM distraction_rules WHERE list_type = ? ORDER BY id ASC";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, listType.name());

            try (ResultSet rs = pstmt.executeQuery()) {
                List<AppWindowRule> rules = new ArrayList<>();
                while (rs.next()) {
                    rules.add(new AppWindowRule(rs.getString("app_name"), rs.getString("window_title")));
                }
                return rules;
            }
        } catch (SQLException e) {
            throw new StorageException("讀取分心規則失敗", e);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
