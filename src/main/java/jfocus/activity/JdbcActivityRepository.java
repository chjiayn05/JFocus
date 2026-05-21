package jfocus.activity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/**
 * 使用 JDBC 存取活動資料。
 */
public class JdbcActivityRepository implements ActivityRepository {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DURATION_SECONDS_SQL = "CASE "
            + "WHEN end_time IS NOT NULL THEN "
            + "CASE WHEN CAST(strftime('%s', end_time) - strftime('%s', start_time) AS INTEGER) < 0 THEN 0 "
            + "ELSE CAST(strftime('%s', end_time) - strftime('%s', start_time) AS INTEGER) END "
            + "WHEN duration IS NOT NULL THEN CASE WHEN duration < 0 THEN 0 ELSE duration END "
            + "ELSE 0 END";

    private final DatabaseCore databaseCore;

    /**
     * 建立新的 JDBC repository。
     *
     * @param databaseCore 資料庫核心
     */
    public JdbcActivityRepository(DatabaseCore databaseCore) {
        this.databaseCore = Objects.requireNonNull(databaseCore, "databaseCore cannot be null");
    }

    @Override
    public void saveActivity(ActivityRecord activity) {
        Objects.requireNonNull(activity, "activity cannot be null");

        String sql = "INSERT INTO activities(app_name, window_title, start_time, end_time, duration, is_focus, session_id) VALUES(?,?,?,?,?,?,?)";
        int durationSeconds = calculateDurationSeconds(activity.startTime(), activity.endTime());

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, activity.appName());
            pstmt.setString(2, activity.windowTitle());
            pstmt.setString(3, TIMESTAMP_FORMATTER.format(activity.startTime()));
            pstmt.setString(4, TIMESTAMP_FORMATTER.format(activity.endTime()));
            pstmt.setInt(5, durationSeconds);
            pstmt.setInt(6, activity.focus() ? 1 : 0);
            pstmt.setString(7, activity.sessionId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("寫入資料失敗", e);
        }
    }

    @Override
    public Map<String, Integer> getAppUsageByDate(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");

        String sql = "SELECT app_name, SUM(" + DURATION_SECONDS_SQL + ") AS total_time FROM activities "
            + "WHERE start_time >= ? AND start_time < ? GROUP BY app_name";
        return queryUsageStats(sql, toDayStart(date), toDayStart(date.plusDays(1)));
    }

    @Override
    public Map<String, Integer> getAppUsageBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }

        String sql = "SELECT app_name, SUM(" + DURATION_SECONDS_SQL + ") AS total_time "
                + "FROM activities WHERE session_id = ? GROUP BY app_name";
        return queryUsageStats(sql, sessionId);
    }

    @Override
    public Map<String, Integer> getAppUsageByRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        String sql = "SELECT app_name, SUM(" + DURATION_SECONDS_SQL + ") AS total_time FROM activities "
                + "WHERE start_time >= ? AND start_time < ? GROUP BY app_name";
        return queryUsageStats(sql, toDayStart(startDate), toDayStart(endDate.plusDays(1)));
    }

    @Override
    public List<ActivityRecord> findActivitiesAfterId(int lastActivityId, int limit) {
        if (lastActivityId < 0) {
            throw new IllegalArgumentException("lastActivityId cannot be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        String sql = "SELECT id, app_name, window_title, start_time, end_time, duration, is_focus, session_id "
                + "FROM activities WHERE id > ? ORDER BY id ASC LIMIT ?";

        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindParameters(pstmt, lastActivityId, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                List<ActivityRecord> activities = new ArrayList<>();
                while (rs.next()) {
                    activities.add(mapActivity(rs));
                }
                return activities;
            }
        } catch (SQLException e) {
            throw new StorageException("讀取活動資料失敗", e);
        }
    }

    private Map<String, Integer> queryUsageStats(String sql, Object... params) {
        try (Connection conn = databaseCore.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindParameters(pstmt, params);

            try (ResultSet rs = pstmt.executeQuery()) {
                Map<String, Integer> usageStats = new HashMap<>();
                while (rs.next()) {
                    usageStats.put(rs.getString("app_name"), rs.getInt("total_time"));
                }
                return usageStats;
            }
        } catch (SQLException e) {
            throw new StorageException("查詢活動資料失敗", e);
        }
    }

    private void bindParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        for (int index = 0; index < params.length; index++) {
            pstmt.setObject(index + 1, params[index]);
        }
    }

    private ActivityRecord mapActivity(ResultSet rs) throws SQLException {
        LocalDateTime startTime = parseTimestamp(rs.getString("start_time"));
        LocalDateTime endTime = parseOptionalTimestamp(rs.getString("end_time"));
        if (endTime == null) {
            Integer duration = getNullableInt(rs, "duration");
            int safeDuration = duration == null ? 0 : Math.max(duration, 0);
            endTime = startTime.plusSeconds(safeDuration);
        }

        return new ActivityRecord(
                rs.getInt("id"),
                rs.getString("app_name"),
                rs.getString("window_title"),
                startTime,
                endTime,
                rs.getInt("is_focus") == 1,
                rs.getString("session_id"));
    }

    private String toDayStart(LocalDate date) {
        return TIMESTAMP_FORMATTER.format(date.atStartOfDay());
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            throw new StorageException("活動資料缺少 start_time", null);
        }

        try {
            return value.contains("T")
                    ? LocalDateTime.parse(value)
                    : LocalDateTime.parse(value, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new StorageException("無法解析活動時間: " + value, e);
        }
    }

    private LocalDateTime parseOptionalTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return value.contains("T")
                    ? LocalDateTime.parse(value)
                    : LocalDateTime.parse(value, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new StorageException("無法解析活動結束時間: " + value, e);
        }
    }

    private Integer getNullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    private int calculateDurationSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        long seconds = Duration.between(startTime, endTime).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }
}
